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
package com.vitorpamplona.quartz.experimental.fitness.workout

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseType
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * NIP-101e (draft) workout record, interoperable with the RUNSTR dialect.
 *
 * Parsing is intentionally lax: every tag is optional, units default to
 * metric (km/m) and pounds, and durations accept `HH:MM:SS` or raw seconds.
 * The content is plain text (user notes), never JSON.
 */
@Immutable
class WorkoutRecordEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    RootScope,
    SearchableEvent {
    override fun indexableContent() = listOfNotNull(title(), content).joinToString("\n")

    fun title() = tags.title()

    fun exercise() = tags.exercise()

    fun exerciseType() = tags.exerciseType()

    fun durationSeconds() = tags.durationSeconds()

    fun distance() = tags.distance()

    fun elevationGain() = tags.elevationGain()

    fun elevationLoss() = tags.elevationLoss()

    fun calories() = tags.calories()

    fun steps() = tags.steps()

    fun avgHeartRate() = tags.avgHeartRate()

    fun maxHeartRate() = tags.maxHeartRate()

    fun splits() = tags.splits()

    fun sets() = tags.sets()

    fun reps() = tags.reps()

    fun weight() = tags.weight()

    fun workoutSource() = tags.workoutSource()

    fun workoutStartTime() = tags.workoutStartTime()

    companion object {
        const val KIND = 1301
        const val ALT_DESCRIPTION = "Workout record"

        @OptIn(ExperimentalUuidApi::class)
        fun build(
            exercise: ExerciseType,
            durationSeconds: Long,
            notes: String = "",
            title: String? = null,
            workoutId: String = Uuid.random().toString(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<WorkoutRecordEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, notes, createdAt) {
            alt(ALT_DESCRIPTION)
            dTag(workoutId)
            exercise(exercise)
            hashtag(exercise.hashtag)
            duration(durationSeconds)
            title?.let { title(it) }
            initializer()
        }
    }
}
