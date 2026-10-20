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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relayList

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.relay_list_screen_title
import com.vitorpamplona.amethyst.commons.resources.relay_list_section_count
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.types.RelayListRow
import com.vitorpamplona.amethyst.commons.ui.note.types.RelayListSectionHeader
import com.vitorpamplona.amethyst.commons.ui.note.types.observeRelayListSections
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event

/**
 * Every relay of a relay-list event. Lists of tens of thousands of relays exist, so the relays
 * live in a [LazyColumn] here instead of in the feed card, which only previews the first few.
 */
@Composable
fun RelayListMembersScreen(
    noteId: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadNote(baseNoteHex = noteId) { note ->
        if (note == null) {
            RelayListLoading(nav)
        } else {
            RelayListMembersScreen(note, accountViewModel, nav)
        }
    }
}

@Composable
private fun RelayListMembersScreen(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event by observeNoteEvent<Event>(note, accountViewModel)

    if (event == null) {
        RelayListLoading(nav)
        return
    }

    val sections = observeRelayListSections(note, event, accountViewModel)

    Scaffold(
        topBar = {
            TopBarWithBackButton(
                caption = sections.singleOrNull()?.title ?: stringRes(Res.string.relay_list_screen_title),
                nav = nav,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            sections.forEach { section ->
                item(contentType = "header") {
                    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 5.dp)) {
                        RelayListSectionHeader(section)
                        Text(
                            text = pluralStringRes(Res.plurals.relay_list_section_count, section.relays.size, section.relays.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    HorizontalDivider(thickness = DividerThickness)
                }

                // Indexed, not keyed by URL: a list written by hand can repeat a relay, and a
                // duplicate key would crash the LazyColumn.
                items(section.relays.size, contentType = { "relay" }) { index ->
                    Column {
                        RelayListRow(section.relays[index], accountViewModel, nav, Modifier.padding(vertical = 10.dp))
                        HorizontalDivider(thickness = DividerThickness)
                    }
                }
            }
        }
    }
}

@Composable
private fun RelayListLoading(nav: INav) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(
                caption = stringRes(Res.string.relay_list_screen_title),
                nav = nav,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}
