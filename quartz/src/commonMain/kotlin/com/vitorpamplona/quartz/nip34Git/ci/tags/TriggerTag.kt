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
package com.vitorpamplona.quartz.nip34Git.ci.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/** The normalized reason a CI attempt was run (`o` tag). */
enum class CiTrigger(
    val code: String,
) {
    PUSH("push"),
    PULL_REQUEST("pull_request"),
    SCHEDULE("schedule"),
    MANUAL("manual"),
    ;

    companion object {
        fun parse(code: String): CiTrigger? =
            when (code) {
                PUSH.code -> PUSH
                PULL_REQUEST.code -> PULL_REQUEST
                SCHEDULE.code -> SCHEDULE
                MANUAL.code -> MANUAL
                else -> null
            }
    }
}

/**
 * Nostr CI `o` tag. An unknown trigger parses to null rather than to one of today's values, so a
 * future trigger is never displayed as `push`. A Manual Trigger (9840) omits `o`: it is always
 * `manual`.
 */
class TriggerTag {
    companion object {
        const val TAG_NAME = "o"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        fun parse(tag: Array<String>): CiTrigger? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            return CiTrigger.parse(tag[1])
        }

        fun assemble(trigger: CiTrigger) = arrayOf(TAG_NAME, trigger.code)
    }
}
