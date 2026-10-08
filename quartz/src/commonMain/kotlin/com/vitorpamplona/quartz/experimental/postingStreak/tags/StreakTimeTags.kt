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
package com.vitorpamplona.quartz.experimental.postingStreak.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * Clients "SHOULD reject events where `start` or `end` isn't a non-negative integer": a value that
 * is not a plain run of decimal digits (a sign, a fraction, an empty string) is null.
 */
private fun parseNonNegativeSeconds(value: String): Long? {
    ensure(value.isNotEmpty() && value.all { it in '0'..'9' }) { return null }
    return value.toLongOrNull()
}

/** `["start", "<unix seconds>"]`: the first creative event of the streak. */
class StreakStartTag {
    companion object {
        const val TAG_NAME = "start"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME

        fun parse(tag: Array<String>): Long? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            return parseNonNegativeSeconds(tag[1])
        }

        fun assemble(timestamp: Long) = arrayOf(TAG_NAME, timestamp.toString())
    }
}

/** `["end", "<unix seconds>"]`: the latest creative event of the streak. */
class StreakEndTag {
    companion object {
        const val TAG_NAME = "end"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME

        fun parse(tag: Array<String>): Long? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            return parseNonNegativeSeconds(tag[1])
        }

        fun assemble(timestamp: Long) = arrayOf(TAG_NAME, timestamp.toString())
    }
}
