/**
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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.quartz.nip01Core.core.EmptyTagList
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip56Reports.ReportType

@Composable
fun RenderReport(
    note: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? ReportEvent ?: return

    val base = remember { (noteEvent.reportedPost() + noteEvent.reportedAuthor()) }

    val reportType =
        base
            .map {
                when (it.type) {
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
            }.toSet()
            .joinToString(", ")

    val content =
        remember {
            reportType + (
                note.event
                    ?.content
                    ?.ifBlank { null }
                    ?.let { ": $it" } ?: ""
            )
        }

    TranslatableRichTextViewer(
        content = content,
        canPreview = true,
        modifier = Modifier,
        tags = EmptyTagList,
        backgroundColor = backgroundColor,
        id = note.idHex,
        callbackUri = note.toNostrUri(),
        quotesLeft = 1,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    note.replyTo?.lastOrNull()?.let {
        NoteCompose(
            baseNote = it,
            modifier = MaterialTheme.colorScheme.replyModifier,
            isQuotedNote = true,
            unPackReply = false,
            makeItShort = true,
            quotesLeft = quotesLeft - 1,
            parentBackgroundColor = backgroundColor,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}
