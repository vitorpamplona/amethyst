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

/** CI conclusion values, aligned with the GitHub API `conclusion` field. */
enum class CiConclusion(
    val code: String,
) {
    /** Completed successfully. */
    SUCCESS("success"),

    /** Completed with a failing step or job. */
    FAILURE("failure"),

    /** Completed without a blocking outcome, e.g. a failing job allowed to fail. */
    NEUTRAL("neutral"),

    /** Cancelled before completion. */
    CANCELLED("cancelled"),

    /** Intentionally not run. */
    SKIPPED("skipped"),

    /** Exceeded the publisher's time limit. */
    TIMED_OUT("timed_out"),

    /** Failed before execution, e.g. clone or workflow parse failure. */
    STARTUP_FAILURE("startup_failure"),
    ;

    companion object {
        fun parse(code: String): CiConclusion? =
            when (code) {
                SUCCESS.code -> SUCCESS
                FAILURE.code -> FAILURE
                NEUTRAL.code -> NEUTRAL
                CANCELLED.code -> CANCELLED
                SKIPPED.code -> SKIPPED
                TIMED_OUT.code -> TIMED_OUT
                STARTUP_FAILURE.code -> STARTUP_FAILURE
                else -> null
            }
    }
}

/**
 * Nostr CI `conclusion` tag. An unknown value parses to null rather than being normalized, so a
 * future conclusion is never displayed as one of today's (ngit's rule).
 */
class ConclusionTag {
    companion object {
        const val TAG_NAME = "conclusion"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        fun parse(tag: Array<String>): CiConclusion? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            return CiConclusion.parse(tag[1])
        }

        fun assemble(conclusion: CiConclusion) = arrayOf(TAG_NAME, conclusion.code)
    }
}
