/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.HandlerThread
import android.util.LruCache
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

class LocationUtil(
    context: Context,
) {
    companion object {
        const val MIN_TIME: Long = 1000L
        const val MIN_DISTANCE: Float = 0.0f
    }

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var locationListener: LocationListener? = null

    val locationStateFlow = MutableStateFlow<Location>(Location(LocationManager.NETWORK_PROVIDER))
    val providerState = mutableStateOf(false)
    val isStart: MutableState<Boolean> = mutableStateOf(false)

    private val locHandlerThread = HandlerThread("LocationUtil Thread")

    init {
        locHandlerThread.start()
    }

    @SuppressLint("MissingPermission")
    fun start(
        minTimeMs: Long = MIN_TIME,
        minDistanceM: Float = MIN_DISTANCE,
    ) {
        locationListener().let {
            locationListener = it
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                minTimeMs,
                minDistanceM,
                it,
                locHandlerThread.looper,
            )
        }
        providerState.value = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        isStart.value = true
    }

    fun stop() {
        locationListener?.let { locationManager.removeUpdates(it) }
        isStart.value = false
    }

    private fun locationListener() =
        object : LocationListener {
            override fun onLocationChanged(location: Location) {
                locationStateFlow.value = location
            }

            override fun onProviderEnabled(provider: String) {
                providerState.value = true
            }

            override fun onProviderDisabled(provider: String) {
                providerState.value = false
            }
        }
}

object CachedGeoLocations {
    val locationNames = LruCache<String, String>(20)

    fun cached(geoHashStr: String): String? = locationNames[geoHashStr]

    suspend fun geoLocate(
        geoHashStr: String,
        location: Location,
        context: Context,
    ): String? {
        locationNames[geoHashStr]?.let {
            return it
        }

        val name = ReverseGeoLocationUtil().execute(location, context)?.ifBlank { null }

        if (name != null) {
            locationNames.put(geoHashStr, name)
        }

        return name
    }
}

private class ReverseGeoLocationUtil {
    suspend fun execute(
        location: Location,
        context: Context,
    ): String? {
        return try {
            Geocoder(context)
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
                ?.let { address ->
                    listOfNotNull(address.locality ?: address.subAdminArea, address.countryCode)
                        .joinToString(", ")
                }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            return null
        }
    }
}
