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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.DurationTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseType

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

private fun Double.trimmed(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()

private fun paceMinPerUnit(
    durationSeconds: Long,
    distanceValue: Double,
): String {
    val secondsPerUnit = (durationSeconds / distanceValue).toLong()
    return "${secondsPerUnit / 60}:${(secondsPerUnit % 60).toString().padStart(2, '0')}"
}

@Composable
fun WorkoutDisplay(baseNote: Note) {
    val event = (baseNote.event as? WorkoutRecordEvent) ?: return

    val type = event.exerciseType()
    val typeLabel = type?.let { stringRes(it.labelRes()) } ?: event.exercise() ?: stringRes(R.string.workout)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                symbol = type.symbol(),
                contentDescription = typeLabel,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column {
                Text(
                    text = event.title() ?: typeLabel,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (event.title() != null) {
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.placeholderText,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        WorkoutStatsRow(event)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkoutStatsRow(event: WorkoutRecordEvent) {
    val duration = event.durationSeconds()
    val distance = event.distance()

    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        duration?.let {
            WorkoutStat(DurationTag.formatTime(it), stringRes(R.string.workout_duration))
        }

        distance?.let {
            WorkoutStat("${it.value.trimmed()} ${it.unit}", stringRes(R.string.workout_distance))
        }

        if (duration != null && distance != null && distance.value > 0.0) {
            WorkoutStat(
                "${paceMinPerUnit(duration, distance.value)} /${distance.unit}",
                stringRes(R.string.workout_pace),
            )
        }

        event.elevationGain()?.let {
            WorkoutStat("${it.value.trimmed()} ${it.unit}", stringRes(R.string.workout_elevation))
        }

        event.calories()?.let {
            WorkoutStat("$it kcal", stringRes(R.string.workout_calories))
        }

        event.steps()?.let {
            WorkoutStat("$it", stringRes(R.string.workout_steps))
        }

        event.avgHeartRate()?.let {
            WorkoutStat("$it bpm", stringRes(R.string.workout_heart_rate))
        }

        event.sets()?.let {
            WorkoutStat("$it", stringRes(R.string.workout_sets))
        }

        event.reps()?.let {
            WorkoutStat("$it", stringRes(R.string.workout_reps))
        }

        event.weight()?.let {
            WorkoutStat("${it.value.trimmed()} ${it.unit}", stringRes(R.string.workout_weight))
        }
    }
}

@Composable
private fun WorkoutStat(
    value: String,
    label: String,
) {
    Column {
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
