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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.nip43RelayMembers.ui.RelayRoleChips
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.channel.observeChannel
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.ephemeral_relay_chat
import com.vitorpamplona.amethyst.commons.resources.geohash_chat
import com.vitorpamplona.amethyst.commons.resources.kc_group_admin_count
import com.vitorpamplona.amethyst.commons.resources.kc_group_admins
import com.vitorpamplona.amethyst.commons.resources.kc_group_channel_hide
import com.vitorpamplona.amethyst.commons.resources.kc_group_channel_mute
import com.vitorpamplona.amethyst.commons.resources.kc_group_community_rules
import com.vitorpamplona.amethyst.commons.resources.kc_group_concord_control
import com.vitorpamplona.amethyst.commons.resources.kc_group_concord_edit
import com.vitorpamplona.amethyst.commons.resources.kc_group_concord_entity_banlist
import com.vitorpamplona.amethyst.commons.resources.kc_group_concord_entity_channel
import com.vitorpamplona.amethyst.commons.resources.kc_group_concord_entity_dissolved
import com.vitorpamplona.amethyst.commons.resources.kc_group_concord_entity_grant
import com.vitorpamplona.amethyst.commons.resources.kc_group_concord_entity_invite
import com.vitorpamplona.amethyst.commons.resources.kc_group_concord_entity_invite_registry
import com.vitorpamplona.amethyst.commons.resources.kc_group_concord_entity_invite_revoked
import com.vitorpamplona.amethyst.commons.resources.kc_group_concord_entity_metadata
import com.vitorpamplona.amethyst.commons.resources.kc_group_concord_entity_role
import com.vitorpamplona.amethyst.commons.resources.kc_group_concord_invite_revoked
import com.vitorpamplona.amethyst.commons.resources.kc_group_create
import com.vitorpamplona.amethyst.commons.resources.kc_group_create_invite
import com.vitorpamplona.amethyst.commons.resources.kc_group_delete_event
import com.vitorpamplona.amethyst.commons.resources.kc_group_delete_group
import com.vitorpamplona.amethyst.commons.resources.kc_group_deleted_events
import com.vitorpamplona.amethyst.commons.resources.kc_group_edit_metadata
import com.vitorpamplona.amethyst.commons.resources.kc_group_field_group
import com.vitorpamplona.amethyst.commons.resources.kc_group_field_nickname
import com.vitorpamplona.amethyst.commons.resources.kc_group_field_version
import com.vitorpamplona.amethyst.commons.resources.kc_group_geohash_presence
import com.vitorpamplona.amethyst.commons.resources.kc_group_hidden_count
import com.vitorpamplona.amethyst.commons.resources.kc_group_join_request
import com.vitorpamplona.amethyst.commons.resources.kc_group_leave_request
import com.vitorpamplona.amethyst.commons.resources.kc_group_members
import com.vitorpamplona.amethyst.commons.resources.kc_group_nests_command
import com.vitorpamplona.amethyst.commons.resources.kc_group_nests_kick
import com.vitorpamplona.amethyst.commons.resources.kc_group_nests_mute
import com.vitorpamplona.amethyst.commons.resources.kc_group_participant_count
import com.vitorpamplona.amethyst.commons.resources.kc_group_participants
import com.vitorpamplona.amethyst.commons.resources.kc_group_pin_count
import com.vitorpamplona.amethyst.commons.resources.kc_group_pinned
import com.vitorpamplona.amethyst.commons.resources.kc_group_put_user
import com.vitorpamplona.amethyst.commons.resources.kc_group_put_user_roles
import com.vitorpamplona.amethyst.commons.resources.kc_group_relay_invite_request
import com.vitorpamplona.amethyst.commons.resources.kc_group_relay_role
import com.vitorpamplona.amethyst.commons.resources.kc_group_remove_user
import com.vitorpamplona.amethyst.commons.resources.kc_group_role_count
import com.vitorpamplona.amethyst.commons.resources.kc_group_roles
import com.vitorpamplona.amethyst.commons.resources.kc_group_rules_allowed_kinds
import com.vitorpamplona.amethyst.commons.resources.kc_group_rules_allowed_people
import com.vitorpamplona.amethyst.commons.resources.kc_group_rules_blocked_people
import com.vitorpamplona.amethyst.commons.resources.kc_group_rules_bytes
import com.vitorpamplona.amethyst.commons.resources.kc_group_rules_max_size
import com.vitorpamplona.amethyst.commons.resources.kc_group_rules_wot
import com.vitorpamplona.amethyst.commons.resources.kc_group_rules_wot_depth
import com.vitorpamplona.amethyst.commons.resources.kc_group_teleported
import com.vitorpamplona.amethyst.commons.resources.kc_group_update_pins
import com.vitorpamplona.amethyst.commons.resources.relay_group_field_about
import com.vitorpamplona.amethyst.commons.resources.relay_group_field_name
import com.vitorpamplona.amethyst.commons.resources.relay_group_field_topics
import com.vitorpamplona.amethyst.commons.resources.relay_group_join_code_label
import com.vitorpamplona.amethyst.commons.resources.relay_group_member_count
import com.vitorpamplona.amethyst.commons.resources.relay_group_parent_label
import com.vitorpamplona.amethyst.commons.resources.relay_group_pinned_content_description
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadPublicChatChannel
import com.vitorpamplona.amethyst.commons.ui.note.platform.LoadCityName
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.LabelMemberRow
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.ListCard
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.UserMemberRow
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.hidesPrivateMembers
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.loadPrivateItems
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.rememberAllMembers
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.RenderChatRaid
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.PublicChatChannelHeader
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChatEditEvent
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.concord.cord04Roles.control.ControlEditionEvent
import com.vitorpamplona.quartz.concord.cord05Invites.bundle.ConcordInviteBundleEvent
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChatEvent
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashPresenceEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.experimental.ephemChat.list.EphemeralChatListEvent
import com.vitorpamplona.quartz.experimental.ephemChat.list.rooms
import com.vitorpamplona.quartz.experimental.nests.admin.AdminCommandEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelHideMessageEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMuteUserEvent
import com.vitorpamplona.quartz.nip28PublicChat.list.PublicChatListEvent
import com.vitorpamplona.quartz.nip28PublicChat.list.channels
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupAdminsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupParticipantsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupPinnedEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupRolesEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.CreateGroupEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.DeleteGroupEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupCreateInviteEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupDeleteEventEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupEditMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupPutUserEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupRemoveUserEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupUpdatePinListEvent
import com.vitorpamplona.quartz.nip29RelayGroups.request.GroupJoinRequestEvent
import com.vitorpamplona.quartz.nip29RelayGroups.request.GroupLeaveRequestEvent
import com.vitorpamplona.quartz.nip29RelayGroups.tags.AddressPin
import com.vitorpamplona.quartz.nip29RelayGroups.tags.EventPin
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupAdminTag
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupPin
import com.vitorpamplona.quartz.nip43RelayMembers.inviteRequest.RelayInviteRequestEvent
import com.vitorpamplona.quartz.nip43RelayMembers.roles.RelayRoleEvent
import com.vitorpamplona.quartz.nip53LiveActivities.raid.LiveActivitiesRaidEvent
import com.vitorpamplona.quartz.nip72ModCommunities.rules.CommunityRulesEvent
import com.vitorpamplona.quartz.nip72ModCommunities.rules.tags.PubkeyRuleTag

/*
 * Cards for the group-chat family: NIP-29 relay-group moderation, requests and relay-signed
 * metadata, NIP-28 channel moderation and chat lists, NIP-72 community rules, Concord records,
 * bitchat geohash and ephemeral chat, nests room commands, NIP-43 roles and live-activity raids.
 * Each one says what happened, in which group or room, to whom, and why, instead of the raw
 * content, which for most of these kinds is empty, JSON or ciphertext.
 */

// ---------------------------------------------------------------------------------------------
// NIP-29 relay groups
// ---------------------------------------------------------------------------------------------

/**
 * The group a NIP-29 event belongs to. A group id is only unique within its host relay, and these
 * events never name it: the host is the relay that served the note. With one, the card shows the
 * same tappable group header a group message wears; without one, just the id.
 */
@Composable
private fun RelayGroupContext(
    baseNote: Note,
    groupId: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (groupId.isNullOrBlank()) return
    val channel = remember(baseNote, groupId) { relayGroupChannelOf(baseNote, groupId) }
    if (channel != null) {
        RelayGroupChannelHeader(
            channel = channel,
            modifier = MaterialTheme.colorScheme.replyModifier.padding(10.dp),
            accountViewModel = accountViewModel,
            nav = nav,
        )
    } else {
        KindCardField(stringRes(Res.string.kc_group_field_group), groupId, monospace = true)
    }
}

private fun relayGroupChannelOf(
    note: Note,
    groupId: String,
): RelayGroupChannel? =
    note.inGatherers?.firstNotNullOfOrNull { gatherer -> (gatherer as? RelayGroupChannel)?.takeIf { it.groupId.id == groupId } }
        ?: note.relays.firstOrNull()?.let { LocalCache.getOrCreateRelayGroupChannel(GroupId(groupId, it)) }

/** Quotes a pinned event or address of a group pin list. */
@Composable
private fun GroupPinQuote(
    pin: GroupPin,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when (pin) {
        is EventPin -> KindCardQuoteEvent(pin.eventId, quotesLeft, backgroundColor, accountViewModel, nav)
        is AddressPin -> KindCardQuoteAddress(pin.address, quotesLeft, backgroundColor, accountViewModel, nav)
    }
}

/** NIP-29 kind 9000: users added to a group, or given roles in it. */
@Composable
fun RenderGroupPutUser(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? GroupPutUserEvent ?: return
    val users = remember(event) { event.userPubKeys() }
    val roles =
        remember(event) {
            event.tags
                .mapNotNull(GroupAdminTag::parse)
                .flatMap { it.roles }
                .distinct()
        }

    KindCard(
        icon = MaterialSymbols.PersonAdd,
        title = stringRes(if (roles.isEmpty()) Res.string.kc_group_put_user else Res.string.kc_group_put_user_roles),
    ) {
        RelayGroupContext(baseNote, event.groupId(), accountViewModel, nav)
        KindCardUsers(users, accountViewModel, nav)
        KindCardChips(roles)
        KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav)
    }
}

/** NIP-29 kind 9001: users removed from a group. */
@Composable
fun RenderGroupRemoveUser(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? GroupRemoveUserEvent ?: return
    val users = remember(event) { event.userPubKeys() }

    KindCard(
        icon = MaterialSymbols.PersonRemove,
        title = stringRes(Res.string.kc_group_remove_user),
        iconTint = MaterialTheme.colorScheme.error,
    ) {
        RelayGroupContext(baseNote, event.groupId(), accountViewModel, nav)
        KindCardUsers(users, accountViewModel, nav)
        KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav)
    }
}

/** NIP-29 kind 9002: a moderator changed the group's name, description, topics or place. */
@Composable
fun RenderGroupEditMetadata(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? GroupEditMetadataEvent ?: return
    val topics = remember(event) { event.hashtags().joinToString(", ") { "#$it" } }

    KindCard(
        icon = MaterialSymbols.Edit,
        title = stringRes(Res.string.kc_group_edit_metadata),
    ) {
        RelayGroupContext(baseNote, event.groupId(), accountViewModel, nav)
        KindCardField(stringRes(Res.string.relay_group_field_name), event.name())
        KindCardField(stringRes(Res.string.relay_group_field_about), event.about(), maxLines = 4)
        KindCardField(stringRes(Res.string.relay_group_field_topics), topics)
        KindCardField(stringRes(Res.string.relay_group_parent_label), event.parent(), monospace = true)
    }
}

/** NIP-29 kind 9005: a moderator removed messages from the group. */
@Composable
fun RenderGroupDeleteEvent(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? GroupDeleteEventEvent ?: return
    val deleted = remember(event) { event.deletedEventIds() }

    KindCard(
        icon = MaterialSymbols.Delete,
        title = stringRes(Res.string.kc_group_delete_event),
        subtitle = if (deleted.size > 1) pluralStringRes(Res.plurals.kc_group_deleted_events, deleted.size, deleted.size) else null,
        iconTint = MaterialTheme.colorScheme.error,
    ) {
        RelayGroupContext(baseNote, event.groupId(), accountViewModel, nav)
        KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav)
        deleted.firstOrNull()?.let { KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

/** NIP-29 kind 9007: a new group. */
@Composable
fun RenderCreateGroup(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? CreateGroupEvent ?: return

    KindCard(
        icon = MaterialSymbols.GroupAdd,
        title = stringRes(Res.string.kc_group_create),
    ) {
        RelayGroupContext(baseNote, event.groupId(), accountViewModel, nav)
        KindCardField(stringRes(Res.string.relay_group_field_name), event.name())
        KindCardField(stringRes(Res.string.relay_group_field_about), event.about(), maxLines = 4)
    }
}

/** NIP-29 kind 9008: a group was deleted. */
@Composable
fun RenderDeleteGroup(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? DeleteGroupEvent ?: return

    KindCard(
        icon = MaterialSymbols.DeleteForever,
        title = stringRes(Res.string.kc_group_delete_group),
        iconTint = MaterialTheme.colorScheme.error,
    ) {
        RelayGroupContext(baseNote, event.groupId(), accountViewModel, nav)
        KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav)
    }
}

/** NIP-29 kind 9009: an invite code for a closed group. */
@Composable
fun RenderGroupCreateInvite(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? GroupCreateInviteEvent ?: return

    KindCard(
        icon = MaterialSymbols.Key,
        title = stringRes(Res.string.kc_group_create_invite),
    ) {
        RelayGroupContext(baseNote, event.groupId(), accountViewModel, nav)
        KindCardField(stringRes(Res.string.relay_group_join_code_label), event.code(), monospace = true)
    }
}

/** NIP-29 kind 9010: the group's pin list changed. */
@Composable
fun RenderGroupUpdatePinList(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? GroupUpdatePinListEvent ?: return
    val pins = remember(event) { event.pins() }

    KindCard(
        icon = MaterialSymbols.PushPin,
        title = stringRes(Res.string.kc_group_update_pins),
        subtitle = pluralStringRes(Res.plurals.kc_group_pin_count, pins.size, pins.size),
    ) {
        RelayGroupContext(baseNote, event.groupId(), accountViewModel, nav)
        pins.firstOrNull()?.let { GroupPinQuote(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

/** NIP-29 kind 9021: a user asks to join a group, with an optional reason and invite code. */
@Composable
fun RenderGroupJoinRequest(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? GroupJoinRequestEvent ?: return

    KindCard(
        icon = MaterialSymbols.GroupAdd,
        title = stringRes(Res.string.kc_group_join_request),
    ) {
        RelayGroupContext(baseNote, event.groupId(), accountViewModel, nav)
        KindCardField(stringRes(Res.string.relay_group_join_code_label), event.inviteCode(), monospace = true)
        KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav)
    }
}

/** NIP-29 kind 9022: a user leaves a group. */
@Composable
fun RenderGroupLeaveRequest(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? GroupLeaveRequestEvent ?: return

    KindCard(
        icon = MaterialSymbols.AutoMirrored.ExitToApp,
        title = stringRes(Res.string.kc_group_leave_request),
    ) {
        RelayGroupContext(baseNote, event.groupId(), accountViewModel, nav)
        KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav)
    }
}

/** NIP-29 kind 39001: the relay's list of the group's admins and their roles. */
@Composable
fun RenderGroupAdmins(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? GroupAdminsEvent ?: return
    val admins = remember(event) { event.admins() }

    KindCard(
        icon = MaterialSymbols.Shield,
        title = stringRes(Res.string.kc_group_admins),
        subtitle = pluralStringRes(Res.plurals.kc_group_admin_count, admins.size, admins.size),
    ) {
        RelayGroupContext(baseNote, event.groupId(), accountViewModel, nav)
        admins.take(5).forEach { admin ->
            key(admin.pubKey) {
                UserMemberRow(admin.pubKey, accountViewModel, nav)
                KindCardChips(admin.roles)
            }
        }
    }
}

/** NIP-29 kind 39002: the relay's member roster for the group. */
@Composable
fun RenderGroupMembers(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? GroupMembersEvent ?: return
    val members = remember(event) { event.members() }

    RosterCard(
        baseNote = baseNote,
        groupId = event.groupId(),
        icon = MaterialSymbols.Group,
        title = stringRes(Res.string.kc_group_members),
        subtitle = pluralStringRes(Res.plurals.relay_group_member_count, members.size, members.size),
        people = members,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

/** NIP-29 kind 39004: the people currently taking part in the group (e.g. a live room). */
@Composable
fun RenderGroupParticipants(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? GroupParticipantsEvent ?: return
    val participants = remember(event) { event.participants() }

    RosterCard(
        baseNote = baseNote,
        groupId = event.groupId(),
        icon = MaterialSymbols.Groups,
        title = stringRes(Res.string.kc_group_participants),
        subtitle = pluralStringRes(Res.plurals.kc_group_participant_count, participants.size, participants.size),
        people = participants,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
private fun RosterCard(
    baseNote: Note,
    groupId: String,
    icon: MaterialSymbol,
    title: String,
    subtitle: String,
    people: List<HexKey>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // A roster can hold thousands: only the first few are drawn.
    val shown = remember(people) { people.take(5) }
    KindCard(icon = icon, title = title, subtitle = subtitle) {
        RelayGroupContext(baseNote, groupId, accountViewModel, nav)
        KindCardUsers(shown, accountViewModel, nav)
    }
}

/** NIP-29 kind 39003: the roles the group's relay supports, with their descriptions. */
@Composable
fun RenderGroupRoles(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? GroupRolesEvent ?: return
    val roles = remember(event) { event.roles() }
    val names = remember(roles) { roles.map { it.name } }

    KindCard(
        icon = MaterialSymbols.Shield,
        title = stringRes(Res.string.kc_group_roles),
        subtitle = pluralStringRes(Res.plurals.kc_group_role_count, roles.size, roles.size),
    ) {
        RelayGroupContext(baseNote, event.groupId(), accountViewModel, nav)
        KindCardChips(names)
        roles.take(8).forEach { KindCardField(it.name, it.description) }
    }
}

/** NIP-29 kind 39005: the group's pinned events, as the relay publishes them. */
@Composable
fun RenderGroupPinned(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? GroupPinnedEvent ?: return
    val pins = remember(event) { event.pins() }

    KindCard(
        icon = MaterialSymbols.PushPin,
        title = stringRes(Res.string.kc_group_pinned),
        subtitle = pluralStringRes(Res.plurals.kc_group_pin_count, pins.size, pins.size),
    ) {
        RelayGroupContext(baseNote, event.groupId(), accountViewModel, nav)
        pins.firstOrNull()?.let { GroupPinQuote(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

// ---------------------------------------------------------------------------------------------
// NIP-28 public chats
// ---------------------------------------------------------------------------------------------

@Composable
private fun PublicChatContext(
    channelId: HexKey?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (channelId == null) return
    PublicChatChannelHeader(
        channelHex = channelId,
        sendToChannel = true,
        modifier = MaterialTheme.colorScheme.replyModifier.padding(10.dp),
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

/** NIP-28 kind 43: a channel moderator hides messages, with a reason. */
@Composable
fun RenderChannelHideMessage(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ChannelHideMessageEvent ?: return
    val hidden = remember(event) { event.eventsToHide() }
    val channelId = remember(event) { event.channelId() }

    KindCard(
        icon = MaterialSymbols.VisibilityOff,
        title = stringRes(Res.string.kc_group_channel_hide),
        subtitle = if (hidden.size > 1) pluralStringRes(Res.plurals.kc_group_hidden_count, hidden.size, hidden.size) else null,
    ) {
        PublicChatContext(channelId, accountViewModel, nav)
        KindCardText(baseNote, event.reason(), backgroundColor, accountViewModel, nav)
        hidden.firstOrNull()?.let { KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

/** NIP-28 kind 44: a channel moderator mutes users, with a reason. */
@Composable
fun RenderChannelMuteUser(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ChannelMuteUserEvent ?: return
    val users = remember(event) { event.usersToMute() }
    val channelId = remember(event) { event.channelId() }

    KindCard(
        icon = MaterialSymbols.PersonOff,
        title = stringRes(Res.string.kc_group_channel_mute),
    ) {
        PublicChatContext(channelId, accountViewModel, nav)
        KindCardUsers(users, accountViewModel, nav)
        KindCardText(baseNote, event.reason(), backgroundColor, accountViewModel, nav)
    }
}

/** NIP-51 kind 10005: the public chats a user follows, public ones and (for the author) private ones. */
@Composable
fun RenderPublicChatList(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? PublicChatListEvent ?: return
    val public = remember(event) { event.publicChannels() }
    val private by loadPrivateItems(event, accountViewModel) { event.privateTags(it)?.channels() }

    ListCard(
        title = kindName(event.kind),
        description = null,
        items = rememberAllMembers(public, private),
        hasUnreadablePrivateItems = event.hidesPrivateMembers(private),
        backgroundColor = backgroundColor,
    ) { channel ->
        key(channel.eventId) { PublicChatRow(channel.eventId, accountViewModel, nav) }
    }
}

@Composable
private fun PublicChatRow(
    channelId: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadPublicChatChannel(channelId) { channel ->
        val state by observeChannel(channel, accountViewModel)
        val name = remember(state) { channel.toBestDisplayName() }
        LabelMemberRow(name) { nav.nav(routeFor(channel)) }
    }
}

// ---------------------------------------------------------------------------------------------
// Ephemeral and geohash chats
// ---------------------------------------------------------------------------------------------

/** Kind 10023: the ephemeral chat rooms a user follows. */
@Composable
fun RenderEphemeralChatList(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? EphemeralChatListEvent ?: return
    val public = remember(event) { event.publicRooms() }
    val private by loadPrivateItems(event, accountViewModel) { event.privateTags(it)?.rooms() }

    ListCard(
        title = kindName(event.kind),
        description = null,
        items = rememberAllMembers(public, private),
        hasUnreadablePrivateItems = event.hidesPrivateMembers(private),
        backgroundColor = backgroundColor,
    ) { room ->
        key(room.toKey()) { LabelMemberRow(room.toDisplayKey()) { nav.nav(routeFor(room)) } }
    }
}

/** Kind 23333: a message in an ephemeral relay chat room. Names the room, then the text. */
@Composable
fun RenderEphemeralChat(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? EphemeralChatEvent ?: return
    val roomId = remember(event) { event.roomId() }
    val title = roomId?.toDisplayKey() ?: event.room()

    Column {
        Box(modifier = if (roomId != null) Modifier.clickable { nav.nav(routeFor(roomId)) } else Modifier) {
            KindCard(
                icon = MaterialSymbols.Chat,
                title = title,
                subtitle = stringRes(Res.string.ephemeral_relay_chat),
            )
        }
        KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav)
    }
}

/** Kind 20000: a bitchat message in a geohash cell. Names the place, then the text. */
@Composable
fun RenderGeohashChat(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? GeohashChatEvent ?: return
    val geohash = remember(event) { event.geohash() }
    val nickname = remember(event) { event.nickname() }
    val teleported = remember(event) { event.isTeleported() }

    Column {
        GeohashRoomHeader(geohash, stringRes(Res.string.geohash_chat), nav) {
            KindCardField(stringRes(Res.string.kc_group_field_nickname), nickname)
            if (teleported) KindCardChips(listOf(stringRes(Res.string.kc_group_teleported)))
        }
        KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav)
    }
}

/** Kind 20001: a bitchat user announces they are in a geohash cell's chat. */
@Composable
fun RenderGeohashPresence(
    baseNote: Note,
    nav: INav,
) {
    val event = baseNote.event as? GeohashPresenceEvent ?: return
    val geohash = remember(event) { event.geohash() }
    val nickname = remember(event) { event.nickname() }

    GeohashRoomHeader(geohash, stringRes(Res.string.kc_group_geohash_presence), nav) {
        KindCardField(stringRes(Res.string.kc_group_field_nickname), nickname)
    }
}

/** The place a geohash chat event is in, by city name when the platform resolves one; opens the cell's chat. */
@Composable
private fun GeohashRoomHeader(
    geohash: String?,
    subtitle: String,
    nav: INav,
    body: @Composable ColumnScope.() -> Unit,
) {
    if (geohash.isNullOrBlank()) {
        KindCard(icon = MaterialSymbols.LocationOn, title = subtitle, body = body)
        return
    }

    Box(modifier = Modifier.clickable { nav.nav(Route.GeohashChat(geohash)) }) {
        LoadCityName(
            geohashStr = geohash,
            onLoading = { KindCard(icon = MaterialSymbols.LocationOn, title = "#$geohash", subtitle = subtitle, body = body) },
        ) { city ->
            if (city == geohash) {
                KindCard(icon = MaterialSymbols.LocationOn, title = "#$geohash", subtitle = subtitle, body = body)
            } else {
                KindCard(icon = MaterialSymbols.LocationOn, title = city, subtitle = "$subtitle · #$geohash", body = body)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Audio rooms, communities, raids
// ---------------------------------------------------------------------------------------------

/** Kind 4312: a nests audio-room host kicks or force-mutes a participant. */
@Composable
fun RenderNestsAdminCommand(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? AdminCommandEvent ?: return
    val action = remember(event) { event.action() }
    val target = remember(event) { event.targetPubkey() }
    val room = remember(event) { event.room()?.let { Address.parse(it) } }

    val icon =
        when (action) {
            AdminCommandEvent.Action.KICK -> MaterialSymbols.PersonRemove
            AdminCommandEvent.Action.MUTE -> MaterialSymbols.MicOff
            null -> MaterialSymbols.Gavel
        }
    val title =
        when (action) {
            AdminCommandEvent.Action.KICK -> stringRes(Res.string.kc_group_nests_kick)
            AdminCommandEvent.Action.MUTE -> stringRes(Res.string.kc_group_nests_mute)
            null -> stringRes(Res.string.kc_group_nests_command)
        }

    KindCard(icon = icon, title = title) {
        target?.let { KindCardUsers(listOf(it), accountViewModel, nav) }
        room?.let { KindCardQuoteAddress(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

/** NIP-72 kind 34551: the machine-readable rules of a community. */
@Composable
fun RenderCommunityRules(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? CommunityRulesEvent ?: return
    val kinds = remember(event) { event.allowedKinds().sorted() }
    val rules = remember(event) { event.pubkeyRules() }
    val allowed = remember(rules) { rules.count { it.policy == PubkeyRuleTag.Policy.ALLOW } }
    val blocked = remember(rules) { rules.count { it.policy == PubkeyRuleTag.Policy.DENY } }
    val wot = remember(event) { event.wotGates() }
    val wotRoots = remember(wot) { wot.map { it.rootPubkey } }
    val maxSize = remember(event) { event.maxEventSize() }
    val community = remember(event) { event.communityAddress()?.let { Address.parse(it) } }

    KindCard(
        icon = MaterialSymbols.Gavel,
        title = stringRes(Res.string.kc_group_community_rules),
    ) {
        if (kinds.isNotEmpty()) {
            KindCardField(stringRes(Res.string.kc_group_rules_allowed_kinds), kinds.map { kindName(it) }.joinToString(", "), maxLines = 4)
        }
        KindCardField(stringRes(Res.string.kc_group_rules_allowed_people), allowed.takeIf { it > 0 }?.toString())
        KindCardField(stringRes(Res.string.kc_group_rules_blocked_people), blocked.takeIf { it > 0 }?.toString())
        maxSize?.let { KindCardField(stringRes(Res.string.kc_group_rules_max_size), pluralStringRes(Res.plurals.kc_group_rules_bytes, it, it)) }
        if (wot.isNotEmpty()) {
            val depth = wot.maxOf { it.depth }
            KindCardField(stringRes(Res.string.kc_group_rules_wot), pluralStringRes(Res.plurals.kc_group_rules_wot_depth, depth, depth))
            KindCardUsers(wotRoots, accountViewModel, nav, max = 3)
        }
        community?.let { KindCardQuoteAddress(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

/** NIP-53 kind 1312: a stream raids another. Falls back to a plain card when either end is missing. */
@Composable
fun RenderLiveActivityRaid(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? LiveActivitiesRaidEvent ?: return
    val complete = remember(event) { event.fromAddress() != null && event.toAddress() != null }

    if (complete) {
        RenderChatRaid(baseNote, accountViewModel, nav)
    } else {
        KindCard(icon = MaterialSymbols.AutoMirrored.Forward, title = kindName(event.kind)) {
            KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Concord
// ---------------------------------------------------------------------------------------------

/** Concord kind 3302: an edit of an earlier chat message, with its new text. */
@Composable
fun RenderConcordChatEdit(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ConcordChatEditEvent ?: return
    val target = remember(event) { event.editedMessageId() }

    KindCard(
        icon = MaterialSymbols.Edit,
        title = stringRes(Res.string.kc_group_concord_edit),
    ) {
        KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav)
        target?.let { KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

/** Concord kind 3308: a new version of a piece of community state (details, a role, a channel, ...). */
@Composable
fun RenderConcordControlEdition(baseNote: Note) {
    val event = baseNote.event as? ControlEditionEvent ?: return
    val entity = remember(event) { event.entityKind() }
    val version = remember(event) { event.version() }

    KindCard(
        icon = MaterialSymbols.Settings,
        title = stringRes(Res.string.kc_group_concord_control),
        subtitle = entity?.let { concordEntityLabel(it) },
    ) {
        KindCardField(stringRes(Res.string.kc_group_field_version), version?.toString())
        KindCardCode(event.content, maxLines = 6)
    }
}

@Composable
private fun concordEntityLabel(kind: ControlEntityKind): String =
    stringRes(
        when (kind) {
            ControlEntityKind.METADATA -> Res.string.kc_group_concord_entity_metadata
            ControlEntityKind.ROLE -> Res.string.kc_group_concord_entity_role
            ControlEntityKind.CHANNEL -> Res.string.kc_group_concord_entity_channel
            ControlEntityKind.GRANT -> Res.string.kc_group_concord_entity_grant
            ControlEntityKind.BANLIST -> Res.string.kc_group_concord_entity_banlist
            ControlEntityKind.INVITE_LIVE -> Res.string.kc_group_concord_entity_invite
            ControlEntityKind.INVITE_REGISTRY -> Res.string.kc_group_concord_entity_invite_registry
            ControlEntityKind.INVITE_REVOKED -> Res.string.kc_group_concord_entity_invite_revoked
            ControlEntityKind.DISSOLVED -> Res.string.kc_group_concord_entity_dissolved
            ControlEntityKind.PIN_LIST -> Res.string.relay_group_pinned_content_description
        },
    )

/** Concord kind 33301: an encrypted invite bundle, or the tombstone of a revoked link. */
@Composable
fun RenderConcordInviteBundle(baseNote: Note) {
    val event = baseNote.event as? ConcordInviteBundleEvent ?: return
    val revoked = remember(event) { event.versionedSubKind() == ControlEntityKind.INVITE_REVOKED }

    if (revoked) {
        KindCard(
            icon = MaterialSymbols.Link,
            title = stringRes(Res.string.kc_group_concord_invite_revoked),
            iconTint = MaterialTheme.colorScheme.error,
        )
    } else {
        RenderPrivateKindCard(baseNote, MaterialSymbols.Link)
    }
}

// ---------------------------------------------------------------------------------------------
// NIP-43 relay membership
// ---------------------------------------------------------------------------------------------

/** NIP-43 kind 28935 (retired): a request for an invite code from the relay. */
@Suppress("DEPRECATION")
@Composable
fun RenderRelayInviteRequest(baseNote: Note) {
    if (baseNote.event !is RelayInviteRequestEvent) return
    KindCard(
        icon = MaterialSymbols.Key,
        title = stringRes(Res.string.kc_group_relay_invite_request),
    )
}

/** NIP-43 kind 33534: a role the relay hands its members, with its color and description. */
@Composable
fun RenderRelayRole(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? RelayRoleEvent ?: return
    val roles = remember(event) { listOf(event.role()) }

    KindCard(
        icon = MaterialSymbols.MilitaryTech,
        title = stringRes(Res.string.kc_group_relay_role),
    ) {
        RelayRoleChips(roles)
        KindCardText(baseNote, event.description(), backgroundColor, accountViewModel, nav)
    }
}
