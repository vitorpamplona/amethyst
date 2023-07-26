package com.vitorpamplona.amethyst.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.HandlerThread
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableStateFlow

class LocationUtil(context: Context) {
    companion object {
        const val MIN_TIME: Long = 1000L
        const val MIN_DISTANCE: Float = 0.0f
    }

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var locationListener: LocationListener? = null

    val locationStateFlow = MutableStateFlow<Location>(Location(LocationManager.NETWORK_PROVIDER))
    val providerState = mutableStateOf(false)
    val isStart: MutableState<Boolean> = mutableStateOf(false)

    private val locHandlerThread = HandlerThread("LocationUtil Thread")

    init {
        locHandlerThread.start()
    }

    @SuppressLint("MissingPermission")
    fun start(minTimeMs: Long = MIN_TIME, minDistanceM: Float = MIN_DISTANCE) {
        locationListener().let {
            locationListener = it
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minTimeMs, minDistanceM, it, locHandlerThread.looper)
        }
        providerState.value = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        isStart.value = true
    }

    fun stop() {
        locationListener?.let {
            locationManager.removeUpdates(it)
        }
        isStart.value = false
    }

    private fun locationListener() = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            locationStateFlow.value = location
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        }

        override fun onProviderEnabled(provider: String) {
            providerState.value = true
        }

        override fun onProviderDisabled(provider: String) {
            providerState.value = false
        }
    }
}

class ReverseGeoLocationUtil {
    fun execute(
        location: Location,
        context: Context
    ): String? {
        return try {
            Geocoder(context).getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()?.let { address ->
                listOfNotNull(address.locality ?: address.subAdminArea, address.countryCode).joinToString(", ")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
