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
package com.vitorpamplona.quartz.nip46RemoteSigner.signer

import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

class RemoteSignerManager(
    val timeout: Long = 65_000,
    val client: INostrClient,
    val signer: NostrSignerInternal,
    val remoteKey: String,
    val relayList: Set<NormalizedRelayUrl>,
    val maxRetries: Int = 1,
    /**
     * Invoked with the authorization URL when the bunker answers a request with
     * a NIP-46 `auth_url` challenge. The caller should surface it (open a
     * browser / print it) so the user can authorize; the manager keeps waiting
     * for the real response on the same request id.
     */
    val onAuthUrl: ((String) -> Unit)? = null,
) {
    private val pending = LargeCache<String, Channel<BunkerResponse>>()

    suspend fun newResponse(responseEvent: NostrConnectEvent) {
        val decryptedJson = signer.decrypt(responseEvent.content, remoteKey)
        val bunkerResponse = OptimizedJsonMapper.fromJsonTo<BunkerResponse>(decryptedJson)

        // Peek (don't remove): a request may receive an `auth_url` challenge
        // followed by the real response under the same id. The waiting
        // continuation removes the entry in its `finally`.
        val channel = pending.get(bunkerResponse.id)
        if (channel == null) {
            Log.d("NIP46") { "no channel for bunker response id=${bunkerResponse.id} (duplicate, unknown, or late)" }
            return
        }
        channel.trySend(bunkerResponse)
    }

    /**
     * Launches the signer, waits and parses the result.
     *
     * Each retry attempt uses a fresh request id so a late response from a previous
     * attempt cannot resume the current attempt with stale data. The bunker request
     * builder is still called only once per call; the manager rewrites the id per
     * attempt internally.
     *
     * @param bunkerRequestBuilder The BunkerRequest to be sent.
     * @param parser A function that parses the BunkerResponse into a [SignerResult.RequestAddressed<T>].
     * @return The result after parsing the BunkerResponse using the provided parser.
     */
    suspend fun <T : IResult> launchWaitAndParse(
        bunkerRequestBuilder: () -> BunkerRequest,
        parser: (response: BunkerResponse) -> SignerResult.RequestAddressed<T>,
    ): SignerResult.RequestAddressed<T> {
        val template = bunkerRequestBuilder()

        var attempt = 0
        while (true) {
            val attemptRequest =
                BunkerRequest(
                    id = RandomInstance.randomChars(32),
                    method = template.method,
                    params = template.params,
                )
            val event =
                NostrConnectEvent.create(
                    message = attemptRequest,
                    remoteKey = remoteKey,
                    signer = signer,
                )

            // UNLIMITED so an `auth_url` challenge and the follow-up real
            // response (same id) can both be buffered without dropping either.
            val channel = Channel<BunkerResponse>(capacity = Channel.UNLIMITED)
            pending.put(attemptRequest.id, channel)

            var announcedAuthUrl: String? = null
            val response =
                try {
                    client.publish(event, relayList = relayList)
                    withTimeoutOrNull(timeout) {
                        var real: BunkerResponse? = null
                        while (real == null) {
                            val r = channel.receive()
                            if (r.result == BunkerResponse.RESULT_AUTH_URL) {
                                // Surface the auth URL once and keep waiting for the real response.
                                val url = r.error
                                if (url != null && url != announcedAuthUrl) {
                                    announcedAuthUrl = url
                                    onAuthUrl?.invoke(url)
                                }
                            } else {
                                real = r
                            }
                        }
                        real
                    }
                } finally {
                    pending.remove(attemptRequest.id)
                    channel.close()
                }

            when {
                response != null -> return parser(response)
                attempt >= maxRetries -> return SignerResult.RequestAddressed.TimedOut()
                else -> {
                    attempt++
                    delay(2_000L)
                }
            }
        }
    }
}
