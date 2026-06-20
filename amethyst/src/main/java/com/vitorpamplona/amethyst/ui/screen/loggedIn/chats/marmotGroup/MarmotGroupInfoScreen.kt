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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.marmot.GroupMemberInfo
import com.vitorpamplona.amethyst.model.nip11RelayInfo.loadRelayInfo
import com.vitorpamplona.amethyst.ui.components.util.setText
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.RenderRelayIcon
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.ui.note.timeAgo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.LoadUser
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.MediumRelayIconModifier
import com.vitorpamplona.amethyst.ui.theme.SuggestionListDefaultHeightChat
import com.vitorpamplona.amethyst.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.ripple24dp
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrlOrNull
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
    val relayActivity by chatroom.relayActivity.collectAsStateWithLifecycle()
    val members by chatroom.members.collectAsStateWithLifecycle()
    var showLeaveDialog by remember { mutableStateOf(false) }
    var isLeaving by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<GroupMemberInfo?>(null) }
    var memberToPromote by remember { mutableStateOf<GroupMemberInfo?>(null) }
    var memberToDemote by remember { mutableStateOf<GroupMemberInfo?>(null) }
    var addSearchInput by remember { mutableStateOf("") }
    var addStatus by remember { mutableStateOf<String?>(null) }
    var isAddError by remember { mutableStateOf(false) }
    var isAdding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val myPubkey = accountViewModel.account.signer.pubKey
    val context = LocalContext.current

    val userSuggestions =
        remember {
            UserSuggestionState(accountViewModel.account, accountViewModel.nip05ClientBuilder())
        }

    DisposableEffect(Unit) {
        onDispose { userSuggestions.reset() }
    }

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
                title = { Text(stringRes(R.string.marmot_group_info_title)) },
                actions = {
                    IconButton(onClick = { nav.nav(Route.MarmotGroupEditInfo(nostrGroupId)) }) {
                        Icon(
                            symbol = MaterialSymbols.Edit,
                            contentDescription = stringRes(R.string.marmot_edit_group_info),
                        )
                    }
                    IconButton(
                        onClick = { showLeaveDialog = true },
                        enabled = !isLeaving,
                    ) {
                        Icon(
                            symbol = MaterialSymbols.AutoMirrored.ExitToApp,
                            contentDescription = stringRes(R.string.marmot_leave_group),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
        ) {
            // Group header section (fixed at top)
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayName ?: stringRes(R.string.marmot_group_fallback_name, nostrGroupId.take(8)),
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
                            text = pluralStringResource(R.plurals.marmot_member_count, members.size, members.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (groupRelays.isNotEmpty()) {
                        GroupRelayStrip(
                            relayUrls = groupRelays,
                            relayActivity = relayActivity,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                }
            }
            HorizontalDivider()

            // Members list (scrollable, takes remaining vertical space)
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (groupRelays.isNotEmpty()) {
                    item {
                        RelayHealthSection(
                            relayUrls = groupRelays,
                            relayActivity = relayActivity,
                            nav = nav,
                        )
                        HorizontalDivider()
                    }
                }

                item {
                    Text(
                        text = stringRes(R.string.members),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }

                val isMeAdmin = myPubkey in adminPubkeys
                items(members, key = { it.leafIndex }) { member ->
                    val isMe = member.pubkey == myPubkey
                    val isAdmin = member.pubkey in adminPubkeys
                    // Only admins can mutate other members' roles. Self-promote
                    // is a no-op; self-demote must go through the leave flow so
                    // we hide the toggle for the current user.
                    val canToggleAdmin = isMeAdmin && !isMe
                    // Guard against removing the last admin (MIP-03 depletion
                    // guard would otherwise reject the commit).
                    val canDemote = canToggleAdmin && isAdmin && adminPubkeys.size > 1
                    val canPromote = canToggleAdmin && !isAdmin
                    MemberRow(
                        member = member,
                        isMe = isMe,
                        isAdmin = isAdmin,
                        canRemove = !isMe,
                        canPromote = canPromote,
                        canDemote = canDemote,
                        accountViewModel = accountViewModel,
                        nav = nav,
                        onRemoveClick = { memberToRemove = member },
                        onPromoteClick = { memberToPromote = member },
                        onDemoteClick = { memberToDemote = member },
                    )
                    HorizontalDivider()
                }
            }

            HorizontalDivider()

            // Suggestions + add-member input (fixed at bottom)
            AddMemberInline(
                nostrGroupId = nostrGroupId,
                searchInput = addSearchInput,
                onSearchInputChange = { value ->
                    addSearchInput = value
                    if (!isAddError) addStatus = null
                    if (value.length > 2) {
                        userSuggestions.processCurrentWord(value)
                    } else {
                        userSuggestions.reset()
                    }
                },
                userSuggestions = userSuggestions,
                statusMessage = addStatus,
                isError = isAddError,
                isAdding = isAdding,
                accountViewModel = accountViewModel,
                onAdd = { user ->
                    isAdding = true
                    isAddError = false
                    addStatus = stringRes(context, R.string.marmot_adding_user, user.toBestDisplayName())
                    val targetPubkey = user.pubkeyHex
                    val targetName = user.toBestDisplayName()
                    scope.launch(Dispatchers.IO) {
                        try {
                            val result = accountViewModel.addMarmotGroupMember(nostrGroupId, targetPubkey)
                            if (result.startsWith("Success")) {
                                addStatus = null
                                isAddError = false
                                addSearchInput = ""
                                userSuggestions.reset()
                            } else {
                                addStatus = stringRes(context, R.string.marmot_failed_to_add_user, targetName, result.removePrefix("Error: "))
                                isAddError = true
                            }
                        } catch (e: Exception) {
                            addStatus = stringRes(context, R.string.marmot_failed_to_add_user, targetName, e.message ?: stringRes(context, R.string.marmot_unknown_error))
                            isAddError = true
                        } finally {
                            isAdding = false
                        }
                    }
                },
            )
        }
    }

    if (showLeaveDialog) {
        LeaveGroupDialog(
            groupName = displayName ?: stringRes(R.string.marmot_this_group),
            onConfirm = {
                showLeaveDialog = false
                isLeaving = true
                scope.launch(Dispatchers.IO) {
                    try {
                        accountViewModel.leaveMarmotGroup(nostrGroupId)
                        nav.nav(Route.Message)
                    } catch (e: Exception) {
                        isLeaving = false
                        launch(Dispatchers.Main) {
                            Toast
                                .makeText(
                                    context,
                                    stringRes(context, R.string.marmot_failed_to_leave_group, e.message),
                                    Toast.LENGTH_LONG,
                                ).show()
                        }
                    }
                }
            },
            onDismiss = { showLeaveDialog = false },
        )
    }

    memberToRemove?.let { member ->
        ConfirmRemoveMemberDialog(
            memberPubkey = member.pubkey,
            accountViewModel = accountViewModel,
            onConfirm = {
                memberToRemove = null
                scope.launch(Dispatchers.IO) {
                    try {
                        accountViewModel.removeMarmotGroupMember(nostrGroupId, member.leafIndex)
                        launch(Dispatchers.Main) {
                            Toast
                                .makeText(context, stringRes(context, R.string.marmot_member_removed), Toast.LENGTH_SHORT)
                                .show()
                        }
                    } catch (e: Exception) {
                        launch(Dispatchers.Main) {
                            Toast
                                .makeText(
                                    context,
                                    stringRes(context, R.string.marmot_failed_to_remove_member, e.message),
                                    Toast.LENGTH_LONG,
                                ).show()
                        }
                    }
                }
            },
            onDismiss = { memberToRemove = null },
        )
    }

    memberToPromote?.let { member ->
        ConfirmGrantAdminDialog(
            memberPubkey = member.pubkey,
            accountViewModel = accountViewModel,
            onConfirm = {
                memberToPromote = null
                scope.launch(Dispatchers.IO) {
                    try {
                        accountViewModel.grantMarmotGroupAdmin(nostrGroupId, member.pubkey)
                        launch(Dispatchers.Main) {
                            Toast
                                .makeText(context, stringRes(context, R.string.marmot_admin_granted), Toast.LENGTH_SHORT)
                                .show()
                        }
                    } catch (e: Exception) {
                        launch(Dispatchers.Main) {
                            Toast
                                .makeText(
                                    context,
                                    stringRes(context, R.string.marmot_failed_to_grant_admin, e.message),
                                    Toast.LENGTH_LONG,
                                ).show()
                        }
                    }
                }
            },
            onDismiss = { memberToPromote = null },
        )
    }

    memberToDemote?.let { member ->
        ConfirmRevokeAdminDialog(
            memberPubkey = member.pubkey,
            accountViewModel = accountViewModel,
            onConfirm = {
                memberToDemote = null
                scope.launch(Dispatchers.IO) {
                    try {
                        accountViewModel.revokeMarmotGroupAdmin(nostrGroupId, member.pubkey)
                        launch(Dispatchers.Main) {
                            Toast
                                .makeText(context, stringRes(context, R.string.marmot_admin_revoked), Toast.LENGTH_SHORT)
                                .show()
                        }
                    } catch (e: Exception) {
                        launch(Dispatchers.Main) {
                            Toast
                                .makeText(
                                    context,
                                    stringRes(context, R.string.marmot_failed_to_revoke_admin, e.message),
                                    Toast.LENGTH_LONG,
                                ).show()
                        }
                    }
                }
            },
            onDismiss = { memberToDemote = null },
        )
    }
}

@Composable
private fun AddMemberInline(
    nostrGroupId: HexKey,
    searchInput: String,
    onSearchInputChange: (String) -> Unit,
    userSuggestions: UserSuggestionState,
    statusMessage: String?,
    isError: Boolean,
    isAdding: Boolean,
    accountViewModel: AccountViewModel,
    onAdd: (com.vitorpamplona.amethyst.model.User) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!isAdding && searchInput.length > 2) {
            ShowUserSuggestionList(
                userSuggestions = userSuggestions,
                onSelect = { user -> onAdd(user) },
                accountViewModel = accountViewModel,
                modifier = SuggestionListDefaultHeightChat,
                onEmpty = {
                    Text(
                        stringRes(R.string.marmot_keypackage_required),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingContent = { user ->
                    IconButton(onClick = { onAdd(user) }) {
                        Icon(
                            symbol = MaterialSymbols.PersonAdd,
                            contentDescription = stringRes(R.string.marmot_add_to_group),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
            HorizontalDivider()
        }

        if (statusMessage != null) {
            Text(
                text = statusMessage,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
            )
        }

        OutlinedTextField(
            value = searchInput,
            onValueChange = onSearchInputChange,
            label = { Text(stringRes(R.string.marmot_add_member)) },
            placeholder = { Text(stringRes(R.string.marmot_add_member_placeholder)) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            enabled = !isAdding,
        )
    }
}

@Composable
fun MemberRow(
    member: GroupMemberInfo,
    isMe: Boolean,
    isAdmin: Boolean,
    canRemove: Boolean,
    canPromote: Boolean,
    canDemote: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
    onRemoveClick: () -> Unit,
    onPromoteClick: () -> Unit,
    onDemoteClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { nav.nav(Route.Profile(member.pubkey)) }
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
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
                val displayName = user?.toBestDisplayName() ?: stringRes(R.string.marmot_user_fallback_name, member.pubkey.take(16))
                val youSuffix = stringRes(R.string.marmot_member_suffix_you)
                val adminSuffix = stringRes(R.string.marmot_member_suffix_admin)
                val suffix =
                    buildString {
                        if (isMe) append(youSuffix)
                        if (isAdmin) append(adminSuffix)
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
        if (canPromote) {
            IconButton(onClick = onPromoteClick) {
                Icon(
                    symbol = MaterialSymbols.StarBorder,
                    contentDescription = stringRes(R.string.marmot_grant_admin_privileges),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else if (canDemote) {
            IconButton(onClick = onDemoteClick) {
                Icon(
                    symbol = MaterialSymbols.Star,
                    contentDescription = stringRes(R.string.marmot_revoke_admin_privileges),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (canRemove) {
            IconButton(onClick = onRemoveClick) {
                Icon(
                    symbol = MaterialSymbols.PersonRemove,
                    contentDescription = stringRes(R.string.marmot_remove_member),
                    tint = MaterialTheme.colorScheme.error,
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
        title = { Text(stringRes(R.string.marmot_leave_group)) },
        text = {
            Text(stringRes(R.string.marmot_leave_group_confirm, groupName))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringRes(R.string.leave), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.cancel))
            }
        },
    )
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
        title = { Text(stringRes(R.string.marmot_remove_member)) },
        text = {
            LoadUser(baseUserHex = memberPubkey, accountViewModel = accountViewModel) { user ->
                val name = user?.toBestDisplayName() ?: stringRes(R.string.marmot_user_fallback_name, memberPubkey.take(16))
                Text(stringRes(R.string.marmot_remove_member_confirm, name))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringRes(R.string.remove), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ConfirmGrantAdminDialog(
    memberPubkey: HexKey,
    accountViewModel: AccountViewModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.marmot_grant_admin_title)) },
        text = {
            LoadUser(baseUserHex = memberPubkey, accountViewModel = accountViewModel) { user ->
                val name = user?.toBestDisplayName() ?: stringRes(R.string.marmot_user_fallback_name, memberPubkey.take(16))
                Text(stringRes(R.string.marmot_grant_admin_confirm, name))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringRes(R.string.marmot_grant))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ConfirmRevokeAdminDialog(
    memberPubkey: HexKey,
    accountViewModel: AccountViewModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.marmot_revoke_admin_title)) },
        text = {
            LoadUser(baseUserHex = memberPubkey, accountViewModel = accountViewModel) { user ->
                val name = user?.toBestDisplayName() ?: stringRes(R.string.marmot_user_fallback_name, memberPubkey.take(16))
                Text(stringRes(R.string.marmot_revoke_admin_confirm, name))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringRes(R.string.marmot_revoke), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.cancel))
            }
        },
    )
}

/** Window (in seconds) within which a relay is considered actively carrying this group's traffic. */
private const val RELAY_ACTIVITY_WINDOW_SECS = 7L * 24 * 60 * 60

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupRelayStrip(
    relayUrls: List<String>,
    relayActivity: Map<NormalizedRelayUrl, Long>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val normalized =
        remember(relayUrls) {
            relayUrls.mapNotNull { url -> url.normalizeRelayUrlOrNull()?.let { url to it } }
        }

    if (normalized.isEmpty()) return

    FlowRow(
        modifier = Modifier.padding(start = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val nowSeconds = System.currentTimeMillis() / 1000L
        normalized.forEach { (raw, relay) ->
            val lastSeen = relayActivity[relay]
            val isActive = lastSeen != null && (nowSeconds - lastSeen) <= RELAY_ACTIVITY_WINDOW_SECS
            GroupRelayTile(
                relay = relay,
                fallbackUrl = raw,
                isActive = isActive,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupRelayTile(
    relay: NormalizedRelayUrl,
    fallbackUrl: String,
    isActive: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val relayInfo by loadRelayInfo(relay)
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val clickableModifier =
        remember(relay) {
            Modifier
                .size(35.dp)
                .combinedClickable(
                    indication = ripple24dp,
                    interactionSource = MutableInteractionSource(),
                    onLongClick = {
                        scope.launch {
                            clipboardManager.setText(relay.url)
                        }
                    },
                    onClick = { nav.nav(Route.RelayInfo(relay.url)) },
                )
        }

    Box(
        modifier = clickableModifier,
        contentAlignment = Alignment.Center,
    ) {
        RenderRelayIcon(
            displayUrl = relayInfo.id ?: fallbackUrl,
            iconUrl = relayInfo.icon,
            loadProfilePicture = accountViewModel.settings.showProfilePictures(),
            pingInMs = 0,
            loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
            iconModifier = MediumRelayIconModifier,
        )

        val dotColor =
            if (isActive) {
                MaterialTheme.colorScheme.allGoodColor
            } else {
                MaterialTheme.colorScheme.placeholderText
            }

        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(1.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor),
            )
        }
    }
}

/**
 * Detailed per-relay listing showing URL, connection-status dot, and the
 * freshness of the most recent kind:445 group event we've received from
 * each relay. Complements the compact [GroupRelayStrip] at the top of the
 * screen — that one is an at-a-glance visual; this one surfaces the
 * underlying data so users can diagnose which relay is lagging.
 */
@Composable
fun RelayHealthSection(
    relayUrls: List<String>,
    relayActivity: Map<NormalizedRelayUrl, Long>,
    nav: INav,
) {
    val normalized =
        remember(relayUrls) {
            relayUrls.mapNotNull { url -> url.normalizeRelayUrlOrNull()?.let { url to it } }
        }
    if (normalized.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringRes(R.string.marmot_relays_header),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        val nowSeconds = System.currentTimeMillis() / 1000L
        normalized.forEach { (raw, relay) ->
            val lastSeen = relayActivity[relay]
            val isActive = lastSeen != null && (nowSeconds - lastSeen) <= RELAY_ACTIVITY_WINDOW_SECS
            RelayHealthRow(
                relay = relay,
                fallbackUrl = raw,
                lastSeen = lastSeen,
                isActive = isActive,
                nav = nav,
            )
        }
    }
}

@Composable
private fun RelayHealthRow(
    relay: NormalizedRelayUrl,
    fallbackUrl: String,
    lastSeen: Long?,
    isActive: Boolean,
    nav: INav,
) {
    val context = LocalContext.current
    val dotColor =
        if (isActive) {
            MaterialTheme.colorScheme.allGoodColor
        } else {
            MaterialTheme.colorScheme.placeholderText
        }
    val subtitle =
        if (lastSeen == null) {
            stringRes(R.string.marmot_relay_no_events)
        } else {
            stringRes(R.string.marmot_relay_last_event, timeAgo(lastSeen, context))
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { nav.nav(Route.RelayInfo(relay.url)) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
        )
        Column(
            modifier =
                Modifier
                    .padding(start = 12.dp)
                    .weight(1f),
        ) {
            Text(
                text = relay.url.ifEmpty { fallbackUrl },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
