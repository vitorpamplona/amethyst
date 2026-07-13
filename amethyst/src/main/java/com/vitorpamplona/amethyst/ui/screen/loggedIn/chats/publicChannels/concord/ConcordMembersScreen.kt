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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.concord.ConcordMembership
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.datasource.ConcordChannelSubscription
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.MutableStateFlow
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * The members roster of one Concord community — the analog of NIP-29's
 * `RelayGroupMembersScreen`. Concord has no relay-signed roster (membership is key
 * possession), so this shows the *privileged* roster derivable from the folded
 * Control Plane: the owner, every role-holder (admins/moderators), and banned
 * users. The overflow menu offers promote/demote (owner) and ban/unban, gated on
 * the viewer's authority exactly as the write path enforces on fold.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConcordMembersScreen(
    communityId: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val account = accountViewModel.account

    // Self-sufficient: mount the Concord plane subscription so the community folds even when this
    // screen is opened directly (deep link), not only from the hub.
    ConcordChannelSubscription(accountViewModel.dataSources().concordChannels, accountViewModel)

    // Re-resolve the session as sessions are created/folded (revision-keyed), so a deep link that
    // lands before the community's session exists still picks it up once it does.
    val revision by account.concordSessions.revision.collectAsStateWithLifecycle()
    val session = remember(account, communityId, revision) { account.concordSessions.sessionFor(communityId) }
    val state by (session?.state ?: remember { MutableStateFlow(null) }).collectAsStateWithLifecycle()

    val myPubKey = account.signer.pubKey
    val roster =
        remember(state) {
            val s = state ?: return@remember emptyList<RosterEntry>()
            val authority = s.authority
            val pubkeys = (listOf(s.ownerPubKey) + authority.roleHolders() + authority.bannedMembers()).map { it.lowercase() }.distinct()
            pubkeys
                .map { RosterEntry(it, ConcordMembership.of(authority, it)) }
                .sortedWith(compareBy({ it.membership.sortRank() }, { it.pubkey }))
        }

    val iAmOwner = state?.authority?.isOwner(myPubKey) == true
    val iCanBan = state?.let { it.authority.isOwner(myPubKey) || it.authority.effectivePermissions(myPubKey).has(ConcordPermissions.BAN) } == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringRes(R.string.concord_members_title), fontWeight = FontWeight.Bold, maxLines = 1)
                        state?.metadata?.name?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) {
                        SymbolIcon(symbol = MaterialSymbols.AutoMirrored.ArrowBack, contentDescription = stringRes(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        if (roster.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringRes(R.string.concord_members_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(roster, key = { it.pubkey }) { entry ->
                    ConcordMemberRow(
                        entry = entry,
                        communityId = communityId,
                        isSelf = entry.pubkey.equals(myPubKey, ignoreCase = true),
                        viewerIsOwner = iAmOwner,
                        viewerCanBan = iCanBan,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                    HorizontalDivider(thickness = 0.25.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun ConcordMemberRow(
    entry: RosterEntry,
    communityId: String,
    isSelf: Boolean,
    viewerIsOwner: Boolean,
    viewerCanBan: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val user = remember(entry.pubkey) { accountViewModel.checkGetOrCreateUser(entry.pubkey) }
    val isOwnerTarget = entry.membership == ConcordMembership.OWNER
    val isBanned = entry.membership == ConcordMembership.BANNED
    val isAdmin = entry.membership == ConcordMembership.ADMIN

    // Owner can promote/demote anyone but the owner; ban is available to owner + BAN holders,
    // never against the owner or yourself. A banned user only offers "unban".
    val canToggleAdmin = viewerIsOwner && !isOwnerTarget && !isBanned && !isSelf
    val canBan = viewerCanBan && !isOwnerTarget && !isSelf
    // Hard removal (CORD-06 Refounding) rotates the community key; same authority as ban.
    val canRemove = viewerCanBan && !isOwnerTarget && !isSelf
    val hasMenu = canToggleAdmin || canBan || canRemove

    var confirmRemove by remember { mutableStateOf(false) }
    if (confirmRemove) {
        ConcordRemoveMemberDialog(
            onConfirm = {
                accountViewModel.removeConcordMember(communityId, entry.pubkey)
                confirmRemove = false
            },
            onDismiss = { confirmRemove = false },
        )
    }

    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UserPicture(entry.pubkey, Size35dp, accountViewModel = accountViewModel, nav = nav)
        Column(Modifier.weight(1f)) {
            if (user != null) {
                UsernameDisplay(user, accountViewModel = accountViewModel)
            } else {
                Text(entry.pubkey.take(8), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        MemberBadge(entry.membership)
        if (hasMenu) {
            var expanded by remember { mutableStateOf(false) }
            IconButton(onClick = { expanded = true }) {
                SymbolIcon(symbol = MaterialSymbols.MoreVert, contentDescription = stringRes(R.string.more_options))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (canToggleAdmin) {
                    DropdownMenuItem(
                        text = { Text(stringRes(if (isAdmin) R.string.concord_members_remove_admin else R.string.concord_members_make_admin)) },
                        onClick = {
                            accountViewModel.setConcordAdmin(communityId, entry.pubkey, makeAdmin = !isAdmin)
                            expanded = false
                        },
                    )
                }
                if (canBan) {
                    DropdownMenuItem(
                        text = { Text(stringRes(if (isBanned) R.string.concord_members_unban else R.string.concord_members_ban)) },
                        onClick = {
                            accountViewModel.setConcordBan(communityId, entry.pubkey, ban = !isBanned)
                            expanded = false
                        },
                    )
                }
                if (canRemove) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringRes(R.string.concord_members_remove),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            confirmRemove = true
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/** A small pill labelling the member's standing (owner / admin / banned; plain members render nothing). */
@Composable
private fun MemberBadge(membership: ConcordMembership) {
    val label =
        when (membership) {
            ConcordMembership.OWNER -> stringRes(R.string.concord_role_owner)
            ConcordMembership.ADMIN -> stringRes(R.string.concord_role_admin)
            ConcordMembership.BANNED -> stringRes(R.string.concord_role_banned)
            else -> return
        }
    val container = if (membership == ConcordMembership.BANNED) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val content = if (membership == ConcordMembership.BANNED) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Surface(shape = RoundedCornerShape(6.dp), color = container) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Confirms a hard removal — spells out that it rotates the community key (CORD-06). */
@Composable
private fun ConcordRemoveMemberDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.concord_members_remove_title)) },
        text = { Text(stringRes(R.string.concord_members_remove_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringRes(R.string.concord_members_remove_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(R.string.cancel)) }
        },
    )
}

private class RosterEntry(
    val pubkey: HexKey,
    val membership: ConcordMembership,
)

/** Owner first, then admins, then plain members, then banned last. */
private fun ConcordMembership.sortRank(): Int =
    when (this) {
        ConcordMembership.OWNER -> 0
        ConcordMembership.ADMIN -> 1
        ConcordMembership.MEMBER -> 2
        ConcordMembership.NONE -> 3
        ConcordMembership.BANNED -> 4
    }
