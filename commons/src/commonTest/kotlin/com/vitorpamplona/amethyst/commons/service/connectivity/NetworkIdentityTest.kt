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

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

/**
 * The network-change trigger Android and iOS both re-initialize Tor on, and the iOS path readings
 * that feed it: a move to another network is a change, a metered flip or a blip offline and back
 * to a network with the same id is not, the first network is the status quo.
 */
class NetworkIdentityTest {
    private fun active(
        id: Long,
        mobile: Boolean = false,
    ) = ConnectivityStatus.Active(id, mobile)

    @Test
    fun theFirstNetworkIsNotAChange() =
        runTest {
            assertEquals(emptyList(), flowOf(ConnectivityStatus.StartingService, active(1)).networkIdentityChanges().toList())
        }

    @Test
    fun aDifferentNetworkIsAChangeEachTime() =
        runTest {
            val changes = flowOf(active(1), active(2), ConnectivityStatus.Off, active(3), active(1)).networkIdentityChanges().toList()
            assertEquals(listOf(2L, 3L, 1L), changes)
        }

    @Test
    fun meteredFlipsAndOfflineBlipsOnTheSameIdAreNotChanges() =
        runTest {
            val changes = flowOf(active(1), active(1, mobile = true), ConnectivityStatus.Off, active(1), ConnectivityStatus.StartingService).networkIdentityChanges().toList()
            assertEquals(emptyList(), changes)
        }

    private fun reading(
        satisfied: Boolean = true,
        primary: String? = "en0/wifi",
        gateways: List<String> = listOf("192.168.1.1"),
        metered: Boolean = false,
    ) = NetworkPathReading(satisfied, primary, gateways, metered)

    private fun NetworkPathIdentity.id(reading: NetworkPathReading): Long = assertIs<ConnectivityStatus.Active>(statusOf(reading)).networkId

    @Test
    fun anUnsatisfiedPathIsOff() {
        assertEquals(ConnectivityStatus.Off, NetworkPathIdentity().statusOf(reading(satisfied = false)))
    }

    @Test
    fun theSameNetworkKeepsItsIdWhateverItsMeteredFlag() {
        val identity = NetworkPathIdentity()
        val wifi = identity.id(reading())
        assertEquals(wifi, identity.id(reading(metered = true)))
        assertEquals(wifi, identity.id(reading(gateways = listOf("192.168.1.1"))))
        assertEquals(true, (identity.statusOf(reading(metered = true)) as ConnectivityStatus.Active).isMobile)
    }

    @Test
    fun anotherWifiCellularOrAVpnIsAnotherId() {
        val identity = NetworkPathIdentity()
        val home = identity.id(reading())
        val office = identity.id(reading(gateways = listOf("10.0.0.1")))
        val cellular = identity.id(reading(primary = "pdp_ip0/cellular", gateways = emptyList(), metered = true))
        val vpn = identity.id(reading(primary = "utun3/other"))
        assertEquals(4, setOf(home, office, cellular, vpn).size)
    }

    @Test
    fun gatewayOrderDoesNotMatter() {
        val identity = NetworkPathIdentity()
        assertEquals(identity.id(reading(gateways = listOf("a", "b"))), identity.id(reading(gateways = listOf("b", "a"))))
    }

    @Test
    fun theSameNetworkAfterADropIsANewIdAsOnAndroid() {
        val identity = NetworkPathIdentity()
        val before = identity.id(reading())
        assertEquals(ConnectivityStatus.Off, identity.statusOf(reading(satisfied = false)))
        val after = identity.id(reading())
        assertNotEquals(before, after)
        // Readings while connected do not count as reconnections.
        assertEquals(after, identity.id(reading()))
    }
}
