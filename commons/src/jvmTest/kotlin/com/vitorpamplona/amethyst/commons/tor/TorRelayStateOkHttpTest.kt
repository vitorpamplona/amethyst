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
package com.vitorpamplona.amethyst.commons.tor

import com.vitorpamplona.amethyst.commons.service.http.DualHttpClientManager
import com.vitorpamplona.amethyst.commons.service.http.EncryptionKeyCache
import com.vitorpamplona.amethyst.commons.service.http.OnionLocationCache
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
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
import kotlin.test.assertNull

/**
 * NIP-11 and NIP-86 requests go to a relay's HTTP endpoint, and must take the same route as that
 * relay's socket. [okHttpClientForRelay] was a member of [TorRelayState] before the class moved to
 * commonMain; it is now an extension over the same decision.
 */
class TorRelayStateOkHttpTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun tearDown() = scope.cancel()

    private val proxyPort = ServerSocket(0).use { it.localPort }

    private val clients =
        DualHttpClientManager(
            userAgent = "test",
            proxyPortProvider = MutableStateFlow(proxyPort),
            isMobileDataProvider = MutableStateFlow(false),
            keyCache = EncryptionKeyCache(),
            scope = scope,
            dns = SurgeDns(),
            onionCache = OnionLocationCache(),
        )

    private val settings =
        TorSettingsFlow(
            torType = MutableStateFlow(TorType.INTERNAL),
            newRelaysViaTor = MutableStateFlow(true),
            trustedRelaysViaTor = MutableStateFlow(false),
        )

    private val state = TorRelayState(settings, scope)

    private val relay = NormalizedRelayUrl("wss://nip11.example.com/")

    private fun proxyPortOf(url: NormalizedRelayUrl) = (state.okHttpClientForRelay(clients, url).proxy?.address() as? InetSocketAddress)?.port

    @Test
    fun aTorRoutedRelayGetsTheProxiedClient() {
        assertEquals(proxyPort, proxyPortOf(relay))
    }

    @Test
    fun aDirectRelayGetsTheDirectClient() {
        state.trustedRelays.value = setOf(relay)
        assertNull(state.okHttpClientForRelay(clients, relay).proxy)

        state.trustedRelays.value = emptySet()
        settings.torType.value = TorType.OFF
        assertNull(state.okHttpClientForRelay(clients, relay).proxy)
    }
}
