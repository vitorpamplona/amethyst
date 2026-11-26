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
package com.vitorpamplona.amethyst.ios.tor

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * The C ABI's lifecycle, through the real Rust library on the simulator. Needs no network:
 * creating the client and binding the listener are local, and nothing here waits for the
 * directory download that initialize() starts in the background.
 */
class ArtiNativeLifecycleTest {
    @Test
    fun initializeSocksStopDestroyAndReinitialize() {
        val log = LogCollector()
        assertEquals(0, ArtiNative.setLogCallback(log::add))
        try {
            assertTrue(ArtiNative.version.startsWith("Arti 2.6.0"), ArtiNative.version)

            // Start clean whatever ran before in this process.
            assertEquals(0, ArtiNative.destroy())
            assertEquals(-1, ArtiNative.isBootstrapped())
            assertEquals(-1, ArtiNative.bootstrapProgressPermille())
            assertEquals(-1, ArtiNative.startSocksProxy(17392), "no client yet")

            val dir = freshArtiDir("lifecycle")
            val started = TimeSource.Monotonic.markNow()
            assertEquals(0, ArtiNative.initialize(dir))
            println("B12A lifecycle: initialize took ${started.elapsedNow().inWholeMilliseconds}ms")
            assertEquals(0, ArtiNative.initialize(dir), "a second initialize reuses the client")

            assertTrue(ArtiNative.isBootstrapped() in 0..1)
            assertTrue(ArtiNative.bootstrapProgressPermille() in 0..1000)

            val port = startSocksOnFreePort()
            assertTrue(port > 0, "no free port in 17392..17412")
            val accepted = ArtiNative.socksAcceptedCount()
            assertContentEquals(byteArrayOf(5, 0), socksGreeting(port), "SOCKS5, no auth")
            assertEquals(accepted + 1, ArtiNative.socksAcceptedCount())

            // Restarting on the same port replaces the listener rather than failing to bind.
            assertEquals(0, ArtiNative.startSocksProxy(port), "restart on the same port: ${log.lines.takeLast(6)}")
            assertContentEquals(byteArrayOf(5, 0), socksGreeting(port))

            assertEquals(0, ArtiNative.stopSocksProxy())
            assertNull(socksGreeting(port), "nothing listens after stop")

            val destroying = TimeSource.Monotonic.markNow()
            assertEquals(0, ArtiNative.destroy())
            println("B12A lifecycle: destroy took ${destroying.elapsedNow().inWholeMilliseconds}ms")
            assertEquals(-1, ArtiNative.isBootstrapped())

            // destroy() released the state file lock, so the same directory opens again.
            assertEquals(0, ArtiNative.initialize(dir), "re-initialize after destroy")
            assertEquals(0, ArtiNative.destroy())

            val lines = log.lines
            assertTrue(lines.any { it == "Log callback registered" }, lines.toString())
            assertTrue(lines.count { it == "Arti initialized successfully" } == 2, lines.toString())
            assertTrue(lines.any { it == "Arti client destroyed" }, lines.toString())
            println("B12A lifecycle: ${lines.size} log lines, e.g. ${lines.take(6)}")
        } finally {
            ArtiNative.setLogCallback(null)
        }
    }
}
