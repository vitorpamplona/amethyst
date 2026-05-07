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
package com.vitorpamplona.nestsclient.interop.native

import java.io.File
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Boots a native `moq-relay` subprocess + provides the test sidecar
 * binary paths for the cross-stack interop harness.
 *
 *   - `moq-relay` is `cargo install`ed at the version pinned in
 *     `nestsClient/tests/hang-interop/REV` and cached under
 *     `~/.cache/amethyst-nests-interop/hang-interop-cargo/bin/`.
 *   - TLS: `--tls-generate localhost` so the relay self-signs at
 *     startup. Kotlin clients use the existing
 *     `PermissiveCertValidator` to skip chain validation.
 *   - Auth: `--auth-public ""` so connections need no JWT. Real JWT
 *     issuance is exercised separately by the existing
 *     `NostrNestsAuthInteropTest` against the Docker'd `moq-auth`.
 *
 * One harness instance per test class — startup is ~500 ms once the
 * cached binaries exist, so tests amortise cheaply.
 *
 * **Phase 1 status**: harness boots the relay and exposes paths to
 * the (stub) sidecar binaries `hang-listen` / `hang-publish` /
 * `udp-loss-shim`. Phase 2 fills in the sidecars' real subscribe /
 * publish loops. See
 * `nestsClient/plans/2026-05-06-cross-stack-interop-test.md`.
 */
class NativeMoqRelayHarness private constructor(
    private val relayProcess: Process,
    private val relayPort: Int,
    private val sidecarsDir: Path,
    private val cargoBinDir: Path,
) : AutoCloseable {
    private var stopped = false

    /** Base relay URL. Append a broadcast namespace for connections. */
    val relayUrl: String get() = "https://127.0.0.1:$relayPort"

    /** UDP loopback (host, port) the relay listens on. */
    fun loopbackHostPort(): Pair<String, Int> = "127.0.0.1" to relayPort

    /** Path to the (Phase-1 stub) hang-listen binary. */
    fun hangListenBin(): Path = sidecarsDir.resolve(binName("hang-listen"))

    /** Path to the (Phase-1 stub) hang-publish binary. */
    fun hangPublishBin(): Path = sidecarsDir.resolve(binName("hang-publish"))

    /** Path to the (Phase-1 stub) udp-loss-shim binary. */
    fun udpLossShimBin(): Path = sidecarsDir.resolve(binName("udp-loss-shim"))

    /** Path to the cargo-installed `moq-token-cli` binary. */
    fun moqTokenBin(): Path = cargoBinDir.resolve(binName("moq-token-cli"))

    override fun close() {
        if (stopped) return
        stopped = true
        runCatching { relayProcess.destroy() }
        if (!relayProcess.waitFor(5, TimeUnit.SECONDS)) {
            runCatching { relayProcess.destroyForcibly() }
        }
    }

    companion object {
        /** Gate property — mirrors `nestsInterop`. */
        const val ENABLE_PROPERTY = "nestsHangInterop"

        /**
         * `interopBuildHangSidecars` writes this. Points to
         * `nestsClient/tests/hang-interop/target/release` where the (Phase-1
         * stub) sidecar binaries live.
         */
        const val SIDECARS_DIR_PROPERTY = "nestsHangInteropSidecarsDir"

        /**
         * Cargo install root used by `interopInstallMoqRelay` /
         * `interopInstallMoqTokenCli`. Sub-directory `bin/` holds
         * `moq-relay` + `moq-token`.
         */
        const val CARGO_BIN_DIR_PROPERTY = "nestsHangInteropCargoBinDir"

        private const val PORT_READY_TIMEOUT_MS = 30_000L
        private const val PORT_PROBE_INTERVAL_MS = 200L

        fun isEnabled(): Boolean = System.getProperty(ENABLE_PROPERTY) == "true"

        /**
         * JUnit "skipped" if the gate isn't on, like the existing
         * [com.vitorpamplona.nestsclient.interop.NostrNestsHarness.assumeNestsInterop].
         */
        fun assumeHangInterop() {
            if (isEnabled()) return
            val msg =
                "Skipping cross-stack hang interop test — set -D$ENABLE_PROPERTY=true to enable. " +
                    "See nestsClient/plans/2026-05-06-cross-stack-interop-test.md."
            try {
                val assume = Class.forName("org.junit.Assume")
                val assumeTrue = assume.getMethod("assumeTrue", String::class.java, Boolean::class.javaPrimitiveType)
                assumeTrue.invoke(null, msg, false)
            } catch (e: java.lang.reflect.InvocationTargetException) {
                throw e.targetException ?: e
            } catch (_: ClassNotFoundException) {
                throw IllegalStateException(msg)
            }
        }

        @Volatile private var shared: NativeMoqRelayHarness? = null
        private val sharedLock = Any()

        /**
         * Bring the relay up if not already running; reuses the same
         * subprocess across test classes within one JVM run. Mirrors
         * the singleton pattern in [com.vitorpamplona.nestsclient.interop.NostrNestsHarness].
         */
        fun shared(): NativeMoqRelayHarness {
            shared?.let { return it }
            synchronized(sharedLock) {
                shared?.let { return it }
                val instance = doStart()
                Runtime.getRuntime().addShutdownHook(
                    Thread({ runCatching { instance.close() } }, "NativeMoqRelayHarness-shutdown"),
                )
                shared = instance
                return instance
            }
        }

        /**
         * Tear down the current shared relay subprocess (if any) so the
         * next [shared] call boots a fresh one. Used by per-method
         * `@BeforeTest` hooks in `HangInteropTest` /
         * `BrowserInteropTest` to keep relay-side accumulated state
         * (per-subscriber forward queues, announce tables) from
         * leaking between scenarios. Each scenario then runs against
         * a relay that started ~500 ms before the test body.
         */
        fun resetShared() {
            synchronized(sharedLock) {
                shared?.let {
                    runCatching { it.close() }
                }
                shared = null
            }
        }

        private fun doStart(): NativeMoqRelayHarness {
            check(isEnabled()) {
                "NativeMoqRelayHarness.shared() called without -D$ENABLE_PROPERTY=true."
            }

            val sidecarsDir = requireDirProperty(SIDECARS_DIR_PROPERTY)
            val cargoBinDir = requireDirProperty(CARGO_BIN_DIR_PROPERTY)

            val moqRelay = cargoBinDir.resolve(binName("moq-relay"))
            check(Files.isExecutable(moqRelay)) {
                "moq-relay not found at $moqRelay — did `interopBuildHangSidecars` run? " +
                    "Try: ./gradlew :nestsClient:interopBuildHangSidecars"
            }
            check(Files.isExecutable(sidecarsDir.resolve(binName("hang-listen")))) {
                "hang-listen sidecar not found under $sidecarsDir — did `interopBuildSidecars` run?"
            }

            val port = reservePort()
            val pb =
                ProcessBuilder(
                    moqRelay.toString(),
                    "--server-bind",
                    "127.0.0.1:$port",
                    // moq-relay also opens an outbound clustering
                    // client; its default `[::]:0` bind fails in
                    // sandboxes without IPv6 (errno 97 EAFNOSUPPORT).
                    // Pin to IPv4 loopback to keep the harness
                    // portable across CI runners.
                    "--client-bind",
                    "127.0.0.1:0",
                    "--tls-generate",
                    "localhost",
                    // Empty prefix grants pub+sub on every path, so
                    // tests don't need to mint JWTs. The
                    // moq-relay/auth.rs `verify` path falls through
                    // to public access when no JWT is present.
                    "--auth-public",
                    "",
                    "--log-level",
                    "info",
                ).redirectErrorStream(true)

            val process = pb.start()
            val drainer = ProcessOutputDrainer(process, "moq-relay").also { it.start() }

            try {
                // moq-relay logs `addr=… listening` on bind. Wait for
                // that line — strictly more reliable than a port
                // probe (TCP probes succeed on a UDP-only listener,
                // and UDP probes against a SO_REUSEPORT-bound socket
                // can also succeed even when the relay is healthy).
                drainer.waitForLine("listening", PORT_READY_TIMEOUT_MS)
                // Belt-and-braces: also confirm the UDP port is bound.
                // If something's broken in the log path this still
                // catches a non-listening relay within a few seconds.
                waitForUdpBound("127.0.0.1", port, 3_000L)
            } catch (t: Throwable) {
                // Best-effort log capture before tearing down so the
                // failure includes WHY the relay didn't come up.
                val tail = drainer.tail()
                runCatching { process.destroyForcibly() }
                throw IllegalStateException(
                    "moq-relay did not become ready on 127.0.0.1:$port within " +
                        "${PORT_READY_TIMEOUT_MS}ms.\n--- moq-relay log tail ---\n$tail",
                    t,
                )
            }

            return NativeMoqRelayHarness(
                relayProcess = process,
                relayPort = port,
                sidecarsDir = sidecarsDir,
                cargoBinDir = cargoBinDir,
            )
        }

        private fun requireDirProperty(name: String): Path {
            val raw = System.getProperty(name)
            check(!raw.isNullOrBlank()) {
                "system property '$name' not set — did the Gradle test task forward it? " +
                    "(see :nestsClient build.gradle.kts)"
            }
            val path = File(raw).toPath()
            check(Files.isDirectory(path)) {
                "system property '$name' = '$raw' is not a directory; " +
                    "did `interopBuildHangSidecars` run?"
            }
            return path
        }

        /**
         * Ask the OS for a free TCP port, close the socket, and use
         * that port number for the relay's UDP listener. There's a
         * tiny window where another process could grab the port; in
         * practice CI loopback is uncontested. Reused from the same
         * pattern used elsewhere in the test infra.
         */
        private fun reservePort(): Int {
            ServerSocket(0).use { return it.localPort }
        }

        /**
         * Confirm the relay's UDP port is bound by trying to bind a
         * *second* `DatagramSocket` on it. If the OS rejects with
         * `BindException`, something owns the port — almost
         * certainly the relay we just spawned. UDP namespace is
         * separate from TCP, so this is the only kind of probe that
         * meaningfully reports "is the relay listening" on a
         * QUIC-only data plane.
         *
         * Defaults to `SO_REUSEADDR=false` so a relay bound without
         * `SO_REUSEPORT` correctly fails our second bind. Falls back
         * to the relay's startup log line as the primary signal —
         * see `doStart` callers.
         */
        private fun waitForUdpBound(
            host: String,
            port: Int,
            timeoutMs: Long,
        ) {
            val deadline = System.currentTimeMillis() + timeoutMs
            var lastError: Throwable? = null
            while (System.currentTimeMillis() < deadline) {
                try {
                    val probe = DatagramSocket(null)
                    probe.reuseAddress = false
                    try {
                        probe.bind(InetSocketAddress(host, port))
                        // Bind succeeded → nothing else is on this
                        // UDP port. The relay isn't bound yet.
                    } finally {
                        probe.close()
                    }
                    Thread.sleep(PORT_PROBE_INTERVAL_MS)
                } catch (_: java.net.BindException) {
                    return
                } catch (t: Throwable) {
                    lastError = t
                    Thread.sleep(PORT_PROBE_INTERVAL_MS)
                }
            }
            throw IllegalStateException(
                "moq-relay UDP port $host:$port did not bind within ${timeoutMs}ms",
                lastError,
            )
        }

        private fun binName(stem: String): String =
            if (System
                    .getProperty("os.name")
                    .orEmpty()
                    .lowercase()
                    .contains("win")
            ) {
                "$stem.exe"
            } else {
                stem
            }
    }
}

/**
 * Reads the subprocess's combined stdout/stderr into a bounded ring
 * so the harness can include the tail in a failure message. Without
 * this the relay's log line `listening on 127.0.0.1:<port>` is the
 * only signal that startup succeeded, and a silent error (cert
 * generation failure, port collision after the OS reservation, …)
 * leaves us with nothing to include in the assertion.
 */
private class ProcessOutputDrainer(
    private val process: Process,
    private val name: String,
) {
    private val ring = ConcurrentLinkedQueue<String>()
    private val maxLines = 64
    private var thread: Thread? = null
    private val lock = ReentrantLock()
    private val newLineCond = lock.newCondition()

    fun start() {
        thread =
            Thread({
                process.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        ring.add(line)
                        while (ring.size > maxLines) ring.poll()
                        lock.withLock { newLineCond.signalAll() }
                    }
                }
            }, "NativeMoqRelayHarness-$name").apply {
                isDaemon = true
                start()
            }
    }

    fun tail(): String = ring.joinToString("\n")

    /**
     * Block until the drainer has observed a line containing
     * [needle], scanning lines that have already been buffered
     * (handles the race where the relay finished logging "listening"
     * before [waitForLine] was called) plus any new lines that
     * arrive within [timeoutMs]. Throws if the deadline expires
     * before a match. Substring match rather than regex to keep
     * upstream-log-format tweaks from breaking us.
     */
    fun waitForLine(
        needle: String,
        timeoutMs: Long,
    ) {
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        if (ring.any { it.contains(needle) }) return
        lock.withLock {
            while (true) {
                if (ring.any { it.contains(needle) }) return
                val remaining = deadlineNanos - System.nanoTime()
                if (remaining <= 0) {
                    throw IllegalStateException(
                        "did not observe '$needle' in $name output within ${timeoutMs}ms",
                    )
                }
                newLineCond.awaitNanos(remaining)
            }
        }
    }
}
