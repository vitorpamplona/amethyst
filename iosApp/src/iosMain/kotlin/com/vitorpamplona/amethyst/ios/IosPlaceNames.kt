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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vitorpamplona.amethyst.commons.model.location.PlaceHit
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHash
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLGeocoder
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLPlacemark
import kotlin.coroutines.resume

/**
 * Place names for geohashes ("Lisbon, PT"), as Android's CachedReversedGeoLocations: Core Location's
 * reverse geocoder, the last few answers kept. Read and written on the main thread only.
 */
internal object IosPlaceNames {
    private const val MAX_CACHED = 20
    private const val MAX_RETRIES = 5

    private val names = LinkedHashMap<String, String>()

    fun cached(geohash: String): String? = names[geohash]

    /** The name of [geohash]'s centre, retried with backoff as Android does; the geohash itself when none comes. */
    suspend fun resolve(geohash: String): String {
        names[geohash]?.let { return it }
        val center = runCatching { GeoHash.decode(geohash) }.getOrNull() ?: return geohash
        var delayMs = 1_000L
        repeat(MAX_RETRIES) {
            val name = reverseGeocode(CLLocation(latitude = center.centerLat, longitude = center.centerLon))
            if (name != null) {
                names[geohash] = name
                if (names.size > MAX_CACHED) names.remove(names.keys.first())
                return name
            }
            delay(delayMs)
            delayMs *= 2
        }
        return geohash
    }

    private suspend fun reverseGeocode(location: CLLocation): String? =
        suspendCancellableCoroutine { cont ->
            val geocoder = CLGeocoder()
            cont.invokeOnCancellation { geocoder.cancelGeocode() }
            geocoder.reverseGeocodeLocation(location) { placemarks, _ ->
                val name = placemarks?.firstNotNullOfOrNull { (it as? CLPlacemark)?.cityCountry()?.ifBlank { null } }
                if (cont.isActive) cont.resume(name)
            }
        }

    /** Places named [query], from Core Location's forward geocoder, as Android's ForwardGeolocation. */
    @OptIn(ExperimentalForeignApi::class)
    suspend fun search(query: String): List<PlaceHit> =
        suspendCancellableCoroutine { cont ->
            val geocoder = CLGeocoder()
            cont.invokeOnCancellation { geocoder.cancelGeocode() }
            geocoder.geocodeAddressString(query) { placemarks, _ ->
                val hits =
                    placemarks.orEmpty().mapNotNull { placemark ->
                        val place = placemark as? CLPlacemark ?: return@mapNotNull null
                        val point = place.location ?: return@mapNotNull null
                        point.coordinate.useContents { PlaceHit(place.displayLine(), latitude, longitude) }
                    }
                if (cont.isActive) cont.resume(hits.take(MAX_SEARCH_RESULTS))
            }
        }

    private const val MAX_SEARCH_RESULTS = 5

    /** Its most specific name, then the enclosing locality, region and country, as Android labels a hit. */
    private fun CLPlacemark.displayLine(): String {
        val primary = name ?: locality ?: subAdministrativeArea ?: administrativeArea ?: country ?: ""
        val context = listOfNotNull(locality, administrativeArea, country).distinct().filter { it != primary }
        return listOfNotNull(primary.ifBlank { null }, context.joinToString(", ").ifBlank { null }).joinToString(", ")
    }

    /** "City, CC", as Android's Address.toCityCountry. */
    private fun CLPlacemark.cityCountry(): String =
        buildString {
            val city = locality ?: subAdministrativeArea
            if (!city.isNullOrBlank()) append(city)
            val country = ISOcountryCode
            if (!country.isNullOrBlank()) {
                if (isNotEmpty()) append(", ")
                append(country)
            }
        }
}

/** The note's place name: shown at once when known, else [onLoading] while the geocoder answers. */
@Composable
internal fun IosLoadCityName(
    geohashStr: String,
    onLoading: (@Composable () -> Unit)?,
    content: @Composable (String) -> Unit,
) {
    var name by remember(geohashStr) { mutableStateOf(IosPlaceNames.cached(geohashStr)) }
    val resolved = name
    if (resolved != null) {
        content(resolved)
    } else {
        onLoading?.invoke()
        LaunchedEffect(geohashStr) { name = IosPlaceNames.resolve(geohashStr) }
    }
}
