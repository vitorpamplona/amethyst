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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * A horizontal strip of the recent posters in a channel — the "who's here" cue that makes a busy
 * channel feel alive. Each poster is drawn with the app's standard profile avatar
 * ([ClickableUserPicture]), so it carries the same following badge (top-right) and trust-score tag
 * (bottom-centre) shown everywhere else a user appears, instead of a bare cropped image. Laid out
 * with a small gap rather than an overlapping stack so those badges stay readable; the newest poster
 * is leftmost. Renders nothing for an empty [authorHexes], so callers can drop it in unconditionally.
 */
@Composable
fun ConcordAuthorFacepile(
    authorHexes: List<HexKey>,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 24.dp,
    maxShown: Int = 4,
) {
    if (authorHexes.isEmpty()) return
    val shown = authorHexes.take(maxShown)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        shown.forEach { hex ->
            ClickableUserPicture(
                baseUserHex = hex,
                size = avatarSize,
                accountViewModel = accountViewModel,
            )
        }
    }
}
