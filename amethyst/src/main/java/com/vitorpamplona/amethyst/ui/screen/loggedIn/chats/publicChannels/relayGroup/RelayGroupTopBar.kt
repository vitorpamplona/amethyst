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

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupMembership
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.channel.observeChannel
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
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

    TopBarExtensibleWithBackButton(
        title = {
            Column {
                Text(
                    text = channel.toBestDisplayName(),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle(channel.groupId.relayUrl.displayUrl(), memberCount, displayMembership),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                        requested = true
                        accountViewModel.joinRelayGroup(channel)
                    }) {
                        Text(stringRes(R.string.join))
                    }
                }

                else -> {
                    IconButton(onClick = { menuOpen = true }) {
                        Text("⋮", style = MaterialTheme.typography.titleLarge)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
}

@Composable
private fun subtitle(
    relayHost: String,
    memberCount: Int,
    membership: RelayGroupMembership,
): String {
    val parts = mutableListOf(relayHost)
    if (memberCount > 0) parts.add(pluralStringResource(R.plurals.relay_group_member_count, memberCount, memberCount))
    val role =
        when (membership) {
            RelayGroupMembership.ADMIN -> stringRes(R.string.relay_group_role_admin)
            RelayGroupMembership.MODERATOR -> stringRes(R.string.relay_group_role_moderator)
            RelayGroupMembership.MEMBER -> stringRes(R.string.relay_group_role_member)
            RelayGroupMembership.PENDING -> stringRes(R.string.relay_group_pending)
            RelayGroupMembership.NONE -> null
        }
    role?.let { parts.add(it) }
    return parts.joinToString(" · ")
}
