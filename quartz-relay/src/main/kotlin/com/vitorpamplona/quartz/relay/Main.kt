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
package com.vitorpamplona.quartz.relay

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.IRelayPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.EmptyPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.FullAuthPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.VerifyPolicy
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import java.io.File

/**
 * Standalone entry point. Run with `./gradlew :quartz-relay:run` (when the
 * application plugin is configured) or `java -cp ... Main`.
 *
 * Usage:
 *   --host <addr>      bind address (default 0.0.0.0)
 *   --port <n>         tcp port (default 7447, 0 to autobind)
 *   --path <p>         ws path (default /)
 *   --info <file>      NIP-11 doc file (default: built-in)
 *   --db <file>        sqlite db path (default: in-memory)
 *   --auth             require NIP-42 AUTH for REQ/EVENT/COUNT
 *   --verify           verify event signatures (recommended for any
 *                      relay accepting traffic from real clients)
 */
fun main(args: Array<String>) {
    val a = parseArgs(args)
    val host = a.opt("--host") ?: "0.0.0.0"
    val port = a.opt("--port")?.toInt() ?: 7447
    val path = a.opt("--path") ?: "/"
    val infoFile = a.opt("--info")?.let { File(it) }
    val dbFile = a.opt("--db")
    val requireAuth = a.flag("--auth")
    val verifySigs = a.flag("--verify")

    val urlStr = "ws://$host:$port$path"
    // For binding 0.0.0.0 we still want to scope the relay to a "public" url
    // shape for NIP-42 challenge validation; use the host the operator
    // exposes (--info usually carries the public URL). Fall back to
    // 127.0.0.1 so localhost smoke tests work.
    val advertisedUrl = (if (host == "0.0.0.0") "ws://127.0.0.1:$port$path" else urlStr).normalizeRelayUrl()

    val info = infoFile?.let { RelayInfo.fromFile(it) } ?: RelayInfo.default(advertisedUrl)

    val store: IEventStore = EventStore(dbName = dbFile, relay = advertisedUrl)

    val policyBuilder: () -> IRelayPolicy =
        when {
            verifySigs && requireAuth -> { -> VerifyPolicy + FullAuthPolicy(advertisedUrl) }
            verifySigs -> { -> VerifyPolicy }
            requireAuth -> { -> FullAuthPolicy(advertisedUrl) }
            else -> { -> EmptyPolicy }
        }

    val relay = Relay(advertisedUrl, store, info, policyBuilder)
    val server = LocalRelayServer(relay, host = host, port = port, path = path).start()

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop()
            relay.close()
        },
    )

    println("quartz-relay listening on ${server.url}")
    println("NIP-11 info doc: curl -H 'Accept: application/nostr+json' http://$host:$port$path")

    // Park the main thread; shutdown hook handles teardown.
    Thread.currentThread().join()
}

private class Args(
    private val opts: Map<String, String>,
    private val flags: Set<String>,
) {
    fun opt(k: String) = opts[k]

    fun flag(k: String) = k in flags
}

private fun parseArgs(args: Array<String>): Args {
    val opts = mutableMapOf<String, String>()
    val flags = mutableSetOf<String>()
    var i = 0
    while (i < args.size) {
        val a = args[i]
        if (a.startsWith("--")) {
            val next = args.getOrNull(i + 1)
            if (next != null && !next.startsWith("--")) {
                opts[a] = next
                i += 2
            } else {
                flags += a
                i += 1
            }
        } else {
            i += 1
        }
    }
    return Args(opts, flags)
}
