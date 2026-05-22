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
package com.vitorpamplona.amethyst.ui.note

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNote
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.note.elements.BannerImage
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.SimpleImageBorder

@Composable
fun DisplayAuthorBanner(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    modifier: Modifier = SimpleImageBorder,
) {
    val noteAuthor = baseNote.author
    if (noteAuthor != null) {
        BannerImage(
            noteAuthor,
            modifier,
            accountViewModel,
        )
    } else {
        val authorState by observeNote(baseNote, accountViewModel)
        CrossfadeIfEnabled(authorState.note.author, accountViewModel = accountViewModel) { author ->
            if (author != null) {
                BannerImage(
                    author,
                    modifier,
                    accountViewModel,
                )
            } else {
                BannerImage(
                    null as String?,
                    modifier,
                    accountViewModel,
                )
            }
        }
    }
}
