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
package com.vitorpamplona.amethyst.ios

import com.vitorpamplona.amethyst.commons.model.location.GatedDeviceLocation
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHash
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyHundredMeters
import platform.CoreLocation.kCLLocationAccuracyKilometer
import platform.Foundation.NSError
import platform.darwin.NSObject

/**
 * The device's position as geohashes, from Core Location: the shared [GatedDeviceLocation] (which
 * listens only while the app is on screen and permission is granted), fed by a CLLocationManager
 * as Android feeds it LocationManager's fixes.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun iosDeviceLocation(
    scope: CoroutineScope,
    isForeground: StateFlow<Boolean>,
): GatedDeviceLocation<CLLocation> =
    GatedDeviceLocation(
        scope = scope,
        isForeground = isForeground,
        locationSource = ::coreLocationFixes,
        toGeoHash = { location, chars -> location.coordinate.useContents { GeoHash.encode(latitude, longitude, chars) } },
    )

/**
 * Fixes at most every [minDistanceM] metres. A 500 m filter (the coarse profile) is served at
 * kilometre accuracy, which iOS meets from Wi-Fi and cells without the GPS; the precise profile
 * asks for a hundred metres. [minTimeMs] has no Core Location counterpart: the distance filter
 * already keeps updates rare.
 */
@Suppress("UNUSED_PARAMETER")
private fun coreLocationFixes(
    minTimeMs: Long,
    minDistanceM: Float,
): Flow<CLLocation> =
    callbackFlow {
        val manager = CLLocationManager()
        val delegate =
            object : NSObject(), CLLocationManagerDelegateProtocol {
                override fun locationManager(
                    manager: CLLocationManager,
                    didUpdateLocations: List<*>,
                ) {
                    (didUpdateLocations.lastOrNull() as? CLLocation)?.let { trySend(it) }
                }

                override fun locationManager(
                    manager: CLLocationManager,
                    didFailWithError: NSError,
                ) {
                    // Unknown location is a passing state (no fix yet); a denial closes the source.
                    if (didFailWithError.code == DENIED_ERROR_CODE) close(IllegalStateException(didFailWithError.localizedDescription))
                }
            }
        manager.delegate = delegate
        manager.desiredAccuracy = if (minDistanceM >= COARSE_THRESHOLD_M) kCLLocationAccuracyKilometer else kCLLocationAccuracyHundredMeters
        manager.distanceFilter = minDistanceM.toDouble()
        manager.startUpdatingLocation()
        awaitClose {
            manager.stopUpdatingLocation()
            manager.delegate = null
        }
    }.flowOn(Dispatchers.Main) // A CLLocationManager calls its delegate on the run loop it was made on.

/** kCLErrorDenied. */
private const val DENIED_ERROR_CODE = 1L

private const val COARSE_THRESHOLD_M = 500f
