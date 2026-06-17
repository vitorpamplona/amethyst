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
package com.vitorpamplona.amethyst.service.workouts.health

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseType

/**
 * A finished workout read from Health Connect and mapped to the fields Amethyst
 * can publish as a NIP-101e kind 1301 event. Platform-neutral and free of any
 * Health Connect types so it can feed the navigation route and the suggestion
 * UI directly.
 *
 * [id] is the Health Connect record id, used to remember which sessions the
 * user has already handled (accepted or dismissed) so each is offered once.
 */
@Immutable
data class DetectedWorkout(
    val id: String,
    val exercise: ExerciseType,
    val title: String?,
    val startTimeEpochSeconds: Long,
    val durationSeconds: Long,
    val distanceMeters: Double?,
    val calories: Int?,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val steps: Int?,
    val elevationGainMeters: Double?,
    /** Human-readable name of the app/device that wrote the record (e.g. "Samsung Health"). */
    val source: String,
)
