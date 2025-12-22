/**
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
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

class RemoteSignerManager(
    val timeout: Long = 30000,
    val client: INostrClient,
    val signer: NostrSignerInternal,
    val remoteKey: String,
    val relayList: Set<NormalizedRelayUrl>,
) {
    private val awaitingRequests = LargeCache<String, Continuation<BunkerResponse>>()

    fun newResponse(responseEvent: NostrConnectEvent) {
        val bunkerResponse = OptimizedJsonMapper.fromJsonTo<BunkerResponse>(responseEvent.content)
        awaitingRequests.get(bunkerResponse.id)?.resume(bunkerResponse)
    }

    /**
     * Launches the signer, waits and parses the result
     *
     * @param bunkerRequestBuilder The BunkerRequest to be sent.
     * @param parser A function that parses the BunkerResponse into a [SignerResult.RequestAddressed<T>].
     * @return The result after parsing the BunkerResponse using the provided parser.
     *
     * This function uses the [tryAndWait] utility to implement a timeout on the Bunker Request approval.
     * It assigns a unique ID to the request and keeps a continuation to resume once the result is received.
     * If the timeout occurs or the continuation is cancelled, the request ID is cleaned up from [awaitingRequests].
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
        val result =
            tryAndWait(timeout) { continuation ->
                continuation.invokeOnCancellation {
                    awaitingRequests.remove(request.id)
                }

                awaitingRequests.put(request.id, continuation)

                client.send(event, relayList = relayList)
            }

        return when (result) {
            null -> SignerResult.RequestAddressed.TimedOut()
            else -> parser(result)
        }
    }
}
