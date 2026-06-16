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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.experimental.fitness.workout.WorkoutRecordEvent
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.DistanceTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.DurationTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.Elevation
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseType
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.WeightTag
import kotlin.math.abs
import kotlin.math.round

fun ExerciseType?.symbol(): MaterialSymbol =
    when (this) {
        ExerciseType.RUNNING -> MaterialSymbols.DirectionsRun
        ExerciseType.WALKING -> MaterialSymbols.DirectionsWalk
        ExerciseType.CYCLING -> MaterialSymbols.DirectionsBike
        ExerciseType.HIKING -> MaterialSymbols.Hiking
        ExerciseType.SWIMMING -> MaterialSymbols.Pool
        ExerciseType.ROWING -> MaterialSymbols.Rowing
        ExerciseType.STRENGTH -> MaterialSymbols.FitnessCenter
        ExerciseType.YOGA -> MaterialSymbols.SelfImprovement
        ExerciseType.MEDITATION -> MaterialSymbols.SelfImprovement
        ExerciseType.DIET -> MaterialSymbols.Restaurant
        ExerciseType.FASTING -> MaterialSymbols.Timer
        null -> MaterialSymbols.DirectionsRun
    }

fun ExerciseType.labelRes(): Int =
    when (this) {
        ExerciseType.RUNNING -> R.string.exercise_running
        ExerciseType.WALKING -> R.string.exercise_walking
        ExerciseType.CYCLING -> R.string.exercise_cycling
        ExerciseType.HIKING -> R.string.exercise_hiking
        ExerciseType.SWIMMING -> R.string.exercise_swimming
        ExerciseType.ROWING -> R.string.exercise_rowing
        ExerciseType.STRENGTH -> R.string.exercise_strength
        ExerciseType.YOGA -> R.string.exercise_yoga
        ExerciseType.MEDITATION -> R.string.exercise_meditation
        ExerciseType.DIET -> R.string.exercise_diet
        ExerciseType.FASTING -> R.string.exercise_fasting
    }

private fun Double.trimmed(): String = if (this % 1.0 == 0.0 && abs(this) < 1e15) toLong().toString() else toString()

private fun paceMinPerUnit(
    durationSeconds: Long,
    distanceValue: Double,
): String {
    val secondsPerUnit = (durationSeconds / distanceValue).toLong()
    return "${secondsPerUnit / 60}:${(secondsPerUnit % 60).toString().padStart(2, '0')}"
}

/** Average speed, more natural than pace for wheeled/water sports. Returns e.g. `24.5 km/h` or `15.2 mph`. */
private fun speed(
    durationSeconds: Long,
    distance: DistanceTag,
): String {
    val hours = durationSeconds / 3600.0
    return if (distance.unit == DistanceTag.MILES) {
        "${trimToOneDecimal(distance.value / hours)} mph"
    } else {
        "${trimToOneDecimal(distance.toKilometers() / hours)} km/h"
    }
}

private fun trimToOneDecimal(value: Double): String {
    val rounded = round(value * 10.0) / 10.0
    return rounded.trimmed()
}

/** One-shot snapshot of the parsed workout tags, so the feed doesn't re-scan the tag array on every recomposition. */
@Immutable
class WorkoutInfo(
    val title: String?,
    val type: ExerciseType?,
    val exerciseRaw: String?,
    val source: String?,
    val durationSeconds: Long?,
    val distance: DistanceTag?,
    val elevationGain: Elevation?,
    val elevationLoss: Elevation?,
    val calories: Int?,
    val steps: Int?,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val sets: Int?,
    val reps: Int?,
    val weight: WeightTag?,
) {
    companion object {
        fun from(event: WorkoutRecordEvent) =
            WorkoutInfo(
                title = event.title(),
                type = event.exerciseType(),
                exerciseRaw = event.exercise(),
                source = event.workoutSource(),
                durationSeconds = event.durationSeconds(),
                distance = event.distance(),
                elevationGain = event.elevationGain(),
                elevationLoss = event.elevationLoss(),
                calories = event.calories(),
                steps = event.steps(),
                avgHeartRate = event.avgHeartRate(),
                maxHeartRate = event.maxHeartRate(),
                sets = event.sets(),
                reps = event.reps(),
                weight = event.weight(),
            )
    }
}

/** Which metric is promoted to the hero number, so the grid below can skip repeating it. */
private enum class HeroKind { DISTANCE, STEPS, DURATION, NONE }

/** A single secondary metric: a bold value over a muted label. */
@Immutable
private class Stat(
    val value: String,
    val label: String,
)

@Composable
fun WorkoutDisplay(baseNote: Note) {
    val event = (baseNote.event as? WorkoutRecordEvent) ?: return

    val info = remember(baseNote) { WorkoutInfo.from(event) }
    val typeLabel = info.type?.let { stringRes(it.labelRes()) } ?: info.exerciseRaw ?: stringRes(R.string.workout)

    val duration = info.durationSeconds
    val distance = info.distance
    val steps = info.steps

    val heroKind =
        when {
            distance != null && distance.value > 0.0 -> HeroKind.DISTANCE
            steps != null -> HeroKind.STEPS
            duration != null -> HeroKind.DURATION
            else -> HeroKind.NONE
        }

    val secondaryStats =
        buildSecondaryStats(
            info = info,
            heroKind = heroKind,
            durationLabel = stringRes(R.string.workout_duration),
            distanceLabel = stringRes(R.string.workout_distance),
            paceLabel = stringRes(R.string.workout_pace),
            speedLabel = stringRes(R.string.workout_speed),
            elevationGainLabel = stringRes(R.string.workout_elevation),
            elevationLossLabel = stringRes(R.string.workout_elevation_loss),
            caloriesLabel = stringRes(R.string.workout_calories),
            stepsLabel = stringRes(R.string.workout_steps),
            heartRateLabel = stringRes(R.string.workout_heart_rate),
            maxHeartRateLabel = stringRes(R.string.workout_max_heart_rate),
            setsLabel = stringRes(R.string.workout_sets),
            repsLabel = stringRes(R.string.workout_reps),
            weightLabel = stringRes(R.string.workout_weight),
        )

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    symbol = info.type.symbol(),
                    contentDescription = typeLabel,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.title ?: typeLabel,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (info.title != null) {
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.placeholderText,
                    )
                }
            }

            info.source?.let {
                Spacer(modifier = Modifier.width(8.dp))
                WorkoutSourceBadge(it)
            }
        }

        when (heroKind) {
            HeroKind.DISTANCE ->
                WorkoutHero(distance!!.value.trimmed(), distance.unit, stringRes(R.string.workout_distance))
            HeroKind.STEPS ->
                WorkoutHero(steps!!.toString(), null, stringRes(R.string.workout_steps))
            HeroKind.DURATION ->
                WorkoutHero(DurationTag.formatTime(duration!!), null, stringRes(R.string.workout_duration))
            HeroKind.NONE -> {}
        }

        WorkoutStatsGrid(secondaryStats)
    }
}

/** Builds the ordered list of secondary metrics, skipping whichever one is shown as the hero. */
private fun buildSecondaryStats(
    info: WorkoutInfo,
    heroKind: HeroKind,
    durationLabel: String,
    distanceLabel: String,
    paceLabel: String,
    speedLabel: String,
    elevationGainLabel: String,
    elevationLossLabel: String,
    caloriesLabel: String,
    stepsLabel: String,
    heartRateLabel: String,
    maxHeartRateLabel: String,
    setsLabel: String,
    repsLabel: String,
    weightLabel: String,
): List<Stat> {
    val duration = info.durationSeconds
    val distance = info.distance

    return buildList {
        if (heroKind != HeroKind.DURATION) {
            duration?.let { add(Stat(DurationTag.formatTime(it), durationLabel)) }
        }
        if (heroKind != HeroKind.DISTANCE) {
            distance?.let { add(Stat("${it.value.trimmed()} ${it.unit}", distanceLabel)) }
        }
        if (duration != null && distance != null && distance.value > 0.0) {
            // Cycling is conventionally reported as speed; running/walking/etc. as pace.
            if (info.type == ExerciseType.CYCLING) {
                add(Stat(speed(duration, distance), speedLabel))
            } else {
                add(Stat("${paceMinPerUnit(duration, distance.value)} /${distance.unit}", paceLabel))
            }
        }
        info.elevationGain?.let { add(Stat("${it.value.trimmed()} ${it.unit}", elevationGainLabel)) }
        info.elevationLoss?.let { add(Stat("${it.value.trimmed()} ${it.unit}", elevationLossLabel)) }
        info.calories?.let { add(Stat("$it kcal", caloriesLabel)) }
        if (heroKind != HeroKind.STEPS) {
            info.steps?.let { add(Stat("$it", stepsLabel)) }
        }
        info.avgHeartRate?.let { add(Stat("$it bpm", heartRateLabel)) }
        info.maxHeartRate?.let { add(Stat("$it bpm", maxHeartRateLabel)) }
        info.sets?.let { add(Stat("$it", setsLabel)) }
        info.reps?.let { add(Stat("$it", repsLabel)) }
        info.weight?.let { add(Stat("${it.value.trimmed()} ${it.unit}", weightLabel)) }
    }
}

/** The headline metric, shown large above the secondary grid (e.g. `5.2 km`). */
@Composable
private fun WorkoutHero(
    value: String,
    unit: String?,
    label: String,
) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            unit?.let {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.placeholderText,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.placeholderText,
        )
    }
}

/** Fixed-column grid so secondary metrics line up in tidy columns instead of free-flowing. */
@Composable
private fun WorkoutStatsGrid(
    stats: List<Stat>,
    columns: Int = 3,
) {
    if (stats.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        stats.chunked(columns).forEach { rowStats ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowStats.forEach { stat ->
                    WorkoutStat(stat.value, stat.label, Modifier.weight(1f))
                }
                // Pad the last row so columns stay aligned across rows.
                repeat(columns - rowStats.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Small chip showing how the workout was recorded (e.g. GPS, RUNSTR, HEALTHKIT, MANUAL). */
@Composable
private fun WorkoutSourceBadge(source: String) {
    Text(
        text = source.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun WorkoutStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.placeholderText,
        )
    }
}
