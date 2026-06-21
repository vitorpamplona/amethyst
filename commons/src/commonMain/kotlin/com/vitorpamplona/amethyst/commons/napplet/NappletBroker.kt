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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.TimeUtils
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
 * - **Signer self-gating**: an identity read or a sign-as-user op ([NappletRequest.signsAsUser])
 *   is gated here only when the key lives in Amethyst (a [NostrSignerInternal]). Remote (NIP-46) and
 *   external (NIP-55) signers run their own per-request consent UI, so we defer to them rather than
 *   double-prompt. A standing DENY is still honored, and the applet must still have *declared* the
 *   capability. This is safe only because the napplet host runs foreground-only, so the signer's
 *   prompt appears in the clear context of the user interacting with that napplet (it can't be
 *   fired from the background).
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
) {
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

        val authorized =
            when {
                // Keyboard/command action registration is a shell-mediated UI affordance, not key
                // access — declared is enough; it never prompts.
                request is NappletRequest.RegisterAction || request is NappletRequest.UnregisterAction -> true
                // Remote/external signers run their own per-request consent UI — defer to them.
                signerSelfGates(request) -> true
                // A standing allow short-circuits, except for per-use capabilities (e.g. payments).
                ledger.decide(identity, capability) == PermissionDecision.ALLOW && !capability.requiresPerUseConsent -> true
                else -> {
                    val grant = consentPrompt.request(identity, capability, request)
                    ledger.record(identity, capability, effectiveGrant(capability, grant))
                    grant.allowsExecution
                }
            }

        if (!authorized) return NappletResponse.Denied(capability, "The user declined.")

        return try {
            execute(identity, request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NappletResponse.Failed(e.message ?: e::class.simpleName ?: "Unknown error")
        }
    }

    /** Identity reads and sign-as-user ops are gated by us only when we hold the key; remote/external signers gate themselves. */
    private fun signerSelfGates(request: NappletRequest): Boolean = (request.capability == NappletCapability.IDENTITY || request.signsAsUser) && signer !is NostrSignerInternal

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

            is NappletRequest.IdentityRead -> {
                val gateway = identityReads ?: return NappletResponse.Unsupported("identity.${request.method}")
                val raw = gateway.read(request.method, request.argument) ?: return NappletResponse.Unsupported("identity.${request.method}")
                NappletResponse.Json(raw)
            }

            // The napplet supplies an unsigned template; the shell signs and publishes it.
            // created_at comes from the host, never the applet, so it cannot backdate.
            is NappletRequest.Publish -> signAndPublish(request.kind, request.tags, request.content)

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
                NappletResponse.Events(gateway.query(request.filter))
            }

            // Live tailing is a follow-up; for now subscribe returns the initial matches.
            is NappletRequest.Subscribe -> {
                val gateway = relay ?: return NappletResponse.Unsupported("relay.subscribe")
                NappletResponse.Events(gateway.query(request.filter))
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

            // Keyboard actions are acknowledged so SDK napplets' registerAction() resolves; the
            // actual global-key binding (and the keys.action push) is a follow-up.
            is NappletRequest.RegisterAction -> NappletResponse.ActionRegistered(request.actionId)

            is NappletRequest.UnregisterAction -> NappletResponse.Done

            is NappletRequest.PayInvoice -> {
                val gateway = wallet ?: return NappletResponse.Unsupported("value.payInvoice")
                NappletResponse.Paid(gateway.payInvoice(request.invoice))
            }

            is NappletRequest.ResourceBytes -> {
                val gateway = resource ?: return NappletResponse.Unsupported("resource.bytes")
                val fetched = gateway.fetch(request.url) ?: return NappletResponse.Failed("Could not fetch the resource.")
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
}
