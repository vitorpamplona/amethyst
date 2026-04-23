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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.marmotGroup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupChatroom
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarmotGroupListScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var groupList by remember { mutableStateOf(listOf<Pair<HexKey, MarmotGroupChatroom>>()) }
    var selectedTab by remember { mutableStateOf(0) }

    // Load group list
    LaunchedEffect(Unit) {
        loadGroupList(accountViewModel, onUpdate = { groupList = it })
    }

    // Listen for group list changes
    LaunchedEffect(Unit) {
        accountViewModel.account.marmotGroupList.groupListChanges.collect {
            loadGroupList(accountViewModel, onUpdate = { groupList = it })
        }
    }

    // KeyPackage publishing is handled at Account startup
    // (Account.ensureMarmotKeyPackagePublished), so this screen no longer
    // needs to do anything to make sure invitees can find a KeyPackage.

    val followState by accountViewModel.account.kind3FollowList.flow
        .collectAsStateWithLifecycle()
    val followingKeySet = followState.authors

    val knownGroups = remember(groupList, followingKeySet) { groupList.filter { it.second.isKnown(followingKeySet) } }
    val newRequestGroups = remember(groupList, followingKeySet) { groupList.filter { !it.second.isKnown(followingKeySet) } }
    val visibleGroups = if (selectedTab == 0) knownGroups else newRequestGroups

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) {
                        Icon(
                            symbol = MaterialSymbols.AutoMirrored.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                title = { Text("Marmot Groups") },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.nav(Route.CreateMarmotGroup) }, shape = CircleShape) {
                Icon(MaterialSymbols.Add, contentDescription = "Create Group")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = if (knownGroups.isEmpty()) "Known" else "Known (${knownGroups.size})",
                        )
                    },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = if (newRequestGroups.isEmpty()) "New Requests" else "New Requests (${newRequestGroups.size})",
                        )
                    },
                )
            }

            if (visibleGroups.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (selectedTab == 0) "No groups yet" else "No invitations",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text =
                                if (selectedTab == 0) {
                                    "Create a group or accept an invitation."
                                } else {
                                    "When someone adds you to a group it will show up here until you reply."
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(visibleGroups, key = { it.first }) { (groupId, chatroom) ->
                        MarmotGroupListItem(
                            groupId = groupId,
                            chatroom = chatroom,
                            onClick = {
                                nav.nav(Route.MarmotGroupChat(groupId))
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

private fun loadGroupList(
    accountViewModel: AccountViewModel,
    onUpdate: (List<Pair<HexKey, MarmotGroupChatroom>>) -> Unit,
) {
    val groups = mutableListOf<Pair<HexKey, MarmotGroupChatroom>>()
    accountViewModel.account.marmotGroupList.rooms.forEach { key, chatroom ->
        groups.add(key to chatroom)
    }
    // Also add groups from MarmotManager that might not have messages yet
    accountViewModel.account.marmotManager?.activeGroupIds()?.forEach { groupId ->
        if (groups.none { it.first == groupId }) {
            groups.add(groupId to accountViewModel.account.marmotGroupList.getOrCreateGroup(groupId))
        }
    }
    groups.sortByDescending { it.second.newestMessage?.createdAt() ?: 0L }
    onUpdate(groups)
}

@Composable
fun MarmotGroupListItem(
    groupId: HexKey,
    chatroom: MarmotGroupChatroom,
    onClick: () -> Unit,
) {
    val displayName by chatroom.displayName.collectAsStateWithLifecycle()
    val unread by chatroom.unreadCount.collectAsStateWithLifecycle()
    val newestMessage = chatroom.newestMessage

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName ?: "Group ${groupId.take(8)}...",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (newestMessage != null) {
                Text(
                    text = newestMessage.event?.content ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            } else {
                Text(
                    text = "No messages yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (unread > 0) {
                Box(
                    modifier =
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (unread > 99) "99+" else unread.toString(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Text(
                    text = "${chatroom.messages.size} msgs",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
