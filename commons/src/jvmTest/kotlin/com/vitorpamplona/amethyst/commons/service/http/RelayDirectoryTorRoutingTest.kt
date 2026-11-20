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
package com.vitorpamplona.amethyst.commons.service.http

import com.vitorpamplona.amethyst.commons.service.georelay.GeoRelayDirectory
import com.vitorpamplona.amethyst.commons.tor.TorSettingsFlow
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.SurgeDns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The georelays CSV that routes geohash chats is a GitHub download. It used to go out on the
 * direct client whatever the Tor settings, so a user routing everything through Tor still made a
 * clearnet request to GitHub. It now follows the "new relays via Tor" switch.
 */
class RelayDirectoryTorRoutingTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun tearDown() = scope.cancel()

    private val proxyPort = ServerSocket(0).use { it.localPort }

    private fun builder(settings: TorSettingsFlow) =
        RoleBasedHttpClientBuilder(
            DualHttpClientManager(
                userAgent = "test",
                proxyPortProvider = MutableStateFlow(proxyPort),
                isMobileDataProvider = MutableStateFlow(false),
                keyCache = EncryptionKeyCache(),
                scope = scope,
                dns = SurgeDns(),
                onionCache = OnionLocationCache(),
            ),
            settings,
        )

    private val csv = GeoRelayDirectory.CSV_URL

    @Test
    fun directoryGoesThroughTorWhenNewRelaysDo() {
        for (type in listOf(TorType.INTERNAL, TorType.EXTERNAL)) {
            val builder = builder(TorSettingsFlow(torType = MutableStateFlow(type), newRelaysViaTor = MutableStateFlow(true)))
            assertTrue(builder.shouldUseTorForRelayDirectory(csv))
            assertEquals(proxyPort, (builder.okHttpClientForRelayDirectory(csv).proxy?.address() as? InetSocketAddress)?.port)
        }
    }

    @Test
    fun directoryIsDirectWhenTorIsOffOrNewRelaysAreDirect() {
        val off = builder(TorSettingsFlow(torType = MutableStateFlow(TorType.OFF), newRelaysViaTor = MutableStateFlow(true)))
        assertFalse(off.shouldUseTorForRelayDirectory(csv))
        assertNull(off.okHttpClientForRelayDirectory(csv).proxy)

        val newDirect = builder(TorSettingsFlow(torType = MutableStateFlow(TorType.INTERNAL), newRelaysViaTor = MutableStateFlow(false)))
        assertFalse(newDirect.shouldUseTorForRelayDirectory(csv))
    }

    @Test
    fun localDirectoryIsNeverProxied() {
        val builder = builder(TorSettingsFlow(torType = MutableStateFlow(TorType.INTERNAL), newRelaysViaTor = MutableStateFlow(true)))
        assertFalse(builder.shouldUseTorForRelayDirectory("http://127.0.0.1:8080/nostr_relays.csv"))
    }
}
