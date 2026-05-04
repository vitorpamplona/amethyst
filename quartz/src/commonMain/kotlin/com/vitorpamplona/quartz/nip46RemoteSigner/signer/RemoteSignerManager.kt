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
import com.vitorpamplona.quartz.utils.cache.LargeCache
import com.vitorpamplona.quartz.utils.tryAndWait
import kotlinx.coroutines.delay
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

class RemoteSignerManager(
    val timeout: Long = 65_000,
    val client: INostrClient,
    val signer: NostrSignerInternal,
    val remoteKey: String,
    val relayList: Set<NormalizedRelayUrl>,
    val maxRetries: Int = 1,
) {
    private val awaitingRequests = LargeCache<String, Continuation<BunkerResponse>>()

    suspend fun newResponse(responseEvent: NostrConnectEvent) {
        val decryptedJson = signer.decrypt(responseEvent.content, remoteKey)
        val bunkerResponse = OptimizedJsonMapper.fromJsonTo<BunkerResponse>(decryptedJson)
        awaitingRequests.get(bunkerResponse.id)?.resume(bunkerResponse)
    }

    /**
     * Launches the signer, waits and parses the result.
     *
     * Builds the request once and republishes the same event on retry to ensure
     * the bunker's response (keyed by request ID) can always be matched.
     *
     * @param bunkerRequestBuilder The BunkerRequest to be sent.
     * @param parser A function that parses the BunkerResponse into a [SignerResult.RequestAddressed<T>].
     * @return The result after parsing the BunkerResponse using the provided parser.
     */
    suspend fun <T : IResult> launchWaitAndParse(
        bunkerRequestBuilder: () -> BunkerRequest,
        parser: (response: BunkerResponse) -> SignerResult.RequestAddressed<T>,
    ): SignerResult.RequestAddressed<T> {
        val request = bunkerRequestBuilder()
        val event =
            NostrConnectEvent.create(
                message = request,
                remoteKey = remoteKey,
                signer = signer,
            )

        var attempt = 0
        while (true) {
            val result =
                tryAndWait(timeout) { continuation ->
                    continuation.invokeOnCancellation {
                        awaitingRequests.remove(request.id)
                    }

                    awaitingRequests.put(request.id, continuation)

                    client.publish(event, relayList = relayList)
                }

            when {
                result != null -> {
                    return parser(result)
                }

                attempt >= maxRetries -> {
                    return SignerResult.RequestAddressed.TimedOut()
                }

                else -> {
                    attempt++
                    delay(2_000L)
                }
            }
        }
    }
}
