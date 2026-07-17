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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.StaticSubscription
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerClientMetadata
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestGetPublicKey
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip04Decrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip04Encrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Decrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Encrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestPing
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class NostrSignerRemote(
    val signer: NostrSignerInternal,
    val remotePubkey: HexKey,
    val relays: Set<NormalizedRelayUrl>,
    val client: INostrClient,
    val permissions: String? = null,
    val secret: String? = null,
    /**
     * Optional NIP-46 client metadata (name/url/image) advertised on the
     * `connect` request so the bunker can show who is asking to connect.
     */
    val clientMetadata: BunkerClientMetadata? = null,
    /**
     * Invoked with the authorization URL when the bunker answers with a NIP-46
     * `auth_url` challenge. Surface it (open a browser / print it); the pending
     * request keeps waiting for the real response.
     */
    val onAuthUrl: ((String) -> Unit)? = null,
) : NostrSigner(signer.pubKey) {
    // The user's real identity, resolved from the bunker via `get_public_key`. The constructor
    // `signer` is the ephemeral NIP-46 TRANSPORT keypair, NOT the user — so until this is bound,
    // `pubKey` falls back to the transport key. Every self-encryption / self-authorship site keys
    // off `pubKey`, so leaving it as the transport key silently breaks private NIP-51 lists, NIP-37
    // drafts, Concord list decryption, etc. for bunker accounts. Bound either eagerly from a saved
    // identity ([bindUserPubkey]) or lazily by the first [getPublicKey] call.
    private var resolvedUserPubkey: HexKey? = null

    /**
     * The account identity. Returns the bunker-resolved user key once known, else the transport
     * key. Internal NIP-46 transport (the response-subscription `p` filter, request addressing)
     * deliberately uses `signer.pubKey`/`remotePubkey` directly and is unaffected by this.
     */
    override val pubKey: HexKey
        get() = resolvedUserPubkey ?: signer.pubKey

    /**
     * Bind the user's identity pubkey when it is already known (e.g. a persisted bunker account
     * reloaded from disk, or the CLI's stored identity) so `pubKey` is correct without a
     * `get_public_key` round-trip.
     */
    fun bindUserPubkey(userPubkey: HexKey) {
        resolvedUserPubkey = userPubkey
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val manager =
        RemoteSignerManager(
            signer = signer,
            remoteKey = remotePubkey,
            relayList = relays,
            client = client,
            onAuthUrl = onAuthUrl,
        )

    val subscription =
        StaticSubscription(
            client,
            relays.associateWith {
                listOf(
                    Filter(
                        kinds = listOf(NostrConnectEvent.KIND),
                        tags = mapOf("p" to listOf(signer.pubKey)),
                    ),
                )
            },
        ) { event ->
            if (event is NostrConnectEvent) {
                scope.launch {
                    // Incoming bunker responses come straight off the relay and are
                    // decrypted here on a fire-and-forget launch whose scope carries no
                    // CoroutineExceptionHandler. A malformed/hostile event (or a benign
                    // SignerExceptions from decryption) must not escape and crash the app.
                    try {
                        manager.newResponse(event)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: SignerExceptions) {
                        Log.d("NostrSignerRemote") { "Could not decrypt bunker response ${event.id}: ${e.message}" }
                    } catch (e: Exception) {
                        Log.w("NostrSignerRemote", "Failed to process bunker response ${event.id}", e)
                    }
                }
            }
        }

    fun openSubscription() {
        subscription.refresh()
    }

    fun closeSubscription() {
        subscription.close()
        scope.cancel()
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
                parser = SignResponse::parse,
            )

        if (result is SignerResult.RequestAddressed.Successful<SignResult>) {
            @Suppress("UNCHECKED_CAST")
            (result.result.event as? T)?.let {
                return it
            }
        }

        throw convertExceptions("Could not sign", result)
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
                parser = Nip04EncryptResponse::parse,
            )

        if (result is SignerResult.RequestAddressed.Successful<EncryptionResult>) {
            return result.result.ciphertext
        }

        throw convertExceptions("Could not encrypt", result)
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
                parser = Nip04DecryptResponse::parse,
            )

        if (result is SignerResult.RequestAddressed.Successful<DecryptionResult>) {
            return result.result.plaintext
        }

        throw convertExceptions("Could not decrypt", result)
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
                parser = Nip44EncryptResponse::parse,
            )

        if (result is SignerResult.RequestAddressed.Successful<EncryptionResult>) {
            return result.result.ciphertext
        }

        throw convertExceptions("Could not encrypt", result)
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
                parser = Nip44DecryptResponse::parse,
            )

        if (result is SignerResult.RequestAddressed.Successful<DecryptionResult>) {
            return result.result.plaintext
        }

        throw convertExceptions("Could not decrypt", result)
    }

    suspend fun ping(): String {
        val result =
            manager.launchWaitAndParse(
                bunkerRequestBuilder = {
                    BunkerRequestPing()
                },
                parser = PingResponse::parse,
            )

        if (result is SignerResult.RequestAddressed.Successful<PingResult>) {
            return result.result.pong
        }

        throw convertExceptions("Could not ping", result)
    }

    suspend fun connect() {
        val result =
            manager.launchWaitAndParse(
                bunkerRequestBuilder = {
                    BunkerRequestConnect(
                        remoteKey = remotePubkey,
                        permissions = permissions,
                        secret = secret,
                        clientMetadata = clientMetadata,
                    )
                },
                parser = ConnectResponse::parse,
            )

        if (result is SignerResult.RequestAddressed.Successful<ConnectResult>) {
            return
        }

        throw convertExceptions("Could not connect", result)
    }

    suspend fun getPublicKey(): HexKey {
        val result =
            manager.launchWaitAndParse(
                bunkerRequestBuilder = {
                    BunkerRequestGetPublicKey()
                },
                parser = PubKeyResponse::parse,
            )

        if (result is SignerResult.RequestAddressed.Successful<PublicKeyResult>) {
            // Cache it so `pubKey` reflects the real identity from here on (self-encryption etc.).
            resolvedUserPubkey = result.result.pubkey
            return result.result.pubkey
        }

        throw convertExceptions("Could not get public key", result)
    }

    override suspend fun decryptZapEvent(event: LnZapRequestEvent): LnZapPrivateEvent {
        TODO("Not yet implemented")
    }

    override suspend fun deriveKey(nonce: HexKey): HexKey {
        TODO("Not yet implemented")
    }

    /**
     * NIP-BC `sign_psbt` over NIP-46. The bunker-side command is not yet
     * standardized/shipped, so this is intentionally unsupported until the
     * remote-signer ecosystem catches up.
     */
    override suspend fun signPsbt(psbtHex: String): String =
        throw SignerExceptions.UnsupportedMethodException(
            "Remote (NIP-46) signers do not support sign_psbt yet",
        )

    override fun hasForegroundSupport(): Boolean = true

    fun convertExceptions(
        title: String,
        result: SignerResult.RequestAddressed<*>,
    ): Exception =
        when (result) {
            is SignerResult.RequestAddressed.Successful<*> -> IllegalStateException("$title: This should not happen. There is a bug on Quartz.")
            is SignerResult.RequestAddressed.ReceivedButCouldNotParseEventFromResult<*> -> IllegalStateException("$title: Failed to parse event: ${result.eventJson}.")
            is SignerResult.RequestAddressed.ReceivedButCouldNotVerifyResultingEvent<*> -> IllegalStateException("$title: Failed to verify event: ${result.invalidEvent.toJson()}.")
            is SignerResult.RequestAddressed.ReceivedButCouldNotPerform<*> -> SignerExceptions.CouldNotPerformException("$title: ${result.message}")
            is SignerResult.RequestAddressed.Rejected<*> -> SignerExceptions.ManuallyUnauthorizedException("$title: User has rejected the request.")
            is SignerResult.RequestAddressed.TimedOut<*> -> SignerExceptions.TimedOutException("$title: User didn't accept or reject in time.")
        }

    companion object {
        fun fromBunkerUri(
            bunkerUri: String,
            signer: NostrSignerInternal,
            client: INostrClient,
            permissions: String? = null,
            clientMetadata: BunkerClientMetadata? = null,
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
                clientMetadata = clientMetadata,
            )
        }
    }
}
