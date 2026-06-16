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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.PostingTopBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.DistanceTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseType

@Composable
fun NewWorkoutScreen(
    prefill: Route.NewWorkout,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val postViewModel: NewWorkoutViewModel = viewModel()
    postViewModel.init(accountViewModel)
    postViewModel.prefill(prefill)

    BackHandler {
        postViewModel.cancel()
        nav.popBack()
    }

    Scaffold(
        topBar = {
            PostingTopBar(
                titleRes = R.string.new_workout,
                isActive = postViewModel::canPost,
                onCancel = {
                    postViewModel.cancel()
                    nav.popBack()
                },
                onPost = {
                    accountViewModel.launchSigner {
                        postViewModel.sendPostSync()
                        nav.popBack()
                    }
                },
            )
        },
    ) { pad ->
        Surface(
            modifier =
                Modifier
                    .padding(pad)
                    .consumeWindowInsets(pad)
                    .imePadding(),
        ) {
            NewWorkoutBody(postViewModel)
        }
    }
}

@Composable
private fun NewWorkoutBody(postViewModel: NewWorkoutViewModel) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ExerciseTypeSelector(postViewModel.exercise) { postViewModel.exercise = it }

        OutlinedTextField(
            value = postViewModel.title,
            onValueChange = { postViewModel.title = it },
            label = { Text(stringRes(R.string.workout_title)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Text(
            text = stringRes(R.string.workout_duration),
            style = MaterialTheme.typography.titleSmall,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(postViewModel.hours, { postViewModel.hours = it }, stringRes(R.string.workout_hours), Modifier.weight(1f))
            NumberField(postViewModel.minutes, { postViewModel.minutes = it }, stringRes(R.string.workout_minutes), Modifier.weight(1f))
            NumberField(postViewModel.seconds, { postViewModel.seconds = it }, stringRes(R.string.workout_seconds), Modifier.weight(1f))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = postViewModel.distance,
                onValueChange = { postViewModel.distance = it },
                label = { Text(stringRes(R.string.workout_distance)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(2f),
                singleLine = true,
            )

            FilterChip(
                selected = postViewModel.distanceUnit == DistanceTag.KILOMETERS,
                onClick = { postViewModel.distanceUnit = DistanceTag.KILOMETERS },
                label = { Text(DistanceTag.KILOMETERS) },
            )
            FilterChip(
                selected = postViewModel.distanceUnit == DistanceTag.MILES,
                onClick = { postViewModel.distanceUnit = DistanceTag.MILES },
                label = { Text(DistanceTag.MILES) },
            )
        }

        NumberField(postViewModel.calories, { postViewModel.calories = it }, stringRes(R.string.workout_calories), Modifier.fillMaxWidth())

        OutlinedTextField(
            value = postViewModel.notes,
            onValueChange = { postViewModel.notes = it },
            label = { Text(stringRes(R.string.workout_notes)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
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
