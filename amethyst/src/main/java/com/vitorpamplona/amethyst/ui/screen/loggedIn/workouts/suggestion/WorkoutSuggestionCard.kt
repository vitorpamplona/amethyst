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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.BooleanType
import com.vitorpamplona.amethyst.service.workouts.health.DetectedWorkout
import com.vitorpamplona.amethyst.service.workouts.health.HealthConnectManager
import com.vitorpamplona.amethyst.service.workouts.health.HealthConnectStore
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.labelRes
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.SourceTag
import kotlinx.coroutines.launch

/**
 * Banner shown above the Workouts feed. When Health Connect is available it
 * either invites the user to connect (first run) or surfaces workouts detected
 * since the last visit, each offering to open the pre-filled kind 1301 composer.
 * Renders nothing on devices without Health Connect.
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
    val state =
        remember(pubkeyHex) {
            WorkoutSuggestionState(
                manager = HealthConnectManager(context),
                store = HealthConnectStore(context),
                pubkeyHex = pubkeyHex,
            )
        }
    val scope = rememberCoroutineScope()

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
        modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!hasPermission && !connectDismissed) {
            ConnectHealthCard(
                onConnect = { permissionLauncher.launch(HealthConnectManager.PERMISSIONS) },
                onDismiss = { connectDismissed = true },
            )
        }

        suggestions.forEach { workout ->
            WorkoutSuggestionRow(
                workout = workout,
                onShare = {
                    state.handle(workout.id)
                    nav.nav(workout.toNewWorkoutRoute())
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
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringRes(R.string.workout_suggestion_connect_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    symbol = MaterialSymbols.Close,
                    contentDescription = stringRes(R.string.workout_suggestion_dismiss),
                )
            }
        }
        Text(
            text = stringRes(R.string.workout_suggestion_connect_message),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onConnect) {
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
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                symbol = MaterialSymbols.DirectionsRun,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = workout.title ?: stringRes(R.string.workout_suggestion_detected_title),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = workout.summaryLine(),
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
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onShare) {
                Text(stringRes(R.string.workout_suggestion_share))
            }
        }
    }
}

/** "Running · 5.20 km · 28:14" — activity, distance (if any), then duration. */
@Composable
private fun DetectedWorkout.summaryLine(): String {
    val parts = mutableListOf(stringRes(exercise.labelRes()))
    distanceMeters?.takeIf { it > 0 }?.let {
        parts.add(stringRes(R.string.workout_suggestion_distance_km, "%.2f".format(it / 1000.0)))
    }
    parts.add(formatDuration(durationSeconds))
    return parts.joinToString(" · ")
}

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

private fun DetectedWorkout.toNewWorkoutRoute() =
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
