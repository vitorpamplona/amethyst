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
package com.vitorpamplona.amethyst.desktop.napplet

import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.ServerSocketChannel
import java.nio.file.Files
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [DesktopCefEngine.profileInUse]: the check that keeps a second Chromium off a profile another one holds. */
class DesktopCefEngineProfileTest {
    private lateinit var dir: File

    @Before
    fun setUp() {
        // Chromium's own layout: the profile links to a socket in a short temp folder (a Unix socket
        // path has a ~100 byte limit).
        assumeFalse(System.getProperty("os.name").startsWith("Windows"))
        dir = Files.createTempDirectory(File("/tmp").toPath(), "cef").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun profileLinkedTo(socket: File): File {
        val profile = File(dir, "profile").apply { mkdirs() }
        Files.createSymbolicLink(File(profile, "SingletonSocket").toPath(), socket.toPath())
        return profile
    }

    @Test
    fun aFreshProfileIsFree() {
        assertFalse(DesktopCefEngine.profileInUse(File(dir, "profile").apply { mkdirs() }))
    }

    @Test
    fun aProfileWhoseChromiumListensIsInUse() {
        val socket = File(dir, "SingletonSocket")
        ServerSocketChannel.open(StandardProtocolFamily.UNIX).use { server ->
            server.bind(UnixDomainSocketAddress.of(socket.toPath()))
            assertTrue(DesktopCefEngine.profileInUse(profileLinkedTo(socket)))
        }
    }

    @Test
    fun aProfileLeftByACrashedChromiumIsFree() {
        val socket = File(dir, "SingletonSocket")
        // Bound and closed: the socket file stays, and nobody answers on it.
        ServerSocketChannel.open(StandardProtocolFamily.UNIX).use { it.bind(UnixDomainSocketAddress.of(socket.toPath())) }
        assertTrue(socket.exists())
        assertFalse(DesktopCefEngine.profileInUse(profileLinkedTo(socket)))
    }

    @Test
    fun aLinkToNothingIsFree() {
        assertFalse(DesktopCefEngine.profileInUse(profileLinkedTo(File(dir, "gone"))))
    }
}
