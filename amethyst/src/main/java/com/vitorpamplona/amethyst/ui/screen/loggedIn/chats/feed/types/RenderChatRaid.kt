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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.compose.nip53LiveActivities.StreamSystemCard
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.CrossfadeToDisplayComment
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.LoadUser
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip53LiveActivities.raid.LiveActivitiesRaidEvent

@Composable
fun RenderChatRaid(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val raid = baseNote.event as? LiveActivitiesRaidEvent ?: return
    val from = remember(raid) { raid.fromAddress() }
    val to = remember(raid) { raid.toAddress() }

    // Without both endpoints we can't render a meaningful raid card.
    if (from == null || to == null) return

    val accent = MaterialTheme.colorScheme.primary

    StreamSystemCard(
        accent = accent,
        accentAlpha = 0.14f,
        onClick = { nav.nav(to.toRoute()) },
    ) {
        val backgroundColor = remember(accent) { mutableStateOf(accent.copy(alpha = 0.14f)) }

        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserPicture(from.pubKeyHex, Size20dp, Modifier, accountViewModel, nav)
                    Spacer(StdHorzSpacer)
                    LoadUser(baseUserHex = from.pubKeyHex, accountViewModel = accountViewModel) { user ->
                        if (user != null) {
                            UsernameDisplay(
                                baseUser = user,
                                fontWeight = FontWeight.Bold,
                                accountViewModel = accountViewModel,
                            )
                        }
                    }

                    Spacer(StdHorzSpacer)
                    Text(
                        text = stringRes(R.string.chat_raid_is_raiding),
                        color = accent,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(StdHorzSpacer)

                    UserPicture(to.pubKeyHex, Size20dp, Modifier, accountViewModel, nav)
                    Spacer(StdHorzSpacer)
                    LoadUser(baseUserHex = to.pubKeyHex, accountViewModel = accountViewModel) { user ->
                        if (user != null) {
                            UsernameDisplay(
                                baseUser = user,
                                fontWeight = FontWeight.Bold,
                                accountViewModel = accountViewModel,
                            )
                        }
                    }
                }

                val message = raid.content
                if (message.isNotBlank()) {
                    Spacer(Modifier.padding(top = 2.dp))
                    CrossfadeToDisplayComment(
                        comment = message,
                        backgroundColor = backgroundColor,
                        nav = nav,
                        accountViewModel = accountViewModel,
                    )
                }
            }

            Icon(
                symbol = MaterialSymbols.AutoMirrored.ArrowForwardIos,
                contentDescription = null,
                tint = accent,
                modifier = Size20Modifier,
            )
        }
    }
}

private fun Address.toRoute(): Route = Route.LiveActivityChannel(kind, pubKeyHex, dTag)
