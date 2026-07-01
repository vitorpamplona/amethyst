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
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.PostingTopBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.suggestion.DetectedWorkoutCarousel
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
            NewWorkoutBody(postViewModel, accountViewModel)
        }
    }
}

@Composable
private fun NewWorkoutBody(
    postViewModel: NewWorkoutViewModel,
    accountViewModel: AccountViewModel,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Tap a recent Health Connect workout to pre-load the form.
        DetectedWorkoutCarousel(
            accountViewModel = accountViewModel,
            onPick = { postViewModel.applyPrefill(it) },
        )

        WorkoutHero(postViewModel.exercise)

        ExerciseTypeSelector(postViewModel.exercise) { postViewModel.exercise = it }

        OutlinedTextField(
            value = postViewModel.title,
            onValueChange = { postViewModel.title = it },
            label = { Text(stringRes(R.string.workout_title)) },
            leadingIcon = { FieldIcon(MaterialSymbols.Edit) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        SectionLabel(MaterialSymbols.Schedule, stringRes(R.string.workout_duration))
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
                leadingIcon = { FieldIcon(MaterialSymbols.DirectionsRun) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
                singleLine = true,
            )

            SingleChoiceSegmentedButtonRow {
                val units = listOf(DistanceTag.KILOMETERS, DistanceTag.MILES)
                units.forEachIndexed { index, unit ->
                    SegmentedButton(
                        selected = postViewModel.distanceUnit == unit,
                        onClick = { postViewModel.distanceUnit = unit },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = units.size),
                    ) {
                        Text(unit)
                    }
                }
            }
        }

        OutlinedTextField(
            value = postViewModel.calories,
            onValueChange = { postViewModel.calories = it },
            label = { Text(stringRes(R.string.workout_calories)) },
            leadingIcon = { FieldIcon(MaterialSymbols.LocalFireDepartment) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = postViewModel.notes,
            onValueChange = { postViewModel.notes = it },
            label = { Text(stringRes(R.string.workout_notes)) },
            leadingIcon = { FieldIcon(MaterialSymbols.EditNote) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
    }
}

/** Big animated activity badge + name; the form's focal point, updating as the type changes. */
@Composable
private fun WorkoutHero(exercise: ExerciseType) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(88.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Crossfade(targetState = exercise, label = "workoutHeroIcon") { type ->
                    Icon(
                        symbol = type.symbol(),
                        contentDescription = null,
                        modifier = Modifier.size(46.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        Crossfade(targetState = exercise, label = "workoutHeroLabel") { type ->
            Text(
                text = stringRes(type.labelRes()),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseTypeSelector(
    selected: ExerciseType,
    onSelect: (ExerciseType) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ExerciseType.entries.forEach { type ->
            FilterChip(
                selected = type == selected,
                onClick = { onSelect(type) },
                label = { Text(stringRes(type.labelRes())) },
                leadingIcon = { FieldIcon(type.symbol(), size = 18.dp) },
            )
        }
    }
}

@Composable
private fun SectionLabel(
    symbol: MaterialSymbol,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FieldIcon(symbol, size = 18.dp, tint = MaterialTheme.colorScheme.primary)
        Text(text = label, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun FieldIcon(
    symbol: MaterialSymbol,
    size: Dp = 20.dp,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        symbol = symbol,
        contentDescription = null,
        modifier = Modifier.size(size),
        tint = tint,
    )
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
