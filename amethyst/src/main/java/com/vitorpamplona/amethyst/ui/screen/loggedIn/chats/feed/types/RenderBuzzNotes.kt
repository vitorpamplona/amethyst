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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.types

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzWorkspaceStates
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.layouts.ChatSystemMessage
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.buzz.forum.ForumVoteEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleEndedEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleParticipantJoinedEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleParticipantLeftEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleStartedEvent
import com.vitorpamplona.quartz.buzz.jobs.JobAcceptedEvent
import com.vitorpamplona.quartz.buzz.jobs.JobCancelEvent
import com.vitorpamplona.quartz.buzz.jobs.JobErrorEvent
import com.vitorpamplona.quartz.buzz.jobs.JobProgressEvent
import com.vitorpamplona.quartz.buzz.jobs.JobRequestEvent
import com.vitorpamplona.quartz.buzz.jobs.JobResultEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageDiffEvent
import com.vitorpamplona.quartz.buzz.stream.SystemMessageEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip29RelayGroups.groupId

/**
 * Observes the newest kind-40003 edit overlaying [note], recomposing when new edits
 * arrive. Returns null when the message is unedited or has no channel scope.
 *
 * Resolution goes through [BuzzWorkspaceStates] keyed by the note's `h` channel id
 * (a Buzz UUID) rather than any channel object: the state exists independently of
 * when — or whether — the channel materialized, so a row composed before the first
 * edit arrived still starts rendering overlays the moment one lands.
 */
@Composable
fun observeBuzzEdit(note: Note): Note? {
    // Key on note.event, not note: LocalCache mutates a Note in place, so keying on the Note instance
    // would cache a null groupId taken before the event populated and never recompute for that row.
    val channelId = remember(note.event) { note.event?.groupId() } ?: return null
    val state = remember(channelId) { BuzzWorkspaceStates.getOrCreate(channelId) }
    // Subscribing to the version counter is what re-runs editFor on new arrivals.
    val version by state.editUpdates.collectAsState()
    return remember(note, version) { state.editFor(note.idHex) }
}

/**
 * A Buzz stream message whose content has been superseded by a kind-40003 edit:
 * renders the NEWEST edit's content (never the stale original) plus an "(edited)"
 * marker, mirroring Buzz's own last-write-wins presentation.
 */
@Composable
fun RenderBuzzEditedNote(
    note: Note,
    editNote: Note,
    canPreview: Boolean,
    innerQuote: Boolean,
    bgColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // The edit note may still be loading; fall back to the original rendering rather
    // than committing to an edited branch that would show a blank row.
    val content = editNote.event?.content
    if (content == null) {
        RenderRegularTextNote(note, canPreview, innerQuote, bgColor, accountViewModel, nav)
        return
    }
    val tags = remember(note.event) { note.event?.tags?.toImmutableListOfLists() ?: EmptyTagList }

    Column {
        TranslatableRichTextViewer(
            content = content,
            canPreview = canPreview,
            quotesLeft = if (innerQuote) 0 else 1,
            modifier = Modifier,
            tags = tags,
            backgroundColor = bgColor,
            id = note.idHex,
            callbackUri = note.toNostrUri(),
            authorPubKey = note.author?.pubkeyHex,
            accountViewModel = accountViewModel,
            nav = nav,
        )
        Text(
            text = stringRes(R.string.buzz_message_edited),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
        )
    }
}

/**
 * A Buzz kind-40099 system message ("X joined", "channel created", "topic changed"):
 * narrates the room rather than speaking in it, so it renders as a centered system
 * line like the NIP-28 admin events, from the relay-signed JSON payload.
 */
@Composable
fun RenderBuzzSystemMessage(note: Note) {
    val event = note.event as? SystemMessageEvent ?: return
    // Relay-emitted machine text (join/leave/topic); shown as-is rather than through
    // string resources — the payload vocabulary is Buzz's, not ours to translate yet.
    val text =
        remember(event) {
            val payload = event.payload()
            when (payload?.type) {
                "topic_changed" -> payload.topic?.let { "topic: $it" } ?: "topic changed"
                "purpose_changed" -> payload.purpose?.let { "purpose: $it" } ?: "purpose changed"
                else -> payload?.type?.replace('_', ' ')
            } ?: event.content.take(120)
        }

    ChatSystemMessage(text = text)
}

/**
 * True for the Buzz agent-job and huddle lifecycle kinds that [RenderBuzzActivityRow]
 * narrates as a centered system line rather than a chat bubble. Huddle events in
 * particular MUST be caught here — their `content` is JSON, so rendering them as a plain
 * chat message would show raw `{"ephemeral_channel_id":…}`.
 */
fun isBuzzActivityRow(event: Event?): Boolean =
    event is JobRequestEvent ||
        event is JobAcceptedEvent ||
        event is JobProgressEvent ||
        event is JobResultEvent ||
        event is JobCancelEvent ||
        event is JobErrorEvent ||
        event is HuddleStartedEvent ||
        event is HuddleParticipantJoinedEvent ||
        event is HuddleParticipantLeftEvent ||
        event is HuddleEndedEvent

/**
 * Narrates a Buzz agent-job or huddle lifecycle event as a centered system line. The
 * label is derived from the kind; job progress/result/error also append a short content
 * snippet (the human-readable status/result/error the agent wrote).
 */
@Composable
fun RenderBuzzActivityRow(note: Note) {
    val event = note.event ?: return
    val text =
        remember(event) {
            when (event) {
                is JobRequestEvent -> "⚙ job requested" + event.request().snippet()
                is JobAcceptedEvent -> "⚙ job accepted"
                is JobProgressEvent -> "⚙ job progress" + (event.status()?.let { ": $it" } ?: "") + event.content.snippet()
                is JobResultEvent -> "⚙ job result" + event.result().snippet()
                is JobCancelEvent -> "⚙ job cancelled"
                is JobErrorEvent -> "⚠ job error" + event.error().snippet()
                is HuddleStartedEvent -> "🔊 huddle started"
                is HuddleParticipantJoinedEvent -> "🔊 someone joined the huddle"
                is HuddleParticipantLeftEvent -> "🔊 someone left the huddle"
                is HuddleEndedEvent -> "🔊 huddle ended"
                else -> event.content.take(120)
            }
        }
    ChatSystemMessage(text = text)
}

/** A short one-line snippet of free-text content appended after a label, or "" if blank. */
private fun String.snippet(): String {
    val oneLine = trim().replace('\n', ' ')
    if (oneLine.isEmpty()) return ""
    return ": " + if (oneLine.length > 80) oneLine.take(80) + "…" else oneLine
}

/**
 * A Buzz kind-40008 stream diff: a code/text diff pushed into the channel. Renders the
 * repo/commit/file header from the `DiffMeta` tags plus the diff body in a monospace,
 * horizontally-scrollable block, rather than the plain-text fallback (which mangles the
 * `+`/`-` gutters into wrapped prose).
 */
@Composable
fun RenderBuzzDiff(note: Note) {
    val event = note.event as? StreamMessageDiffEvent ?: return
    val meta = remember(event) { event.diffMeta() }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            val header =
                remember(meta) {
                    meta?.let {
                        buildString {
                            it.filePath?.let(::append)
                            it.language?.let { lang -> append(if (isEmpty()) lang else "  ·  $lang") }
                            it.prNumber?.let { pr -> append("  ·  PR #$pr") }
                            it.commitSha
                                ?.takeIf { sha -> sha.isNotBlank() }
                                ?.let { sha -> append("  ·  ${sha.take(8)}") }
                        }.ifBlank { null }
                    }
                }
            if (header != null) {
                Text(
                    text = header,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = event.content,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            )
            if (meta?.truncated == true) {
                Text(
                    text = stringRes(R.string.buzz_diff_truncated),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

/** A Buzz kind-45002 forum vote: a lightweight up/down signal, shown as a system line. */
@Composable
fun RenderBuzzForumVote(note: Note) {
    val event = note.event as? ForumVoteEvent ?: return
    // Content is the vote token ("+"/"-" or similar); show a compact glyph line.
    val text = remember(event) { if (event.content.trim().startsWith("-")) "▼ downvoted a post" else "▲ upvoted a post" }
    ChatSystemMessage(text = text)
}
