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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl

@Composable
fun RelayGroupTopBar(
    baseChannel: RelayGroupChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Recompose when the relay-signed metadata / roster changes.
    val channelState by observeChannel(baseChannel, accountViewModel)
    val channel = channelState?.channel as? RelayGroupChannel ?: baseChannel

    val myPubkey = accountViewModel.userProfile().pubkeyHex
    val membership = channel.membershipOf(myPubkey)
    val memberCount = channel.memberCount()

    // Optimistic "requested" state: set on Join, cleared once the relay's roster
    // shows us as a member. Never persisted — a fresh visit reads the relay truth.
    var requested by remember(channel.groupId) { mutableStateOf(false) }
    LaunchedEffect(membership) {
        if (membership.isMember()) requested = false
    }
    val displayMembership = if (!membership.isMember() && requested) RelayGroupMembership.PENDING else membership

    var menuOpen by remember { mutableStateOf(false) }
    var showInvite by remember { mutableStateOf(false) }
    var showJoinCode by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }

    TopBarExtensibleWithBackButton(
        title = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = channel.toBestDisplayName(),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    RoleBadge(displayMembership)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (channel.isPrivate()) {
                        Icon(
                            symbol = MaterialSymbols.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                    Text(
                        text = channel.groupId.relayUrl.displayUrl(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (memberCount > 0) {
                        Icon(
                            symbol = MaterialSymbols.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = "$memberCount",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        actions = {
            when {
                displayMembership == RelayGroupMembership.PENDING -> {
                    Text(
                        text = stringRes(R.string.relay_group_pending),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                !displayMembership.isMember() -> {
                    FilledTonalButton(onClick = {
                        // Closed groups need an invite code; open groups join directly.
                        if (channel.isClosed()) {
                            showJoinCode = true
                        } else {
                            requested = true
                            accountViewModel.joinRelayGroup(channel)
                        }
                    }) {
                        Text(stringRes(R.string.join))
                    }
                }

                else -> {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            symbol = MaterialSymbols.MoreVert,
                            contentDescription = stringRes(R.string.more_options),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringRes(R.string.relay_group_menu_members)) },
                            onClick = {
                                menuOpen = false
                                nav.nav(Route.RelayGroupMembers(channel.groupId.id, channel.groupId.relayUrl.url))
                            },
                        )
                        if (displayMembership == RelayGroupMembership.ADMIN) {
                            DropdownMenuItem(
                                text = { Text(stringRes(R.string.relay_group_menu_edit)) },
                                onClick = {
                                    menuOpen = false
                                    showEdit = true
                                },
                            )
                        }
                        if (displayMembership.canModerate()) {
                            DropdownMenuItem(
                                text = { Text(stringRes(R.string.relay_group_invite_title)) },
                                onClick = {
                                    menuOpen = false
                                    showInvite = true
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringRes(R.string.leave)) },
                            onClick = {
                                menuOpen = false
                                accountViewModel.leaveRelayGroup(channel)
                            },
                        )
                    }
                }
            }
        },
        popBack = nav::popBack,
    )

    if (showInvite) {
        InviteRelayGroupDialog(channel, accountViewModel) { showInvite = false }
    }

    if (showEdit) {
        EditRelayGroupDialog(channel, accountViewModel) { showEdit = false }
    }

    if (showJoinCode) {
        JoinRelayGroupDialog(
            channel = channel,
            accountViewModel = accountViewModel,
            onJoined = { requested = true },
            onDismiss = { showJoinCode = false },
        )
    }
}

/** A small colored pill naming the user's role/status in the group. */
@Composable
private fun RoleBadge(membership: RelayGroupMembership) {
    val label =
        when (membership) {
            RelayGroupMembership.ADMIN -> stringRes(R.string.relay_group_role_admin)
            RelayGroupMembership.MODERATOR -> stringRes(R.string.relay_group_role_moderator)
            RelayGroupMembership.PENDING -> stringRes(R.string.relay_group_pending)
            // A plain member needs no badge; the lack of a Join button already says it.
            RelayGroupMembership.MEMBER, RelayGroupMembership.NONE -> return
        }

    val container =
        if (membership == RelayGroupMembership.ADMIN || membership == RelayGroupMembership.MODERATOR) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
    val content =
        if (membership == RelayGroupMembership.ADMIN || membership == RelayGroupMembership.MODERATOR) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }

    Surface(shape = RoundedCornerShape(6.dp), color = container) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
