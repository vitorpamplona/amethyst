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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.req
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestGetPublicKey
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip04Decrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip04Encrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Decrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Encrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestPing
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.utils.cache.LargeCache
import com.vitorpamplona.quartz.utils.tryAndWait
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

class NostrSignerRemote(
    val signer: NostrSignerInternal,
    val remotePubkey: HexKey,
    val relays: Set<NormalizedRelayUrl>,
    val client: INostrClient,
    val permissions: String? = null,
    val secret: String? = null,
) : NostrSigner(signer.pubKey) {
    private val timeout = 30_000L
    private val awaitingRequests = LargeCache<String, Continuation<BunkerResponse>>()

    val subscription =
        client.req(
            relays = relays.toList(),
            filter =
                Filter(
                    kinds = listOf(NostrConnectEvent.KIND),
                    tags = mapOf("p" to listOf(signer.pubKey)),
                ),
        ) { event ->
            val message = signer.signerSync.nip44Decrypt(event.content, remotePubkey)
            val bunkerResponse = OptimizedJsonMapper.fromJsonTo<BunkerResponse>(message)

            awaitingRequests.get(bunkerResponse.id)?.resume(bunkerResponse)
            awaitingRequests.remove(bunkerResponse.id)
        }

    fun openSubscription() {
        subscription.updateFilter()
    }

    fun closeSubscription() {
        subscription.close()
    }

    override fun isWriteable(): Boolean = true

    override suspend fun <T : Event> sign(
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): T {
        val template =
            EventTemplate<Event>(
                createdAt = createdAt,
                kind = kind,
                tags = tags,
                content = content,
            )

        val request =
            BunkerRequestSign(
                event = template,
            )

        val event =
            NostrConnectEvent.create(
                message = request,
                remoteKey = remotePubkey,
                signer = signer,
            )

        client.send(event, relayList = relays)

        TODO("Not yet implemented")
    }

    override suspend fun nip04Encrypt(
        plaintext: String,
        toPublicKey: HexKey,
    ): String {
        val event =
            NostrConnectEvent.create(
                message =
                    BunkerRequestNip04Encrypt(
                        message = plaintext,
                        pubKey = toPublicKey,
                    ),
                remoteKey = remotePubkey,
                signer = signer,
            )
        client.send(event, relayList = relays)
        TODO("Not yet implemented")
    }

    override suspend fun nip04Decrypt(
        ciphertext: String,
        fromPublicKey: HexKey,
    ): String {
        val event =
            NostrConnectEvent.create(
                message =
                    BunkerRequestNip04Decrypt(
                        ciphertext = ciphertext,
                        pubKey = fromPublicKey,
                    ),
                remoteKey = remotePubkey,
                signer = signer,
            )
        client.send(event, relayList = relays)
        TODO("Not yet implemented")
    }

    override suspend fun nip44Encrypt(
        plaintext: String,
        toPublicKey: HexKey,
    ): String {
        val event =
            NostrConnectEvent.create(
                message =
                    BunkerRequestNip44Encrypt(
                        message = plaintext,
                        pubKey = toPublicKey,
                    ),
                remoteKey = remotePubkey,
                signer = signer,
            )
        client.send(event, relayList = relays)
        TODO("Not yet implemented")
    }

    override suspend fun nip44Decrypt(
        ciphertext: String,
        fromPublicKey: HexKey,
    ): String {
        val event =
            NostrConnectEvent.create(
                message =
                    BunkerRequestNip44Decrypt(
                        ciphertext = ciphertext,
                        pubKey = fromPublicKey,
                    ),
                remoteKey = remotePubkey,
                signer = signer,
            )
        client.send(event, relayList = relays)
        TODO("Not yet implemented")
    }

    suspend fun ping(): String {
        val event =
            NostrConnectEvent.create(
                message = BunkerRequestPing(),
                remoteKey = remotePubkey,
                signer = signer,
            )
        client.send(event, relayList = relays)
        TODO("Not yet implemented")
    }

    suspend fun connect(): BunkerResponse? {
        val request =
            BunkerRequestConnect(
                remoteKey = remotePubkey,
                permissions = permissions,
                secret = secret,
            )
        val event =
            NostrConnectEvent.create(
                message = request,
                remoteKey = remotePubkey,
                signer = signer,
            )

        val result =
            tryAndWait(timeout) { continuation ->
                continuation.invokeOnCancellation {
                    awaitingRequests.remove(request.id)
                }

                awaitingRequests.put(request.id, continuation)

                client.send(event, relayList = relays)
            }

        return result
    }

    suspend fun getPublicKey(): String {
        val event =
            NostrConnectEvent.create(
                message = BunkerRequestGetPublicKey(),
                remoteKey = remotePubkey,
                signer = signer,
            )
        client.send(event, relayList = relays)
        TODO("Not yet implemented")
    }

    override suspend fun decryptZapEvent(event: LnZapRequestEvent): LnZapPrivateEvent {
        TODO("Not yet implemented")
    }

    override suspend fun deriveKey(nonce: HexKey): HexKey {
        TODO("Not yet implemented")
    }

    override fun hasForegroundSupport(): Boolean = true
}
