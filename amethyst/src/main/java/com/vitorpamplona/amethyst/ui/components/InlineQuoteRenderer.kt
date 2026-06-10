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
package com.vitorpamplona.amethyst.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.types.ReplyRenderType
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.innerPostModifier

/**
 * Strategy for rendering a note quoted inline in another note's text (a
 * `nostr:nevent1...`/`nostr:note1...` mention with preview budget left, or a
 * legacy `#[index]` event tag).
 *
 * Both rich-text paths (plain and markdown) funnel through [DisplayFullNote],
 * which reads [LocalInlineQuoteRenderer], so providing a different renderer
 * re-skins inline quotes for a whole subtree without threading a parameter
 * through the parser stack. Chat bubbles use this to keep a quoted chat
 * message in the chat reply design instead of the default quoted-note card
 * (see `chatInlineQuoteRenderer`).
 *
 * A renderer draws only the quoted note itself; any leftover characters
 * around the mention stay with the caller ([DisplayFullNote]).
 */
fun interface InlineQuoteRenderer {
    @Composable
    fun Render(
        note: Note,
        quotesLeft: Int,
        backgroundColor: MutableState<Color>,
        accountViewModel: AccountViewModel,
        nav: INav,
    )
}

/** The quoted-note card used everywhere outside chats. */
val DefaultInlineQuoteRenderer =
    InlineQuoteRenderer { note, quotesLeft, backgroundColor, accountViewModel, nav ->
        NoteCompose(
            baseNote = note,
            modifier = MaterialTheme.colorScheme.innerPostModifier,
            isQuotedNote = true,
            unPackReply = ReplyRenderType.LINE,
            quotesLeft = quotesLeft - 1,
            parentBackgroundColor = backgroundColor,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }

val LocalInlineQuoteRenderer = compositionLocalOf { DefaultInlineQuoteRenderer }
