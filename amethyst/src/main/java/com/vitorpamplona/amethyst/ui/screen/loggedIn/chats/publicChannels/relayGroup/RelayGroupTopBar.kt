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

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzDmRegistry
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzWorkspaceStates
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupMembership
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.buzz_agent_work_title
import com.vitorpamplona.amethyst.commons.resources.buzz_canvas_title
import com.vitorpamplona.amethyst.commons.resources.join
import com.vitorpamplona.amethyst.commons.resources.relay_group_invite_title
import com.vitorpamplona.amethyst.commons.resources.relay_group_menu_edit
import com.vitorpamplona.amethyst.commons.resources.relay_group_menu_members
import com.vitorpamplona.amethyst.commons.resources.relay_group_pending
import com.vitorpamplona.amethyst.commons.resources.relay_group_role_admin
import com.vitorpamplona.amethyst.commons.resources.relay_group_role_moderator
import com.vitorpamplona.amethyst.commons.resources.relay_group_threads_title
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.channel.observeChannel
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserName
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.ui.note.njumpLink
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.buzz.workspace.buzzParticipants
import com.vitorpamplona.quartz.buzz.workspace.isBuzzDm
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl

@Composable
fun RelayGroupTopBar(
    baseChannel: RelayGroupChannel,
    inviteCode: String? = null,
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

    // Arrived via an invite link carrying a join code (`wss://relay'id?code=…`): the
    // user already opted in by tapping it, so fire the kind-9021 join once (with the
    // code) instead of making them re-enter it. Guarded to exactly once and skipped if
    // the roster already shows us as a member.
    var autoJoined by remember(channel.groupId) { mutableStateOf(false) }
    LaunchedEffect(channel.groupId, inviteCode, membership) {
        if (inviteCode != null && !autoJoined && !membership.isMember()) {
            autoJoined = true
            requested = true
            accountViewModel.joinRelayGroup(channel, inviteCode)
        }
    }
    val displayMembership = if (!membership.isMember() && requested) RelayGroupMembership.PENDING else membership

    // A Buzz DM is a private 1:1 conversation: title it by the OTHER participant (not the generic "DM"
    // metadata name), and drop the forum-threads + share affordances — a DM has no forum, and its
    // private, membership-gated naddr is meaningless to hand out.
    val isDm = channel.event?.isBuzzDm() == true
    val dmOther = if (isDm) channel.event?.buzzParticipants()?.firstOrNull { it != myPubkey } else null

    var menuOpen by remember { mutableStateOf(false) }
    // My kind-10009 list, live: drives the Add/Remove-from-Messages toggle in the overflow below.
    val joinedGroupIds by accountViewModel.account.relayGroupList.liveRelayGroupIds
        .collectAsStateWithLifecycle()
    // A Buzz DM is hidden via its own per-viewer 30622 snapshot, not the kind-10009 list, so the
    // Messages toggle branches on this for DMs.
    val hiddenDms by BuzzDmRegistry.hidden.collectAsStateWithLifecycle()
    // Read once here (nav.canPop() is @Composable) so the post-action navigation can pop from a menu
    // callback — leaving a group shouldn't strand the user on the screen of a group they left.
    val canPop = nav.canPop()
    var showInvite by remember { mutableStateOf(false) }
    var showJoinCode by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val isBuzzRelay = remember(channel.groupId.relayUrl) { BuzzRelayDialect.isBuzz(channel.groupId.relayUrl) }

    TopBarExtensibleWithBackButton(
        title = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (dmOther != null) {
                        DmParticipantTitle(dmOther, channel, accountViewModel, Modifier.weight(1f, fill = false))
                    } else {
                        Text(
                            text = channel.toBestDisplayName(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
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
            // Buzz canvas (kind 40100): shown on any Buzz-dialect relay so a member can open the
            // channel's shared markdown doc — or create one when it has none yet. The only affordance
            // that stays an icon: it is this channel's shared document, i.e. content, while Threads and
            // Share are navigation the reader needs once in a while.
            //
            // Except on a DM, where Buzz itself never offers to *write* one: its canvas entry needs
            // `hasCanvas || canEditNarrative`, and `canEditNarrative` excludes `channelType === "dm"`
            // outright. So a DM shows the icon only when a canvas already exists — which is also what
            // keeps us from advertising "start a shared doc" in a two-person conversation.
            val hasCanvas by observeBuzzCanvas(channel.groupId.id)
            if (isBuzzRelay && (!isDm || hasCanvas)) {
                IconButton(onClick = { nav.nav(Route.BuzzCanvas(channel.groupId.id, channel.groupId.relayUrl.url)) }) {
                    Icon(
                        symbol = MaterialSymbols.Dashboard,
                        contentDescription = stringRes(Res.string.buzz_canvas_title),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // remember the bech32 (naddr) encode — this top bar recomposes on every roster/metadata
            // emission (observeChannel), and the encode is otherwise redone each time.
            val naddr = remember(channel.groupId, isDm) { if (isDm) null else channel.toNAddr() }

            if (displayMembership == RelayGroupMembership.PENDING) {
                Text(
                    text = stringRes(Res.string.relay_group_pending),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (!displayMembership.isMember() && channel.requiresMembershipToPost()) {
                FilledTonalButton(onClick = {
                    // Closed groups need an invite code; open groups join directly.
                    if (channel.isClosed()) {
                        showJoinCode = true
                    } else {
                        requested = true
                        accountViewModel.joinRelayGroup(channel)
                    }
                }) {
                    Text(stringRes(Res.string.join))
                }
            }

            // The membership actions are only meaningful once the relay lets you in: while a join is
            // pending, and on a gated group that doesn't list you, the Join affordance above stands in
            // for them. Threads and Share stay available either way — you can want to hand out a group
            // you are still only browsing.
            val showMembershipActions =
                displayMembership != RelayGroupMembership.PENDING &&
                    !(!displayMembership.isMember() && channel.requiresMembershipToPost())

            // Everything here used to be a top-bar icon. Threads especially over-advertised itself: on a
            // Buzz `t=stream` channel it is always empty (forum posts live in `t=forum` channels, which
            // the relay's channel list already surfaces in their own section), so it read as a broken
            // feature on every chat. Demoted to the overflow, where the frequency of use actually is.
            // A Buzz DM always gets the overflow too — its hide/unhide (below) is the DM row's old
            // action, and it must be reachable even where the membership actions aren't offered.
            if (!isDm || naddr != null || showMembershipActions || (isDm && isBuzzRelay)) {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        symbol = MaterialSymbols.MoreVert,
                        contentDescription = stringRes(R.string.more_options),
                        modifier = Modifier.size(22.dp),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    // Pin/Unpin moved here off the community-list row. A local favorite, so it's offered
                    // for any Buzz channel/forum regardless of membership; DMs are never pinned.
                    if (isBuzzRelay && !isDm) {
                        BuzzPinDropdownItem(channel.groupId, accountViewModel) { menuOpen = false }
                    }
                    // A DM's Add/Remove-from-Messages, moved off the DM list row. It rides the per-viewer
                    // 30622 hide snapshot (kind-41012 hide / re-open), not the kind-10009 list, and is
                    // shown regardless of the membership gate below.
                    if (isBuzzRelay && isDm) {
                        val dmHidden = channel.groupId.id in (hiddenDms[myPubkey] ?: emptySet())
                        DropdownMenuItem(
                            text = { Text(stringRes(if (dmHidden) R.string.add_to_messages else R.string.remove_from_messages)) },
                            onClick = {
                                menuOpen = false
                                if (dmHidden) {
                                    val participants =
                                        channel.event
                                            ?.buzzParticipants()
                                            ?.filter { it != myPubkey }
                                            .orEmpty()
                                    accountViewModel.unhideBuzzDm(channel.groupId.relayUrl, participants.ifEmpty { listOf(myPubkey) })
                                } else {
                                    accountViewModel.hideBuzzDm(channel)
                                }
                            },
                        )
                    }
                    if (!isDm) {
                        DropdownMenuItem(
                            text = { Text(stringRes(Res.string.relay_group_threads_title)) },
                            onClick = {
                                menuOpen = false
                                nav.nav(Route.RelayGroupThreads(channel.groupId.id, channel.groupId.relayUrl.url))
                            },
                        )
                    }
                    // The agent surface: this channel's job backlog (43001-43006) and workflow runs
                    // (46020 + lifecycle) folded into one **Agent work** board, where the human-approval
                    // gate is an inline card state. A menu entry rather than an icon — it's a view of
                    // the channel reached occasionally, and icons pushed the bar back to four, truncating
                    // the channel name and relay the title row is there to show.
                    if (isBuzzRelay && !isDm) {
                        DropdownMenuItem(
                            text = { Text(stringRes(Res.string.buzz_agent_work_title)) },
                            onClick = {
                                menuOpen = false
                                nav.nav(Route.BuzzAgentWork(channel.groupId.id, channel.groupId.relayUrl.url))
                            },
                        )
                    }
                    if (naddr != null) {
                        val context = LocalContext.current
                        DropdownMenuItem(
                            text = { Text(stringRes(R.string.quick_action_share)) },
                            onClick = {
                                menuOpen = false
                                shareRelayGroup(context, naddr)
                            },
                        )
                    }
                    if (showMembershipActions) {
                        DropdownMenuItem(
                            text = { Text(stringRes(Res.string.relay_group_menu_members)) },
                            onClick = {
                                menuOpen = false
                                nav.nav(Route.RelayGroupMembers(channel.groupId.id, channel.groupId.relayUrl.url))
                            },
                        )
                        if (displayMembership == RelayGroupMembership.ADMIN) {
                            DropdownMenuItem(
                                text = { Text(stringRes(Res.string.relay_group_menu_edit)) },
                                onClick = {
                                    menuOpen = false
                                    nav.nav(Route.RelayGroupEdit(channel.groupId.id, channel.groupId.relayUrl.url))
                                },
                            )
                        }
                        if (displayMembership.canModerate()) {
                            DropdownMenuItem(
                                text = { Text(stringRes(Res.string.relay_group_invite_title)) },
                                onClick = {
                                    menuOpen = false
                                    showInvite = true
                                },
                            )
                        }
                        // Two distinct actions, never conflated: the Messages toggle adds/drops the group
                        // on my kind-10009 list but keeps my relay membership either way; "Leave" sends
                        // the kind-9022 that actually removes me. Same split as the channel-invite card.
                        // (A DM's Messages toggle is the hide/unhide item above — it rides a different
                        // mechanism and must show even when these membership actions don't.)
                        //
                        // Reads the live kind-10009 list rather than assuming the group is on it: this
                        // bar also opens for channels reached from the workspace browse (a Buzz relay
                        // lists every channel you're a member of, joined or not) and for ones you removed
                        // earlier — both need the "Add" half. And because it's a reversible toggle, remove
                        // does NOT pop back: you're still a member reading the channel, and staying is
                        // what makes the entry flip so the action is visibly undoable. Leave still pops.
                        val onMyList = channel.groupId in joinedGroupIds
                        DropdownMenuItem(
                            text = { Text(stringRes(if (onMyList) R.string.remove_from_messages else R.string.add_to_messages)) },
                            onClick = {
                                menuOpen = false
                                if (onMyList) {
                                    accountViewModel.removeRelayGroupFromMessages(channel)
                                } else {
                                    accountViewModel.addRelayGroupToMessages(channel)
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringRes(R.string.leave), color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuOpen = false
                                accountViewModel.leaveRelayGroup(channel)
                                if (canPop) nav.popBack()
                            },
                        )
                        // Archive/Unarchive (kind-9002 `archived` tag) — a reversible hide-from-the-sidebar,
                        // Buzz-only and admin-gated like Delete but NOT destructive, so no confirm dialog.
                        // A DM is never archived (it has its own hide), so this is channels/forums only.
                        if (isBuzzRelay && !isDm && displayMembership == RelayGroupMembership.ADMIN) {
                            val archived = channel.isArchived()
                            DropdownMenuItem(
                                text = { Text(stringRes(if (archived) R.string.buzz_channel_unarchive else R.string.buzz_channel_archive)) },
                                onClick = {
                                    menuOpen = false
                                    accountViewModel.archiveRelayGroup(channel, !archived)
                                },
                            )
                        }
                        // Deleting the whole channel/group (kind-9008) is destructive for everyone, so it's
                        // shown ONLY to an admin/owner — the same authorization gate as Edit above — and
                        // routed through a confirmation dialog rather than firing on tap.
                        if (displayMembership == RelayGroupMembership.ADMIN) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringRes(if (isBuzzRelay) R.string.buzz_channel_delete else R.string.relay_group_delete),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    confirmDelete = true
                                },
                            )
                        }
                    }
                }
            }
        },
        // No back arrow when opened as a bottom-nav tab (nothing to pop); the bottom bar shows instead.
        showBackButton = nav.canPop(),
        popBack = nav::popBack,
    )

    if (showInvite) {
        InviteRelayGroupDialog(channel, accountViewModel) { showInvite = false }
    }

    if (showJoinCode) {
        JoinRelayGroupDialog(
            channel = channel,
            accountViewModel = accountViewModel,
            onJoined = { requested = true },
            onDismiss = { showJoinCode = false },
        )
    }

    if (confirmDelete) {
        val deleteLabel = stringRes(if (isBuzzRelay) R.string.buzz_channel_delete else R.string.relay_group_delete)
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(deleteLabel) },
            text = {
                Text(
                    stringRes(
                        if (isBuzzRelay) R.string.buzz_channel_delete_confirm else R.string.relay_group_delete_confirm,
                        channel.toBestDisplayName(),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    accountViewModel.deleteRelayGroup(channel)
                    if (canPop) nav.popBack()
                }) {
                    Text(deleteLabel, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringRes(R.string.cancel))
                }
            },
        )
    }
}

/** Title for a Buzz DM: the OTHER participant's display name (reactive), falling back to the channel name. */
@Composable
private fun DmParticipantTitle(
    otherPubkey: HexKey,
    channel: RelayGroupChannel,
    accountViewModel: AccountViewModel,
    modifier: Modifier,
) {
    val user = remember(otherPubkey) { LocalCache.getOrCreateUser(otherPubkey) }
    val name by observeUserName(user, accountViewModel)
    Text(
        text = name.ifBlank { channel.toBestDisplayName() },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Fire the system share sheet with a njump web link to the group's naddr. */
private fun shareRelayGroup(
    context: Context,
    naddr: String,
) {
    val sendIntent =
        Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, njumpLink(naddr))
            putExtra(Intent.EXTRA_TITLE, stringRes(context, R.string.quick_action_share_browser_link))
        }
    context.startActivity(Intent.createChooser(sendIntent, stringRes(context, R.string.quick_action_share)))
}

/** A small colored pill naming the user's role/status in the group. */
@Composable
private fun RoleBadge(membership: RelayGroupMembership) {
    val label =
        when (membership) {
            RelayGroupMembership.ADMIN -> stringRes(Res.string.relay_group_role_admin)
            RelayGroupMembership.MODERATOR -> stringRes(Res.string.relay_group_role_moderator)
            RelayGroupMembership.PENDING -> stringRes(Res.string.relay_group_pending)
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

/**
 * Whether this Buzz channel has a canvas (kind 40100) in cache, recomposing when one lands.
 *
 * Only the top bar's DM case needs this: a DM gets the canvas affordance solely when a document
 * already exists, mirroring Buzz's own `hasCanvas || canEditNarrative` where `canEditNarrative`
 * excludes DMs. Reads the same [BuzzWorkspaceStates] registry the canvas screen renders from, so the
 * icon appears the moment the document arrives rather than on the next visit.
 */
@Composable
private fun observeBuzzCanvas(channelId: String): State<Boolean> {
    val state = remember(channelId) { BuzzWorkspaceStates.getOrCreate(channelId) }
    val version by state.canvasUpdates.collectAsStateWithLifecycle()
    return remember(channelId, version) { mutableStateOf(state.canvasNote != null) }
}
