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
import com.vitorpamplona.quartz.nip01Core.crypto.verify
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
 * - **Per-use capabilities** ([NappletCapability.requiresPerUseConsent], i.e. [NappletCapability.WALLET])
 *   never auto-approve from a prior grant — every payment is confirmed afresh, with the amount shown.
 * - **Signer self-gating**: [NappletCapability.IDENTITY] is gated here only when the key lives in
 *   Amethyst (a [NostrSignerInternal]). Remote (NIP-46) and external (NIP-55) signers run their own
 *   per-request consent UI, so we defer to them rather than double-prompt. A standing DENY is still
 *   honored, and the applet must still have *declared* `identity`. This is safe only because the
 *   napplet host runs foreground-only, so the signer's prompt appears in the clear context of the
 *   user interacting with that napplet (it can't be fired from the background).
 *
 * Security invariants enforced here (never trusted from the applet): the signing identity is
 * always the host's signer; [NappletRequest.SignEvent] stamps `created_at` from the host clock;
 * a response never contains private key bytes; storage is namespaced per applet coordinate.
 */
class NappletBroker(
    private val signer: NostrSigner,
    private val ledger: NappletPermissionLedger,
    private val consentPrompt: NappletConsentPrompt,
    private val relay: NappletRelayGateway? = null,
    private val storage: NappletStorage? = null,
    private val wallet: NappletWalletGateway? = null,
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

        if (capability !in declared) {
            return NappletResponse.Denied(capability, "This napplet did not declare the '${capability.name.lowercase()}' capability.")
        }

        // A standing denial always wins, even for signer-self-gated capabilities.
        if (ledger.decide(identity, capability) == PermissionDecision.DENY) {
            return NappletResponse.Denied(capability, "Blocked by a standing denial.")
        }

        val authorized =
            when {
                // Remote/external signers run their own per-request consent UI — defer identity to them.
                signerSelfGates(capability) -> true
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

    /** Identity ops are gated by us only when we hold the key; remote/external signers gate themselves. */
    private fun signerSelfGates(capability: NappletCapability): Boolean = capability == NappletCapability.IDENTITY && signer !is NostrSignerInternal

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
            is NappletRequest.GetPublicKey -> NappletResponse.PublicKey(signer.pubKey)

            is NappletRequest.SignEvent -> {
                // created_at comes from the host, never the applet, so it cannot backdate.
                val signed: Event = signer.sign(TimeUtils.now(), request.kind, request.tags, request.content)
                NappletResponse.SignedEvent(signed)
            }

            is NappletRequest.Nip04Encrypt ->
                NappletResponse.Text(signer.nip04Encrypt(request.plaintext, request.peerPubKey))

            is NappletRequest.Nip04Decrypt ->
                NappletResponse.Text(signer.nip04Decrypt(request.ciphertext, request.peerPubKey))

            is NappletRequest.Nip44Encrypt ->
                NappletResponse.Text(signer.nip44Encrypt(request.plaintext, request.peerPubKey))

            is NappletRequest.Nip44Decrypt ->
                NappletResponse.Text(signer.nip44Decrypt(request.ciphertext, request.peerPubKey))

            is NappletRequest.Publish -> publish(request.event)

            is NappletRequest.QueryEvents -> {
                val gateway = relay ?: return NappletResponse.Unsupported("query")
                NappletResponse.Events(gateway.query(request.filter))
            }

            is NappletRequest.StorageGet -> {
                val store = storage ?: return NappletResponse.Unsupported("storage.get")
                NappletResponse.StorageValue(store.get(identity.coordinate, request.key))
            }

            is NappletRequest.StorageSet -> {
                val store = storage ?: return NappletResponse.Unsupported("storage.set")
                store.set(identity.coordinate, request.key, request.value)
                NappletResponse.Done
            }

            is NappletRequest.StorageRemove -> {
                val store = storage ?: return NappletResponse.Unsupported("storage.remove")
                store.remove(identity.coordinate, request.key)
                NappletResponse.Done
            }

            is NappletRequest.PayInvoice -> {
                val gateway = wallet ?: return NappletResponse.Unsupported("payInvoice")
                NappletResponse.Paid(gateway.payInvoice(request.invoice))
            }
        }

    private suspend fun publish(event: Event): NappletResponse {
        val gateway = relay ?: return NappletResponse.Unsupported("publish")

        // An applet may only publish as the active user, and only validly-signed events.
        if (event.pubKey != signer.pubKey) {
            return NappletResponse.Failed("Refusing to publish an event for a different identity.")
        }
        if (!event.verify()) {
            return NappletResponse.Failed("Refusing to publish an event with an invalid signature.")
        }

        return NappletResponse.Published(gateway.publish(event))
    }
}
