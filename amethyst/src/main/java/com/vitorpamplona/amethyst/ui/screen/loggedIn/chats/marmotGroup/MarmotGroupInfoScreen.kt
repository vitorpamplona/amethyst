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

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.marmot.GroupMemberInfo
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.LoadUser
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarmotGroupInfoScreen(
    nostrGroupId: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val chatroom =
        remember(nostrGroupId) {
            accountViewModel.account.marmotGroupList.getOrCreateGroup(nostrGroupId)
        }
    val displayName by chatroom.displayName.collectAsStateWithLifecycle()
    val groupDescription by chatroom.description.collectAsStateWithLifecycle()
    val adminPubkeys by chatroom.adminPubkeys.collectAsStateWithLifecycle()
    val groupRelays by chatroom.relays.collectAsStateWithLifecycle()
    var members by remember { mutableStateOf(emptyList<GroupMemberInfo>()) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var isLeaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val myPubkey = accountViewModel.account.signer.pubKey
    val context = LocalContext.current

    LaunchedEffect(nostrGroupId) {
        members = accountViewModel.marmotGroupMembers(nostrGroupId)
        chatroom.memberCount.value = members.size
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                title = { Text("Group Info") },
                actions = {
                    IconButton(onClick = { nav.nav(Route.MarmotGroupAddMember(nostrGroupId)) }) {
                        Icon(
                            imageVector = Icons.Default.GroupAdd,
                            contentDescription = "Add Member",
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            // Group header section
            item {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                ) {
                    Text(
                        text = displayName ?: "Group ${nostrGroupId.take(8)}...",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (!groupDescription.isNullOrEmpty()) {
                        Text(
                            text = groupDescription!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Text(
                        text = "${members.size} members",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (groupRelays.isNotEmpty()) {
                        Text(
                            text = "Relays: ${groupRelays.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    val epoch = accountViewModel.account.marmotManager?.groupEpoch(nostrGroupId)
                    if (epoch != null) {
                        Text(
                            text = "Epoch: $epoch",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                HorizontalDivider()
            }

            // Members section header
            item {
                Text(
                    text = "Members",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            // Member list
            items(members, key = { it.leafIndex }) { member ->
                MemberRow(
                    member = member,
                    isMe = member.pubkey == myPubkey,
                    isAdmin = member.pubkey in adminPubkeys,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
                HorizontalDivider()
            }

            // Leave group section
            item {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { showLeaveDialog = true }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Leave Group",
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = "Leave Group",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (showLeaveDialog) {
        LeaveGroupDialog(
            groupName = displayName ?: "this group",
            onConfirm = {
                showLeaveDialog = false
                isLeaving = true
                scope.launch(Dispatchers.IO) {
                    try {
                        accountViewModel.leaveMarmotGroup(nostrGroupId)
                        accountViewModel.account.marmotGroupList.removeGroup(nostrGroupId)
                        nav.nav(Route.MarmotGroupList)
                    } catch (e: Exception) {
                        isLeaving = false
                        launch(Dispatchers.Main) {
                            Toast
                                .makeText(
                                    context,
                                    "Failed to leave group: ${e.message}",
                                    Toast.LENGTH_LONG,
                                ).show()
                        }
                    }
                }
            },
            onDismiss = { showLeaveDialog = false },
        )
    }
}

@Composable
fun MemberRow(
    member: GroupMemberInfo,
    isMe: Boolean,
    isAdmin: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { nav.nav(Route.Profile(member.pubkey)) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UserPicture(
            userHex = member.pubkey,
            size = 36.dp,
            accountViewModel = accountViewModel,
            nav = nav,
        )
        Column(modifier = Modifier.weight(1f)) {
            LoadUser(baseUserHex = member.pubkey, accountViewModel = accountViewModel) { user ->
                val displayName = user?.toBestDisplayName() ?: "${member.pubkey.take(16)}..."
                val suffix =
                    buildString {
                        if (isMe) append(" (you)")
                        if (isAdmin) append(" - admin")
                    }
                Text(
                    text = "$displayName$suffix",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (isMe || isAdmin) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
fun LeaveGroupDialog(
    groupName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Leave Group") },
        text = {
            Text("Are you sure you want to leave \"$groupName\"? You will no longer receive messages from this group.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Leave", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
