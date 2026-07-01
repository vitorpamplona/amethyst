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
package com.vitorpamplona.amethyst.cli.commands

import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Identity
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.commons.defaults.DefaultNIP65RelaySet
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient

/**
 * NIP-46 NostrConnect (client-initiated) flow helpers.
 *
 * Parsing/encoding the `nostrconnect://` offer is shared between the client
 * (`amy login --nostrconnect`, [login]) and the signer (`amy bunker connect`,
 * which only parses). The signer side lives in [BunkerCommand].
 */
object NostrConnect {
    private const val NOSTRCONNECT_SCHEME = "nostrconnect://"

    data class Offer(
        val clientPubkey: String,
        val relays: Set<NormalizedRelayUrl>,
        val secret: String,
        val name: String?,
    )

    /** Parse `nostrconnect://<client-pubkey>?relay=…&secret=…&name=…` (percent-decoded). */
    fun parseOffer(uri: String): Offer? {
        if (!uri.startsWith(NOSTRCONNECT_SCHEME)) return null
        val parts = uri.removePrefix(NOSTRCONNECT_SCHEME).split("?", limit = 2)
        val clientPubkey = parts[0].lowercase()
        if (clientPubkey.length != 64 || clientPubkey.any { it !in "0123456789abcdef" }) return null
        val relays = mutableSetOf<NormalizedRelayUrl>()
        var secret: String? = null
        var name: String? = null
        parts.getOrNull(1)?.split("&")?.forEach { param ->
            val kv = param.split("=", limit = 2)
            if (kv.size < 2) return@forEach
            val value = java.net.URLDecoder.decode(kv[1], "UTF-8")
            when (kv[0]) {
                "relay" -> RelayUrlNormalizer.normalizeOrNull(value)?.let { relays.add(it) }
                "secret" -> secret = value
                "name" -> name = value
            }
        }
        if (secret == null) return null
        return Offer(clientPubkey, relays, secret!!, name)
    }

    private fun buildOffer(
        clientPubkey: String,
        relays: Set<NormalizedRelayUrl>,
        secret: String,
        name: String?,
    ): String {
        val enc = { s: String -> java.net.URLEncoder.encode(s, "UTF-8") }
        return buildString {
            append(NOSTRCONNECT_SCHEME).append(clientPubkey)
            append("?").append(relays.joinToString("&") { "relay=${enc(it.url)}" })
            append("&secret=").append(enc(secret))
            if (name != null) append("&name=").append(enc(name))
        }
    }

    /**
     * `amy login --nostrconnect [--relay URL[,URL…]] [--name N] [--timeout SECS]`
     *
     * Mint a local transport keypair, print a `nostrconnect://` offer for the
     * user to paste into a signer, then wait for the signer's connect ACK
     * (a kind:24133 whose decrypted result echoes our secret). The ACK's author
     * is the remote signer; persist a bunker account that acts as that key.
     */
    suspend fun login(
        dataDir: DataDir,
        args: Args,
    ): Int {
        if (dataDir.identityExists()) {
            return Output.error("exists", "identity already exists at ${dataDir.identityFile}; use a fresh --data-dir or delete it first")
        }
        val relays = RawEventSupport.relayFlag(args).ifEmpty { DefaultNIP65RelaySet }
        if (relays.isEmpty()) return Output.error("bad_args", "no relays; pass --relay URL[,URL…]")
        val timeoutMs = (args.flag("timeout")?.toLongOrNull() ?: 120L) * 1000
        val name = args.flag("name")

        val clientKey = KeyPair()
        val clientSigner = NostrSignerInternal(clientKey)
        val clientPub = clientKey.pubKey.toHexKey()
        val secret = KeyPair().privKey!!.toHexKey().take(32)
        val offer = buildOffer(clientPub, relays, secret, name)

        // Surface the offer immediately so the human/harness can paste it.
        System.err.println("[nostrconnect] paste this into your signer within ${timeoutMs / 1000}s:")
        System.err.println(offer)

        val okhttp = OkHttpClient.Builder().build()
        val client = NostrClient(websocketBuilder = BasicOkHttpWebSocket.Builder { okhttp })
        val incoming = Channel<NostrConnectEvent>(UNLIMITED)
        val subId = newSubId()
        val listener =
            object : SubscriptionListener {
                override fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    if (event is NostrConnectEvent) incoming.trySend(event)
                }
            }

        try {
            client.connect()
            val filter = Filter(kinds = listOf(NostrConnectEvent.KIND), tags = mapOf("p" to listOf(clientPub)))
            client.subscribe(subId, relays.associateWith { listOf(filter) }, listener)

            val signerPub =
                withTimeoutOrNull(timeoutMs) {
                    // Drain kind:24133 until one decrypts to a BunkerResponse echoing our secret.
                    var found: String? = null
                    while (found == null) {
                        found = verifyAck(incoming.receive(), clientSigner, secret)
                    }
                    found
                }

            if (signerPub == null) {
                return Output.error("timeout", "no signer connected within ${timeoutMs / 1000}s")
            }

            val identity = Identity.bunkerIdentity(signerPub, relays.map { it.url }, secret, clientKey.privKey!!.toHexKey())
            dataDir.saveIdentity(identity)
            Output.emit(
                mapOf(
                    "npub" to identity.npub,
                    "hex" to identity.pubKeyHex,
                    "read_only" to false,
                    "signer" to "bunker",
                    "bunker_relays" to relays.map { it.url },
                    "data_dir" to dataDir.root.absolutePath,
                ),
            )
            return 0
        } finally {
            client.unsubscribe(subId)
            incoming.close()
            client.close()
        }
    }

    /** Returns the signer pubkey if [event] is the connect ACK echoing [secret], else null. */
    private suspend fun verifyAck(
        event: NostrConnectEvent,
        clientSigner: NostrSignerInternal,
        secret: String,
    ): String? =
        try {
            val msg = event.decryptMessage(clientSigner)
            if (msg is BunkerResponse && msg.result == secret) event.pubKey else null
        } catch (e: Exception) {
            null
        }
}
