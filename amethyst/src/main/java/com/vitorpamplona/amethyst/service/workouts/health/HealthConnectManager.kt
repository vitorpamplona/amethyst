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

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Reads finished workouts from Android Health Connect — the single aggregator
 * every Android health source funnels into (Samsung Health/Galaxy Watch,
 * Google Fit, Fitbit, Garmin Connect, Strava, …) — and maps each session to a
 * [DetectedWorkout] ready to become a NIP-101e kind 1301 event.
 *
 * Read-only. The caller decides when to request permission; this class never
 * prompts on its own.
 */
class HealthConnectManager(
    private val context: Context,
) {
    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    companion object {
        private const val TAG = "HealthConnectManager"

        /** Read permissions needed to map a workout. */
        val PERMISSIONS =
            setOf(
                HealthPermission.getReadPermission(ExerciseSessionRecord::class),
                HealthPermission.getReadPermission(DistanceRecord::class),
                HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
                HealthPermission.getReadPermission(HeartRateRecord::class),
                HealthPermission.getReadPermission(StepsRecord::class),
                HealthPermission.getReadPermission(ElevationGainedRecord::class),
            )

        /** True when a Health Connect provider is installed and up to date on this device. */
        fun isAvailable(context: Context): Boolean = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

        /** True when the device has no Health Connect provider at all (vs needing an update). */
        fun needsProviderUpdate(context: Context): Boolean = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED
    }

    suspend fun grantedPermissions(): Set<String> = client.permissionController.getGrantedPermissions()

    suspend fun hasAllPermissions(): Boolean = grantedPermissions().containsAll(PERMISSIONS)

    /**
     * All exercise sessions that ended within [since]..[now], mapped to
     * [DetectedWorkout]. Sessions whose activity type Amethyst cannot represent
     * are skipped. Returns an empty list (never throws) if Health Connect is
     * unavailable or a read fails.
     */
    suspend fun readNewWorkouts(
        since: Instant,
        now: Instant = Instant.now(),
    ): List<DetectedWorkout> {
        if (!isAvailable(context)) return emptyList()

        return try {
            val response =
                client.readRecords(
                    ReadRecordsRequest(
                        recordType = ExerciseSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(since, now),
                    ),
                )
            response.records.mapNotNull { mapSession(it) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Failed to read workouts from Health Connect", e)
            emptyList()
        }
    }

    private suspend fun mapSession(session: ExerciseSessionRecord): DetectedWorkout? {
        val exercise = ExerciseTypeMapper.toExerciseType(session.exerciseType) ?: return null

        val durationSeconds = Duration.between(session.startTime, session.endTime).seconds
        if (durationSeconds <= 0) return null

        val totals = aggregate(session)

        return DetectedWorkout(
            id = session.metadata.id,
            exercise = exercise,
            title = session.title?.takeIf { it.isNotBlank() },
            startTimeEpochSeconds = session.startTime.epochSecond,
            durationSeconds = durationSeconds,
            distanceMeters = totals?.get(DistanceRecord.DISTANCE_TOTAL)?.inMeters,
            calories = totals?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories?.roundToInt(),
            avgHeartRate = totals?.get(HeartRateRecord.BPM_AVG)?.toInt(),
            maxHeartRate = totals?.get(HeartRateRecord.BPM_MAX)?.toInt(),
            steps = totals?.get(StepsRecord.COUNT_TOTAL)?.toInt(),
            elevationGainMeters = totals?.get(ElevationGainedRecord.ELEVATION_GAINED_TOTAL)?.inMeters,
        )
    }

    /** Aggregates the optional metrics over the session window. Null if aggregation fails. */
    private suspend fun aggregate(session: ExerciseSessionRecord): AggregationResult? =
        try {
            client.aggregate(
                AggregateRequest(
                    metrics =
                        setOf(
                            DistanceRecord.DISTANCE_TOTAL,
                            TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                            HeartRateRecord.BPM_AVG,
                            HeartRateRecord.BPM_MAX,
                            StepsRecord.COUNT_TOTAL,
                            ElevationGainedRecord.ELEVATION_GAINED_TOTAL,
                        ),
                    timeRangeFilter = TimeRangeFilter.between(session.startTime, session.endTime),
                ),
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Failed to aggregate workout metrics", e)
            null
        }
}
