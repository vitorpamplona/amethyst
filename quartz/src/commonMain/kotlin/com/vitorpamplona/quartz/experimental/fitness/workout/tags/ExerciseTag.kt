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
package com.vitorpamplona.quartz.experimental.fitness.workout.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * Activity verbs used by NIP-101e clients (RUNSTR dialect). The tag value is the
 * lowercase [code]; the matching capitalized [hashtag] is published as a `t` tag
 * so the workout is discoverable.
 */
enum class ExerciseType(
    val code: String,
    val hashtag: String,
) {
    RUNNING("running", "Running"),
    WALKING("walking", "Walking"),
    CYCLING("cycling", "Cycling"),
    HIKING("hiking", "Hiking"),
    SWIMMING("swimming", "Swimming"),
    ROWING("rowing", "Rowing"),
    STRENGTH("strength", "Strength"),
    YOGA("yoga", "Yoga"),
    MEDITATION("meditation", "Meditation"),
    DIET("diet", "Diet"),
    FASTING("fasting", "Fasting"),
    ;

    companion object {
        fun parse(code: String) = entries.firstOrNull { it.code == code.lowercase() }
    }
}

class ExerciseTag {
    companion object {
        const val TAG_NAME = "exercise"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        /** Returns the raw verb. Other clients may publish verbs outside [ExerciseType]. */
        fun parse(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return tag[1]
        }

        fun parseType(tag: Array<String>) = parse(tag)?.let { ExerciseType.parse(it) }

        fun assemble(code: String) = arrayOf(TAG_NAME, code)

        fun assemble(type: ExerciseType) = assemble(type.code)
    }
}
