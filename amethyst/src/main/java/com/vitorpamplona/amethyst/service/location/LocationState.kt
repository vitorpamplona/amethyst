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
package com.vitorpamplona.amethyst.service.location

import android.content.Context
import com.fonfon.kgeohash.GeoHash
import com.fonfon.kgeohash.toGeoHash
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeohashPrecision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

class LocationState(
    context: Context,
    scope: CoroutineScope,
) {
    companion object {
        const val MIN_TIME: Long = 10000L
        const val MIN_DISTANCE: Float = 100.0f
    }

    sealed class LocationResult {
        data class Success(
            val geoHash: GeoHash,
        ) : LocationResult()

        object LackPermission : LocationResult()

        object Loading : LocationResult()
    }

    private var hasLocationPermission = MutableStateFlow<Boolean>(false)
    private var latestLocation: LocationResult = LocationResult.Loading

    fun setLocationPermission(newValue: Boolean) {
        if (newValue != hasLocationPermission.value) {
            hasLocationPermission.tryEmit(newValue)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val geohashStateFlow =
        hasLocationPermission
            .transformLatest {
                if (it) {
                    emit(LocationResult.Loading)
                    val result =
                        LocationFlow(context)
                            .get(MIN_TIME, MIN_DISTANCE)
                            .map {
                                LocationResult.Success(it.toGeoHash(GeohashPrecision.KM_5_X_5.digits)) as LocationResult
                            }.onEach {
                                latestLocation = it
                            }.catch { e ->
                                e.printStackTrace()
                                latestLocation = LocationResult.LackPermission
                                emit(LocationResult.LackPermission)
                            }

                    emitAll(result)
                } else {
                    emit(LocationResult.LackPermission)
                }
            }.stateIn(
                scope,
                SharingStarted.WhileSubscribed(5000),
                latestLocation,
            )
}
