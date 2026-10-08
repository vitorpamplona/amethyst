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

import com.vitorpamplona.quartz.experimental.fitness.workout.tags.DifficultyTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.DurationTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseSetTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.IntervalTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.RestBetweenRoundsTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.RestBetweenSetsTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.RoundsTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.TitleTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.WorkoutTypeTag
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag

// Kind 33402 builders. They live apart from the kind 1301 ones in TagArrayBuilderExt.kt because
// several share a name (title, hashtag) and both files would otherwise compile to one JVM class.

fun TagArrayBuilder<WorkoutTemplateEvent>.title(title: String) = addUnique(TitleTag.assemble(title))

fun TagArrayBuilder<WorkoutTemplateEvent>.workoutType(type: String) = addUnique(WorkoutTypeTag.assemble(type))

fun TagArrayBuilder<WorkoutTemplateEvent>.rounds(rounds: Int) = addUnique(RoundsTag.assemble(rounds))

/** NIP-101e: the total workout duration "in seconds", written as raw seconds (not `HH:MM:SS`). */
fun TagArrayBuilder<WorkoutTemplateEvent>.duration(seconds: Long) = addUnique(arrayOf(DurationTag.TAG_NAME, seconds.toString()))

fun TagArrayBuilder<WorkoutTemplateEvent>.interval(seconds: Int) = addUnique(IntervalTag.assemble(seconds))

fun TagArrayBuilder<WorkoutTemplateEvent>.restBetweenRounds(seconds: Int) = addUnique(RestBetweenRoundsTag.assemble(seconds))

fun TagArrayBuilder<WorkoutTemplateEvent>.restBetweenSets(seconds: Int) = addUnique(RestBetweenSetsTag.assemble(seconds))

fun TagArrayBuilder<WorkoutTemplateEvent>.difficulty(difficulty: String) = addUnique(DifficultyTag.assemble(difficulty))

/** One prescribed set; repeat the same exercise coordinate once per set, in order. */
fun TagArrayBuilder<WorkoutTemplateEvent>.exercise(set: ExerciseSetTag) = add(ExerciseSetTag.assemble(set))

fun TagArrayBuilder<WorkoutTemplateEvent>.exercises(sets: List<ExerciseSetTag>) = addAll(sets.map { ExerciseSetTag.assemble(it) })

fun TagArrayBuilder<WorkoutTemplateEvent>.hashtag(hashtag: String) = add(HashtagTag.assemble(hashtag))
