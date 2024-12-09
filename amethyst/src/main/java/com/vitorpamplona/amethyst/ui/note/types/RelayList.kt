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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.ShowMoreButton
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.note.AddRelayButton
import com.vitorpamplona.amethyst.ui.note.RemoveRelayButton
import com.vitorpamplona.amethyst.ui.note.getGradient
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.ammolite.relays.RelayBriefInfoCache
import com.vitorpamplona.quartz.events.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.events.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.events.RelaySetEvent
import com.vitorpamplona.quartz.events.SearchRelayListEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun DisplayRelaySet(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? RelaySetEvent ?: return

    val relays by
        remember(baseNote) {
            mutableStateOf(
                noteEvent.relays().map { RelayBriefInfoCache.RelayBriefInfo(it) }.toImmutableList(),
            )
        }

    DisplayRelaySet(
        relays,
        noteEvent.firstTagFor("title", "name") ?: "#${noteEvent.dTag()}",
        noteEvent.description(),
        backgroundColor,
        accountViewModel,
        nav,
    )
}

@Composable
fun DisplayNIP65RelayList(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? AdvertisedRelayListEvent ?: return

    val writeRelays by
        remember(baseNote) {
            mutableStateOf(
                noteEvent.writeRelays().map { RelayBriefInfoCache.RelayBriefInfo(it) }.toImmutableList(),
            )
        }

    val readRelays by
        remember(baseNote) {
            mutableStateOf(
                noteEvent.readRelays()?.map { RelayBriefInfoCache.RelayBriefInfo(it) }?.toImmutableList() ?: persistentListOf(),
            )
        }

    DisplayRelaySet(
        writeRelays,
        stringRes(id = R.string.public_home_section),
        null,
        backgroundColor,
        accountViewModel,
        nav,
    )

    DisplayRelaySet(
        readRelays,
        stringRes(id = R.string.public_notif_section),
        null,
        backgroundColor,
        accountViewModel,
        nav,
    )
}

@Composable
fun DisplayDMRelayList(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? ChatMessageRelayListEvent ?: return

    val relays by
        remember(baseNote) {
            mutableStateOf(
                noteEvent.relays().map { RelayBriefInfoCache.RelayBriefInfo(it) }.toImmutableList(),
            )
        }

    DisplayRelaySet(
        relays,
        stringRes(id = R.string.dm_relays_title),
        null,
        backgroundColor,
        accountViewModel,
        nav,
    )
}

@Composable
fun DisplaySearchRelayList(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? SearchRelayListEvent ?: return

    val relays by
        remember(baseNote) {
            mutableStateOf(
                noteEvent.relays().map { RelayBriefInfoCache.RelayBriefInfo(it) }.toImmutableList(),
            )
        }

    DisplayRelaySet(
        relays,
        stringRes(id = R.string.search_relays_title),
        null,
        backgroundColor,
        accountViewModel,
        nav,
    )
}

@Composable
fun DisplayRelaySet(
    relays: ImmutableList<RelayBriefInfoCache.RelayBriefInfo>,
    relayListName: String,
    relayDescription: String?,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var expanded by remember { mutableStateOf(false) }

    val toMembersShow =
        if (expanded) {
            relays
        } else {
            relays.take(3)
        }

    Text(
        text = relayListName,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(5.dp),
        textAlign = TextAlign.Center,
    )

    relayDescription?.let {
        Text(
            text = it,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(5.dp),
            textAlign = TextAlign.Center,
            color = Color.Gray,
        )
    }

    Box {
        Column(modifier = Modifier.padding(top = 5.dp)) {
            toMembersShow.forEach { relay ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = relay.displayUrl,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .padding(start = 10.dp, bottom = 5.dp)
                                .weight(1f),
                    )

                    Column(modifier = Modifier.padding(start = 10.dp)) {
                        RelayOptionsAction(relay.url, accountViewModel, nav)
                    }
                }
            }
        }

        if (relays.size > 3 && !expanded) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(getGradient(backgroundColor)),
            ) {
                ShowMoreButton { expanded = !expanded }
            }
        }
    }
}

@Composable
private fun RelayOptionsAction(
    relay: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val userStateRelayInfo by accountViewModel.account
        .userProfile()
        .live()
        .relayInfo
        .observeAsState()
    val isCurrentlyOnTheUsersList by
        remember(userStateRelayInfo) {
            derivedStateOf {
                userStateRelayInfo
                    ?.user
                    ?.latestContactList
                    ?.relays()
                    ?.none { it.key == relay } == true
            }
        }

    if (isCurrentlyOnTheUsersList) {
        AddRelayButton {
            nav.nav(Route.EditRelays.base + "?toAdd=" + URLEncoder.encode(relay, StandardCharsets.UTF_8.toString()))
        }
    } else {
        RemoveRelayButton {
            nav.nav(Route.EditRelays.base + "?toAdd=" + URLEncoder.encode(relay, StandardCharsets.UTF_8.toString()))
        }
    }
}
