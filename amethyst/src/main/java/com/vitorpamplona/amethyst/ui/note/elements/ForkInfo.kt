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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNote
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUser
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserInfo
import com.vitorpamplona.amethyst.ui.components.CreateClickableTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.LoadNote
import com.vitorpamplona.amethyst.ui.components.appendLink
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.nip05
import com.vitorpamplona.quartz.experimental.forks.forkFromAddress
import com.vitorpamplona.quartz.experimental.forks.forkFromVersion
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent

@Composable
fun ShowForkInformation(
    noteEvent: BaseThreadedEvent,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val forkedAddress = remember(noteEvent) { noteEvent.forkFromAddress() }
    val forkedEvent = remember(noteEvent) { noteEvent.forkFromVersion() }
    if (forkedAddress != null) {
        LoadAddressableNote(forkedAddress, accountViewModel) { addressableNote ->
            if (addressableNote != null) {
                ForkInformationRowLightColor(addressableNote, modifier, accountViewModel, nav)
            }
        }
    } else if (forkedEvent != null) {
        LoadNote(forkedEvent.eventId, accountViewModel) { event ->
            if (event != null) {
                ForkInformationRowLightColor(event, modifier, accountViewModel, nav)
            }
        }
    }
}

@Composable
fun ForkInformationRowLightColor(
    originalVersion: Note,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteState by observeNote(originalVersion, accountViewModel)
    val note = noteState?.note ?: return
    val author = note.author ?: return
    val route = remember(note) { routeFor(note, accountViewModel.account) }

    if (route != null) {
        Row(modifier) {
            Text(
                text =
                    buildAnnotatedString {
                        appendLink(stringRes(id = R.string.forked_from) + " ") {
                            nav.nav(route)
                        }
                    },
                style =
                    LocalTextStyle.current.copy(
                        color = MaterialTheme.colorScheme.nip05,
                        fontSize = Font14SP,
                    ),
                maxLines = 1,
                overflow = TextOverflow.Visible,
            )

            val userState by observeUser(author, accountViewModel)
            userState?.user?.toBestDisplayName()?.let {
                CreateClickableTextWithEmoji(
                    clickablePart = it,
                    maxLines = 1,
                    route = route,
                    overrideColor = MaterialTheme.colorScheme.nip05,
                    fontSize = Font14SP,
                    nav = nav,
                    tags = userState?.user?.info?.tags,
                )
            }
        }
    }
}

@Composable
fun ForkInformationRow(
    originalVersion: Note,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteState by observeNote(originalVersion, accountViewModel)
    val note = noteState?.note ?: return
    val route = remember(note) { routeFor(note, accountViewModel.account) }

    if (route != null) {
        Row(modifier) {
            val author = note.author ?: return
            val meta by observeUserInfo(author, accountViewModel)

            Text(stringRes(id = R.string.forked_from))
            Spacer(modifier = StdHorzSpacer)

            val userMetadata by observeUserInfo(author, accountViewModel)

            CreateClickableTextWithEmoji(
                clickablePart = remember(meta) { meta?.bestName() ?: author.pubkeyDisplayHex() },
                maxLines = 1,
                route = route,
                nav = nav,
                tags = userMetadata?.tags,
            )
        }
    }
}
