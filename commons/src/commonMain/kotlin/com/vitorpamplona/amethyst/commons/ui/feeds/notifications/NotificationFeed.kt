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
package com.vitorpamplona.amethyst.commons.ui.feeds.notifications

import com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers
import com.vitorpamplona.quartz.experimental.attestations.request.AttestationRequestEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.music.playlist.MusicPlaylistEvent
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip58Badges.award.BadgeAwardEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.nip64Chess.challenge.accept.LiveChessGameAcceptEvent
import com.vitorpamplona.quartz.nip64Chess.move.LiveChessMoveEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoHorizontalEvent
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent
import com.vitorpamplona.quartz.nip71Video.VideoVerticalEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceReplyEvent
import com.vitorpamplona.quartz.nipA4PublicMessages.PublicMessageEvent
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent
import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/*
 * Event-level notification-feed logic, shared between the Android
 * `LocalCache`/`Note` graph (`NotificationFeedFilter`) and clients that only
 * have raw `Event`s (e.g. Amy CLI against a file-backed event store).
 *
 * A notification is, at heart, an event that p-tags you. Amethyst's "Global"
 * notification mode keeps exactly that — every event in the notification kind
 * set that tags the user, minus your own (zaps excepted) and muted authors.
 * Its "Selected" mode adds the per-kind `tagsAnEventByUser` relevance
 * heuristics, which walk the `Note` reply graph (reaction/repost target
 * authorship, reply parents, citations) and so are not reproducible against a
 * raw Event store; those stay Android-only. See the plan for the exact gap.
 */

/**
 * Addressable (parameterized-replaceable) kinds that can show up as
 * notifications. Kept distinct because Android scans the addressable cache
 * separately, but folded into [NotificationFeedKinds] for the relay REQ.
 */
val NotificationFeedAddressableKinds: List<Int> =
    listOf(
        AudioTrackEvent.KIND,
        MusicTrackEvent.KIND,
        MusicPlaylistEvent.KIND,
        PodcastEpisodeEvent.KIND,
        PodcastMetadataEvent.KIND,
        CalendarTimeSlotEvent.KIND,
        CalendarDateSlotEvent.KIND,
        CalendarRSVPEvent.KIND,
        ClassifiedsEvent.KIND,
        LiveActivitiesEvent.KIND,
        LiveChessGameAcceptEvent.KIND,
        LiveChessMoveEvent.KIND,
        LongTextNoteEvent.KIND,
        NipTextEvent.KIND,
        VideoVerticalEvent.KIND,
        VideoHorizontalEvent.KIND,
        WikiNoteEvent.KIND,
        AttestationRequestEvent.KIND,
    )

/**
 * Every event kind the notifications feed admits — drives both the relay REQ
 * filter and the [isNotificationRenderableKind] membership test. Single source
 * of truth: the Android `NotificationFeedFilter.NOTIFICATION_KINDS` references
 * this.
 */
val NotificationFeedKinds: Set<Int> =
    setOf(
        BadgeAwardEvent.KIND,
        ChannelMessageEvent.KIND,
        ChatMessageEvent.KIND,
        ChatMessageEncryptedFileHeaderEvent.KIND,
        CommentEvent.KIND,
        GenericRepostEvent.KIND,
        GitIssueEvent.KIND,
        GitPatchEvent.KIND,
        HighlightEvent.KIND,
        TextNoteEvent.KIND,
        ReactionEvent.KIND,
        RepostEvent.KIND,
        LnZapEvent.KIND,
        NutzapEvent.KIND,
        OnchainZapEvent.KIND,
        LiveActivitiesChatMessageEvent.KIND,
        PictureEvent.KIND,
        PollEvent.KIND,
        ZapPollEvent.KIND,
        PrivateDmEvent.KIND,
        PublicMessageEvent.KIND,
        VideoNormalEvent.KIND,
        VideoShortEvent.KIND,
        VoiceEvent.KIND,
        VoiceReplyEvent.KIND,
    ) + NotificationFeedAddressableKinds

/**
 * Whether an event's kind is one the notifications feed can show. The
 * `returns(true) implies non-null` contract lets it gate a chain whose tail
 * dereferences the (non-null) event.
 */
@OptIn(ExperimentalContracts::class)
fun Event?.isNotificationRenderableKind(): Boolean {
    contract { returns(true) implies (this@isNotificationRenderableKind != null) }
    return this != null && kind in NotificationFeedKinds
}

/**
 * The Event-level subset of `NotificationFeedFilter.acceptableEvent` that
 * reproduces Amethyst's **Global** notification mode: a notification is an
 * in-kind event that p-tags [myPubkey], is not your own (zaps excepted, since
 * they can be self-directed through a provider), is not from a muted/blocked
 * author, and is not stamped in the future.
 *
 * The Note-graph parts — per-kind `tagsAnEventByUser` relevance (Selected
 * mode), muted-thread resolution for reaction/zap/repost targets, and
 * decrypted-DM-content hiding — are intentionally not reproduced here; see the
 * plan's "known parity gaps".
 *
 * @param myPubkey the account being notified.
 * @param hidden mute / block state (NIP-51); only the author sets are used.
 * @param now upper time bound; events stamped after it are dropped.
 */
class NotificationFeedParams(
    val myPubkey: HexKey,
    val hidden: LiveHiddenUsers,
    val now: Long = TimeUtils.oneMinuteFromNow(),
) {
    fun tagsMe(event: Event): Boolean = event.isTaggedUser(myPubkey)

    fun isNotSelf(event: Event): Boolean = event is LnZapEvent || event.pubKey != myPubkey

    fun isNotHidden(event: Event): Boolean = !hidden.isUserHidden(event.pubKey)

    fun isNotInTheFuture(event: Event): Boolean = event.createdAt <= now

    /** Full Global-mode notification acceptance for one event. */
    fun match(event: Event): Boolean =
        event.isNotificationRenderableKind() &&
            tagsMe(event) &&
            isNotSelf(event) &&
            isNotHidden(event) &&
            isNotInTheFuture(event)
}

/** Newest-first ordering with a stable `id` tiebreaker, matching the Android feed. */
val NotificationFeedOrder: Comparator<Event> =
    compareByDescending<Event> { it.createdAt }.thenBy { it.id }

/** Dedup by id and apply [NotificationFeedOrder]. */
fun Collection<Event>.sortedByNotificationFeedOrder(): List<Event> = distinctBy { it.id }.sortedWith(NotificationFeedOrder)
