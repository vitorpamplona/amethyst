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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.utils

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.messages.ChatroomMessageCompose
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText

@Composable
fun DisplayReplyingToNote(
    replyingNote: Note?,
    accountViewModel: AccountViewModel,
    nav: INav,
    onCancel: () -> Unit,
) {
    Row(
        Modifier
            .padding(horizontal = 10.dp)
            .heightIn(max = 100.dp)
            .verticalScroll(rememberScrollState())
            .animateContentSize(),
    ) {
        if (replyingNote != null) {
            Column(remember { Modifier.weight(1f) }) {
                ChatroomMessageCompose(
                    baseNote = replyingNote,
                    null,
                    innerQuote = true,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    onWantsToReply = {},
                    onWantsToEditDraft = {},
                )
            }

            Column(Modifier.padding(start = 5.dp)) {
                IconButton(
                    modifier = Size20Modifier,
                    onClick = onCancel,
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        null,
                        modifier = Size20Modifier,
                        tint = MaterialTheme.colorScheme.placeholderText,
                    )
                }
            }
        }
    }
}
