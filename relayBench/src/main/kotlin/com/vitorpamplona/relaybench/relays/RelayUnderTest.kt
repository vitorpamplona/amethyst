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
package com.vitorpamplona.relaybench.relays

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.ServerSocket
import java.net.Socket

/**
 * A relay implementation the harness can boot as an external process.
 *
 * Contract: [command] must start a relay that listens on `127.0.0.1:port`
 * with persistent storage under `dataDir`, signature verification ON, no
 * auth required, and otherwise stock defaults — that's the "similar setup"
 * every implementation is compared under. Adding a relay to the benchmark
 * means adding one subclass (or using [CustomRelay] with a command
 * template, no code needed).
 */
abstract class RelayUnderTest(
    val name: String,
) {
    abstract fun command(
        port: Int,
        dataDir: File,
    ): List<String>

    /** Hook for writing config files before launch. */
    open fun prepare(
        port: Int,
        dataDir: File,
    ) {}

    fun start(workDir: File): RunningRelay {
        val port = ServerSocket(0).use { it.localPort }
        val dataDir = File(workDir, "$name-$port").apply { mkdirs() }
        prepare(port, dataDir)
        val logFile = File(dataDir, "$name.log")
        val process =
            ProcessBuilder(command(port, dataDir))
                .redirectErrorStream(true)
                .redirectOutput(logFile)
                .start()

        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive) break
            try {
                Socket("127.0.0.1", port).close()
                return RunningRelay(this, process, port, dataDir, logFile)
            } catch (_: Exception) {
                Thread.sleep(50)
            }
        }
        process.destroyForcibly()
        throw IllegalStateException(
            "$name did not open port $port within 30s. Log tail:\n" + logFile.tail(20),
        )
    }
}

class RunningRelay(
    val relay: RelayUnderTest,
    private val process: Process,
    val port: Int,
    val dataDir: File,
    val logFile: File,
) {
    val wsUrl: String get() = "ws://127.0.0.1:$port"

    fun ensureAlive() {
        check(process.isAlive) {
            "${relay.name} process died mid-benchmark. Log tail:\n" + logFile.tail(20)
        }
    }

    /** NIP-11 info document — name/software/version for the report header. */
    fun fetchInfo(http: OkHttpClient): Nip11Info? =
        runCatching {
            val request =
                Request
                    .Builder()
                    .url("http://127.0.0.1:$port/")
                    .header("Accept", "application/nostr+json")
                    .build()
            http.newCall(request).execute().use { response ->
                val node = jacksonObjectMapper().readTree(response.body.string())
                Nip11Info(
                    software = node["software"]?.asText()?.substringAfterLast('/')?.removeSuffix(".git"),
                    version = node["version"]?.asText(),
                )
            }
        }.getOrNull()

    /** On-disk footprint of everything the relay wrote (DB + logs excluded). */
    fun storageBytes(): Long =
        dataDir
            .walkTopDown()
            .filter { it.isFile && it != logFile }
            .sumOf { it.length() }

    fun stop() {
        process.destroy()
        if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor()
        }
    }
}

data class Nip11Info(
    val software: String?,
    val version: String?,
)

/** Geode — the standalone JVM relay from this repo (`:geode:installDist`). */
class GeodeRelay(
    private val bin: String,
) : RelayUnderTest("geode") {
    override fun command(
        port: Int,
        dataDir: File,
    ): List<String> =
        listOf(
            bin,
            "--host",
            "127.0.0.1",
            "--port",
            "$port",
            "--db",
            File(dataDir, "geode.sqlite").absolutePath,
        )
}

/**
 * strfry — writes a minimal config (proven against strfry v1 by geode's
 * interop tests) and lets compiled-in defaults govern everything else.
 * `rejectEventsOlderThanSeconds` is raised to 100 years (0 would reject
 * *everything*, not disable the check) so historical corpora (like the
 * checked-in 2024 dump) replay 1:1; geode has no old-event cutoff, so this
 * keeps the two setups equivalent. `nofiles = 0` skips the setrlimit call,
 * which fails inside containers with a low hard limit. Event size
 * and tag-count ceilings come from the harness options because the corpus
 * is pre-filtered to the same numbers — both relays are always offered a
 * stream they are configured to fully accept.
 */
class StrfryRelay(
    private val bin: String,
    private val maxEventBytes: Int,
    private val maxTags: Int,
) : RelayUnderTest("strfry") {
    override fun prepare(
        port: Int,
        dataDir: File,
    ) {
        File(dataDir, "strfry-db").mkdirs()
        File(dataDir, "strfry.conf").writeText(
            """
            db = "${File(dataDir, "strfry-db").absolutePath}"
            events {
                maxEventSize = $maxEventBytes
                rejectEventsOlderThanSeconds = 3155760000
                maxNumTags = $maxTags
            }
            relay {
                bind = "127.0.0.1"
                port = $port
                nofiles = 0
                maxWebsocketPayloadSize = ${maxEventBytes + 65536}
            }
            """.trimIndent(),
        )
    }

    override fun command(
        port: Int,
        dataDir: File,
    ): List<String> = listOf(bin, "--config", File(dataDir, "strfry.conf").absolutePath, "relay")
}

/**
 * Any other relay, from a command template: `{port}` and `{dir}` are
 * substituted at launch. Example:
 *
 *   --relay 'nostr-rs-relay=/usr/bin/nostr-rs-relay --db {dir} --port {port}'
 */
class CustomRelay(
    name: String,
    private val template: String,
) : RelayUnderTest(name) {
    override fun command(
        port: Int,
        dataDir: File,
    ): List<String> =
        template
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .map { it.replace("{port}", "$port").replace("{dir}", dataDir.absolutePath) }
}

private fun File.tail(lines: Int): String = if (exists()) readLines().takeLast(lines).joinToString("\n") else "(no log)"
