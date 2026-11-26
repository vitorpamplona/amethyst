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
package com.vitorpamplona.amethyst.commons.service.connectivity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [networkPathStatus] over the real `NWPathMonitor` of the simulator (the Mac's own network; no
 * traffic is sent), and the readings [IosConnectivity] derives from a status, the ones Android's
 * `ConnectivityManager` gives the app.
 */
class IosConnectivityTest {
    @Test
    fun theMonitorReportsTheCurrentPath() =
        runBlocking {
            val first = withTimeout(10_000) { networkPathStatus().first { it != ConnectivityStatus.StartingService } }
            println("B12f connectivity: NWPathMonitor reports $first")
            assertTrue(first is ConnectivityStatus.Active || first == ConnectivityStatus.Off)
        }

    @Test
    fun meteredReadingsFollowTheStatusAsOnAndroid() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val source = MutableStateFlow<ConnectivityStatus>(ConnectivityStatus.StartingService)
                val connectivity = IosConnectivity(scope, source)
                // Kept collected, as the app's network-change trigger keeps them.
                connectivity.status.launchIn(scope)
                connectivity.isMobileOrNull.launchIn(scope)
                connectivity.isMobileOrFalse.launchIn(scope)

                assertTrue(connectivity.isMeteredOrMobileNow(), "no reading yet counts as metered")

                source.value = ConnectivityStatus.Active(1, isMobile = false)
                withTimeout(5_000) { connectivity.isMobileOrNull.first { it == false } }
                assertEquals(false, connectivity.isMobileOrFalse.value)
                assertEquals(false, connectivity.isMeteredOrMobileNow())

                source.value = ConnectivityStatus.Active(2, isMobile = true)
                withTimeout(5_000) { connectivity.isMobileOrNull.first { it == true } }
                assertEquals(true, connectivity.isMeteredOrMobileNow())

                source.value = ConnectivityStatus.Off
                withTimeout(5_000) { connectivity.isMobileOrNull.first { it == null } }
                assertEquals(false, connectivity.isMobileOrFalse.value)
                assertTrue(connectivity.isMeteredOrMobileNow(), "no network counts as metered")
            } finally {
                scope.cancel()
            }
        }
}
