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

import com.vitorpamplona.quartz.experimental.fitness.workout.tags.AvgHeartRateTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.CaloriesTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.DifficultyTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.DistanceTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.DurationTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ElevationGainTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ElevationLossTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseSetTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseType
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.IntervalTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.MaxHeartRateTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.RepsTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.RestBetweenRoundsTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.RestBetweenSetsTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.RoundsTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.SetsTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.SourceTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.SplitTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.StepsTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.TemplateTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.TitleTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.WeightTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.WorkoutCompletedTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.WorkoutEndTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.WorkoutStartTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.WorkoutStartTimeTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.WorkoutTypeTag
import com.vitorpamplona.quartz.nip01Core.core.AddressSerializer
import com.vitorpamplona.quartz.nip01Core.core.TagArray

fun TagArray.title() = firstNotNullOfOrNull(TitleTag::parse)

fun TagArray.exercise() = firstNotNullOfOrNull(ExerciseTag::parse)

fun TagArray.exerciseType() = firstNotNullOfOrNull(ExerciseTag::parseType)

fun TagArray.durationSeconds() = firstNotNullOfOrNull(DurationTag::parse)

fun TagArray.distance() = firstNotNullOfOrNull(DistanceTag::parse)

fun TagArray.elevationGain() = firstNotNullOfOrNull(ElevationGainTag::parse)

fun TagArray.elevationLoss() = firstNotNullOfOrNull(ElevationLossTag::parse)

fun TagArray.calories() = firstNotNullOfOrNull(CaloriesTag::parse)

fun TagArray.steps() = firstNotNullOfOrNull(StepsTag::parse)

fun TagArray.avgHeartRate() = firstNotNullOfOrNull(AvgHeartRateTag::parse)

fun TagArray.maxHeartRate() = firstNotNullOfOrNull(MaxHeartRateTag::parse)

fun TagArray.splits() = mapNotNull(SplitTag::parse)

fun TagArray.sets() = firstNotNullOfOrNull(SetsTag::parse)

fun TagArray.reps() = firstNotNullOfOrNull(RepsTag::parse)

fun TagArray.weight() = firstNotNullOfOrNull(WeightTag::parse)

fun TagArray.workoutSource() = firstNotNullOfOrNull(SourceTag::parse)

fun TagArray.workoutStartTime() = firstNotNullOfOrNull(WorkoutStartTimeTag::parse)

// --- POWR / NIP-101e strength dialect ---

fun TagArray.workoutTypeCode() = firstNotNullOfOrNull(WorkoutTypeTag::parse)

fun TagArray.workoutType() = workoutTypeCode()?.let(ExerciseType::parse)

fun TagArray.workoutStart() = firstNotNullOfOrNull(WorkoutStartTag::parse)

fun TagArray.workoutEnd() = firstNotNullOfOrNull(WorkoutEndTag::parse)

fun TagArray.workoutCompleted() = firstNotNullOfOrNull(WorkoutCompletedTag::parse)

fun TagArray.exerciseSets() = mapNotNull(ExerciseSetTag::parse)

fun TagArray.exerciseSetAddressIds() = mapNotNull(ExerciseSetTag::parseAddressId)

fun TagArray.exerciseSetHints() = mapNotNull(ExerciseSetTag::parseAsHint)

/**
 * The distinct exercise-template coordinates the `exercise` tags point at, each re-parsed as a
 * NIP-01 address so only well-formed `kind:<64-hex>:d` values come out (a prescription repeats one
 * coordinate per set, and some clients write their own non-nostr ids in the slot).
 *
 * The raw values are de-duplicated before anything is parsed, and only address-shaped ones (not
 * Workstr's `workstr:exercise:` ids) reach the parser.
 */
fun TagArray.exerciseTemplateAddresses() =
    mapNotNullTo(LinkedHashSet()) { ExerciseSetTag.parseAddressId(it)?.takeIf(AddressSerializer::isAddressShape) }
        .mapNotNull(AddressSerializer::parse)
        .distinct()

// --- NIP-101e workout template (kind 33402) ---

fun TagArray.rounds() = firstNotNullOfOrNull(RoundsTag::parse)

fun TagArray.intervalSeconds() = firstNotNullOfOrNull(IntervalTag::parse)

fun TagArray.restBetweenRoundsSeconds() = firstNotNullOfOrNull(RestBetweenRoundsTag::parse)

fun TagArray.restBetweenSetsSeconds() = firstNotNullOfOrNull(RestBetweenSetsTag::parse)

fun TagArray.difficulty() = firstNotNullOfOrNull(DifficultyTag::parse)

fun TagArray.templateAddressId() = firstNotNullOfOrNull(TemplateTag::parseAddressId)

fun TagArray.templateHint() = firstNotNullOfOrNull(TemplateTag::parseAsHint)

/** The client name from a `["client", name, ...]` tag (RUNSTR and POWR both emit this). */
fun TagArray.clientName() =
    firstNotNullOfOrNull { tag ->
        if (tag.size > 1 && tag[0] == "client" && tag[1].isNotEmpty()) tag[1] else null
    }
