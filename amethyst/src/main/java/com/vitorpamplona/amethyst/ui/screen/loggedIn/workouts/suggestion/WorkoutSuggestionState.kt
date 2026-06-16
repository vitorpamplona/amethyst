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

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.service.workouts.health.DetectedWorkout
import com.vitorpamplona.amethyst.service.workouts.health.HealthConnectManager
import com.vitorpamplona.amethyst.service.workouts.health.HealthConnectStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import java.time.Instant

/**
 * Foreground state holder for the Health Connect workout suggestions shown on
 * the Workouts screen. Scans Health Connect for recently finished workouts the
 * user has not yet handled and exposes them for the suggestion banner.
 *
 * Remembered in composition (not a ViewModel) to match the codebase's
 * permission-launcher pattern; [refresh] is driven from a LaunchedEffect.
 */
@Stable
class WorkoutSuggestionState(
    private val manager: HealthConnectManager,
    private val store: HealthConnectStore,
    private val pubkeyHex: String,
) {
    private val _suggestions = MutableStateFlow<List<DetectedWorkout>>(emptyList())
    val suggestions = _suggestions.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission = _hasPermission.asStateFlow()

    suspend fun refresh() {
        val granted = manager.hasAllPermissions()
        _hasPermission.value = granted
        if (!granted) {
            _suggestions.value = emptyList()
            return
        }

        val since = Instant.now().minus(Duration.ofDays(HealthConnectStore.LOOKBACK_DAYS))
        val handled = store.handledIds(pubkeyHex)

        _suggestions.value =
            manager
                .readNewWorkouts(since)
                .filter { it.id !in handled }
                .sortedByDescending { it.startTimeEpochSeconds }
                .take(HealthConnectStore.MAX_SUGGESTIONS)
    }

    /** Marks a workout handled (accepted or dismissed) so it is not offered again. */
    fun handle(workoutId: String) {
        store.markHandled(pubkeyHex, workoutId)
        _suggestions.value = _suggestions.value.filterNot { it.id == workoutId }
    }
}
