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
package com.vitorpamplona.quartz.nip46RemoteSigner.server

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestGetPublicKey
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestGetRelays
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip04Decrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip04Encrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Decrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Encrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestPing
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseAck
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseDecrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseEncrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseError
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseGetRelays
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponsePong
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponsePublicKey
import com.vitorpamplona.quartz.nip46RemoteSigner.ReadWrite

/**
 * The signer/bunker side of NIP-46: turns a decrypted [BunkerRequest] from a
 * client into the [BunkerResponse] the client expects, performing the actual
 * crypto with the user's own [signer].
 *
 * This is the "NIP-46 processor" — it is deliberately signer-agnostic: [signer]
 * can be a local keypair ([NostrSignerInternal]) or an external NIP-55 app
 * ([NostrSignerExternal]), and every request is fulfilled through the same
 * [NostrSigner] surface (`sign`, `nip04/44Encrypt/Decrypt`). Whichever signer
 * the user logged in with is the one that ultimately performs the work.
 *
 * Authorization is delegated to [authorizer]: signing/encryption/decryption are
 * gated, while public/harmless reads (`get_public_key`, `ping`, `get_relays`)
 * always succeed. All failures — decryption, authorization, an unsupported
 * method, or an exception from the signer — are turned into a
 * [BunkerResponseError] carrying the request id, so the client always gets a
 * reply it can correlate.
 *
 * Pairs with [NostrConnectSignerService], which subscribes to the relays,
 * decrypts each kind-24133 request, calls [process], and publishes the reply.
 */
class BunkerRequestProcessor(
    val signer: NostrSigner,
    val relays: suspend () -> Set<NormalizedRelayUrl>,
    val authorizer: Nip46RequestAuthorizer,
) {
    /**
     * Fulfils a single decrypted [request] sent by [clientPubKey], returning the
     * response to encrypt and send back. Never throws — signer/authorizer errors
     * are captured as [BunkerResponseError].
     */
    suspend fun process(
        clientPubKey: HexKey,
        request: BunkerRequest,
    ): BunkerResponse =
        try {
            when (request) {
                is BunkerRequestConnect ->
                    when (val decision = authorizer.onConnect(clientPubKey, request)) {
                        is Nip46ConnectDecision.Accept -> BunkerResponse(request.id, decision.ackSecret, null)
                        is Nip46ConnectDecision.Reject -> BunkerResponseError(request.id, decision.reason)
                    }

                is BunkerRequestGetPublicKey -> BunkerResponsePublicKey(request.id, signer.pubKey)

                is BunkerRequestPing -> BunkerResponsePong(request.id)

                is BunkerRequestGetRelays ->
                    BunkerResponseGetRelays(request.id, relays().associate { it.url to ReadWrite(read = true, write = true) })

                is BunkerRequestSign ->
                    ifAuthorized(clientPubKey, request) {
                        val signed = signer.sign<Event>(request.event.createdAt, request.event.kind, request.event.tags, request.event.content)
                        BunkerResponseEvent(request.id, signed)
                    }

                is BunkerRequestNip04Encrypt ->
                    ifAuthorized(clientPubKey, request) {
                        BunkerResponseEncrypt(request.id, signer.nip04Encrypt(request.message, request.pubKey))
                    }

                is BunkerRequestNip04Decrypt ->
                    ifAuthorized(clientPubKey, request) {
                        BunkerResponseDecrypt(request.id, signer.nip04Decrypt(request.ciphertext, request.pubKey))
                    }

                is BunkerRequestNip44Encrypt ->
                    ifAuthorized(clientPubKey, request) {
                        BunkerResponseEncrypt(request.id, signer.nip44Encrypt(request.message, request.pubKey))
                    }

                is BunkerRequestNip44Decrypt ->
                    ifAuthorized(clientPubKey, request) {
                        BunkerResponseDecrypt(request.id, signer.nip44Decrypt(request.ciphertext, request.pubKey))
                    }

                else ->
                    when (request.method) {
                        METHOD_LOGOUT -> {
                            authorizer.onLogout(clientPubKey)
                            BunkerResponseAck(request.id)
                        }
                        else -> BunkerResponseError(request.id, "unsupported method: ${request.method}")
                    }
            }
        } catch (e: Exception) {
            BunkerResponseError(request.id, "${e::class.simpleName}: ${e.message}")
        }

    private suspend inline fun ifAuthorized(
        clientPubKey: HexKey,
        request: BunkerRequest,
        block: () -> BunkerResponse,
    ): BunkerResponse =
        if (authorizer.authorize(clientPubKey, request)) {
            block()
        } else {
            BunkerResponseError(request.id, ERROR_UNAUTHORIZED)
        }

    companion object {
        /** Ack result for a `connect` request with no secret to echo (mirrors [BunkerResponseAck.RESULT]). */
        const val ACK: String = "ack"

        /** Error result returned when [Nip46RequestAuthorizer.authorize] denies a request. */
        const val ERROR_UNAUTHORIZED: String = "unauthorized"

        /** NIP-46 `logout` method name — the client asks to be disconnected. */
        const val METHOD_LOGOUT: String = "logout"
    }
}
