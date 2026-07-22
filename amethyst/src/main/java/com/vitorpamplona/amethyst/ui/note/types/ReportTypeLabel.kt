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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.runtime.Composable
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip56Reports.ReportType

/** The localized name of a NIP-56 report reason. An unrecognized reason reads as "Other". */
@Composable
fun reportTypeLabel(type: ReportType?): String =
    when (type) {
        ReportType.EXPLICIT -> stringRes(R.string.explicit_content)
        ReportType.NUDITY -> stringRes(R.string.nudity)
        ReportType.PROFANITY -> stringRes(R.string.profanity_hateful_speech)
        ReportType.SPAM -> stringRes(R.string.spam)
        ReportType.IMPERSONATION -> stringRes(R.string.impersonation)
        ReportType.ILLEGAL -> stringRes(R.string.illegal_behavior)
        ReportType.MALWARE -> stringRes(R.string.malware)
        ReportType.OTHER -> stringRes(R.string.other)
        ReportType.HARASSMENT -> stringRes(R.string.harassment)
        ReportType.VIOLENCE -> stringRes(R.string.violence)
        null -> stringRes(R.string.other)
    }
