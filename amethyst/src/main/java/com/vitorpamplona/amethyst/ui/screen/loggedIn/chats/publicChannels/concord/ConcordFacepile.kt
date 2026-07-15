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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.vitorpamplona.amethyst.ui.note.DisplayBlankAuthor
import com.vitorpamplona.amethyst.ui.note.ObserveAndDrawInnerUserPicture
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.LoadUser
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * A horizontal stack of overlapping avatars for the recent posters in a channel — the "who's here"
 * cue that makes a busy channel feel alive. Each avatar wears a thin ring in the row's background
 * colour so the overlap reads as a deck of cards; the newest poster sits on top (leftmost, highest
 * z-index). Renders nothing for an empty [authorHexes], so callers can drop it in unconditionally.
 */
@Composable
fun ConcordAuthorFacepile(
    authorHexes: List<HexKey>,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 22.dp,
    maxShown: Int = 4,
) {
    if (authorHexes.isEmpty()) return
    val shown = authorHexes.take(maxShown)
    // A ring one-and-a-half dp wide, and each avatar pulled left over its neighbour by a third of
    // its width — enough overlap to read as a stack without hiding faces.
    val ring = 1.5.dp
    val overlap = avatarSize * 0.36f
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(-overlap)) {
        shown.forEachIndexed { index, hex ->
            Box(
                Modifier
                    // Newest (index 0) on top so it isn't clipped by the one after it.
                    .zIndex((shown.size - index).toFloat())
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(ring),
            ) {
                LoadUser(baseUserHex = hex, accountViewModel) { user ->
                    if (user != null) {
                        ObserveAndDrawInnerUserPicture(user, avatarSize - ring * 2, accountViewModel)
                    } else {
                        DisplayBlankAuthor(avatarSize - ring * 2, Modifier, accountViewModel)
                    }
                }
            }
        }
    }
}
