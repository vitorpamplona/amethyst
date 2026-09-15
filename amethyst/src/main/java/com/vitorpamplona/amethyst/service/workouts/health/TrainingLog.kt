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

import com.vitorpamplona.quartz.experimental.fitness.workout.WorkoutRecordEvent
import kotlin.math.abs

/**
 * The user's training log: everything they have done, from both places Amethyst can learn about
 * it.
 *
 * Health Connect knows what their watch recorded. It does not know about a workout they typed
 * into Amethyst by hand, or one they posted from another NIP-101e client, and it cannot see
 * further back than 30 days. Their own kind 1301 events know all of those and none of the
 * device detail. Neither source alone is the truth, so the dashboard reads both.
 *
 * The practical consequence is that My Fitness works with no health permissions at all — a user
 * who never connects Health Connect still gets a summary of what they have logged.
 */
object TrainingLog {
    /**
     * How far apart two records of the same activity can start and still be judged the same
     * workout. Generous on purpose: a published event often carries only the minute the user
     * typed, or the time the post went out, while Health Connect has the exact second.
     */
    const val DEDUPE_TOLERANCE_SECONDS = 900L

    /**
     * Combines both sources into one log, newest first.
     *
     * Where the same workout appears in both — the usual case once a user shares one that came
     * from their watch — the Health Connect copy wins: it carries the metrics the published
     * event may have dropped (heart rate, steps, climb), and its start time is the recorded one
     * rather than a publish timestamp.
     */
    fun merge(
        healthConnect: List<DetectedWorkout>,
        published: List<DetectedWorkout>,
    ): List<DetectedWorkout> {
        val deduped =
            published.filterNot { candidate ->
                healthConnect.any { it.isProbablySameWorkoutAs(candidate) }
            }

        return (healthConnect + deduped).sortedByDescending { it.startTimeEpochSeconds }
    }

    private fun DetectedWorkout.isProbablySameWorkoutAs(other: DetectedWorkout): Boolean =
        exercise == other.exercise &&
            abs(startTimeEpochSeconds - other.startTimeEpochSeconds) <= DEDUPE_TOLERANCE_SECONDS
}

/**
 * Reads a published workout back into the training log, or null when it carries nothing a
 * summary can use — no recognisable activity, or no duration.
 *
 * Start time prefers the explicit `start` tag and falls back to the event's own `created_at`.
 * That fallback is the publish time, not the workout time: a manual entry has no better signal,
 * and for day-level bucketing (streaks, active days, the week split) it is close enough, since
 * people post a workout the same day they do it.
 */
fun WorkoutRecordEvent.toDetectedWorkout(): DetectedWorkout? {
    val activity = activityType() ?: return null

    val duration = durationSeconds() ?: return null
    if (duration <= 0) return null

    return DetectedWorkout(
        id = id,
        exercise = activity,
        title = title()?.takeIf { it.isNotBlank() },
        startTimeEpochSeconds = workoutStartTime() ?: workoutStart() ?: createdAt,
        durationSeconds = duration,
        distanceMeters = distance()?.toMeters()?.takeIf { it > 0 },
        calories = calories()?.takeIf { it > 0 },
        avgHeartRate = avgHeartRate()?.takeIf { it > 0 },
        maxHeartRate = maxHeartRate()?.takeIf { it > 0 },
        steps = steps()?.takeIf { it > 0 },
        elevationGainMeters = elevationGain()?.toMeters()?.takeIf { it > 0 },
        source = workoutSource() ?: "",
        origin = WorkoutOrigin.PUBLISHED,
    )
}
