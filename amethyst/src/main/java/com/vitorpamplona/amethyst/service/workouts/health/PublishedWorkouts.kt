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

import com.vitorpamplona.amethyst.commons.fitness.DetectedWorkout
import com.vitorpamplona.amethyst.commons.fitness.toDetectedWorkout
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.quartz.experimental.fitness.workout.WorkoutRecordEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The workouts [pubkeyHex] has published, from [sinceEpochSeconds] onwards, as the cache currently
 * holds them.
 *
 * Shared by the My Fitness dashboard, which counts them, and the New Workout carousel, which uses
 * them to avoid offering a workout the user already shared. Both need the same view of "what have
 * I already posted", and two answers to that question would mean the carousel offering something
 * the dashboard knows is a duplicate.
 *
 * Scans off the main thread: LocalCache holds every event the session has seen and this walks all
 * of them. It reports only what is already cached — it issues no REQ of its own.
 */
suspend fun publishedWorkoutsOf(
    pubkeyHex: String,
    sinceEpochSeconds: Long,
): List<DetectedWorkout> =
    withContext(Dispatchers.Default) {
        LocalCache.notes
            .filterIntoSet { _, note ->
                val event = note.event
                event is WorkoutRecordEvent && event.pubKey == pubkeyHex
            }.mapNotNull { (it.event as WorkoutRecordEvent).toDetectedWorkout() }
            .filter { it.startTimeEpochSeconds >= sinceEpochSeconds }
    }
