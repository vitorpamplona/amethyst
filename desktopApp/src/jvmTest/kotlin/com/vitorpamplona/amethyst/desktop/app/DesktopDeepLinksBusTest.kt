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
package com.vitorpamplona.amethyst.desktop.app

import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import kotlin.concurrent.thread
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The loopback instance bus: the ack round-trip, the stale port file reading as "no instance",
 * the read timeout on accepted connections, and the single-line read.
 */
class DesktopDeepLinksBusTest {
    companion object {
        // The bus is a process-wide singleton, so every test shares one instance on this dir.
        private val busDir: File = Files.createTempDirectory("deep-link-bus").toFile()

        private fun busPort(): Int = File(busDir, "instance.port").readText().trim().toInt()
    }

    @BeforeTest
    fun setUp() {
        DesktopDeepLinks.startInstanceBus(busDir)
        DesktopDeepLinks.pending.value = null
    }

    @Test
    fun forwardedLinkIsAckedAndQueued() {
        assertTrue(DesktopDeepLinks.forwardToRunningInstance("nostr:note1deadbeef", busDir))
        // The instance queues the link before it writes the ack, so this is already routed.
        assertEquals("nostr:note1deadbeef", DesktopDeepLinks.pending.value)
    }

    @Test
    fun deadPortMeansNoRunningInstance() {
        val dir = Files.createTempDirectory("deep-link-dead").toFile()
        val port = ServerSocket(0, 50, InetAddress.getLoopbackAddress()).use { it.localPort }
        File(dir, "instance.port").writeText(port.toString())
        assertFalse(DesktopDeepLinks.forwardToRunningInstance("nostr:note1deadbeef", dir))
        assertNull(DesktopDeepLinks.pending.value)
    }

    @Test
    fun silentListenerMeansNoRunningInstance() {
        // A crashed app's port can be taken over by a process that never answers with the ack;
        // the forwarder must not report the link as delivered.
        val dir = Files.createTempDirectory("deep-link-silent").toFile()
        val silent = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        File(dir, "instance.port").writeText(silent.localPort.toString())
        thread(isDaemon = true) { runCatching { silent.accept().use { Thread.sleep(5_000) } } }
        try {
            assertFalse(DesktopDeepLinks.forwardToRunningInstance("nostr:note1deadbeef", dir))
        } finally {
            runCatching { silent.close() }
        }
        assertNull(DesktopDeepLinks.pending.value)
    }

    @Test
    fun unreadablePortFileMeansNoInstance() {
        val dir = Files.createTempDirectory("deep-link-garbage").toFile()
        File(dir, "instance.port").writeText("not-a-port")
        assertFalse(DesktopDeepLinks.forwardToRunningInstance("nostr:note1deadbeef", dir))
        assertNull(DesktopDeepLinks.pending.value)
    }

    @Test
    fun onlyTheFirstLineIsRouted() {
        Socket(InetAddress.getLoopbackAddress(), busPort()).use { socket ->
            socket.soTimeout = 5_000
            socket.getOutputStream().write("nostr:first\nnostr:second\n".toByteArray(Charsets.UTF_8))
            socket.getOutputStream().flush()
            assertEquals("ok", socket.getInputStream().bufferedReader().readLine())
        }
        assertEquals("nostr:first", DesktopDeepLinks.pending.value)
    }

    @Test
    fun nonDeepLinkGetsNoAck() {
        Socket(InetAddress.getLoopbackAddress(), busPort()).use { socket ->
            socket.soTimeout = 5_000
            socket.getOutputStream().write("https://example.com\n".toByteArray(Charsets.UTF_8))
            socket.getOutputStream().flush()
            assertNull(socket.getInputStream().bufferedReader().readLine())
        }
        assertNull(DesktopDeepLinks.pending.value)
    }

    @Test
    fun idleConnectionIsDroppedByTheReadTimeout() {
        // Sends nothing: the bus must time the connection out (a few seconds) and close it,
        // not pin a thread on the read forever.
        Socket(InetAddress.getLoopbackAddress(), busPort()).use { socket ->
            socket.soTimeout = 15_000
            assertEquals(-1, socket.getInputStream().read())
        }
    }

    @Test
    fun theAckDoesNotWaitForTheWindowToComeForward() {
        // The first foreground request initialises the desktop toolkit (GTK on Linux), which can
        // take longer than the 2 s the forwarder waits: the link must still be acked in time.
        val original = DesktopDeepLinks.requestForeground
        DesktopDeepLinks.requestForeground = { Thread.sleep(3_000) }
        try {
            assertTrue(DesktopDeepLinks.forwardToRunningInstance("nostr:note1slowwindow", busDir))
            assertEquals("nostr:note1slowwindow", DesktopDeepLinks.pending.value)
        } finally {
            DesktopDeepLinks.requestForeground = original
        }
    }
}
