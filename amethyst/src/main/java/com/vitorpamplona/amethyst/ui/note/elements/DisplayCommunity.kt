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
package com.vitorpamplona.amethyst.ui.note.elements

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.buildLinkString
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.HalfStartPadding
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip72ModCommunities.communityAddress

@Composable
fun DisplayFollowingCommunityInPost(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(HalfStartPadding) {
        Row(verticalAlignment = Alignment.CenterVertically) { DisplayCommunity(baseNote, nav) }
    }
}

@Composable
private fun DisplayCommunity(
    note: Note,
    nav: INav,
) {
    val communityTag =
        remember(note) { note.event?.communityAddress() } ?: return

    val displayTag =
        remember(note) {
            buildLinkString(
                getCommunityShortName(communityTag),
            ) {
                nav.nav(Route.Community(communityTag.kind, communityTag.pubKeyHex, communityTag.dTag))
            }
        }

    Text(
        text = displayTag,
        style =
            LocalTextStyle.current.copy(
                color =
                    MaterialTheme.colorScheme.primary.copy(
                        alpha = 0.52f,
                    ),
            ),
        maxLines = 1,
    )
}

fun getCommunityShortName(communityAddress: Address): String {
    val name =
        if (communityAddress.dTag.length > 10) {
            communityAddress.dTag.take(10) + "..."
        } else {
            communityAddress.dTag.take(10)
        }

    return "/n/$name"
}
