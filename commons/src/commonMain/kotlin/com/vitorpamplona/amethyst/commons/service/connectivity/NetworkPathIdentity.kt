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

/**
 * One reading of the system's default network path, reduced to what tells networks apart: whether
 * it can carry traffic, the interface it prefers (name and kind, e.g. `en0/wifi`), its gateways,
 * and whether the system calls it metered.
 */
data class NetworkPathReading(
    val satisfied: Boolean,
    val primaryInterface: String?,
    val gateways: List<String>,
    val metered: Boolean,
)

/**
 * Turns path readings into the [ConnectivityStatus] Android's `ConnectivityFlow` reports, for a
 * platform whose path monitor names no network (iOS's `NWPathMonitor`).
 *
 * Android gives every network connection a handle of its own: a new one for each wifi network, for
 * cellular, for a VPN, and again for the same wifi after it dropped. The id here follows that: it is
 * derived from the preferred interface and the gateways (a different wifi has a different gateway),
 * plus a count of the times the path came back after being unusable, so reconnecting to the same
 * network is a new id as it is on Android. A reading that differs only in being metered keeps the
 * id, as Android's `onCapabilitiesChanged` keeps the handle.
 *
 * Not thread-safe: feed it from one thread (the monitor's serial queue).
 */
class NetworkPathIdentity {
    private var reconnects = 0L
    private var wasSatisfied: Boolean? = null

    fun statusOf(reading: NetworkPathReading): ConnectivityStatus {
        val previous = wasSatisfied
        wasSatisfied = reading.satisfied
        if (!reading.satisfied) return ConnectivityStatus.Off
        if (previous == false) reconnects++
        return ConnectivityStatus.Active(networkIdOf(reading), reading.metered)
    }

    private fun networkIdOf(reading: NetworkPathReading): Long {
        val network = (reading.primaryInterface ?: "") + "|" + reading.gateways.sorted().joinToString(",")
        return (reconnects shl 32) or (network.hashCode().toLong() and 0xFFFFFFFFL)
    }
}
