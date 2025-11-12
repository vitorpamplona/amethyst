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
import com.vitorpamplona.quartz.nip01Core.crypto.verify
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
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseDecrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseEncrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseError
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponsePong
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponsePublicKey
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.utils.Hex

class NostrSignerRemote(
    val signer: NostrSignerInternal,
    val remotePubkey: HexKey,
    val relays: Set<NormalizedRelayUrl>,
    val client: INostrClient,
    val permissions: String? = null,
    val secret: String? = null,
) : NostrSigner(signer.pubKey) {
    private val manager =
        RemoteSignerManager(
            signer = signer,
            remoteKey = remotePubkey,
            relayList = relays,
            client = client,
        )

    val subscription =
        client.req(
            relays = relays.toList(),
            filter =
                Filter(
                    kinds = listOf(NostrConnectEvent.KIND),
                    tags = mapOf("p" to listOf(signer.pubKey)),
                ),
        ) { event ->
            manager.newResponse(event)
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
        val result =
            manager.launchWaitAndParse(
                bunkerRequestBuilder = {
                    val template =
                        EventTemplate<Event>(
                            createdAt = createdAt,
                            kind = kind,
                            tags = tags,
                            content = content,
                        )

                    BunkerRequestSign(
                        event = template,
                    )
                },
                parser = { response ->
                    if (response is BunkerResponseEvent) {
                        if (!response.event.verify()) {
                            SignerResult.RequestAddressed.ReceivedButCouldNotVerifyResultingEvent(response.event)
                        } else {
                            SignerResult.RequestAddressed.Successful(SignResult(response.event))
                        }
                    } else if (response is BunkerResponseError) {
                        SignerResult.RequestAddressed.Rejected()
                    } else {
                        SignerResult.RequestAddressed.ReceivedButCouldNotPerform()
                    }
                },
            )

        if (result is SignerResult.RequestAddressed.Successful<SignResult>) {
            (result.result.event as? T)?.let {
                return it
            }
        }

        throw Exception("Could not sign")
    }

    override suspend fun nip04Encrypt(
        plaintext: String,
        toPublicKey: HexKey,
    ): String {
        val result =
            manager.launchWaitAndParse(
                bunkerRequestBuilder = {
                    BunkerRequestNip04Encrypt(
                        message = plaintext,
                        pubKey = toPublicKey,
                    )
                },
                parser = { response ->
                    when (response) {
                        is BunkerResponseEncrypt -> {
                            SignerResult.RequestAddressed.Successful(EncryptionResult(response.ciphertext))
                        }

                        is BunkerResponseError -> {
                            SignerResult.RequestAddressed.Rejected()
                        }

                        else -> {
                            SignerResult.RequestAddressed.ReceivedButCouldNotPerform()
                        }
                    }
                },
            )

        if (result is SignerResult.RequestAddressed.Successful<EncryptionResult>) {
            return result.result.ciphertext
        }

        throw Exception("Could not encrypt")
    }

    override suspend fun nip04Decrypt(
        ciphertext: String,
        fromPublicKey: HexKey,
    ): String {
        val result =
            manager.launchWaitAndParse(
                bunkerRequestBuilder = {
                    BunkerRequestNip04Decrypt(
                        ciphertext = ciphertext,
                        pubKey = fromPublicKey,
                    )
                },
                parser = { response ->
                    when (response) {
                        is BunkerResponseDecrypt -> {
                            SignerResult.RequestAddressed.Successful(DecryptionResult(response.plaintext))
                        }
                        is BunkerResponseError -> {
                            SignerResult.RequestAddressed.Rejected()
                        }
                        else -> {
                            SignerResult.RequestAddressed.ReceivedButCouldNotPerform()
                        }
                    }
                },
            )

        if (result is SignerResult.RequestAddressed.Successful<DecryptionResult>) {
            return result.result.plaintext
        }

        throw Exception("Could not decrypt")
    }

    override suspend fun nip44Encrypt(
        plaintext: String,
        toPublicKey: HexKey,
    ): String {
        val result =
            manager.launchWaitAndParse(
                bunkerRequestBuilder = {
                    BunkerRequestNip44Encrypt(
                        message = plaintext,
                        pubKey = toPublicKey,
                    )
                },
                parser = { response ->
                    when (response) {
                        is BunkerResponseEncrypt -> {
                            SignerResult.RequestAddressed.Successful(EncryptionResult(response.ciphertext))
                        }

                        is BunkerResponseError -> {
                            SignerResult.RequestAddressed.Rejected()
                        }

                        else -> {
                            SignerResult.RequestAddressed.ReceivedButCouldNotPerform()
                        }
                    }
                },
            )

        if (result is SignerResult.RequestAddressed.Successful<EncryptionResult>) {
            return result.result.ciphertext
        }

        throw Exception("Could not encrypt")
    }

    override suspend fun nip44Decrypt(
        ciphertext: String,
        fromPublicKey: HexKey,
    ): String {
        val result =
            manager.launchWaitAndParse(
                bunkerRequestBuilder = {
                    BunkerRequestNip44Decrypt(
                        ciphertext = ciphertext,
                        pubKey = fromPublicKey,
                    )
                },
                parser = { response ->
                    when (response) {
                        is BunkerResponseDecrypt -> {
                            SignerResult.RequestAddressed.Successful(DecryptionResult(response.plaintext))
                        }

                        is BunkerResponseError -> {
                            SignerResult.RequestAddressed.Rejected()
                        }

                        else -> {
                            SignerResult.RequestAddressed.ReceivedButCouldNotPerform()
                        }
                    }
                },
            )

        if (result is SignerResult.RequestAddressed.Successful<DecryptionResult>) {
            return result.result.plaintext
        }

        throw Exception("Could not decrypt")
    }

    suspend fun ping(): String {
        val result =
            manager.launchWaitAndParse(
                bunkerRequestBuilder = {
                    BunkerRequestPing()
                },
                parser = { response ->
                    when (response) {
                        is BunkerResponsePong -> {
                            SignerResult.RequestAddressed.Successful(PingResult(response.id))
                        }

                        is BunkerResponseError -> {
                            SignerResult.RequestAddressed.Rejected()
                        }

                        else -> {
                            SignerResult.RequestAddressed.ReceivedButCouldNotPerform()
                        }
                    }
                },
            )

        if (result is SignerResult.RequestAddressed.Successful<PingResult>) {
            return result.result.pong
        }

        throw Exception("Could not ping")
    }

    suspend fun connect(): HexKey {
        val result =
            manager.launchWaitAndParse(
                bunkerRequestBuilder = {
                    BunkerRequestConnect(
                        remoteKey = remotePubkey,
                        permissions = permissions,
                        secret = secret,
                    )
                },
                parser = { response ->
                    when (response) {
                        is BunkerResponsePublicKey -> {
                            SignerResult.RequestAddressed.Successful(PublicKeyResult(response.pubkey))
                        }

                        is BunkerResponseError -> {
                            SignerResult.RequestAddressed.Rejected()
                        }

                        else -> {
                            SignerResult.RequestAddressed.ReceivedButCouldNotPerform()
                        }
                    }
                },
            )

        if (result is SignerResult.RequestAddressed.Successful<PublicKeyResult>) {
            return result.result.pubkey
        }

        throw Exception("Could not connect")
    }

    suspend fun getPublicKey(): HexKey {
        val result =
            manager.launchWaitAndParse(
                bunkerRequestBuilder = {
                    BunkerRequestGetPublicKey()
                },
                parser = { response ->
                    when (response) {
                        is BunkerResponsePublicKey -> {
                            SignerResult.RequestAddressed.Successful(PublicKeyResult(response.pubkey))
                        }

                        is BunkerResponseError -> {
                            SignerResult.RequestAddressed.Rejected()
                        }

                        else -> {
                            SignerResult.RequestAddressed.ReceivedButCouldNotPerform()
                        }
                    }
                },
            )

        if (result is SignerResult.RequestAddressed.Successful<PublicKeyResult>) {
            return result.result.pubkey
        }

        throw Exception("Could not get public key")
    }

    override suspend fun decryptZapEvent(event: LnZapRequestEvent): LnZapPrivateEvent {
        TODO("Not yet implemented")
    }

    override suspend fun deriveKey(nonce: HexKey): HexKey {
        TODO("Not yet implemented")
    }

    override fun hasForegroundSupport(): Boolean = true

    companion object {
        fun fromBunkerUri(
            bunkerUri: String,
            signer: NostrSignerInternal,
            client: INostrClient,
            permissions: String? = null,
        ): NostrSignerRemote {
            if (!bunkerUri.startsWith("bunker://")) throw Exception("Invalid bunker uri")
            val splitData = bunkerUri.split("?")
            val remotePubkey = splitData[0].removePrefix("bunker://")
            if (!Hex.isHex(remotePubkey)) throw Exception("Invalid pubkey in bunker uri")
            val params = splitData[1].split("&")
            val relays = mutableSetOf<NormalizedRelayUrl>()
            var secret: String? = null
            for (param in params) {
                val splitParam = param.split("=")
                if (splitParam.size < 2) continue
                if (splitParam.first() == "relay") {
                    relays.add(NormalizedRelayUrl(splitParam[1]))
                }
                if (splitParam.first() == "secret") {
                    secret = splitParam[1]
                }
            }
            return NostrSignerRemote(
                signer = signer,
                remotePubkey = remotePubkey,
                relays = relays,
                client = client,
                permissions = permissions,
                secret = secret,
            )
        }
    }
}
