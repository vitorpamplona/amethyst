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
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
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
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.ReadWrite
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.withTimeoutOrNull

/**
 * `amy bunker [--relay URL[,URL…]] [--secret S] [--timeout SECS]`
 *
 * Run a NIP-46 remote signer (a "bunker") for the active LOCAL account
 * (nak's `bunker`). Prints a `bunker://…` connection string, then listens on
 * the relays for kind:24133 requests, decrypts each one, performs it with the
 * account's key (sign / nip04 / nip44 / get_public_key / ping), and publishes
 * the encrypted reply.
 *
 * Pair it with `amy login bunker://…` in another amy: that account then signs
 * remotely through this bunker. Long-running — stops at `--timeout` SECS or on
 * interrupt.
 *
 * Thin assembly only: every request/response type + the encrypted wrapper
 * live in quartz (`BunkerRequest*`, `BunkerResponse*`, `NostrConnectEvent`);
 * this file dispatches to `ctx.signer`.
 */
object BunkerCommand {
    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int =
        if (rest.firstOrNull() == "connect") {
            connect(dataDir, rest.drop(1).toTypedArray())
        } else {
            advertise(dataDir, rest)
        }

    /** `amy bunker …` — advertise a bunker:// uri and service requests. */
    private suspend fun advertise(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val timeoutMs = args.flag("timeout")?.toLongOrNull()?.let { it * 1000 }
        val accountError = checkHostable(dataDir)
        if (accountError != null) return accountError

        Context.open(dataDir).use { ctx ->
            if (!ctx.identity.hasPrivateKey) {
                return Output.error("read_only", "bunker host needs a local private key (this account is read-only)")
            }
            ctx.prepare()

            val relays = RawEventSupport.relayFlag(args).ifEmpty { ctx.outboxRelays() }
            if (relays.isEmpty()) return Output.error("no_relays", "no relays available; pass --relay or run `amy relay add`")
            val secret = args.flag("secret") ?: KeyPair().privKey!!.toHexKey().take(32)
            val self = ctx.identity.pubKeyHex

            // Percent-encode params (spec/nak convention: relay=wss%3A%2F%2F…).
            val enc = { s: String -> java.net.URLEncoder.encode(s, "UTF-8") }
            val uri =
                buildString {
                    append("bunker://").append(self)
                    append("?").append(relays.joinToString("&") { "relay=${enc(it.url)}" })
                    append("&secret=").append(enc(secret))
                }
            Output.emit(
                mapOf(
                    "bunker_uri" to uri,
                    "pubkey" to self,
                    "relays" to relays.map { it.url },
                    "secret" to secret,
                ),
            )
            System.err.println("[bunker] listening as ${self.take(8)}… on ${relays.size} relay(s); paste the bunker:// uri into `amy login`")

            serve(ctx, relays, secret, timeoutMs)
            return 0
        }
    }

    /**
     * `amy bunker connect <nostrconnect://…>` — the client-initiated
     * (NostrConnect) flow: parse a client's offer, send the connect ACK that
     * echoes the offer's secret back (so the client learns our signer pubkey),
     * then service that client's requests on the offer's relays.
     */
    private suspend fun connect(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val timeoutMs = args.flag("timeout")?.toLongOrNull()?.let { it * 1000 }
        val uri = args.positional(0, "nostrconnect-uri")
        val offer = NostrConnect.parseOffer(uri) ?: return Output.error("bad_args", "not a valid nostrconnect:// uri")
        val accountError = checkHostable(dataDir)
        if (accountError != null) return accountError

        Context.open(dataDir).use { ctx ->
            if (!ctx.identity.hasPrivateKey) {
                return Output.error("read_only", "bunker host needs a local private key (this account is read-only)")
            }
            ctx.prepare()
            if (offer.relays.isEmpty()) return Output.error("bad_args", "nostrconnect uri carries no relay")

            // Send the connect ACK (result == secret) to the client.
            val ack = BunkerResponse(newSubId(), offer.secret, null)
            val reply = NostrConnectEvent.create(ack, offer.clientPubkey, ctx.signer)
            ctx.client.publish(reply, offer.relays)
            Output.emit(
                mapOf(
                    "connected_to" to offer.clientPubkey,
                    "pubkey" to ctx.identity.pubKeyHex,
                    "relays" to offer.relays.map { it.url },
                ),
            )
            System.err.println("[bunker] acked nostrconnect from ${offer.clientPubkey.take(8)}…; now servicing requests")

            serve(ctx, offer.relays, offer.secret, timeoutMs)
            return 0
        }
    }

    /** A remote-signer (bunker) account cannot itself host a bunker. */
    private fun checkHostable(dataDir: DataDir): Int? =
        if (dataDir.loadIdentityFileOrNull()?.bunker != null) {
            Output.error("bad_account", "this account signs through a remote bunker — host a bunker from a local-key account")
        } else {
            null
        }

    /** Subscribe for kind:24133 requests addressed to us and service them until timeout/interrupt. */
    private suspend fun serve(
        ctx: Context,
        relays: Set<NormalizedRelayUrl>,
        secret: String,
        timeoutMs: Long?,
    ) {
        val self = ctx.identity.pubKeyHex
        val events = Channel<NostrConnectEvent>(UNLIMITED)
        val seen = mutableSetOf<String>()
        val subId = newSubId()
        val listener =
            object : SubscriptionListener {
                override fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    if (event is NostrConnectEvent && seen.add(event.id)) events.trySend(event)
                }
            }

        val filter = Filter(kinds = listOf(NostrConnectEvent.KIND), tags = mapOf("p" to listOf(self)))
        ctx.client.subscribe(subId, relays.associateWith { listOf(filter) }, listener)
        try {
            val loop: suspend () -> Unit = {
                while (true) handle(ctx, events.receive(), secret, relays)
            }
            if (timeoutMs != null) withTimeoutOrNull(timeoutMs) { loop() } else loop()
        } finally {
            ctx.client.unsubscribe(subId)
            events.close()
        }
    }

    private suspend fun handle(
        ctx: Context,
        event: NostrConnectEvent,
        secret: String,
        relays: Set<NormalizedRelayUrl>,
    ) {
        val signer = ctx.signer
        val client = event.talkingWith(signer.pubKey)
        val request =
            try {
                event.decryptMessage(signer) as? BunkerRequest ?: return
            } catch (e: Exception) {
                System.err.println("[bunker] could not decrypt request ${event.id.take(8)}: ${e.message}")
                return
            }

        val response: BunkerResponse =
            try {
                when (request) {
                    is BunkerRequestConnect ->
                        if (request.secret == secret) {
                            BunkerResponseAck(request.id)
                        } else {
                            BunkerResponseError(request.id, "invalid secret")
                        }
                    is BunkerRequestGetPublicKey -> BunkerResponsePublicKey(request.id, signer.pubKey)
                    is BunkerRequestGetRelays -> BunkerResponseGetRelays(request.id, relays.associate { it.url to ReadWrite(read = true, write = true) })
                    is BunkerRequestPing -> BunkerResponsePong(request.id)
                    is BunkerRequestSign -> {
                        val signed = signer.sign<Event>(request.event.createdAt, request.event.kind, request.event.tags, request.event.content)
                        BunkerResponseEvent(request.id, signed)
                    }
                    is BunkerRequestNip04Encrypt -> BunkerResponseEncrypt(request.id, signer.nip04Encrypt(request.message, request.pubKey))
                    is BunkerRequestNip04Decrypt -> BunkerResponseDecrypt(request.id, signer.nip04Decrypt(request.ciphertext, request.pubKey))
                    is BunkerRequestNip44Encrypt -> BunkerResponseEncrypt(request.id, signer.nip44Encrypt(request.message, request.pubKey))
                    is BunkerRequestNip44Decrypt -> BunkerResponseDecrypt(request.id, signer.nip44Decrypt(request.ciphertext, request.pubKey))
                    else -> BunkerResponseError(request.id, "unsupported method: ${request.method}")
                }
            } catch (e: Exception) {
                BunkerResponseError(request.id, "${e::class.simpleName}: ${e.message}")
            }

        System.err.println("[bunker] ${request.method} from ${client.take(8)}… → ${if (response is BunkerResponseError) "error: ${response.error}" else "ok"}")

        try {
            val reply = NostrConnectEvent.create(response, client, signer)
            ctx.client.publish(reply, relays)
        } catch (e: Exception) {
            System.err.println("[bunker] failed to send reply for ${request.method}: ${e.message}")
        }
    }
}
