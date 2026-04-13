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
package com.vitorpamplona.amethyst.desktop.service.namecoin

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.prefs.Preferences

class DesktopNamecoinPreferencesTest {
    private lateinit var testPrefs: Preferences
    private lateinit var namecoinPrefs: DesktopNamecoinPreferences

    @Before
    fun setup() {
        // Use a unique test node to avoid polluting real preferences
        testPrefs = Preferences.userRoot().node("amethyst-test-namecoin-${System.nanoTime()}")
        namecoinPrefs = DesktopNamecoinPreferences(prefs = testPrefs)
    }

    @After
    fun cleanup() {
        try {
            testPrefs.removeNode()
        } catch (_: Exception) {
        }
    }

    @Test
    fun `default settings are enabled with no custom servers`() {
        val settings = namecoinPrefs.current
        assertTrue(settings.enabled)
        assertTrue(settings.customServers.isEmpty())
        assertFalse(settings.hasCustomServers)
    }

    @Test
    fun `setEnabled persists and updates flow`() =
        runBlocking {
            namecoinPrefs.setEnabled(false)
            assertFalse(namecoinPrefs.current.enabled)
            assertFalse(namecoinPrefs.settings.value.enabled)

            // Verify persistence by creating a new instance with the same prefs node
            val reloaded = DesktopNamecoinPreferences(prefs = testPrefs)
            assertFalse(reloaded.current.enabled)
        }

    @Test
    fun `addServer persists and updates flow`() =
        runBlocking {
            namecoinPrefs.addServer("example.com:50006")
            assertEquals(listOf("example.com:50006"), namecoinPrefs.current.customServers)
            assertTrue(namecoinPrefs.current.hasCustomServers)

            // Verify persistence
            val reloaded = DesktopNamecoinPreferences(prefs = testPrefs)
            assertEquals(listOf("example.com:50006"), reloaded.current.customServers)
        }

    @Test
    fun `addServer ignores blank strings`() =
        runBlocking {
            namecoinPrefs.addServer("")
            namecoinPrefs.addServer("   ")
            assertTrue(namecoinPrefs.current.customServers.isEmpty())
        }

    @Test
    fun `addServer ignores duplicates`() =
        runBlocking {
            namecoinPrefs.addServer("example.com:50006")
            namecoinPrefs.addServer("example.com:50006")
            assertEquals(1, namecoinPrefs.current.customServers.size)
        }

    @Test
    fun `removeServer persists and updates flow`() =
        runBlocking {
            namecoinPrefs.addServer("server1.com:50006")
            namecoinPrefs.addServer("server2.com:50001:tcp")
            assertEquals(2, namecoinPrefs.current.customServers.size)

            namecoinPrefs.removeServer("server1.com:50006")
            assertEquals(listOf("server2.com:50001:tcp"), namecoinPrefs.current.customServers)

            // Verify persistence
            val reloaded = DesktopNamecoinPreferences(prefs = testPrefs)
            assertEquals(listOf("server2.com:50001:tcp"), reloaded.current.customServers)
        }

    @Test
    fun `reset restores defaults`() =
        runBlocking {
            namecoinPrefs.setEnabled(false)
            namecoinPrefs.addServer("example.com:50006")
            assertFalse(namecoinPrefs.current.enabled)
            assertTrue(namecoinPrefs.current.hasCustomServers)

            namecoinPrefs.reset()
            assertTrue(namecoinPrefs.current.enabled)
            assertFalse(namecoinPrefs.current.hasCustomServers)

            // Verify persistence
            val reloaded = DesktopNamecoinPreferences(prefs = testPrefs)
            assertTrue(reloaded.current.enabled)
            assertFalse(reloaded.current.hasCustomServers)
        }

    @Test
    fun `customServersOrNull returns null when empty`() {
        assertEquals(null, namecoinPrefs.customServersOrNull)
    }

    @Test
    fun `customServersOrNull returns parsed servers when configured`() =
        runBlocking {
            namecoinPrefs.addServer("example.com:50006")
            val servers = namecoinPrefs.customServersOrNull
            assertEquals(1, servers?.size)
            assertEquals("example.com", servers?.first()?.host)
            assertEquals(50006, servers?.first()?.port)
            assertTrue(servers?.first()?.useSsl == true)
        }

    @Test
    fun `round-trip multiple operations`() =
        runBlocking {
            namecoinPrefs.setEnabled(true)
            namecoinPrefs.addServer("server1.com:50006")
            namecoinPrefs.addServer("onion.onion:50001:tcp")
            namecoinPrefs.setEnabled(false)
            namecoinPrefs.removeServer("server1.com:50006")

            val settings = namecoinPrefs.current
            assertFalse(settings.enabled)
            assertEquals(listOf("onion.onion:50001:tcp"), settings.customServers)

            // Verify full persistence round-trip
            val reloaded = DesktopNamecoinPreferences(prefs = testPrefs)
            assertEquals(settings, reloaded.current)
        }
}
