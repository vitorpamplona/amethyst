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

/**
 * The sets logged for one exercise within a POWR / NIP-101e workout, grouped by
 * the referenced exercise-template coordinate and ordered by set number.
 */
@Immutable
class ExerciseGroup(
    val reference: String,
    val sets: List<ExerciseSetTag>,
) {
    /** Best-effort human label derived from the template d-tag, e.g. `back-squat-bb` → "Back Squat Bb". */
    fun displayName(): String? = sets.firstNotNullOfOrNull { it.dTag() }?.let(::slugToTitle)

    /** Sum of every set's volume in kilograms, or null if no set has both weight and reps. */
    fun totalVolumeKg(): Double? {
        val volumes = sets.mapNotNull { it.volumeKg() }
        return if (volumes.isEmpty()) null else volumes.sum()
    }

    /** Heaviest weight logged across the group's sets, in kilograms. */
    fun topWeightKg(): Double? = sets.mapNotNull { it.weightKg }.filter { it > 0.0 }.maxOrNull()
}

/** Groups exercise sets by their template reference, preserving first-seen order and sorting by set number. */
fun groupExerciseSets(sets: List<ExerciseSetTag>): List<ExerciseGroup> {
    if (sets.isEmpty()) return emptyList()
    val byReference = LinkedHashMap<String, MutableList<ExerciseSetTag>>()
    sets.forEach { byReference.getOrPut(it.reference) { mutableListOf() }.add(it) }
    return byReference.map { (reference, groupSets) ->
        ExerciseGroup(reference, groupSets.sortedBy { it.setNumber ?: Int.MAX_VALUE })
    }
}

/** Turns a NIP-101e d-tag slug (`seated-calf-raise-machine`) into a title (`Seated Calf Raise Machine`). */
fun slugToTitle(slug: String): String =
    slug
        .split('-', '_', ' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }
