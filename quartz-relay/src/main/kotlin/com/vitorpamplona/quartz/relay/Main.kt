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
import com.vitorpamplona.quartz.relay.config.RelayConfig
import java.io.File

/**
 * Standalone entry point.
 *
 * Run with:
 *   ./gradlew :quartz-relay:run --args="--config /etc/quartz-relay.toml"
 * or
 *   java -cp ... com.vitorpamplona.quartz.relay.MainKt --port 7447 --verify
 *
 * Configuration precedence (highest to lowest):
 *   1. CLI flags (`--host`, `--port`, …)
 *   2. TOML file passed via `--config <path>`
 *   3. Built-in defaults (host=0.0.0.0, port=7447, in-memory db, …)
 *
 * Sections currently parsed AND enforced: `[info]`, `[network]`,
 * `[database]`, `[options]`. Sections parsed but not yet enforced
 * (forward-compat for the rate-limit / authorization work):
 * `[limits]`, `[authorization]`.
 *
 * CLI flags:
 *   --config <file>    TOML config (see config.example.toml)
 *   --host <addr>      bind address (default from config or 0.0.0.0)
 *   --port <n>         tcp port (default from config or 7447, 0 to autobind)
 *   --path <p>         ws path (default from config or /)
 *   --info <file>      NIP-11 doc file (overrides [info] section)
 *   --db <file>        sqlite db path (overrides [database].file)
 *   --auth             require NIP-42 AUTH (sets options.require_auth = true)
 *   --verify           verify event signatures (sets options.verify_signatures = true)
 */
fun main(args: Array<String>) {
    val a = parseArgs(args)

    val config: RelayConfig =
        a
            .opt("--config")
            ?.let { RelayConfig.fromFile(File(it)) }
            ?: RelayConfig()

    val host = a.opt("--host") ?: config.network.host
    val port = a.opt("--port")?.toInt() ?: config.network.port
    val path = a.opt("--path") ?: config.network.path

    val cliInfoFile = a.opt("--info")?.let { File(it) }
    val dbFile = a.opt("--db") ?: config.database.file?.takeUnless { config.database.in_memory }
    val requireAuth = a.flag("--auth") || config.options.require_auth
    val verifySigs = a.flag("--verify") || config.options.verify_signatures

    // Advertised URL: explicit `info.relay_url` wins, then build from
    // host/port/path. 0.0.0.0 bind → 127.0.0.1 in the URL so NIP-42
    // challenges are well-formed.
    val advertisedHost = if (host == "0.0.0.0") "127.0.0.1" else host
    val advertisedUrl =
        (config.info.relay_url ?: "ws://$advertisedHost:$port$path").normalizeRelayUrl()

    val info =
        cliInfoFile?.let { RelayInfo.fromFile(it) }
            ?: config.resolveInfo(advertisedUrl)

    val store: IEventStore = EventStore(dbName = dbFile, relay = advertisedUrl)

    val policyBuilder: () -> IRelayPolicy =
        when {
            verifySigs && requireAuth -> { -> VerifyPolicy + FullAuthPolicy(advertisedUrl) }
            verifySigs -> { -> VerifyPolicy }
            requireAuth -> { -> FullAuthPolicy(advertisedUrl) }
            else -> { -> EmptyPolicy }
        }

    warnUnenforcedSections(config)

    val relay = Relay(advertisedUrl, store, info, policyBuilder)
    val server = LocalRelayServer(relay, host = host, port = port, path = path).start()

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop()
            relay.close()
        },
    )

    println("quartz-relay listening on ${server.url}")
    println("NIP-11 info doc: curl -H 'Accept: application/nostr+json' http://$advertisedHost:$port$path")

    // Park the main thread; shutdown hook handles teardown.
    Thread.currentThread().join()
}

/** Surface a warning when the operator has set sections we don't yet enforce. */
private fun warnUnenforcedSections(config: RelayConfig) {
    val warnings = mutableListOf<String>()
    val l = config.limits
    if (l.max_event_bytes != null ||
        l.max_ws_message_bytes != null ||
        l.max_ws_frame_bytes != null ||
        l.messages_per_sec != null ||
        l.subscriptions_per_min != null ||
        l.max_subscriptions_per_session != null ||
        l.max_filters_per_req != null
    ) {
        warnings += "[limits] section is parsed but NOT YET ENFORCED — rate limits / message size caps are pending."
    }
    val auth = config.authorization
    if (auth.pubkey_whitelist.isNotEmpty() ||
        auth.pubkey_blacklist.isNotEmpty() ||
        auth.kind_whitelist.isNotEmpty() ||
        auth.kind_blacklist.isNotEmpty()
    ) {
        warnings += "[authorization] section is parsed but NOT YET ENFORCED — pubkey/kind allow-deny lists are pending."
    }
    if (config.options.reject_future_seconds != null) {
        warnings += "[options].reject_future_seconds is parsed but NOT YET ENFORCED."
    }
    if (config.network.remote_ip_header != null) {
        warnings += "[network].remote_ip_header is parsed but NOT YET ENFORCED — IP-based limits are pending."
    }
    warnings.forEach { System.err.println("warning: $it") }
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
