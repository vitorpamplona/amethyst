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
package com.vitorpamplona.amethyst.commons.domain.nip46

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.StaticSubscription
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerMessage
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseAck
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.signer.NostrSignerRemote
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.SecureRandom
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

object NostrConnectLoginUseCase {
    const val NOSTRCONNECT_TIMEOUT_MS = 120_000L

    data class NostrConnectUri(
        val uri: String,
        val ephemeralKeyPair: KeyPair,
        val ephemeralSigner: NostrSignerInternal,
        val relays: Set<NormalizedRelayUrl>,
        val secret: String,
    )

    data class ConnectRequestData(
        val requestId: String?,
        val signerPubkey: HexKey,
        val userPubkey: HexKey,
    )

    fun generateUri(
        ephemeralKeyPair: KeyPair,
        relays: List<String>,
        appName: String,
    ): NostrConnectUri {
        val ephemeralSigner = NostrSignerInternal(ephemeralKeyPair)
        val ephemeralPubKey = ephemeralKeyPair.pubKey.toHexKey()
        val secret = generateSecret()

        val relayParams = relays.joinToString("&") { "relay=$it" }
        val uri = "nostrconnect://$ephemeralPubKey?$relayParams&secret=$secret&name=$appName"
        val normalizedRelays = relays.map { NormalizedRelayUrl(it) }.toSet()

        return NostrConnectUri(
            uri = uri,
            ephemeralKeyPair = ephemeralKeyPair,
            ephemeralSigner = ephemeralSigner,
            relays = normalizedRelays,
            secret = secret,
        )
    }

    suspend fun awaitAndLogin(
        uriData: NostrConnectUri,
        client: INostrClient,
    ): BunkerLoginResult {
        val connectData =
            waitForConnectRequest(
                ephemeralSigner = uriData.ephemeralSigner,
                ephemeralPubKey = uriData.ephemeralKeyPair.pubKey.toHexKey(),
                relays = uriData.relays,
                expectedSecret = uriData.secret,
                client = client,
            )

        if (connectData.requestId != null) {
            sendAckResponse(uriData.ephemeralSigner, connectData, uriData.relays, client)
        }

        val remoteSigner =
            NostrSignerRemote(
                signer = uriData.ephemeralSigner,
                remotePubkey = connectData.signerPubkey,
                relays = uriData.relays,
                client = client,
            )
        remoteSigner.openSubscription()

        val verifiedPubkey = remoteSigner.getPublicKey()

        return BunkerLoginResult(remoteSigner, verifiedPubkey)
    }

    private suspend fun waitForConnectRequest(
        ephemeralSigner: NostrSignerInternal,
        ephemeralPubKey: HexKey,
        relays: Set<NormalizedRelayUrl>,
        expectedSecret: String,
        client: INostrClient,
    ): ConnectRequestData {
        val deferred = CompletableDeferred<NostrConnectEvent>()

        val subscription =
            StaticSubscription(
                client,
                relays.associateWith {
                    listOf(
                        Filter(
                            kinds = listOf(NostrConnectEvent.KIND),
                            tags = mapOf("p" to listOf(ephemeralPubKey)),
                            since = TimeUtils.now() - 60,
                        ),
                    )
                },
            ) { event ->
                if (event is NostrConnectEvent && !deferred.isCompleted) {
                    deferred.complete(event)
                }
            }

        try {
            val event =
                withTimeout(NOSTRCONNECT_TIMEOUT_MS) {
                    deferred.await()
                }

            val signerPubkey = event.talkingWith(ephemeralSigner.pubKey)
            val decryptedJson = ephemeralSigner.decrypt(event.content, signerPubkey)
            val message = OptimizedJsonMapper.fromJsonTo<BunkerMessage>(decryptedJson)

            return when (message) {
                is BunkerRequest -> {
                    if (message.method != BunkerRequestConnect.METHOD_NAME) {
                        throw Exception("Expected 'connect' method, got '${message.method}'")
                    }

                    val userPubkey =
                        message.params.getOrNull(0)
                            ?: throw Exception("Missing user pubkey in connect request")
                    val receivedSecret = message.params.getOrNull(1)

                    if (receivedSecret != expectedSecret) {
                        throw Exception("Secret mismatch in connect request")
                    }

                    ConnectRequestData(
                        requestId = message.id,
                        signerPubkey = signerPubkey,
                        userPubkey = userPubkey,
                    )
                }

                is BunkerResponse -> {
                    if (message.error != null) {
                        throw Exception("Signer rejected connection: ${message.error}")
                    }

                    val userPubkey =
                        message.result
                            ?.takeIf { it.length == 64 && Hex.isHex(it) }
                            ?: signerPubkey

                    ConnectRequestData(
                        requestId = null,
                        signerPubkey = signerPubkey,
                        userPubkey = userPubkey,
                    )
                }

                else -> {
                    throw Exception("Unexpected NIP-46 message format")
                }
            }
        } finally {
            subscription.close()
        }
    }

    private suspend fun sendAckResponse(
        ephemeralSigner: NostrSignerInternal,
        connectData: ConnectRequestData,
        relays: Set<NormalizedRelayUrl>,
        client: INostrClient,
    ) {
        val ackResponse = BunkerResponseAck(id = connectData.requestId!!)
        val ackEvent =
            NostrConnectEvent.create(
                message = ackResponse,
                remoteKey = connectData.signerPubkey,
                signer = ephemeralSigner,
            )
        client.publish(ackEvent, relays)
    }

    private fun generateSecret(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.toHexKey()
    }
}
