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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.fitness

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.fitness.DetectedWorkout
import com.vitorpamplona.amethyst.commons.fitness.WorkoutStats
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.my_fitness_active_days
import com.vitorpamplona.amethyst.commons.resources.my_fitness_avg_heart_rate
import com.vitorpamplona.amethyst.commons.resources.my_fitness_best_biggest_climb
import com.vitorpamplona.amethyst.commons.resources.my_fitness_best_highest_heart_rate
import com.vitorpamplona.amethyst.commons.resources.my_fitness_best_longest_distance
import com.vitorpamplona.amethyst.commons.resources.my_fitness_best_longest_duration
import com.vitorpamplona.amethyst.commons.resources.my_fitness_best_most_steps
import com.vitorpamplona.amethyst.commons.resources.my_fitness_bests
import com.vitorpamplona.amethyst.commons.resources.my_fitness_by_activity
import com.vitorpamplona.amethyst.commons.resources.my_fitness_calories
import com.vitorpamplona.amethyst.commons.resources.my_fitness_connect_banner
import com.vitorpamplona.amethyst.commons.resources.my_fitness_connect_button
import com.vitorpamplona.amethyst.commons.resources.my_fitness_connect_message
import com.vitorpamplona.amethyst.commons.resources.my_fitness_connect_title
import com.vitorpamplona.amethyst.commons.resources.my_fitness_distance
import com.vitorpamplona.amethyst.commons.resources.my_fitness_elevation
import com.vitorpamplona.amethyst.commons.resources.my_fitness_empty
import com.vitorpamplona.amethyst.commons.resources.my_fitness_loading_metrics
import com.vitorpamplona.amethyst.commons.resources.my_fitness_max_heart_rate
import com.vitorpamplona.amethyst.commons.resources.my_fitness_recent
import com.vitorpamplona.amethyst.commons.resources.my_fitness_share
import com.vitorpamplona.amethyst.commons.resources.my_fitness_steps
import com.vitorpamplona.amethyst.commons.resources.my_fitness_streak
import com.vitorpamplona.amethyst.commons.resources.my_fitness_this_week
import com.vitorpamplona.amethyst.commons.resources.my_fitness_time
import com.vitorpamplona.amethyst.commons.resources.my_fitness_title
import com.vitorpamplona.amethyst.commons.resources.my_fitness_unit_bpm
import com.vitorpamplona.amethyst.commons.resources.my_fitness_unit_kcal
import com.vitorpamplona.amethyst.commons.resources.my_fitness_vs_last_week
import com.vitorpamplona.amethyst.commons.resources.my_fitness_weekly_average
import com.vitorpamplona.amethyst.commons.resources.my_fitness_window
import com.vitorpamplona.amethyst.commons.resources.my_fitness_window_note
import com.vitorpamplona.amethyst.commons.resources.my_fitness_workouts
import com.vitorpamplona.amethyst.commons.resources.workout_suggestion_connect_details
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.service.workouts.health.HealthConnectManager
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.health.HealthConnectRationaleActivity
import com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.labelRes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.suggestion.toNewWorkoutRoute
import com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.symbol

/**
 * The user's own training, summarised: how much they did this week against last, what they
 * spent the time on, their best efforts, and how many days in a row they have shown up.
 *
 * This is the reason Amethyst reads Health Connect at all — the numbers are for the person who
 * recorded them. Publishing one as a note is an optional action from the workout list, never a
 * precondition for seeing any of this.
 *
 * Reached from the drawer, or from a bottom-bar slot the user pinned.
 */
@Composable
fun MyFitnessScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: MyFitnessViewModel = viewModel()
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Which account's workouts to summarise. Re-runs on an account switch, which resets the
    // dashboard to Loading rather than showing the previous user's numbers.
    viewModel.init(accountViewModel.userProfile().pubkeyHex)

    val permissionLauncher =
        rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
            viewModel.refresh(context)
        }

    // Re-checks permissions as well as data, so revoking access in Health Connect drops the
    // screen back to its prompt instead of leaving stale numbers up.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh(context)
        onPauseOrDispose {}
    }

    val openRationale = { context.startActivity(Intent(context, HealthConnectRationaleActivity::class.java)) }
    val requestPermissions = { permissionLauncher.launch(HealthConnectManager.PERMISSIONS) }

    // DisappearingScaffold + AppBottomBar, like every other pinnable destination: My Fitness can
    // be pinned to the bottom bar, and a bare Scaffold would make that bar vanish on arrival.
    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = { TopBarWithBackButton(stringRes(Res.string.my_fitness_title), nav) },
        bottomBar = {
            AppBottomBar(Route.MyFitness, nav, accountViewModel) { route ->
                if (route != Route.MyFitness) nav.navBottomBar(route)
            }
        },
        accountViewModel = accountViewModel,
    ) { padding ->
        Surface(modifier = Modifier.padding(padding)) {
            when (val current = state) {
                MyFitnessViewModel.State.Loading -> CenteredBox { CircularProgressIndicator() }

                is MyFitnessViewModel.State.Ready ->
                    if (current.report.isEmpty) {
                        // Nothing logged yet. Offering Health Connect is the useful thing to do
                        // when it could fill the screen; otherwise just say the log is empty.
                        if (current.healthConnect == MyFitnessViewModel.HealthConnectStatus.AVAILABLE) {
                            ConnectPrompt(onDetails = openRationale, onConnect = requestPermissions)
                        } else {
                            CenteredBox {
                                Text(
                                    text = stringRes(Res.string.my_fitness_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 32.dp),
                                )
                            }
                        }
                    } else {
                        Dashboard(
                            report = current.report,
                            // Only offered when it would actually add something: a device with no
                            // provider gets no banner to act on.
                            showConnectBanner = current.healthConnect == MyFitnessViewModel.HealthConnectStatus.AVAILABLE,
                            metricsPending = current.metricsPending,
                            onDetails = openRationale,
                            onConnect = requestPermissions,
                        ) { workout, label ->
                            nav.nav(workout.toNewWorkoutRoute(label))
                        }
                    }
            }
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Shown above a dashboard that is already working, when Health Connect could add device-recorded
 * workouts to it. Deliberately a strip rather than a blocking card: the summary below it is real,
 * and this only offers to make it richer.
 */
@Composable
private fun ConnectBanner(
    onDetails: () -> Unit,
    onConnect: () -> Unit,
) {
    OutlinedCard(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringRes(Res.string.my_fitness_connect_title), style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringRes(Res.string.my_fitness_connect_banner),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDetails) { Text(stringRes(Res.string.workout_suggestion_connect_details)) }
                TextButton(onClick = onConnect) { Text(stringRes(Res.string.my_fitness_connect_button)) }
            }
        }
    }
}

/**
 * Shown while the per-session metrics are still being read from Health Connect. The dashboard
 * below it is already real — counts, time, streak, the activity split — but its distance,
 * calories and heart rate cells appear as each session's metrics land, and a row of numbers
 * growing on its own needs saying out loud.
 */
@Composable
private fun MetricsPendingNote() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringRes(Res.string.my_fitness_loading_metrics),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConnectPrompt(
    onDetails: () -> Unit,
    onConnect: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(56.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    symbol = MaterialSymbols.DirectionsRun,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Text(stringRes(Res.string.my_fitness_connect_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringRes(Res.string.my_fitness_connect_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDetails) { Text(stringRes(Res.string.workout_suggestion_connect_details)) }
            Button(onClick = onConnect) { Text(stringRes(Res.string.my_fitness_connect_button)) }
        }
    }
}

@Composable
private fun Dashboard(
    report: WorkoutStats.Report,
    showConnectBanner: Boolean,
    metricsPending: Boolean,
    onDetails: () -> Unit,
    onConnect: () -> Unit,
    onShare: (DetectedWorkout, String) -> Unit,
) {
    val miles = remember { prefersMiles() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (showConnectBanner) ConnectBanner(onDetails = onDetails, onConnect = onConnect)
        if (metricsPending) MetricsPendingNote()
        ThisWeekCard(report, miles)
        ConsistencyRow(report)
        WindowTotalsCard(report, miles)
        ActivityBreakdown(report, miles)
        BestEfforts(report, miles)
        RecentWorkouts(report, miles, metricsPending, onShare)

        Text(
            text = stringRes(Res.string.my_fitness_window_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThisWeekCard(
    report: WorkoutStats.Report,
    miles: Boolean,
) {
    SectionCard(stringRes(Res.string.my_fitness_this_week)) {
        // Workouts and time are always worth a cell. Distance and calories are not: a week of
        // strength work has neither, and a column of zeroes reads as missing data rather than
        // as "this activity doesn't have that metric". Last week still counts, so a drop to
        // zero keeps its cell and shows the fall.
        val cells =
            buildList {
                add(
                    StatCell(
                        stringRes(Res.string.my_fitness_workouts),
                        report.thisWeek.workoutCount.toString(),
                        null,
                        WorkoutStats.percentChange(report.thisWeek.workoutCount.toDouble(), report.previousWeek.workoutCount.toDouble()),
                    ),
                )
                add(
                    StatCell(
                        stringRes(Res.string.my_fitness_time),
                        formatDuration(report.thisWeek.durationSeconds),
                        null,
                        WorkoutStats.percentChange(report.thisWeek.durationSeconds.toDouble(), report.previousWeek.durationSeconds.toDouble()),
                    ),
                )
                if (report.thisWeek.distanceMeters > 0 || report.previousWeek.distanceMeters > 0) {
                    add(
                        StatCell(
                            stringRes(Res.string.my_fitness_distance),
                            formatDistanceValue(report.thisWeek.distanceMeters, miles),
                            distanceUnit(miles),
                            WorkoutStats.percentChange(report.thisWeek.distanceMeters, report.previousWeek.distanceMeters),
                        ),
                    )
                }
                if (report.thisWeek.calories > 0 || report.previousWeek.calories > 0) {
                    add(
                        StatCell(
                            stringRes(Res.string.my_fitness_calories),
                            report.thisWeek.calories.toString(),
                            stringRes(Res.string.my_fitness_unit_kcal),
                            WorkoutStats.percentChange(report.thisWeek.calories.toDouble(), report.previousWeek.calories.toDouble()),
                        ),
                    )
                }
            }
        StatGrid(cells)
    }
}

@Composable
private fun ConsistencyRow(report: WorkoutStats.Report) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        HighlightTile(report.currentStreakDays.toString(), stringRes(Res.string.my_fitness_streak), Modifier.weight(1f))
        HighlightTile(report.activeDays.toString(), stringRes(Res.string.my_fitness_active_days), Modifier.weight(1f))
        HighlightTile(report.windowTotals.workoutCount.toString(), stringRes(Res.string.my_fitness_workouts), Modifier.weight(1f))
    }
}

@Composable
private fun WindowTotalsCard(
    report: WorkoutStats.Report,
    miles: Boolean,
) {
    SectionCard("${stringRes(Res.string.my_fitness_window)} · ${stringRes(Res.string.my_fitness_weekly_average)}") {
        val cells =
            buildList {
                add(StatCell(stringRes(Res.string.my_fitness_time), formatDuration(report.weeklyAverage.durationSeconds), null, null))
                if (report.windowTotals.distanceMeters > 0) {
                    add(
                        StatCell(
                            stringRes(Res.string.my_fitness_distance),
                            formatDistanceValue(report.weeklyAverage.distanceMeters, miles),
                            distanceUnit(miles),
                            null,
                        ),
                    )
                }
                if (report.windowTotals.steps > 0) {
                    add(StatCell(stringRes(Res.string.my_fitness_steps), report.weeklyAverage.steps.toString(), null, null))
                }
                if (report.windowTotals.elevationGainMeters > 0) {
                    add(
                        StatCell(
                            stringRes(Res.string.my_fitness_elevation),
                            formatElevationValue(report.weeklyAverage.elevationGainMeters, miles),
                            elevationUnit(miles),
                            null,
                        ),
                    )
                }
                report.windowTotals.avgHeartRate?.let {
                    add(StatCell(stringRes(Res.string.my_fitness_avg_heart_rate), it.toString(), stringRes(Res.string.my_fitness_unit_bpm), null))
                }
                report.windowTotals.maxHeartRate?.let {
                    add(StatCell(stringRes(Res.string.my_fitness_max_heart_rate), it.toString(), stringRes(Res.string.my_fitness_unit_bpm), null))
                }
            }
        StatGrid(cells)
    }
}

@Composable
private fun ActivityBreakdown(
    report: WorkoutStats.Report,
    miles: Boolean,
) {
    if (report.byActivity.isEmpty()) return

    SectionCard(stringRes(Res.string.my_fitness_by_activity)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            report.byActivity.forEach { activity ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        symbol = activity.exercise.symbol(),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringRes(activity.exercise.labelRes()),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = activitySummary(activity, miles),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun activitySummary(
    activity: WorkoutStats.ActivityTotals,
    miles: Boolean,
): String {
    val parts = mutableListOf<String>()
    parts.add("${activity.totals.workoutCount}×")
    parts.add(formatDuration(activity.totals.durationSeconds))
    if (activity.totals.distanceMeters > 0) {
        parts.add("${formatDistanceValue(activity.totals.distanceMeters, miles)} ${distanceUnit(miles)}")
    }
    return parts.joinToString(" · ")
}

@Composable
private fun BestEfforts(
    report: WorkoutStats.Report,
    miles: Boolean,
) {
    if (report.bests.isEmpty()) return

    SectionCard(stringRes(Res.string.my_fitness_bests)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            report.bests.forEach { best ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = bestLabel(best.kind),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = bestValue(best, miles),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun bestLabel(kind: WorkoutStats.BestKind): String =
    when (kind) {
        WorkoutStats.BestKind.LONGEST_DISTANCE -> stringRes(Res.string.my_fitness_best_longest_distance)
        WorkoutStats.BestKind.LONGEST_DURATION -> stringRes(Res.string.my_fitness_best_longest_duration)
        WorkoutStats.BestKind.BIGGEST_CLIMB -> stringRes(Res.string.my_fitness_best_biggest_climb)
        WorkoutStats.BestKind.MOST_STEPS -> stringRes(Res.string.my_fitness_best_most_steps)
        WorkoutStats.BestKind.HIGHEST_HEART_RATE -> stringRes(Res.string.my_fitness_best_highest_heart_rate)
    }

@Composable
private fun bestValue(
    best: WorkoutStats.Best,
    miles: Boolean,
): String =
    when (best.kind) {
        WorkoutStats.BestKind.LONGEST_DISTANCE ->
            "${formatDistanceValue(best.workout.distanceMeters ?: 0.0, miles)} ${distanceUnit(miles)}"
        WorkoutStats.BestKind.LONGEST_DURATION -> formatDuration(best.workout.durationSeconds)
        WorkoutStats.BestKind.BIGGEST_CLIMB ->
            "${formatElevationValue(best.workout.elevationGainMeters ?: 0.0, miles)} ${elevationUnit(miles)}"
        WorkoutStats.BestKind.MOST_STEPS -> (best.workout.steps ?: 0).toString()
        WorkoutStats.BestKind.HIGHEST_HEART_RATE ->
            "${best.workout.maxHeartRate ?: 0} ${stringRes(Res.string.my_fitness_unit_bpm)}"
    }

@Composable
private fun RecentWorkouts(
    report: WorkoutStats.Report,
    miles: Boolean,
    metricsPending: Boolean,
    onShare: (DetectedWorkout, String) -> Unit,
) {
    SectionCard(stringRes(Res.string.my_fitness_recent)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            report.workouts.take(RECENT_LIMIT).forEach { workout ->
                // Resolved here rather than in the click lambda: displayLabel reads a string
                // resource, which only composition can do.
                val label = workout.displayLabel()
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(
                            symbol = workout.exercise.symbol(),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        // Only what is still unpublished can be shared. Offering to share a
                        // workout that is already posted would publish a second kind 1301 for
                        // the same effort — whether it came back from a relay, or is the
                        // Health Connect copy of one the user shared earlier.
                        //
                        // Nor while the metrics are still loading. The composer route carries
                        // them as primitives where 0 means absent, so sharing a workout whose
                        // aggregations have not come back yet posts a kind 1301 with its
                        // duration and nothing else — and then the workout counts as shared, so
                        // the full numbers never get their turn. Everything still offering a
                        // Share button in that window is a Health Connect workout, since a
                        // published one is by definition already published, so the wait is
                        // blanket rather than per-workout.
                        if (!workout.alreadyPublished && !metricsPending) {
                            TextButton(onClick = { onShare(workout, label) }) {
                                Text(stringRes(Res.string.my_fitness_share), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    Text(
                        text = workoutSummary(workout, miles),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private const val RECENT_LIMIT = 15

@Composable
private fun DetectedWorkout.displayLabel(): String = title?.takeIf { it.isNotBlank() } ?: stringRes(exercise.labelRes())

@Composable
private fun workoutSummary(
    workout: DetectedWorkout,
    miles: Boolean,
): String {
    val parts = mutableListOf<String>()
    parts.add(formatDuration(workout.durationSeconds))
    workout.distanceMeters?.takeIf { it > 0 }?.let { parts.add("${formatDistanceValue(it, miles)} ${distanceUnit(miles)}") }
    workout.calories?.takeIf { it > 0 }?.let { parts.add("$it ${stringRes(Res.string.my_fitness_unit_kcal)}") }
    workout.avgHeartRate?.takeIf { it > 0 }?.let { parts.add("$it ${stringRes(Res.string.my_fitness_unit_bpm)}") }
    workout.elevationGainMeters?.takeIf { it > 0 }?.let { parts.add("${formatElevationValue(it, miles)} ${elevationUnit(miles)}") }
    return parts.joinToString(" · ")
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        OutlinedCard(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.padding(14.dp)) { content() }
        }
    }
}

@Composable
private fun HighlightTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class StatCell(
    val label: String,
    val value: String,
    val unit: String?,
    val percentChange: Int?,
)

/** Two-column grid of stats — a plain Column of Rows so it nests inside the scrolling page. */
@Composable
private fun StatGrid(cells: List<StatCell>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        cells.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { cell ->
                    StatCellView(cell, Modifier.weight(1f))
                }
                // Keeps a lone trailing cell at half width instead of stretching it across.
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatCellView(
    cell: StatCell,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = cell.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            cell.unit?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
        Text(
            text = cell.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        cell.percentChange?.let { change ->
            Text(
                text = "${if (change >= 0) "+" else ""}$change% ${stringRes(Res.string.my_fitness_vs_last_week)}",
                style = MaterialTheme.typography.labelSmall,
                color =
                    if (change >= 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}
