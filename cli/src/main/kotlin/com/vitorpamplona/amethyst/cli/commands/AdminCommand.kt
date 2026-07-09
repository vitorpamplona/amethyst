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
import com.vitorpamplona.amethyst.commons.relayManagement.Nip86Retriever
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip86RelayManagement.Nip86Client
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Request
import okhttp3.OkHttpClient

/**
 * `amy admin RELAY METHOD [args]` — NIP-86 Relay Management API (nak's
 * `relay`/admin). Signs a NIP-98 request with the account key and POSTs it to
 * the relay's HTTP endpoint. Reuses quartz's `Nip86Client` (request build +
 * NIP-98 auth + response parse) and the shared `commons` `Nip86Retriever`
 * (the exact HTTP path Amethyst's relay-management screen runs).
 *
 *   admin wss://relay supported-methods
 *   admin wss://relay ban-pubkey HEX [--reason R]   / unban-pubkey HEX
 *   admin wss://relay allow-pubkey HEX [--reason R]  / list-allowed-pubkeys
 *   admin wss://relay list-banned-pubkeys
 *   admin wss://relay ban-event ID [--reason R] / allow-event ID / list-banned-events
 *   admin wss://relay list-needing-moderation
 *   admin wss://relay change-name S / change-description S / change-icon URL
 *   admin wss://relay allow-kind N / disallow-kind N / list-allowed-kinds
 *   admin wss://relay block-ip IP [--reason R] / unblock-ip IP / list-blocked-ips
 */
object AdminCommand {
    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val relayArg = args.positionalOrNull(0) ?: return Output.error("bad_args", "usage: admin RELAY METHOD [args]")
        val method = args.positionalOrNull(1) ?: return Output.error("bad_args", "missing method; e.g. supported-methods")
        val relay = RelayUrlNormalizer.normalizeOrNull(relayArg) ?: return Output.invalidRelayUrl(relayArg)
        val p2 = args.positionalOrNull(2)
        val reason = args.flag("reason")

        fun needArg(name: String): String? =
            p2 ?: run {
                Output.error("bad_args", "$method requires a $name argument")
                null
            }

        val request: Nip86Request =
            when (method) {
                "supported-methods" -> Nip86Request.supportedMethods()
                "ban-pubkey" -> Nip86Request.banPubkey(needArg("pubkey") ?: return 2, reason)
                "unban-pubkey" -> Nip86Request.unbanPubkey(needArg("pubkey") ?: return 2, reason)
                "list-banned-pubkeys" -> Nip86Request.listBannedPubkeys()
                "allow-pubkey" -> Nip86Request.allowPubkey(needArg("pubkey") ?: return 2, reason)
                "unallow-pubkey" -> Nip86Request.unallowPubkey(needArg("pubkey") ?: return 2, reason)
                "list-allowed-pubkeys" -> Nip86Request.listAllowedPubkeys()
                "ban-event" -> Nip86Request.banEvent(needArg("event-id") ?: return 2, reason)
                "allow-event" -> Nip86Request.allowEvent(needArg("event-id") ?: return 2, reason)
                "list-banned-events" -> Nip86Request.listBannedEvents()
                "list-needing-moderation" -> Nip86Request.listEventsNeedingModeration()
                "change-name" -> Nip86Request.changeRelayName(needArg("name") ?: return 2)
                "change-description" -> Nip86Request.changeRelayDescription(needArg("description") ?: return 2)
                "change-icon" -> Nip86Request.changeRelayIcon(needArg("icon-url") ?: return 2)
                "allow-kind" -> Nip86Request.allowKind((needArg("kind") ?: return 2).toIntOrNull() ?: return Output.error("bad_args", "kind must be an integer"))
                "disallow-kind" -> Nip86Request.disallowKind((needArg("kind") ?: return 2).toIntOrNull() ?: return Output.error("bad_args", "kind must be an integer"))
                "list-allowed-kinds" -> Nip86Request.listAllowedKinds()
                "block-ip" -> Nip86Request.blockIp(needArg("ip") ?: return 2, reason)
                "unblock-ip" -> Nip86Request.unblockIp(needArg("ip") ?: return 2)
                "list-blocked-ips" -> Nip86Request.listBlockedIps()
                else -> return Output.error("bad_args", "unknown method: $method")
            }

        Context.open(dataDir).use { ctx ->
            val client = Nip86Client(relay, ctx.signer)
            val http = OkHttpClient.Builder().build()
            val retriever = Nip86Retriever { _ -> http }
            val response = retriever.execute(client, request)
            if (response.error != null) return Output.error("relay_error", response.error)
            // Reparse the kotlinx JSON result through Jackson so it renders as
            // structured JSON (text + --json) instead of a toString blob.
            val resultNode = response.result?.toString()?.let { Output.mapper.readTree(it) }
            Output.emit(mapOf("relay" to relay.url, "method" to method, "result" to resultNode))
        }
        return 0
    }
}
