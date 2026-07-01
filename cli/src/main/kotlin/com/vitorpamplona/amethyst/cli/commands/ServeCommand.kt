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
import com.vitorpamplona.geode.KtorRelay
import com.vitorpamplona.geode.RelayEngine
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import kotlinx.coroutines.awaitCancellation

/**
 * `amy serve [--host H] [--port N] [--path P] [--db FILE] [--admin NPUBS]` —
 * run a Nostr relay (nak's `serve`). Embeds **geode** (the standalone Ktor
 * relay built on quartz's relay-server code), so amy serves the exact same
 * relay implementation, including NIP-86 admin and NIP-77 Negentropy.
 *
 * In-memory by default (ephemeral, like nak serve); pass `--db FILE` for a
 * persistent SQLite store. The account's own pubkey is always an admin so
 * `amy admin ws://host:port …` works against it out of the box; `--admin`
 * adds more (comma-separated npub/hex). Blocks until interrupted.
 */
object ServeCommand {
    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val host = args.flag("host") ?: "127.0.0.1"
        val port = args.intFlag("port", 7447)
        val path = args.flag("path") ?: "/"
        val dbFile = args.flag("db")
        val extraAdmins =
            args
                .flag("admin")
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()

        // Resolve admin pubkeys (self + --admin) up front, then drop the
        // Context — the embedded relay owns its own store and needs no account.
        val adminPubkeys =
            Context.open(dataDir).use { ctx ->
                buildSet {
                    add(ctx.identity.pubKeyHex)
                    extraAdmins.forEach { add(ctx.requireUserHex(it)) }
                }
            }

        // 0.0.0.0 isn't routable in a NIP-42 challenge; advertise loopback.
        val advertisedHost = if (host == "0.0.0.0") "127.0.0.1" else host
        val url = "ws://$advertisedHost:$port$path".normalizeRelayUrl()
        // In-memory is RelayEngine's default; only build a SQLite store for --db.
        val relay =
            if (dbFile != null) {
                RelayEngine(url, store = EventStore(dbName = dbFile, relay = url), adminPubkeys = adminPubkeys)
            } else {
                RelayEngine(url, adminPubkeys = adminPubkeys)
            }
        val server = KtorRelay(relay, host = host, port = port, path = path).start()

        Runtime.getRuntime().addShutdownHook(
            Thread {
                runCatching { server.stop() }
                runCatching { relay.close() }
            },
        )

        Output.emit(
            mapOf(
                "listening" to server.url,
                "host" to host,
                "port" to port,
                "path" to path,
                "persistent" to (dbFile != null),
                "admin_pubkeys" to adminPubkeys.toList(),
            ),
        )
        System.err.println("[serve] relay up at ${server.url} — Ctrl-C to stop")

        // Block until the process is interrupted; the shutdown hook tears down.
        awaitCancellation()
    }
}
