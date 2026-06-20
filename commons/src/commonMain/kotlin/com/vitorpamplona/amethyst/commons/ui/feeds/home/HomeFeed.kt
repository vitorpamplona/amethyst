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
package com.vitorpamplona.amethyst.commons.ui.feeds.home

import com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers
import com.vitorpamplona.amethyst.commons.ui.feeds.isRenderableRepost
import com.vitorpamplona.quartz.experimental.agora.FundraiserEvent
import com.vitorpamplona.quartz.experimental.attestations.attestation.AttestationEvent
import com.vitorpamplona.quartz.experimental.attestations.proficiency.AttestorProficiencyEvent
import com.vitorpamplona.quartz.experimental.attestations.recommendation.AttestorRecommendationEvent
import com.vitorpamplona.quartz.experimental.attestations.request.AttestationRequestEvent
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.birdstar.BirdexEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.music.playlist.MusicPlaylistEvent
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hasMoreHashtagsThan
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip10Notes.threadRootIdOrSelf
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip64Chess.end.LiveChessGameEndEvent
import com.vitorpamplona.quartz.nip64Chess.game.ChessGameEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/*
 * Event-level home-feed logic, shared between the Android `LocalCache`/`Note`
 * graph (`HomeNewThreadFeedFilter`) and clients that only have raw `Event`s
 * (e.g. Amy CLI against a file-backed event store).
 *
 * The Android home feed is reactive and `Note`-graph-bound; the *inclusion
 * rules* underneath it are not. This file lifts those rules to `Event` so a
 * single definition decides what belongs on the home feed regardless of
 * whether a Note graph exists.
 */

/**
 * The event kinds the home feed requests and renders. Mirrors the type set
 * accepted by [isHomeFeedRenderableKind] and drives the relay REQ filter so a
 * subscription only pulls what the feed can show.
 */
val HomeFeedKinds: List<Int> =
    listOf(
        TextNoteEvent.KIND,
        RepostEvent.KIND,
        GenericRepostEvent.KIND,
        ClassifiedsEvent.KIND,
        FundraiserEvent.KIND,
        BirdexEvent.KIND,
        LongTextNoteEvent.KIND,
        WikiNoteEvent.KIND,
        ZapPollEvent.KIND,
        PollEvent.KIND,
        HighlightEvent.KIND,
        InteractiveStoryPrologueEvent.KIND,
        CommentEvent.KIND,
        AudioTrackEvent.KIND,
        MusicTrackEvent.KIND,
        MusicPlaylistEvent.KIND,
        PodcastEpisodeEvent.KIND,
        PodcastMetadataEvent.KIND,
        VoiceEvent.KIND,
        AudioHeaderEvent.KIND,
        ChessGameEvent.KIND,
        LiveChessGameEndEvent.KIND,
        AttestationEvent.KIND,
        AttestationRequestEvent.KIND,
        AttestorRecommendationEvent.KIND,
        AttestorProficiencyEvent.KIND,
    )

/**
 * Whether an event's *type* (and, for long-form/wiki, non-empty content) is
 * renderable on the home feed. This is the kind half of
 * `HomeNewThreadFeedFilter.acceptableEvent`, lifted to [Event] so Android and
 * Amy share one definition. Mute / thread / author / time filtering is
 * applied separately (see [HomeFeedParams]).
 *
 * The `returns(true) implies non-null` contract lets it replace the
 * `is TextNoteEvent || …` allow-list in a feed's acceptance chain without
 * losing the chain's non-null smart-cast (the `&& filterParams.match(…)` tail
 * relies on it).
 */
@OptIn(ExperimentalContracts::class)
fun Event?.isHomeFeedRenderableKind(): Boolean {
    contract { returns(true) implies (this@isHomeFeedRenderableKind != null) }
    return this is TextNoteEvent ||
        this is ClassifiedsEvent ||
        this is FundraiserEvent ||
        this is BirdexEvent ||
        this.isRenderableRepost() ||
        (this is LongTextNoteEvent && this.content.isNotEmpty()) ||
        (this is WikiNoteEvent && this.content.isNotEmpty()) ||
        this is ZapPollEvent ||
        this is PollEvent ||
        this is HighlightEvent ||
        this is InteractiveStoryPrologueEvent ||
        this is CommentEvent ||
        this is AudioTrackEvent ||
        this is MusicTrackEvent ||
        this is MusicPlaylistEvent ||
        this is PodcastEpisodeEvent ||
        this is PodcastMetadataEvent ||
        this is VoiceEvent ||
        this is AudioHeaderEvent ||
        this is ChessGameEvent ||
        this is LiveChessGameEndEvent ||
        this is AttestationEvent ||
        this is AttestationRequestEvent ||
        this is AttestorRecommendationEvent ||
        this is AttestorProficiencyEvent
}

/**
 * Event-level equivalent of `Note.isNewThread()`: top-level posts and reposts
 * only. A root note is its own thread root, so `threadRootIdOrSelf() == id`
 * detects "has no reply parent" without walking a Note graph; reposts are
 * always new threads; chat messages and root-scoped comments never are.
 */
fun Event.isNewThreadEvent(): Boolean =
    (
        this is RepostEvent ||
            this is GenericRepostEvent ||
            threadRootIdOrSelf() == id
    ) &&
        !(this is CommentEvent && this.hasRootScopeIdentifier()) &&
        this !is ChannelMessageEvent &&
        this !is LiveActivitiesChatMessageEvent

/** Newest-first ordering with a stable `id` tiebreaker, matching the Android feed. */
val HomeFeedOrder: Comparator<Event> =
    compareByDescending<Event> { it.createdAt }.thenBy { it.id }

/** Dedup by id and apply [HomeFeedOrder]. */
fun Collection<Event>.sortedByHomeFeedOrder(): List<Event> = distinctBy { it.id }.sortedWith(HomeFeedOrder)

/**
 * The Event-level subset of `FilterByListParams` that the home feed needs:
 * author-follow membership + mute / muted-thread / future / excessive-hashtag
 * clamps. The Android `IFeedTopNavFilter` (followed hashtags / geohashes /
 * communities resolved through `LocalCache`'s outbox model) is intentionally
 * *not* reproduced here — see the plan's "known parity gaps".
 *
 * @param followedAuthors pubkeys whose top-level posts belong on the feed.
 * @param hidden mute / block / muted-thread state (NIP-51).
 * @param now upper time bound; events stamped after it are dropped.
 */
class HomeFeedParams(
    val followedAuthors: Set<HexKey>,
    val hidden: LiveHiddenUsers,
    val now: Long = TimeUtils.oneMinuteFromNow(),
) {
    fun isAuthorAccepted(pubKey: HexKey): Boolean = pubKey in followedAuthors

    fun isNotHidden(pubKey: HexKey): Boolean = !hidden.isUserHidden(pubKey)

    fun isNotInMutedThread(event: Event): Boolean = hidden.mutedThreads.isEmpty() || !hidden.isThreadMuted(event.threadRootIdOrSelf())

    fun isNotInTheFuture(event: Event): Boolean = event.createdAt <= now

    fun hasExcessiveHashtags(event: Event): Boolean = hidden.maxHashtagLimit > 0 && event.hasMoreHashtagsThan(hidden.maxHashtagLimit)

    /** Full home-feed acceptance for one event: renderable kind + new thread + list filters. */
    fun match(event: Event): Boolean =
        event.isHomeFeedRenderableKind() &&
            event.isNewThreadEvent() &&
            isAuthorAccepted(event.pubKey) &&
            isNotHidden(event.pubKey) &&
            isNotInMutedThread(event) &&
            isNotInTheFuture(event) &&
            !hasExcessiveHashtags(event)
}
