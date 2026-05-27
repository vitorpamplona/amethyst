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

import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.NamecoinSettings
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinBackend
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinCoreRpcConfig
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

    // ── Pinned certs ─────────────────────────────────────────────────────

    private val samplePem1 =
        "-----BEGIN CERTIFICATE-----\nAAAA\n-----END CERTIFICATE-----"
    private val samplePem2 =
        "-----BEGIN CERTIFICATE-----\nBBBB\n-----END CERTIFICATE-----"

    @Test
    fun `loadPinnedCerts returns empty by default`() {
        assertTrue(namecoinPrefs.loadPinnedCerts().isEmpty())
    }

    @Test
    fun `addPinnedCert persists and survives reload`() {
        namecoinPrefs.addPinnedCert(samplePem1)
        assertEquals(listOf(samplePem1), namecoinPrefs.loadPinnedCerts())

        val reloaded = DesktopNamecoinPreferences(prefs = testPrefs)
        assertEquals(listOf(samplePem1), reloaded.loadPinnedCerts())
    }

    @Test
    fun `addPinnedCert appends without duplicating`() {
        namecoinPrefs.addPinnedCert(samplePem1)
        namecoinPrefs.addPinnedCert(samplePem2)
        namecoinPrefs.addPinnedCert(samplePem1) // duplicate

        assertEquals(listOf(samplePem1, samplePem2), namecoinPrefs.loadPinnedCerts())
    }

    @Test
    fun `addPinnedCert ignores blank input`() {
        namecoinPrefs.addPinnedCert("")
        namecoinPrefs.addPinnedCert("   ")
        assertTrue(namecoinPrefs.loadPinnedCerts().isEmpty())
    }

    @Test
    fun `reset clears pinned certs`() =
        runBlocking {
            namecoinPrefs.addPinnedCert(samplePem1)
            namecoinPrefs.addPinnedCert(samplePem2)
            assertEquals(2, namecoinPrefs.loadPinnedCerts().size)

            namecoinPrefs.reset()
            assertTrue(namecoinPrefs.loadPinnedCerts().isEmpty())

            // Verify persistence — cleared certs stay cleared after reload.
            val reloaded = DesktopNamecoinPreferences(prefs = testPrefs)
            assertTrue(reloaded.loadPinnedCerts().isEmpty())
        }

    @Test
    fun `pinned certs are independent of settings copy`() =
        runBlocking {
            // Add some pinned certs.
            namecoinPrefs.addPinnedCert(samplePem1)
            // Mutating other settings must not clobber the pinned-cert list.
            namecoinPrefs.addServer("example.com:50006")
            namecoinPrefs.setEnabled(false)

            assertEquals(listOf(samplePem1), namecoinPrefs.loadPinnedCerts())
        }

    // ── Backend / Core RPC / fallback round-trip ──────────────────────

    @Test
    fun `default backend is ElectrumX with empty Core RPC config and no fallbacks`() {
        val settings = namecoinPrefs.current
        assertEquals(NamecoinBackend.ELECTRUMX, settings.backend)
        assertEquals(NamecoinCoreRpcConfig(), settings.namecoinCoreRpc)
        assertFalse(settings.fallbackToCustomElectrumx)
        assertFalse(settings.fallbackToDefaultElectrumx)
    }

    @Test
    fun `setBackend persists and updates flow`() =
        runBlocking {
            namecoinPrefs.setBackend(NamecoinBackend.NAMECOIN_CORE_RPC)
            assertEquals(NamecoinBackend.NAMECOIN_CORE_RPC, namecoinPrefs.current.backend)

            val reloaded = DesktopNamecoinPreferences(prefs = testPrefs)
            assertEquals(NamecoinBackend.NAMECOIN_CORE_RPC, reloaded.current.backend)
        }

    @Test
    fun `setCoreRpcConfig persists URL username password`() =
        runBlocking {
            val cfg =
                NamecoinCoreRpcConfig(
                    url = "http://abc.onion:8336/",
                    username = "rpcuser",
                    password = "hunter2",
                )
            namecoinPrefs.setCoreRpcConfig(cfg)
            assertEquals(cfg, namecoinPrefs.current.namecoinCoreRpc)

            val reloaded = DesktopNamecoinPreferences(prefs = testPrefs)
            assertEquals(cfg, reloaded.current.namecoinCoreRpc)
        }

    @Test
    fun `setCoreRpcConfig round-trips usePinnedTrustStore flag`() =
        runBlocking {
            val cfg =
                NamecoinCoreRpcConfig(
                    url = "https://lan-host:8336/",
                    username = "u",
                    password = "p",
                    usePinnedTrustStore = true,
                )
            namecoinPrefs.setCoreRpcConfig(cfg)
            val reloaded = DesktopNamecoinPreferences(prefs = testPrefs)
            assertTrue(reloaded.current.namecoinCoreRpc.usePinnedTrustStore)
        }

    @Test
    fun `setFallbackToCustomElectrumx persists`() =
        runBlocking {
            namecoinPrefs.setFallbackToCustomElectrumx(true)
            assertTrue(namecoinPrefs.current.fallbackToCustomElectrumx)

            val reloaded = DesktopNamecoinPreferences(prefs = testPrefs)
            assertTrue(reloaded.current.fallbackToCustomElectrumx)
        }

    @Test
    fun `setFallbackToDefaultElectrumx persists`() =
        runBlocking {
            namecoinPrefs.setFallbackToDefaultElectrumx(true)
            assertTrue(namecoinPrefs.current.fallbackToDefaultElectrumx)

            val reloaded = DesktopNamecoinPreferences(prefs = testPrefs)
            assertTrue(reloaded.current.fallbackToDefaultElectrumx)
        }

    @Test
    fun `reset clears backend Core RPC and fallback flags`() =
        runBlocking {
            namecoinPrefs.setBackend(NamecoinBackend.NAMECOIN_CORE_RPC)
            namecoinPrefs.setCoreRpcConfig(
                NamecoinCoreRpcConfig(url = "http://x:8336/", username = "u", password = "p"),
            )
            namecoinPrefs.setFallbackToCustomElectrumx(true)
            namecoinPrefs.setFallbackToDefaultElectrumx(true)

            namecoinPrefs.reset()

            val s = namecoinPrefs.current
            assertEquals(NamecoinBackend.ELECTRUMX, s.backend)
            assertEquals(NamecoinCoreRpcConfig(), s.namecoinCoreRpc)
            assertFalse(s.fallbackToCustomElectrumx)
            assertFalse(s.fallbackToDefaultElectrumx)

            val reloaded = DesktopNamecoinPreferences(prefs = testPrefs)
            assertEquals(NamecoinSettings.DEFAULT, reloaded.current)
        }

    @Test
    fun `full round-trip across all backend fields`() =
        runBlocking {
            namecoinPrefs.setEnabled(true)
            namecoinPrefs.addServer("server1.com:50006")
            namecoinPrefs.setBackend(NamecoinBackend.NAMECOIN_CORE_RPC)
            val cfg =
                NamecoinCoreRpcConfig(
                    url = "http://onion.onion:8336/",
                    username = "alice",
                    password = "s3cret",
                    usePinnedTrustStore = false,
                )
            namecoinPrefs.setCoreRpcConfig(cfg)
            namecoinPrefs.setFallbackToCustomElectrumx(true)
            namecoinPrefs.setFallbackToDefaultElectrumx(true)

            val snapshot = namecoinPrefs.current
            val reloaded = DesktopNamecoinPreferences(prefs = testPrefs)
            assertEquals(snapshot, reloaded.current)
        }
}
