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
package com.vitorpamplona.amethyst.ui.note.creators.location

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.fonfon.kgeohash.toGeoHash
import com.vitorpamplona.amethyst.service.location.CachedReversedGeoLocations
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LoadCityName(
    geohashStr: String,
    onLoading: (@Composable () -> Unit)? = null,
    content: @Composable (String) -> Unit,
) {
    var cityName by remember(geohashStr) { mutableStateOf(CachedReversedGeoLocations.cached(geohashStr)) }

    if (cityName == null) {
        if (onLoading != null) {
            onLoading()
        }

        val context = LocalContext.current

        LaunchedEffect(key1 = geohashStr, context) {
            val location = runCatching { geohashStr.toGeoHash() }.getOrNull()?.toLocation()
            if (location != null) {
                launch {
                    var notReady = true
                    var myStep = 1000L
                    while (notReady) {
                        // Retries while the Reverse Geolocation service is offline.
                        CachedReversedGeoLocations.geoLocate(geohashStr, location, context) { newCityName ->
                            if (newCityName != cityName) {
                                notReady = false
                                cityName = newCityName
                            }
                        }
                        myStep = myStep * 2L
                        delay(myStep)
                    }
                }
            }
        }
    } else {
        cityName?.let { content(it) }
    }
}
