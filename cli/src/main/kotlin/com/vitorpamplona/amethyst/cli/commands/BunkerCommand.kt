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
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46PermissionAuthorizer
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46PermissionAuthorizer.Companion.toSignerOp
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectURI
import com.vitorpamplona.quartz.nip46RemoteSigner.server.BunkerRequestProcessor
import com.vitorpamplona.quartz.nip46RemoteSigner.server.Nip46ConnectDecision
import com.vitorpamplona.quartz.nip46RemoteSigner.server.Nip46RequestAuthorizer
import com.vitorpamplona.quartz.nip46RemoteSigner.server.NostrConnectSignerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * `amy bunker [--relay URL[,URL…]] [--secret S] [--perms P] [--interactive] [--timeout SECS]`
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
 * By default every request is approved (the CLI bunker hosts the operator's own
 * key — the pairing secret is the gate). Two opt-in gates narrow that:
 *  - `--perms sign_event:1,nip44_encrypt,…` restricts the signer to the listed
 *    ops (anything else is rejected). Fully scriptable/headless.
 *  - `--interactive` prompts `y/N` on the terminal for any op the policy doesn't
 *    already allow, so the operator approves/rejects each one live. Requires a
 *    TTY; composes with `--perms` (perms auto-allow, prompt for the rest).
 *
 * Thin assembly only: the request dispatch, the encrypted wrapper and the
 * subscribe/serve loop all live in quartz (`BunkerRequestProcessor`,
 * `NostrConnectSignerService`, `NostrConnectEvent`); the permission parsing +
 * request→op mapping are reused from commons (`Nip46PermissionAuthorizer`).
 */
object BunkerCommand {
    /**
     * A bunker authorizer: validate the connect [secret], then decide each op.
     *
     * When [gated] is false (no `--perms`, no `--interactive`) every op is
     * approved — the headless default for hosting the operator's own key. When
     * gated, an op is allowed if [allowAllSignKinds] covers it or it is in
     * [allowedOps]; otherwise it is denied, unless [interactive] is set, in which
     * case the operator is prompted on the terminal. Prompts are serialized by
     * [promptLock] because the service dispatches requests concurrently.
     */
    private class CliAuthorizer(
        val secret: String,
        val allowedOps: List<NostrSignerOp>,
        val allowAllSignKinds: Boolean,
        val interactive: Boolean,
        val gated: Boolean,
    ) : Nip46RequestAuthorizer {
        private val promptLock = Mutex()

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
        ): Boolean {
            if (!gated) return true
            // Metadata ops (ping / get_public_key / get_relays) map to no op and need no grant.
            val op = request.toSignerOp() ?: return true
            val statically = (op is NostrSignerOp.SignKind && allowAllSignKinds) || op in allowedOps
            if (statically) return true
            return if (interactive) prompt(clientPubKey, request) else false
        }

        /** Ask the operator on the terminal. Serialized so concurrent requests don't interleave prompts. */
        private suspend fun prompt(
            clientPubKey: HexKey,
            request: BunkerRequest,
        ): Boolean =
            promptLock.withLock {
                withContext(Dispatchers.IO) {
                    val kindInfo = (request as? BunkerRequestSign)?.let { " (kind:${it.event.kind})" } ?: ""
                    System.err.println("[bunker] ${clientPubKey.take(8)}… requests ${request.method}$kindInfo")
                    (request as? BunkerRequestSign)?.event?.content?.take(160)?.trim()?.let {
                        if (it.isNotEmpty()) System.err.println("         content: ${it.replace('\n', ' ')}")
                    }
                    System.err.print("[bunker] approve? [y/N] ")
                    System.err.flush()
                    val answer = readlnOrNull()?.trim()?.lowercase()
                    val approved = answer == "y" || answer == "yes"
                    System.err.println(if (approved) "[bunker] → approved" else "[bunker] → denied")
                    approved
                }
            }
    }

    val USAGE: String =
        """
        |Remote signing (NIP-46):
        |  bunker [--relay URL[,URL…]]  run a remote signer for this (local-key) account; prints a
        |    [--secret S] [--timeout SECS]  bunker:// uri and signs requests until interrupt/timeout.
        |    [--perms P] [--interactive]    --perms sign_event:1,nip44_encrypt,… restricts which ops
        |                                    are allowed (rest rejected); --interactive prompts y/N on
        |                                    the terminal for anything not pre-allowed (needs a TTY).
        |                                    Default (neither): approve everything.
        |  bunker connect NOSTRCONNECT-URI             act as signer for a client's nostrconnect://
        |    [--perms P] [--interactive] [--timeout SECS]  offer (acks + services; same gating flags)
        """.trimMargin()

    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.firstOrNull() == "--help" || rest.firstOrNull() == "-h") {
            System.err.println(USAGE)
            return 0
        }
        return if (rest.firstOrNull() == "connect") {
            connect(dataDir, rest.drop(1).toTypedArray())
        } else {
            advertise(dataDir, rest)
        }
    }

    /** `amy bunker …` — advertise a bunker:// uri and service requests. */
    private suspend fun advertise(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val timeoutMs = args.flag("timeout")?.toLongOrNull()?.let { it * 1000 }
        interactiveTtyError(args)?.let { return it }
        args.rejectUnknown("relay", "secret", "perms")
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

            val authorizer = buildAuthorizer(args, secret)
            logPolicy(args)
            serve(ctx, relays, authorizer, timeoutMs)
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
        interactiveTtyError(args)?.let { return it }
        args.rejectUnknown("perms")
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
            // Surface what the client asked for; whether it's honored depends on this bunker's own
            // --perms/--interactive gate (below), not on the client's self-declared `perms`.
            offer.perms?.let { System.err.println("[bunker] client requested perms: $it") }

            val authorizer = buildAuthorizer(args, offer.secret)
            logPolicy(args)
            serve(ctx, offer.relays, authorizer, timeoutMs)
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

    /**
     * `--interactive` prompts the operator on the terminal, so it needs a real TTY; a piped/headless
     * stdin would make [readlnOrNull] return null and silently deny everything. Fail fast instead.
     */
    private fun interactiveTtyError(args: Args): Int? =
        if (args.bool("interactive") && System.console() == null) {
            Output.error("no_tty", "--interactive needs a terminal (stdin/stdout is not a TTY); use --perms for headless gating")
        } else {
            null
        }

    /** Builds the request gate from `--perms` / `--interactive` (both absent → approve everything). */
    private fun buildAuthorizer(
        args: Args,
        secret: String,
    ): CliAuthorizer {
        val perms = args.flag("perms")?.ifBlank { null }
        val interactive = args.bool("interactive")
        // parsePerms drops a bare `sign_event` (Amethyst grants per kind), so detect it here for "any kind".
        val allowAllSignKinds =
            perms
                ?.split(',')
                ?.any { it.trim().lowercase() == "sign_event" || it.trim().lowercase() == "sign" } ?: false
        return CliAuthorizer(
            secret = secret,
            allowedOps = Nip46PermissionAuthorizer.parsePerms(perms),
            allowAllSignKinds = allowAllSignKinds,
            interactive = interactive,
            gated = perms != null || interactive,
        )
    }

    /** Tell the operator which gate is active, so an unexpectedly-restrictive run is obvious. */
    private fun logPolicy(args: Args) {
        val perms = args.flag("perms")?.ifBlank { null }
        val interactive = args.bool("interactive")
        when {
            perms != null && interactive -> System.err.println("[bunker] gate: auto-allow [$perms], prompt for the rest")
            perms != null -> System.err.println("[bunker] gate: allow only [$perms], reject the rest")
            interactive -> System.err.println("[bunker] gate: prompt on the terminal for every op")
            else -> System.err.println("[bunker] gate: auto-approve every request (secret is the only gate)")
        }
    }

    /** Subscribe for kind:24133 requests addressed to us and service them until timeout/interrupt. */
    private suspend fun serve(
        ctx: Context,
        relays: Set<NormalizedRelayUrl>,
        authorizer: CliAuthorizer,
        timeoutMs: Long?,
    ) {
        val processor =
            BunkerRequestProcessor(
                signer = ctx.signer,
                relays = { relays },
                authorizer = authorizer,
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
