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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.suggestion

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.BooleanType
import com.vitorpamplona.amethyst.service.workouts.health.DetectedWorkout
import com.vitorpamplona.amethyst.service.workouts.health.HealthConnectManager
import com.vitorpamplona.amethyst.service.workouts.health.HealthConnectStore
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.labelRes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.symbol
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.SourceTag
import kotlinx.coroutines.launch

/**
 * Banner shown above the Workouts feed. When Health Connect is available it
 * either invites the user to connect (first run) or surfaces workouts detected
 * since the last visit, each offering to open the pre-filled kind 1301 composer.
 * Renders nothing on devices without Health Connect or when the user disabled
 * the suggestion in Compose settings.
 */
@Composable
fun WorkoutSuggestions(
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val available = remember { HealthConnectManager.isAvailable(context) }
    if (!available) return

    val suggestEnabled by accountViewModel.settings.uiSettingsFlow.suggestWorkoutsFromHealthConnect
        .collectAsStateWithLifecycle()
    if (suggestEnabled == BooleanType.NEVER) return

    val pubkeyHex = accountViewModel.account.signer.pubKey
    val scope = rememberCoroutineScope()
    val state =
        remember(pubkeyHex) {
            WorkoutSuggestionState(
                manager = HealthConnectManager(context),
                store = HealthConnectStore(context),
                pubkeyHex = pubkeyHex,
                scope = scope,
            )
        }

    val permissionLauncher =
        rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
            scope.launch { state.refresh() }
        }

    LaunchedEffect(pubkeyHex) { state.refresh() }

    val suggestions by state.suggestions.collectAsStateWithLifecycle()
    val hasPermission by state.hasPermission.collectAsStateWithLifecycle()
    var connectDismissed by rememberSaveable(pubkeyHex) { mutableStateOf(false) }

    if (suggestions.isEmpty() && (hasPermission || connectDismissed)) return

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!hasPermission && !connectDismissed) {
            ConnectHealthCard(
                onConnect = { permissionLauncher.launch(HealthConnectManager.PERMISSIONS) },
                onDismiss = { connectDismissed = true },
            )
        }

        suggestions.forEach { workout ->
            // RUNSTR always emits a title; default to the activity name when Health
            // Connect gives none, so every shared event carries one.
            val routeTitle = workout.title ?: stringRes(workout.exercise.labelRes())
            WorkoutSuggestionRow(
                workout = workout,
                onShare = {
                    state.handle(workout.id)
                    nav.nav(workout.toNewWorkoutRoute(routeTitle))
                },
                onDismiss = { state.handle(workout.id) },
            )
        }
    }
}

@Composable
private fun ConnectHealthCard(
    onConnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActivityBadge(MaterialSymbols.Favorite)
            Text(
                text = stringRes(R.string.workout_suggestion_connect_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(start = 14.dp),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    symbol = MaterialSymbols.Close,
                    contentDescription = stringRes(R.string.workout_suggestion_dismiss),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = stringRes(R.string.workout_suggestion_connect_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(onClick = onConnect) {
                Text(stringRes(R.string.workout_suggestion_connect_button))
            }
        }
    }
}

@Composable
private fun WorkoutSuggestionRow(
    workout: DetectedWorkout,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActivityBadge(workout.exercise.symbol())
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    text = workout.title ?: stringRes(workout.exercise.labelRes()),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = relativeTime(workout.startTimeEpochSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    symbol = MaterialSymbols.Close,
                    contentDescription = stringRes(R.string.workout_suggestion_dismiss),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        MetricChips(workout)

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.workout_suggestion_dismiss))
            }
            FilledTonalButton(onClick = onShare) {
                Text(stringRes(R.string.workout_suggestion_share))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetricChips(workout: DetectedWorkout) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MetricChip(MaterialSymbols.Timer, formatDuration(workout.durationSeconds))
        workout.distanceMeters?.takeIf { it > 0 }?.let {
            MetricChip(null, stringRes(R.string.workout_suggestion_distance_km, "%.2f".format(it / 1000.0)))
        }
        workout.avgHeartRate?.takeIf { it > 0 }?.let {
            MetricChip(MaterialSymbols.Favorite, stringRes(R.string.workout_suggestion_heart_rate, it.toString()))
        }
        workout.calories?.takeIf { it > 0 }?.let {
            MetricChip(MaterialSymbols.LocalFireDepartment, stringRes(R.string.workout_suggestion_calories, it.toString()))
        }
        workout.steps?.takeIf { it > 0 }?.let {
            MetricChip(MaterialSymbols.DirectionsWalk, it.toString())
        }
    }
}

@Composable
private fun MetricChip(
    symbol: MaterialSymbol?,
    text: String,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (symbol != null) {
                Icon(
                    symbol = symbol,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Circular tinted badge holding the activity (or section) glyph. */
@Composable
private fun ActivityBadge(symbol: MaterialSymbol) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                symbol = symbol,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

private fun relativeTime(epochSeconds: Long): String =
    DateUtils
        .getRelativeTimeSpanString(
            epochSeconds * 1000L,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()

private fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%d:%02d".format(m, s)
    }
}

private fun DetectedWorkout.toNewWorkoutRoute(title: String) =
    Route.NewWorkout(
        exercise = exercise.code,
        title = title,
        durationSeconds = durationSeconds,
        distanceMeters = distanceMeters ?: 0.0,
        calories = calories ?: 0,
        avgHeartRate = avgHeartRate ?: 0,
        maxHeartRate = maxHeartRate ?: 0,
        steps = steps ?: 0,
        elevationGainMeters = elevationGainMeters ?: 0.0,
        startTime = startTimeEpochSeconds,
        source = SourceTag.HEALTH_CONNECT,
    )
