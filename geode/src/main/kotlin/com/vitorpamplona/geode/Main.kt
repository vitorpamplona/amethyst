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
package com.vitorpamplona.geode

import com.vitorpamplona.geode.config.RelayConfig
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.IRelayPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.EmptyPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.FullAuthPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.KindAllowDenyPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.PubkeyAllowDenyPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.RejectFutureEventsPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.VerifyAuthOnlyPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.VerifyPolicy
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip77Negentropy.NegentropySettings
import java.io.File

/**
 * Standalone entry point.
 *
 * Run with:
 *   ./gradlew :geode:run --args="--config /etc/geode.toml"
 * or
 *   java -cp ... com.vitorpamplona.geode.MainKt --port 7447 --verify
 *
 * Configuration precedence (highest to lowest):
 *   1. CLI flags (`--host`, `--port`, …)
 *   2. TOML file passed via `--config <path>`
 *   3. Built-in defaults (host=0.0.0.0, port=7447, in-memory db, …)
 *
 * Every section is enforced: `[info]` populates the NIP-11 doc,
 * `[network]` controls the bind, `[database]` chooses the SQLite path,
 * `[options]` toggles AUTH/verify/future-skew, `[limits]` and
 * `[authorization]` plug into the relay's policy stack.
 *
 * CLI flags:
 *   --config <file>    TOML config (see config.example.toml)
 *   --host <addr>      bind address (default from config or 0.0.0.0)
 *   --port <n>         tcp port (default from config or 7447, 0 to autobind)
 *   --path <p>         ws path (default from config or /)
 *   --info <file>      NIP-11 doc file (overrides [info] section)
 *   --db <file>        sqlite db path (overrides [database].file)
 *   --auth             require NIP-42 AUTH (sets options.require_auth = true)
 *   --no-verify        DO NOT verify event signatures (off by default
 *                      verify is on; use only for trusted-input
 *                      scenarios like fixture replay).
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
    // Verify is on by default; only disable when the operator explicitly
    // opts out (CLI `--no-verify` or `[options].verify_signatures = false`
    // in the config).
    val verifySigs = !a.flag("--no-verify") && config.options.verify_signatures
    // Parallel verify is on whenever signature checking is on; the
    // IngestQueue handles it instead of VerifyPolicy. Operators can
    // force the legacy in-policy path with `--no-parallel-verify` or
    // `[options].parallel_verify = false`.
    val parallelVerify =
        verifySigs && !a.flag("--no-parallel-verify") && config.options.parallel_verify

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

    val policyBuilder: () -> IRelayPolicy = {
        composePolicy(config, advertisedUrl, requireAuth, verifySigs, parallelVerify)
    }

    val stateFile = config.admin.state_file?.let { File(it) }
    val negentropySettings =
        NegentropySettings(
            frameSizeLimit = config.negentropy.frame_size_limit,
            maxSyncEvents = config.negentropy.max_sync_events,
            maxSessionsPerConnection = config.negentropy.max_sessions_per_connection,
        )
    val relay =
        Relay(
            advertisedUrl,
            store,
            info,
            policyBuilder,
            stateFile = stateFile,
            parallelVerify = parallelVerify,
            negentropySettings = negentropySettings,
        )
    // Frame cap honors max_ws_frame_bytes when set; max_ws_message_bytes
    // is treated as the same cap (Ktor's WebSockets plugin only exposes
    // a single per-frame limit; multi-frame messages remain unbounded).
    val frameLimit =
        (config.limits.max_ws_frame_bytes ?: config.limits.max_ws_message_bytes)?.toLong()
    val server =
        LocalRelayServer(
            relay,
            host = host,
            port = port,
            path = path,
            maxFrameBytes = frameLimit,
            adminPubkeys = config.admin.pubkeys.toSet(),
            publicUrl = config.admin.public_url,
            connectionGroupSize = config.network.connection_group_size,
            workerGroupSize = config.network.worker_group_size,
            callGroupSize = config.network.call_group_size,
        ).start()

    Runtime.getRuntime().addShutdownHook(
        Thread {
            // Each step wrapped so a throw in `server.stop()` doesn't
            // skip `relay.close()` (which closes the SQLite store).
            runCatching { server.stop() }
            runCatching { relay.close() }
        },
    )

    println("geode listening on ${server.url}")
    println("NIP-11 info doc: curl -H 'Accept: application/nostr+json' http://$advertisedHost:$port$path")

    // Park the main thread; shutdown hook handles teardown.
    Thread.currentThread().join()
}

/**
 * Builds the policy stack for one connection from the config.
 *
 * Order matters — cheap rejection paths run before expensive ones:
 *   1. AUTH (drops everything if not authenticated)
 *   2. Future-timestamp + allow/deny lists
 *   3. Signature verification (most expensive — Schnorr verify)
 */
private fun composePolicy(
    config: RelayConfig,
    advertisedUrl: com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl,
    requireAuth: Boolean,
    verifySigs: Boolean,
    parallelVerify: Boolean,
): IRelayPolicy {
    val pieces = mutableListOf<IRelayPolicy>()

    if (requireAuth) {
        pieces += FullAuthPolicy(advertisedUrl)
    }

    config.options.reject_future_seconds?.let { secs ->
        pieces += RejectFutureEventsPolicy(secs)
    }

    val auth = config.authorization
    if (auth.kind_whitelist.isNotEmpty() || auth.kind_blacklist.isNotEmpty()) {
        pieces += KindAllowDenyPolicy(auth.kind_whitelist.toSet(), auth.kind_blacklist.toSet())
    }
    if (auth.pubkey_whitelist.isNotEmpty() || auth.pubkey_blacklist.isNotEmpty()) {
        pieces += PubkeyAllowDenyPolicy(auth.pubkey_whitelist.toSet(), auth.pubkey_blacklist.toSet())
    }

    if (verifySigs) {
        // When parallel verify is on, the IngestQueue handles EVENT
        // verification on the writer's CPU fan-out — but AUTH events
        // bypass the queue, so we still need the policy chain to
        // verify those. `VerifyAuthOnlyPolicy` does exactly that.
        pieces += if (parallelVerify) VerifyAuthOnlyPolicy else VerifyPolicy
    }

    return pieces.fold<IRelayPolicy, IRelayPolicy>(EmptyPolicy) { acc, p ->
        if (acc === EmptyPolicy) p else acc + p
    }
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
            // Support both `--key value` and `--key=value`. Splitting
            // on the first `=` lets operators paste config values that
            // happen to contain `=` (e.g. NIP-11 contact emails) by
            // using the space-separated form.
            val eq = a.indexOf('=')
            if (eq > 0) {
                opts[a.substring(0, eq)] = a.substring(eq + 1)
                i += 1
            } else {
                val next = args.getOrNull(i + 1)
                if (next != null && !next.startsWith("--")) {
                    opts[a] = next
                    i += 2
                } else {
                    flags += a
                    i += 1
                }
            }
        } else {
            i += 1
        }
    }
    return Args(opts, flags)
}
