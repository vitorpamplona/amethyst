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
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.NamecoinSettings
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.DEFAULT_ELECTRUMX_SERVERS
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumXClient
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxServer
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinResolveOutcome

/**
 * `amy namecoin …` — resolve `.bit` / `d/` / `id/` identifiers to a
 * Nostr pubkey via the Namecoin blockchain.
 *
 * Stateless: no account, no `~/.amy/`, no relays. Talks directly to
 * one or more ElectrumX servers over TLS to fetch the latest
 * `name_show` value for the requested name and extracts the `nostr`
 * field per the NIP-05-Namecoin parser (priority: exact local-part →
 * `_` wildcard → first valid entry; `nostr:` prefix tolerated;
 * accepts the simple-string, single-identity-object, and
 * extended-NIP-05-like `names` map forms).
 *
 * Verbs:
 *   resolve IDENTIFIER     resolve to a Nostr pubkey (+ relays)
 *   servers                list the default ElectrumX server set
 *
 * IDENTIFIER accepts the same forms the Amethyst Android/Desktop apps
 * accept:
 *   d/example              raw `d/` Namecoin name (root `_` local-part)
 *   id/alice               raw `id/` Namecoin name (always `_`)
 *   example.bit            domain form (canonicalised to `d/example`)
 *   alice@example.bit      NIP-05-style local-part in a `.bit` domain
 *
 * Flags (resolve):
 *   --server HOST:PORT[:tcp][,…]  override the ElectrumX server list (one or
 *                          more `host:port[:tcp]` entries, same form the apps
 *                          accept; TLS by default, `:tcp` for plaintext;
 *                          default: the same hard-coded mainnet set the apps ship with)
 *   --timeout SECS         overall lookup timeout (default 20)
 *
 * Exit codes follow amy convention:
 *   0   success (Output.emit was called)
 *   1   Output.error was called (bad_args, network, name not found, …)
 *   2   usage error raised before the verb runs (e.g. a missing positional,
 *       thrown by Args and mapped to 2 by Main)
 *
 * The JSON shape on success (resolve):
 *   {
 *     "identifier":    "alice@example.bit",
 *     "namecoin_name": "d/example",
 *     "local_part":    "alice",
 *     "pubkey":        "<64 hex>",
 *     "relays":        ["wss://relay.damus.io/", …]
 *   }
 *
 * The JSON shape on success (servers):
 *   {
 *     "count": 7,
 *     "servers": [
 *       { "host": "electrumx.testls.space", "port": 50002, "tls": true },
 *       …
 *     ]
 *   }
 */
object NamecoinCommand {
    private val DEFAULT_TIMEOUT_SECS = 20L

    val USAGE: String =
        """
        |Namecoin (stateless — no account, talks to ElectrumX over TLS):
        |  namecoin resolve IDENT       resolve a Namecoin identifier (.bit, d/, id/, alice@x.bit)
        |    [--server URL[,URL]]         to a Nostr pubkey + relays via the Namecoin blockchain
        |    [--timeout SECS]             (--server: host:port[:tcp] entries; default timeout 20s)
        |  namecoin servers             print the default ElectrumX server list
        """.trimMargin()

    suspend fun dispatch(rest: Array<String>): Int =
        route(
            name = "namecoin",
            tail = rest,
            usage = "namecoin <resolve|servers> …",
            help = USAGE,
            routes =
                mapOf(
                    "resolve" to { tail -> resolve(tail) },
                    "servers" to { tail -> servers(tail) },
                ),
        )

    private suspend fun resolve(rest: Array<String>): Int {
        val args = Args(rest)
        val identifier = args.positional(0, "identifier").trim()
        if (identifier.isEmpty()) {
            return Output.error("bad_args", "namecoin resolve <identifier>")
        }
        if (!NamecoinNameResolver.isNamecoinIdentifier(identifier)) {
            return Output.error(
                "bad_args",
                "not a Namecoin identifier (expected .bit, d/, or id/): $identifier",
            )
        }

        val serverFlag = args.flag("server")
        val servers = parseServerFlag(serverFlag)
        if (servers != null && servers.isEmpty()) {
            return Output.error("bad_args", "--server: no valid host:port entries in '$serverFlag'")
        }

        val timeoutFlag = args.flag("timeout")
        args.rejectUnknown()
        val timeoutSecs =
            if (timeoutFlag != null) {
                timeoutFlag.toLongOrNull()
                    ?: return Output.error("bad_args", "--timeout must be an integer (was '$timeoutFlag')")
            } else {
                DEFAULT_TIMEOUT_SECS
            }
        if (timeoutSecs <= 0) {
            return Output.error("bad_args", "--timeout must be positive (was $timeoutSecs)")
        }
        val timeoutMs = timeoutSecs * 1000

        val resolver =
            NamecoinNameResolver(
                electrumxClient = ElectrumXClient(),
                lookupTimeoutMs = timeoutMs,
                serverListProvider = { servers ?: DEFAULT_ELECTRUMX_SERVERS },
            )

        return when (val outcome = resolver.resolveDetailed(identifier)) {
            is NamecoinResolveOutcome.Success -> {
                val r = outcome.result
                Output.emit(
                    mapOf(
                        "identifier" to identifier,
                        "namecoin_name" to r.namecoinName,
                        "local_part" to r.localPart,
                        "pubkey" to r.pubkey,
                        "relays" to r.relays,
                    ),
                )
                0
            }
            is NamecoinResolveOutcome.NameNotFound ->
                Output.error("not_found", "Namecoin name does not exist: ${outcome.name}")
            is NamecoinResolveOutcome.NoNostrField ->
                Output.error(
                    "no_nostr_field",
                    "Namecoin name has no Nostr field: ${outcome.name}",
                )
            is NamecoinResolveOutcome.MalformedRecord ->
                Output.error(
                    "malformed_record",
                    outcome.error,
                    extra = mapOf("namecoin_name" to outcome.name),
                )
            is NamecoinResolveOutcome.ServersUnreachable ->
                Output.error("servers_unreachable", outcome.message)
            is NamecoinResolveOutcome.InvalidIdentifier ->
                Output.error("invalid_identifier", outcome.identifier)
            NamecoinResolveOutcome.Timeout ->
                Output.error("timeout", "lookup timed out after ${timeoutSecs}s")
        }
    }

    private fun servers(rest: Array<String>): Int {
        // No positional/flags today — but reject unknown ones so future
        // additions don't silently break.
        val args = Args(rest)
        args.rejectUnknown()
        if (args.positionalOrNull(0) != null) {
            return Output.error("bad_args", "namecoin servers takes no arguments")
        }
        Output.emit(
            mapOf(
                "count" to DEFAULT_ELECTRUMX_SERVERS.size,
                "servers" to
                    DEFAULT_ELECTRUMX_SERVERS.map { srv ->
                        mapOf(
                            "host" to srv.host,
                            "port" to srv.port,
                            "tls" to srv.useSsl,
                        )
                    },
            ),
        )
        return 0
    }

    /**
     * Parse the `--server HOST:PORT[:tcp][,…]` flag — one or more comma-separated
     * entries in the same `host:port[:tcp]` form the Android/Desktop Namecoin
     * Settings accept (TLS by default; a trailing `:tcp` selects plaintext).
     *
     * Each entry is parsed by the shared [NamecoinSettings.parseServerString], so
     * the CLI inherits the apps' exact syntax **and** their trust model — notably
     * `usePinnedTrustStore = true`, which is required for the self-signed certs the
     * Namecoin ElectrumX servers use (a hand-rolled parser that left it `false`
     * would fail the TLS handshake against those servers).
     *
     * Returns null when the flag is absent (caller falls back to the default server
     * list). Returns an empty list when the flag is present but produced zero valid
     * entries — the caller treats that as a hard `bad_args` rather than silently
     * using defaults, so a fat-fingered `--server foo:bar` is impossible to overlook.
     */
    private fun parseServerFlag(raw: String?): List<ElectrumxServer>? {
        if (raw == null) return null
        return raw
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { NamecoinSettings.parseServerString(it) }
    }
}
