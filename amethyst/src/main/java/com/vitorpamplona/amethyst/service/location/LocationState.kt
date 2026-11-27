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
package com.vitorpamplona.amethyst.service.location

import android.content.Context
import android.location.Location
import android.location.LocationManager
import com.vitorpamplona.amethyst.commons.model.location.GatedDeviceLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

// `toGeoHash` is an extension on Location declared in LocationGeoHash.kt, same
// package, so it needs no import.

/**
 * Android's [GatedDeviceLocation]: fixes from LocationManager, listening only while the app is in
 * the foreground (the gate, cache and precision ladder are the shared class's).
 */
class LocationState(
    context: Context,
    scope: CoroutineScope,
    isForeground: StateFlow<Boolean>,
    /**
     * Resource-ledger hook: true while location updates are actively
     * registered. Reaches the OS only through the default [locationSource],
     * which hands it to [LocationFlow] — a caller that overrides
     * [locationSource] (the tests do) is responsible for firing it, or not.
     */
    onListening: ((Boolean) -> Unit)? = null,
    locationSource: (Long, Float) -> Flow<Location> = { minTimeMs, minDistanceM ->
        LocationFlow(context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
            .get(minTimeMs, minDistanceM, onListening)
    },
) : GatedDeviceLocation<Location>(scope, isForeground, locationSource, { location, chars -> location.toGeoHash(chars) }) {
    companion object {
        const val COARSE_MIN_TIME: Long = GatedDeviceLocation.COARSE_MIN_TIME
        const val COARSE_MIN_DISTANCE: Float = GatedDeviceLocation.COARSE_MIN_DISTANCE
        const val PRECISE_MIN_TIME: Long = GatedDeviceLocation.PRECISE_MIN_TIME
        const val PRECISE_MIN_DISTANCE: Float = GatedDeviceLocation.PRECISE_MIN_DISTANCE
        const val BACKGROUND_GRACE_MS: Long = GatedDeviceLocation.BACKGROUND_GRACE_MS
        const val SUBSCRIPTION_STOP_TIMEOUT_MS: Long = GatedDeviceLocation.SUBSCRIPTION_STOP_TIMEOUT_MS
    }
}
