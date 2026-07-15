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
package com.vitorpamplona.amethyst.desktop.ui.scheduledposts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.service.drafts.DesktopDraftStore
import com.vitorpamplona.amethyst.desktop.ui.DraftsScreen
import com.vitorpamplona.amethyst.desktop.ui.ReadingColumn

/**
 * Host for the "Drafts & Scheduled" deck column: a two-tab surface over the
 * existing article [DraftsScreen] (which brings its own [ReadingColumn]) and the
 * new [ScheduledPostsList] management list.
 *
 * @param accountPubkeyHex the logged-in account whose scheduled posts to show.
 *   The shared store holds every account's rows, so the list must be scoped here.
 */
@Composable
fun DraftsAndScheduledScreen(
    draftStore: DesktopDraftStore,
    accountPubkeyHex: String,
    relayManager: DesktopRelayConnectionManager,
    account: AccountState.LoggedIn,
    onOpenEditor: (slug: String?) -> Unit,
    onEditInComposer: (content: String, draftDTag: String?, scheduledForSec: Long?) -> Unit = { _, _, _ -> },
) {
    // Scheduled first — it's the destination's headline and matches the sidebar label.
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Text("Scheduled", modifier = Modifier.padding(12.dp))
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Text("Drafts", modifier = Modifier.padding(12.dp))
            }
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                Text("Articles", modifier = Modifier.padding(12.dp))
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 ->
                    ReadingColumn {
                        ScheduledPostsList(
                            accountPubkeyHex = accountPubkeyHex,
                            onEditInComposer = onEditInComposer,
                        )
                    }
                1 ->
                    ReadingColumn {
                        NoteDraftsList(
                            relayManager = relayManager,
                            account = account,
                            // Editing a draft re-opens the composer prefilled with its content
                            // and reuses its dTag, so re-saving replaces the same draft row.
                            onOpenDraft = { dTag, content -> onEditInComposer(content, dTag, null) },
                        )
                    }
                else ->
                    DraftsScreen(
                        draftStore = draftStore,
                        onOpenEditor = onOpenEditor,
                    )
            }
        }
    }
}
