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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserInfo
import com.vitorpamplona.amethyst.ui.components.CreateTextWithEmoji
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.FollowingIcon
import com.vitorpamplona.amethyst.ui.note.InnerUserPicture
import com.vitorpamplona.amethyst.ui.note.ObserveAndRenderUserCards
import com.vitorpamplona.amethyst.ui.note.WatchUserFollows
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.layouts.UserDisplayNameLayout
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.Size5Modifier

@Composable
fun DrawAuthorInfo(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    baseNote.author?.let {
        WatchAndDisplayUser(it, accountViewModel, nav)
    }
}

@Composable
private fun WatchAndDisplayUser(
    author: User,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val userState by observeUserInfo(author, accountViewModel)

    UserDisplayNameLayout(
        picture = {
            InnerUserPicture(
                userHex = author.pubkeyHex,
                userPicture = userState?.info?.picture,
                userName = userState?.info?.bestName(),
                size = Size20dp,
                modifier = Modifier,
                accountViewModel = accountViewModel,
            )

            WatchUserFollows(author.pubkeyHex, accountViewModel) { newFollowingState ->
                if (newFollowingState) {
                    FollowingIcon(Size5Modifier)
                }
            }

            ObserveAndRenderUserCards(author, Size20dp, Modifier.align(Alignment.BottomCenter), accountViewModel)
        },
        name = {
            if (userState != null) {
                CreateTextWithEmoji(
                    text = userState?.info?.bestName() ?: author.pubkeyDisplayHex(),
                    tags = userState?.tags ?: EmptyTagList,
                    maxLines = 1,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                CreateTextWithEmoji(
                    text = author.pubkeyDisplayHex(),
                    tags = EmptyTagList,
                    maxLines = 1,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    )
}
