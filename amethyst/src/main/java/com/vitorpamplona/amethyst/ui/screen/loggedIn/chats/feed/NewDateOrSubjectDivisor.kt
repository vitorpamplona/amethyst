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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.note.dateFormatter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.layouts.ChatDivisor
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip14Subject.subject

@Composable
fun NewDateOrSubjectDivisor(
    previous: Note?,
    note: Note,
) {
    if (previous == null) return

    val never = stringRes(R.string.never)
    val today = stringRes(R.string.today)

    val prevDate = remember(previous) { dateFormatter(previous.event?.createdAt, never, today) }
    val date = remember(note) { dateFormatter(note.event?.createdAt, never, today) }

    val subject = remember(note) { note.event?.subject() }

    if (prevDate != date) {
        if (subject != null) {
            ChatDivisor("$date - $subject")
        } else {
            ChatDivisor(date)
        }
    } else {
        if (subject != null) {
            ChatDivisor(subject)
        }
    }
}
