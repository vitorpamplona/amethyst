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
package com.vitorpamplona.amethyst.desktop.network

import com.vitorpamplona.amethyst.commons.tor.TorServiceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopRelayConnectionManagerTest {
    private fun createManager(): DesktopRelayConnectionManager {
        val fakeTorManager =
            object : com.vitorpamplona.amethyst.commons.tor.ITorManager {
                override val status = MutableStateFlow<TorServiceStatus>(TorServiceStatus.Off)
                override val activePortOrNull = MutableStateFlow<Int?>(null)

                override suspend fun dormant() {}

                override suspend fun active() {}

                override suspend fun newIdentity() {}
            }
        val scope = CoroutineScope(SupervisorJob())
        val httpClient = DesktopHttpClient(fakeTorManager, { false }, { com.vitorpamplona.amethyst.commons.tor.TorType.OFF }, scope)
        return DesktopRelayConnectionManager(httpClient)
    }

    @Test
    fun testRelayConnectionManagerCanBeInstantiated() {
        val manager = createManager()
        assertNotNull(manager)
    }

    @Test
    fun testRelayConnectionManagerHasNoActiveConnectionsInitially() {
        val manager = createManager()
        val connectedRelays = manager.connectedRelays.value
        val availableRelays = manager.availableRelays.value
        assertTrue(connectedRelays.isEmpty(), "Should have no connected relays on initialization")
        assertTrue(availableRelays.isEmpty(), "Should have no available relays on initialization")
    }
}
