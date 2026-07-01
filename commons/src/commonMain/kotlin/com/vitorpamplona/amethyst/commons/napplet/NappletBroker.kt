/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.napplet.permissions.PermissionDecision
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletResponse
import com.vitorpamplona.amethyst.commons.napplet.signers.AppConnectResult
import com.vitorpamplona.amethyst.commons.napplet.signers.AppSignerPolicy
import com.vitorpamplona.amethyst.commons.napplet.signers.NostrConnectPrompt
import com.vitorpamplona.amethyst.commons.napplet.signers.NostrOpDecision
import com.vitorpamplona.amethyst.commons.napplet.signers.NostrSignerConsentPrompt
import com.vitorpamplona.amethyst.commons.napplet.signers.NostrSignerOp
import com.vitorpamplona.amethyst.commons.napplet.signers.NostrSignerPermissionLedger
import com.vitorpamplona.amethyst.commons.napplet.signers.SignerOpGrant
import com.vitorpamplona.amethyst.commons.napplet.signers.toSignerOp
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/**
 * The trust boundary, expressed as code. The broker is the **only** component that holds a
 * [NostrSigner] and turns an untrusted napplet's [NappletRequest] into a [NappletResponse],
 * gating every dangerous operation through three checks, in order:
 *
 * 1. **Declaration** — the request's capability must appear in the manifest's `requires`
 *    (`declared`). An applet cannot use a capability it never asked for up front.
 * 2. **Ledger** — a standing grant/denial decides without prompting.
 * 3. **Consent** — when no standing decision exists, the user is asked.
 *
 * Two capability-specific policies refine step 2/3:
 * - **Per-use capabilities** ([NappletCapability.requiresPerUseConsent], i.e. [NappletCapability.VALUE])
 *   never auto-approve from a prior grant — every payment is confirmed afresh, with the amount shown.
 * - All signer types — internal, NIP-46 remote, and NIP-55 external — are gated through Amethyst's
 *   consent UI before the operation reaches the signer. External signers add their own per-request
 *   prompt on top (double-prompting), ensuring the user can differentiate requests from different
 *   apps inside the external signer.
 *
 * Security invariants enforced here (never trusted from the applet): the signing identity is
 * always the host's signer; the napplet only ever supplies an unsigned template — the shell signs
 * it and stamps `created_at` from the host clock; a response never contains private key bytes;
 * storage is namespaced per applet coordinate.
 */
class NappletBroker(
    private val signer: NostrSigner,
    private val ledger: NappletPermissionLedger,
    private val consentPrompt: NappletConsentPrompt,
    private val relay: NappletRelayGateway? = null,
    private val storage: NappletStorage? = null,
    private val wallet: NappletWalletGateway? = null,
    private val resource: NappletResourceGateway? = null,
    private val upload: NappletUploadGateway? = null,
    private val identityReads: NappletIdentityGateway? = null,
    private val theme: NappletThemeGateway? = null,
    private val notify: NappletNotifyGateway? = null,
    private val signerLedger: NostrSignerPermissionLedger? = null,
    private val nostrConnectPrompt: NostrConnectPrompt? = null,
    private val signerConsentPrompt: NostrSignerConsentPrompt? = null,
    // Wall-clock source (ms) for the post-cancel re-prompt cooldown; injectable for tests.
    private val nowMillis: () -> Long = { TimeUtils.nowMillis() },
) {
    // Serializes the consent-prompt path so concurrent requests queue into one dialog at a time
    // (see [authorizeWithConsent]). Only the prompt is held here; non-prompting paths and execute()
    // run unserialized.
    private val consentLock = Mutex()

    // Serializes first-connect and per-op signer consent dialogs so concurrent signing requests
    // queue into one dialog at a time rather than launching several dialogs simultaneously.
    private val signerConsentLock = Mutex()

    // In-memory session grants (AllowForSession): cleared when this broker instance is destroyed.
    // Only accessed under signerConsentLock.
    private val sessionAllows = mutableSetOf<String>()

    // Apps whose first-connect dialog the user just dismissed with Cancel, mapped to the wall-clock
    // instant (ms) their re-prompt suppression expires. Cancelling means "not now": for a brief
    // cooldown we drop re-prompts so the burst of requests a page/napplet fires on load (relays +
    // storage + identity, all racing) doesn't relaunch the dialog once per request. Once the cooldown
    // lapses the entry is stale, so a fresh request — e.g. the user deliberately re-triggering login —
    // prompts again. (A plain "suppress forever" set used to lock the user out: a single Cancel killed
    // every future prompt for the broker's whole lifetime, and because Cancel persists nothing there
    // was no Connected-Apps entry to clear to recover.)
    // Only accessed under signerConsentLock.
    private val cancelledUntil = mutableMapOf<String, Long>()

    /**
     * Authorizes and runs [request] on behalf of [identity]. [declared] is the capability set the
     * manifest's `requires` resolved to; a request outside it is refused before any prompt.
     * Re-throws [CancellationException]; converts any other failure into [NappletResponse.Failed]
     * so host internals never cross back to the applet process.
     */
    suspend fun handle(
        identity: NappletIdentity,
        request: NappletRequest,
        declared: Set<NappletCapability>,
    ): NappletResponse {
        val capability = request.capability

        // shell.supports is capability negotiation: always answerable, no declaration/consent.
        if (request is NappletRequest.ShellSupports) {
            val cap = NappletCapability.fromNapDomain(request.domain)
            return NappletResponse.Supported(cap != null && cap in declared)
        }

        if (capability !in declared) {
            return NappletResponse.Denied(capability, "This napplet did not declare the '${capability.name.lowercase()}' capability.")
        }

        // A standing denial always wins, even for signer-self-gated capabilities.
        if (ledger.decide(identity, capability) == PermissionDecision.DENY) {
            return NappletResponse.Denied(capability, "Blocked by a standing denial.")
        }

        // Show the first-connect dialog if the app has no signer policy yet.
        if (signerLedger != null && !signerLedger.hasPolicy(identity.coordinate)) {
            if (!ensureConnected(identity, declared)) {
                return NappletResponse.Denied(capability, "Connection not authorized.")
            }
        }

        val authorized =
            when {
                // Keyboard/command action registration is a shell-mediated UI affordance, not key
                // access — declared is enough; it never prompts.
                request is NappletRequest.RegisterAction || request is NappletRequest.UnregisterAction -> true
                // Cosmetic/negotiation capabilities (theme) never prompt.
                !capability.requiresConsent -> true
                // A standing allow short-circuits, except for per-use capabilities (e.g. payments).
                ledger.decide(identity, capability) == PermissionDecision.ALLOW && !capability.requiresPerUseConsent -> true
                else -> authorizeWithConsent(identity, capability, request)
            }

        if (!authorized) return NappletResponse.Denied(capability, "The user declined.")

        // Additional per-operation gate for signing/encryption.
        if (signerLedger != null) {
            val op = request.toSignerOp()
            if (op != null && !authorizeSignerOp(identity, op, request)) {
                return NappletResponse.Denied(capability, "Signing operation declined.")
            }
        }

        return try {
            execute(identity, request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NappletResponse.Failed(e.message ?: e::class.simpleName ?: "Unknown error")
        }
    }

    /**
     * Prompts for consent under [consentLock] so several requests arriving at once — the common case,
     * a napplet reading relays + storage + identity on load — queue into one dialog at a time instead
     * of racing. Without this each would launch a consent prompt concurrently; the host can only show
     * one, so the rest are dropped and their calls hang forever.
     *
     * After taking the lock we re-read the standing decision: a sibling request for the same capability
     * may have recorded an Allow/Deny while we waited, so we honor that instead of prompting again.
     * Per-use capabilities (payments) always re-prompt, so they skip the re-check.
     */
    private suspend fun authorizeWithConsent(
        identity: NappletIdentity,
        capability: NappletCapability,
        request: NappletRequest,
    ): Boolean =
        consentLock.withLock {
            if (!capability.requiresPerUseConsent) {
                when (ledger.decide(identity, capability)) {
                    PermissionDecision.DENY -> return@withLock false
                    PermissionDecision.ALLOW -> return@withLock true
                    PermissionDecision.ASK -> {}
                }
            }
            val grant = consentPrompt.request(identity, capability, request)
            ledger.record(identity, capability, effectiveGrant(capability, grant))
            grant.allowsExecution
        }

    /** Downgrades a grant to one-shot when the capability forbids persisting that scope (e.g. payments). */
    private fun effectiveGrant(
        capability: NappletCapability,
        grant: GrantState,
    ): GrantState =
        when {
            grant == GrantState.ALLOW_ALWAYS && !capability.canGrantAlways -> GrantState.ALLOW_ONCE
            grant == GrantState.ALLOW_SESSION && !capability.canGrantSession -> GrantState.ALLOW_ONCE
            else -> grant
        }

    private suspend fun execute(
        identity: NappletIdentity,
        request: NappletRequest,
    ): NappletResponse =
        when (request) {
            // Negotiation is resolved in handle(); execute() is never reached for it.
            is NappletRequest.ShellSupports -> NappletResponse.Supported(true)

            is NappletRequest.GetPublicKey -> NappletResponse.PublicKey(signer.pubKey)

            is NappletRequest.ThemeGet -> {
                val gateway = theme ?: return NappletResponse.Unsupported("theme.get")
                val colors = gateway.current()
                NappletResponse.Theme(colors.background, colors.text, colors.primary)
            }

            is NappletRequest.IdentityRead -> {
                val gateway = identityReads ?: return NappletResponse.Unsupported("identity.${request.method}")
                val raw = gateway.read(request.method, request.argument) ?: return NappletResponse.Unsupported("identity.${request.method}")
                NappletResponse.Json(raw)
            }

            // The napplet supplies an unsigned template; the shell signs and publishes it.
            // created_at comes from the host, never the applet, so it cannot backdate.
            is NappletRequest.Publish -> signAndPublish(request.kind, request.tags, request.content)

            // NIP-07 signEvent: sign as the user (honoring the app's created_at) and return it WITHOUT
            // publishing — the web app sends it to relays itself. pubkey is still fixed by the signer.
            is NappletRequest.SignEvent -> NappletResponse.Published(signer.sign(request.createdAt, request.kind, request.tags, request.content), emptyList())

            is NappletRequest.PublishEncrypted -> {
                val ciphertext =
                    when (request.encryption.trim().lowercase()) {
                        "nip04" -> signer.nip04Encrypt(request.content, request.recipient)
                        else -> signer.nip44Encrypt(request.content, request.recipient)
                    }
                signAndPublish(request.kind, withRecipientTag(request.tags, request.recipient), ciphertext)
            }

            is NappletRequest.QueryEvents -> {
                val gateway = relay ?: return NappletResponse.Unsupported("relay.query")
                NappletResponse.Events(gateway.query(request.filters))
            }

            // The broker only authorizes the subscription (consent + declaration); the host opens the
            // live relay subscription and streams relay.event/relay.eose/relay.closed by subId.
            is NappletRequest.Subscribe -> {
                relay ?: return NappletResponse.Unsupported("relay.subscribe")
                NappletResponse.Subscribed
            }

            is NappletRequest.StorageGet -> {
                val store = storage ?: return NappletResponse.Unsupported("storage.getItem")
                NappletResponse.StorageValue(store.get(identity.coordinate, request.key))
            }

            is NappletRequest.StorageSet -> {
                val store = storage ?: return NappletResponse.Unsupported("storage.setItem")
                store.set(identity.coordinate, request.key, request.value)
                NappletResponse.Done
            }

            is NappletRequest.StorageRemove -> {
                val store = storage ?: return NappletResponse.Unsupported("storage.removeItem")
                store.remove(identity.coordinate, request.key)
                NappletResponse.Done
            }

            is NappletRequest.StorageKeys -> {
                val store = storage ?: return NappletResponse.Unsupported("storage.keys")
                NappletResponse.Strings(store.keys(identity.coordinate))
            }

            is NappletRequest.NotifyCreate -> {
                val gateway = notify ?: return NappletResponse.Unsupported("notify.create")
                NappletResponse.NotifyCreated(gateway.create(identity.coordinate, request.title, request.body))
            }

            is NappletRequest.NotifyList -> {
                val gateway = notify ?: return NappletResponse.Unsupported("notify.list")
                NappletResponse.NotifyListed(gateway.list(identity.coordinate))
            }

            is NappletRequest.NotifyDismiss -> {
                val gateway = notify ?: return NappletResponse.Unsupported("notify.dismiss")
                gateway.dismiss(identity.coordinate, request.id)
                NappletResponse.Done
            }

            // Keyboard actions are acknowledged (with the honored binding) so SDK napplets'
            // registerAction() resolves; the host binds the key and pushes keys.action when triggered.
            is NappletRequest.RegisterAction -> NappletResponse.ActionRegistered(request.actionId, request.binding)

            is NappletRequest.UnregisterAction -> NappletResponse.Done

            is NappletRequest.PayInvoice -> {
                val gateway = wallet ?: return NappletResponse.Unsupported("value.payInvoice")
                NappletResponse.Paid(gateway.payInvoice(request.invoice))
            }

            is NappletRequest.ResourceBytes -> {
                val gateway = resource ?: return NappletResponse.Unsupported("resource.bytes")
                val fetched = gateway.fetch(request.url, identity.coordinate) ?: return NappletResponse.Failed("Could not fetch the resource.")
                NappletResponse.Bytes(fetched.bytes, fetched.contentType)
            }

            is NappletRequest.UploadBlob -> {
                val gateway = upload ?: return NappletResponse.Unsupported("upload.upload")
                val res = gateway.upload(request.bytes, request.contentType, request.filename) ?: return NappletResponse.Failed("Upload failed.")
                NappletResponse.Uploaded(res.url, res.sha256, res.size, res.mimeType)
            }
        }

    /**
     * Signs a napplet-supplied template **as the active user** and publishes it. The applet never
     * sees a key, can never sign as another identity (the signer fixes `pubkey`), and cannot
     * backdate (`created_at` is the host clock).
     */
    private suspend fun signAndPublish(
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): NappletResponse {
        val gateway = relay ?: return NappletResponse.Unsupported("relay.publish")
        val signed: Event = signer.sign(TimeUtils.now(), kind, tags, content)
        return NappletResponse.Published(signed, gateway.publish(signed))
    }

    /** Ensures the encrypted event addresses [recipient] with a `p` tag, without duplicating one. */
    private fun withRecipientTag(
        tags: Array<Array<String>>,
        recipient: String,
    ): Array<Array<String>> =
        if (tags.any { it.size >= 2 && it[0] == "p" && it[1] == recipient }) {
            tags
        } else {
            tags + arrayOf(arrayOf("p", recipient))
        }

    /**
     * Shows the first-connect "Connect to Nostr" dialog if no signer policy exists yet.
     * On success, stores the chosen policy and bulk-grants all declared non-payment capabilities.
     * Returns false if the user cancelled or blocked the app.
     */
    private suspend fun ensureConnected(
        identity: NappletIdentity,
        declared: Set<NappletCapability>,
    ): Boolean =
        signerConsentLock.withLock {
            val sl = signerLedger ?: return@withLock true
            // Re-check after acquiring lock: a sibling request may have set the policy while we waited.
            if (sl.hasPolicy(identity.coordinate)) return@withLock true

            // Within the post-cancel cooldown, suppress re-prompting so a load-time burst doesn't
            // relaunch the dialog per request. Once it lapses, drop the stale entry and fall through to
            // prompt again — this is what lets the user retry after dismissing the dialog. The cooldown
            // self-clears, so there is nothing to hunt down and clear in Connected Apps.
            cancelledUntil[identity.coordinate]?.let { until ->
                if (nowMillis() < until) return@withLock false
                cancelledUntil.remove(identity.coordinate)
            }

            val prompt = nostrConnectPrompt ?: return@withLock true
            when (val result = prompt.request(identity)) {
                is AppConnectResult.Connected -> {
                    cancelledUntil.remove(identity.coordinate)
                    sl.setPolicy(identity.coordinate, result.policy)
                    // Bulk-grant non-payment capabilities only for non-paranoid policies.
                    // PARANOID users chose "ask me for everything" — leave the capability ledger
                    // empty so each capability prompts on first use.
                    if (result.policy != AppSignerPolicy.PARANOID) {
                        for (cap in declared) {
                            if (!cap.requiresPerUseConsent) {
                                ledger.record(identity, cap, GrantState.ALLOW_ALWAYS)
                            }
                        }
                    }
                    true
                }
                AppConnectResult.Blocked -> {
                    sl.setPolicy(identity.coordinate, AppSignerPolicy.PARANOID)
                    for (cap in declared) {
                        ledger.record(identity, cap, GrantState.DENY)
                    }
                    false
                }
                AppConnectResult.Cancelled -> {
                    cancelledUntil[identity.coordinate] = nowMillis() + CANCEL_REPROMPT_COOLDOWN_MS
                    false
                }
            }
        }

    /**
     * Gates a specific signing/encryption operation through the signer permission ledger.
     * If the ledger says ASK, prompts the user and records their choice.
     */
    private suspend fun authorizeSignerOp(
        identity: NappletIdentity,
        op: NostrSignerOp,
        request: NappletRequest,
    ): Boolean =
        signerConsentLock.withLock {
            val sl = signerLedger ?: return@withLock true
            // Session grants win immediately without touching storage.
            if (op.key in sessionAllows) {
                sl.updateLastUsed(identity.coordinate)
                return@withLock true
            }
            when (sl.decide(identity.coordinate, op)) {
                NostrOpDecision.ALLOW -> {
                    sl.updateLastUsed(identity.coordinate)
                    true
                }
                NostrOpDecision.DENY -> false
                NostrOpDecision.ASK -> {
                    val prompt = signerConsentPrompt ?: return@withLock true
                    when (val grant = prompt.request(identity, op, request)) {
                        is SignerOpGrant.AllowAll -> {
                            sl.setPolicy(identity.coordinate, AppSignerPolicy.FULL_TRUST)
                            sl.updateLastUsed(identity.coordinate)
                            true
                        }
                        is SignerOpGrant.AllowForOp -> {
                            sl.setOpDecision(identity.coordinate, op, NostrOpDecision.ALLOW)
                            sl.updateLastUsed(identity.coordinate)
                            true
                        }
                        is SignerOpGrant.AllowForSession -> {
                            sessionAllows.add(op.key)
                            sl.updateLastUsed(identity.coordinate)
                            true
                        }
                        is SignerOpGrant.AllowUntil -> {
                            sl.setTimedOpDecision(identity.coordinate, op, NostrOpDecision.ALLOW, grant.expiresAt)
                            sl.updateLastUsed(identity.coordinate)
                            true
                        }
                        is SignerOpGrant.DenyForOp -> {
                            sl.setOpDecision(identity.coordinate, op, NostrOpDecision.DENY)
                            false
                        }
                        else -> grant.isAllowed
                    }
                }
            }
        }

    companion object {
        /**
         * How long (ms) a Cancel on the first-connect dialog suppresses re-prompting for the same app.
         * Long enough to absorb the burst of requests a page/napplet fires on load without relaunching
         * the dialog per request; short enough that a deliberate user retry seconds later prompts again.
         */
        private const val CANCEL_REPROMPT_COOLDOWN_MS = 3_000L
    }
}
