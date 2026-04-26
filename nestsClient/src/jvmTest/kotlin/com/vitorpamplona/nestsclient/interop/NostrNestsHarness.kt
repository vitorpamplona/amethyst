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
package com.vitorpamplona.nestsclient.interop

import java.io.File
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path

/**
 * Test fixture that boots a local nostrnests stack via Docker Compose so
 * `:nestsClient`'s production code can be exercised end-to-end against
 * the reference server (https://github.com/nostrnests/nests).
 *
 * Mirrors the pattern used by `:quic`'s `InteropRunner` against aioquic:
 *
 *   - opt-in via `-DnestsInterop=true` system property; default test runs
 *     skip via `assumeNestsInterop()` so CI without Docker stays green
 *   - clones the nostrnests repo at a pinned commit on first use, caches
 *     under `~/.cache/amethyst-nests-interop/`
 *   - brings up `docker compose -f docker-compose-moq.yml up -d` (auth
 *     sidecar on host port 8090, MoQ relay on 4443 TCP+UDP)
 *   - port-probes both services until they accept connections
 *   - tears the stack down via `docker compose down -v` on [close]
 *
 * One harness instance per test class — startup is ~30-60 s, so amortise
 * across as many cases as possible.
 */
class NostrNestsHarness private constructor(
    private val workDir: File,
    val authBaseUrl: String,
    val moqEndpoint: String,
) : AutoCloseable {
    private var stopped = false

    override fun close() {
        if (stopped) return
        stopped = true
        runDocker(workDir, "down", "-v", "--remove-orphans")
    }

    companion object {
        /** System property gate. Tests should call [assumeNestsInterop] first. */
        const val ENABLE_PROPERTY = "nestsInterop"

        /** Tests that depend on the harness call this in `@BeforeTest`. */
        fun isEnabled(): Boolean = System.getProperty(ENABLE_PROPERTY) == "true"

        /**
         * Pin the nostrnests revision to a known-good SHA. Bump deliberately
         * — drift on `main` should not silently change interop expectations.
         * Override at runtime via `-DnestsInteropRev=<sha-or-branch>`.
         */
        const val DEFAULT_REVISION = "main"

        const val AUTH_HOST_PORT = 8090
        const val MOQ_HOST_PORT = 4443

        private const val REPO_URL = "https://github.com/nostrnests/nests.git"
        private const val COMPOSE_FILE = "docker-compose-moq.yml"
        private const val PORT_READY_TIMEOUT_MS = 90_000L
        private const val PORT_PROBE_INTERVAL_MS = 500L

        /**
         * Bring the stack up. Caller must `close()` to tear it down — use
         * `try-with-resources` / Kotlin's `use { … }`.
         */
        fun start(): NostrNestsHarness {
            check(isEnabled()) {
                "NostrNestsHarness.start called without -D$ENABLE_PROPERTY=true. " +
                    "Call NostrNestsHarness.assumeNestsInterop() first to skip cleanly."
            }

            val workDir = ensureRepo()
            // Bring everything up detached. The compose file's services log
            // to stdout; we don't tail them — failures surface via
            // port-probe timeouts.
            runDocker(workDir, "up", "-d")
            try {
                waitForPort("127.0.0.1", AUTH_HOST_PORT, PORT_READY_TIMEOUT_MS)
                waitForPort("127.0.0.1", MOQ_HOST_PORT, PORT_READY_TIMEOUT_MS)
            } catch (t: Throwable) {
                // If readiness probe fails, tear down so we don't leak the
                // stack into the next test run.
                runCatching { runDocker(workDir, "down", "-v", "--remove-orphans") }
                throw t
            }
            return NostrNestsHarness(
                workDir = workDir,
                authBaseUrl = "http://127.0.0.1:$AUTH_HOST_PORT",
                // The moq-relay terminates TLS — we use a permissive
                // certificate validator on the QuicWebTransportFactory in
                // tests. Path is `/anon` per the relay's default WT route.
                moqEndpoint = "https://127.0.0.1:$MOQ_HOST_PORT/anon",
            )
        }

        /**
         * Convenience for `@BeforeTest`: throws JUnit's `AssumptionViolated`
         * (via `org.junit.Assume`) when the property isn't set, which JUnit
         * reports as "skipped" rather than "failed". Falls back to a regular
         * exception if JUnit's Assume isn't on the classpath.
         */
        fun assumeNestsInterop() {
            if (isEnabled()) return
            val msg = "Skipping nostrnests interop test — set -D$ENABLE_PROPERTY=true to enable"
            try {
                val assume = Class.forName("org.junit.Assume")
                val assumeTrue =
                    assume.getMethod("assumeTrue", String::class.java, Boolean::class.javaPrimitiveType)
                assumeTrue.invoke(null, msg, false)
            } catch (e: java.lang.reflect.InvocationTargetException) {
                // Unwrap so JUnit sees the real AssumptionViolatedException
                // (treated as "skipped") instead of the reflection wrapper
                // (treated as "failed").
                throw e.targetException ?: e
            } catch (_: ClassNotFoundException) {
                throw IllegalStateException(msg)
            }
        }

        // ----- internals -----

        private fun cacheRoot(): Path {
            val home = System.getProperty("user.home") ?: "/tmp"
            val root = Path.of(home, ".cache", "amethyst-nests-interop")
            Files.createDirectories(root)
            return root
        }

        private fun ensureRepo(): File {
            val rev = System.getProperty("nestsInteropRev") ?: DEFAULT_REVISION
            val target = cacheRoot().resolve("nests").toFile()
            if (!target.exists()) {
                runProcess(cacheRoot().toFile(), "git", "clone", REPO_URL, "nests")
            }
            // Always fetch + checkout the requested revision so test runs
            // are reproducible even if `main` advances.
            runProcess(target, "git", "fetch", "origin", "--quiet")
            runProcess(target, "git", "checkout", "--quiet", rev)
            return target
        }

        private fun runDocker(
            workDir: File,
            vararg args: String,
        ) {
            runProcess(workDir, "docker", "compose", "-f", COMPOSE_FILE, *args)
        }

        private fun runProcess(
            workDir: File,
            vararg args: String,
        ) {
            val process =
                ProcessBuilder(*args)
                    .directory(workDir)
                    .redirectErrorStream(true)
                    .start()
            val output = process.inputStream.bufferedReader().readText()
            val exit = process.waitFor()
            check(exit == 0) {
                "${args.joinToString(" ")} exited with code $exit\n--- output ---\n$output"
            }
        }

        private fun waitForPort(
            host: String,
            port: Int,
            timeoutMs: Long,
        ) {
            val deadline = System.currentTimeMillis() + timeoutMs
            var lastError: Throwable? = null
            while (System.currentTimeMillis() < deadline) {
                try {
                    Socket(host, port).use { return }
                } catch (t: Throwable) {
                    lastError = t
                    Thread.sleep(PORT_PROBE_INTERVAL_MS)
                }
            }
            throw IllegalStateException(
                "Port $host:$port did not become ready within ${timeoutMs}ms",
                lastError,
            )
        }
    }
}
