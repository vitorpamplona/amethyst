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
package com.vitorpamplona.quartz.experimental.fitness

import com.vitorpamplona.quartz.experimental.fitness.workout.ExerciseTemplateEvent
import com.vitorpamplona.quartz.experimental.fitness.workout.WorkoutTemplateEvent
import com.vitorpamplona.quartz.experimental.fitness.workout.difficulty
import com.vitorpamplona.quartz.experimental.fitness.workout.duration
import com.vitorpamplona.quartz.experimental.fitness.workout.hashtag
import com.vitorpamplona.quartz.experimental.fitness.workout.interval
import com.vitorpamplona.quartz.experimental.fitness.workout.restBetweenRounds
import com.vitorpamplona.quartz.experimental.fitness.workout.rounds
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseSetTag
import com.vitorpamplona.quartz.nip01Core.core.AddressSerializer
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutTemplateEventTest {
    private val powr = "0bdd91e8a30d87d041eafd1871f17d426fa415c69a9a822eccad49017bac59e7"

    private fun set(
        d: String,
        number: String,
    ) = arrayOf("exercise", "33401:$powr:$d", "wss://relay.powr.build", "", "", "", "normal", number)

    // A real POWR template (kind 33402, 2026-10 relay census), trimmed to its first exercises.
    private fun sample(): Event =
        EventFactory.create(
            id = "d33aa097e4c78e0650f8ec57705c7095f634c35e0a6748644e2c6bf577911b87",
            pubKey = "e29cab1e05a29f00d25bc145a8f070f0138d50da5413212a835b7cd5dfcf8b50",
            createdAt = 1_791_331_734L,
            kind = WorkoutTemplateEvent.KIND,
            tags =
                arrayOf(
                    arrayOf("d", "bar-zero---push"),
                    arrayOf("title", "Bar Zero - Push"),
                    arrayOf("type", "strength"),
                    set("tricep-dips", "1"),
                    set("tricep-dips", "2"),
                    set("press", "1"),
                    set("press", "2"),
                    set("bench-press", "1"),
                    arrayOf("rest_between_sets", "90"),
                    arrayOf("difficulty", "intermediate"),
                    arrayOf("alt", "Workout template: Bar Zero - Push"),
                    arrayOf("client", "POWR"),
                ),
            content = "",
            sig = "3a189f61475861da6aa2b6b72b8e3578aa00fb75bdec670b840428bd9f2939693ec5c7b96013d9bb7128c3614f141b86feae1fcad5d88af91fe84480b6def03c",
        )

    private fun <T : Event> EventTemplate<T>.toEvent(): T = EventFactory.create("2".repeat(64), "f".repeat(64), createdAt, kind, tags, content, "")

    @Test
    fun factoryBuildsTemplateForKind33402() {
        assertIs<WorkoutTemplateEvent>(sample())
        assertTrue(EventFactory.isKnownKind(WorkoutTemplateEvent.KIND))
    }

    @Test
    fun parsesPowrTemplate() {
        val event = assertIs<WorkoutTemplateEvent>(sample())

        assertEquals("bar-zero---push", event.dTag())
        assertEquals("Bar Zero - Push", event.title())
        assertEquals("strength", event.workoutTypeCode())
        assertEquals(90, event.restBetweenSetsSeconds())
        assertEquals("intermediate", event.difficulty())
        assertEquals("POWR", event.client())
        assertNull(event.rounds())
        assertNull(event.durationSeconds())

        val sets = event.exerciseSets()
        assertEquals(5, sets.size)
        assertEquals("tricep-dips", sets[0].dTag())
        // A template leaves weight, reps and rpe for the user to fill in.
        assertNull(sets[0].weightKg)
        assertNull(sets[0].reps)
        assertEquals("normal", sets[0].setType)
        assertEquals(2, sets[1].setNumber)
        assertEquals(listOf("tricep-dips", "press", "bench-press"), event.exerciseGroups().map { it.sets.first().dTag() })
    }

    @Test
    fun linksEachExerciseTemplateOnce() {
        val event = assertIs<WorkoutTemplateEvent>(sample())

        val expected = listOf("tricep-dips", "press", "bench-press").map { "${ExerciseTemplateEvent.KIND}:$powr:$it" }
        assertEquals(expected, event.linkedAddressIds())
        event.linkedAddressIds().forEach { assertEquals(it, AddressSerializer.parse(it)?.toValue()) }
        assertEquals(5, event.addressHints().size)
        assertEquals(
            "wss://relay.powr.build/",
            event
                .addressHints()
                .first()
                .relay.url,
        )
        assertTrue(event is RootScope)
    }

    @Test
    fun timingTagsFromTheSpecExample() {
        val event =
            assertIs<WorkoutTemplateEvent>(
                EventFactory.create<Event>(
                    "1".repeat(64),
                    powr,
                    1L,
                    WorkoutTemplateEvent.KIND,
                    arrayOf(
                        arrayOf("d", "emom"),
                        arrayOf("title", "20min Squat/Deadlift EMOM"),
                        arrayOf("type", "emom"),
                        arrayOf("duration", "1200"),
                        arrayOf("rounds", "20"),
                        arrayOf("interval", "30"),
                        arrayOf("rest_between_rounds", "15"),
                        arrayOf("exercise", "33401:$powr:squat", "", "", "5", "7", "normal"),
                        arrayOf("t", "conditioning"),
                    ),
                    "20 minute EMOM alternating between squats and deadlifts.",
                    "",
                ),
            )
        assertEquals(1200L, event.durationSeconds())
        assertEquals(20, event.rounds())
        assertEquals(30, event.intervalSeconds())
        assertEquals(15, event.restBetweenRoundsSeconds())
        assertEquals(5, event.exerciseSets().single().reps)
        assertEquals(7.0, event.exerciseSets().single().rpe)
        assertEquals(listOf("conditioning"), event.topics())
        // Empty relay slot: a link, but no hint.
        assertEquals(listOf("33401:$powr:squat"), event.linkedAddressIds())
        assertEquals(emptyList(), event.addressHints())
    }

    @Test
    fun malformedAndForeignTagsAreSkipped() {
        val event =
            assertIs<WorkoutTemplateEvent>(
                EventFactory.create<Event>(
                    "1".repeat(64),
                    powr,
                    1L,
                    WorkoutTemplateEvent.KIND,
                    arrayOf(
                        // Workstr's own ids are not nostr coordinates.
                        arrayOf("exercise", "workstr:exercise:burpees", "Burpees", "", "8-12", "60", "normal"),
                        // 64 characters, but not hex: no edge.
                        arrayOf("exercise", "33401:${"z".repeat(64)}:x", "wss://relay.example.com"),
                        arrayOf("exercise"),
                        arrayOf("rounds", "-1"),
                        arrayOf("interval", "abc"),
                        arrayOf("rest_between_rounds"),
                        arrayOf("title"),
                    ),
                    "",
                    "",
                ),
            )
        assertEquals(emptyList(), event.linkedAddressIds())
        assertEquals(emptyList(), event.addressHints())
        assertNull(event.rounds())
        assertNull(event.intervalSeconds())
        assertNull(event.restBetweenRoundsSeconds())
        assertNull(event.title())
        assertEquals("", event.indexableContent())
    }

    @Test
    fun indexesTitleAndNotesAndTheVisitorAgrees() {
        val event =
            WorkoutTemplateEvent
                .build("Leg Day", "strength", emptyList(), notes = "Squat heavy, then accessories.")
                .toEvent()
        assertEquals("Leg Day\nSquat heavy, then accessories.", event.indexableContent())

        val visited = mutableListOf<String>()
        event.forEachIndexableField { field ->
            field?.let { visited.add(it) }
            true
        }
        assertEquals(event.indexableContent(), visited.joinToString(event.indexableSeparator()))
    }

    @Test
    fun buildRoundTrips() {
        val squat = ExerciseSetTag("33401:$powr:squat", "wss://relay.powr.build", 80.0, 5, 7.5, "normal", 1)
        val bodyweight = ExerciseSetTag("33401:$powr:pushups", null, null, 15, null, "warmup", null)
        val event =
            WorkoutTemplateEvent
                .build("Full Body", "circuit", listOf(squat, bodyweight), notes = "Two stations.", dTag = "full-body", createdAt = 1_700_000_000L) {
                    rounds(3)
                    duration(1800)
                    interval(45)
                    restBetweenRounds(90)
                    difficulty("beginner")
                    hashtag("legs")
                }.toEvent()

        assertEquals("full-body", event.dTag())
        assertEquals("Full Body", event.title())
        assertEquals("circuit", event.workoutTypeCode())
        assertEquals(3, event.rounds())
        assertEquals(1800L, event.durationSeconds())
        assertEquals(45, event.intervalSeconds())
        assertEquals(90, event.restBetweenRoundsSeconds())
        assertEquals("beginner", event.difficulty())
        assertEquals(listOf("legs"), event.topics())
        assertEquals("Two stations.", event.content)

        assertEquals(
            listOf("exercise", "33401:$powr:squat", "wss://relay.powr.build", "80", "5", "7.5", "normal", "1"),
            event.tags.first { it[0] == "exercise" }.toList(),
        )
        assertEquals(listOf("exercise", "33401:$powr:pushups", "", "", "15", "", "warmup"), event.tags.last { it[0] == "exercise" }.toList())

        val sets = event.exerciseSets()
        assertEquals(listOf(80.0, null), sets.map { it.weightKg })
        assertEquals(listOf(5, 15), sets.map { it.reps })
        assertEquals(listOf(7.5, null), sets.map { it.rpe })
        assertEquals(listOf("normal", "warmup"), sets.map { it.setType })
        assertEquals(listOf(1, null), sets.map { it.setNumber })
        assertEquals(listOf("33401:$powr:squat", "33401:$powr:pushups"), event.linkedAddressIds())
    }
}
