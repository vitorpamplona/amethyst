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
import com.vitorpamplona.amethyst.commons.fitness.DetectedWorkout
import com.vitorpamplona.amethyst.commons.fitness.TrainingLog
import com.vitorpamplona.amethyst.commons.fitness.WorkoutStats
import com.vitorpamplona.amethyst.service.workouts.health.HealthConnectManager
import com.vitorpamplona.amethyst.service.workouts.health.publishedWorkoutsOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * State holder for the My Fitness dashboard: the user's own training over the last
 * [WorkoutStats.WINDOW_DAYS], summarised by [WorkoutStats].
 *
 * The log is built from both sources Amethyst has — Health Connect, and the user's own published
 * kind 1301 events (see [TrainingLog]) — so the dashboard is useful before any health permission
 * is granted and stays useful if one is revoked. Health Connect adds device detail; it is not a
 * precondition.
 *
 * Nothing here publishes or touches the network. The dashboard is the user looking at their own
 * numbers; sharing one is a separate, deliberate action from the workout list.
 */
@Stable
class MyFitnessViewModel : ViewModel() {
    /** Whether the richer Health Connect source is switched on, and whether it even could be. */
    enum class HealthConnectStatus {
        /** Granted and contributing to the log. */
        CONNECTED,

        /** A provider is installed but Amethyst has no permissions — worth offering. */
        AVAILABLE,

        /** No provider on this device; there is nothing to offer. */
        UNAVAILABLE,
    }

    @Immutable
    sealed interface State {
        /** First load, before Health Connect has been checked. */
        data object Loading : State

        /**
         * Summarised. [WorkoutStats.Report.isEmpty] covers "nothing logged yet", which is a
         * normal state rather than an error — a new user has published nothing and may not have
         * connected Health Connect.
         */
        data class Ready(
            val report: WorkoutStats.Report,
            val healthConnect: HealthConnectStatus,
        ) : State
    }

    private val pubkeyHex = MutableStateFlow<String?>(null)

    /**
     * Health Connect's contribution. A push source: the platform has no change feed we can
     * observe, so [refresh] re-reads it when the screen resumes or a permission is granted.
     */
    private val fromHealthConnect = MutableStateFlow<List<DetectedWorkout>>(emptyList())

    /** Null until the first [refresh] resolves, which is what keeps the screen on [State.Loading]. */
    private val healthConnectStatus = MutableStateFlow<HealthConnectStatus?>(null)

    private var manager: HealthConnectManager? = null

    /**
     * The user's published workouts, live. Re-subscribes on an account switch; a workout arriving
     * from a relay — or the one the user just posted — lands here without a refresh.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val fromRelays: Flow<List<DetectedWorkout>> =
        pubkeyHex.flatMapLatest { me ->
            if (me == null) flowOf(emptyList()) else publishedWorkoutsOf(me)
        }

    val state: StateFlow<State> =
        combine(fromHealthConnect, fromRelays, healthConnectStatus) { healthConnect, published, status ->
            if (status == null) {
                State.Loading
            } else {
                val now = Instant.now()
                val since = now.minus(Duration.ofDays(WorkoutStats.WINDOW_DAYS)).epochSecond

                State.Ready(
                    report =
                        WorkoutStats.report(
                            TrainingLog.merge(healthConnect, published.filter { it.startTimeEpochSeconds >= since }),
                            now,
                        ),
                    healthConnect = status,
                )
            }
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State.Loading)

    /** The account whose workouts this dashboard summarises. */
    fun init(pubkeyHex: String) {
        this.pubkeyHex.value = pubkeyHex
    }

    /**
     * Re-reads Health Connect. Safe to call on every resume: it re-checks permissions as well as
     * data, so revoking access drops those workouts out of the log rather than leaving stale
     * numbers on display. The published side needs no refresh — it is observed.
     */
    fun refresh(context: Context) {
        viewModelScope.launch {
            val status = healthConnectStatus(context)

            fromHealthConnect.value =
                if (status == HealthConnectStatus.CONNECTED) {
                    val now = Instant.now()
                    manager?.readWorkouts(now.minus(Duration.ofDays(WorkoutStats.WINDOW_DAYS)), now).orEmpty()
                } else {
                    emptyList()
                }

            healthConnectStatus.value = status
        }
    }

    private suspend fun healthConnectStatus(context: Context): HealthConnectStatus {
        if (!HealthConnectManager.isAvailable(context)) return HealthConnectStatus.UNAVAILABLE

        val hc = manager ?: HealthConnectManager(context.applicationContext).also { manager = it }
        return if (hc.hasAllPermissions()) HealthConnectStatus.CONNECTED else HealthConnectStatus.AVAILABLE
    }
}
