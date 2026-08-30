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
package androidx.health.connect.client.records

import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import java.time.Instant
import java.time.ZoneOffset

/**
 * JVM stand-ins for the Health Connect record types the app reads.
 *
 * Only the shapes the mapper touches, and only enough of them to compile: no
 * record is ever produced here, because [androidx.health.connect.client.HealthConnectClient]
 * reports the platform as unavailable before anything can read one. The
 * exercise-type codes are the platform's own, so the mapper's table means the
 * same thing on both platforms.
 */
interface Record {
    val metadata: Metadata
}

interface IntervalRecord : Record {
    val startTime: Instant
    val startZoneOffset: ZoneOffset?
    val endTime: Instant
    val endZoneOffset: ZoneOffset?
}

class ExerciseSessionRecord(
    override val startTime: Instant,
    override val startZoneOffset: ZoneOffset?,
    override val endTime: Instant,
    override val endZoneOffset: ZoneOffset?,
    val exerciseType: Int,
    val title: String? = null,
    val notes: String? = null,
    override val metadata: Metadata,
) : IntervalRecord {
    companion object {
        const val EXERCISE_TYPE_OTHER_WORKOUT = 0
        const val EXERCISE_TYPE_BADMINTON = 2
        const val EXERCISE_TYPE_BASEBALL = 4
        const val EXERCISE_TYPE_BASKETBALL = 5
        const val EXERCISE_TYPE_BIKING = 8
        const val EXERCISE_TYPE_BIKING_STATIONARY = 9
        const val EXERCISE_TYPE_BOOT_CAMP = 10
        const val EXERCISE_TYPE_CALISTHENICS = 13
        const val EXERCISE_TYPE_DANCING = 16
        const val EXERCISE_TYPE_ELLIPTICAL = 25
        const val EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING = 28
        const val EXERCISE_TYPE_HIKING = 29
        const val EXERCISE_TYPE_PILATES = 37
        const val EXERCISE_TYPE_ROWING = 39
        const val EXERCISE_TYPE_ROWING_MACHINE = 40
        const val EXERCISE_TYPE_RUNNING = 56
        const val EXERCISE_TYPE_RUNNING_TREADMILL = 57
        const val EXERCISE_TYPE_STAIR_CLIMBING = 62
        const val EXERCISE_TYPE_STRENGTH_TRAINING = 65
        const val EXERCISE_TYPE_SWIMMING_OPEN_WATER = 73
        const val EXERCISE_TYPE_SWIMMING_POOL = 74
        const val EXERCISE_TYPE_WALKING = 79
        const val EXERCISE_TYPE_WEIGHTLIFTING = 81
        const val EXERCISE_TYPE_YOGA = 83
    }
}

class DistanceRecord(
    override val startTime: Instant,
    override val startZoneOffset: ZoneOffset?,
    override val endTime: Instant,
    override val endZoneOffset: ZoneOffset?,
    val distance: Length,
    override val metadata: Metadata,
) : IntervalRecord {
    companion object {
        val DISTANCE_TOTAL: AggregateMetric<Length> = AggregateMetric("Distance_total")
    }
}

class ActiveCaloriesBurnedRecord(
    override val startTime: Instant,
    override val startZoneOffset: ZoneOffset?,
    override val endTime: Instant,
    override val endZoneOffset: ZoneOffset?,
    val energy: Energy,
    override val metadata: Metadata,
) : IntervalRecord {
    companion object {
        val ACTIVE_CALORIES_TOTAL: AggregateMetric<Energy> = AggregateMetric("ActiveCaloriesBurned_total")
    }
}

class TotalCaloriesBurnedRecord(
    override val startTime: Instant,
    override val startZoneOffset: ZoneOffset?,
    override val endTime: Instant,
    override val endZoneOffset: ZoneOffset?,
    val energy: Energy,
    override val metadata: Metadata,
) : IntervalRecord {
    companion object {
        val ENERGY_TOTAL: AggregateMetric<Energy> = AggregateMetric("TotalCaloriesBurned_energy_total")
    }
}

class HeartRateRecord(
    override val startTime: Instant,
    override val startZoneOffset: ZoneOffset?,
    override val endTime: Instant,
    override val endZoneOffset: ZoneOffset?,
    val samples: List<Sample>,
    override val metadata: Metadata,
) : IntervalRecord {
    class Sample(
        val time: Instant,
        val beatsPerMinute: Long,
    )

    companion object {
        val BPM_AVG: AggregateMetric<Long> = AggregateMetric("HeartRate_bpm_avg")
        val BPM_MIN: AggregateMetric<Long> = AggregateMetric("HeartRate_bpm_min")
        val BPM_MAX: AggregateMetric<Long> = AggregateMetric("HeartRate_bpm_max")
    }
}

class StepsRecord(
    override val startTime: Instant,
    override val startZoneOffset: ZoneOffset?,
    override val endTime: Instant,
    override val endZoneOffset: ZoneOffset?,
    val count: Long,
    override val metadata: Metadata,
) : IntervalRecord {
    companion object {
        val COUNT_TOTAL: AggregateMetric<Long> = AggregateMetric("Steps_count_total")
    }
}

class ElevationGainedRecord(
    override val startTime: Instant,
    override val startZoneOffset: ZoneOffset?,
    override val endTime: Instant,
    override val endZoneOffset: ZoneOffset?,
    val elevation: Length,
    override val metadata: Metadata,
) : IntervalRecord {
    companion object {
        val ELEVATION_GAINED_TOTAL: AggregateMetric<Length> = AggregateMetric("ElevationGained_elevation_total")
    }
}
