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

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.service.workouts.health.HealthConnectManager
import com.vitorpamplona.amethyst.service.workouts.health.WorkoutStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * State holder for the My Fitness dashboard: the user's own training over the last
 * [WorkoutStats.WINDOW_DAYS], read from Health Connect and summarised by [WorkoutStats].
 *
 * Nothing here publishes or touches the network. The dashboard is the user looking at their
 * own numbers; sharing one of them is a separate, deliberate action from the workout list.
 */
@Stable
class MyFitnessViewModel : ViewModel() {
    @Immutable
    sealed interface State {
        /** First load, or a reload after a permission change. */
        data object Loading : State

        /** No Health Connect provider on this device — nothing to offer. */
        data object Unavailable : State

        /** Provider present, permissions not granted yet. */
        data object NeedsPermission : State

        /** Granted and summarised. [WorkoutStats.Report.isEmpty] covers "nothing recorded yet". */
        data class Ready(
            val report: WorkoutStats.Report,
        ) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private var manager: HealthConnectManager? = null

    /**
     * Refreshes the dashboard. Safe to call on every resume: it re-checks permissions first,
     * so revoking access in Health Connect drops the screen back to its prompt rather than
     * leaving stale numbers on display.
     */
    fun refresh(context: Context) {
        viewModelScope.launch {
            if (!HealthConnectManager.isAvailable(context)) {
                _state.value = State.Unavailable
                return@launch
            }

            val hc = manager ?: HealthConnectManager(context.applicationContext).also { manager = it }

            if (!hc.hasAllPermissions()) {
                _state.value = State.NeedsPermission
                return@launch
            }

            val now = Instant.now()
            val workouts = hc.readWorkouts(now.minus(Duration.ofDays(WorkoutStats.WINDOW_DAYS)), now)
            _state.value = State.Ready(WorkoutStats.report(workouts, now))
        }
    }
}
