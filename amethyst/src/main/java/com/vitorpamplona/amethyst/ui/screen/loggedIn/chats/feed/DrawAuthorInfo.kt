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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserInfo
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserNickname
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
import com.vitorpamplona.amethyst.ui.theme.isLight

@Composable
fun DrawAuthorInfo(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // A geohash chat resolves the message's `n` nickname here (throwaway keys have no profile);
    // null everywhere else, so authors render from their profile as usual.
    val nameOverride = LocalChatDisplayNameResolver.current?.invoke(baseNote)
    baseNote.author?.let {
        WatchAndDisplayUser(it, nameOverride, accountViewModel, nav)
    }
}

/**
 * A stable, pubkey-derived name color so authors are scannable in fast-moving
 * group rooms. The hue comes from the pubkey; saturation/lightness are tuned per
 * theme so every hue stays readable on the "them" bubble fill.
 */
fun authorNameColorFor(
    pubkeyHex: String,
    isLightTheme: Boolean,
): Color {
    val hue = (pubkeyHex.take(6).toIntOrNull(16) ?: pubkeyHex.hashCode()).mod(360).toFloat()
    return if (isLightTheme) {
        Color.hsl(hue, saturation = 0.70f, lightness = 0.35f)
    } else {
        Color.hsl(hue, saturation = 0.55f, lightness = 0.70f)
    }
}

@Composable
private fun WatchAndDisplayUser(
    author: User,
    nameOverride: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val userState by observeUserInfo(author, accountViewModel)
    val nickname by observeUserNickname(author, accountViewModel)
    val petName = nickname?.petName
    // A geohash message's `n` nickname wins over the (usually empty) profile of a throwaway key.
    val displayName = nameOverride ?: petName ?: userState?.info?.bestName()

    val isLightTheme = MaterialTheme.colorScheme.isLight
    val nameColor =
        remember(author.pubkeyHex, isLightTheme) {
            authorNameColorFor(author.pubkeyHex, isLightTheme)
        }

    UserDisplayNameLayout(
        picture = {
            InnerUserPicture(
                userHex = author.pubkeyHex,
                userPicture = userState?.info?.picture,
                userName = displayName,
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
            CreateTextWithEmoji(
                text = displayName ?: author.pubkeyDisplayHex(),
                tags = (if (nameOverride == null && petName != null) nickname?.tags else userState?.tags) ?: EmptyTagList,
                color = nameColor,
                maxLines = 1,
                fontWeight = FontWeight.Bold,
            )
        },
    )
}
