/**
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

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import com.vitorpamplona.amethyst.service.location.LocationState.Companion.MIN_DISTANCE
import com.vitorpamplona.amethyst.service.location.LocationState.Companion.MIN_TIME
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class LocationFlow(
    private val context: Context,
) {
    @SuppressLint("MissingPermission")
    fun get(
        minTimeMs: Long = MIN_TIME,
        minDistanceM: Float = MIN_DISTANCE,
    ): Flow<Location> =
        callbackFlow {
            Log.i("LocationFlow", "Start")
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            val locationCallback =
                object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        Log.d("LocationFlow", "onLocationChanged $location")
                        launch { send(location) }
                    }
                }

            locationManager.allProviders.forEach {
                val location = locationManager.getLastKnownLocation(it)
                Log.d("LocationFlow", "Last Known location is $location")
                if (location != null) {
                    send(location)
                }
                Log.d("LocationFlow", "Requesting Updates")
                locationManager.requestLocationUpdates(
                    it,
                    minTimeMs,
                    minDistanceM,
                    locationCallback,
                    Looper.getMainLooper(),
                )
            }

            awaitClose {
                Log.i("LocationFlow", "Stop")
                locationManager.removeUpdates(locationCallback)
            }
        }
}
