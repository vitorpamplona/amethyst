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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.replyModifier
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

    val reportTypes =
        remember(noteEvent) {
            (noteEvent.reportedPost() + noteEvent.reportedAuthor())
                .mapTo(LinkedHashSet()) { it.type }
        }

    val explicitContent = stringRes(R.string.explicit_content)
    val nudity = stringRes(R.string.nudity)
    val profanity = stringRes(R.string.profanity_hateful_speech)
    val spam = stringRes(R.string.spam)
    val impersonation = stringRes(R.string.impersonation)
    val illegal = stringRes(R.string.illegal_behavior)
    val malware = stringRes(R.string.malware)
    val other = stringRes(R.string.other)
    val harassment = stringRes(R.string.harassment)
    val violence = stringRes(R.string.violence)

    val content =
        remember(reportTypes, noteEvent) {
            val reportTypeText =
                reportTypes.joinToString(", ") {
                    when (it) {
                        ReportType.EXPLICIT -> explicitContent
                        ReportType.NUDITY -> nudity
                        ReportType.PROFANITY -> profanity
                        ReportType.SPAM -> spam
                        ReportType.IMPERSONATION -> impersonation
                        ReportType.ILLEGAL -> illegal
                        ReportType.MALWARE -> malware
                        ReportType.OTHER -> other
                        ReportType.HARASSMENT -> harassment
                        ReportType.VIOLENCE -> violence
                        null -> other
                    }
                }
            val extra = noteEvent.content.ifBlank { null }?.let { ": $it" } ?: ""
            reportTypeText + extra
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
            unPackReply = ReplyRenderType.NONE,
            makeItShort = true,
            quotesLeft = quotesLeft - 1,
            parentBackgroundColor = backgroundColor,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}
