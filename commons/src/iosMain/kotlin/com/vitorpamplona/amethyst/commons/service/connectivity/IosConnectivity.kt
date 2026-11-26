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

import com.vitorpamplona.quartz.utils.Log
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import platform.Network.nw_endpoint_copy_address_string
import platform.Network.nw_interface_get_name
import platform.Network.nw_interface_get_type
import platform.Network.nw_interface_type_cellular
import platform.Network.nw_interface_type_loopback
import platform.Network.nw_interface_type_other
import platform.Network.nw_interface_type_wifi
import platform.Network.nw_interface_type_wired
import platform.Network.nw_path_enumerate_gateways
import platform.Network.nw_path_enumerate_interfaces
import platform.Network.nw_path_get_status
import platform.Network.nw_path_is_constrained
import platform.Network.nw_path_is_expensive
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.NSObject
import platform.darwin.dispatch_queue_create
import platform.posix.free

/**
 * The default network as `NWPathMonitor` sees it, as a [ConnectivityStatus] flow: iOS's
 * counterpart of Android's `ConnectivityFlow` (a `ConnectivityManager` default-network callback).
 * Starts with `StartingService`, then one status per path update, `Off` when no path can carry
 * traffic. Ids come from [NetworkPathIdentity], so a move to another network is a new id as it is on
 * Android, and "metered" means what Android's `isMeteredOrMobileData` does: the system calls the
 * path expensive (cellular, a phone's hotspot) or constrained (Low Data Mode), or it runs over
 * cellular. Distinct and debounced by 200 ms, as Android's.
 */
@OptIn(ExperimentalForeignApi::class, FlowPreview::class)
fun networkPathStatus(): Flow<ConnectivityStatus> =
    callbackFlow {
        trySend(ConnectivityStatus.StartingService)

        val identity = NetworkPathIdentity()
        val monitor = nw_path_monitor_create()
        nw_path_monitor_set_queue(monitor, dispatch_queue_create("com.vitorpamplona.amethyst.connectivity", null))
        nw_path_monitor_set_update_handler(monitor) { path ->
            val reading = readingOf(path)
            val status = identity.statusOf(reading)
            Log.d("IosConnectivity") { "Path update: $reading -> $status" }
            trySend(status)
        }
        Log.d("IosConnectivity", "Starting the network path monitor")
        nw_path_monitor_start(monitor)

        awaitClose {
            Log.d("IosConnectivity", "Stopping the network path monitor")
            nw_path_monitor_cancel(monitor)
            trySend(ConnectivityStatus.Off)
        }
    }.distinctUntilChanged().debounce(200)

@OptIn(ExperimentalForeignApi::class)
private fun readingOf(path: NSObject?): NetworkPathReading {
    var primary: String? = null
    var cellular = false
    nw_path_enumerate_interfaces(path) { iface ->
        val type = nw_interface_get_type(iface)
        if (primary == null) primary = "${nw_interface_get_name(iface)?.toKString()}/${typeName(type)}"
        if (type == nw_interface_type_cellular) cellular = true
        true
    }
    val gateways = ArrayList<String>()
    nw_path_enumerate_gateways(path) { endpoint ->
        nw_endpoint_copy_address_string(endpoint)?.let {
            gateways += it.toKString()
            free(it)
        }
        true
    }
    return NetworkPathReading(
        satisfied = nw_path_get_status(path) == nw_path_status_satisfied,
        primaryInterface = primary,
        gateways = gateways,
        metered = nw_path_is_expensive(path) || nw_path_is_constrained(path) || cellular,
    )
}

private fun typeName(type: UInt): String =
    when (type) {
        nw_interface_type_wifi -> "wifi"
        nw_interface_type_cellular -> "cellular"
        nw_interface_type_wired -> "wired"
        nw_interface_type_loopback -> "loopback"
        nw_interface_type_other -> "other"
        else -> type.toString()
    }

/**
 * iOS's connectivity state, the same three readings Android's `ConnectivityManager` gives the app:
 * the [status], and whether the network is metered or mobile, null or false while there is none.
 */
class IosConnectivity(
    scope: CoroutineScope,
    source: Flow<ConnectivityStatus> = networkPathStatus(),
) {
    val status: StateFlow<ConnectivityStatus> =
        source.distinctUntilChanged().stateIn(scope, SharingStarted.WhileSubscribed(30000), ConnectivityStatus.StartingService)

    val isMobileOrNull: StateFlow<Boolean?> =
        status
            .map { (it as? ConnectivityStatus.Active)?.isMobile }
            .stateIn(scope, SharingStarted.WhileSubscribed(2000), (status.value as? ConnectivityStatus.Active)?.isMobile)

    val isMobileOrFalse: StateFlow<Boolean> =
        status
            .map { (it as? ConnectivityStatus.Active)?.isMobile ?: false }
            .stateIn(scope, SharingStarted.WhileSubscribed(2000), (status.value as? ConnectivityStatus.Active)?.isMobile ?: false)

    /**
     * Whether the network is metered or mobile now. With no reading yet, or no network, it counts as
     * metered, as Android's does when the system reports no network (iOS has no synchronous query to
     * ask instead).
     */
    fun isMeteredOrMobileNow(): Boolean = (status.value as? ConnectivityStatus.Active)?.isMobile ?: true
}
