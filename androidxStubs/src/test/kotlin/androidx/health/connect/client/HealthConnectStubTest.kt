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
package androidx.health.connect.client

import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import com.vitorpamplona.amethyst.shared.platform.JvmContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class HealthConnectStubTest {
    @Test
    fun `the sdk reports itself unavailable, not merely out of date`() {
        // The app shows a different, actionable message for each: "update the
        // provider" would send a desktop user looking for an app that does not
        // exist for their platform.
        assertEquals(HealthConnectClient.SDK_UNAVAILABLE, HealthConnectClient.getSdkStatus(JvmContext))
    }

    @Test
    fun `creating a client fails instead of handing back an empty one`() {
        // An empty client would answer "you have no workouts", which is a claim
        // about the user's data this build has no basis to make.
        assertFailsWith<IllegalStateException> { HealthConnectClient.getOrCreate(JvmContext) }
    }

    @Test
    fun `permission names match the platform's`() {
        // These are compared against what a provider reports, so they only mean
        // something if both sides spell them the same way.
        assertEquals(
            "android.permission.health.READ_EXERCISE_SESSION",
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        )
        assertEquals(
            "android.permission.health.READ_DISTANCE",
            HealthPermission.getReadPermission(DistanceRecord::class),
        )
        assertEquals(
            "android.permission.health.READ_ACTIVE_CALORIES_BURNED",
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        )
        assertEquals(
            "android.permission.health.READ_HEART_RATE",
            HealthPermission.getReadPermission(HeartRateRecord::class),
        )
        assertEquals("android.permission.health.READ_STEPS", HealthPermission.getReadPermission(StepsRecord::class))
    }

    @Test
    fun `units convert the way the platform defines them`() {
        // A workout mapped through these carries the numbers the NIP-101e event
        // publishes, so a wrong factor is a wrong distance on someone's note.
        assertEquals(5000.0, Length.kilometers(5.0).inMeters)
        assertEquals(1609.344, Length.miles(1.0).inMeters)
        assertEquals(5.0, Length.meters(5000.0).inKilometers)
        assertEquals(1000.0, Energy.kilocalories(1.0).inCalories)
        assertEquals(4184.0, Energy.kilocalories(1.0).inJoules)
    }

    @Test
    fun `an aggregation reads back by metric and is null for what it did not cover`() {
        val result =
            AggregationResult(
                mapOf(
                    DistanceRecord.DISTANCE_TOTAL.metricKey to Length.kilometers(10.0),
                    HeartRateRecord.BPM_AVG.metricKey to 142L,
                ),
            )

        assertEquals(10000.0, result[DistanceRecord.DISTANCE_TOTAL]?.inMeters)
        assertEquals(142L, result[HeartRateRecord.BPM_AVG])
        // No heart-rate strap on a session means no max, and the mapper treats
        // every metric as optional.
        assertNull(result[HeartRateRecord.BPM_MAX])
        assertNull(result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL])
    }

    @Test
    fun `metric keys are distinct, so one metric cannot read another's value`() {
        val keys =
            listOf(
                DistanceRecord.DISTANCE_TOTAL,
                ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                HeartRateRecord.BPM_AVG,
                HeartRateRecord.BPM_MAX,
                StepsRecord.COUNT_TOTAL,
            ).map { it.metricKey }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `the exercise type codes are the platform's own`() {
        // The mapper's table is keyed on these, so a wrong code silently maps a
        // run to a swim.
        assertEquals(56, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING)
        assertEquals(57, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL)
        assertEquals(8, ExerciseSessionRecord.EXERCISE_TYPE_BIKING)
        assertEquals(79, ExerciseSessionRecord.EXERCISE_TYPE_WALKING)
        assertEquals(74, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL)
        assertEquals(83, ExerciseSessionRecord.EXERCISE_TYPE_YOGA)
    }
}
