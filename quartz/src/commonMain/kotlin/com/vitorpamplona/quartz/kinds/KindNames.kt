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
package com.vitorpamplona.quartz.kinds

import com.vitorpamplona.quartz.experimental.attestations.attestation.AttestationEvent
import com.vitorpamplona.quartz.experimental.attestations.proficiency.AttestorProficiencyEvent
import com.vitorpamplona.quartz.experimental.attestations.recommendation.AttestorRecommendationEvent
import com.vitorpamplona.quartz.experimental.attestations.request.AttestationRequestEvent
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.edits.TextNoteModificationEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.experimental.ephemChat.list.EphemeralChatListEvent
import com.vitorpamplona.quartz.experimental.fitness.workout.WorkoutRecordEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryReadingStateEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStorySceneEvent
import com.vitorpamplona.quartz.experimental.medical.FhirResourceEvent
import com.vitorpamplona.quartz.experimental.music.playlist.MusicPlaylistEvent
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import com.vitorpamplona.quartz.experimental.nip95.data.FileStorageEvent
import com.vitorpamplona.quartz.experimental.nip95.header.FileStorageHeaderEvent
import com.vitorpamplona.quartz.experimental.nipA3.PaymentTargetsEvent
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.experimental.nns.NNSEvent
import com.vitorpamplona.quartz.experimental.notifications.wake.WakeUpEvent
import com.vitorpamplona.quartz.experimental.profileGallery.ProfileGalleryEntryEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip03Timestamp.OtsEvent
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelHideMessageEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMuteUserEvent
import com.vitorpamplona.quartz.nip28PublicChat.list.ChannelListEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.selection.EmojiPackSelectionEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentCommentEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip37Drafts.privateOutbox.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.nip38UserStatus.StatusEvent
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.LnZapPaymentRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.PinListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.OldBookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent
import com.vitorpamplona.quartz.nip51Lists.geohashList.GeohashListEvent
import com.vitorpamplona.quartz.nip51Lists.hashtagList.HashtagListEvent
import com.vitorpamplona.quartz.nip51Lists.labeledBookmarkList.LabeledBookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.peopleList.PeopleListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BlockedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BroadcastRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.IndexerRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.ProxyRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.RelayFeedsListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.TrustedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relaySets.RelaySetEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.calendar.CalendarEvent
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingRoomEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.nestsServers.NestsServersEvent
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip58Badges.accepted.AcceptedBadgeSetEvent
import com.vitorpamplona.quartz.nip58Badges.award.BadgeAwardEvent
import com.vitorpamplona.quartz.nip58Badges.definition.BadgeDefinitionEvent
import com.vitorpamplona.quartz.nip58Badges.profile.ProfileBadgesEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.EphemeralGiftWrapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip62RequestToVanish.RequestToVanishEvent
import com.vitorpamplona.quartz.nip64Chess.challenge.accept.LiveChessGameAcceptEvent
import com.vitorpamplona.quartz.nip64Chess.challenge.offer.LiveChessGameChallengeEvent
import com.vitorpamplona.quartz.nip64Chess.draw.LiveChessDrawOfferEvent
import com.vitorpamplona.quartz.nip64Chess.end.LiveChessGameEndEvent
import com.vitorpamplona.quartz.nip64Chess.game.ChessGameEvent
import com.vitorpamplona.quartz.nip64Chess.jester.JesterEvent
import com.vitorpamplona.quartz.nip64Chess.move.LiveChessMoveEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.RelayDiscoveryEvent
import com.vitorpamplona.quartz.nip66RelayMonitor.monitor.RelayMonitorEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoHorizontalEvent
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent
import com.vitorpamplona.quartz.nip71Video.VideoVerticalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.follow.CommunityListEvent
import com.vitorpamplona.quartz.nip75ZapGoals.GoalEvent
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.ContactCardEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip89AppHandlers.recommendation.AppRecommendationEvent
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryRequest.NIP90ContentDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryResponse.NIP90ContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.status.NIP90StatusEvent
import com.vitorpamplona.quartz.nip90Dvms.userDiscoveryRequest.NIP90UserDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.userDiscoveryResponse.NIP90UserDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent
import com.vitorpamplona.quartz.nip96FileStorage.config.FileServersEvent
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceReplyEvent
import com.vitorpamplona.quartz.nipA4PublicMessages.PublicMessageEvent
import com.vitorpamplona.quartz.nipB0WebBookmarks.WebBookmarkEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomAuthorizationEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent
import com.vitorpamplona.quartz.nipF4Podcasts.authored.AuthoredPodcastsEvent
import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent
import com.vitorpamplona.quartz.nipF4Podcasts.favorites.FavoritePodcastsListEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent

/**
 * Human-readable label and defining NIP for a Nostr event kind.
 */
data class KindName(
    val name: String,
    val nip: String?,
)

/**
 * Canonical, **i18n-free** registry mapping event-kind numbers to an English
 * label and the NIP that defines them. This is the single source of truth for
 * "what is kind N" across the project:
 *
 *  - headless consumers (the `amy` CLI) print [nameFor] directly;
 *  - localized front ends (the Android app) overlay their own translated
 *    strings on top and fall back to [nameFor] for kinds they don't translate.
 *
 * Keep this list as the place new kinds are registered; translations are a
 * platform concern layered on top, never a fork of this data.
 */
object KindNames {
    val names: Map<Int, KindName> =
        mapOf(
            AcceptedBadgeSetEvent.KIND to KindName("Accepted Badge Set", "58"),
            AdvertisedRelayListEvent.KIND to KindName("Outbox Relays", "65"),
            AppDefinitionEvent.KIND to KindName("Apps", "89"),
            AppRecommendationEvent.KIND to KindName("App Recommendations", "89"),
            AppSpecificDataEvent.KIND to KindName("User Settings", "78"),
            AudioHeaderEvent.KIND to KindName("Audio Header", null),
            AudioTrackEvent.KIND to KindName("Audio Track", null),
            MusicTrackEvent.KIND to KindName("Music Track", null),
            MusicPlaylistEvent.KIND to KindName("Music Playlist", null),
            PodcastEpisodeEvent.KIND to KindName("Podcast Episode", "F4"),
            PodcastMetadataEvent.KIND to KindName("Podcast Show", "F4"),
            AuthoredPodcastsEvent.KIND to KindName("Authored Podcasts", "F4"),
            FavoritePodcastsListEvent.KIND to KindName("Favorite Podcasts", "F4"),
            AttestationEvent.KIND to KindName("Attestation", null),
            AttestationRequestEvent.KIND to KindName("Attestation Request", null),
            AttestorRecommendationEvent.KIND to KindName("Attestor Recommendation", null),
            AttestorProficiencyEvent.KIND to KindName("Attestor Proficiency", null),
            BadgeAwardEvent.KIND to KindName("Badge Awards", "58"),
            BadgeDefinitionEvent.KIND to KindName("Badge Definitions", "58"),
            BlockedRelayListEvent.KIND to KindName("Blocked Relays", "51"),
            BlossomServersEvent.KIND to KindName("Blossom Servers", "B7"),
            NestsServersEvent.KIND to KindName("Nests Servers", "53"),
            BlossomAuthorizationEvent.KIND to KindName("Blossom Auth", "B7"),
            BroadcastRelayListEvent.KIND to KindName("Broadcast Relays", "51"),
            BookmarkListEvent.KIND to KindName("Bookmark List", "51"),
            OldBookmarkListEvent.KIND to KindName("Old Bookmark List", "51"),
            CalendarDateSlotEvent.KIND to KindName("Day Appointment", "52"),
            CalendarEvent.KIND to KindName("Calendar", "52"),
            CalendarTimeSlotEvent.KIND to KindName("Appointment", "52"),
            CalendarRSVPEvent.KIND to KindName("Appt RSVP", "52"),
            ChessGameEvent.KIND to KindName("Chess Games", "64"),
            JesterEvent.KIND to KindName("Chess Auth", "64"),
            RelayFeedsListEvent.KIND to KindName("Favorite Relays", "51"),
            LiveChessGameChallengeEvent.KIND to KindName("Chess Challenges", "64"),
            LiveChessGameAcceptEvent.KIND to KindName("Chess Game Accept", "64"),
            LiveChessMoveEvent.KIND to KindName("Chess Move", "64"),
            LiveChessGameEndEvent.KIND to KindName("Chess Game End", "64"),
            LiveChessDrawOfferEvent.KIND to KindName("Chess Draw Offer", "64"),
            ChannelCreateEvent.KIND to KindName("Channel Definition", "28"),
            ChannelHideMessageEvent.KIND to KindName("Channel Hide Msg", "28"),
            ChannelListEvent.KIND to KindName("Channel List", "28"),
            ChannelMessageEvent.KIND to KindName("Channel Message", "28"),
            ChannelMetadataEvent.KIND to KindName("Channel Metadata", "28"),
            ChannelMuteUserEvent.KIND to KindName("Channel Mute User", "28"),
            ChatMessageEncryptedFileHeaderEvent.KIND to KindName("DM File", "17"),
            ChatMessageEvent.KIND to KindName("DM Message", "17"),
            ChatMessageRelayListEvent.KIND to KindName("DM Relays", "17"),
            ClassifiedsEvent.KIND to KindName("Classifieds", "99"),
            CommentEvent.KIND to KindName("Comments", "22"),
            CommunityDefinitionEvent.KIND to KindName("Community Def", "72"),
            CommunityListEvent.KIND to KindName("Community List", "72"),
            CommunityPostApprovalEvent.KIND to KindName("Community Post", "72"),
            ContactListEvent.KIND to KindName("Follow List", "02"),
            DeletionEvent.KIND to KindName("Deletions", "09"),
            DraftWrapEvent.KIND to KindName("Drafts", "37"),
            EmojiPackEvent.KIND to KindName("Emoji Packs", "30"),
            EmojiPackSelectionEvent.KIND to KindName("Emoji Pack List", "30"),
            EphemeralChatEvent.KIND to KindName("Ephemeral Chat", null),
            EphemeralChatListEvent.KIND to KindName("Ephemeral Chatrooms", null),
            FileHeaderEvent.KIND to KindName("File Headers", "94"),
            ProfileGalleryEntryEvent.KIND to KindName("Profile Gallery", null),
            FileServersEvent.KIND to KindName("File Servers", "96"),
            FileStorageEvent.KIND to KindName("Blob Data", null),
            FileStorageHeaderEvent.KIND to KindName("Blob Headers", null),
            FhirResourceEvent.KIND to KindName("Medical Data", null),
            FollowListEvent.KIND to KindName("Follow Packs", "51"),
            GenericRepostEvent.KIND to KindName("Reposts (16)", "18"),
            GeohashListEvent.KIND to KindName("Geohash Follows", "51"),
            GiftWrapEvent.KIND to KindName("GiftWraps", "59"),
            EphemeralGiftWrapEvent.KIND to KindName("GiftWraps", "59"),
            GitIssueEvent.KIND to KindName("Git Issue", "34"),
            GitPatchEvent.KIND to KindName("Git Patch", "34"),
            GitRepositoryEvent.KIND to KindName("Git Repo", "34"),
            GitReplyEvent.KIND to KindName("Git Reply", "34"),
            GoalEvent.KIND to KindName("Zap Goals", "75"),
            HashtagListEvent.KIND to KindName("Hashtag Follows", "51"),
            HighlightEvent.KIND to KindName("Highlights", "84"),
            HTTPAuthorizationEvent.KIND to KindName("Http Auth", "98"),
            IndexerRelayListEvent.KIND to KindName("Index Relay List", "51"),
            InteractiveStoryPrologueEvent.KIND to KindName("Adventure Prologue", null),
            InteractiveStorySceneEvent.KIND to KindName("Adventure Scene", null),
            InteractiveStoryReadingStateEvent.KIND to KindName("Adventure Reading", null),
            LabeledBookmarkListEvent.KIND to KindName("Named Bookmarks", "51"),
            LiveActivitiesChatMessageEvent.KIND to KindName("Live Chats", "53"),
            LiveActivitiesEvent.KIND to KindName("Live Streams", "53"),
            LnZapEvent.KIND to KindName("Zaps", "57"),
            LnZapPaymentRequestEvent.KIND to KindName("NWC Request", "47"),
            LnZapPaymentResponseEvent.KIND to KindName("NWC Response", "47"),
            LnZapPrivateEvent.KIND to KindName("Private Zaps", "57"),
            LnZapRequestEvent.KIND to KindName("Zap Req", "57"),
            LongTextNoteEvent.KIND to KindName("Blogs", "23"),
            MeetingRoomEvent.KIND to KindName("Meeting Room", "53"),
            MeetingRoomPresenceEvent.KIND to KindName("Room Presence", "53"),
            MeetingSpaceEvent.KIND to KindName("Meeting Space", "53"),
            MetadataEvent.KIND to KindName("Profile", "01"),
            MuteListEvent.KIND to KindName("Mute List", "51"),
            NNSEvent.KIND to KindName("NNS", null),
            NipTextEvent.KIND to KindName("NIP", null),
            NostrConnectEvent.KIND to KindName("Nostr Connect", "46"),
            NIP90StatusEvent.KIND to KindName("DVM Status", "90"),
            NIP90ContentDiscoveryRequestEvent.KIND to KindName("DVM Content Req", "90"),
            NIP90ContentDiscoveryResponseEvent.KIND to KindName("DVM Content Resp", "90"),
            NIP90UserDiscoveryRequestEvent.KIND to KindName("DVM User Req", "90"),
            NIP90UserDiscoveryResponseEvent.KIND to KindName("DVM User Resp", "90"),
            OtsEvent.KIND to KindName("OTS", "03"),
            PaymentTargetsEvent.KIND to KindName("PayTo", null),
            PeopleListEvent.KIND to KindName("People Lists", "51"),
            ProfileBadgesEvent.KIND to KindName("Profile Badges", "58"),
            PictureEvent.KIND to KindName("Pictures", "68"),
            WorkoutRecordEvent.KIND to KindName("Workouts", null),
            PinListEvent.KIND to KindName("Pins", "51"),
            ZapPollEvent.KIND to KindName("Zap Poll", null),
            PollEvent.KIND to KindName("Poll", "88"),
            PollResponseEvent.KIND to KindName("Poll Response", "88"),
            PrivateDmEvent.KIND to KindName("NIP-04 DMs", "04"),
            PrivateOutboxRelayListEvent.KIND to KindName("Private Relays", "37"),
            ProxyRelayListEvent.KIND to KindName("Proxy Relays", "51"),
            PublicMessageEvent.KIND to KindName("Public Message", "A4"),
            ReactionEvent.KIND to KindName("Reactions", "25"),
            ContactCardEvent.KIND to KindName("Contact Card", "85"),
            RelayAuthEvent.KIND to KindName("Relay Auth", "42"),
            RelayDiscoveryEvent.KIND to KindName("Relay Discovery", "66"),
            RelayMonitorEvent.KIND to KindName("Relay Monitor Announcement", "66"),
            RelaySetEvent.KIND to KindName("Relay Set", "51"),
            ReportEvent.KIND to KindName("Reports", "56"),
            RepostEvent.KIND to KindName("Reposts", "18"),
            RequestToVanishEvent.KIND to KindName("User Delete", "62"),
            SealedRumorEvent.KIND to KindName("Seals", "59"),
            SearchRelayListEvent.KIND to KindName("Search Relays", "50"),
            StatusEvent.KIND to KindName("User Status", "38"),
            TextNoteEvent.KIND to KindName("Notes", "10"),
            TextNoteModificationEvent.KIND to KindName("Edits", null),
            TorrentEvent.KIND to KindName("Torrents", "35"),
            TorrentCommentEvent.KIND to KindName("Torrent Comments", "35"),
            TrustedRelayListEvent.KIND to KindName("Trusted Relays", "51"),
            TrustProviderListEvent.KIND to KindName("Trusted Providers", "85"),
            VideoHorizontalEvent.KIND to KindName("Video (Repl)", "71"),
            VideoVerticalEvent.KIND to KindName("Shorts (Repl)", "71"),
            VideoNormalEvent.KIND to KindName("Video", "71"),
            VideoShortEvent.KIND to KindName("Shorts", "71"),
            VoiceEvent.KIND to KindName("Voice Msg", "A0"),
            VoiceReplyEvent.KIND to KindName("Voice Reply", "A0"),
            WakeUpEvent.KIND to KindName("WakeUp", null),
            WebBookmarkEvent.KIND to KindName("Web Bookmark", "B0"),
            WikiNoteEvent.KIND to KindName("Wiki", "54"),
        )

    fun infoFor(kind: Int): KindName? = names[kind]

    fun nameFor(kind: Int): String? = names[kind]?.name

    fun nipFor(kind: Int): String? = names[kind]?.nip

    /** Case-insensitive substring search by label; returns matching (kind, info) sorted by kind. */
    fun search(query: String): List<Pair<Int, KindName>> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return names.entries
            .filter {
                it.value.name
                    .lowercase()
                    .contains(q)
            }.map { it.key to it.value }
            .sortedBy { it.first }
    }
}
