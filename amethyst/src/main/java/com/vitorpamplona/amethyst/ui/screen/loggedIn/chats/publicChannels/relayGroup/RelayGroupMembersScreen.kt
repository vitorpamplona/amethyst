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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupMembership
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.channel.observeChannel
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupPreviewSubscription
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId

/**
 * The roster of a NIP-29 group: everyone the relay lists as an admin (kind 39001)
 * or member (kind 39002), each with their role badge. A moderator (admin or
 * moderator role) additionally gets a per-user overflow menu to promote/demote
 * (kind 9000 put-user) and kick (kind 9001 remove-user); the host relay enforces
 * the actual permission, this UI just surfaces the actions.
 */
@Composable
fun RelayGroupMembersScreen(
    id: HexKey,
    relayUrl: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val relay = remember(relayUrl) { RelayUrlNormalizer.normalizeOrNull(relayUrl) } ?: return
    val channelId = remember(id, relay) { GroupId(id, relay) }

    LoadRelayGroupChannel(channelId, accountViewModel) { channel ->
        RelayGroupMembers(channel, accountViewModel, nav)
    }
}

private class RosterEntry(
    val pubkey: HexKey,
    val membership: RelayGroupMembership,
)

@Composable
private fun RelayGroupMembers(
    baseChannel: RelayGroupChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Fetch the relay-signed roster (kinds 39001/39002) from the host relay while this
    // standalone screen is open. observeChannel alone does NOT do this for a relay group
    // (its finder only handles public-chat / live-activity channels), so without this the
    // screen would only show whatever the chat screen happened to cache.
    RelayGroupPreviewSubscription(baseChannel, accountViewModel.dataSources().relayGroupPreview, accountViewModel)

    // Recompose when the relay-signed roster (39001/39002) changes.
    val channelState by observeChannel(baseChannel, accountViewModel)
    val channel = channelState?.channel as? RelayGroupChannel ?: baseChannel

    val myPubkey = accountViewModel.userProfile().pubkeyHex
    val iCanModerate = channel.membershipOf(myPubkey).canModerate()
    val iAmAdmin = channel.membershipOf(myPubkey) == RelayGroupMembership.ADMIN

    // Admins/moderators first, then plain members; alphabetical only inside a rank
    // is overkill here — rely on relay order but push elevated roles to the top.
    val roster =
        remember(channel.admins, channel.members) {
            val everyone = (channel.admins.map { it.pubKey } + channel.members).distinct()
            everyone
                .map { RosterEntry(it, channel.membershipOf(it)) }
                .sortedBy { it.membership.rank() }
        }

    Scaffold(
        topBar = {
            TopBarExtensibleWithBackButton(
                title = {
                    Column {
                        Text(
                            text = stringRes(R.string.relay_group_members_title),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = channel.toBestDisplayName(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                popBack = nav::popBack,
            )
        },
    ) { padding ->
        if (roster.isEmpty()) {
            // The roster always has at least the group's admins once it loads, so an
            // empty list means the kind-39001/39002 events haven't arrived yet.
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                itemsIndexed(roster, key = { _, entry -> entry.pubkey }) { index, entry ->
                    if (index > 0) {
                        HorizontalDivider(thickness = 0.25.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    RelayGroupMemberRow(
                        entry = entry,
                        channel = channel,
                        isSelf = entry.pubkey == myPubkey,
                        viewerCanModerate = iCanModerate,
                        viewerIsAdmin = iAmAdmin,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        }
    }
}

/** Sort key so admins float above moderators above plain members. */
private fun RelayGroupMembership.rank(): Int =
    when (this) {
        RelayGroupMembership.ADMIN -> 0
        RelayGroupMembership.MODERATOR -> 1
        else -> 2
    }

@Composable
private fun RelayGroupMemberRow(
    entry: RosterEntry,
    channel: RelayGroupChannel,
    isSelf: Boolean,
    viewerCanModerate: Boolean,
    viewerIsAdmin: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Create-or-get (never a one-shot null): UsernameDisplay observes the user's
    // metadata flow, so the name fills in when the kind:0 arrives instead of being
    // stuck on truncated hex forever.
    val user = remember(entry.pubkey) { accountViewModel.checkGetOrCreateUser(entry.pubkey) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UserPicture(entry.pubkey, Size35dp, accountViewModel = accountViewModel, nav = nav)

        Column(Modifier.weight(1f)) {
            if (user != null) {
                UsernameDisplay(user, accountViewModel = accountViewModel)
            } else {
                Text(
                    text = entry.pubkey.take(8),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        MemberRoleBadge(entry.membership)

        // Moderators can act on others (not themselves); the relay is the final
        // authority, but hide obviously-useless menus (a moderator can't touch an admin).
        val canActOnTarget =
            viewerCanModerate &&
                !isSelf &&
                (viewerIsAdmin || entry.membership != RelayGroupMembership.ADMIN)

        if (canActOnTarget) {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    symbol = MaterialSymbols.MoreVert,
                    contentDescription = stringRes(R.string.more_options),
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (viewerIsAdmin && entry.membership != RelayGroupMembership.ADMIN) {
                    DropdownMenuItem(
                        text = { Text(stringRes(R.string.relay_group_make_admin)) },
                        onClick = {
                            menuOpen = false
                            accountViewModel.putRelayGroupUser(channel, entry.pubkey, listOf(RelayGroupMembership.ROLE_ADMIN))
                        },
                    )
                }
                if (entry.membership != RelayGroupMembership.MODERATOR && entry.membership != RelayGroupMembership.ADMIN) {
                    DropdownMenuItem(
                        text = { Text(stringRes(R.string.relay_group_make_moderator)) },
                        onClick = {
                            menuOpen = false
                            accountViewModel.putRelayGroupUser(channel, entry.pubkey, listOf(RelayGroupMembership.ROLE_MODERATOR))
                        },
                    )
                }
                if (entry.membership == RelayGroupMembership.MODERATOR || entry.membership == RelayGroupMembership.ADMIN) {
                    DropdownMenuItem(
                        text = { Text(stringRes(R.string.relay_group_demote_member)) },
                        onClick = {
                            menuOpen = false
                            accountViewModel.putRelayGroupUser(channel, entry.pubkey, emptyList())
                        },
                    )
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringRes(R.string.relay_group_remove_user),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        menuOpen = false
                        confirmRemove = true
                    },
                )
            }
        }
    }

    if (confirmRemove) {
        val displayName = user?.toBestDisplayName() ?: entry.pubkey.take(8)
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringRes(R.string.relay_group_remove_user)) },
            text = { Text(stringRes(R.string.relay_group_remove_user_confirm, displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    accountViewModel.removeRelayGroupUser(channel, entry.pubkey)
                }) {
                    Text(stringRes(R.string.relay_group_remove_user), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) {
                    Text(stringRes(R.string.cancel))
                }
            },
        )
    }
}

/** A small colored pill for an elevated role; plain members get nothing. */
@Composable
private fun MemberRoleBadge(membership: RelayGroupMembership) {
    val label =
        when (membership) {
            RelayGroupMembership.ADMIN -> stringRes(R.string.relay_group_role_admin)
            RelayGroupMembership.MODERATOR -> stringRes(R.string.relay_group_role_moderator)
            else -> return
        }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
