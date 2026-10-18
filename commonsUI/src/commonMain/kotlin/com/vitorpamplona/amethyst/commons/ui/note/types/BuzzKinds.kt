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

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.buzz_canvas_title
import com.vitorpamplona.amethyst.commons.resources.buzz_diff_truncated
import com.vitorpamplona.amethyst.commons.resources.buzz_typing_one
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_action
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_action_ban
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_action_delete
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_action_dismiss
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_action_escalate
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_action_kick
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_action_timeout
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_agent
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_archive_request
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_archived
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_archived_identities
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_ban
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_bookmarked
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_branch
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_channel
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_channel_summary
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_commit
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_consent
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_diff
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_dm_add_member
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_dm_created
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_dm_hide
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_dm_open
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_dm_visibility
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_edited
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_feedback
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_feedback_bug
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_feedback_needs_work
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_feedback_praise
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_forum_downvote
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_forum_post
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_forum_reply
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_forum_upvote
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_hidden_dm_count
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_huddle_ended
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_huddle_guidelines
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_huddle_joined
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_huddle_left
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_huddle_live
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_huddle_reaction
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_huddle_started
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_identities_count
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_identity_archived
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_identity_unarchived
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_job_accepted
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_job_cancelled
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_job_error
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_job_progress
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_job_request
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_job_result
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_last_activity
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_last_participant
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_last_reply
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_member_removed
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_messages
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_people_count
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_permanent
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_pinned
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_presence
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_presence_away
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_presence_offline
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_presence_online
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_pull_request
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_purpose
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_reason
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_relay_add_member
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_relay_change_role
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_relay_remove_member
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_reminder
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_reminder_for
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_removed_by
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_replaced_by
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_reply_count
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_report_closed
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_report_dismissed
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_report_resolved
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_repository
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_requested_by
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_role
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_role_admin
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_role_member
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_role_owner
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_scheduled
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_status
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_thread_summary
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_timeout
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_topic
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_unarchive_request
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_until
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_untimeout
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_visibility
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_workspace_icon_cleared
import com.vitorpamplona.amethyst.commons.resources.kc_buzz_workspace_icon_set
import com.vitorpamplona.amethyst.commons.resources.members
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.timeAbsoluteNoDot
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.buzzSystemMessageText
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.observeUserNameByHex
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.buzz.cwChannelWindow.ThreadSummaryEvent
import com.vitorpamplona.quartz.buzz.dm.DmAddMemberEvent
import com.vitorpamplona.quartz.buzz.dm.DmCreatedEvent
import com.vitorpamplona.quartz.buzz.dm.DmHideEvent
import com.vitorpamplona.quartz.buzz.dm.DmOpenEvent
import com.vitorpamplona.quartz.buzz.dvDmVisibility.DmVisibilityEvent
import com.vitorpamplona.quartz.buzz.forum.ForumCommentEvent
import com.vitorpamplona.quartz.buzz.forum.ForumPostEvent
import com.vitorpamplona.quartz.buzz.forum.ForumVoteEvent
import com.vitorpamplona.quartz.buzz.forum.tags.VoteDirection
import com.vitorpamplona.quartz.buzz.huddles.HuddleEndedEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleGuidelinesEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleLivenessEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleParticipantJoinedEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleParticipantLeftEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleReactionEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleStartedEvent
import com.vitorpamplona.quartz.buzz.iaIdentityArchival.ArchiveRequestEvent
import com.vitorpamplona.quartz.buzz.iaIdentityArchival.ArchivedIdentitiesListEvent
import com.vitorpamplona.quartz.buzz.iaIdentityArchival.ArchivedIdentityEvent
import com.vitorpamplona.quartz.buzz.iaIdentityArchival.UnarchiveRequestEvent
import com.vitorpamplona.quartz.buzz.iaIdentityArchival.UnarchivedIdentityEvent
import com.vitorpamplona.quartz.buzz.jobs.JobAcceptedEvent
import com.vitorpamplona.quartz.buzz.jobs.JobCancelEvent
import com.vitorpamplona.quartz.buzz.jobs.JobErrorEvent
import com.vitorpamplona.quartz.buzz.jobs.JobProgressEvent
import com.vitorpamplona.quartz.buzz.jobs.JobRequestEvent
import com.vitorpamplona.quartz.buzz.jobs.JobResultEvent
import com.vitorpamplona.quartz.buzz.moderation.ModerationBanEvent
import com.vitorpamplona.quartz.buzz.moderation.ModerationResolveReportEvent
import com.vitorpamplona.quartz.buzz.moderation.ModerationTimeoutEvent
import com.vitorpamplona.quartz.buzz.moderation.ModerationUntimeoutEvent
import com.vitorpamplona.quartz.buzz.moderation.ProductFeedbackEvent
import com.vitorpamplona.quartz.buzz.moderation.tags.ActionTag
import com.vitorpamplona.quartz.buzz.moderation.tags.CategoryTag
import com.vitorpamplona.quartz.buzz.moderation.tags.StatusTag
import com.vitorpamplona.quartz.buzz.notifications.MemberRemovedNotificationEvent
import com.vitorpamplona.quartz.buzz.presence.PresenceStatus
import com.vitorpamplona.quartz.buzz.presence.PresenceUpdateEvent
import com.vitorpamplona.quartz.buzz.presence.TypingIndicatorEvent
import com.vitorpamplona.quartz.buzz.relayAdmin.RelayAdminAddMemberEvent
import com.vitorpamplona.quartz.buzz.relayAdmin.RelayAdminChangeRoleEvent
import com.vitorpamplona.quartz.buzz.relayAdmin.RelayAdminRemoveMemberEvent
import com.vitorpamplona.quartz.buzz.relayAdmin.tags.RoleTag
import com.vitorpamplona.quartz.buzz.stream.CanvasEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageBookmarkedEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageDiffEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageEditEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessagePinnedEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageScheduledEvent
import com.vitorpamplona.quartz.buzz.stream.StreamReminderEvent
import com.vitorpamplona.quartz.buzz.stream.SystemMessageEvent
import com.vitorpamplona.quartz.buzz.stream.sidecars.ChannelSummaryEvent
import com.vitorpamplona.quartz.buzz.stream.sidecars.PresenceSnapshotEvent
import com.vitorpamplona.quartz.buzz.wpWorkspaceProfile.SetWorkspaceProfileEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId

/*
 * Feed cards for the Buzz workspace kinds: chat-stream side events (edits, pins, bookmarks,
 * diffs, canvases, reminders), huddles, agent jobs, forums, DMs, presence, community moderation,
 * relay administration and identity archival. Most of them carry JSON, nothing, or a one-word
 * token in `content`, so the text-note fallback either printed raw payload or an empty post.
 *
 * Many are relay-signed: the note header then names the relay keypair, so each card names the
 * people it is about from the event's own tags.
 */

private val BuzzPresenceOnlineColor = Color(0xFF4CAF50)
private val BuzzPresenceAwayColor = Color(0xFFFFB300)

// ---------------------------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------------------------

/**
 * The Buzz channel a channel-scoped event belongs to, drawn with the same tappable header a Buzz
 * message wears in the notifications feed. Falls back to the bare channel id when the channel has
 * not been loaded into the cache.
 */
@Composable
private fun BuzzChannel(
    baseNote: Note,
    channelId: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val channel = remember(baseNote, channelId) { findBuzzChannel(baseNote, channelId) }
    if (channel != null) {
        RelayGroupChannelHeader(
            channel = channel,
            modifier = MaterialTheme.colorScheme.replyModifier.padding(10.dp),
            accountViewModel = accountViewModel,
            nav = nav,
        )
    } else {
        KindCardField(stringRes(Res.string.kc_buzz_channel), channelId, monospace = true, maxLines = 1)
    }
}

private fun findBuzzChannel(
    note: Note,
    channelId: String?,
): RelayGroupChannel? =
    note.inGatherers?.firstNotNullOfOrNull { it as? RelayGroupChannel }
        ?: LocalCache.getRelayGroupChannelForContent(note)
        ?: channelId?.let { id ->
            note.relays.firstNotNullOfOrNull { LocalCache.getRelayGroupChannelIfExists(GroupId(id, it)) }
        }

/** A small caption followed by the people it introduces ("Requested by", "Removed by"). */
@Composable
private fun BuzzLabeledUsers(
    label: String,
    pubKeys: List<HexKey>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (pubKeys.isEmpty()) return
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    KindCardUsers(pubKeys, accountViewModel, nav)
}

@Composable
private fun buzzUntilText(expiresAt: Long?): String = expiresAt?.let { timeAbsoluteNoDot(it) } ?: stringRes(Res.string.kc_buzz_permanent)

@Composable
private fun buzzPresenceLabel(status: String): String =
    when (PresenceStatus.fromWire(status)) {
        PresenceStatus.ONLINE -> stringRes(Res.string.kc_buzz_presence_online)
        PresenceStatus.AWAY -> stringRes(Res.string.kc_buzz_presence_away)
        PresenceStatus.OFFLINE -> stringRes(Res.string.kc_buzz_presence_offline)
        // Other apps share kind 20001 with their own payloads; name the kind rather than print those.
        null -> if (status.isBlank() || looksLikeOpaquePayload(status)) stringRes(Res.string.kc_buzz_presence) else status
    }

@Composable
private fun buzzRoleLabel(role: String?): String? =
    when (role) {
        null -> null
        RoleTag.OWNER -> stringRes(Res.string.kc_buzz_role_owner)
        RoleTag.ADMIN -> stringRes(Res.string.kc_buzz_role_admin)
        RoleTag.MEMBER -> stringRes(Res.string.kc_buzz_role_member)
        else -> role
    }

// ---------------------------------------------------------------------------------------------
// Chat stream side events
// ---------------------------------------------------------------------------------------------

/** kind 40099: the relay's narration of a channel change, worded exactly as the chat screen words it. */
@Composable
fun RenderBuzzSystemMessageCard(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? SystemMessageEvent ?: return
    val channelId = remember(event) { event.channel() }
    KindCard(
        icon = MaterialSymbols.Info,
        title = buzzSystemMessageText(event, accountViewModel),
    ) {
        BuzzChannel(baseNote, channelId, accountViewModel, nav)
    }
}

/** kind 40100: a channel canvas, the shared Markdown document of a channel. */
@Composable
fun RenderBuzzCanvas(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? CanvasEvent ?: return
    val channelId = remember(event) { event.channel() }
    KindCard(icon = MaterialSymbols.Description, title = stringRes(Res.string.buzz_canvas_title)) {
        BuzzChannel(baseNote, channelId, accountViewModel, nav)
        KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav, quotesLeft)
    }
}

/** kind 40003: the new text of a message, then the message it replaces. */
@Composable
fun RenderBuzzMessageEdit(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? StreamMessageEditEvent ?: return
    val target = remember(event) { event.editedMessage() }
    KindCard(icon = MaterialSymbols.Edit, title = stringRes(Res.string.kc_buzz_edited)) {
        KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav, quotesLeft)
        target?.let { KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

/** kind 40004: a message pinned in its channel. */
@Composable
fun RenderBuzzMessagePinned(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? StreamMessagePinnedEvent ?: return
    val target = remember(event) { event.pinnedMessage() }
    val channelId = remember(event) { event.channel() }
    KindCard(icon = MaterialSymbols.PushPin, title = stringRes(Res.string.kc_buzz_pinned)) {
        if (target != null) {
            KindCardQuoteEvent(target, quotesLeft, backgroundColor, accountViewModel, nav)
        } else {
            BuzzChannel(baseNote, channelId, accountViewModel, nav)
        }
    }
}

/** kind 40005: a message bookmarked by its author. */
@Composable
fun RenderBuzzMessageBookmarked(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? StreamMessageBookmarkedEvent ?: return
    val target = remember(event) { event.bookmarkedMessage() }
    val channelId = remember(event) { event.channel() }
    KindCard(icon = MaterialSymbols.Bookmark, title = stringRes(Res.string.kc_buzz_bookmarked)) {
        if (target != null) {
            KindCardQuoteEvent(target, quotesLeft, backgroundColor, accountViewModel, nav)
        } else {
            BuzzChannel(baseNote, channelId, accountViewModel, nav)
        }
    }
}

/** kind 40006: a message waiting to be delivered later. */
@Composable
fun RenderBuzzMessageScheduled(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? StreamMessageScheduledEvent ?: return
    val channelId = remember(event) { event.channel() }
    KindCard(icon = MaterialSymbols.Schedule, title = stringRes(Res.string.kc_buzz_scheduled)) {
        BuzzChannel(baseNote, channelId, accountViewModel, nav)
        KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav, quotesLeft)
    }
}

/** kind 40007: a reminder for someone, optionally about a message. */
@Composable
fun RenderBuzzReminder(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? StreamReminderEvent ?: return
    val recipients = remember(event) { event.recipients() }
    val target = remember(event) { event.targetMessage() }
    KindCard(icon = MaterialSymbols.Notifications, title = stringRes(Res.string.kc_buzz_reminder)) {
        BuzzLabeledUsers(stringRes(Res.string.kc_buzz_reminder_for), recipients, accountViewModel, nav)
        KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav, quotesLeft)
        target?.let { KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

/**
 * kind 40008: a unified diff with its repo/commit/file header. The chat screen's
 * `RenderBuzzDiff` draws the whole diff; a feed card caps it so one long patch cannot fill the feed.
 */
@Composable
fun RenderBuzzDiffCard(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? StreamMessageDiffEvent ?: return
    val meta = remember(event) { event.diffMeta() }
    val description = remember(event) { event.description() ?: meta?.description }
    val channelId = remember(event) { event.channel() }
    KindCard(
        icon = MaterialSymbols.Code,
        title = meta?.filePath ?: stringRes(Res.string.kc_buzz_diff),
        subtitle = description,
    ) {
        BuzzChannel(baseNote, channelId, accountViewModel, nav)
        KindCardField(stringRes(Res.string.kc_buzz_repository), meta?.repoUrl, maxLines = 1)
        KindCardField(stringRes(Res.string.kc_buzz_branch), meta?.branch?.let { "${it.source} → ${it.target}" }, maxLines = 1)
        KindCardField(stringRes(Res.string.kc_buzz_pull_request), meta?.prNumber?.let { "#$it" })
        KindCardField(stringRes(Res.string.kc_buzz_commit), meta?.commitSha?.take(12), monospace = true)
        KindCardCode(event.content, maxLines = 16)
        if (meta?.truncated == true) {
            Text(
                text = stringRes(Res.string.buzz_diff_truncated),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** kind 40901: the relay's summary of a channel (name, purpose, counts). */
@Composable
fun RenderBuzzChannelSummary(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ChannelSummaryEvent ?: return
    val summary = remember(event) { event.summary() }
    if (summary == null) {
        RenderPrivateKindCard(baseNote, MaterialSymbols.Info)
        return
    }
    val channelId = remember(event) { event.channel() ?: summary.channelId }
    KindCard(
        icon = MaterialSymbols.Info,
        title = summary.name?.ifBlank { null } ?: stringRes(Res.string.kc_buzz_channel_summary),
        subtitle = summary.about?.ifBlank { null },
    ) {
        BuzzChannel(baseNote, channelId, accountViewModel, nav)
        KindCardField(stringRes(Res.string.kc_buzz_topic), summary.topic)
        KindCardField(stringRes(Res.string.kc_buzz_purpose), summary.purpose)
        KindCardField(stringRes(Res.string.kc_buzz_visibility), summary.visibility)
        KindCardField(stringRes(Res.string.members), summary.memberCount?.toString())
        KindCardField(stringRes(Res.string.kc_buzz_messages), summary.messageCount?.toString())
        KindCardField(stringRes(Res.string.kc_buzz_last_activity), summary.lastActivityAt?.let { timeAbsoluteNoDot(it) })
        if (summary.archived == true) KindCardChips(listOf(stringRes(Res.string.kc_buzz_archived)))
    }
}

/** kind 39005: the relay's reply count and participants for one thread, then the thread's root. */
@Composable
fun RenderBuzzThreadSummary(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ThreadSummaryEvent ?: return
    val summary = remember(event) { event.summaryOrNull() }
    if (summary == null) {
        RenderPrivateKindCard(baseNote, MaterialSymbols.Forum)
        return
    }
    val participants = remember(event) { event.linkedPubKeys() }
    val root = remember(event) { event.rootEventTag() ?: event.rootId().ifBlank { null } }
    val replies = summary.replyCount.toInt()
    KindCard(
        icon = MaterialSymbols.Forum,
        title = stringRes(Res.string.kc_buzz_thread_summary),
        subtitle = pluralStringRes(Res.plurals.kc_buzz_reply_count, replies, replies),
    ) {
        KindCardField(stringRes(Res.string.kc_buzz_last_reply), summary.lastReplyAt?.let { timeAbsoluteNoDot(it) })
        KindCardUsers(participants, accountViewModel, nav)
        root?.let { KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

// ---------------------------------------------------------------------------------------------
// Forum
// ---------------------------------------------------------------------------------------------

/** kind 45001: the opening post of a forum thread. Its content is the post itself. */
@Composable
fun RenderBuzzForumPost(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ForumPostEvent ?: return
    val channelId = remember(event) { event.channel() }
    KindCard(icon = MaterialSymbols.Forum, title = stringRes(Res.string.kc_buzz_forum_post)) {
        BuzzChannel(baseNote, channelId, accountViewModel, nav)
        KindCardText(baseNote, event.body(), backgroundColor, accountViewModel, nav, quotesLeft)
    }
}

/** kind 45003: a reply in a forum thread, then the post or reply it answers. */
@Composable
fun RenderBuzzForumComment(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ForumCommentEvent ?: return
    val parent = remember(event) { event.replyTo() ?: event.threadRoot() }
    KindCard(icon = MaterialSymbols.Forum, title = stringRes(Res.string.kc_buzz_forum_reply)) {
        KindCardText(baseNote, event.body(), backgroundColor, accountViewModel, nav, quotesLeft)
        parent?.let { KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

/** kind 45002: an up or down vote on a forum post. */
@Composable
fun RenderBuzzForumVoteCard(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ForumVoteEvent ?: return
    val isDown = remember(event) { event.direction() == VoteDirection.DOWN }
    val target = remember(event) { event.target() }
    KindCard(
        icon = if (isDown) MaterialSymbols.ArrowDownward else MaterialSymbols.ArrowUpward,
        title = stringRes(if (isDown) Res.string.kc_buzz_forum_downvote else Res.string.kc_buzz_forum_upvote),
    ) {
        target?.let { KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

// ---------------------------------------------------------------------------------------------
// Huddles
// ---------------------------------------------------------------------------------------------

/** kind 48100: someone opened an audio huddle in a channel. */
@Composable
fun RenderBuzzHuddleStarted(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? HuddleStartedEvent ?: return
    val channelId = remember(event) { event.channelId() }
    KindCard(icon = MaterialSymbols.Call, title = stringRes(Res.string.kc_buzz_huddle_started)) {
        BuzzChannel(baseNote, channelId, accountViewModel, nav)
    }
}

/** kind 48101: relay-signed, so the person who joined comes from the `p` tag. */
@Composable
fun RenderBuzzHuddleJoined(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? HuddleParticipantJoinedEvent ?: return
    val channelId = remember(event) { event.channelId() }
    val participant = remember(event) { listOfNotNull(event.participant()) }
    KindCard(icon = MaterialSymbols.Call, title = stringRes(Res.string.kc_buzz_huddle_joined)) {
        KindCardUsers(participant, accountViewModel, nav)
        BuzzChannel(baseNote, channelId, accountViewModel, nav)
    }
}

/** kind 48102: relay-signed, so the person who left comes from the `p` tag. */
@Composable
fun RenderBuzzHuddleLeft(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? HuddleParticipantLeftEvent ?: return
    val channelId = remember(event) { event.channelId() }
    val participant = remember(event) { listOfNotNull(event.participant()) }
    KindCard(icon = MaterialSymbols.CallEnd, title = stringRes(Res.string.kc_buzz_huddle_left)) {
        KindCardUsers(participant, accountViewModel, nav)
        BuzzChannel(baseNote, channelId, accountViewModel, nav)
    }
}

/** kind 48103: the huddle closed, usually when its last participant left. */
@Composable
fun RenderBuzzHuddleEnded(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? HuddleEndedEvent ?: return
    val channelId = remember(event) { event.channelId() }
    val participant = remember(event) { listOfNotNull(event.participant()) }
    KindCard(icon = MaterialSymbols.CallEnd, title = stringRes(Res.string.kc_buzz_huddle_ended)) {
        BuzzChannel(baseNote, channelId, accountViewModel, nav)
        BuzzLabeledUsers(stringRes(Res.string.kc_buzz_last_participant), participant, accountViewModel, nav)
    }
}

/** kind 48104: the relay's statement that a huddle is live right now. */
@Composable
fun RenderBuzzHuddleLiveness(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? HuddleLivenessEvent ?: return
    val channelId = remember(event) { event.channelId() }
    KindCard(icon = MaterialSymbols.Call, title = stringRes(Res.string.kc_buzz_huddle_live)) {
        BuzzChannel(baseNote, channelId, accountViewModel, nav)
    }
}

/** kind 48106: the rules agents in a channel's huddles follow. */
@Composable
fun RenderBuzzHuddleGuidelines(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? HuddleGuidelinesEvent ?: return
    val channelId = remember(event) { event.channelId() }
    KindCard(icon = MaterialSymbols.Gavel, title = stringRes(Res.string.kc_buzz_huddle_guidelines)) {
        BuzzChannel(baseNote, channelId, accountViewModel, nav)
        KindCardText(baseNote, event.guidelines(), backgroundColor, accountViewModel, nav, quotesLeft)
    }
}

/** kind 24810: an emoji burst sent inside a huddle. */
@Composable
fun RenderBuzzHuddleReaction(baseNote: Note) {
    val event = baseNote.event as? HuddleReactionEvent ?: return
    val emoji = remember(event) { event.emoji() }
    val sender = remember(event) { event.senderName() }
    KindCard(
        icon = MaterialSymbols.AddReaction,
        title = stringRes(Res.string.kc_buzz_huddle_reaction, emoji),
        subtitle = sender,
    )
}

// ---------------------------------------------------------------------------------------------
// Agent jobs
// ---------------------------------------------------------------------------------------------

/** kind 43001: someone asks an agent to do a task. */
@Composable
fun RenderBuzzJobRequest(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? JobRequestEvent ?: return
    val agent = remember(event) { listOfNotNull(event.target()) }
    val channelId = remember(event) { event.channel() }
    KindCard(icon = MaterialSymbols.Work, title = stringRes(Res.string.kc_buzz_job_request)) {
        KindCardText(baseNote, event.request(), backgroundColor, accountViewModel, nav, quotesLeft)
        BuzzLabeledUsers(stringRes(Res.string.kc_buzz_agent), agent, accountViewModel, nav)
        BuzzChannel(baseNote, channelId, accountViewModel, nav)
    }
}

/** The shared shape of the job replies: a title, an optional status, the agent's text, then the request. */
@Composable
private fun BuzzJobReplyCard(
    baseNote: Note,
    icon: MaterialSymbol,
    title: String,
    status: String?,
    text: String,
    jobRequest: HexKey?,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
    iconTint: Color = MaterialTheme.colorScheme.primary,
) {
    KindCard(icon = icon, title = title, iconTint = iconTint) {
        KindCardField(stringRes(Res.string.kc_buzz_status), status)
        KindCardText(baseNote, text, backgroundColor, accountViewModel, nav, quotesLeft)
        jobRequest?.let { KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

/** kind 43002: an agent took the job. */
@Composable
fun RenderBuzzJobAccepted(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? JobAcceptedEvent ?: return
    val request = remember(event) { event.jobRequest() }
    BuzzJobReplyCard(
        baseNote,
        MaterialSymbols.Done,
        stringRes(Res.string.kc_buzz_job_accepted),
        null,
        event.content,
        request,
        quotesLeft,
        backgroundColor,
        accountViewModel,
        nav,
    )
}

/** kind 43003: an in-flight status update from the agent. */
@Composable
fun RenderBuzzJobProgress(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? JobProgressEvent ?: return
    val request = remember(event) { event.jobRequest() }
    val status = remember(event) { event.status() }
    BuzzJobReplyCard(
        baseNote,
        MaterialSymbols.HourglassEmpty,
        stringRes(Res.string.kc_buzz_job_progress),
        status,
        event.content,
        request,
        quotesLeft,
        backgroundColor,
        accountViewModel,
        nav,
    )
}

/** kind 43004: the finished job's output. */
@Composable
fun RenderBuzzJobResult(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? JobResultEvent ?: return
    val request = remember(event) { event.jobRequest() }
    val status = remember(event) { event.status() }
    BuzzJobReplyCard(
        baseNote,
        MaterialSymbols.CheckCircle,
        stringRes(Res.string.kc_buzz_job_result),
        status,
        event.result(),
        request,
        quotesLeft,
        backgroundColor,
        accountViewModel,
        nav,
    )
}

/** kind 43005: a request to stop an in-flight job, with an optional reason. */
@Composable
fun RenderBuzzJobCancel(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? JobCancelEvent ?: return
    val request = remember(event) { event.jobRequest() }
    BuzzJobReplyCard(
        baseNote,
        MaterialSymbols.Cancel,
        stringRes(Res.string.kc_buzz_job_cancelled),
        null,
        event.reason(),
        request,
        quotesLeft,
        backgroundColor,
        accountViewModel,
        nav,
    )
}

/** kind 43006: the job failed; the content is the agent's error message. */
@Composable
fun RenderBuzzJobError(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? JobErrorEvent ?: return
    val request = remember(event) { event.jobRequest() }
    val status = remember(event) { event.status() }
    BuzzJobReplyCard(
        baseNote,
        MaterialSymbols.Error,
        stringRes(Res.string.kc_buzz_job_error),
        status,
        event.error(),
        request,
        quotesLeft,
        backgroundColor,
        accountViewModel,
        nav,
        iconTint = MaterialTheme.colorScheme.error,
    )
}

// ---------------------------------------------------------------------------------------------
// Direct messages
// ---------------------------------------------------------------------------------------------

/** kind 41010: the author asked the relay to open a DM with these people. */
@Composable
fun RenderBuzzDmOpen(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? DmOpenEvent ?: return
    val participants = remember(event) { event.participants() }
    KindCard(icon = MaterialSymbols.Chat, title = stringRes(Res.string.kc_buzz_dm_open)) {
        KindCardUsers(participants, accountViewModel, nav, max = 8)
    }
}

/** kind 41001: relay-signed confirmation that a DM exists, with everyone in it. */
@Composable
fun RenderBuzzDmCreated(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? DmCreatedEvent ?: return
    val participants = remember(event) { event.participants() }
    val dmId = remember(event) { event.dmId().ifBlank { null } }
    val channel = remember(baseNote, dmId) { findBuzzChannel(baseNote, dmId) }
    KindCard(icon = MaterialSymbols.Chat, title = stringRes(Res.string.kc_buzz_dm_created)) {
        channel?.let {
            RelayGroupChannelHeader(
                channel = it,
                modifier = MaterialTheme.colorScheme.replyModifier.padding(10.dp),
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
        KindCardUsers(participants, accountViewModel, nav, max = 9)
    }
}

/** kind 41011: someone added to an existing group DM. */
@Composable
fun RenderBuzzDmAddMember(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? DmAddMemberEvent ?: return
    val member = remember(event) { listOfNotNull(event.member()) }
    val channelId = remember(event) { event.channelId() }
    KindCard(icon = MaterialSymbols.PersonAdd, title = stringRes(Res.string.kc_buzz_dm_add_member)) {
        KindCardUsers(member, accountViewModel, nav)
        BuzzChannel(baseNote, channelId, accountViewModel, nav)
    }
}

/** kind 41012: the author hid a DM from their sidebar. */
@Composable
fun RenderBuzzDmHide(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? DmHideEvent ?: return
    val channelId = remember(event) { event.channelId() }
    KindCard(icon = MaterialSymbols.VisibilityOff, title = stringRes(Res.string.kc_buzz_dm_hide)) {
        BuzzChannel(baseNote, channelId, accountViewModel, nav)
    }
}

/** kind 30622: relay-signed snapshot of the DMs one viewer has hidden. */
@Composable
fun RenderBuzzDmVisibility(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? DmVisibilityEvent ?: return
    val viewer = remember(event) { listOfNotNull(event.viewerFromPTag()) }
    val hidden = remember(event) { event.hiddenChannels().size }
    KindCard(
        icon = MaterialSymbols.VisibilityOff,
        title = stringRes(Res.string.kc_buzz_dm_visibility),
        subtitle = pluralStringRes(Res.plurals.kc_buzz_hidden_dm_count, hidden, hidden),
    ) {
        KindCardUsers(viewer, accountViewModel, nav)
    }
}

// ---------------------------------------------------------------------------------------------
// Presence
// ---------------------------------------------------------------------------------------------

/** kind 20001: someone went online, away or offline. */
@Composable
fun RenderBuzzPresenceUpdate(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? PresenceUpdateEvent ?: return
    val status = remember(event) { event.status() }
    val subject = remember(event) { event.subjectPubKey() }
    val tint =
        when (remember(event) { event.presenceStatus() }) {
            PresenceStatus.ONLINE -> BuzzPresenceOnlineColor
            PresenceStatus.AWAY -> BuzzPresenceAwayColor
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    KindCard(icon = MaterialSymbols.Circle, title = buzzPresenceLabel(status), iconTint = tint) {
        // A relay-synthesized update is signed by the relay: the person is in the `p` tag.
        if (subject != event.pubKey) KindCardUsers(listOf(subject), accountViewModel, nav)
    }
}

/** kind 40902: the relay's bulk presence list. */
@Composable
fun RenderBuzzPresenceSnapshot(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? PresenceSnapshotEvent ?: return
    val snapshot = remember(event) { event.snapshot() }
    if (snapshot == null) {
        RenderPrivateKindCard(baseNote, MaterialSymbols.Groups)
        return
    }
    val people = remember(event) { event.linkedPubKeys() }
    val counts =
        remember(snapshot) {
            snapshot.entries
                .groupingBy { it.status }
                .eachCount()
                .toList()
        }
    val total = snapshot.entries.size
    KindCard(
        icon = MaterialSymbols.Groups,
        title = stringRes(Res.string.kc_buzz_presence),
        subtitle = pluralStringRes(Res.plurals.kc_buzz_people_count, total, total),
    ) {
        KindCardChips(counts.map { (status, count) -> buzzPresenceLabel(status) + " · " + count })
        KindCardUsers(people, accountViewModel, nav)
    }
}

/** kind 20002: someone is typing in a channel. */
@Composable
fun RenderBuzzTyping(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? TypingIndicatorEvent ?: return
    val channelId = remember(event) { event.channelId() }
    val name = observeUserNameByHex(event.pubKey, accountViewModel)
    KindCard(icon = MaterialSymbols.EditNote, title = stringRes(Res.string.buzz_typing_one, name)) {
        BuzzChannel(baseNote, channelId, accountViewModel, nav)
    }
}

// ---------------------------------------------------------------------------------------------
// Channel membership and community moderation
// ---------------------------------------------------------------------------------------------

/** kind 44101: relay-signed notice that someone was removed from a channel, and by whom. */
@Composable
fun RenderBuzzMemberRemoved(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? MemberRemovedNotificationEvent ?: return
    val target = remember(event) { event.target() }
    val actor = remember(event) { event.actor() }
    val channelId = remember(event) { event.channel() }
    KindCard(icon = MaterialSymbols.PersonRemove, title = stringRes(Res.string.kc_buzz_member_removed)) {
        KindCardUsers(listOfNotNull(target), accountViewModel, nav)
        if (actor != null && actor != target) {
            BuzzLabeledUsers(stringRes(Res.string.kc_buzz_removed_by), listOf(actor), accountViewModel, nav)
        }
        BuzzChannel(baseNote, channelId, accountViewModel, nav)
    }
}

/** kind 9040: a moderator banned someone, until a date or for good. */
@Composable
fun RenderBuzzModerationBan(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ModerationBanEvent ?: return
    val target = remember(event) { listOfNotNull(event.target()) }
    val expiresAt = remember(event) { event.expiresAt() }
    val reason = remember(event) { event.reason() }
    KindCard(
        icon = MaterialSymbols.Block,
        title = stringRes(Res.string.kc_buzz_ban),
        iconTint = MaterialTheme.colorScheme.error,
    ) {
        KindCardUsers(target, accountViewModel, nav)
        KindCardField(stringRes(Res.string.kc_buzz_until), buzzUntilText(expiresAt))
        KindCardField(stringRes(Res.string.kc_buzz_reason), reason)
    }
}

/** kind 9042: a moderator stopped someone from posting until a date. */
@Composable
fun RenderBuzzModerationTimeout(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ModerationTimeoutEvent ?: return
    val target = remember(event) { listOfNotNull(event.target()) }
    val expiresAt = remember(event) { event.expiresAt() }
    val reason = remember(event) { event.reason() }
    KindCard(icon = MaterialSymbols.Timer, title = stringRes(Res.string.kc_buzz_timeout)) {
        KindCardUsers(target, accountViewModel, nav)
        KindCardField(stringRes(Res.string.kc_buzz_until), buzzUntilText(expiresAt))
        KindCardField(stringRes(Res.string.kc_buzz_reason), reason)
    }
}

/** kind 9043: a moderator lifted a timeout early. */
@Composable
fun RenderBuzzModerationUntimeout(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ModerationUntimeoutEvent ?: return
    val target = remember(event) { listOfNotNull(event.target()) }
    KindCard(icon = MaterialSymbols.TimerOff, title = stringRes(Res.string.kc_buzz_untimeout)) {
        KindCardUsers(target, accountViewModel, nav)
    }
}

/** kind 9044: a moderator closed a kind-1984 report: the outcome, the action taken, then the report. */
@Composable
fun RenderBuzzModerationResolveReport(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ModerationResolveReportEvent ?: return
    val report = remember(event) { event.report() }
    val status = remember(event) { event.status() }
    val action = remember(event) { event.action() }
    val reason = remember(event) { event.reason() }
    val title =
        when (status) {
            StatusTag.RESOLVED -> stringRes(Res.string.kc_buzz_report_resolved)
            StatusTag.DISMISSED -> stringRes(Res.string.kc_buzz_report_dismissed)
            else -> stringRes(Res.string.kc_buzz_report_closed)
        }
    val actionText =
        when (action) {
            null -> null
            ActionTag.DELETE -> stringRes(Res.string.kc_buzz_action_delete)
            ActionTag.KICK -> stringRes(Res.string.kc_buzz_action_kick)
            ActionTag.BAN -> stringRes(Res.string.kc_buzz_action_ban)
            ActionTag.TIMEOUT -> stringRes(Res.string.kc_buzz_action_timeout)
            ActionTag.DISMISS -> stringRes(Res.string.kc_buzz_action_dismiss)
            ActionTag.ESCALATE -> stringRes(Res.string.kc_buzz_action_escalate)
            else -> action
        }
    KindCard(icon = MaterialSymbols.Gavel, title = title) {
        KindCardField(stringRes(Res.string.kc_buzz_action), actionText)
        KindCardField(stringRes(Res.string.kc_buzz_reason), reason)
        report?.let { KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

/** kind 42000: feedback a user sent to the workspace operators. */
@Composable
fun RenderBuzzProductFeedback(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ProductFeedbackEvent ?: return
    val category =
        when (val raw = remember(event) { event.category() }) {
            null -> null
            CategoryTag.BUG -> stringRes(Res.string.kc_buzz_feedback_bug)
            CategoryTag.PRAISE -> stringRes(Res.string.kc_buzz_feedback_praise)
            CategoryTag.NEEDS_WORK -> stringRes(Res.string.kc_buzz_feedback_needs_work)
            else -> raw
        }
    KindCard(icon = MaterialSymbols.Recommend, title = stringRes(Res.string.kc_buzz_feedback), subtitle = category) {
        KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav, quotesLeft)
    }
}

// ---------------------------------------------------------------------------------------------
// Relay administration
// ---------------------------------------------------------------------------------------------

/** kind 9030: an admin adds someone to the relay's member list. */
@Composable
fun RenderBuzzRelayAdminAddMember(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? RelayAdminAddMemberEvent ?: return
    val target = remember(event) { listOfNotNull(event.target()) }
    val role = remember(event) { event.role() }
    KindCard(icon = MaterialSymbols.PersonAdd, title = stringRes(Res.string.kc_buzz_relay_add_member)) {
        KindCardUsers(target, accountViewModel, nav)
        KindCardField(stringRes(Res.string.kc_buzz_role), buzzRoleLabel(role))
    }
}

/** kind 9031: an admin removes someone from the relay's member list. */
@Composable
fun RenderBuzzRelayAdminRemoveMember(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? RelayAdminRemoveMemberEvent ?: return
    val target = remember(event) { listOfNotNull(event.target()) }
    KindCard(icon = MaterialSymbols.PersonRemove, title = stringRes(Res.string.kc_buzz_relay_remove_member)) {
        KindCardUsers(target, accountViewModel, nav)
    }
}

/** kind 9032: the owner changes a member's role. */
@Composable
fun RenderBuzzRelayAdminChangeRole(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? RelayAdminChangeRoleEvent ?: return
    val target = remember(event) { listOfNotNull(event.target()) }
    val role = remember(event) { event.role() }
    KindCard(icon = MaterialSymbols.Shield, title = stringRes(Res.string.kc_buzz_relay_change_role)) {
        KindCardUsers(target, accountViewModel, nav)
        KindCardField(stringRes(Res.string.kc_buzz_role), buzzRoleLabel(role))
    }
}

/** kind 9033: an admin sets or clears the workspace icon. */
@Composable
fun RenderBuzzSetWorkspaceProfile(baseNote: Note) {
    val event = baseNote.event as? SetWorkspaceProfileEvent ?: return
    val icon = remember(event) { event.icon()?.ifBlank { null } }
    KindCard(
        icon = MaterialSymbols.Photo,
        title = stringRes(if (icon != null) Res.string.kc_buzz_workspace_icon_set else Res.string.kc_buzz_workspace_icon_cleared),
    ) {
        if (icon != null) {
            AsyncImage(
                model = icon,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Identity archival (NIP-IA)
// ---------------------------------------------------------------------------------------------

/** kind 9035: someone asks the relay to archive an identity (optionally pointing to its successor). */
@Composable
fun RenderBuzzArchiveRequest(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ArchiveRequestEvent ?: return
    val target = remember(event) { listOfNotNull(event.target()) }
    val replacedBy = remember(event) { listOfNotNull(event.replacedBy()) }
    val reason = remember(event) { event.reason() }
    KindCard(icon = MaterialSymbols.PersonOff, title = stringRes(Res.string.kc_buzz_archive_request)) {
        KindCardUsers(target, accountViewModel, nav)
        KindCardField(stringRes(Res.string.kc_buzz_reason), reason)
        BuzzLabeledUsers(stringRes(Res.string.kc_buzz_replaced_by), replacedBy, accountViewModel, nav)
        KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav, quotesLeft)
    }
}

/** kind 9036: someone asks the relay to lift an archive. */
@Composable
fun RenderBuzzUnarchiveRequest(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? UnarchiveRequestEvent ?: return
    val target = remember(event) { listOfNotNull(event.target()) }
    val reason = remember(event) { event.reason() }
    KindCard(icon = MaterialSymbols.Person, title = stringRes(Res.string.kc_buzz_unarchive_request)) {
        KindCardUsers(target, accountViewModel, nav)
        KindCardField(stringRes(Res.string.kc_buzz_reason), reason)
        KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav, quotesLeft)
    }
}

/** kind 8002: relay-signed record that an identity was archived, with who asked and on what grounds. */
@Composable
fun RenderBuzzIdentityArchived(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ArchivedIdentityEvent ?: return
    val target = remember(event) { event.target() }
    val consent = remember(event) { event.consent() }
    val replacedBy = remember(event) { listOfNotNull(event.replacedBy()) }
    val reason = remember(event) { event.reason() }
    val requester = remember(consent, target) { listOfNotNull(consent?.actorPubKey?.takeIf { it != target }) }
    KindCard(icon = MaterialSymbols.PersonOff, title = stringRes(Res.string.kc_buzz_identity_archived)) {
        KindCardUsers(listOfNotNull(target), accountViewModel, nav)
        KindCardField(stringRes(Res.string.kc_buzz_reason), reason)
        KindCardField(stringRes(Res.string.kc_buzz_consent), consent?.path)
        BuzzLabeledUsers(stringRes(Res.string.kc_buzz_requested_by), requester, accountViewModel, nav)
        BuzzLabeledUsers(stringRes(Res.string.kc_buzz_replaced_by), replacedBy, accountViewModel, nav)
        KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav, quotesLeft)
    }
}

/** kind 8003: relay-signed record that an archived identity was restored. */
@Composable
fun RenderBuzzIdentityUnarchived(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? UnarchivedIdentityEvent ?: return
    val target = remember(event) { event.target() }
    val consent = remember(event) { event.consent() }
    val reason = remember(event) { event.reason() }
    val requester = remember(consent, target) { listOfNotNull(consent?.actorPubKey?.takeIf { it != target }) }
    KindCard(icon = MaterialSymbols.Person, title = stringRes(Res.string.kc_buzz_identity_unarchived)) {
        KindCardUsers(listOfNotNull(target), accountViewModel, nav)
        KindCardField(stringRes(Res.string.kc_buzz_reason), reason)
        KindCardField(stringRes(Res.string.kc_buzz_consent), consent?.path)
        BuzzLabeledUsers(stringRes(Res.string.kc_buzz_requested_by), requester, accountViewModel, nav)
        KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav, quotesLeft)
    }
}

/** kind 13535: the relay's current list of archived identities. */
@Composable
fun RenderBuzzArchivedIdentitiesList(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ArchivedIdentitiesListEvent ?: return
    val identities = remember(event) { event.archivedIdentities() }
    KindCard(
        icon = MaterialSymbols.PersonOff,
        title = stringRes(Res.string.kc_buzz_archived_identities),
        subtitle = pluralStringRes(Res.plurals.kc_buzz_identities_count, identities.size, identities.size),
    ) {
        KindCardUsers(identities, accountViewModel, nav)
    }
}
