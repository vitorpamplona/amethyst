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
 * Reads a non-negative whole number from the second slot of a [tagName] tag. The NIP-101e timing
 * and count tags of a kind 33402 template all have this shape; a negative, fractional or
 * non-numeric value is malformed and reads as null.
 */
private fun parseCount(
    tag: Array<String>,
    tagName: String,
): Int? {
    ensure(tag.has(1)) { return null }
    ensure(tag[0] == tagName) { return null }
    val value = tag[1].trim().toIntOrNull() ?: return null
    ensure(value >= 0) { return null }
    return value
}

/** NIP-101e `rounds`: the number of rounds for repeating formats (circuit, EMOM, AMRAP). */
class RoundsTag {
    companion object {
        const val TAG_NAME = "rounds"

        fun parse(tag: Array<String>): Int? = parseCount(tag, TAG_NAME)

        fun assemble(rounds: Int) = arrayOf(TAG_NAME, rounds.toString())
    }
}

/** NIP-101e `interval`: the duration of each exercise portion in seconds, for timed workouts. */
class IntervalTag {
    companion object {
        const val TAG_NAME = "interval"

        fun parse(tag: Array<String>): Int? = parseCount(tag, TAG_NAME)

        fun assemble(seconds: Int) = arrayOf(TAG_NAME, seconds.toString())
    }
}

/** NIP-101e `rest_between_rounds`: rest between rounds, in seconds. */
class RestBetweenRoundsTag {
    companion object {
        const val TAG_NAME = "rest_between_rounds"

        fun parse(tag: Array<String>): Int? = parseCount(tag, TAG_NAME)

        fun assemble(seconds: Int) = arrayOf(TAG_NAME, seconds.toString())
    }
}

/**
 * `rest_between_sets`: rest between sets, in seconds. A POWR extension: not in the NIP-101e text,
 * but POWR writes it on every strength template.
 */
class RestBetweenSetsTag {
    companion object {
        const val TAG_NAME = "rest_between_sets"

        fun parse(tag: Array<String>): Int? = parseCount(tag, TAG_NAME)

        fun assemble(seconds: Int) = arrayOf(TAG_NAME, seconds.toString())
    }
}
