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
package com.vitorpamplona.quartz.nip56Reports

import com.vitorpamplona.quartz.utils.Log

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
    HARASSMENT("harassment"),
    VIOLENCE("violence"),
    OTHER("other"),
    ;

    companion object {
        fun parseOrNull(
            code: String,
            tag: Array<String>,
        ): ReportType? =
            when (code) {
                "harassment" -> HARASSMENT
                "Harassment" -> HARASSMENT
                "Harrassment" -> HARASSMENT
                "impersonation" -> IMPERSONATION
                "Impersonation" -> IMPERSONATION
                "Falsificação de identidade \uD83E\uDD78" -> IMPERSONATION
                "Suplantación de identidad \uD83E\uDD78" -> IMPERSONATION
                "Impersonation \uD83E\uDD78" -> IMPERSONATION
                "Předstírání identity \uD83E\uDD78" -> IMPERSONATION
                "illegal" -> ILLEGAL
                "Illegal" -> ILLEGAL
                "Illegal Material" -> ILLEGAL
                "Illegal \uD83D\uDCD1" -> ILLEGAL
                "Ilegal \uD83D\uDCD1" -> ILLEGAL
                "Illégal \uD83D\uDCD1" -> ILLEGAL
                "csam" -> ILLEGAL
                "abuse" -> ILLEGAL
                "fraud" -> ILLEGAL
                "explicit" -> NUDITY
                "explıcıt" -> NUDITY
                "Explicit" -> NUDITY
                "nudity" -> NUDITY
                "Nudity" -> NUDITY
                "Nudity \uD83C\uDF51\uD83C\uDF46" -> NUDITY
                "sexual-content" -> NUDITY
                "inappropriate" -> NUDITY
                "Naaktheid \uD83C\uDF51\uD83C\uDF46" -> NUDITY
                "profanity" -> PROFANITY
                "Profanity" -> PROFANITY
                "Profanity \uD83D\uDDEF\uFE0F" -> PROFANITY
                "malware" -> MALWARE
                "Malware" -> MALWARE
                "other" -> OTHER
                "Other" -> OTHER
                "mod" -> OTHER
                "Mod" -> OTHER
                "MOD" -> OTHER
                "ai-generated" -> OTHER
                "duplicate" -> OTHER
                "" -> OTHER
                "spam" -> SPAM
                "Spam" -> SPAM
                "Spam \uD83D\uDCE3" -> SPAM
                "Bot Activity" -> SPAM
                "スパム \uD83D\uDCE3" -> SPAM
                "Pourriel \uD83D\uDCE3" -> SPAM
                "violence" -> VIOLENCE
                else -> Log.w("ReportedEventTag", "Report type not supported: `$code` ${tag.joinToString(", ")}").let { OTHER }
            }
    }
}
