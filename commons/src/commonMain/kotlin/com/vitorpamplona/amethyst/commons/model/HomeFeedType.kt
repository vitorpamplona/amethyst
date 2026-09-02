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

import com.vitorpamplona.quartz.experimental.agora.FundraiserEvent
import com.vitorpamplona.quartz.experimental.attestations.attestation.AttestationEvent
import com.vitorpamplona.quartz.experimental.attestations.proficiency.AttestorProficiencyEvent
import com.vitorpamplona.quartz.experimental.attestations.recommendation.AttestorRecommendationEvent
import com.vitorpamplona.quartz.experimental.attestations.request.AttestationRequestEvent
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.birdstar.BirdDetectionEvent
import com.vitorpamplona.quartz.experimental.birdstar.BirdexEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.music.playlist.MusicPlaylistEvent
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip64Chess.challenge.offer.LiveChessGameChallengeEvent
import com.vitorpamplona.quartz.nip64Chess.end.LiveChessGameEndEvent
import com.vitorpamplona.quartz.nip64Chess.game.ChessGameEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoHorizontalEvent
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent
import com.vitorpamplona.quartz.nip71Video.VideoVerticalEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceReplyEvent
import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent

/**
 * The distinct event-kind groups the Home feed downloads (in the relay assembler) and renders (in
 * the DAL). Each is independently toggleable in Settings › Home: turning one off both drops its
 * kinds from the always-on home relay filters AND hides them from the New Threads / Conversations /
 * Everything tabs.
 *
 * [code] is the stable on-disk identifier (do NOT rename — it is what [encode]/[decode] persist);
 * the enum ordinal is never stored, so entries may be reordered freely. [kinds] are the Nostr event
 * kinds this group governs; they must stay disjoint across entries so a single toggle owns each kind.
 */
enum class HomeFeedType(
    val code: String,
    val kinds: List<Int>,
) {
    TEXT_NOTES("text_notes", listOf(TextNoteEvent.KIND)),
    REPOSTS("reposts", listOf(RepostEvent.KIND, GenericRepostEvent.KIND)),
    COMMENTS("comments", listOf(CommentEvent.KIND)),
    PICTURES("pictures", listOf(PictureEvent.KIND)),
    VIDEOS("videos", listOf(VideoNormalEvent.KIND, VideoHorizontalEvent.KIND)),
    SHORTS("shorts", listOf(VideoShortEvent.KIND, VideoVerticalEvent.KIND)),
    ARTICLES("articles", listOf(LongTextNoteEvent.KIND)),
    WIKI("wiki", listOf(WikiNoteEvent.KIND)),
    HIGHLIGHTS("highlights", listOf(HighlightEvent.KIND)),
    POLLS("polls", listOf(PollEvent.KIND, ZapPollEvent.KIND, PollResponseEvent.KIND)),
    CLASSIFIEDS("classifieds", listOf(ClassifiedsEvent.KIND)),
    TORRENTS("torrents", listOf(TorrentEvent.KIND)),
    VOICE("voice", listOf(VoiceEvent.KIND, VoiceReplyEvent.KIND)),
    LIVE_ACTIVITIES("live_activities", listOf(LiveActivitiesEvent.KIND, LiveActivitiesChatMessageEvent.KIND)),
    EPHEMERAL_CHAT("ephemeral_chat", listOf(EphemeralChatEvent.KIND)),
    INTERACTIVE_STORIES("interactive_stories", listOf(InteractiveStoryPrologueEvent.KIND)),
    CHESS("chess", listOf(ChessGameEvent.KIND, LiveChessGameChallengeEvent.KIND, LiveChessGameEndEvent.KIND)),
    BIRDS("birds", listOf(BirdDetectionEvent.KIND, BirdexEvent.KIND)),
    ATTESTATIONS(
        "attestations",
        listOf(
            AttestationEvent.KIND,
            AttestationRequestEvent.KIND,
            AttestorRecommendationEvent.KIND,
            AttestorProficiencyEvent.KIND,
        ),
    ),
    NIPS("nips", listOf(NipTextEvent.KIND)),
    MUSIC("music", listOf(AudioTrackEvent.KIND, MusicTrackEvent.KIND, MusicPlaylistEvent.KIND, AudioHeaderEvent.KIND)),
    PODCASTS("podcasts", listOf(PodcastEpisodeEvent.KIND, PodcastMetadataEvent.KIND)),
    FUNDRAISERS("fundraisers", listOf(FundraiserEvent.KIND)),
    ;

    companion object {
        /** Every group, enabled by default so a fresh (or never-customized) account loads everything. */
        val ALL: Set<HomeFeedType> = entries.toSet()

        fun fromCode(code: String?): HomeFeedType? = entries.firstOrNull { it.code == code }

        /** Serializes a set of groups as their comma-joined [code]s, for SharedPreferences. */
        fun encode(types: Set<HomeFeedType>): String = types.joinToString(",") { it.code }

        /** Parses a comma-joined [code] list back to a set, dropping any unknown codes. */
        fun decode(joined: String?): Set<HomeFeedType> =
            joined
                ?.split(",")
                ?.mapNotNull { fromCode(it.trim()) }
                ?.toSet()
                ?: emptySet()

        /**
         * The event kinds to drop from the home relay filters and the home DAL, given the currently
         * [enabled] set. A kind stays live if ANY enabled group still owns it (guards against a
         * future overlap between two groups), so disabling one group never silently hides a kind a
         * still-enabled group also wants.
         */
        fun disabledKinds(enabled: Set<HomeFeedType>): Set<Int> {
            if (enabled.size == ALL.size) return emptySet()
            val enabledKinds = enabled.flatMapTo(HashSet()) { it.kinds }
            return (ALL - enabled).flatMapTo(HashSet()) { it.kinds }.apply { removeAll(enabledKinds) }
        }
    }
}
