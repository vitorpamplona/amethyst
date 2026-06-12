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

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.experimental.fitness.workout.WorkoutRecordEvent
import com.vitorpamplona.quartz.experimental.fitness.workout.calories
import com.vitorpamplona.quartz.experimental.fitness.workout.distance
import com.vitorpamplona.quartz.experimental.fitness.workout.source
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.DistanceTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseType
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.SourceTag
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate

@Stable
class NewWorkoutViewModel : ViewModel() {
    lateinit var accountViewModel: AccountViewModel
    lateinit var account: Account

    var exercise by mutableStateOf(ExerciseType.RUNNING)
    var title by mutableStateOf("")
    var hours by mutableStateOf("")
    var minutes by mutableStateOf("")
    var seconds by mutableStateOf("")
    var distance by mutableStateOf("")
    var distanceUnit by mutableStateOf(DistanceTag.KILOMETERS)
    var calories by mutableStateOf("")
    var notes by mutableStateOf("")

    fun init(accountVM: AccountViewModel) {
        this.accountViewModel = accountVM
        this.account = accountVM.account
    }

    fun durationSeconds(): Long =
        (hours.toLongOrNull() ?: 0L) * 3600 +
            (minutes.toLongOrNull() ?: 0L) * 60 +
            (seconds.toLongOrNull() ?: 0L)

    fun canPost(): Boolean = durationSeconds() > 0

    fun cancel() {
        exercise = ExerciseType.RUNNING
        title = ""
        hours = ""
        minutes = ""
        seconds = ""
        distance = ""
        distanceUnit = DistanceTag.KILOMETERS
        calories = ""
        notes = ""
    }

    suspend fun sendPostSync() {
        val template = createTemplate() ?: return
        cancel()
        account.signAndComputeBroadcast(template)
    }

    private fun createTemplate(): EventTemplate<WorkoutRecordEvent>? {
        val durationSecs = durationSeconds()
        if (durationSecs <= 0) return null

        val distanceValue = distance.toDoubleOrNull()
        val kcal = calories.toIntOrNull()

        return WorkoutRecordEvent.build(
            exercise = exercise,
            durationSeconds = durationSecs,
            notes = notes,
            title = title.ifBlank { null },
        ) {
            source(SourceTag.MANUAL)
            distanceValue?.let { distance(it, distanceUnit) }
            kcal?.let { calories(it) }
        }
    }
}
