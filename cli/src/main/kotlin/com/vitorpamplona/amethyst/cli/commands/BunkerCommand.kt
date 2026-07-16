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
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectURI
import com.vitorpamplona.quartz.nip46RemoteSigner.server.BunkerRequestProcessor
import com.vitorpamplona.quartz.nip46RemoteSigner.server.Nip46ConnectDecision
import com.vitorpamplona.quartz.nip46RemoteSigner.server.Nip46RequestAuthorizer
import com.vitorpamplona.quartz.nip46RemoteSigner.server.NostrConnectSignerService
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
 * Thin assembly only: the request dispatch, the encrypted wrapper and the
 * subscribe/serve loop all live in quartz (`BunkerRequestProcessor`,
 * `NostrConnectSignerService`, `NostrConnectEvent`); this file just wires the
 * CLI account's `ctx.signer` into them and auto-approves every request (a
 * headless bunker for the operator's own local key).
 */
object BunkerCommand {
    /**
     * A headless bunker authorizer: validate the connect [secret] and then
     * approve every operation. The CLI bunker hosts the operator's OWN key, so
     * there is no separate user to prompt — the pairing secret is the gate.
     */
    private class CliAuthorizer(
        val secret: String,
    ) : Nip46RequestAuthorizer {
        override suspend fun onConnect(
            clientPubKey: HexKey,
            request: BunkerRequestConnect,
        ): Nip46ConnectDecision =
            if (request.secret == secret) {
                Nip46ConnectDecision.Accept(BunkerRequestProcessor.ACK)
            } else {
                Nip46ConnectDecision.Reject("invalid secret")
            }

        override suspend fun authorize(
            clientPubKey: HexKey,
            request: BunkerRequest,
        ): Boolean = true
    }

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

            // Percent-encoded per the spec/nak convention (relay=wss%3A%2F%2F…).
            val uri = NostrConnectURI.buildBunker(self, relays, secret)
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
                    "requested_perms" to offer.perms,
                ),
            )
            System.err.println("[bunker] acked nostrconnect from ${offer.clientPubkey.take(8)}…; now servicing requests")
            // The CLI bunker auto-approves the operator's own key, so `perms` isn't a gate here; we surface
            // it so an interop operator can see what an app-side signer would have been asked to pre-grant.
            offer.perms?.let { System.err.println("[bunker] client requested perms: $it") }

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
        val processor =
            BunkerRequestProcessor(
                signer = ctx.signer,
                relays = { relays },
                authorizer = CliAuthorizer(secret),
            )
        val service =
            NostrConnectSignerService(
                client = ctx.client,
                // The CLI bunker deliberately advertises the operator's own key as the transport key
                // (a simple dev/interop tool); the app uses a separate transport key for privacy.
                transportSigner = ctx.signer,
                processor = processor,
                relays = relays,
                onServiced = { request, client, error ->
                    val outcome = if (error != null) "error: $error" else "ok"
                    System.err.println("[bunker] ${request.method} from ${client.take(8)}… → $outcome")
                },
            )

        if (timeoutMs != null) withTimeoutOrNull(timeoutMs) { service.run() } else service.run()
    }
}
