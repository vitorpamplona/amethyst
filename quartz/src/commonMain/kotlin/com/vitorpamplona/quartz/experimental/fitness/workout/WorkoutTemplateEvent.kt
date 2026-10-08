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
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseSetTag
import com.vitorpamplona.quartz.nip01Core.core.AddressSerializer
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * NIP-101e (draft, nips#1816) workout template, kind 33402, as published by POWR: an addressable,
 * reusable workout plan. It prescribes a list of sets, each an `exercise` tag pointing at a kind
 * 33401 [ExerciseTemplateEvent] with the same `<coordinate>, <relay>, weight, reps, rpe, set_type`
 * layout a [WorkoutRecordEvent] logs. A template "MAY prescribe specific parameters while leaving
 * others as empty strings for user input", so every set field may be null here. A record points
 * back at the template it followed through its `template` tag.
 *
 * Parsing is lax, as for the other two fitness kinds: every tag is optional. Unrelated apps also
 * publish on kind 33402 (a geocaching checkpoint game, Workstr programs with non-nostr exercise ids);
 * they read as templates with no prescribed sets.
 *
 * Searchable by `title` and the `content` notes, both written by people. A NIP-22 root: people
 * comment on a shared workout plan as they do on a logged workout.
 */
@Immutable
class WorkoutTemplateEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressHintProvider,
    RootScope,
    SearchableEvent {
    override fun indexableContent() = listOfNotNull(title(), content).joinToString("\n")

    // The read path: the same fields indexableContent() joins, handed over without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(title())) return
        visitor.visit(content)
    }

    // Only hints whose coordinate is a well-formed address, the same filter linkedAddressIds() applies.
    override fun addressHints(): List<AddressHint> = tags.exerciseSetHints().filter { AddressSerializer.isAddressShape(it.addressId) }

    /**
     * `EXERCISE`: the kind 33401 exercise templates the plan prescribes, once each even when a
     * coordinate is repeated for every set. The set values (weight, reps, rpe, set type) are props of
     * that link, not edges.
     */
    override fun linkedAddressIds(): List<String> = tags.exerciseTemplateAddresses().map { it.toValue() }

    fun title() = tags.title()

    /** The raw `type` value: `strength`, `circuit`, `emom`, `amrap` or another workout format. */
    fun workoutTypeCode() = tags.workoutTypeCode()

    fun rounds() = tags.rounds()

    /** Total workout duration in seconds (raw seconds per NIP-101e; `HH:MM:SS` is accepted too). */
    fun durationSeconds() = tags.durationSeconds()

    fun intervalSeconds() = tags.intervalSeconds()

    fun restBetweenRoundsSeconds() = tags.restBetweenRoundsSeconds()

    fun restBetweenSetsSeconds() = tags.restBetweenSetsSeconds()

    fun difficulty() = tags.difficulty()

    /** Every prescribed set, in tag order. Only the coordinate form of `exercise` is read. */
    fun exerciseSets() = tags.exerciseSets()

    /** The prescribed sets grouped by exercise, in first-appearance order. */
    fun exerciseGroups() = groupExerciseSets(exerciseSets())

    fun topics() = hashtags()

    fun client() = tags.clientName()

    companion object {
        const val KIND = 33402
        const val ALT_DESCRIPTION = "Workout template"

        @OptIn(ExperimentalUuidApi::class)
        fun build(
            title: String,
            workoutType: String,
            exercises: List<ExerciseSetTag>,
            notes: String = "",
            dTag: String = Uuid.random().toString(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<WorkoutTemplateEvent>.() -> Unit = {},
        ) = eventTemplate<WorkoutTemplateEvent>(KIND, notes, createdAt) {
            dTag(dTag)
            title(title)
            workoutType(workoutType)
            exercises(exercises)
            initializer()
        }
    }
}
