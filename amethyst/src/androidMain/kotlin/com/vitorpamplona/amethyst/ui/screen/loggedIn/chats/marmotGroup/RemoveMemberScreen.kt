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
import androidx.compose.material.icons.filled.PersonRemove
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.marmot.GroupMemberInfo
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.LoadUser
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoveMemberScreen(
    nostrGroupId: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var members by remember { mutableStateOf(emptyList<GroupMemberInfo>()) }
    var memberToRemove by remember { mutableStateOf<GroupMemberInfo?>(null) }
    var isRemoving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val myPubkey = accountViewModel.account.signer.pubKey
    val context = LocalContext.current

    LaunchedEffect(nostrGroupId) {
        members = accountViewModel.marmotGroupMembers(nostrGroupId)
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
                title = { Text("Remove Member") },
            )
        },
    ) { padding ->
        val removableMembers = members.filter { it.pubkey != myPubkey }

        if (removableMembers.isEmpty()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "No members to remove",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
            ) {
                item {
                    Text(
                        text = "Select a member to remove",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }

                items(removableMembers, key = { it.leafIndex }) { member ->
                    RemovableMemberRow(
                        member = member,
                        accountViewModel = accountViewModel,
                        nav = nav,
                        onRemoveClick = { memberToRemove = member },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (memberToRemove != null) {
        val member = memberToRemove!!
        ConfirmRemoveMemberDialog(
            memberPubkey = member.pubkey,
            accountViewModel = accountViewModel,
            onConfirm = {
                memberToRemove = null
                isRemoving = true
                scope.launch(Dispatchers.IO) {
                    try {
                        accountViewModel.removeMarmotGroupMember(nostrGroupId, member.leafIndex)
                        members = accountViewModel.marmotGroupMembers(nostrGroupId)
                        isRemoving = false
                        launch(Dispatchers.Main) {
                            Toast
                                .makeText(context, "Member removed", Toast.LENGTH_SHORT)
                                .show()
                        }
                        nav.popBack()
                    } catch (e: Exception) {
                        isRemoving = false
                        launch(Dispatchers.Main) {
                            Toast
                                .makeText(
                                    context,
                                    "Failed to remove member: ${e.message}",
                                    Toast.LENGTH_LONG,
                                ).show()
                        }
                    }
                }
            },
            onDismiss = { memberToRemove = null },
        )
    }
}

@Composable
private fun RemovableMemberRow(
    member: GroupMemberInfo,
    accountViewModel: AccountViewModel,
    nav: INav,
    onRemoveClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onRemoveClick)
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
                Text(
                    text = user?.toBestDisplayName() ?: "${member.pubkey.take(16)}...",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = Icons.Default.PersonRemove,
            contentDescription = "Remove",
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ConfirmRemoveMemberDialog(
    memberPubkey: HexKey,
    accountViewModel: AccountViewModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove Member") },
        text = {
            LoadUser(baseUserHex = memberPubkey, accountViewModel = accountViewModel) { user ->
                val name = user?.toBestDisplayName() ?: "${memberPubkey.take(16)}..."
                Text("Are you sure you want to remove \"$name\" from this group?")
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
