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
package com.vitorpamplona.amethyst.commons.model

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.nip88Polls.PollResponsesCache
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoHorizontalEvent
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoVerticalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent

/**
 * Type-specific content attached to a [Note] when its event arrives from a relay.
 *
 * This sealed interface solves the "stub problem": we can't know a Note's event type
 * at creation time (stubs are created when reactions/replies reference an event we
 * haven't seen yet). The [NoteContent] is set on [Note.content] during [Note.loadEvent].
 *
 * Each subclass carries only the data relevant to its event type, avoiding the memory
 * waste of storing poll responses, edit histories, or decrypted content on every Note.
 */
sealed interface NoteContent {
    /**
     * Lazily-parsed rich text content (images, videos, OpenGraph previews, custom emoji).
     * Replaces the external [CachedRichTextParser] LruCache by co-locating parsed
     * content with its Note, so it shares the Note's lifecycle.
     */
    var parsedContent: RichTextViewerState?
}

/** Kind 1 text notes. Stores edit history (TextNoteModificationEvent). */
@Stable
class TextNoteContent(
    override var parsedContent: RichTextViewerState? = null,
    var edits: List<Note> = emptyList(),
) : NoteContent

/** Kind 6 / Kind 16 reposts and community post approvals. */
@Stable
class RepostContent(
    override var parsedContent: RichTextViewerState? = null,
) : NoteContent

/** NIP-88 polls and experimental zap polls. Stores vote responses. */
@Stable
class PollContent(
    override var parsedContent: RichTextViewerState? = null,
    val responses: PollResponsesCache = PollResponsesCache(),
) : NoteContent

/**
 * Encrypted messages: NIP-04 DMs, NIP-17 chat messages, sealed gossip, gift wraps.
 * Caches decrypted content so we don't re-decrypt on every recomposition.
 */
@Stable
class EncryptedContent(
    override var parsedContent: RichTextViewerState? = null,
    var decryptedContent: String? = null,
) : NoteContent

/** NIP-23 long-form content (kind 30023). Supports edits like text notes. */
@Stable
class LongFormContent(
    override var parsedContent: RichTextViewerState? = null,
    var edits: List<Note> = emptyList(),
) : NoteContent

/** NIP-53 live activities (kind 30311). */
@Stable
class LiveActivityContent(
    override var parsedContent: RichTextViewerState? = null,
) : NoteContent

/** NIP-68 pictures, NIP-71 videos, NIP-94 file headers, audio tracks. */
@Stable
class MediaContent(
    override var parsedContent: RichTextViewerState? = null,
) : NoteContent

/** NIP-22 comments (kind 1111). Supports edits. */
@Stable
class CommentContent(
    override var parsedContent: RichTextViewerState? = null,
    var edits: List<Note> = emptyList(),
) : NoteContent

/** NIP-28 public chat channel messages. */
@Stable
class ChannelMessageContent(
    override var parsedContent: RichTextViewerState? = null,
) : NoteContent

/** NIP-99 classifieds, NIP-52 calendar events, NIP-54 wiki, NIP-84 highlights, etc. */
@Stable
class StructuredContent(
    override var parsedContent: RichTextViewerState? = null,
) : NoteContent

/** Fallback for event types without specific content needs. */
@Stable
class GenericContent(
    override var parsedContent: RichTextViewerState? = null,
) : NoteContent

/** Creates the appropriate [NoteContent] subclass based on the event type. */
fun createNoteContent(event: Event): NoteContent =
    when (event) {
        is TextNoteEvent -> TextNoteContent()
        is PollEvent -> PollContent()
        is ZapPollEvent -> PollContent()
        is PrivateDmEvent -> EncryptedContent()
        is ChatMessageEvent -> EncryptedContent()
        is GiftWrapEvent -> EncryptedContent()
        is LongTextNoteEvent -> LongFormContent()
        is CommentEvent -> CommentContent()
        is LiveActivitiesEvent -> LiveActivityContent()
        is PictureEvent -> MediaContent()
        is VideoHorizontalEvent -> MediaContent()
        is VideoVerticalEvent -> MediaContent()
        is VideoNormalEvent -> MediaContent()
        is FileHeaderEvent -> MediaContent()
        is AudioTrackEvent -> MediaContent()
        is AudioHeaderEvent -> MediaContent()
        is RepostEvent -> RepostContent()
        is GenericRepostEvent -> RepostContent()
        is CommunityPostApprovalEvent -> RepostContent()
        is ChannelMessageEvent -> ChannelMessageContent()
        else -> GenericContent()
    }
