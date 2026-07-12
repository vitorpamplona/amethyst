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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.types

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.LoadDecryptedContentOrNull
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.jumboEmojiCount
import com.vitorpamplona.amethyst.ui.stringRes

// Jumbo sizes step down as the emoji count grows so up to three still fit a line.
private val JumboEmojiSingle = 50.sp
private val JumboEmojiPair = 40.sp
private val JumboEmojiTriple = 32.sp

@Composable
fun RenderRegularTextNote(
    note: Note,
    canPreview: Boolean,
    innerQuote: Boolean,
    bgColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadDecryptedContentOrNull(note = note, accountViewModel = accountViewModel) { eventContent ->
        if (eventContent != null) {
            SensitivityWarning(
                note = note,
                accountViewModel = accountViewModel,
            ) {
                val jumboCount = remember(eventContent) { jumboEmojiCount(eventContent) }

                if (jumboCount > 0) {
                    // Emoji-only messages render as jumbo emoji (the bubble behind
                    // them is transparent — see NormalChatNote).
                    Text(
                        text = eventContent.trim(),
                        fontSize =
                            when (jumboCount) {
                                1 -> JumboEmojiSingle
                                2 -> JumboEmojiPair
                                else -> JumboEmojiTriple
                            },
                    )
                } else {
                    val tags = remember(note.event) { note.event?.tags?.toImmutableListOfLists() ?: EmptyTagList }

                    TranslatableRichTextViewer(
                        content = eventContent,
                        canPreview = canPreview,
                        quotesLeft = if (innerQuote) 0 else 1,
                        modifier = Modifier,
                        tags = tags,
                        backgroundColor = bgColor,
                        id = note.idHex,
                        callbackUri = note.toNostrUri(),
                        authorPubKey = note.author?.pubkeyHex,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        } else {
            TranslatableRichTextViewer(
                content = stringRes(id = R.string.could_not_decrypt_the_message),
                canPreview = true,
                quotesLeft = 0,
                modifier = Modifier,
                tags = EmptyTagList,
                backgroundColor = bgColor,
                id = note.idHex,
                callbackUri = note.toNostrUri(),
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}
