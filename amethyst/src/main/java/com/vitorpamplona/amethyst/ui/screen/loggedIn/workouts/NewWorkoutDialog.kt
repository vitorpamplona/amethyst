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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.navigation.topbars.PostingTopBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.DistanceTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseType

@Composable
fun NewWorkoutDialog(
    onClose: () -> Unit,
    onPosted: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    var exercise by remember { mutableStateOf(ExerciseType.RUNNING) }
    var title by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    var seconds by remember { mutableStateOf("") }
    var distance by remember { mutableStateOf("") }
    var distanceUnit by remember { mutableStateOf(DistanceTag.KILOMETERS) }
    var calories by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    fun durationSeconds(): Long =
        (hours.toLongOrNull() ?: 0L) * 3600 +
            (minutes.toLongOrNull() ?: 0L) * 60 +
            (seconds.toLongOrNull() ?: 0L)

    Dialog(
        onDismissRequest = onClose,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Scaffold(
            topBar = {
                PostingTopBar(
                    titleRes = R.string.new_workout,
                    isActive = { durationSeconds() > 0 },
                    onPost = {
                        accountViewModel.launchSigner {
                            accountViewModel.account.sendWorkout(
                                exercise = exercise,
                                durationSeconds = durationSeconds(),
                                notes = notes,
                                title = title.ifBlank { null },
                                distanceValue = distance.toDoubleOrNull(),
                                distanceUnit = distanceUnit,
                                kcal = calories.toIntOrNull(),
                            )
                        }
                        onClose()
                        onPosted()
                    },
                    onCancel = onClose,
                )
            },
        ) { pad ->
            Surface(
                modifier =
                    Modifier
                        .padding(pad)
                        .fillMaxSize(),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp)
                            .imePadding()
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ExerciseTypeSelector(exercise) { exercise = it }

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringRes(R.string.workout_title)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    Text(
                        text = stringRes(R.string.workout_duration),
                        style = MaterialTheme.typography.titleSmall,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberField(hours, { hours = it }, stringRes(R.string.workout_hours), Modifier.weight(1f))
                        NumberField(minutes, { minutes = it }, stringRes(R.string.workout_minutes), Modifier.weight(1f))
                        NumberField(seconds, { seconds = it }, stringRes(R.string.workout_seconds), Modifier.weight(1f))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = distance,
                            onValueChange = { distance = it },
                            label = { Text(stringRes(R.string.workout_distance)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(2f),
                            singleLine = true,
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                FilterChip(
                                    selected = distanceUnit == DistanceTag.KILOMETERS,
                                    onClick = { distanceUnit = DistanceTag.KILOMETERS },
                                    label = { Text(DistanceTag.KILOMETERS) },
                                )
                                FilterChip(
                                    selected = distanceUnit == DistanceTag.MILES,
                                    onClick = { distanceUnit = DistanceTag.MILES },
                                    label = { Text(DistanceTag.MILES) },
                                )
                            }
                        }
                    }

                    NumberField(calories, { calories = it }, stringRes(R.string.workout_calories), Modifier.fillMaxWidth())

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(stringRes(R.string.workout_notes)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseTypeSelector(
    selected: ExerciseType,
    onSelect: (ExerciseType) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        ExerciseType.entries.forEach { type ->
            FilterChip(
                selected = type == selected,
                onClick = { onSelect(type) },
                label = { Text(stringRes(type.labelRes())) },
            )
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        singleLine = true,
    )
}
