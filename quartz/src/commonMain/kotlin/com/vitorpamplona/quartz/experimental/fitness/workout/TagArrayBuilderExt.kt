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
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.DistanceTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.DurationTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.Elevation
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ElevationGainTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ElevationLossTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseType
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.MaxHeartRateTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.RepsTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.SetsTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.SourceTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.SplitTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.StepsTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.TitleTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.WeightTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.WorkoutStartTimeTag
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.tags.dTag.DTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag

fun TagArrayBuilder<WorkoutRecordEvent>.dTag(workoutId: String) = addUnique(DTag.assemble(workoutId))

fun TagArrayBuilder<WorkoutRecordEvent>.title(title: String) = addUnique(TitleTag.assemble(title))

fun TagArrayBuilder<WorkoutRecordEvent>.exercise(type: ExerciseType) = addUnique(ExerciseTag.assemble(type))

fun TagArrayBuilder<WorkoutRecordEvent>.exercise(code: String) = addUnique(ExerciseTag.assemble(code))

fun TagArrayBuilder<WorkoutRecordEvent>.duration(seconds: Long) = addUnique(DurationTag.assemble(seconds))

fun TagArrayBuilder<WorkoutRecordEvent>.source(source: String) = addUnique(SourceTag.assemble(source))

fun TagArrayBuilder<WorkoutRecordEvent>.distance(
    value: Double,
    unit: String = DistanceTag.KILOMETERS,
) = addUnique(DistanceTag.assemble(value, unit))

fun TagArrayBuilder<WorkoutRecordEvent>.elevationGain(
    value: Double,
    unit: String = Elevation.METERS,
) = addUnique(ElevationGainTag.assemble(value, unit))

fun TagArrayBuilder<WorkoutRecordEvent>.elevationLoss(
    value: Double,
    unit: String = Elevation.METERS,
) = addUnique(ElevationLossTag.assemble(value, unit))

fun TagArrayBuilder<WorkoutRecordEvent>.calories(kcal: Int) = addUnique(CaloriesTag.assemble(kcal))

fun TagArrayBuilder<WorkoutRecordEvent>.steps(steps: Int) = addUnique(StepsTag.assemble(steps))

fun TagArrayBuilder<WorkoutRecordEvent>.avgHeartRate(bpm: Int) = addUnique(AvgHeartRateTag.assemble(bpm))

fun TagArrayBuilder<WorkoutRecordEvent>.maxHeartRate(bpm: Int) = addUnique(MaxHeartRateTag.assemble(bpm))

fun TagArrayBuilder<WorkoutRecordEvent>.splits(splits: List<SplitTag>) = addAll(SplitTag.assemble(splits))

fun TagArrayBuilder<WorkoutRecordEvent>.sets(sets: Int) = addUnique(SetsTag.assemble(sets))

fun TagArrayBuilder<WorkoutRecordEvent>.reps(reps: Int) = addUnique(RepsTag.assemble(reps))

fun TagArrayBuilder<WorkoutRecordEvent>.weight(
    value: Double,
    unit: String = WeightTag.POUNDS,
) = addUnique(WeightTag.assemble(value, unit))

fun TagArrayBuilder<WorkoutRecordEvent>.workoutStartTime(timestamp: Long) = addUnique(WorkoutStartTimeTag.assemble(timestamp))

fun TagArrayBuilder<WorkoutRecordEvent>.hashtag(hashtag: String) = add(HashtagTag.assemble(hashtag))
