/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.nip56Reports

import android.util.Log

enum class ReportType(
    val code: String,
) {
    EXPLICIT("explicit"), // Not used anymore.
    ILLEGAL("illegal"),
    SPAM("spam"),
    IMPERSONATION("impersonation"),
    NUDITY("nudity"),
    PROFANITY("profanity"),
    MALWARE("malware"),
    MOD("mod"),
    OTHER("other"),
    ;

    companion object {
        fun parseOrNull(
            code: String,
            tag: Array<String>,
        ): ReportType? =
            when (code) {
                EXPLICIT.code -> EXPLICIT
                ILLEGAL.code -> ILLEGAL
                SPAM.code -> SPAM
                IMPERSONATION.code -> IMPERSONATION
                NUDITY.code -> NUDITY
                PROFANITY.code -> PROFANITY
                MALWARE.code -> MALWARE
                MOD.code -> MOD
                "MOD" -> MOD
                OTHER.code -> OTHER
                else -> {
                    Log.w("ReportedEventTag", "Report type not supported: $code ${tag.joinToString(", ")}")
                    null
                }
            }
    }
}
