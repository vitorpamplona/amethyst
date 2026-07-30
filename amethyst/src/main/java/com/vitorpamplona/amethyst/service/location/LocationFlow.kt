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

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Wraps [LocationManager] update registration as a cold [Flow].
 *
 * Registers on **one** provider, chosen by [LocationProviderLadder], rather than
 * on every provider the device reports. The previous shotgun cost four
 * simultaneous registrations — passive, network, fused and gps, the last at
 * HIGH_ACCURACY — to produce a 5 km geohash.
 *
 * Takes a [LocationManager] rather than a `Context` so the registration
 * behaviour is unit-testable; the caller does the `getSystemService` lookup.
 *
 * [onListening] is fired from inside the flow, after a registration succeeds, and
 * released again from the `try`/`finally` that wraps everything after it, never
 * as an `onStart`/`onCompletion` pair on the returned flow. The distinction
 * matters: an `onStart` fires on collection even when nothing registered, so a
 * device with no usable provider would accrue location time with no location
 * running, and — because the ledger refcounts the two [LocationState] flows
 * together — the unpaired close would steal the other flow's holder.
 *
 * The pair is kept honest from both ends. The acquire cannot fire without a
 * registration, because a failure to register throws before reaching it. The
 * release cannot be skipped, because everything after the acquire runs inside a
 * `try`/`finally` rather than inside `awaitClose` — [freshestLastKnownLocation]
 * (the seed sweep below) can throw a non-cancellation exception and unwind
 * before `awaitClose` is ever reached, and `try`/`finally` is what still runs
 * the release on that path; see `releasesTheRegistrationWhenTheSeedThrows` in
 * `LocationFlowTest`. (In principle a collector cancelling mid-seed would also
 * unwind past `awaitClose` the same way, but that path could not be
 * reproduced — `callbackFlow`'s `send` is buffered and returns without
 * suspending, so it never observes the cancellation.)
 */
class LocationFlow(
    private val locationManager: LocationManager,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val hasFine: Boolean = false,
) {
    @SuppressLint("MissingPermission")
    fun get(
        minTimeMs: Long,
        minDistanceM: Float,
        onListening: ((Boolean) -> Unit)? = null,
    ): Flow<Location> =
        callbackFlow {
            val locationCallback =
                LocationListener { location ->
                    Log.d("LocationFlow") { "onLocationChanged $location" }
                    launch { send(location) }
                }

            // One binder call, reused for both the ladder filter and the seed.
            val providers = locationManager.allProviders

            val candidates = LocationProviderLadder.chooseProviders(sdkInt, hasFine) { it in providers }

            var registered: String? = null
            for (provider in candidates) {
                try {
                    locationManager.requestLocationUpdates(
                        provider,
                        minTimeMs,
                        minDistanceM,
                        locationCallback,
                        Looper.getMainLooper(),
                    )
                    registered = provider
                    break
                } catch (e: SecurityException) {
                    Log.w("LocationFlow", "Provider $provider refused the update request", e)
                }
            }

            if (registered == null) {
                throw SecurityException("No usable location provider. Candidates: $candidates")
            }

            Log.i("LocationFlow") { "Listening on $registered every ${minTimeMs}ms / ${minDistanceM}m" }
            onListening?.invoke(true)

            // Everything after the acquire runs under try/finally, not under
            // awaitClose. freshestLastKnownLocation() just below can throw a
            // non-cancellation exception and unwind before awaitClose is ever
            // reached — proven by releasesTheRegistrationWhenTheSeedThrows in
            // LocationFlowTest, which fails if the release moves into
            // awaitClose. Cleanup parked inside awaitClose would then never
            // run — the registration would leak and the refcount would stick
            // at >= 1 for the life of the process, so location.ms would accrue
            // forever with nothing listening. (In principle a collector
            // cancelling mid-seed would unwind the same way, but that path
            // could not be reproduced: callbackFlow's send is buffered and
            // returns without suspending, so it never observes the
            // cancellation.) The finally covers both cases regardless.
            try {
                // Seeded after registration so the no-provider path throws
                // without having emitted anything; seeding first would show the
                // consumer Success -> LackPermission on a device with no
                // compatible provider.
                freshestLastKnownLocation(providers)?.let {
                    Log.d("LocationFlow") { "Last known location is $it" }
                    send(it)
                }

                awaitClose { }
            } finally {
                Log.i("LocationFlow") { "Stopped listening on $registered" }
                locationManager.removeUpdates(locationCallback)
                onListening?.invoke(false)
            }
        }

    /**
     * The freshest cached fix across every provider. Permission-checked per
     * provider like the update request is, so each lookup is guarded — on a
     * device where a provider refuses us, the others should still seed.
     */
    @SuppressLint("MissingPermission")
    private fun freshestLastKnownLocation(providers: List<String>): Location? =
        providers
            .mapNotNull { provider ->
                try {
                    locationManager.getLastKnownLocation(provider)
                } catch (e: SecurityException) {
                    Log.w("LocationFlow", "No permission to read the last known location of $provider", e)
                    null
                }
            }.maxByOrNull { it.time }
}
