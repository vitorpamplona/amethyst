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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays

import android.content.Context
import androidx.annotation.StringRes
import com.vitorpamplona.amethyst.R
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
import com.vitorpamplona.quartz.kinds.KindNames
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
import com.vitorpamplona.quartz.nipXXBolt12Zaps.zap.Bolt12ZapEvent

/** Returns the `@StringRes` id for the translated kind name, or -1 if unknown. */
@Suppress("DEPRECATION")
@StringRes
fun kindDisplayName(kind: Int): Int =
    when (kind) {
        AcceptedBadgeSetEvent.KIND -> R.string.kind_accepted_badge_set
        AdvertisedRelayListEvent.KIND -> R.string.kind_outbox_relays
        AppDefinitionEvent.KIND -> R.string.kind_apps
        AppRecommendationEvent.KIND -> R.string.kind_app_recommendations
        AppSpecificDataEvent.KIND -> R.string.kind_user_settings
        AudioHeaderEvent.KIND -> R.string.kind_audio_header
        AudioTrackEvent.KIND -> R.string.kind_audio_track
        MusicTrackEvent.KIND -> R.string.kind_music_track
        MusicPlaylistEvent.KIND -> R.string.kind_music_playlist
        PodcastEpisodeEvent.KIND -> R.string.kind_podcast_episode
        PodcastMetadataEvent.KIND -> R.string.kind_podcast_metadata
        AuthoredPodcastsEvent.KIND -> R.string.kind_authored_podcasts
        FavoritePodcastsListEvent.KIND -> R.string.kind_favorite_podcasts
        AttestationEvent.KIND -> R.string.attestation
        AttestationRequestEvent.KIND -> R.string.attestation_request
        AttestorRecommendationEvent.KIND -> R.string.attestor_recommendation
        AttestorProficiencyEvent.KIND -> R.string.attestor_proficiency
        BadgeAwardEvent.KIND -> R.string.kind_badge_awards
        BadgeDefinitionEvent.KIND -> R.string.kind_badge_definitions
        BlockedRelayListEvent.KIND -> R.string.kind_blocked_relays
        BlossomServersEvent.KIND -> R.string.kind_blossom_servers
        NestsServersEvent.KIND -> R.string.kind_nests_servers
        BlossomAuthorizationEvent.KIND -> R.string.kind_blossom_auth
        BroadcastRelayListEvent.KIND -> R.string.kind_broadcast_relays
        BookmarkListEvent.KIND -> R.string.kind_bookmark_list
        OldBookmarkListEvent.KIND -> R.string.kind_old_bookmark_list
        CalendarDateSlotEvent.KIND -> R.string.kind_day_appointment
        CalendarEvent.KIND -> R.string.kind_calendar
        CalendarTimeSlotEvent.KIND -> R.string.kind_appointment
        CalendarRSVPEvent.KIND -> R.string.kind_appt_rsvp
        ChessGameEvent.KIND -> R.string.kind_chess_games
        JesterEvent.KIND -> R.string.kind_chess_auth
        RelayFeedsListEvent.KIND -> R.string.kind_favorite_relays
        LiveChessGameChallengeEvent.KIND -> R.string.kind_chess_challenges
        LiveChessGameAcceptEvent.KIND -> R.string.kind_chess_game_accept
        LiveChessMoveEvent.KIND -> R.string.kind_chess_move
        LiveChessGameEndEvent.KIND -> R.string.kind_chess_game_end
        LiveChessDrawOfferEvent.KIND -> R.string.kind_chess_draw_offer
        ChannelCreateEvent.KIND -> R.string.kind_channel_definition
        ChannelHideMessageEvent.KIND -> R.string.kind_channel_hide_msg
        ChannelListEvent.KIND -> R.string.kind_channel_list
        ChannelMessageEvent.KIND -> R.string.kind_channel_message
        ChannelMetadataEvent.KIND -> R.string.kind_channel_metadata
        ChannelMuteUserEvent.KIND -> R.string.kind_channel_mute_user
        ChatMessageEncryptedFileHeaderEvent.KIND -> R.string.kind_dm_file
        ChatMessageEvent.KIND -> R.string.kind_dm_message
        ChatMessageRelayListEvent.KIND -> R.string.kind_dm_relays
        ClassifiedsEvent.KIND -> R.string.kind_classifieds
        CommentEvent.KIND -> R.string.kind_comments
        CommunityDefinitionEvent.KIND -> R.string.kind_community_def
        CommunityListEvent.KIND -> R.string.kind_community_list
        CommunityPostApprovalEvent.KIND -> R.string.kind_community_post
        ContactListEvent.KIND -> R.string.kind_follow_list
        DeletionEvent.KIND -> R.string.kind_deletions
        DraftWrapEvent.KIND -> R.string.kind_drafts
        EmojiPackEvent.KIND -> R.string.kind_emoji_packs
        EmojiPackSelectionEvent.KIND -> R.string.kind_emoji_pack_list
        EphemeralChatEvent.KIND -> R.string.kind_ephemeral_chat
        EphemeralChatListEvent.KIND -> R.string.kind_ephemeral_chatrooms
        FileHeaderEvent.KIND -> R.string.kind_file_headers
        ProfileGalleryEntryEvent.KIND -> R.string.kind_profile_gallery
        FileServersEvent.KIND -> R.string.kind_file_servers
        FileStorageEvent.KIND -> R.string.kind_blob_data
        FileStorageHeaderEvent.KIND -> R.string.kind_blob_headers
        FhirResourceEvent.KIND -> R.string.kind_medical_data
        FollowListEvent.KIND -> R.string.kind_follow_packs
        GenericRepostEvent.KIND -> R.string.kind_reposts_16
        GeohashListEvent.KIND -> R.string.kind_geohash_follows
        GiftWrapEvent.KIND -> R.string.kind_gift_wraps
        EphemeralGiftWrapEvent.KIND -> R.string.kind_gift_wraps
        GitIssueEvent.KIND -> R.string.kind_git_issue
        GitPatchEvent.KIND -> R.string.kind_git_patch
        GitRepositoryEvent.KIND -> R.string.kind_git_repo
        GitReplyEvent.KIND -> R.string.kind_git_reply
        GoalEvent.KIND -> R.string.kind_zap_goals
        HashtagListEvent.KIND -> R.string.kind_hashtag_follows
        HighlightEvent.KIND -> R.string.kind_highlights
        HTTPAuthorizationEvent.KIND -> R.string.kind_http_auth
        IndexerRelayListEvent.KIND -> R.string.kind_index_relay_list
        InteractiveStoryPrologueEvent.KIND -> R.string.kind_adventure_prologue
        InteractiveStorySceneEvent.KIND -> R.string.kind_adventure_scene
        InteractiveStoryReadingStateEvent.KIND -> R.string.kind_adventure_reading
        LabeledBookmarkListEvent.KIND -> R.string.kind_named_bookmarks
        LiveActivitiesChatMessageEvent.KIND -> R.string.kind_live_chats
        LiveActivitiesEvent.KIND -> R.string.kind_live_streams
        LnZapEvent.KIND -> R.string.kind_zaps
        Bolt12ZapEvent.KIND -> R.string.kind_zaps
        LnZapPaymentRequestEvent.KIND -> R.string.kind_nwc_request
        LnZapPaymentResponseEvent.KIND -> R.string.kind_nwc_response
        LnZapPrivateEvent.KIND -> R.string.kind_private_zaps
        LnZapRequestEvent.KIND -> R.string.kind_zap_req
        LongTextNoteEvent.KIND -> R.string.kind_blogs
        MeetingRoomEvent.KIND -> R.string.kind_meeting_room
        MeetingRoomPresenceEvent.KIND -> R.string.kind_room_presence
        MeetingSpaceEvent.KIND -> R.string.kind_meeting_space
        MetadataEvent.KIND -> R.string.kind_profile
        MuteListEvent.KIND -> R.string.kind_mute_list
        NNSEvent.KIND -> R.string.kind_nns
        NipTextEvent.KIND -> R.string.kind_nip
        NostrConnectEvent.KIND -> R.string.kind_nostr_connect
        NIP90StatusEvent.KIND -> R.string.kind_dvm_status
        NIP90ContentDiscoveryRequestEvent.KIND -> R.string.kind_dvm_content_req
        NIP90ContentDiscoveryResponseEvent.KIND -> R.string.kind_dvm_content_resp
        NIP90UserDiscoveryRequestEvent.KIND -> R.string.kind_dvm_user_req
        NIP90UserDiscoveryResponseEvent.KIND -> R.string.kind_dvm_user_resp
        OtsEvent.KIND -> R.string.kind_ots
        PaymentTargetsEvent.KIND -> R.string.kind_pay_to
        PeopleListEvent.KIND -> R.string.kind_people_lists
        ProfileBadgesEvent.KIND -> R.string.kind_profile_badges
        PictureEvent.KIND -> R.string.kind_pictures
        WorkoutRecordEvent.KIND -> R.string.kind_workouts
        PinListEvent.KIND -> R.string.kind_pins
        ZapPollEvent.KIND -> R.string.kind_zap_poll
        PollEvent.KIND -> R.string.kind_poll
        PollResponseEvent.KIND -> R.string.kind_poll_response
        PrivateDmEvent.KIND -> R.string.kind_nip04_dms
        PrivateOutboxRelayListEvent.KIND -> R.string.kind_private_relays
        ProxyRelayListEvent.KIND -> R.string.kind_proxy_relays
        PublicMessageEvent.KIND -> R.string.kind_public_message
        ReactionEvent.KIND -> R.string.kind_reactions
        ContactCardEvent.KIND -> R.string.kind_contact_card
        RelayAuthEvent.KIND -> R.string.kind_relay_auth
        RelayDiscoveryEvent.KIND -> R.string.kind_relay_discovery
        RelayMonitorEvent.KIND -> R.string.kind_relay_monitor
        RelaySetEvent.KIND -> R.string.kind_relay_set
        ReportEvent.KIND -> R.string.kind_reports
        RepostEvent.KIND -> R.string.kind_reposts
        RequestToVanishEvent.KIND -> R.string.kind_user_delete
        SealedRumorEvent.KIND -> R.string.kind_seals
        SearchRelayListEvent.KIND -> R.string.kind_search_relays
        StatusEvent.KIND -> R.string.kind_user_status
        TextNoteEvent.KIND -> R.string.kind_notes
        TextNoteModificationEvent.KIND -> R.string.kind_edits
        TorrentEvent.KIND -> R.string.kind_torrents
        TorrentCommentEvent.KIND -> R.string.kind_torrent_comments
        TrustedRelayListEvent.KIND -> R.string.kind_trusted_relays
        TrustProviderListEvent.KIND -> R.string.kind_trusted_providers
        VideoHorizontalEvent.KIND -> R.string.kind_video_repl
        VideoVerticalEvent.KIND -> R.string.kind_shorts_repl
        VideoNormalEvent.KIND -> R.string.kind_video
        VideoShortEvent.KIND -> R.string.kind_shorts
        VoiceEvent.KIND -> R.string.kind_voice_msg
        VoiceReplyEvent.KIND -> R.string.kind_voice_reply
        WakeUpEvent.KIND -> R.string.kind_wake
        WebBookmarkEvent.KIND -> R.string.kind_web_bookmark
        WikiNoteEvent.KIND -> R.string.kind_wiki
        else -> -1
    }

/**
 * Returns the translated display name for [kind] using Android string resources when available,
 * falling back to the English name from [KindNames], then to "k<number>".
 */
fun kindNameFor(
    context: Context,
    kind: Int,
): String {
    val resId = kindDisplayName(kind)
    return if (resId != -1) context.getString(resId) else (KindNames.nameFor(kind) ?: "k$kind")
}
