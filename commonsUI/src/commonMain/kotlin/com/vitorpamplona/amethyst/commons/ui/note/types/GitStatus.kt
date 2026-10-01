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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.nip34Git.ui.statusKind
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.GitStatusPill
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip34Git.status.GitStatusEvent

/**
 * Renders a NIP-34 status event (kinds 1630-1633). These usually carry an
 * empty `content`, so the text fallback drew a blank note: show the new
 * status as a pill, the optional comment, and the issue/patch/PR it
 * targets (linked through the event's marked-`root` `e` tag) quoted below.
 */
@Composable
fun RenderGitStatusEvent(
    note: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = note.event as? GitStatusEvent ?: return
    val kind = remember(event) { event.statusKind() }

    GitStatusPill(kind)

    if (event.content.isNotBlank()) {
        Spacer(modifier = StdVertSpacer)
        Text(
            text = event.content,
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    if (note.replyTo?.lastOrNull() != null) {
        Spacer(modifier = StdVertSpacer)
        RenderTargetNote(note, quotesLeft, backgroundColor, accountViewModel, nav)
    }
}
