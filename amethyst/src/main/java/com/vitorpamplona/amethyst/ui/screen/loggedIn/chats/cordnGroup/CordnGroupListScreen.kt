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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.cordnGroup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cordn_groups_title
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.CordnGroupRoomCompose
import com.vitorpamplona.amethyst.ui.stringRes

/**
 * Every cordn group this account is in.
 *
 * ## Why this exists when Messages already lists them
 *
 * Messages mixes every conversation type into one inbox, which is right for
 * reading and wrong for finding: a cordn group is the one kind whose messages
 * do not come from a relay, so when something is not arriving the question is
 * always "which coordinator" rather than "which relay". A list of just these,
 * reachable from the drawer like the Marmot rooms beside it, is where that
 * question can be answered. Tapping a row opens the same chat the inbox opens.
 *
 * ## It renders from local state alone
 *
 * `CordnGroupList.all` is the rooms `CordnRuntime` already holds, and its sync
 * loop already subscribes per coordinator. There is no relay REQ behind this
 * screen and no list-level fetch to make -- which is why
 * `BottomBarFeedPreloaders` has nothing to warm for it. Ordered by the newest
 * message, off the same `revision` signal the inbox ordering uses, so a group
 * that has just received something rises here too.
 */
@Composable
fun CordnGroupListScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val runtime = accountViewModel.account.cordnRuntime

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.cordn_groups_title), nav) },
    ) { padding ->
        if (runtime == null) {
            EmptyState(
                title = stringRes(R.string.cordn_group_unavailable),
                description = stringRes(R.string.cordn_group_unavailable_detail),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        // `revision` rather than `all`: a room's newest message changes inside
        // the room object, which `all` cannot see, so ordering on `all` alone
        // would freeze after the first message. See CordnGroupList.revision.
        val revision by runtime.groups.revision.collectAsStateWithLifecycle()
        val rooms =
            remember(revision) {
                runtime.groups.all.value
                    .sortedByDescending {
                        it.newest.value
                            ?.envelope
                            ?.createdAt ?: 0L
                    }
            }

        if (rooms.isEmpty()) {
            Column(
                modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringRes(R.string.cordn_groups_none),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringRes(R.string.cordn_groups_none_detail),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { nav.nav(Route.CordnCreateGroup) }) {
                    Text(stringRes(R.string.cordn_groups_start))
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            state = rememberLazyListState(),
        ) {
            items(rooms, key = { it.coordinatorPubKey + it.gid }) { room ->
                // The inbox's own row, not a second rendering of it: it already
                // reads the live name, preview, annotations and unread count off
                // the room, and two of those going out of step would be a bug
                // nobody would look for here.
                CordnGroupRoomCompose(room, accountViewModel, nav)
                HorizontalDivider(Modifier.fillMaxWidth())
            }
        }
    }
}
