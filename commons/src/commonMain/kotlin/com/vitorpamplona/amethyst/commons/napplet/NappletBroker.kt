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

import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.napplet.permissions.PermissionDecision
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletResponse
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.coroutines.cancellation.CancellationException

/**
 * The trust boundary, expressed as code. The broker is the **only** component that holds a
 * [NostrSigner] and turns an untrusted napplet's [NappletRequest] into a [NappletResponse],
 * gating every dangerous operation through the [ledger] (and, when no standing grant exists,
 * the [consentPrompt]). On Android it runs in the main process behind an IPC boundary; the
 * applet's process never sees this object, the signer, or any key material.
 *
 * Security invariants enforced here (not trusted from the applet):
 * - The signing identity is always the host's signer — an applet can never sign or publish as
 *   someone else, and [NappletRequest.SignEvent] sets `created_at` from the host clock.
 * - A response never contains private key bytes (see [NappletResponse]).
 * - Every request is authorized against the ledger before it touches the signer or relays.
 */
class NappletBroker(
    private val signer: NostrSigner,
    private val ledger: NappletPermissionLedger,
    private val consentPrompt: NappletConsentPrompt,
    private val relay: NappletRelayGateway? = null,
) {
    /**
     * Authorizes and runs [request] on behalf of [identity]. Re-throws
     * [CancellationException] (e.g. the host tore the applet down mid-prompt) and converts any
     * other failure into [NappletResponse.Failed] — a thrown exception must never cross back to
     * the applet process carrying host internals.
     */
    suspend fun handle(
        identity: NappletIdentity,
        request: NappletRequest,
    ): NappletResponse {
        val capability = request.capability

        val authorized =
            when (ledger.decide(identity, capability)) {
                PermissionDecision.ALLOW -> true
                PermissionDecision.DENY ->
                    return NappletResponse.Denied(capability, "Blocked by a standing denial.")
                PermissionDecision.ASK -> {
                    val grant = consentPrompt.request(identity, capability, request)
                    ledger.record(identity, capability, grant)
                    grant.allowsExecution
                }
            }

        if (!authorized) return NappletResponse.Denied(capability, "The user declined.")

        return try {
            execute(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NappletResponse.Failed(e.message ?: e::class.simpleName ?: "Unknown error")
        }
    }

    private suspend fun execute(request: NappletRequest): NappletResponse =
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
