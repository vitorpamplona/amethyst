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
import com.vitorpamplona.quartz.experimental.fitness.workout.WorkoutRecordEvent
import com.vitorpamplona.quartz.experimental.fitness.workout.calories
import com.vitorpamplona.quartz.experimental.fitness.workout.distance
import com.vitorpamplona.quartz.experimental.fitness.workout.slugToTitle
import com.vitorpamplona.quartz.experimental.fitness.workout.source
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.DurationTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseType
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutRecordEventTest {
    private fun parse(
        tags: Array<Array<String>>,
        content: String = "",
    ): Event =
        EventFactory.create(
            id = "a".repeat(64),
            pubKey = "b".repeat(64),
            createdAt = 1718000000L,
            kind = WorkoutRecordEvent.KIND,
            tags = tags,
            content = content,
            sig = "c".repeat(128),
        )

    /** Tag layout as published by RUNSTR (docs/KIND_1301_SPEC.md in RUNSTR-LLC/RUNSTR). */
    @Test
    fun parsesRunstrDialect() {
        val event =
            parse(
                arrayOf(
                    arrayOf("d", "57b08a45-2c2f-4b51-9b6f-21f3936b3ef1"),
                    arrayOf("title", "Morning Run"),
                    arrayOf("exercise", "running"),
                    arrayOf("distance", "5.20", "km"),
                    arrayOf("duration", "00:31:30"),
                    arrayOf("elevation_gain", "50", "m"),
                    arrayOf("elevation_loss", "48", "m"),
                    arrayOf("calories", "312"),
                    arrayOf("steps", "8421"),
                    arrayOf("source", "gps"),
                    arrayOf("client", "RUNSTR", "1.0.5"),
                    arrayOf("t", "Running"),
                    arrayOf("split", "1", "00:06:01"),
                    arrayOf("split", "2", "00:12:10"),
                ),
                content = "Felt great today!",
            )

        assertTrue(event is WorkoutRecordEvent)

        assertEquals("57b08a45-2c2f-4b51-9b6f-21f3936b3ef1", event.dTag())
        assertEquals("Morning Run", event.title())
        assertEquals("running", event.exercise())
        assertEquals(ExerciseType.RUNNING, event.exerciseType())
        assertEquals(31 * 60 + 30L, event.durationSeconds())
        assertEquals(5200.0, event.distance()?.toMeters())
        assertEquals(50.0, event.elevationGain()?.toMeters())
        assertEquals(48.0, event.elevationLoss()?.toMeters())
        assertEquals(312, event.calories())
        assertEquals(8421, event.steps())
        assertEquals("gps", event.workoutSource())
        assertEquals("Felt great today!", event.content)

        val splits = event.splits()
        assertEquals(2, splits.size)
        assertEquals(1, splits[0].number)
        assertEquals(6 * 60 + 1L, splits[0].cumulativeSeconds)
        assertEquals(12 * 60 + 10L, splits[1].cumulativeSeconds)
    }

    @Test
    fun parsesLaxUnitsAndRawSecondsLikeRunstr() {
        val event =
            parse(
                arrayOf(
                    arrayOf("exercise", "Running"),
                    arrayOf("distance", "3.1", "mi"),
                    arrayOf("duration", "1800"),
                    arrayOf("elevation_gain", "100", "ft"),
                ),
            ) as WorkoutRecordEvent

        assertEquals(ExerciseType.RUNNING, event.exerciseType())
        assertEquals(3.1 * 1609.344, event.distance()!!.toMeters())
        assertEquals(1800L, event.durationSeconds())
        assertEquals(100 * 0.3048, event.elevationGain()!!.toMeters())
        assertNull(event.title())
    }

    @Test
    fun parsesStrengthWorkout() {
        val event =
            parse(
                arrayOf(
                    arrayOf("exercise", "strength"),
                    arrayOf("duration", "00:45:00"),
                    arrayOf("sets", "5"),
                    arrayOf("reps", "10"),
                    arrayOf("weight", "165", "lbs"),
                ),
            ) as WorkoutRecordEvent

        assertEquals(ExerciseType.STRENGTH, event.exerciseType())
        assertEquals(5, event.sets())
        assertEquals(10, event.reps())
        assertEquals(165 * 0.45359237, event.weight()!!.toKilograms())
        assertNull(event.distance())
    }

    /** Tag layout as published by POWR (NIP-101e strength dialect: per-set `exercise` coordinates). */
    @Test
    fun parsesPowrDialect() {
        val coord = "33401:0bdd91e8a30d87d041eafd1871f17d426fa415c69a9a822eccad49017bac59e7:back-squat-bb"
        val relay = "wss://relay.powr.build"
        val event =
            parse(
                arrayOf(
                    arrayOf("title", "Novice Hypertrophy — Day 2"),
                    arrayOf("type", "strength"),
                    arrayOf("start", "1781969106"),
                    arrayOf("end", "1781972319"),
                    arrayOf("completed", "true"),
                    arrayOf("template", "33402:0bdd91e8a30d87d041eafd1871f17d426fa415c69a9a822eccad49017bac59e7:novice-hyp-day2-lower-a", relay),
                    arrayOf("exercise", coord, relay, "84", "8", "8", "normal", "1"),
                    arrayOf("exercise", coord, relay, "84", "8", "8", "normal", "2"),
                    arrayOf("exercise", coord, relay, "84", "8", "9", "normal", "3"),
                    arrayOf("exercise", "33401:0bdd91e8a30d87d041eafd1871f17d426fa415c69a9a822eccad49017bac59e7:seated-calf-raise-machine", relay, "", "8", "", "normal", "1"),
                    arrayOf("client", "POWR"),
                ),
            ) as WorkoutRecordEvent

        // The activity type now comes from the `type` tag, not the coordinate-form `exercise` tag.
        assertEquals(ExerciseType.STRENGTH, event.workoutType())
        assertEquals(ExerciseType.STRENGTH, event.activityType())
        assertNull(event.exercise()) // must not surface "33401:..." as a verb
        assertNull(event.exerciseType())

        assertEquals("Novice Hypertrophy — Day 2", event.title())
        assertEquals(1781969106L, event.workoutStart())
        assertEquals(1781972319L, event.workoutEnd())
        assertEquals(true, event.workoutCompleted())
        assertEquals("POWR", event.client())

        // No `duration` tag: derived from start/end.
        assertEquals(1781972319L - 1781969106L, event.effectiveDurationSeconds())

        val sets = event.exerciseSets()
        assertEquals(4, sets.size)
        assertEquals(84.0, sets[0].weightKg)
        assertEquals(8, sets[0].reps)
        assertEquals(8.0, sets[0].rpe)
        assertEquals("normal", sets[0].setType)
        assertEquals(1, sets[0].setNumber)
        assertEquals("back-squat-bb", sets[0].dTag())
        // Bodyweight / machine set leaves weight empty.
        assertNull(sets[3].weightKg)
        assertEquals(8, sets[3].reps)

        val groups = event.exerciseGroups()
        assertEquals(2, groups.size)
        assertEquals("Back Squat Bb", groups[0].displayName())
        assertEquals(3, groups[0].sets.size)
        assertEquals(84.0 * 8 * 3, groups[0].totalVolumeKg()) // 3 sets of 8 reps @ 84 kg (the 9 is RPE)
        assertEquals(84.0, groups[0].topWeightKg())
        assertEquals("Seated Calf Raise Machine", groups[1].displayName())
        assertNull(groups[1].totalVolumeKg()) // no weights logged
    }

    @Test
    fun exposesPowrTemplateReferencesAsHints() {
        val backSquat = "33401:0bdd91e8a30d87d041eafd1871f17d426fa415c69a9a822eccad49017bac59e7:back-squat-bb"
        val deadlift = "33401:0bdd91e8a30d87d041eafd1871f17d426fa415c69a9a822eccad49017bac59e7:deadlift-bb"
        val template = "33402:0bdd91e8a30d87d041eafd1871f17d426fa415c69a9a822eccad49017bac59e7:novice-hyp-day2-lower-a"
        val relay = "wss://relay.powr.build"
        val event =
            parse(
                arrayOf(
                    arrayOf("type", "strength"),
                    arrayOf("template", template, relay),
                    arrayOf("exercise", backSquat, relay, "84", "8", "8", "normal", "1"),
                    arrayOf("exercise", backSquat, relay, "84", "8", "8", "normal", "2"),
                    arrayOf("exercise", deadlift, relay, "100", "5", "8", "normal", "1"),
                ),
            ) as WorkoutRecordEvent

        // Each referenced template is exposed once for fetching (deduped), incl. the 33402 template.
        val linked = event.linkedAddressIds()
        assertEquals(setOf(backSquat, deadlift, template), linked.toSet())
        assertEquals(3, linked.size)

        // Relay hints let Amethyst fetch the templates from where POWR published them.
        val hints = event.addressHints()
        assertEquals(setOf(backSquat, deadlift, template), hints.map { it.addressId }.toSet())
        assertTrue(hints.all { it.relay.url == "wss://relay.powr.build/" })
    }

    @Test
    fun parsesExerciseTemplate() {
        val event: Event =
            EventFactory.create(
                id = "a".repeat(64),
                pubKey = "b".repeat(64),
                createdAt = 1718000000L,
                kind = ExerciseTemplateEvent.KIND,
                tags =
                    arrayOf(
                        arrayOf("d", "back-squat-bb"),
                        arrayOf("title", "Back Squat (Barbell)"),
                        arrayOf("format", "weight", "reps", "rpe", "set_type"),
                        arrayOf("format_units", "kg", "count", "0-10", "enum"),
                        arrayOf("equipment", "barbell"),
                        arrayOf("difficulty", "intermediate"),
                    ),
                content = "Keep a neutral spine.",
                sig = "c".repeat(128),
            )

        assertTrue(event is ExerciseTemplateEvent)
        assertEquals("back-squat-bb", event.dTag())
        assertEquals("Back Squat (Barbell)", event.title())
        assertEquals(listOf("weight", "reps", "rpe", "set_type"), event.format())
        assertEquals(listOf("kg", "count", "0-10", "enum"), event.formatUnits())
        assertEquals("barbell", event.equipment())
        assertEquals("intermediate", event.difficulty())
    }

    @Test
    fun slugToTitlePrettifiesDTags() {
        assertEquals("Back Squat Bb", slugToTitle("back-squat-bb"))
        assertEquals("Seated Calf Raise Machine", slugToTitle("seated-calf-raise-machine"))
        assertEquals("Deadlift", slugToTitle("deadlift"))
    }

    @Test
    fun durationFormatsAsPaddedTime() {
        assertEquals("00:31:30", DurationTag.formatTime(31 * 60 + 30L))
        assertEquals("01:00:05", DurationTag.formatTime(3605L))
        assertEquals(3605L, DurationTag.parseTime("01:00:05"))
        assertEquals(125L, DurationTag.parseTime("02:05"))
        assertNull(DurationTag.parseTime("not-a-time"))
    }

    @Test
    fun buildEmitsCanonicalTags() {
        val template =
            WorkoutRecordEvent.build(
                exercise = ExerciseType.RUNNING,
                durationSeconds = 31 * 60 + 30L,
                notes = "Easy pace",
                title = "Morning Run",
                workoutId = "fixed-id",
            ) {
                distance(5.2)
                calories(312)
                source("manual")
            }

        val tags = template.tags

        assertEquals(arrayOf("d", "fixed-id").toList(), tags.first { it[0] == "d" }.toList())
        assertEquals(arrayOf("exercise", "running").toList(), tags.first { it[0] == "exercise" }.toList())
        assertEquals(arrayOf("t", "Running").toList(), tags.first { it[0] == "t" }.toList())
        assertEquals(arrayOf("duration", "00:31:30").toList(), tags.first { it[0] == "duration" }.toList())
        assertEquals(arrayOf("title", "Morning Run").toList(), tags.first { it[0] == "title" }.toList())
        assertEquals(arrayOf("distance", "5.2", "km").toList(), tags.first { it[0] == "distance" }.toList())
        assertEquals(arrayOf("calories", "312").toList(), tags.first { it[0] == "calories" }.toList())
        assertEquals(arrayOf("source", "manual").toList(), tags.first { it[0] == "source" }.toList())
        assertEquals("Easy pace", template.content)
    }
}
