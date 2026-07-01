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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupChatroom
import com.vitorpamplona.amethyst.ui.navigation.bottombars.FabBottomBarPadded
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.NonClickableUserPictures
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.header.DisplayUserSetAsSubject
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.HexKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarmotGroupListScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var groupList by remember { mutableStateOf(listOf<Pair<HexKey, MarmotGroupChatroom>>()) }
    var selectedTab by remember { mutableIntStateOf(0) }

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
                            contentDescription = stringRes(R.string.back),
                        )
                    }
                },
                title = { Text(stringRes(R.string.marmot_groups_title)) },
            )
        },
        floatingActionButton = {
            FabBottomBarPadded(nav) {
                FloatingActionButton(onClick = { nav.nav(Route.CreateMarmotGroup) }, shape = CircleShape) {
                    Icon(MaterialSymbols.Add, contentDescription = stringRes(R.string.marmot_create_group))
                }
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
                            text =
                                if (knownGroups.isEmpty()) {
                                    stringRes(R.string.marmot_tab_known)
                                } else {
                                    stringRes(R.string.marmot_tab_known_count, knownGroups.size)
                                },
                        )
                    },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text =
                                if (newRequestGroups.isEmpty()) {
                                    stringRes(R.string.marmot_tab_new_requests)
                                } else {
                                    stringRes(R.string.marmot_tab_new_requests_count, newRequestGroups.size)
                                },
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
                            text =
                                if (selectedTab == 0) {
                                    stringRes(R.string.marmot_no_groups)
                                } else {
                                    stringRes(R.string.marmot_no_invitations)
                                },
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text =
                                if (selectedTab == 0) {
                                    stringRes(R.string.marmot_no_groups_desc)
                                } else {
                                    stringRes(R.string.marmot_no_invitations_desc)
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
                            accountViewModel = accountViewModel,
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
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    val displayName by chatroom.displayName.collectAsStateWithLifecycle()
    val members by chatroom.members.collectAsStateWithLifecycle()
    val memberPubkeys = remember(members) { members.map { it.pubkey } }
    val newestMessage = chatroom.newestMessage

    val lastReadTime by accountViewModel.account.loadLastReadFlow(marmotGroupLastReadRoute(groupId)).collectAsStateWithLifecycle()
    // Not remembered: chatroom.messages can shrink without newestMessage or
    // lastReadTime changing (pruning, kind:5 deletion of an older message),
    // so caching on those keys would serve a stale count. The set is pruned
    // to ~100 entries, so counting per recomposition is cheap.
    val unread = chatroom.messages.count { (it.createdAt() ?: Long.MIN_VALUE) > lastReadTime }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (memberPubkeys.isNotEmpty()) {
            NonClickableUserPictures(
                userHexList = memberPubkeys,
                size = 55.dp,
                accountViewModel = accountViewModel,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            if (!displayName.isNullOrBlank()) {
                Text(
                    text = displayName!!,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (memberPubkeys.isNotEmpty()) {
                DisplayUserSetAsSubject(
                    userList = memberPubkeys,
                    accountViewModel = accountViewModel,
                    fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Normal,
                )
            } else {
                Text(
                    text = stringRes(R.string.marmot_group_fallback_name, groupId.take(8)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
                    text = stringRes(R.string.marmot_no_messages_yet),
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
                    text = pluralStringResource(R.plurals.marmot_message_count, chatroom.messages.size, chatroom.messages.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
