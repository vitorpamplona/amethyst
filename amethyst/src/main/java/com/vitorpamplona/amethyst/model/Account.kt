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
package com.vitorpamplona.amethyst.model

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.commons.actions.ConcordActions
import com.vitorpamplona.amethyst.commons.actions.ConcordModeration
import com.vitorpamplona.amethyst.commons.actions.ConcordSubscriptionPlanner
import com.vitorpamplona.amethyst.commons.audio.VisualizerStyle
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.InMemoryNip46ClientStore
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46ClientStore
import com.vitorpamplona.amethyst.commons.connectedApps.signers.InMemoryNostrSignerPermissionStore
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionLedger
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionStore
import com.vitorpamplona.amethyst.commons.marmot.MarmotManager
import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel
import com.vitorpamplona.amethyst.commons.model.concord.ConcordChannelListState
import com.vitorpamplona.amethyst.commons.model.concord.ConcordSessionManager
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatListDecryptionCache
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatListState
import com.vitorpamplona.amethyst.commons.model.nip18Reposts.RepostAction
import com.vitorpamplona.amethyst.commons.model.nip25Reactions.ReactionAction
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatListDecryptionCache
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatListState
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupListDecryptionCache
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupListState
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.UserStatusAction
import com.vitorpamplona.amethyst.commons.model.nip51Lists.favoriteAlgoFeedsLists.FavoriteAlgoFeedsListDecryptionCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.hashtagLists.HashtagListDecryptionCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.muteList.MuteListDecryptionCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.peopleList.PeopleListDecryptionCache
import com.vitorpamplona.amethyst.commons.model.nip56Reports.ReportAction
import com.vitorpamplona.amethyst.commons.model.nip72Communities.CommunityListDecryptionCache
import com.vitorpamplona.amethyst.commons.model.nip85TrustedAssertions.ContactCardDecryptionCache
import com.vitorpamplona.amethyst.commons.model.nip85TrustedAssertions.ContactCardsState
import com.vitorpamplona.amethyst.commons.model.nip85TrustedAssertions.TrustProviderListDecryptionCache
import com.vitorpamplona.amethyst.commons.model.privateChats.hasEncryptedContent
import com.vitorpamplona.amethyst.commons.onchain.OnchainZapSendError
import com.vitorpamplona.amethyst.commons.onchain.OnchainZapSendResult
import com.vitorpamplona.amethyst.commons.onchain.OnchainZapSendStage
import com.vitorpamplona.amethyst.commons.onchain.OnchainZapSender
import com.vitorpamplona.amethyst.commons.onchain.OnchainZapShare
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthCustomToggles
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPermissionStore
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.service.pow.PersistedPoWJob
import com.vitorpamplona.amethyst.commons.service.pow.PoWCategory
import com.vitorpamplona.amethyst.commons.service.pow.PoWPolicy
import com.vitorpamplona.amethyst.commons.service.pow.PoWPublishQueue
import com.vitorpamplona.amethyst.commons.service.pow.PoWReplay
import com.vitorpamplona.amethyst.commons.viewmodels.ReplyMode
import com.vitorpamplona.amethyst.logTime
import com.vitorpamplona.amethyst.model.algoFeeds.FavoriteAlgoFeedsOrchestrator
import com.vitorpamplona.amethyst.model.edits.PrivateStorageRelayListDecryptionCache
import com.vitorpamplona.amethyst.model.edits.PrivateStorageRelayListState
import com.vitorpamplona.amethyst.model.localRelays.ForwardKind0ToLocalRelayState
import com.vitorpamplona.amethyst.model.localRelays.LocalRelayListState
import com.vitorpamplona.amethyst.model.marmot.KeyPackageRelayListState
import com.vitorpamplona.amethyst.model.nip01UserMetadata.AccountHomeRelayState
import com.vitorpamplona.amethyst.model.nip01UserMetadata.AccountMineRelayState
import com.vitorpamplona.amethyst.model.nip01UserMetadata.AccountOutboxRelayState
import com.vitorpamplona.amethyst.model.nip01UserMetadata.NotificationInboxRelayState
import com.vitorpamplona.amethyst.model.nip01UserMetadata.UserMetadataState
import com.vitorpamplona.amethyst.model.nip02FollowLists.DeclaredFollowsPerOutboxRelay
import com.vitorpamplona.amethyst.model.nip02FollowLists.DeclaredFollowsPerUsingRelay
import com.vitorpamplona.amethyst.model.nip02FollowLists.FollowListOutboxOrProxyRelays
import com.vitorpamplona.amethyst.model.nip02FollowLists.FollowListReusedOutboxOrProxyRelays
import com.vitorpamplona.amethyst.model.nip02FollowLists.FollowsPerOutboxRelay
import com.vitorpamplona.amethyst.model.nip02FollowLists.Kind3FollowListState
import com.vitorpamplona.amethyst.model.nip03Timestamp.OtsState
import com.vitorpamplona.amethyst.model.nip17Dms.DmInboxRelayState
import com.vitorpamplona.amethyst.model.nip17Dms.DmRelayListState
import com.vitorpamplona.amethyst.model.nip30CustomEmojis.OwnedEmojiPacksState
import com.vitorpamplona.amethyst.model.nip46Signer.Nip46SignerState
import com.vitorpamplona.amethyst.model.nip47WalletConnect.NwcSignerState
import com.vitorpamplona.amethyst.model.nip51Lists.BookmarkListState
import com.vitorpamplona.amethyst.model.nip51Lists.GitRepositoryListState
import com.vitorpamplona.amethyst.model.nip51Lists.HiddenUsersState
import com.vitorpamplona.amethyst.model.nip51Lists.OldBookmarkListState
import com.vitorpamplona.amethyst.model.nip51Lists.PinListState
import com.vitorpamplona.amethyst.model.nip51Lists.blockPeopleList.BlockPeopleListState
import com.vitorpamplona.amethyst.model.nip51Lists.blockedRelays.BlockedRelayListDecryptionCache
import com.vitorpamplona.amethyst.model.nip51Lists.blockedRelays.BlockedRelayListState
import com.vitorpamplona.amethyst.model.nip51Lists.broadcastRelays.BroadcastRelayListDecryptionCache
import com.vitorpamplona.amethyst.model.nip51Lists.broadcastRelays.BroadcastRelayListState
import com.vitorpamplona.amethyst.model.nip51Lists.favoriteAlgoFeedsLists.FavoriteAlgoFeedsListState
import com.vitorpamplona.amethyst.model.nip51Lists.geohashLists.GeohashListDecryptionCache
import com.vitorpamplona.amethyst.model.nip51Lists.geohashLists.GeohashListState
import com.vitorpamplona.amethyst.model.nip51Lists.hashtagLists.HashtagListState
import com.vitorpamplona.amethyst.model.nip51Lists.indexerRelays.IndexerRelayListDecryptionCache
import com.vitorpamplona.amethyst.model.nip51Lists.indexerRelays.IndexerRelayListState
import com.vitorpamplona.amethyst.model.nip51Lists.interestSets.InterestSetsState
import com.vitorpamplona.amethyst.model.nip51Lists.labeledBookmarkLists.LabeledBookmarkListsState
import com.vitorpamplona.amethyst.model.nip51Lists.muteList.MuteListState
import com.vitorpamplona.amethyst.model.nip51Lists.peopleList.FollowListsState
import com.vitorpamplona.amethyst.model.nip51Lists.peopleList.PeopleListsState
import com.vitorpamplona.amethyst.model.nip51Lists.proxyRelays.ProxyRelayListDecryptionCache
import com.vitorpamplona.amethyst.model.nip51Lists.proxyRelays.ProxyRelayListState
import com.vitorpamplona.amethyst.model.nip51Lists.relayFeeds.RelayFeedListState
import com.vitorpamplona.amethyst.model.nip51Lists.relayFeeds.RelayFeedsListDecryptionCache
import com.vitorpamplona.amethyst.model.nip51Lists.searchRelays.SearchRelayListDecryptionCache
import com.vitorpamplona.amethyst.model.nip51Lists.searchRelays.SearchRelayListState
import com.vitorpamplona.amethyst.model.nip51Lists.trustedRelays.TrustedRelayListDecryptionCache
import com.vitorpamplona.amethyst.model.nip51Lists.trustedRelays.TrustedRelayListState
import com.vitorpamplona.amethyst.model.nip62Vanish.VanishRequestsState
import com.vitorpamplona.amethyst.model.nip65RelayList.Nip65RelayListState
import com.vitorpamplona.amethyst.model.nip72Communities.CommunityListState
import com.vitorpamplona.amethyst.model.nip78AppSpecific.AppSpecificState
import com.vitorpamplona.amethyst.model.nip89AppHandlers.AppRecommendationsState
import com.vitorpamplona.amethyst.model.nipA3PaymentTargets.NipA3PaymentTargetsState
import com.vitorpamplona.amethyst.model.nipB7Blossom.BlossomServerListState
import com.vitorpamplona.amethyst.model.serverList.MergedFollowListsState
import com.vitorpamplona.amethyst.model.serverList.MergedFollowPlusMineRelayListsState
import com.vitorpamplona.amethyst.model.serverList.MergedFollowPlusMineWithIndexRelayListsState
import com.vitorpamplona.amethyst.model.serverList.MergedFollowPlusMineWithSearchRelayListsState
import com.vitorpamplona.amethyst.model.serverList.TrustedRelayListsState
import com.vitorpamplona.amethyst.model.topNavFeeds.FeedDecryptionCaches
import com.vitorpamplona.amethyst.model.topNavFeeds.FeedTopNavFilterState
import com.vitorpamplona.amethyst.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.OutboxLoaderState
import com.vitorpamplona.amethyst.model.trustedAssertions.TrustProviderListState
import com.vitorpamplona.amethyst.service.location.LocationState
import com.vitorpamplona.amethyst.service.relayClient.authCommand.model.InMemoryRelayAuthPermissionStore
import com.vitorpamplona.amethyst.service.relayClient.authCommand.model.RelayAuthPermissionCache
import com.vitorpamplona.amethyst.service.relayClient.authCommand.model.RelayAuthPermissionLedger
import com.vitorpamplona.amethyst.service.relayClient.chatDelivery.ChatDeliveryTracker
import com.vitorpamplona.amethyst.service.relayClient.notifyCommand.model.NotifyRequestsCache
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.nwc.NWCPaymentFilterAssembler
import com.vitorpamplona.amethyst.service.uploads.FileHeader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.EventProcessor
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.concordChannelLastReadRoute
import com.vitorpamplona.quartz.buzz.dm.DmAddMemberEvent
import com.vitorpamplona.quartz.buzz.dm.DmHideEvent
import com.vitorpamplona.quartz.buzz.dm.DmOpenEvent
import com.vitorpamplona.quartz.buzz.presence.TypingIndicatorEvent
import com.vitorpamplona.quartz.buzz.relayAdmin.RelayAdminAddMemberEvent
import com.vitorpamplona.quartz.buzz.relayAdmin.RelayAdminRemoveMemberEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageV2Event
import com.vitorpamplona.quartz.buzz.threading.buzzThread
import com.vitorpamplona.quartz.buzz.threading.buzzThreadReply
import com.vitorpamplona.quartz.buzz.threading.buzzThreadRoot
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEvent
import com.vitorpamplona.quartz.concord.cord02Community.HeldRoot
import com.vitorpamplona.quartz.concord.cord02Community.ImagePointer
import com.vitorpamplona.quartz.concord.cord03Channels.ChannelChat
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelId
import com.vitorpamplona.quartz.concord.cord04Roles.ChannelEntity
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.concord.cord04Roles.MetadataEntity
import com.vitorpamplona.quartz.concord.cord04Roles.RoleEntity
import com.vitorpamplona.quartz.concord.cord05Invites.CommunityInvite
import com.vitorpamplona.quartz.concord.cord05Invites.InviteBundleStatus
import com.vitorpamplona.quartz.concord.cord05Invites.InviteRelayDictionary
import com.vitorpamplona.quartz.concord.crypto.GroupKey
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.experimental.bounties.BountyAddValueEvent
import com.vitorpamplona.quartz.experimental.edits.TextNoteModificationEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryBaseEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryReadingStateEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStorySceneEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.image
import com.vitorpamplona.quartz.experimental.interactiveStories.summary
import com.vitorpamplona.quartz.experimental.interactiveStories.tags.StoryOptionTag
import com.vitorpamplona.quartz.experimental.nip95.data.FileStorageEvent
import com.vitorpamplona.quartz.experimental.nip95.header.FileStorageHeaderEvent
import com.vitorpamplona.quartz.experimental.nip95.header.blurhash
import com.vitorpamplona.quartz.experimental.nip95.header.dimension
import com.vitorpamplona.quartz.experimental.nip95.header.fileSize
import com.vitorpamplona.quartz.experimental.nip95.header.hash
import com.vitorpamplona.quartz.experimental.nip95.header.mimeType
import com.vitorpamplona.quartz.experimental.nipA3.PaymentTarget
import com.vitorpamplona.quartz.experimental.profileGallery.ProfileGalleryEntryEvent
import com.vitorpamplona.quartz.experimental.profileGallery.blurhash
import com.vitorpamplona.quartz.experimental.profileGallery.dimension
import com.vitorpamplona.quartz.experimental.profileGallery.fromEvent
import com.vitorpamplona.quartz.experimental.profileGallery.hash
import com.vitorpamplona.quartz.experimental.profileGallery.image
import com.vitorpamplona.quartz.experimental.profileGallery.mimeType
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupStateStore
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PublishResult
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllWithHooks
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchFirst
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndCollectResults
import com.vitorpamplona.quartz.nip01Core.relay.client.paging.RelayLoadingCursors
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrlOrNull
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hasMoreHashtagsThan
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUserIds
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip03Timestamp.OtsResolver
import com.vitorpamplona.quartz.nip04Dm.PrivateDMCache
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip10Notes.threadRootIdOrSelf
import com.vitorpamplona.quartz.nip13Pow.miner.PoWMiner
import com.vitorpamplona.quartz.nip13Pow.signer.PoWNostrSigner
import com.vitorpamplona.quartz.nip17Dm.NIP17Factory
import com.vitorpamplona.quartz.nip17Dm.base.BaseDMGroupEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip17Dm.base.NIP17Group
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip18Reposts.quotes.quotes
import com.vitorpamplona.quartz.nip19Bech32.entities.Entity
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NRelay
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.hTag
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.CreateGroupEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.CreateInviteEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.EditMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.PutUserEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.RemoveUserEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.UpdatePinListEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.previous
import com.vitorpamplona.quartz.nip29RelayGroups.request.JoinRequestEvent
import com.vitorpamplona.quartz.nip29RelayGroups.request.LeaveRequestEvent
import com.vitorpamplona.quartz.nip32Labeling.LabelEvent
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning
import com.vitorpamplona.quartz.nip37Drafts.DraftEventCache
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Request
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Response
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.OldBookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark
import com.vitorpamplona.quartz.nip51Lists.labeledBookmarkList.LabeledBookmarkListEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingRoomEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip56Reports.ReportType
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapCache
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplits
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiser
import com.vitorpamplona.quartz.nip58Badges.accepted.AcceptedBadgeSetEvent
import com.vitorpamplona.quartz.nip58Badges.accepted.tags.AcceptedBadge
import com.vitorpamplona.quartz.nip58Badges.award.BadgeAwardEvent
import com.vitorpamplona.quartz.nip58Badges.definition.BadgeDefinitionEvent
import com.vitorpamplona.quartz.nip58Badges.definition.tags.ThumbTag
import com.vitorpamplona.quartz.nip58Badges.profile.ProfileBadgesEvent
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.EphemeralGiftWrapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapTemplateConversion
import com.vitorpamplona.quartz.nip62RequestToVanish.RequestToVanishEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayInfo
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip68Picture.PictureMeta
import com.vitorpamplona.quartz.nip68Picture.pictureIMeta
import com.vitorpamplona.quartz.nip71Video.VideoMeta
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.tags.ModeratorTag
import com.vitorpamplona.quartz.nip72ModCommunities.definition.tags.RelayTag
import com.vitorpamplona.quartz.nip72ModCommunities.rules.CommunityRulesEvent
import com.vitorpamplona.quartz.nip72ModCommunities.rules.tags.KindRuleTag
import com.vitorpamplona.quartz.nip72ModCommunities.rules.tags.PubkeyRuleTag
import com.vitorpamplona.quartz.nip72ModCommunities.rules.tags.WotTag
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nip7DThreads.ThreadEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import com.vitorpamplona.quartz.nip89AppHandlers.clientTag.NostrSignerWithClientTag
import com.vitorpamplona.quartz.nip89AppHandlers.clientTag.withoutClientTag
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryRequest.NIP90ContentDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent
import com.vitorpamplona.quartz.nip94FileMetadata.blurhash
import com.vitorpamplona.quartz.nip94FileMetadata.dimension
import com.vitorpamplona.quartz.nip94FileMetadata.fileSize
import com.vitorpamplona.quartz.nip94FileMetadata.hash
import com.vitorpamplona.quartz.nip94FileMetadata.magnet
import com.vitorpamplona.quartz.nip94FileMetadata.mimeType
import com.vitorpamplona.quartz.nip94FileMetadata.originalHash
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.nip94FileMetadata.thumbhash
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.BaseVoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceReplyEvent
import com.vitorpamplona.quartz.nipB0WebBookmarks.WebBookmarkEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.utils.DualCase
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.ciphers.AESGCM
import com.vitorpamplona.quartz.utils.containsAny
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import com.vitorpamplona.quartz.experimental.nip95.header.thumbhash as nip95thumbhash
import com.vitorpamplona.quartz.experimental.profileGallery.thumbhash as galleryThumbhash

private const val ONCHAIN_BACKEND_NOT_CONFIGURED = "Bitcoin chain backend is not configured"

/** Name of the default Concord community Admin role minted by "Make admin". */
private const val CONCORD_ADMIN_ROLE = "Admin"

/**
 * How often a joined Concord community's stored invite link is re-resolved to check whether
 * we were left out of a Refounding (see `recoverStrandedConcordCommunities`). Stranding is
 * rare and silent, so this trades detection latency for not turning the revision tick into a
 * relay-fetch loop.
 */
private const val RECOVERY_CHECK_INTERVAL_MS = 15 * 60 * 1000L

@OptIn(DelicateCoroutinesApi::class)
@Stable
class Account(
    val settings: AccountSettings = AccountSettings(KeyPair()),
    override val signer: NostrSigner,
    val geolocationFlow: () -> StateFlow<LocationState.LocationResult>,
    val nwcFilterAssembler: () -> NWCPaymentFilterAssembler,
    val cashuWalletFilterAssembler: () -> com.vitorpamplona.amethyst.commons.relayClient.assemblers.CashuWalletFilterAssembler,
    val cashuMintDirectoryFilterAssembler: () -> com.vitorpamplona.amethyst.commons.relayClient.assemblers.CashuMintDirectoryFilterAssembler,
    val okHttpClientForMoney: (String) -> okhttp3.OkHttpClient,
    val otsResolverBuilder: () -> OtsResolver,
    val cache: LocalCache,
    val client: INostrClient,
    val scope: CoroutineScope,
    val mlsGroupStateStore: MlsGroupStateStore? = null,
    val marmotMessageStore: com.vitorpamplona.quartz.marmot.mls.group.MarmotMessageStore? = null,
    val marmotKeyPackageStore: com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageBundleStore? = null,
    val powQueue: () -> PoWPublishQueue? = { null },
    relayAuthPermissionStore: RelayAuthPermissionStore = InMemoryRelayAuthPermissionStore(),
    signerPermissionStore: NostrSignerPermissionStore = InMemoryNostrSignerPermissionStore(),
    nip46ClientStore: Nip46ClientStore = InMemoryNip46ClientStore(),
) : IAccount {
    private var userProfileCache: User? = null

    override fun userProfile(): User = userProfileCache ?: cache.getOrCreateUser(signer.pubKey).also { userProfileCache = it }

    // IAccount interface properties
    override val pubKey: String get() = signer.pubKey
    override val showSensitiveContent: Boolean? get() = hiddenUsers.flow.value.showSensitiveContent
    override val hiddenWordsCase: List<DualCase> get() = hiddenUsers.flow.value.hiddenWordsCase
    override val hiddenUsersHashCodes: Set<Int> get() = hiddenUsers.flow.value.hiddenUsersHashCodes
    override val spammersHashCodes: Set<Int> get() = hiddenUsers.flow.value.spammersHashCodes

    val userMetadata = UserMetadataState(signer, cache, scope, settings)

    // Per-account NIP-42 ALLOW/DENY overrides, warm-cached in memory so a relay AUTH challenge is
    // answered without a disk read. Backed by a per-account file (see AccountCacheState).
    val relayAuthPermissions = RelayAuthPermissionCache(relayAuthPermissionStore, scope)

    // Per-account NIP-42 policy evaluator (blocked → per-relay override → global policy → prompt),
    // reading THIS account's own toggles, relay lists and follow graph. Cached here so every AUTH
    // path (foreground screen + background notification consumer) shares one instance, and so an
    // AUTH challenge is decided per account instead of folding every logged-in account together.
    val relayAuthLedger =
        RelayAuthPermissionLedger(
            store = relayAuthPermissions,
            globalPolicy = { settings.defaultRelayAuthPolicy.value },
            customToggles = {
                RelayAuthCustomToggles(
                    myRelaysAndVenues = settings.relayAuthTrustMyRelaysAndVenues.value,
                    readFollows = settings.relayAuthTrustReadFollows.value,
                    messageFollows = settings.relayAuthTrustMessageFollows.value,
                    messageStrangers = settings.relayAuthTrustMessageStrangers.value,
                )
            },
            isInMyRelayList = { relayUrl -> relayUrl.normalizeRelayUrlOrNull()?.let { it in trustedRelays.flow.value } ?: false },
            isBlocked = { relayUrl -> relayUrl.normalizeRelayUrlOrNull()?.let { it in blockedRelayList.flow.value } ?: false },
            isFollowed = { pubkey -> pubkey in allFollows.flow.value.authors },
            isTrustedVenue = { venueId ->
                venueId in publicChatList.flowSet.value ||
                    venueId in communityList.flowSet.value ||
                    Address.parse(venueId)?.pubKeyHex?.let { it in allFollows.flow.value.authors } == true
            },
        )

    // Per-account relay NOTIFY (payment-prompt) cache. NotifyCoordinator attributes each incoming
    // NOTIFY to the account whose AUTH the relay rejected and drops it here, so a prompt for one
    // account never surfaces under another (the old cache was a process-wide singleton).
    val relayNotifications = NotifyRequestsCache()

    override val nip47SignerState = NwcSignerState(signer, nwcFilterAssembler, cache, scope, settings)

    val nip65RelayList = Nip65RelayListState(signer, cache, scope, settings)
    val localRelayList = LocalRelayListState(signer, cache, scope, settings)

    /** Connected-Apps signer permission ledger, shared by napplets and the NIP-46 bunker. */
    val signerPermissionLedger = NostrSignerPermissionLedger(signerPermissionStore)

    /**
     * Runs this account as a NIP-46 remote signer for other apps when
     * [AccountSettings.nip46SignerEnabled] is on, listening on the inbox relays
     * and dispatching to [signer] (see [Nip46SignerState]).
     */
    val nip46Signer =
        Nip46SignerState(
            // Acting as someone else's bunker: the templates arriving here were composed by the
            // connected client, so they are signed exactly as received — our client tag would both
            // misattribute the event and change the id the client expects back.
            signer = signer.withoutClientTag(),
            client = client,
            ledger = signerPermissionLedger,
            clientStore = nip46ClientStore,
            inboxRelays = nip65RelayList.inboxFlow,
            scope = scope,
            settings = settings,
        )

    val forwardKind0ToLocalRelay = ForwardKind0ToLocalRelayState(client, localRelayList, settings)

    val dmRelayList = DmRelayListState(signer, cache, scope, settings)

    val keyPackageRelayList = KeyPackageRelayListState(signer, cache, scope, settings)

    val privateStorageDecryptionCache = PrivateStorageRelayListDecryptionCache(signer)
    val privateStorageRelayList = PrivateStorageRelayListState(signer, cache, privateStorageDecryptionCache, scope, settings)

    val searchRelayListDecryptionCache = SearchRelayListDecryptionCache(signer)
    val searchRelayList = SearchRelayListState(signer, cache, searchRelayListDecryptionCache, scope, settings)

    val trustedRelayListDecryptionCache = TrustedRelayListDecryptionCache(signer)
    val trustedRelayList = TrustedRelayListState(signer, cache, trustedRelayListDecryptionCache, scope, settings)

    val proxyRelayListDecryptionCache = ProxyRelayListDecryptionCache(signer)
    val proxyRelayList = ProxyRelayListState(signer, cache, proxyRelayListDecryptionCache, scope, settings)

    val broadcastRelayListDecryptionCache = BroadcastRelayListDecryptionCache(signer)
    val broadcastRelayList = BroadcastRelayListState(signer, cache, broadcastRelayListDecryptionCache, scope, settings)

    val indexerRelayListDecryptionCache = IndexerRelayListDecryptionCache(signer)
    val indexerRelayList = IndexerRelayListState(signer, cache, indexerRelayListDecryptionCache, scope, settings)

    val relayFeedsListDecryptionCache = RelayFeedsListDecryptionCache(signer)
    val relayFeedsList = RelayFeedListState(signer, cache, relayFeedsListDecryptionCache, scope, settings)

    val blockedRelayListDecryptionCache = BlockedRelayListDecryptionCache(signer)
    val blockedRelayList = BlockedRelayListState(signer, cache, blockedRelayListDecryptionCache, scope, settings)

    val kind3FollowList = Kind3FollowListState(signer, cache, scope, settings)

    val ephemeralChatListDecryptionCache = EphemeralChatListDecryptionCache(signer)
    val ephemeralChatList = EphemeralChatListState(signer, cache, ephemeralChatListDecryptionCache, scope, settings)

    val relayGroupListDecryptionCache = RelayGroupListDecryptionCache(signer)
    val relayGroupList = RelayGroupListState(signer, cache, relayGroupListDecryptionCache, scope, settings)

    val concordChannelList = ConcordChannelListState(signer, cache, scope, settings)

    /**
     * The live read-path for joined Concord Channels: one folding session per
     * community, fed by inbound kind-1059 plane wraps. Kept in step with
     * [concordChannelList] and consulted by the giftwrap decrypt path so a Concord
     * plane wrap routes here instead of being dropped as an undecryptable DM.
     */
    val concordSessions = ConcordSessionManager(concordChannelList.liveCommunities, signer.pubKey, scope, ::consumeConcordRumorGated)

    /**
     * Sink for decrypted Concord rumors: drops a message whose author is banned in
     * the community's current fold before it ever becomes a Note, then delegates to
     * the cache. Bans that arrive *after* a message are handled by removing the
     * author's existing notes on re-fold (see `refreshConcordChannelIndex`); this
     * gate stops *new* posts from a banned author from appearing at all.
     */
    private fun consumeConcordRumorGated(
        communityId: String,
        channelIdHex: String,
        rumor: Event,
        seenOnRelays: Set<NormalizedRelayUrl>,
    ) {
        val authority =
            concordSessions
                .sessionFor(communityId)
                ?.state
                ?.value
                ?.authority
        if (authority?.isBanned(rumor.pubKey) == true) return
        registerConcordEncryptedImages(rumor)
        cache.consumeConcordRumor(communityId, channelIdHex, rumor, seenOnRelays)
    }

    /**
     * Register any encrypted image attachments on a Concord message ([ChannelChat.encryptedImagesOf])
     * so the shared media pipeline can display them: the ciphertext blob's AES-256-GCM key/nonce go
     * into [com.vitorpamplona.amethyst.AppModules.keyCache], and the OkHttp EncryptedBlobInterceptor
     * decrypts the blob transparently on fetch (keyed by URL) — the same path NIP-17 encrypted media
     * uses. Runs for both inbound wraps and our own local echo, so a sent image renders immediately.
     */
    private fun registerConcordEncryptedImages(rumor: Event) {
        val images = ChannelChat.encryptedImagesOf(rumor)
        if (images.isEmpty()) return
        val keyCache = Amethyst.instance.keyCache
        images.forEach { img ->
            if (img.algo == AESGCM.NAME) {
                keyCache.add(img.url, AESGCM(img.key, img.nonce), img.mimeType)
            }
        }
    }

    /**
     * Copies each folded community's metadata (name/icon, channel flags, this account's
     * membership) onto its [ConcordChannel] objects in the cache, and drops messages from
     * authors banned since they loaded. Runs account-wide on every
     * [com.vitorpamplona.amethyst.commons.model.concord.ConcordSessionManager] revision —
     * NOT gated behind the Concord hub screen — so every surface (the Messages-tab
     * community chip, the chat screen title) reflects the current fold, and bans apply,
     * even when the hub was never opened.
     */
    fun refreshConcordChannelIndex() {
        val myPubKey = signer.pubKey
        val relaysByCommunity =
            concordChannelList.liveCommunities.value.associate { entry ->
                entry.id to entry.relays.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }
            }
        for (session in concordSessions.sessions()) {
            val state = session.state.value ?: continue
            val communityId = session.entry.id
            val relays = relaysByCommunity[communityId] ?: emptySet()
            for (channelIdHex in state.channels.keys) {
                val channel = cache.getOrCreateConcordChannel(ConcordChannelId(communityId, channelIdHex))
                // Invalidate the channel's metadata flow only on a real change so the Messages-row
                // name + community chip recompose when the fold first resolves them (they observe
                // metadata.stateFlow via observeChannel), without churning every row every tick.
                if (channel.updateFrom(state, relays, myPubKey)) channel.updateChannelInfo()
                channel.notes
                    .filter { _, note -> note.event?.pubKey?.let { state.authority.isBanned(it) } == true }
                    .forEach { channel.removeNote(it) }
            }
        }
    }

    val publicChatListDecryptionCache = PublicChatListDecryptionCache(signer)
    val publicChatList = PublicChatListState(signer, cache, publicChatListDecryptionCache, scope, settings)

    val communityListDecryptionCache = CommunityListDecryptionCache(signer)
    val communityList = CommunityListState(signer, cache, communityListDecryptionCache, scope, settings)

    val hashtagListDecryptionCache = HashtagListDecryptionCache(signer)
    val hashtagList = HashtagListState(signer, cache, hashtagListDecryptionCache, scope, settings)

    val favoriteAlgoFeedsListDecryptionCache = FavoriteAlgoFeedsListDecryptionCache(signer)
    val favoriteAlgoFeedsList = FavoriteAlgoFeedsListState(signer, cache, favoriteAlgoFeedsListDecryptionCache, scope, settings)
    val favoriteAlgoFeedsOrchestrator = FavoriteAlgoFeedsOrchestrator(this, scope)

    val geohashListDecryptionCache = GeohashListDecryptionCache(signer)
    val geohashList = GeohashListState(signer, cache, geohashListDecryptionCache, scope, settings)

    // Anonymous, per-geohash throwaway identities for Bitchat-interoperable location chats.
    val geohashIdentity = GeohashChatIdentityState(signer)

    val muteListDecryptionCache = MuteListDecryptionCache(signer)
    val muteList = MuteListState(signer, cache, muteListDecryptionCache, scope, settings)

    val trustProviderListDecryptionCache = TrustProviderListDecryptionCache(signer)
    val trustProviderList = TrustProviderListState(signer, cache, trustProviderListDecryptionCache, scope, settings)

    val peopleListDecryptionCache = PeopleListDecryptionCache(signer)
    val blockPeopleList = BlockPeopleListState(signer, cache, peopleListDecryptionCache, scope)
    val peopleLists = PeopleListsState(signer, cache, peopleListDecryptionCache, scope)
    val followLists = FollowListsState(signer, cache, scope)

    val hiddenUsers = HiddenUsersState(muteList.flow, blockPeopleList.flow, scope, settings)

    val labeledBookmarkLists = LabeledBookmarkListsState(signer, cache, scope)
    val interestSets = InterestSetsState(signer, cache, scope)
    val appRecommendations = AppRecommendationsState(signer, cache, scope)
    val oldBookmarkState = OldBookmarkListState(signer, cache, scope)
    val bookmarkState = BookmarkListState(signer, cache, scope)
    val gitRepositoryListState = GitRepositoryListState(signer, cache, scope)
    val pinState = PinListState(signer, cache, scope)
    val emoji = EmojiPackState(signer, cache, scope)
    val ownedEmojiPacks = OwnedEmojiPacksState(signer, cache, scope)

    // needs `emoji` above: nickname edits resolve :shortcodes: against the account's packs
    val contactCardDecryptionCache = ContactCardDecryptionCache(signer)
    val contactCards = ContactCardsState(signer, cache, contactCardDecryptionCache, emoji)

    val vanish = VanishRequestsState(signer, cache, client, scope)

    val appSpecific = AppSpecificState(signer, cache, scope, settings)

    val blossomServers = BlossomServerListState(signer, cache, scope, settings)

    val nestsServers =
        com.vitorpamplona.amethyst.model.nip53NestsServers
            .NestsServerListState(signer, cache, scope)

    // Relay settings
    val homeRelays = AccountHomeRelayState(nip65RelayList, privateStorageRelayList, localRelayList, scope)
    val outboxRelays = AccountOutboxRelayState(nip65RelayList, privateStorageRelayList, localRelayList, broadcastRelayList, scope)
    val mineRelays = AccountMineRelayState(nip65RelayList, privateStorageRelayList, localRelayList, proxyRelayList, scope)
    val dmRelays = DmInboxRelayState(dmRelayList, nip65RelayList, privateStorageRelayList, localRelayList, scope)
    val notificationRelays = NotificationInboxRelayState(nip65RelayList, localRelayList, scope)

    // Account-level notification history paging cursors (one scope per account): how far back each
    // notification relay has been paged by until+limit. Held here so they share the account's lifetime;
    // the history loader ([AccountNotificationsHistoryEoseManager]) binds its orchestrator to these.
    val notificationHistory = RelayLoadingCursors()

    val cashuWalletState =
        com.vitorpamplona.amethyst.model.nip60Cashu.CashuWalletState(
            pubKey = signer.pubKey,
            signer = signer,
            cache = cache,
            scope = scope,
            assembler = cashuWalletFilterAssembler(),
            outboxRelaysFlow = outboxRelays.flow,
            inboxRelaysFlow = notificationRelays.flow,
            dmRelaysFlow = dmRelays.flow,
            settings = settings,
            okHttpClient = okHttpClientForMoney,
        )

    /**
     * NIP-87 cashu mint directory — populated on-demand while the mint
     * picker is on screen. ViewModels call open()/close() ref-counted, the
     * relay subscription only runs while at least one opener is active.
     */
    val cashuMintDirectoryState =
        com.vitorpamplona.amethyst.model.nip60Cashu.CashuMintDirectoryState(
            cache = cache,
            scope = scope,
            assembler = cashuMintDirectoryFilterAssembler(),
            followsFlow =
                kotlinx.coroutines.flow.MutableStateFlow(kind3FollowList.flow.value.authors).also { authorSet ->
                    scope.launch {
                        kind3FollowList.flow.collect { authorSet.value = it.authors }
                    }
                },
        )

    val trustedRelays = TrustedRelayListsState(nip65RelayList, privateStorageRelayList, localRelayList, dmRelayList, searchRelayList, indexerRelayList, proxyRelayList, trustedRelayList, broadcastRelayList, scope)

    // Follows Relays
    val followOutboxesOrProxy = FollowListOutboxOrProxyRelays(kind3FollowList, blockedRelayList, proxyRelayList, cache, scope)

    // only follow relays that are declared in more than one user.
    val followSharedOutboxesOrProxy = FollowListReusedOutboxOrProxyRelays(kind3FollowList, blockedRelayList, proxyRelayList, cache, scope)

    val followPlusAllMineWithIndex = MergedFollowPlusMineWithIndexRelayListsState(followOutboxesOrProxy, nip65RelayList, privateStorageRelayList, localRelayList, indexerRelayList, scope)
    val followPlusAllMineWithSearch = MergedFollowPlusMineWithSearchRelayListsState(followOutboxesOrProxy, nip65RelayList, privateStorageRelayList, localRelayList, searchRelayList, scope)
    val defaultGlobalRelays = MergedFollowPlusMineRelayListsState(followOutboxesOrProxy, nip65RelayList, privateStorageRelayList, localRelayList, scope)

    // keeps a cache of the declared outbox relays for each author
    val declaredFollowsPerOutboxRelay = DeclaredFollowsPerOutboxRelay(kind3FollowList, cache, scope).flow
    val declaredFollowsPerUsingRelay = DeclaredFollowsPerUsingRelay(kind3FollowList, cache, scope).flow

    // keeps a cache of the outbox relays for each author
    val followsPerRelay = FollowsPerOutboxRelay(kind3FollowList, blockedRelayList, proxyRelayList, cache, scope).flow

    // Merges all follow lists to create a single All Follows feed.
    val allFollows = MergedFollowListsState(kind3FollowList, peopleLists, followLists, hashtagList, geohashList, communityList, scope)

    val privateDMDecryptionCache = PrivateDMCache(signer)
    override val privateZapsDecryptionCache = PrivateZapCache(signer)
    val draftsDecryptionCache = DraftEventCache(signer)

    override val chatroomList = cache.getOrCreateChatroomList(signer.pubKey)
    override val marmotGroupList =
        com.vitorpamplona.amethyst.commons.model.marmotGroups
            .MarmotGroupList(signer.pubKey)

    val newNotesPreProcessor = EventProcessor(this, cache)

    // Per-message publish acceptance (relay OKs), feeding the delivery ticks on
    // own chat bubbles.
    val chatDeliveryTracker = ChatDeliveryTracker(client)

    val otsState = OtsState(signer, cache, otsResolverBuilder, scope, settings)

    val marmotManager: MarmotManager? = mlsGroupStateStore?.let { MarmotManager(signer, it, marmotMessageStore, marmotKeyPackageStore) }

    val paymentTargetsState = NipA3PaymentTargetsState(signer, cache, scope, settings)

    val feedDecryptionCaches =
        FeedDecryptionCaches(
            peopleListCache = peopleListDecryptionCache,
            muteListCache = muteListDecryptionCache,
            communityListCache = communityListDecryptionCache,
            hashtagCache = hashtagListDecryptionCache,
            geohashCache = geohashListDecryptionCache,
        )

    fun topNavFilterFlow(listName: MutableStateFlow<TopFilter>) =
        FeedTopNavFilterState(
            feedFilterListName = listName,
            kind3Follows = kind3FollowList.flow,
            allFollows = allFollows.flow,
            locationFlow = geolocationFlow,
            followsRelays = defaultGlobalRelays.flow,
            blockedRelays = blockedRelayList.flow,
            proxyRelays = proxyRelayList.flow,
            mineRelays = mineRelays.flow,
            relayFeeds = relayFeedsList.flow,
            caches = feedDecryptionCaches,
            signer = signer,
            scope = scope,
            favoriteAlgoFeedsOrchestrator = favoriteAlgoFeedsOrchestrator,
            favoriteAlgoFeedAddresses = favoriteAlgoFeedsList.flow,
            interestSetHashtags = interestSets.hashtagsByIdentifier,
        ).flow

    // App-ready Feeds
    val liveHomeFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultHomeFollowList)
    val liveHomeFollowListsPerRelay = OutboxLoaderState(liveHomeFollowLists, cache, scope).flow

    val liveStoriesFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultStoriesFollowList)
    val liveStoriesFollowListsPerRelay = OutboxLoaderState(liveStoriesFollowLists, cache, scope).flow

    val liveDiscoveryFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultDiscoveryFollowList)
    val liveDiscoveryFollowListsPerRelay = OutboxLoaderState(liveDiscoveryFollowLists, cache, scope).flow

    val liveNotificationFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultNotificationFollowList)
    val liveNotificationFollowListsPerRelay = OutboxLoaderState(liveNotificationFollowLists, cache, scope).flow

    val livePollsFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultPollsFollowList)
    val livePollsFollowListsPerRelay = OutboxLoaderState(livePollsFollowLists, cache, scope).flow

    val livePicturesFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultPicturesFollowList)
    val livePicturesFollowListsPerRelay = OutboxLoaderState(livePicturesFollowLists, cache, scope).flow

    val liveRelayGroupsDiscoveryFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultRelayGroupsDiscoveryFollowList)
    val liveRelayGroupsDiscoveryFollowListsPerRelay = OutboxLoaderState(liveRelayGroupsDiscoveryFollowLists, cache, scope).flow

    val liveNappletsFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultNappletsFollowList)
    val liveNappletsFollowListsPerRelay = OutboxLoaderState(liveNappletsFollowLists, cache, scope).flow

    val liveNsitesFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultNsitesFollowList)
    val liveNsitesFollowListsPerRelay = OutboxLoaderState(liveNsitesFollowLists, cache, scope).flow

    val liveWorkoutsFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultWorkoutsFollowList)
    val liveWorkoutsFollowListsPerRelay = OutboxLoaderState(liveWorkoutsFollowLists, cache, scope).flow

    val liveGitRepositoriesFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultGitRepositoriesFollowList)
    val liveGitRepositoriesFollowListsPerRelay = OutboxLoaderState(liveGitRepositoriesFollowLists, cache, scope).flow

    val liveCalendarsFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultCalendarsFollowList)
    val liveCalendarsFollowListsPerRelay = OutboxLoaderState(liveCalendarsFollowLists, cache, scope).flow

    val liveProductsFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultProductsFollowList)
    val liveProductsFollowListsPerRelay = OutboxLoaderState(liveProductsFollowLists, cache, scope).flow

    val liveShortsFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultShortsFollowList)
    val liveShortsFollowListsPerRelay = OutboxLoaderState(liveShortsFollowLists, cache, scope).flow

    val livePublicChatsFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultPublicChatsFollowList)
    val livePublicChatsFollowListsPerRelay = OutboxLoaderState(livePublicChatsFollowLists, cache, scope).flow

    val liveLiveStreamsFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultLiveStreamsFollowList)
    val liveLiveStreamsFollowListsPerRelay = OutboxLoaderState(liveLiveStreamsFollowLists, cache, scope).flow

    val liveNestsFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultNestsFollowList)
    val liveNestsFollowListsPerRelay = OutboxLoaderState(liveNestsFollowLists, cache, scope).flow

    val liveLongsFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultLongsFollowList)
    val liveLongsFollowListsPerRelay = OutboxLoaderState(liveLongsFollowLists, cache, scope).flow

    val liveArticlesFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultArticlesFollowList)
    val liveArticlesFollowListsPerRelay = OutboxLoaderState(liveArticlesFollowLists, cache, scope).flow

    val liveMusicTracksFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultMusicTracksFollowList)
    val liveMusicTracksFollowListsPerRelay = OutboxLoaderState(liveMusicTracksFollowLists, cache, scope).flow

    val liveMusicPlaylistsFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultMusicPlaylistsFollowList)
    val liveMusicPlaylistsFollowListsPerRelay = OutboxLoaderState(liveMusicPlaylistsFollowLists, cache, scope).flow

    val livePodcastEpisodesFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultPodcastEpisodesFollowList)
    val livePodcastEpisodesFollowListsPerRelay = OutboxLoaderState(livePodcastEpisodesFollowLists, cache, scope).flow

    val livePodcastsFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultPodcastsFollowList)
    val livePodcastsFollowListsPerRelay = OutboxLoaderState(livePodcastsFollowLists, cache, scope).flow

    val liveSoftwareAppsFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultSoftwareAppsFollowList)
    val liveSoftwareAppsFollowListsPerRelay = OutboxLoaderState(liveSoftwareAppsFollowLists, cache, scope).flow

    val liveBadgesFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultBadgesFollowList)
    val liveBadgesFollowListsPerRelay = OutboxLoaderState(liveBadgesFollowLists, cache, scope).flow

    val liveBrowseEmojiSetsFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultBrowseEmojiSetsFollowList)
    val liveBrowseEmojiSetsFollowListsPerRelay = OutboxLoaderState(liveBrowseEmojiSetsFollowLists, cache, scope).flow

    val liveCommunitiesFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultCommunitiesFollowList)
    val liveCommunitiesFollowListsPerRelay = OutboxLoaderState(liveCommunitiesFollowLists, cache, scope).flow

    val liveFollowPacksFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultFollowPacksFollowList)
    val liveFollowPacksFollowListsPerRelay = OutboxLoaderState(liveFollowPacksFollowLists, cache, scope).flow

    // App recommendations are read straight from LocalCache (no relay feed of its
    // own), so only the in-memory author/tag matcher is needed here, not a
    // per-relay outbox loader.
    val liveAppRecommendationsFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultAppRecommendationsFollowList)

    override fun isWriteable(): Boolean = settings.isWriteable()

    suspend fun updateWarnReports(warnReports: Boolean): Boolean {
        if (settings.updateWarnReports(warnReports)) {
            sendNewAppSpecificData()
            return true
        }
        return false
    }

    suspend fun updateReportWarningThreshold(threshold: Int): Boolean {
        if (settings.updateReportWarningThreshold(threshold.coerceAtLeast(1))) {
            sendNewAppSpecificData()
            return true
        }
        return false
    }

    suspend fun updateSendKind0EventsToLocalRelay(send: Boolean): Boolean {
        if (settings.changeSendKind0EventsToLocalRelay(send)) {
            sendNewAppSpecificData()
            return true
        }
        return false
    }

    suspend fun updateAddClientTag(add: Boolean): Boolean {
        if (settings.updateAddClientTag(add)) {
            sendNewAppSpecificData()
            return true
        }
        return false
    }

    suspend fun updatePowDifficulty(difficulty: Int) {
        if (settings.updatePowDifficulty(difficulty)) {
            sendNewAppSpecificData()
        }
    }

    suspend fun updatePowCategory(
        category: PoWCategory,
        enabled: Boolean,
    ) {
        if (settings.updatePowCategory(category, enabled)) {
            sendNewAppSpecificData()
        }
    }

    suspend fun updateFilterSpam(filterSpam: Boolean): Boolean {
        if (settings.updateFilterSpam(filterSpam)) {
            if (!settings.syncedSettings.security.filterSpamFromStrangers.value) {
                hiddenUsers.resetTransientUsers()
            }

            sendNewAppSpecificData()
            return true
        }
        return false
    }

    suspend fun updateShowSensitiveContent(show: Boolean?) {
        if (settings.updateShowSensitiveContent(show)) {
            sendNewAppSpecificData()
        }
    }

    suspend fun updateMaxHashtagLimit(limit: Int) {
        if (settings.updateMaxHashtagLimit(limit)) {
            sendNewAppSpecificData()
        }
    }

    suspend fun changeReactionTypes(reactionSet: List<String>) {
        if (settings.changeReactionTypes(reactionSet)) {
            sendNewAppSpecificData()
        }
    }

    suspend fun changeReactionRowItems(items: List<ReactionRowItem>) {
        if (settings.changeReactionRowItems(items)) {
            sendNewAppSpecificData()
        }
    }

    suspend fun changeVideoPlayerButtonItems(items: List<VideoPlayerButtonItem>) {
        if (settings.changeVideoPlayerButtonItems(items)) {
            sendNewAppSpecificData()
        }
    }

    suspend fun changeAudioVisualizer(style: VisualizerStyle) {
        if (settings.changeAudioVisualizer(style)) {
            sendNewAppSpecificData()
        }
    }

    suspend fun toggleChatroomPin(room: ChatroomKey) {
        settings.toggleChatroomPin(room)
        sendNewAppSpecificData()
    }

    suspend fun updateZapAmounts(
        amountSet: List<Long>,
        selectedZapType: LnZapEvent.ZapType,
        nip47Update: Nip47WalletConnect.Nip47URINorm?,
    ) {
        var changed = false

        if (settings.changeZapAmounts(amountSet)) changed = true
        if (settings.changeDefaultZapType(selectedZapType)) changed = true
        if (settings.changeZapPaymentRequest(nip47Update)) changed = true

        if (changed) {
            sendNewAppSpecificData()
        }
    }

    suspend fun toggleDontTranslateFrom(languageCode: String) {
        settings.toggleDontTranslateFrom(languageCode)
        sendNewAppSpecificData()
    }

    suspend fun addDontTranslateFrom(languageCode: String) {
        settings.addDontTranslateFrom(languageCode)
        sendNewAppSpecificData()
    }

    suspend fun removeDontTranslateFrom(languageCode: String) {
        settings.removeDontTranslateFrom(languageCode)
        sendNewAppSpecificData()
    }

    suspend fun updateTranslateTo(languageCode: String) {
        if (settings.updateTranslateTo(languageCode)) {
            sendNewAppSpecificData()
        }
    }

    suspend fun prefer(
        source: String,
        target: String,
        preference: String,
    ) {
        settings.prefer(source, target, preference)
        sendNewAppSpecificData()
    }

    private suspend fun sendNewAppSpecificData() = sendMyPublicAndPrivateOutbox(appSpecific.saveNewAppSpecificData())

    // ---
    // NIP-13 proof-of-work publishing
    // ---

    /**
     * Difficulty to mine [kind] at per this account's NIP-13 settings, or null
     * when the kind publishes immediately: master difficulty off, category
     * disabled, or one of [PoWPolicy]'s hard-excluded kinds (auth, zap
     * requests, NWC/bunker RPC, drafts, lists…).
     */
    fun powDifficultyFor(kind: Int): Int? =
        PoWPolicy.shouldMine(
            kind = kind,
            difficulty = settings.syncedSettings.proofOfWork.difficulty.value,
            enabledCategories = settings.syncedSettings.proofOfWork.enabledCategories.value,
        )

    /**
     * [powDifficultyFor] with a per-post override from the composer chip:
     * null defers to the account settings, 0 disables mining for this post,
     * a positive value forces that difficulty (hard-excluded kinds still win).
     */
    fun powDifficultyFor(
        kind: Int,
        overrideDifficulty: Int?,
    ): Int? =
        when {
            overrideDifficulty == null -> powDifficultyFor(kind)
            overrideDifficulty <= 0 -> null
            PoWPolicy.neverMine(kind) -> null
            else -> overrideDifficulty
        }

    /**
     * Parallel workers a single nonce search should use — the mining queue's
     * per-job budget (half the device's cores). 1 when no queue is wired.
     * Callers that run [PoWMiner] inside a queued job must pass this so the
     * job stays inside the queue's CPU budget.
     */
    fun powMinerWorkers(): Int = powQueue()?.minerThreads ?: 1

    /**
     * Enqueues [work] into the fire-and-forget mining queue. Returns false when
     * no queue is wired (headless/test accounts): callers must then run their
     * direct, un-mined send path instead.
     */
    fun mineInBackground(
        kind: Int,
        difficulty: Int,
        work: suspend (isActive: () -> Boolean) -> Unit,
    ): Boolean {
        val queue = powQueue() ?: return false
        queue.enqueueWork(kind, difficulty, owner = signer.pubKey, work = work)
        return true
    }

    /**
     * Enqueues [template] to be mined at [difficulty] and then handed to
     * [onMined], which should run the exact sign+send path the caller would
     * have used without PoW. Returns false when no queue is wired.
     *
     * When [replay] is given the job is checkpointed to disk so it survives
     * process death: on the next login the restorer re-mines the persisted
     * template and finishes it with the (headless) replay path instead of
     * [onMined]. Pass null for content that must not touch disk.
     *
     * The template is normalized to the final tag shape the signer will submit
     * (client tag included) before mining — a tag appended after mining would
     * invalidate the nonce.
     */
    fun <T : Event> mineTemplateInBackground(
        template: EventTemplate<T>,
        difficulty: Int,
        replay: PoWReplay? = null,
        onMined: suspend (EventTemplate<T>) -> Unit,
    ): Boolean {
        val queue = powQueue() ?: return false
        val finalTemplate = withFinalSignerTags(template)
        val record = replay?.toRecord(RandomInstance.randomChars(16), signer.pubKey, finalTemplate, difficulty)
        queue.enqueue(
            template = finalTemplate,
            pubKey = signer.pubKey,
            difficulty = difficulty,
            persistAs = record,
            // NIP-13 recommends refreshing created_at while mining; scheduled
            // posts keep their intentional future timestamp.
            refreshCreatedAtOnStart = replay !is PoWReplay.Schedule,
            onMined = onMined,
        )
        return true
    }

    /**
     * The one-liner for template send paths: when [template]'s kind should be
     * mined (per settings and the optional composer [overrideDifficulty]),
     * enqueue it and run [send] with the mined template once the nonce is
     * found; otherwise run [send] with [template] right now.
     */
    suspend fun <T : Event> sendMined(
        template: EventTemplate<T>,
        replay: PoWReplay?,
        overrideDifficulty: Int? = null,
        send: suspend (EventTemplate<T>) -> Unit,
    ) {
        val difficulty = powDifficultyFor(template.kind, overrideDifficulty)
        if (difficulty == null || !mineTemplateInBackground(template, difficulty, replay, send)) {
            send(template)
        }
    }

    /**
     * Queues wrap mining for pre-signed [seals] (see NIP17Factory.createSeals):
     * each seal gets its ephemeral-key envelope mined at [difficulty] on the
     * worker pool, then the wraps broadcast. Checkpointed under
     * [PersistedPoWJob.REPLAY_WRAPS] (the seals are already-signed ciphertext,
     * safe to persist) unless an [existingRecord] from the restorer is passed.
     * Returns false when no queue is wired.
     */
    fun mineWrapsInBackground(
        seals: List<NIP17Factory.AddressedSeal>,
        expirationDelta: Long?,
        difficulty: Int,
        existingRecord: PersistedPoWJob? = null,
        // The inner rumor's id (the note the chat feed displays), so the mined
        // wraps still register with the delivery-ticks tracker at publish time.
        // Null for restart-restored jobs, whose rumor id wasn't persisted.
        displayedNoteId: HexKey? = null,
    ): Boolean {
        val queue = powQueue() ?: return false
        if (seals.isEmpty()) return true

        val record =
            existingRecord
                ?: PersistedPoWJob(
                    id = RandomInstance.randomChars(16),
                    accountPubkey = signer.pubKey,
                    kind = GiftWrapEvent.KIND,
                    difficulty = difficulty,
                    templateJson = "",
                    replayType = PersistedPoWJob.REPLAY_WRAPS,
                    extraEventsJson = seals.map { it.seal.toJson() },
                    recipientPubkeys = seals.map { it.recipient },
                    wrapExpirationDelta = expirationDelta,
                    createdAtSec = TimeUtils.now(),
                )

        queue.enqueueStaged(
            kind = GiftWrapEvent.KIND,
            difficulty = difficulty,
            persistAs = record,
            mine = { isActive ->
                // the wrap's ephemeral key is generated inside the wrap build;
                // the conversion hook hands its pubkey back so the nonce can
                // commit to it. Single-threaded on purpose: the conversion is
                // a non-suspend hook deep inside the synchronous NIP-59 wrap
                // build, so it can't race PoWMiner.mine workers.
                val mineWrap: GiftWrapTemplateConversion = { template, ephemeralPubKey ->
                    PoWMiner.run(template, ephemeralPubKey, difficulty, isActive)
                }
                seals.map { NIP17Factory().wrapSeal(it, expirationDelta, templateConversion = mineWrap) }
            },
            publish = { wraps -> broadcastPrivately(wraps, displayedNoteId) },
        )
        return true
    }

    private fun <T : Event> withFinalSignerTags(template: EventTemplate<T>): EventTemplate<T> {
        val currentSigner = signer
        if (currentSigner !is NostrSignerWithClientTag) return template

        val finalTags = currentSigner.prepareTags(template.tags)
        if (finalTags === template.tags) return template

        return EventTemplate(template.createdAt, template.kind, finalTags, template.content)
    }

    /**
     * A signer that mines [kindsToMine] at [difficulty] right before signing.
     * When the account signer stamps a client tag, the miner is layered inside
     * it so mining runs over the final tag set.
     */
    private fun miningSigner(
        difficulty: Int,
        kindsToMine: Set<Int>,
        isActive: () -> Boolean,
    ): NostrSigner {
        val currentSigner = signer
        val workers = powMinerWorkers()
        return if (currentSigner is NostrSignerWithClientTag) {
            NostrSignerWithClientTag(
                inner = PoWNostrSigner(currentSigner.inner, difficulty, kindsToMine, isActive, workers),
                clientTag = currentSigner.clientTag,
                disabled = currentSigner.disabled,
            )
        } else {
            PoWNostrSigner(currentSigner, difficulty, kindsToMine, isActive, workers)
        }
    }

    suspend fun reactTo(
        note: Note,
        reaction: String,
    ) {
        // Reactions to NIP-17 groups and unsealed rumors are gift-wrapped: the
        // inner kind-7 only ever travels as ciphertext, so mining it is pure
        // waste — those targets skip the queue and sign with the plain signer.
        val isPrivateTarget = note.event is NIP17Group || note.isPrivateRumor()

        val powDifficulty = if (isPrivateTarget) null else powDifficultyFor(ReactionEvent.KIND)
        if (powDifficulty != null) {
            val queue = powQueue()
            if (queue != null) {
                // toggle semantics while mining: a second tap on the same
                // reaction un-likes by cancelling the pending job instead of
                // publishing a duplicate (the mined event doesn't exist yet,
                // so hasReacted can't dedupe).
                val dedupeKey = "reaction:${note.idHex}:$reaction"
                if (queue.cancelByKey(dedupeKey)) return

                queue.enqueueWork(ReactionEvent.KIND, powDifficulty, dedupeKey, owner = signer.pubKey) { isActive ->
                    ReactionAction.reactTo(
                        note = note,
                        reaction = reaction,
                        by = userProfile(),
                        signer = miningSigner(powDifficulty, setOf(ReactionEvent.KIND), isActive),
                        onPublic = ::sendAutomatic,
                        onPrivate = ::broadcastPrivately,
                    )
                }
                return
            }
        }

        ReactionAction.reactTo(
            note = note,
            reaction = reaction,
            by = userProfile(),
            signer = signer,
            onPublic = ::sendAutomatic,
            onPrivate = ::broadcastPrivately,
        )
    }

    /**
     * Creates a reaction event without sending it.
     * Returns the event and target relays for tracked broadcasting.
     * Returns null if note has already been reacted to or note has no event.
     */
    suspend fun createReactionEvent(
        note: Note,
        reaction: String,
    ): Pair<Event, Set<NormalizedRelayUrl>>? {
        if (!signer.isWriteable()) return null
        if (note.hasReacted(userProfile(), reaction)) return null

        val eventHint = note.toEventHint<Event>() ?: return null

        // For NIP-17 private groups, we don't support tracked mode (too complex).
        // Unsealed rumors (empty sig) must never get a public reaction —
        // the e-tag would leak the private rumor id to public relays.
        if (eventHint.event is NIP17Group || eventHint.event.sig.isEmpty()) return null

        val event = ReactionAction.reactTo(eventHint, reaction, signer)
        val relays = computeRelayListToBroadcast(event)

        return event to relays
    }

    /**
     * Consumes a reaction event into local cache.
     * Called when tracked broadcasting succeeds.
     */
    fun consumeReactionEvent(event: Event) {
        cache.justConsumeMyOwnEvent(event)
    }

    /**
     * NIP-32: tags [note] with [hashtag] by publishing a kind 1985 label event using the
     * `#t` tag-association namespace. Fire-and-forget; signs and broadcasts immediately.
     */
    suspend fun labelHashtag(
        note: Note,
        hashtag: String,
    ) {
        createLabelHashtagEvent(note, hashtag)?.let { (event, relays) ->
            cache.justConsumeMyOwnEvent(event)
            client.publish(event, relays)
        }
    }

    /**
     * Builds and signs a NIP-32 hashtag label event for [note] without sending it.
     * Returns the signed event and target relays for tracked broadcasting, or null if
     * the account can't write or the note has no underlying event.
     */
    suspend fun createLabelHashtagEvent(
        note: Note,
        hashtag: String,
    ): Pair<Event, Set<NormalizedRelayUrl>>? {
        if (!signer.isWriteable()) return null

        val eventHint = note.toEventHint<Event>() ?: return null

        val template = LabelEvent.buildHashtagLabel(eventHint, hashtag)

        val event = signer.sign(template)
        val relays = computeRelayListToBroadcast(event)

        return event to relays
    }

    /**
     * Consumes a label event into local cache. Called when tracked broadcasting succeeds.
     */
    fun consumeLabelEvent(event: Event) {
        cache.justConsumeMyOwnEvent(event)
    }

    suspend fun createZapRequestFor(
        event: Event,
        pollOption: Int?,
        message: String = "",
        zapType: LnZapEvent.ZapType,
        toUser: User?,
        additionalRelays: Set<NormalizedRelayUrl>? = null,
        amountMillisats: Long? = null,
        lnurl: String? = null,
    ) = LnZapRequestEvent.create(
        zappedEvent = event,
        relays = nip65RelayList.inboxFlow.value + (additionalRelays ?: emptySet()),
        signer = signer,
        pollOption = pollOption,
        message = message,
        zapType = zapType,
        toUserPubHex = toUser?.pubkeyHex,
        amountMillisats = amountMillisats,
        lnurl = lnurl,
    )

    suspend fun calculateIfNoteWasZappedByAccount(
        zappedNote: Note?,
        afterTimeInSeconds: Long,
    ): Boolean = zappedNote?.isZappedBy(userProfile(), afterTimeInSeconds, this) == true

    suspend fun calculateZappedAmount(zappedNote: Note): BigDecimal = zappedNote.zappedAmountWithNWCPayments(nip47SignerState)

    suspend fun sendNwcRequest(
        request: Request,
        onResponse: (Response?) -> Unit,
    ) {
        val (event, relay) = nip47SignerState.sendNwcRequest(request, onResponse)
        client.publish(event, setOf(relay))
    }

    suspend fun sendNwcRequestToWallet(
        walletUri: Nip47WalletConnect.Nip47URINorm,
        request: Request,
        onResponse: (Response?) -> Unit,
    ): HexKey {
        val (event, relay) = nip47SignerState.sendNwcRequestToWallet(walletUri, request, onResponse)
        client.publish(event, setOf(relay))
        return event.id
    }

    /**
     * Number of spoofed (wrong-author) NIP-47 replies that have arrived for
     * the given request id. 0 if the request is unknown or already resolved.
     */
    fun nwcSpoofAttempts(requestId: HexKey): Int = LocalCache.paymentTracker.spoofAttemptsFor(requestId)

    /**
     * Removes a pending NIP-47 request from the tracker. Call this when the
     * UI gives up waiting (timeout) so the entry doesn't stick around.
     */
    fun cleanupNwcRequest(requestId: HexKey) = LocalCache.paymentTracker.cleanup(requestId)

    suspend fun sendZapPaymentRequestFor(
        bolt11: String,
        zappedNote: Note?,
        onResponse: (Response?) -> Unit,
    ) {
        val (event, relay) = nip47SignerState.sendZapPaymentRequestFor(bolt11, zappedNote, onResponse)
        client.publish(event, setOf(relay))
    }

    suspend fun createZapRequestFor(
        user: User,
        message: String = "",
        zapType: LnZapEvent.ZapType,
        amountMillisats: Long? = null,
        lnurl: String? = null,
    ): LnZapRequestEvent {
        val zapRequest =
            LnZapRequestEvent.create(
                userHex = user.pubkeyHex,
                relays = nip65RelayList.inboxFlow.value + (user.inboxRelays() ?: emptyList()),
                signer = signer,
                message = message,
                zapType = zapType,
                amountMillisats = amountMillisats,
                lnurl = lnurl,
            )

        cache.justConsumeMyOwnEvent(zapRequest)
        return zapRequest
    }

    private fun onchainBackendNotConfigured() =
        OnchainZapSendResult.Failure(
            OnchainZapSendStage.LOADING_UTXOS,
            OnchainZapSendError.BACKEND_NOT_CONFIGURED,
            ONCHAIN_BACKEND_NOT_CONFIGURED,
        )

    /**
     * Send a NIP-BC onchain zap: build a Bitcoin transaction paying the recipient's
     * derived Taproot address, sign it, broadcast it, and publish the kind:8333
     * zap receipt. Pass [zappedEvent] to attribute the zap to a specific event, or
     * leave it null for a profile zap.
     */
    suspend fun sendOnchainZap(
        recipientPubKey: HexKey,
        amountSats: Long,
        feeRateSatPerVByte: Double,
        comment: String = "",
        zappedEvent: EventHintBundle<out Event>? = null,
    ): OnchainZapSendResult {
        val backend =
            cache.onchainBackend
                ?: return onchainBackendNotConfigured()
        return OnchainZapSender.send(
            backend = backend,
            signer = signer,
            senderPubKey = signer.pubKey,
            recipientPubKey = recipientPubKey,
            amountSats = amountSats,
            feeRateSatPerVByte = feeRateSatPerVByte,
            comment = comment,
            zappedEvent = zappedEvent,
        ) { template -> signAndComputeBroadcast(template) }
    }

    /**
     * Pay an explicit Bitcoin address (e.g. a profile's NIP-A3 `bitcoin`
     * payment target) from the NIP-BC Taproot wallet. A plain wallet send —
     * no kind:8333 receipt is published. See [OnchainZapSender.sendToAddress].
     */
    suspend fun sendOnchainToAddress(
        recipientAddress: String,
        amountSats: Long,
        feeRateSatPerVByte: Double,
    ): OnchainZapSendResult {
        val backend =
            cache.onchainBackend
                ?: return onchainBackendNotConfigured()
        return OnchainZapSender.sendToAddress(
            backend = backend,
            signer = signer,
            senderPubKey = signer.pubKey,
            recipientAddress = recipientAddress,
            amountSats = amountSats,
            feeRateSatPerVByte = feeRateSatPerVByte,
        )
    }

    /**
     * Send a NIP-BC onchain split zap: a single Bitcoin transaction paying
     * each recipient their precomputed share, plus one kind:8333 receipt per
     * recipient. See [OnchainZapSender.sendSplit] for failure semantics.
     */
    suspend fun sendOnchainZapWithSplits(
        recipients: List<OnchainZapShare>,
        feeRateSatPerVByte: Double,
        comment: String = "",
        zappedEvent: EventHintBundle<out Event>? = null,
    ): OnchainZapSendResult {
        val backend =
            cache.onchainBackend
                ?: return onchainBackendNotConfigured()
        return OnchainZapSender.sendSplit(
            backend = backend,
            signer = signer,
            senderPubKey = signer.pubKey,
            recipients = recipients,
            feeRateSatPerVByte = feeRateSatPerVByte,
            comment = comment,
            zappedEvent = zappedEvent,
        ) { template -> signAndComputeBroadcast(template) }
    }

    suspend fun report(
        note: Note,
        type: ReportType,
        content: String = "",
    ) {
        if (note.isPrivateRumor()) {
            // A kind-1984 e-tagging the rumor would leak the private id onto
            // public relays. Report the author instead (p-tag only).
            note.author?.let { report(it, type, content) }
            return
        }

        val powDifficulty = powDifficultyFor(ReportEvent.KIND)
        if (powDifficulty != null &&
            mineInBackground(ReportEvent.KIND, powDifficulty) { isActive ->
                sendMyPublicAndPrivateOutbox(
                    ReportAction.report(note, type, content, userProfile(), miningSigner(powDifficulty, setOf(ReportEvent.KIND), isActive)),
                )
            }
        ) {
            return
        }

        sendMyPublicAndPrivateOutbox(ReportAction.report(note, type, content, userProfile(), signer))
    }

    suspend fun report(
        user: User,
        type: ReportType,
        content: String = "",
    ) {
        val powDifficulty = powDifficultyFor(ReportEvent.KIND)
        if (powDifficulty != null &&
            mineInBackground(ReportEvent.KIND, powDifficulty) { isActive ->
                sendMyPublicAndPrivateOutbox(
                    ReportAction.report(user, type, content, userProfile(), miningSigner(powDifficulty, setOf(ReportEvent.KIND), isActive)),
                )
            }
        ) {
            return
        }

        sendMyPublicAndPrivateOutbox(ReportAction.report(user, type, content, userProfile(), signer))
    }

    suspend fun delete(note: Note) = delete(listOf(note))

    suspend fun delete(notes: List<Note>) {
        if (!isWriteable()) return

        val myNotes = notes.filter { it.author == userProfile() && it.event != null }
        if (myNotes.isNotEmpty()) {
            // chunks in 200 elements to avoid going over the 65KB limit for events.
            myNotes.chunked(200).forEach { chunkedList ->
                val template = DeletionEvent.build(chunkedList.mapNotNull { it.event })
                val deletionEvent = signer.sign(template)
                val myRelayList = outboxRelays.flow.value.toMutableSet()
                chunkedList.forEach {
                    myRelayList.addAll(it.relays)
                }

                client.publish(deletionEvent, myRelayList)
                cache.justConsumeMyOwnEvent(deletionEvent)
            }
        }
    }

    /**
     * Retracts rumor-only events (private reactions/replies) with a
     * gift-wrapped NIP-09 deletion delivered to the same participants as
     * the [target] rumor they referenced. A public deletion would e-tag
     * the private rumor ids onto public relays.
     */
    suspend fun deletePrivately(
        notes: List<Note>,
        target: Note,
    ) {
        if (!isWriteable()) return
        val targetEvent = target.event ?: return

        val myRumors = notes.filter { it.author == userProfile() }.mapNotNull { it.event }
        if (myRumors.isEmpty()) return

        val recipients = (targetEvent.taggedUserIds() + targetEvent.pubKey).distinct().minus(signer.pubKey)
        broadcastPrivately(
            NIP17Factory().createDeletionNIP17(DeletionEvent.build(myRumors), recipients, signer),
        )
    }

    suspend fun delete(
        event: Event,
        additionalRelays: Set<NormalizedRelayUrl>,
    ) {
        if (!isWriteable()) return
        if (event.pubKey != signer.pubKey) return

        val deletionEvent = signer.sign(DeletionEvent.build(listOf(event)))
        client.publish(deletionEvent, outboxRelays.flow.value + additionalRelays)
        cache.justConsumeMyOwnEvent(deletionEvent)
    }

    suspend fun createHTTPAuthorization(
        url: String,
        method: String,
        body: ByteArray? = null,
    ): HTTPAuthorizationEvent = signer.sign(HTTPAuthorizationEvent.build(url, method, body))

    suspend fun createBlossomUploadAuth(
        hash: HexKey,
        size: Long,
        alt: String,
        servers: List<String> = emptyList(),
    ) = blossomServers.createBlossomUploadAuth(hash, size, alt, servers)

    suspend fun createBlossomMediaAuth(
        hash: HexKey,
        size: Long,
        alt: String,
        servers: List<String> = emptyList(),
    ) = blossomServers.createBlossomMediaAuth(hash, size, alt, servers)

    suspend fun createBlossomDeleteAuth(
        hash: HexKey,
        alt: String,
        servers: List<String> = emptyList(),
    ) = blossomServers.createBlossomDeleteAuth(hash, alt, servers)

    suspend fun createBlossomListAuth(
        alt: String,
        servers: List<String> = emptyList(),
    ) = blossomServers.createBlossomListAuth(alt, servers)

    suspend fun boost(note: Note) {
        val powDifficulty = powDifficultyFor(RepostEvent.KIND)
        if (powDifficulty != null &&
            mineInBackground(RepostEvent.KIND, powDifficulty) { isActive ->
                repostNow(note, miningSigner(powDifficulty, setOf(RepostEvent.KIND, GenericRepostEvent.KIND), isActive))
            }
        ) {
            return
        }

        repostNow(note, signer)
    }

    private suspend fun repostNow(
        note: Note,
        repostSigner: NostrSigner,
    ) {
        RepostAction.repost(note, repostSigner)?.let { event ->
            client.publish(event, computeMyReactionToNote(note, event))
            cache.justConsumeMyOwnEvent(event)
        }
    }

    /**
     * Creates a boost event without sending it.
     * Returns the event and target relays for tracked broadcasting.
     */
    suspend fun createBoostEvent(note: Note): Pair<Event, Set<NormalizedRelayUrl>>? =
        RepostAction.repost(note, signer)?.let { event ->
            event to computeMyReactionToNote(note, event)
        }

    /**
     * Sends a boost event and updates the local cache.
     * Used after tracked broadcasting completes.
     */
    fun sendBoostEvent(
        event: Event,
        relays: Set<NormalizedRelayUrl>,
    ) {
        client.publish(event, relays)
        cache.justConsumeMyOwnEvent(event)
    }

    /**
     * Updates the local cache with a boost event.
     * Called when tracked broadcasting succeeds.
     */
    fun consumeBoostEvent(event: Event) {
        cache.justConsumeMyOwnEvent(event)
    }

    fun computeMyReactionToNote(
        note: Note,
        reaction: Event,
    ): Set<NormalizedRelayUrl> {
        val relaysItCameFrom = note.relays

        val inboxRelaysOfTheAuthorOfTheOriginalNote =
            note.author?.inboxRelays() ?: note.author?.pubkeyHex?.let {
                cache.relayHints.hintsForKey(it)
            } ?: emptyList()

        val reactionOutBoxRelays = outboxRelays.flow.value

        val taggedUsers = reaction.taggedUserIds() + (note.event?.taggedUserIds() ?: emptyList())

        val taggedUserInboxRelays =
            taggedUsers.flatMapTo(mutableSetOf()) { pubkey ->
                if (pubkey == userProfile().pubkeyHex) {
                    notificationRelays.flow.value
                } else {
                    cache
                        .getUserIfExists(pubkey)
                        ?.inboxRelays()
                        ?.ifEmpty { null }
                        ?.toSet()
                        ?: cache.relayHints.hintsForKey(pubkey).toSet()
                }
            }

        val channelRelays = cache.getAnyChannel(note)?.relays() ?: emptySet()

        val replyRelays =
            note.replyTo?.flatMapTo(mutableSetOf()) {
                val existingRelays = it.relays.toSet()

                val replyToAuthor = it.author

                val replyAuthorRelays =
                    if (replyToAuthor != null) {
                        if (replyToAuthor == userProfile()) {
                            outboxRelays.flow.value
                        } else {
                            replyToAuthor.inboxRelays()?.ifEmpty { null }?.toSet()
                                ?: replyToAuthor.allUsedRelaysOrNull()
                                ?: cache.relayHints
                                    .hintsForKey(replyToAuthor.pubkeyHex)
                                    .ifEmpty { null }
                                    ?.toSet()
                                ?: emptySet()
                        }
                    } else {
                        emptySet()
                    }

                existingRelays + replyAuthorRelays
            } ?: emptySet()

        return reactionOutBoxRelays +
            inboxRelaysOfTheAuthorOfTheOriginalNote +
            taggedUserInboxRelays +
            channelRelays +
            replyRelays +
            relaysItCameFrom
    }

    private fun computeRelayListForLinkedUser(user: User): Set<NormalizedRelayUrl> =
        if (user == userProfile()) {
            notificationRelays.flow.value
        } else {
            user.inboxRelays()?.ifEmpty { null }?.toSet()
                ?: (cache.relayHints.hintsForKey(user.pubkeyHex).toSet() + user.allUsedRelays())
        }

    private fun computeRelayListForLinkedUser(pubkey: HexKey): Set<NormalizedRelayUrl> =
        if (pubkey == userProfile().pubkeyHex) {
            notificationRelays.flow.value
        } else {
            cache
                .getUserIfExists(pubkey)
                ?.inboxRelays()
                ?.ifEmpty { null }
                ?.toSet()
                ?: cache.relayHints.hintsForKey(pubkey).toSet()
        }

    private fun computeRelaysForChannels(event: Event): Set<NormalizedRelayUrl> = cache.getAnyChannel(event)?.relays() ?: emptySet()

    // Personal events the user stores just for themselves — drafts, app settings, bookmark
    // lists — and channel/community events that already declare their own home relays
    // should not be replicated to the user's broadcasting relays. Channel/community events
    // that don't define any home relays fall through to broadcast, since there's nowhere
    // else for them to land.
    private fun wantsBroadcastRelays(event: Event): Boolean {
        if (event is DraftWrapEvent ||
            event is AppSpecificDataEvent ||
            event is BookmarkListEvent ||
            event is OldBookmarkListEvent ||
            event is LabeledBookmarkListEvent
        ) {
            return false
        }
        if (event is PollEvent && event.relays().isNotEmpty()) return false
        if (event is MeetingSpaceEvent && event.allRelayUrls().isNotEmpty()) return false
        if (event is MeetingRoomEvent && event.allRelayUrls().isNotEmpty()) return false
        if (event is LiveActivitiesEvent && event.allRelayUrls().isNotEmpty()) return false

        val channelRelays = cache.getAnyChannel(event)?.relays()
        if (channelRelays != null && channelRelays.isNotEmpty()) return false

        return true
    }

    fun computeRelayListToBroadcast(event: Event): Set<NormalizedRelayUrl> = computeRelayListToBroadcast(event, mutableSetOf())

    private fun computeRelayListToBroadcast(
        event: Event,
        visited: MutableSet<HexKey>,
    ): Set<NormalizedRelayUrl> {
        // a-tagged events can form cycles; without this the two recursive descents stack-overflow.
        if (!visited.add(event.id)) return emptySet()

        if (event is GiftWrapEvent) {
            val receiver = event.recipientPubKey()
            return if (receiver != null) {
                val relayList =
                    cache
                        .getOrCreateUser(receiver)
                        .dmInboxRelayList()
                        ?.relays()
                        ?.ifEmpty { null }
                relayList?.toSet() ?: computeRelayListForLinkedUser(receiver)
            } else {
                emptySet()
            }
        }
        // Seals, inner DM messages, and unsigned rumors never get broadcast
        // relays: they only travel inside gift wraps.
        if (event is SealedRumorEvent || event is BaseDMGroupEvent || event.sig.isEmpty()) {
            return emptySet()
        }

        val includeBroadcast = wantsBroadcastRelays(event)
        val broadcastRelays = if (includeBroadcast) broadcastRelayList.flow.value else emptySet()

        if (event is MetadataEvent || event is AdvertisedRelayListEvent) {
            // everywhere
            return followPlusAllMineWithIndex.flow.value + client.availableRelaysFlow().value + broadcastRelays
        }

        val relayList = mutableSetOf<NormalizedRelayUrl>()
        relayList.addAll(broadcastRelays)

        val author = cache.getUserIfExists(event.pubKey)

        if (author != null) {
            if (author == userProfile()) {
                if (includeBroadcast) {
                    relayList.addAll(outboxRelays.flow.value)
                } else {
                    // outboxRelays mixes in the broadcast list; for personal/channel events
                    // we want the user's NIP-65 / private / local outbox without it.
                    relayList.addAll(nip65RelayList.outboxFlow.value)
                    relayList.addAll(privateStorageRelayList.flow.value)
                    relayList.addAll(localRelayList.flow.value)
                }
            } else {
                val relays =
                    author.outboxRelays()?.ifEmpty { null }
                        ?: author.allUsedRelaysOrNull()
                        ?: cache.relayHints.hintsForKey(author.pubkeyHex)

                relayList.addAll(relays)
            }
        } else {
            relayList.addAll(cache.relayHints.hintsForKey(event.pubKey))
        }

        if (event is PubKeyHintProvider) {
            event.pubKeyHints().forEach {
                relayList.add(it.relay)
            }
            event.linkedPubKeys().forEach { pubkey ->
                relayList.addAll(computeRelayListForLinkedUser(pubkey))
            }
        }

        if (event is EventHintProvider) {
            event.eventHints().forEach {
                relayList.add(it.relay)
            }
            event.linkedEventIds().forEach { eventId ->
                cache.getNoteIfExists(eventId)?.let { linkedNote ->
                    val linkedNoteAuthor = linkedNote.author

                    if (linkedNoteAuthor != null) {
                        relayList.addAll(computeRelayListForLinkedUser(linkedNoteAuthor))
                    } else {
                        relayList.addAll(linkedNote.relays.toSet())
                    }

                    linkedNote.event?.let { linkedEvent ->
                        relayList.addAll(computeRelayListToBroadcast(linkedEvent, visited))
                    }
                }
            }
        }

        if (event is AddressHintProvider) {
            event.addressHints().forEach {
                relayList.add(it.relay)
            }
            event.linkedAddressIds().forEach { addressId ->
                cache.getAddressableNoteIfExists(addressId)?.let { linkedNote ->
                    val linkedNoteAuthor = linkedNote.author

                    if (linkedNoteAuthor != null) {
                        relayList.addAll(computeRelayListForLinkedUser(linkedNoteAuthor))
                    } else {
                        relayList.addAll(linkedNote.relays.toSet())
                    }

                    linkedNote.event?.let { linkedEvent ->
                        relayList.addAll(computeRelayListToBroadcast(linkedEvent, visited))
                    }
                }
            }
        }

        if (event is PollEvent) {
            relayList.addAll(event.relays())
        }

        if (event is MeetingSpaceEvent) {
            relayList.addAll(event.allRelayUrls())
        }

        if (event is MeetingRoomEvent) {
            relayList.addAll(event.allRelayUrls())
        }

        if (event is LiveActivitiesEvent) {
            relayList.addAll(event.allRelayUrls())
        }

        relayList.addAll(computeRelaysForChannels(event))

        return relayList
    }

    fun computeRelayListToBroadcast(note: Note): Set<NormalizedRelayUrl> {
        val noteEvent = note.event
        return if (noteEvent != null) {
            computeRelayListToBroadcast(noteEvent)
        } else {
            note.relays.toSet()
        }
    }

    suspend fun broadcast(note: Note) {
        note.event?.let { noteEvent ->
            val host = note.rumorHost
            if (host != null) {
                // Rumors are rebroadcast as their delivering envelope: the
                // cached copy is content-stripped, so download it and send it.
                // A just-sent note has no relays until its self-wrap echoes
                // back — fall back to our own DM inbox relays. Bare seals
                // (kind 13) carry no p tag, so that filter is wrap-only.
                val relays = note.relays.ifEmpty { dmRelays.flow.value.toList() }
                val filter =
                    if (host.kind == SealedRumorEvent.KIND) {
                        Filter(
                            kinds = listOf(host.kind),
                            ids = listOf(host.id),
                        )
                    } else {
                        Filter(
                            kinds = listOf(host.kind),
                            tags = mapOf("p" to listOf(pubKey)),
                            ids = listOf(host.id),
                        )
                    }
                client
                    .fetchFirst(
                        filters = relays.associateWith { _ -> listOf(filter) },
                    )?.let { downloadedEvent ->
                        val toRelays = computeRelayListToBroadcast(downloadedEvent)
                        client.publish(downloadedEvent, toRelays)
                    }
            } else if (noteEvent.sig.isEmpty()) {
                // Rumor with no known wrap: publishing it would disclose the
                // private content to relays even though they reject the
                // missing signature.
                return
            } else {
                client.publish(noteEvent, computeRelayListToBroadcast(note))
            }
        }
    }

    fun upgradeAttestations() = otsState.upgradeAttestationsIfNeeded(::sendAutomatic)

    suspend fun follow(users: List<User>) = sendMyPublicAndPrivateOutbox(kind3FollowList.follow(users))

    suspend fun follow(user: User) = sendMyPublicAndPrivateOutbox(kind3FollowList.follow(user))

    suspend fun unfollow(user: User) = sendMyPublicAndPrivateOutbox(kind3FollowList.unfollow(user))

    suspend fun follow(channel: PublicChatChannel) = sendMyPublicAndPrivateOutbox(publicChatList.follow(channel))

    suspend fun unfollow(channel: PublicChatChannel) = sendMyPublicAndPrivateOutbox(publicChatList.unfollow(channel))

    suspend fun follow(channel: EphemeralChatChannel) = sendMyPublicAndPrivateOutbox(ephemeralChatList.follow(channel))

    suspend fun unfollow(channel: EphemeralChatChannel) = sendMyPublicAndPrivateOutbox(ephemeralChatList.unfollow(channel))

    suspend fun follow(channel: RelayGroupChannel) = sendMyPublicAndPrivateOutbox(relayGroupList.follow(channel))

    suspend fun unfollow(channel: RelayGroupChannel) = sendMyPublicAndPrivateOutbox(relayGroupList.unfollow(channel))

    /**
     * Add a joined Concord community (secret-bearing entry) to the private kind-13302
     * list, and announce a self-signed Guestbook JOIN so this member is visible to
     * whoever later refounds the community (CORD-06 re-keys the Guestbook membership).
     */
    suspend fun joinConcordCommunity(
        entry: ConcordCommunityListEntry,
        inviteCreator: HexKey? = null,
        inviteLabel: String? = null,
    ) {
        sendMyPublicAndPrivateOutbox(concordChannelList.follow(entry))
        announceConcordGuestbookJoin(entry, inviteCreator, inviteLabel)
    }

    /** Publishes a Guestbook JOIN (kind 3306) for [entry] to its community relays. */
    private suspend fun announceConcordGuestbookJoin(
        entry: ConcordCommunityListEntry,
        inviteCreator: HexKey?,
        inviteLabel: String?,
    ) {
        if (!isWriteable()) return
        val guestbook = ConcordActions.guestbookPlane(entry.root.hexToByteArray(), entry.id.hexToByteArray(), entry.rootEpoch)
        val wrap = ConcordActions.buildGuestbookJoin(signer, guestbook, TimeUtils.now(), inviteCreator, inviteLabel)
        concordSessions.ingest(wrap)
        val relays = entry.relays.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }
        if (relays.isNotEmpty()) client.publish(wrap, relays)
    }

    /**
     * Create a new Concord community: mint its genesis (metadata + #general),
     * publish the owner-signed genesis wraps to [relays] (or our outbox), and add
     * the secret-bearing entry to the kind-13302 joined list. Returns the new
     * community id, or null if not writeable.
     */
    suspend fun createConcordCommunity(
        name: String,
        description: String? = null,
        relays: List<String> = emptyList(),
        icon: ImagePointer? = null,
    ): String? {
        if (!isWriteable()) return null
        val relayUrls = relays.ifEmpty { outboxRelays.flow.value.map { it.url } }
        val community = ConcordActions.createCommunity(signer, name, TimeUtils.now(), description, relayUrls, icon)

        val publishTo = relayUrls.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }.ifEmpty { outboxRelays.flow.value }
        community.genesisWraps.forEach { client.publish(it, publishTo) }

        joinConcordCommunity(
            ConcordCommunityListEntry(
                id = community.communityIdHex,
                owner = community.ownerPubKey,
                ownerSalt = community.ownerSalt.toHexKey(),
                root = community.communityRoot.toHexKey(),
                rootEpoch = community.rootEpoch,
                relays = relayUrls,
                name = name,
                addedAt = TimeUtils.now() * 1000,
            ),
        )
        return community.communityIdHex
    }

    /**
     * Mint a shareable invite link for a joined community and publish its
     * kind-33301 public bundle to the community relays. Returns the `…/invite/…`
     * URL, or null if the community isn't joined or isn't writeable.
     */
    suspend fun mintConcordInvite(
        communityId: String,
        base: String = "https://amethyst.social",
    ): String? {
        if (!isWriteable()) return null
        val entry = concordChannelList.liveCommunities.value.firstOrNull { it.id == communityId } ?: return null
        val invite =
            ConcordActions.inviteFor(
                communityIdHex = entry.id,
                ownerPubKey = entry.owner,
                ownerSaltHex = entry.ownerSalt,
                communityRootHex = entry.root,
                rootEpoch = entry.rootEpoch,
                name = entry.name,
                relays = entry.relays,
            )
        val minted = ConcordActions.mintInviteLink(base, invite, TimeUtils.now(), entry.relays)

        val publishTo = entry.relays.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }.ifEmpty { outboxRelays.flow.value }
        if (publishTo.isNotEmpty()) client.publish(minted.bundleEvent, publishTo)
        return minted.url
    }

    /** Drop a joined Concord community from the private kind-13302 list by its id. */
    suspend fun leaveConcordCommunity(communityId: String) = sendMyPublicAndPrivateOutbox(concordChannelList.unfollow(communityId))

    /**
     * Redeem a Concord invite link (`…/invite/<naddr>#<fragment>`): parse it, fetch
     * the kind-33301 public bundle from the link's relays (+ our outbox), unlock it
     * with the fragment token, and add the resulting secret-bearing entry to the
     * kind-13302 joined list.
     *
     * Returns a [ConcordInviteResult] that separates the failure modes so the UI can
     * both explain what went wrong and decide whether a retry could ever help — a
     * bundle we can't open (e.g. minted by a newer client) must not strand the user
     * on a spinner that retries forever.
     *
     * A bundle whose `expires_at` has passed is rejected with
     * [ConcordInviteResult.Expired]. Expiry is resolved inside
     * [ConcordActions.classifyInvite], so it is enforced on every redeem path rather
     * than being a field nobody reads.
     *
     * **This must only ever be called from an explicit user action.** It contacts
     * relay URLs carried in the link (chosen by whoever minted it) and publishes a
     * Guestbook JOIN signed by this account, so calling it on deep-link arrival would
     * leak the user's IP and enroll them without consent — see `ConcordInviteScreen`.
     *
     * If the resolved community is already in the joined list, this returns
     * [ConcordInviteResult.Joined] without re-following or re-announcing a Guestbook
     * JOIN, so reopening an old invite for a community you're already in simply takes
     * you to it.
     */
    suspend fun joinConcordViaInvite(url: String): ConcordInviteResult {
        if (!isWriteable()) return ConcordInviteResult.InvalidLink
        val parsed = ConcordActions.parseInviteLink(url) ?: return ConcordInviteResult.InvalidLink

        val relays =
            (parsed.fragment.relays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) } + outboxRelays.flow.value).toSet()
        if (relays.isEmpty()) return ConcordInviteResult.NotReachable

        val filters = relays.associateWith { listOf(ConcordActions.bundleFilter(parsed.linkSignerPubKey)) }
        val wraps = client.fetchAll(filters = filters)

        // Resolve the coordinate per CORD-05 §2 (newest wins; a vsk=9 tombstone revokes even over a
        // stale openable copy) so we honour revocation and can tell the user *why* a link won't open
        // instead of stranding them on a spinner that retries a link we can never redeem.
        val bundle =
            when (val status = ConcordActions.classifyInvite(wraps, parsed.fragment.token)) {
                is InviteBundleStatus.Live -> status.invite
                is InviteBundleStatus.Expired -> return ConcordInviteResult.Expired
                InviteBundleStatus.Revoked -> return ConcordInviteResult.Revoked
                InviteBundleStatus.Unreadable -> return ConcordInviteResult.Incompatible
                InviteBundleStatus.Absent -> return ConcordInviteResult.NotReachable
            }

        // Already a member? Just take the user to the community. Re-following and re-announcing a
        // Guestbook JOIN (kind 3306) would spam the community relays with a fresh join every time an
        // old invite is reopened, so short-circuit to Joined — the screen forwards to the community
        // either way ("take me there", not "join again").
        if (concordChannelList.liveCommunities.value.any { it.id == bundle.communityId }) {
            return ConcordInviteResult.Joined(bundle.communityId)
        }

        val entry =
            ConcordCommunityListEntry(
                id = bundle.communityId,
                owner = bundle.owner,
                ownerSalt = bundle.ownerSalt,
                root = bundle.communityRoot,
                rootEpoch = bundle.rootEpoch,
                relays = bundle.relays,
                name = bundle.name,
                addedAt = TimeUtils.now() * 1000,
                // Anchor for stranded recovery: keep the link we joined through, domain-agnostic, so a
                // Refounding that leaves us out of the recipient set is recoverable later. See
                // recoverStrandedConcordCommunities().
                inviteRef = ConcordActions.bareInviteRef(url),
            )
        joinConcordCommunity(entry)
        return ConcordInviteResult.Joined(bundle.communityId)
    }

    /**
     * Post [text] to a Concord channel: derive the channel plane key, build an
     * encrypted-seal kind-1059 wrap authored by that plane key (not our identity),
     * fold it locally for an instant echo, and publish it to the community's relays.
     * The `p` tag is ephemeral, so this never routes through the DM outbox — it goes
     * straight to the community relay set. Returns false if not writeable or the
     * community isn't currently joined/folded.
     */
    suspend fun sendConcordChannelMessage(
        communityId: String,
        channelIdHex: String,
        text: String,
        replyTo: Note? = null,
        replyMode: ReplyMode = ReplyMode.INLINE,
    ): Boolean {
        if (!isWriteable()) return false
        val session = concordSessions.sessionFor(communityId) ?: return false
        val entry = session.entry
        val channelKey = ConcordActions.publicChannel(entry.root.hexToByteArray(), channelIdHex.hexToByteArray(), entry.rootEpoch)

        // NIP-30 custom-emoji tags for any `:shortcode:` the user typed, so the message renders the
        // custom image everywhere (the kind-9 rumor carries them; recipients render via the tags).
        val emojiTags = emoji.findEmojiTags(text).map { it.toTagArray() }.toTypedArray()

        val parent = replyTo?.event
        val wrap =
            when {
                // A minichat reply is a kind-1111 thread comment; an inline reply is a kind-9
                // message quoting the parent; a fresh post is a plain kind-9 message.
                parent != null && replyMode == ReplyMode.MINICHAT ->
                    ConcordActions.buildChannelReply(signer, channelKey, channelIdHex, entry.rootEpoch, parent, text, TimeUtils.now(), emojiTags)
                parent != null ->
                    ConcordActions.buildChannelInlineReply(signer, channelKey, channelIdHex, entry.rootEpoch, parent, text, TimeUtils.now(), emojiTags)
                else ->
                    ConcordActions.buildChannelMessage(signer, channelKey, channelIdHex, entry.rootEpoch, text, TimeUtils.now(), emojiTags)
            }
        trackConcordDelivery(entry, channelKey, wrap)
        publishConcordWrap(entry, wrap)
        return true
    }

    /**
     * Send a channel message carrying encrypted image attachments ([imetas], built by the composer
     * from the encrypted upload) — Armada's `encryptAttachments` shape. The ciphertext URLs are
     * appended to [text] and each rides as a NIP-92 `imeta` with `aes-gcm` decryption params. With no
     * attachments this is just a plain [sendConcordChannelMessage].
     */
    suspend fun sendConcordChannelImageMessage(
        communityId: String,
        channelIdHex: String,
        text: String,
        imetas: List<IMetaTag>,
    ): Boolean {
        if (imetas.isEmpty()) return sendConcordChannelMessage(communityId, channelIdHex, text)
        if (!isWriteable()) return false
        val session = concordSessions.sessionFor(communityId) ?: return false
        val entry = session.entry
        val channelKey = ConcordActions.publicChannel(entry.root.hexToByteArray(), channelIdHex.hexToByteArray(), entry.rootEpoch)
        // Carry NIP-30 custom-emoji tags for any `:shortcode:` in the caption, same as a plain message.
        val emojiTags = emoji.findEmojiTags(text).map { it.toTagArray() }.toTypedArray()
        val wrap = ConcordActions.buildChannelImageMessage(signer, channelKey, channelIdHex, entry.rootEpoch, text, imetas, TimeUtils.now(), emojiTags)
        trackConcordDelivery(entry, channelKey, wrap)
        publishConcordWrap(entry, wrap)
        return true
    }

    /**
     * Post [text] into [rootNote]'s minichat — a kind-1111 thread reply rooted at that
     * message. Resolves the chat context from the note's gatherer; today it drives the
     * Concord channel path (NIP-28/NIP-29 public-chat minichats are a follow-up). Returns
     * false if the message isn't in a chat we can post a thread reply to.
     */
    suspend fun sendMinichatReply(
        rootNote: Note,
        text: String,
    ): Boolean {
        if (!isWriteable()) return false
        val gatherers = rootNote.inGatherers

        gatherers?.firstNotNullOfOrNull { it as? ConcordChannel }?.let { concord ->
            return sendConcordChannelMessage(
                concord.channelId.communityId,
                concord.channelId.channelId,
                text,
                rootNote,
                ReplyMode.MINICHAT,
            )
        }

        // Public chats: a plain public kind-1111 comment rooted at the message. NIP-29 groups
        // additionally carry the `h` tag and go only to the host relay.
        val rootEvent = rootNote.event ?: return false

        gatherers?.firstNotNullOfOrNull { it as? PublicChatChannel }?.let { chat ->
            val relays = chat.relays()
            val signed = signer.sign(CommentEvent.replyBuilder(text, EventHintBundle(rootEvent, relays.firstOrNull())))
            cache.justConsumeMyOwnEvent(signed)
            client.publish(signed, relays.ifEmpty { outboxRelays.flow.value })
            return true
        }

        gatherers?.firstNotNullOfOrNull { it as? RelayGroupChannel }?.let { group ->
            val hostRelay = group.groupId.relayUrl
            val signed =
                if (BuzzRelayDialect.isBuzz(hostRelay)) {
                    // Buzz rejects kind-1111, so its minichat threads with a 40002 marked at the message's
                    // root (never `broadcast` — a minichat reply always lives in the thread).
                    val root = rootEvent.tags.buzzThreadRoot() ?: rootEvent.tags.buzzThreadReply() ?: rootEvent.id
                    signer.sign(
                        StreamMessageV2Event.build(group.groupId.id, text) {
                            buzzThread(root, rootEvent.id)
                            rootNote.author?.pubkeyHex?.let { pTag(PTag(it)) }
                            previous(group.previousEventRefs(pubKey))
                        },
                    )
                } else {
                    signer.sign(
                        CommentEvent.replyBuilder(text, EventHintBundle(rootEvent, hostRelay)) {
                            hTag(group.groupId.id)
                            previous(group.previousEventRefs(pubKey))
                        },
                    )
                }
            cache.justConsumeMyOwnEvent(signed)
            client.publish(signed, setOf(hostRelay))
            return true
        }

        return false
    }

    /**
     * React to a Concord message with [reaction] (e.g. `"+"`, an emoji). Mirrors
     * [sendConcordChannelMessage]: builds a kind-7 rumor bound to the message's
     * channel/epoch, wraps it on the plane, and publishes it — so the reaction stays
     * inside the encrypted channel (never a plaintext public kind-7 that would leak
     * the message id). [note] must be a Concord channel message (carries a
     * [ConcordChannel] gatherer).
     */
    suspend fun reactToConcordMessage(
        note: Note,
        reaction: String,
    ): Boolean {
        if (!isWriteable()) return false
        val channel = note.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel } ?: return false
        val target = note.event ?: return false
        val communityId = channel.channelId.communityId
        val channelIdHex = channel.channelId.channelId
        val entry = concordSessions.sessionFor(communityId)?.entry ?: return false

        val channelKey = ConcordActions.publicChannel(entry.root.hexToByteArray(), channelIdHex.hexToByteArray(), entry.rootEpoch)
        // A custom-emoji reaction is a `:shortcode:` content that needs its NIP-30 `emoji` tag to
        // resolve to an image on the other side; a plain unicode/`+` reaction yields no tags.
        val emojiTags = emoji.findEmojiTags(reaction).map { it.toTagArray() }.toTypedArray()
        val wrap = ConcordActions.buildChannelReaction(signer, channelKey, channelIdHex, entry.rootEpoch, target, reaction, TimeUtils.now(), emojiTags)
        publishConcordWrap(entry, wrap)
        return true
    }

    /**
     * Edit my own Concord channel message [note] to [newText]. Mirrors
     * [reactToConcordMessage]: builds a kind-1010 [ChannelChat.edit] rumor bound to the
     * message's channel/epoch, wraps it on the plane, and publishes it — so the edit stays
     * inside the encrypted channel (a public kind-1010 would e-tag the private rumor id onto
     * public relays). The receiving side overlays the newest edit onto the target message;
     * only the *original author's* edits are applied, so we gate to my own kind-9 messages.
     * Returns false if [note] isn't an editable Concord message I authored.
     */
    suspend fun editConcordChannelMessage(
        note: Note,
        newText: String,
    ): Boolean {
        if (!isWriteable()) return false
        val channel = note.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel } ?: return false
        val target = note.event ?: return false
        // Edits only apply to plain kind-9 messages, and only the author may edit their own.
        if (target !is ChatEvent || target.pubKey != signer.pubKey) return false

        val communityId = channel.channelId.communityId
        val channelIdHex = channel.channelId.channelId
        val entry = concordSessions.sessionFor(communityId)?.entry ?: return false

        val channelKey = ConcordActions.publicChannel(entry.root.hexToByteArray(), channelIdHex.hexToByteArray(), entry.rootEpoch)
        // Carry NIP-30 custom-emoji tags for any `:shortcode:` in the new text, same as a fresh message.
        val emojiTags = emoji.findEmojiTags(newText).map { it.toTagArray() }.toTypedArray()
        val wrap = ConcordActions.buildChannelEdit(signer, channelKey, channelIdHex, entry.rootEpoch, target, newText, TimeUtils.now(), emojiTags)
        publishConcordWrap(entry, wrap)
        return true
    }

    /**
     * Publish a typing heartbeat (kind-23311, ephemeral 21059) to a Concord channel — call at
     * most every few seconds while composing. Not folded locally (we never show our own typing);
     * ephemeral, so relays broadcast but never store it.
     */
    suspend fun sendConcordTyping(
        communityId: String,
        channelIdHex: String,
    ) {
        if (!isWriteable()) return
        val entry = concordSessions.sessionFor(communityId)?.entry ?: return
        val channelKey = ConcordActions.publicChannel(entry.root.hexToByteArray(), channelIdHex.hexToByteArray(), entry.rootEpoch)
        val wrap = ConcordActions.buildChannelTyping(signer, channelKey, channelIdHex, entry.rootEpoch, TimeUtils.now())
        val relays = entry.relays.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }
        if (relays.isNotEmpty()) client.publish(wrap, relays)
    }

    /** Instant local echo (the session folds it back as a Note) + publish to the community relays. */
    private fun publishConcordWrap(
        entry: ConcordCommunityListEntry,
        wrap: Event,
    ) {
        concordSessions.ingest(wrap)
        val relays = entry.relays.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }
        if (relays.isNotEmpty()) client.publish(wrap, relays)
    }

    /**
     * Registers an own Concord channel message with the delivery tracker so its chat
     * bubble shows relay-acceptance ticks. Relays OK the encrypted [wrap], but the feed
     * shows the inner rumor, so we re-open the wrap (we just built it, so this always
     * succeeds) to key the tracker by the rumor id the bubble is drawn from. Reactions
     * and typing wraps skip this — they never become a feed row.
     */
    private fun trackConcordDelivery(
        entry: ConcordCommunityListEntry,
        channelKey: GroupKey,
        wrap: Event,
    ) {
        val rumorId = ConcordStreamEnvelope.openOrNull(wrap, channelKey)?.rumor?.id ?: return
        val relays = entry.relays.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }
        chatDeliveryTracker.trackWrappedPublic(rumorId, wrap.id, relays)
    }

    // ── Concord roles & moderation (CORD-04) ─────────────────────────────────
    // Each publishes a Control Plane edition; authority is enforced at fold time by
    // every client's AuthorityResolver, so a call by someone who doesn't outrank the
    // target is simply dropped on fold. Owner-authored calls always take effect.

    /** Grant [member] exactly [roleIds] (empty list revokes their roles). */
    suspend fun grantConcordRole(
        communityId: String,
        member: HexKey,
        roleIds: List<String>,
    ): Boolean {
        val session = concordSessions.sessionFor(communityId) ?: return false
        if (!isWriteable()) return false
        val wrap = ConcordModeration.grant(signer, session.controlPlaneKey(), communityId.hexToByteArray(), member, roleIds, session.controlEditions(), TimeUtils.now(), owner = session.entry.owner)
        publishConcordWrap(session.entry, wrap)
        return true
    }

    /** The default community Admin role: position 1, holding every management + moderation permission. */
    private fun concordAdminRole() =
        RoleEntity(
            name = CONCORD_ADMIN_ROLE,
            position = 1,
            permissions =
                ConcordPermissions
                    .of(
                        ConcordPermissions.MANAGE_ROLES,
                        ConcordPermissions.MANAGE_CHANNELS,
                        ConcordPermissions.MANAGE_METADATA,
                        ConcordPermissions.KICK,
                        ConcordPermissions.BAN,
                        ConcordPermissions.MANAGE_MESSAGES,
                        ConcordPermissions.CREATE_INVITE,
                    ).toWire(),
        )

    /**
     * If [note] is a Concord channel message whose author the OWNER may toggle
     * "admin" on, returns `(communityId, memberHex, isAlreadyAdmin)`. Only the owner
     * qualifies — the Admin role sits at position 1 and the resolver requires the
     * granter to *strictly* outrank it, which only the owner (rank 0) does. Null for
     * the owner's own note, the owner as target, or a non-owner actor.
     */
    fun concordAdminTarget(note: Note): Triple<String, HexKey, Boolean>? {
        val channel = note.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel } ?: return null
        val author = note.author?.pubkeyHex ?: note.event?.pubKey ?: return null
        if (author == signer.pubKey) return null
        val communityId = channel.channelId.communityId
        val state = concordSessions.sessionFor(communityId)?.state?.value ?: return null
        if (state.authority.isOwner(author) || !state.authority.isOwner(signer.pubKey)) return null
        val adminRoleId =
            state.roles.entries
                .firstOrNull { it.value.name == CONCORD_ADMIN_ROLE && it.value.position == 1L }
                ?.key
        val isAdmin = adminRoleId != null && adminRoleId in state.authority.rolesOf(author)
        return Triple(communityId, author, isAdmin)
    }

    /** Promote [member] to the community Admin role, defining that role first if it doesn't exist yet. */
    suspend fun makeConcordAdmin(
        communityId: String,
        member: HexKey,
    ): Boolean {
        val session = concordSessions.sessionFor(communityId) ?: return false
        if (!isWriteable()) return false
        val cp = session.controlPlaneKey()

        val existing =
            session.state.value
                ?.roles
                ?.entries
                ?.firstOrNull { it.value.name == CONCORD_ADMIN_ROLE && it.value.position == 1L }
        val roleIdHex =
            existing?.key ?: run {
                val roleId = RandomInstance.bytes(32)
                val roleWrap = ConcordModeration.defineRole(signer, cp, roleId, concordAdminRole(), session.controlEditions(), TimeUtils.now(), owner = session.entry.owner)
                publishConcordWrap(session.entry, roleWrap)
                roleId.toHexKey()
            }

        val grantWrap = ConcordModeration.grant(signer, cp, communityId.hexToByteArray(), member, listOf(roleIdHex), session.controlEditions(), TimeUtils.now(), owner = session.entry.owner)
        publishConcordWrap(session.entry, grantWrap)
        return true
    }

    /** Revoke all roles from [member] (demote an admin back to a plain member). */
    suspend fun removeConcordAdmin(
        communityId: String,
        member: HexKey,
    ): Boolean {
        val session = concordSessions.sessionFor(communityId) ?: return false
        if (!isWriteable()) return false
        val grantWrap = ConcordModeration.grant(signer, session.controlPlaneKey(), communityId.hexToByteArray(), member, emptyList(), session.controlEditions(), TimeUtils.now(), owner = session.entry.owner)
        publishConcordWrap(session.entry, grantWrap)
        return true
    }

    /**
     * If [note] is a Concord channel message whose author this account is allowed to
     * ban — the actor outranks the target and holds the BAN permission, and the target
     * is neither the owner nor the actor — returns `(communityId, memberHex)`. Null
     * otherwise, so the UI offers Ban only where we are willing to act.
     *
     * The rank half is ours alone. CORD-04 rank-gates role grants (`canActOn`) but the
     * BANLIST is a single whole-list entity, so neither this client's fold nor Armada's
     * rank-checks the *contents* of a banlist edition — both gate only on the author's
     * BAN bit (Armada: `banlistGate` → `isAuthorized(.., Permissions.BAN)`, while its
     * role path uses the rank-aware `canActOnPosition`). A moderator's ban of an admin
     * above them is therefore *accepted* by every client today. Since we cannot refuse
     * such a ban without diverging from Armada, we at least refuse to author one — this
     * restricts what we write, never what we accept, so it cannot split consensus.
     * Enforcing it on the fold needs a spec change; see the QA plan's open findings.
     */
    fun concordBanTarget(note: Note): Pair<String, HexKey>? {
        val channel = note.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel } ?: return null
        val author = note.author?.pubkeyHex ?: note.event?.pubKey ?: return null
        if (author == signer.pubKey) return null
        val communityId = channel.channelId.communityId
        val authority =
            concordSessions
                .sessionFor(communityId)
                ?.state
                ?.value
                ?.authority ?: return null
        if (authority.isOwner(author)) return null
        // The owner short-circuits rather than going through canActOn: canActOn starts at
        // hasPermission, which is false while banned, and a rogue BAN holder *can* currently put
        // the owner on the banlist (see the KDoc) — routing the owner through it would let them be
        // locked out of moderating their own community.
        val canBan = authority.isOwner(signer.pubKey) || authority.canActOn(signer.pubKey, author, ConcordPermissions.BAN)
        return if (canBan) communityId to author else null
    }

    /** Add [member] to the community banlist. */
    suspend fun banConcordMember(
        communityId: String,
        member: HexKey,
    ): Boolean {
        val session = concordSessions.sessionFor(communityId) ?: return false
        if (!isWriteable()) return false
        val wrap = ConcordModeration.ban(signer, session.controlPlaneKey(), communityId.hexToByteArray(), member, session.controlEditions(), TimeUtils.now(), owner = session.entry.owner)
        publishConcordWrap(session.entry, wrap)
        return true
    }

    /** Remove [member] from the community banlist. */
    suspend fun unbanConcordMember(
        communityId: String,
        member: HexKey,
    ): Boolean {
        val session = concordSessions.sessionFor(communityId) ?: return false
        if (!isWriteable()) return false
        val wrap = ConcordModeration.unban(signer, session.controlPlaneKey(), communityId.hexToByteArray(), member, session.controlEditions(), TimeUtils.now(), owner = session.entry.owner)
        publishConcordWrap(session.entry, wrap)
        return true
    }

    // ── Concord refounding / rekey (CORD-06) ──────────────────────────────────
    // A ban is a soft removal — the banned member still holds the room key and can
    // still decrypt traffic; every client just declines to *show* their posts. A
    // Refounding is the hard removal: it rotates the community_root, so a removed
    // member's key stops working for anything published afterwards.

    /**
     * Remove [removed] from the community absolutely (CORD-06 Refounding): ban them,
     * roll the `community_root`, re-key every retained member (Guestbook membership ∪
     * observed authors ∪ the privileged roster ∪ self) via kind-3303 blobs, and republish the compacted
     * Control Plane under the new root. A removed member keeps the prior root (so
     * their history stays readable) but receives no blob, so they can never decrypt
     * anything published after the rotation.
     *
     * Requires ownership or the BAN permission; returns false otherwise (or if the
     * community isn't joined/writeable, or a target is the owner).
     */
    suspend fun refoundConcordCommunity(
        communityId: String,
        removed: Set<HexKey>,
    ): Boolean {
        if (!isWriteable()) return false
        val session = concordSessions.sessionFor(communityId) ?: return false
        val state = session.state.value ?: return false
        val authority = state.authority
        val iCanBan = authority.isOwner(signer.pubKey) || authority.effectivePermissions(signer.pubKey).has(ConcordPermissions.BAN)
        if (!iCanBan) return false
        val removedLower = removed.mapTo(HashSet()) { it.lowercase() }
        if (removedLower.isEmpty() || removedLower.any { authority.isOwner(it) }) return false

        // 1. Ban the removed members on the current Control Plane so the compacted snapshot —
        //    and thus the new epoch — carries the ban. publishConcordWrap folds it in locally
        //    first, so each subsequent edition chains onto the updated banlist head.
        for (target in removedLower) {
            val banWrap = ConcordModeration.ban(signer, session.controlPlaneKey(), communityId.hexToByteArray(), target, session.controlEditions(), TimeUtils.now(), owner = session.entry.owner)
            publishConcordWrap(session.entry, banWrap)
        }

        // 2. Recipient set: everyone we're keeping, minus the removed and the already-banned.
        //    Uses allMembers() — Guestbook joins ∪ OBSERVED AUTHORS ∪ roster ∪ owner — not just the
        //    Guestbook set. Most members never send a Guestbook Join (Amethyst announces one, other
        //    clients need not), so building the set without observed authors silently expelled every
        //    member who had only ever posted: they hold no role, receive no blob, and the Refounding
        //    strands them. That mainly hit cross-client communities, where Armada members are the
        //    bulk of the roster.
        //
        //    Still a floor, not a census (see allMembers): a member who joined without a Guestbook
        //    motion, holds no role, and has never posted leaves no trace to find, so a Refounding
        //    cannot re-key them. Stranded recovery is what gets those members back.
        val recipients =
            (session.allMembers() + signer.pubKey)
                .mapTo(HashSet()) { it.lowercase() }
                .apply {
                    removeAll(removedLower)
                    removeAll(authority.bannedMembers())
                }.toList()

        // 3. Build the refounding: new root, compacted Control Plane, per-recipient rekey blobs.
        val entry = session.entry
        val newRoot = RandomInstance.bytes(32)
        val build =
            ConcordActions.buildRefounding(
                rotatorSigner = signer,
                communityId = communityId,
                priorRoot = entry.root.hexToByteArray(),
                newRoot = newRoot,
                rootEpoch = entry.rootEpoch,
                priorControlWraps = session.controlPlaneWraps(),
                priorControlKey = session.controlPlaneKey(),
                recipientsXOnly = recipients,
                createdAt = TimeUtils.now(),
            )

        // 4. Publish the compacted Control Plane (the new epoch's state) then the rekey blobs
        //    (the key that unlocks it) to the community relays.
        val publishTo = entry.relays.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }
        if (publishTo.isNotEmpty()) {
            build.controlWraps.forEach { client.publish(it, publishTo) }
            build.rekeyWraps.forEach { client.publish(it, publishTo) }
        }

        // 5. Adopt the new epoch ourselves. This rebuilds our session under the new root and
        //    re-folds the compacted Control Plane (with the ban), dropping the removed members.
        adoptConcordRoot(entry, newRoot, build.newEpoch)
        return true
    }

    // Rotations we've already adopted ("communityId:epoch"), so a base-rekey wrap still buffered
    // in the pre-rebuild window (the session rebuild off `liveCommunities` is async) is not
    // adopted — and re-published — twice on successive revision ticks.
    private val adoptedConcordRotations = java.util.Collections.synchronizedSet(HashSet<String>())

    /**
     * Persist a rotated access root/epoch for [entry], keeping the prior root as a
     * [HeldRoot], and re-announce our Guestbook membership at the new epoch so the
     * fresh epoch's Guestbook re-seeds (a later Refounding re-keys that membership —
     * without this, cascading removals would lose everyone but the roster). No-op if
     * this exact rotation was already adopted.
     */
    private suspend fun adoptConcordRoot(
        entry: ConcordCommunityListEntry,
        newRoot: ByteArray,
        newEpoch: Long,
    ) {
        if (!adoptedConcordRotations.add("${entry.id}:$newEpoch")) return
        val held = (entry.heldRoots + HeldRoot(entry.rootEpoch, entry.root)).distinctBy { it.epoch }
        val next =
            ConcordCommunityListEntry(
                id = entry.id,
                owner = entry.owner,
                ownerSalt = entry.ownerSalt,
                root = newRoot.toHexKey(),
                rootEpoch = newEpoch,
                heldRoots = held,
                privateChannels = entry.privateChannels,
                relays = entry.relays,
                name = entry.name,
                addedAt = entry.addedAt,
                // The invite_ref anchor must survive a rotation, or the *next* Refounding we're left
                // out of would be unrecoverable.
                inviteRef = entry.inviteRef,
                excludedAtEpoch = entry.excludedAtEpoch,
                // Unknown keys another client wrote (Armada's list is `[k: string]: unknown`)
                // must survive our rotation write, or we delete their data on every rekey.
                residue = entry.residue,
            )
        sendMyPublicAndPrivateOutbox(concordChannelList.follow(next))
        announceConcordGuestbookJoin(next, inviteCreator = null, inviteLabel = null)
    }

    /**
     * Drain any buffered inbound base-rotation rekeys (CORD-06 receive path): for
     * each joined community, look for our new root among the kind-3303 wraps seen at
     * our next base-rekey address. If a role-authorized rotator (owner or a current,
     * non-banned BAN-holder) delivered us one, adopt it. Idempotent — once adopted, the
     * session rebuilds at the new epoch and its next-rekey address moves on, so a stale
     * wrap never re-triggers. Called on every Concord revision tick.
     *
     * Authority is the roster, never key possession: any non-banned BAN-holder may
     * rotate, including for the owner. The owner deliberately does NOT refuse a root
     * authored by someone else — refusing would strand the owner alone on the dead
     * epoch whenever an admin legitimately rotates, and would diverge from Armada,
     * which forks a community across clients. Self-escalation to BAN is prevented
     * upstream by the role rank gate in AuthorityResolver.
     *
     * A rotation carries only (newRoot, newEpoch, rotator); there is no recipient list,
     * so a receiver cannot tell who was left out, and a BAN-holder can evict anyone (the
     * owner included) by omission — nothing on this receive path can prevent it. The
     * cure is after the fact: see [recoverStrandedConcordCommunities], which re-resolves
     * the invite link the membership was joined through and merges forward.
     */
    private suspend fun drainConcordRekeys() {
        if (!isWriteable()) return
        for (session in concordSessions.sessions()) {
            val wraps = session.pendingBaseRekeyWraps()
            if (wraps.isEmpty()) continue
            val entry = session.entry
            val received =
                ConcordActions.openBaseRekey(
                    wraps = wraps,
                    baseRekey = session.nextBaseRekeyKey(),
                    recipientSigner = signer,
                    priorRoot = entry.root.hexToByteArray(),
                    rootEpoch = entry.rootEpoch,
                ) ?: continue
            if (received.newEpoch <= entry.rootEpoch) continue
            val authority = session.state.value?.authority ?: continue

            // hasPermission, not effectivePermissions: the latter ignores the banlist, so a BAN-holder
            // who has themselves been banned could still rotate the whole community.
            val authorized = authority.isOwner(received.rotator) || authority.hasPermission(received.rotator, ConcordPermissions.BAN)
            if (!authorized) continue
            adoptConcordRoot(entry, received.newRoot, received.newEpoch)
        }
    }

    // Last time we re-resolved each community's invite_ref, so the recovery sweep rides the
    // Concord revision tick (which fires on every structural change) without turning it into a
    // relay-fetch loop.
    private val lastConcordRecoveryCheck = ConcurrentHashMap<String, Long>()

    /**
     * Stranded recovery (CORD-05/06 receive path). A Refounding carries only
     * `(newRoot, newEpoch, rotator)` — **no recipient list** — so a member simply left
     * out of the rekey recipient set receives nothing and sits on the dead epoch
     * forever while everyone else moves on. This happens to any member, the owner
     * included, and [drainConcordRekeys] cannot prevent it: there is no message to
     * miss detecting.
     *
     * The way back is the invite link the membership was joined through
     * ([ConcordCommunityListEntry.inviteRef], persisted by [joinConcordViaInvite] and
     * carried through every rotation by [adoptConcordRoot]). The community keeps
     * re-minting its bundle at that same addressable coordinate, so a bundle there at
     * a **strictly higher** epoch than ours proves we were left behind — and carries
     * the new root. Same or lower epoch is a no-op. Memberships with no link (direct
     * invites, legacy entries) are inert here; that is expected, not an error.
     *
     * The merge itself ([ConcordActions.recoverStranded]) is epoch-monotonic and keeps
     * both the `invite_ref` anchor (so the *next* exclusion is recoverable too) and the
     * entry's [HeldRoot]s (so prior-epoch history the member legitimately holds stays
     * derivable). We then re-announce the Guestbook at the new epoch, exactly as an
     * ordinary rotation does, so the recovered member is visible to whoever refounds
     * next instead of being silently dropped again.
     *
     * Called on the Concord revision tick, but rate-limited per community
     * ([RECOVERY_CHECK_INTERVAL_MS]) — a tick with nothing to do costs a map lookup.
     */
    private suspend fun recoverStrandedConcordCommunities() {
        if (!isWriteable()) return
        val now = TimeUtils.nowMillis()
        for (entry in concordChannelList.liveCommunities.value) {
            val inviteRef = entry.inviteRef ?: continue
            val last = lastConcordRecoveryCheck[entry.id]
            if (last != null && now - last < RECOVERY_CHECK_INTERVAL_MS) continue
            lastConcordRecoveryCheck[entry.id] = now

            val parsed = ConcordActions.parseInviteLink(inviteRef) ?: continue
            val relays =
                (
                    parsed.fragment.relays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) } +
                        entry.relays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
                ).toSet()
            if (relays.isEmpty()) continue

            val filters = relays.associateWith { listOf(ConcordActions.bundleFilter(parsed.linkSignerPubKey)) }
            val wraps = client.fetchAll(filters = filters)
            // Only a live bundle recovers: an expired/revoked link is not a rotation we missed.
            val bundle = (ConcordActions.classifyInvite(wraps, parsed.fragment.token) as? InviteBundleStatus.Live)?.invite ?: continue

            val merged = ConcordActions.recoverStranded(entry, bundle) ?: continue
            if (!adoptedConcordRotations.add("${entry.id}:${merged.rootEpoch}")) continue
            Log.i("Concord", "Stranded recovery: ${entry.id} ${entry.rootEpoch} -> ${merged.rootEpoch}")
            sendMyPublicAndPrivateOutbox(concordChannelList.follow(merged))
            announceConcordGuestbookJoin(merged, inviteCreator = null, inviteLabel = null)
        }
    }

    /**
     * Replace the community metadata (name / icon / description / relays) with a new
     * Control-Plane edition. Honored on fold only when this account holds
     * MANAGE_METADATA (or is the owner); dropped otherwise, like every other edition.
     */
    suspend fun editConcordMetadata(
        communityId: String,
        name: String,
        description: String?,
        icon: ImagePointer?,
        banner: ImagePointer?,
        relays: List<String>,
    ): Boolean {
        val session = concordSessions.sessionFor(communityId) ?: return false
        if (!isWriteable()) return false
        val metadata = MetadataEntity(name = name, icon = icon, banner = banner, description = description, relays = relays)
        val wrap = ConcordModeration.editMetadata(signer, session.controlPlaneKey(), communityId.hexToByteArray(), metadata, session.controlEditions(), TimeUtils.now(), owner = session.entry.owner)
        publishConcordWrap(session.entry, wrap)
        return true
    }

    /**
     * Create a new public text channel in [communityId] (CORD-03/04 channel edition). Honored at fold
     * only when this account holds MANAGE_CHANNELS (or is the owner); the button should be gated on
     * the same predicate. The channel id is a fresh random 32-byte entity id.
     */
    suspend fun createConcordChannel(
        communityId: String,
        name: String,
    ): Boolean {
        val session = concordSessions.sessionFor(communityId) ?: return false
        if (!isWriteable()) return false
        val channelId = RandomInstance.bytes(32)
        val channel = ChannelEntity(name = name.trim())
        val wrap = ConcordModeration.defineChannel(signer, session.controlPlaneKey(), channelId, channel, session.controlEditions(), TimeUtils.now(), owner = session.entry.owner)
        publishConcordWrap(session.entry, wrap)
        return true
    }

    /** Rename an existing channel (chains the next channel edition onto its head). MANAGE_CHANNELS only. */
    suspend fun renameConcordChannel(
        communityId: String,
        channelIdHex: String,
        name: String,
    ): Boolean {
        val session = concordSessions.sessionFor(communityId) ?: return false
        if (!isWriteable()) return false
        // Carry the standing definition forward and change only the name. A ChannelEntity built from
        // scratch defaults `private` and `voice` to false, so renaming a private channel used to
        // publish an edition declaring it PUBLIC — and a voice channel became a text channel.
        val standing =
            session.state.value
                ?.channels
                ?.get(channelIdHex)
                ?.definition
        val channel = ChannelEntity(name = name.trim(), private = standing?.private ?: false, voice = standing?.voice ?: false)
        val wrap = ConcordModeration.defineChannel(signer, session.controlPlaneKey(), channelIdHex.hexToByteArray(), channel, session.controlEditions(), TimeUtils.now(), owner = session.entry.owner)
        publishConcordWrap(session.entry, wrap)
        return true
    }

    /** Delete (tombstone) a channel — terminal; its id is never reused. MANAGE_CHANNELS only. */
    suspend fun deleteConcordChannel(
        communityId: String,
        channelIdHex: String,
        name: String,
    ): Boolean {
        val session = concordSessions.sessionFor(communityId) ?: return false
        if (!isWriteable()) return false
        // Same as rename: preserve the standing flags so a tombstone does not also silently
        // reclassify the channel it retires.
        val standing =
            session.state.value
                ?.channels
                ?.get(channelIdHex)
                ?.definition
        val channel = ChannelEntity(name = name.trim(), private = standing?.private ?: false, voice = standing?.voice ?: false, deleted = true)
        val wrap = ConcordModeration.defineChannel(signer, session.controlPlaneKey(), channelIdHex.hexToByteArray(), channel, session.controlEditions(), TimeUtils.now(), owner = session.entry.owner)
        publishConcordWrap(session.entry, wrap)
        return true
    }

    /**
     * Read-only preview of an invite link: parse it, fetch the kind-33301 bundle from
     * the link's relays (+ our outbox), and unlock it with the fragment token — WITHOUT
     * joining. Returns the [CommunityInvite] (name, relays, community coordinates) so a
     * card can show what the link opens, or null if the link is invalid/unreadable.
     */
    suspend fun peekConcordInvite(url: String): CommunityInvite? {
        val parsed = ConcordActions.parseInviteLink(url) ?: return null
        val relays =
            (parsed.fragment.relays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) } + outboxRelays.flow.value).toSet()
        if (relays.isEmpty()) return null
        val filters = relays.associateWith { listOf(ConcordActions.bundleFilter(parsed.linkSignerPubKey)) }
        val wraps = client.fetchAll(filters = filters)
        return wraps.firstNotNullOfOrNull { ConcordActions.openBundle(it, parsed.fragment.token) }
    }

    /**
     * Bootstrap the Concord hub from the network: fetch this account's kind-13302
     * joined-communities list and fold the newest into [LocalCache], so communities
     * we joined on another Concord client with this key surface here.
     *
     * We query a wide relay set because different Concord clients publish this
     * private list to different places: the reference clients (Armada/Vector) push
     * it to the Concord **stock relays** (e.g. relay.ditto.pub), while a user may
     * also have copied it onto their **own** outbox/read relays. Our normal account
     * subscription never asks for kind 13302, so without this explicit fetch a
     * community joined on Armada would never appear — even if the list sits on the
     * user's own outbox.
     *
     * Read-only import: kind 13302 is replaceable, so folding an older copy is a
     * no-op and this is safe to call on every hub open. Merging our own edits with
     * a foreign writer's is a separate concern (newest-wins replaceable).
     *
     * [extraRelays] are additional relays to query — the bootstrap relays saved on the
     * bottom-bar tabs of pinned communities. A community's private list frequently lives
     * only on the community's own relays (never the user's outbox), so a community pinned
     * to the bottom bar would otherwise never surface when opened cold.
     */
    suspend fun importConcordCommunities(extraRelays: Set<NormalizedRelayUrl> = emptySet()) {
        val stock = InviteRelayDictionary.STOCK.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
        val relays = (stock + mineRelays.flow.value + outboxRelays.flow.value + extraRelays).toSet()
        if (relays.isEmpty()) return
        val filter = Filter(kinds = listOf(ConcordCommunityListEvent.KIND), authors = listOf(signer.pubKey))
        // Stock relays like relay.ditto.pub can be slow (~10–20s to first response), so give
        // the fetch a generous window to drain every relay before we pick the newest copy.
        val events = client.fetchAll(filters = relays.associateWith { listOf(filter) }, timeoutMs = 30_000L)
        val newest = events.filterIsInstance<ConcordCommunityListEvent>().maxByOrNull { it.createdAt }
        val entryCount = newest?.let { runCatching { it.decrypt(signer).size }.getOrElse { -1 } } ?: 0
        Log.d(
            "Concord",
            "importConcordCommunities: queried ${relays.size} relays, fetched ${events.size} 13302 event(s), " +
                "newest=${newest?.id?.take(8)}@${newest?.createdAt}, decoded $entryCount entr${if (entryCount == 1) "y" else "ies"}",
        )
        newest?.let { cache.justConsumeMyOwnEvent(it) }
    }

    /**
     * One-shot warm of every channel of [entries] so a community's channel list and the Messages inbox
     * fill in without the user opening each channel one by one. Per channel, a channel read before is
     * caught up from its last-read time (accurate unread badge + the missed messages ready when it
     * opens) while a channel never read pulls only its single newest wrap for a preview — see
     * [ConcordSubscriptionPlanner.channelPreviewFilters].
     *
     * This is deliberately **not** a live subscription: every wrap the drain pulls flows through the
     * global cache connector (`CacheClientConnector` → `LocalCache.justConsume` → `concordSessions.ingest`),
     * so it lands in the channel's message store the previews/unread counts read — and the always-on
     * plane subscription ([RelaySubscriptionsCoordinator.concordChannels]) keeps them fresh afterward.
     * So this only needs to run when a community's channels first fold (the account preload) or its
     * screen is opened. One drain per call: all [entries]' per-channel filters are grouped by relay.
     */
    suspend fun warmConcordChannelPreviews(entries: List<ConcordCommunityListEntry>) {
        val filters =
            entries.flatMap { entry ->
                val state = concordSessions.sessionFor(entry.id)?.state?.value ?: return@flatMap emptyList()
                ConcordSubscriptionPlanner.channelPreviewFilters(entry, state, lastReadFor = { channelIdHex ->
                    loadLastRead(concordChannelLastReadRoute(entry.id, channelIdHex))
                })
            }
        if (filters.isEmpty()) return
        val byRelay = filters.groupBy { it.relay }.mapValues { (_, group) -> group.map { it.filter } }
        client.fetchAll(filters = byRelay, timeoutMs = 20_000L)
    }

    // ── NIP-29 relay-group actions ───────────────────────────────────────────
    // All group commands are published ONLY to the group's host relay, where
    // relay29 authorizes them. The relay is the source of truth; the kind-10009
    // list is our own cross-device bookkeeping of what we joined.

    /** Send a kind 9021 join request to the group's host relay and remember it. */
    suspend fun joinRelayGroup(
        channel: RelayGroupChannel,
        code: String? = null,
    ) {
        val template = JoinRequestEvent.build(channel.groupId.id, inviteCode = code)
        signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
        follow(channel)
    }

    /**
     * Fire a Buzz kind-20002 typing heartbeat for [channel] to its host relay. Ephemeral
     * (never stored) and fire-and-forget — no delivery tracking, no local echo (we filter
     * our own typing in the UI). Throttled by the composer to [BuzzTypingState.TYPING_HEARTBEAT_SECS].
     */
    suspend fun sendBuzzTyping(channel: RelayGroupChannel) {
        if (!isWriteable()) return
        val signed = signer.sign(TypingIndicatorEvent.build(channel.groupId.id))
        client.publish(signed, setOf(channel.groupId.relayUrl))
    }

    /**
     * Open (or re-surface) a Buzz DM with [participants] on [relay] via a kind-41010
     * command. [participants] are the OTHER 1-8 people — the relay adds me, derives the
     * canonical channel UUID, and confirms with a relay-signed [DmCreatedEvent]
     * (kind-41001) that lands in [com.vitorpamplona.amethyst.commons.model.buzz.BuzzDmRegistry].
     * We never assign the channel id ourselves, so callers discover the materialized DM
     * by watching that registry rather than from this call's return.
     */
    suspend fun openBuzzDm(
        relay: NormalizedRelayUrl,
        participants: List<HexKey>,
    ): String? {
        val signed = signer.sign(DmOpenEvent.build(participants))
        // The relay confirms the DM synchronously in the OK as `response:{"channel_id":"…"}` —
        // the authoritative, relay-assigned channel UUID (the deployed relay does not emit a
        // queryable kind-41001). Read it straight from the ack so the caller can open the chat.
        var results = client.publishAndCollectResults(signed, setOf(relay))
        var channelId = buzzDmChannelIdFromAck(results)

        // NIP-42 write race: on a cold connection the relay rejects the first publish with
        // `auth-required` (our AUTH reply lands async and the write path doesn't re-send). Warm
        // the connection with a pendingOnAuthRequired read so the auth coordinator completes the
        // handshake, then retry the publish on the now-authed socket. Mirrors the amy CLI fix.
        if (channelId == null && results.values.any { !it.accepted && it.message.contains("auth-required", ignoreCase = true) }) {
            client.fetchAllWithHooks(
                filters = mapOf(relay to listOf(Filter(kinds = listOf(DmOpenEvent.KIND), limit = 1))),
                timeoutMs = 8_000,
                pendingOnAuthRequired = true,
            ) { _, _ -> false }
            results = client.publishAndCollectResults(signed, setOf(relay))
            channelId = buzzDmChannelIdFromAck(results)
        }
        return channelId
    }

    /** The relay-assigned DM channel id from a DM-open OK message (`response:{"channel_id":"…"}`). */
    private fun buzzDmChannelIdFromAck(results: Map<NormalizedRelayUrl, PublishResult>): String? =
        results.values
            .firstOrNull { it.accepted }
            ?.message
            ?.substringAfter("\"channel_id\":\"", "")
            ?.substringBefore('"')
            ?.takeIf { it.isNotBlank() }

    /** Hide a Buzz DM from my sidebar with a kind-41012 command (re-opening it un-hides). */
    suspend fun hideBuzzDm(channel: RelayGroupChannel) {
        val template = DmHideEvent.build(channel.groupId.id)
        signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
    }

    /** Add [member] to an existing group DM with a kind-41011 command (creates a new DM set). */
    suspend fun addBuzzDmMember(
        channel: RelayGroupChannel,
        member: HexKey,
    ) {
        val template = DmAddMemberEvent.build(channel.groupId.id, member)
        signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
    }

    /** Send a kind 9022 leave request to the host relay and drop it from our list. */
    suspend fun leaveRelayGroup(channel: RelayGroupChannel) {
        val template = LeaveRequestEvent.build(channel.groupId.id)
        signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
        unfollow(channel)
    }

    /**
     * Create a new group on [relay]: kind 9007 (create-group) then kind 9002
     * (edit-metadata) with the chosen name/visibility, then remember it. Returns
     * the new group's id.
     */
    suspend fun createRelayGroup(
        relay: NormalizedRelayUrl,
        groupId: String,
        name: String,
        about: String? = null,
        picture: String? = null,
        isPrivate: Boolean = false,
        isClosed: Boolean = false,
        isHidden: Boolean = false,
        isRestricted: Boolean = false,
        hashtags: List<String> = emptyList(),
        geohashes: List<String> = emptyList(),
        parent: String? = null,
    ): GroupId {
        signAndSendPrivatelyOrBroadcast(CreateGroupEvent.build(groupId)) { listOf(relay) }

        val edit =
            EditMetadataEvent.build(
                groupId,
                name = name,
                about = about,
                picture = picture,
                status = relayGroupStatus(isPrivate, isClosed, isHidden, isRestricted),
                hashtags = hashtags,
                geohashes = geohashes,
                parent = parent,
            )
        signAndSendPrivatelyOrBroadcast(edit) { listOf(relay) }

        val id = GroupId(groupId, relay)
        follow(LocalCache.getOrCreateRelayGroupChannel(id))
        return id
    }

    /**
     * The set of NIP-29 status flags to emit on a kind-9002 metadata event. Flags are
     * presence-only — public/open/visible/unrestricted are simply the ABSENCE of their
     * restrictive counterpart — so only the enabled restrictive flags are added.
     */
    private fun relayGroupStatus(
        isPrivate: Boolean,
        isClosed: Boolean,
        isHidden: Boolean,
        isRestricted: Boolean,
    ): Set<GroupMetadataEvent.GroupStatus> =
        buildSet {
            if (isPrivate) add(GroupMetadataEvent.GroupStatus.PRIVATE)
            if (isClosed) add(GroupMetadataEvent.GroupStatus.CLOSED)
            if (isHidden) add(GroupMetadataEvent.GroupStatus.HIDDEN)
            if (isRestricted) add(GroupMetadataEvent.GroupStatus.RESTRICTED)
        }

    /** Post a kind 11 thread (forum-style) to the group, scoped by its `h` tag. */
    suspend fun postRelayGroupThread(
        channel: RelayGroupChannel,
        title: String,
        body: String,
    ) {
        val template =
            ThreadEvent.build(body, title) {
                hTag(channel.groupId.id)
                previous(channel.previousEventRefs(pubKey))
            }
        signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
    }

    /** Mint a kind 9009 invite code for the group (admin/moderator only). */
    suspend fun createRelayGroupInvite(
        channel: RelayGroupChannel,
        code: String,
    ) {
        val template = CreateInviteEvent.build(channel.groupId.id, code)
        signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
    }

    /**
     * Replace the group's pinned-message list with a kind 9010 update-pin-list event
     * (admin/moderator only). NIP-29 carries the FULL list, so the relay applies it and
     * republishes the kind-39005 [com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupPinnedEvent].
     */
    suspend fun updateRelayGroupPins(
        channel: RelayGroupChannel,
        pinnedEventIds: List<HexKey>,
    ) {
        val template = UpdatePinListEvent.build(channel.groupId.id, pinnedEventIds)
        signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
    }

    /** Pin [eventId] by appending it to the current list (no-op if already pinned). */
    suspend fun pinRelayGroupMessage(
        channel: RelayGroupChannel,
        eventId: HexKey,
    ) {
        if (channel.isPinned(eventId)) return
        updateRelayGroupPins(channel, channel.pinnedEventIds + eventId)
    }

    /** Unpin [eventId] by removing it from the current list (no-op if not pinned). */
    suspend fun unpinRelayGroupMessage(
        channel: RelayGroupChannel,
        eventId: HexKey,
    ) {
        if (!channel.isPinned(eventId)) return
        updateRelayGroupPins(channel, channel.pinnedEventIds - eventId)
    }

    /** Kick [pubkey] out of the group with a kind 9001 remove-user event (moderator only). */
    suspend fun removeRelayGroupUser(
        channel: RelayGroupChannel,
        pubkey: HexKey,
    ) {
        val template = RemoveUserEvent.build(channel.groupId.id, listOf(pubkey))
        signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
    }

    /**
     * Add [pubkey] to the group (or change its roles) with a kind 9000 put-user
     * event (moderator only). Pass an empty [roles] list for a plain member.
     */
    suspend fun putRelayGroupUser(
        channel: RelayGroupChannel,
        pubkey: HexKey,
        roles: List<String>,
    ) {
        val template = PutUserEvent.build(channel.groupId.id, listOf(pubkey to roles))
        signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
    }

    /**
     * Add [pubkey] to a Buzz **community** (the whole relay/tenant, not one channel) via the
     * relay-admin add-member command (kind 9030). Owner/admin only — the relay validates the
     * sender's role and, on a new insert, updates its NIP-43 membership list (13534). Published to
     * [relay] with no channel scope.
     */
    suspend fun addCommunityMember(
        relay: NormalizedRelayUrl,
        pubkey: HexKey,
        role: String? = null,
    ) {
        signAndSendPrivatelyOrBroadcast(RelayAdminAddMemberEvent.build(pubkey, role)) { listOf(relay) }
    }

    /** Remove [pubkey] from a Buzz community via the relay-admin remove-member command (kind 9031). */
    suspend fun removeCommunityMember(
        relay: NormalizedRelayUrl,
        pubkey: HexKey,
    ) {
        signAndSendPrivatelyOrBroadcast(RelayAdminRemoveMemberEvent.build(pubkey)) { listOf(relay) }
    }

    /**
     * Edit the group's relay-signed metadata with a kind 9002 event (admin only).
     *
     * NIP-29 §Subgroups makes the metadata edit a full replacement of the hierarchy
     * links: a 9002 with no `parent` tag re-roots the group, and one that drops any
     * existing `child` is rejected by the relay. So unless the caller is explicitly
     * re-parenting, we re-carry the group's current [parent] and full [children] list
     * from its latest known metadata to keep the tree intact across a plain name/flag
     * edit. Pass an explicit value to change them.
     */
    suspend fun editRelayGroupMetadata(
        channel: RelayGroupChannel,
        name: String?,
        about: String?,
        picture: String?,
        isPrivate: Boolean,
        isClosed: Boolean,
        isHidden: Boolean,
        isRestricted: Boolean,
        hashtags: List<String> = emptyList(),
        geohashes: List<String> = emptyList(),
        parent: String? = channel.parentGroupId(),
        children: List<String> = channel.childGroupIds(),
    ) {
        val template =
            EditMetadataEvent.build(
                channel.groupId.id,
                name = name,
                about = about,
                picture = picture,
                status = relayGroupStatus(isPrivate, isClosed, isHidden, isRestricted),
                hashtags = hashtags,
                geohashes = geohashes,
                parent = parent,
                children = children,
            )
        signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
    }

    suspend fun follow(community: AddressableNote) = sendMyPublicAndPrivateOutbox(communityList.follow(community))

    suspend fun unfollow(community: AddressableNote) = sendMyPublicAndPrivateOutbox(communityList.unfollow(community))

    suspend fun followHashtag(tag: String) = sendMyPublicAndPrivateOutbox(hashtagList.follow(tag))

    suspend fun unfollowHashtag(tag: String) = sendMyPublicAndPrivateOutbox(hashtagList.unfollow(tag))

    suspend fun followFavoriteAlgoFeed(dvm: AddressBookmark) = sendMyPublicAndPrivateOutbox(favoriteAlgoFeedsList.follow(dvm))

    suspend fun unfollowFavoriteAlgoFeed(dvm: Address) = sendMyPublicAndPrivateOutbox(favoriteAlgoFeedsList.unfollow(dvm))

    fun isFavoriteAlgoFeed(dvm: Address): Boolean = favoriteAlgoFeedsList.flow.value.contains(dvm)

    suspend fun followGeohash(geohash: String) = sendMyPublicAndPrivateOutbox(geohashList.follow(geohash))

    suspend fun unfollowGeohash(geohash: String) = sendMyPublicAndPrivateOutbox(geohashList.unfollow(geohash))

    suspend fun approveCommunityPost(
        post: Note,
        community: AddressableNote,
    ) {
        val commEvent = community.event as? CommunityDefinitionEvent ?: return
        val postHint = post.toEventHint<Event>() ?: return
        val communityHint = community.toEventHint<CommunityDefinitionEvent>() ?: return

        val template = CommunityPostApprovalEvent.build(postHint, communityHint)

        val signedEvent = signer.sign(template)

        val relays = outboxRelays.flow.value + commEvent.relayUrls() + community.relays + (post.author?.inboxRelays() ?: emptyList())

        cache.justConsumeMyOwnEvent(signedEvent)
        client.publish(signedEvent, relays)
    }

    fun sendAutomatic(events: List<Event>) = events.forEach { sendAutomatic(it) }

    fun sendAutomatic(event: Event?) {
        if (event == null) return
        cache.justConsumeMyOwnEvent(event)
        client.publish(event, computeRelayListToBroadcast(event))
    }

    suspend fun sendWebBookmark(
        url: String,
        title: String?,
        description: String,
        hashtags: List<String> = emptyList(),
    ) {
        if (!isWriteable()) return

        val template = WebBookmarkEvent.build(url, title, description, tags = hashtags)
        val signedEvent = signer.sign(template)

        cache.justConsumeMyOwnEvent(signedEvent)
        client.publish(signedEvent, computeRelayListToBroadcast(signedEvent))
    }

    suspend fun deleteWebBookmark(event: WebBookmarkEvent) {
        if (!isWriteable()) return

        val template = DeletionEvent.build(listOf(event))
        val signedEvent = signer.sign(template)

        cache.justConsumeMyOwnEvent(signedEvent)
        client.publish(signedEvent, computeRelayListToBroadcast(signedEvent))
    }

    suspend fun sendBadgeDefinition(
        badgeId: String,
        name: String?,
        imageUrl: String?,
        imageDim: DimensionTag?,
        description: String?,
        thumbs: List<ThumbTag> = emptyList(),
    ) {
        if (!isWriteable()) return

        val template =
            BadgeDefinitionEvent.build(
                badgeId = badgeId,
                name = name,
                imageUrl = imageUrl,
                imageDimensions = imageDim,
                description = description,
                thumbs = thumbs,
            )
        val signedEvent = signer.sign(template)

        cache.justConsumeMyOwnEvent(signedEvent)
        client.publish(signedEvent, outboxRelays.flow.value)
    }

    suspend fun deleteBadgeDefinition(event: BadgeDefinitionEvent) {
        if (!isWriteable()) return
        if (event.pubKey != signer.pubKey) return

        val template = DeletionEvent.build(listOf(event))
        val signedEvent = signer.sign(template)

        cache.justConsumeMyOwnEvent(signedEvent)
        client.publish(signedEvent, computeRelayListToBroadcast(signedEvent))
    }

    suspend fun sendBadgeAward(
        definition: BadgeDefinitionEvent,
        awardees: List<PTag>,
    ) {
        if (!isWriteable()) return
        if (awardees.isEmpty()) return

        val aTag = ATag(definition.kind, definition.pubKey, definition.dTag(), null)
        val template = BadgeAwardEvent.build(aTag, awardees)
        val signedEvent = signer.sign(template)

        val relays =
            outboxRelays.flow.value +
                awardees
                    .flatMap { cache.getOrCreateUser(it.pubKey).inboxRelays() ?: emptyList() }
                    .toSet()

        cache.justConsumeMyOwnEvent(signedEvent)
        client.publish(signedEvent, relays)
    }

    suspend fun sendCommunityDefinition(
        name: String,
        description: String,
        moderators: List<ModeratorTag>,
        image: String? = null,
        rules: String? = null,
        relays: List<RelayTag>? = null,
        dTag: String,
    ): CommunityDefinitionEvent? {
        if (!isWriteable()) return null

        val template =
            CommunityDefinitionEvent.build(
                name = name,
                description = description,
                moderators = moderators,
                image = image,
                rules = rules,
                relays = relays,
                dTag = dTag,
            )
        val signedEvent = signer.sign(template)

        cache.justConsumeMyOwnEvent(signedEvent)
        client.publish(signedEvent, computeRelayListToBroadcast(signedEvent))
        return signedEvent
    }

    /**
     * Publishes a sibling NIP-9B `kind:34551` rules document for a community we just
     * (or previously) defined with [sendCommunityDefinition]. The event is signed by
     * the community owner and addresses the definition through its `a` tag, sharing
     * the same `dTag` so it replaces in place when re-edited.
     */
    suspend fun sendCommunityRules(
        communityDTag: String,
        kindRules: List<KindRuleTag>,
        pubkeyRules: List<PubkeyRuleTag> = emptyList(),
        wotGates: List<WotTag> = emptyList(),
        maxEventSize: Int? = null,
        minRulesCreatedAt: Long? = null,
    ): CommunityRulesEvent? {
        if (!isWriteable()) return null

        val communityAddress = ATag(CommunityDefinitionEvent.KIND, signer.pubKey, communityDTag, null)

        val template =
            CommunityRulesEvent.build(
                dTag = communityDTag,
                communityAddress = communityAddress,
                kindRules = kindRules,
                pubkeyRules = pubkeyRules,
                wotGates = wotGates,
                maxEventSize = maxEventSize,
                minRulesCreatedAt = minRulesCreatedAt,
            )
        val signedEvent = signer.sign(template)

        cache.justConsumeMyOwnEvent(signedEvent)
        client.publish(signedEvent, computeRelayListToBroadcast(signedEvent))
        return signedEvent
    }

    private fun loadCurrentAcceptedBadges(): List<AcceptedBadge> {
        val newNote = cache.getAddressableNoteIfExists(ProfileBadgesEvent.createAddress(signer.pubKey))
        val newEvent = newNote?.event as? ProfileBadgesEvent
        if (newEvent != null) return newEvent.acceptedBadges()

        val oldNote = cache.getAddressableNoteIfExists(AcceptedBadgeSetEvent.createAddress(signer.pubKey))
        val oldEvent = oldNote?.event as? AcceptedBadgeSetEvent
        return oldEvent?.acceptedBadges() ?: emptyList()
    }

    /**
     * Serializes read-modify-write of the accepted-badges replaceable event so two
     * rapid toggles can't race each other into losing updates.
     */
    private val profileBadgesMutex = Mutex()

    /**
     * Returns a createdAt strictly greater than whatever ProfileBadgesEvent (or
     * the legacy AcceptedBadgeSetEvent) currently sits in cache. Needed because
     * LocalCache.consumeBaseReplaceable drops updates whose createdAt isn't
     * strictly greater, and TimeUtils.now() has only second resolution.
     */
    private fun nextProfileBadgesCreatedAt(): Long {
        val latest =
            maxOf(
                (cache.getAddressableNoteIfExists(ProfileBadgesEvent.createAddress(signer.pubKey))?.event?.createdAt) ?: 0L,
                (cache.getAddressableNoteIfExists(AcceptedBadgeSetEvent.createAddress(signer.pubKey))?.event?.createdAt) ?: 0L,
            )
        return maxOf(TimeUtils.now(), latest + 1)
    }

    suspend fun addAcceptedBadge(
        award: BadgeAwardEvent,
        definition: BadgeDefinitionEvent,
    ) {
        if (!isWriteable()) return

        val aTag = ATag(definition.kind, definition.pubKey, definition.dTag(), null)
        val eTag = ETag(award.id)

        val signedEvent =
            profileBadgesMutex.withLock {
                val current = loadCurrentAcceptedBadges()
                if (current.any { it.badgeAward.eventId == award.id }) return
                val updated = current + AcceptedBadge(aTag, eTag)

                val template = ProfileBadgesEvent.build(updated, createdAt = nextProfileBadgesCreatedAt())
                val signed = signer.sign(template)
                cache.justConsumeMyOwnEvent(signed)
                signed
            }

        client.publish(signedEvent, outboxRelays.flow.value)
    }

    suspend fun removeAcceptedBadge(award: BadgeAwardEvent) {
        if (!isWriteable()) return

        val signedEvent =
            profileBadgesMutex.withLock {
                val current = loadCurrentAcceptedBadges()
                val updated = current.filterNot { it.badgeAward.eventId == award.id }
                if (updated.size == current.size) return

                val template = ProfileBadgesEvent.build(updated, createdAt = nextProfileBadgesCreatedAt())
                val signed = signer.sign(template)
                cache.justConsumeMyOwnEvent(signed)
                signed
            }

        client.publish(signedEvent, outboxRelays.flow.value)
    }

    fun sendMyPublicAndPrivateOutbox(event: Event?) {
        if (event == null) return
        cache.justConsumeMyOwnEvent(event)
        client.publish(event, outboxRelays.flow.value)
    }

    fun sendMyPublicAndPrivateOutbox(events: List<Event>) {
        events.forEach {
            client.publish(it, outboxRelays.flow.value)
            cache.justConsumeMyOwnEvent(it)
        }
    }

    fun sendLiterallyEverywhere(event: Event) {
        client.publish(event, followPlusAllMineWithIndex.flow.value + client.availableRelaysFlow().value)
        cache.justConsumeMyOwnEvent(event)
    }

    suspend fun pollRespond(
        event: PollEvent,
        responses: Set<String>,
    ) {
        val poll = cache.getOrCreateNote(event.id).toEventHint<PollEvent>()

        if (poll != null) {
            val template = PollResponseEvent.build(poll, responses)

            val signedEvent = signer.sign(template)

            cache.justConsumeMyOwnEvent(signedEvent)

            client.publish(signedEvent, computeRelayListToBroadcast(signedEvent))
        }
    }

    suspend fun createNip95(
        byteArray: ByteArray,
        headerInfo: FileHeader,
        alt: String?,
        contentWarningReason: String?,
    ): Pair<FileStorageEvent, FileStorageHeaderEvent> {
        val data = signer.sign(FileStorageEvent.build(byteArray, headerInfo.mimeType))

        val template =
            FileStorageHeaderEvent.build(EventHintBundle(data, userProfile().bestRelayHint()), alt) {
                hash(headerInfo.hash)
                fileSize(headerInfo.size)

                headerInfo.mimeType?.let { mimeType(it) }
                headerInfo.dim?.let { dimension(it) }
                headerInfo.blurHash?.let { blurhash(it.blurhash) }
                headerInfo.thumbHash?.let { nip95thumbhash(it.thumbhash) }

                contentWarningReason?.let { contentWarning(contentWarningReason) }
            }

        val signedEvent = signer.sign(template)
        return Pair(data, signedEvent)
    }

    fun consumeAndSendNip95(
        data: FileStorageEvent,
        signedEvent: FileStorageHeaderEvent,
    ): Note? {
        if (!isWriteable()) return null

        val relayList = computeRelayListToBroadcast(signedEvent)

        client.publish(data, relayList = relayList)
        cache.justConsumeMyOwnEvent(data)

        client.publish(signedEvent, relayList = relayList)
        cache.justConsumeMyOwnEvent(signedEvent)

        return cache.getNoteIfExists(signedEvent.id)
    }

    fun consumeNip95(
        data: FileStorageEvent,
        signedEvent: FileStorageHeaderEvent,
    ): Note? {
        cache.justConsumeMyOwnEvent(data)
        cache.justConsumeMyOwnEvent(signedEvent)

        return cache.getNoteIfExists(signedEvent.id)
    }

    fun sendNip95(
        data: FileStorageEvent,
        signedEvent: FileStorageHeaderEvent,
        relayList: Set<NormalizedRelayUrl>,
    ) {
        client.publish(data, relayList = relayList)
        client.publish(signedEvent, relayList = relayList)
    }

    fun sendHeader(
        signedEvent: Event,
        relayList: Set<NormalizedRelayUrl>,
        onReady: (Note) -> Unit,
    ) {
        client.publish(signedEvent, relayList = relayList)
        cache.justConsumeMyOwnEvent(signedEvent)

        cache.getNoteIfExists(signedEvent.id)?.let { onReady(it) }
    }

    suspend fun sendVoiceMessage(
        url: String,
        mimeType: String?,
        hash: String,
        duration: Int,
        waveform: List<Float>,
    ) {
        signAndComputeBroadcast(VoiceEvent.build(url, mimeType, hash, duration, waveform))
    }

    suspend fun sendVoiceReplyMessage(
        url: String,
        mimeType: String?,
        hash: String,
        duration: Int,
        waveform: List<Float>,
        replyTo: EventHintBundle<BaseVoiceEvent>,
    ) {
        signAndComputeBroadcast(VoiceReplyEvent.build(url, mimeType, hash, duration, waveform, replyTo))
    }

    suspend fun sendAllAsOnePictureEvent(
        urlHeaderInfo: Map<String, FileHeader>,
        caption: String?,
        contentWarningReason: String?,
    ) {
        val iMetas =
            urlHeaderInfo.map {
                PictureMeta(
                    url = it.key,
                    mimeType = it.value.mimeType,
                    blurhash = it.value.blurHash?.blurhash,
                    dimension = it.value.dim,
                    alt = caption,
                    hash = it.value.hash,
                    size = it.value.size,
                    service = null,
                    fallback = emptyList(),
                    annotations = emptyList(),
                    thumbhash = it.value.thumbHash?.thumbhash,
                )
            }

        val template =
            PictureEvent.build(iMetas, caption ?: "") {
                caption?.let {
                    hashtags(findHashtags(it))
                    references(findURLs(it))
                    quotes(findNostrUris(it))
                }
                // add zap splits
                // add zap raiser
                // add geohashes
                // add title
                contentWarningReason?.let { contentWarning(contentWarningReason) }
            }

        signAndComputeBroadcast(template)
    }

    suspend fun sendHeader(
        url: String,
        magnetUri: String?,
        headerInfo: FileHeader,
        alt: String?,
        contentWarningReason: String?,
        originalHash: String? = null,
    ) {
        if (!isWriteable()) return

        val isImage = headerInfo.mimeType?.startsWith("image/") == true || RichTextParser.isImageUrl(url)
        val isVideo = headerInfo.mimeType?.startsWith("video/") == true || RichTextParser.isVideoUrl(url)

        val template =
            if (isImage) {
                PictureEvent.build(alt ?: "") {
                    alt?.let {
                        hashtags(findHashtags(it))
                        references(findURLs(it))
                        quotes(findNostrUris(it))
                    }
                    pictureIMeta(
                        url = url,
                        mimeType = headerInfo.mimeType,
                        blurhash = headerInfo.blurHash?.blurhash,
                        dimension = headerInfo.dim,
                        hash = headerInfo.hash,
                        size = headerInfo.size,
                        alt = alt,
                        thumbhash = headerInfo.thumbHash?.thumbhash,
                    )
                    // add zap splits
                    // add zap raiser
                    // add geohashes
                    // add title
                    contentWarningReason?.let { contentWarning(contentWarningReason) }
                }
            } else if (isVideo && headerInfo.dim != null) {
                val videoMeta =
                    VideoMeta(
                        url = url,
                        hash = headerInfo.hash,
                        size = headerInfo.size,
                        mimeType = headerInfo.mimeType,
                        dimension = headerInfo.dim,
                        blurhash = headerInfo.blurHash?.blurhash,
                        alt = alt,
                        thumbhash = headerInfo.thumbHash?.thumbhash,
                    )

                if (headerInfo.dim.height > headerInfo.dim.width) {
                    VideoShortEvent.build(videoMeta, alt ?: "") {
                        contentWarningReason?.let { contentWarning(contentWarningReason) }
                    }
                } else {
                    VideoNormalEvent.build(videoMeta, alt ?: "") {
                        contentWarningReason?.let { contentWarning(contentWarningReason) }
                    }
                }
            } else {
                FileHeaderEvent.build(url, alt) {
                    hash(headerInfo.hash)
                    fileSize(headerInfo.size)

                    headerInfo.mimeType?.let { mimeType(it) }
                    headerInfo.dim?.let { dimension(it) }
                    headerInfo.blurHash?.let { blurhash(it.blurhash) }
                    headerInfo.thumbHash?.let { thumbhash(it.thumbhash) }

                    originalHash?.let { originalHash(it) }
                    magnetUri?.let { magnet(it) }

                    contentWarningReason?.let { contentWarning(contentWarningReason) }
                }
            }

        signAndComputeBroadcast(template)
    }

    suspend fun <T : Event> signAndSendPrivately(
        template: EventTemplate<T>,
        relayList: Set<NormalizedRelayUrl>,
    ) {
        val event = signer.sign(template)
        cache.justConsumeMyOwnEvent(event)
        client.publish(event, relayList)
    }

    /**
     * Sign [template] with an arbitrary [signer] (e.g. a per-geohash ephemeral
     * identity that is deliberately NOT this account's key) and publish to exactly
     * [relayList]. Used by geohash location chat, where authorship inside a cell
     * must not be linkable to the user's npub.
     */
    suspend fun <T : Event> signWithAndSendPrivately(
        template: EventTemplate<T>,
        signer: NostrSigner,
        relayList: Set<NormalizedRelayUrl>,
    ): T {
        val event = signer.sign(template)
        cache.justConsumeMyOwnEvent(event)
        if (relayList.isNotEmpty()) client.publish(event, relayList)
        return event
    }

    suspend fun <T : Event> signAndSendPrivatelyOrBroadcast(
        template: EventTemplate<T>,
        relayList: (T) -> List<NormalizedRelayUrl>?,
    ): T {
        val event = signer.sign(template)
        cache.justConsumeMyOwnEvent(event)
        val relays = relayList(event)
        val targets =
            if (!relays.isNullOrEmpty()) {
                relays.toSet()
            } else {
                computeRelayListToBroadcast(event)
            }
        chatDeliveryTracker.trackPublic(event.id, targets)
        client.publish(event, targets)
        return event
    }

    suspend fun <T : Event> signAndComputeBroadcast(
        template: EventTemplate<T>,
        broadcast: List<Event> = emptyList(),
    ): T {
        val event = signer.sign(template)
        cache.justConsumeMyOwnEvent(event)
        val note =
            if (event is AddressableEvent) {
                cache.getOrCreateAddressableNote(event.address())
            } else {
                cache.getOrCreateNote(event.id)
            }

        val relayList = computeRelayListToBroadcast(note)

        client.publish(event, relayList)

        broadcast.forEach { client.publish(it, relayList) }

        return event
    }

    suspend fun <T : Event> signAnonymouslyAndBroadcast(
        template: EventTemplate<T>,
        broadcast: List<Event> = emptyList(),
        anonymousSigner: NostrSigner = NostrSignerInternal(KeyPair()),
    ): T {
        val event = anonymousSigner.sign(template)

        cache.justConsumeMyOwnEvent(event)
        val note =
            if (event is AddressableEvent) {
                cache.getOrCreateAddressableNote(event.address())
            } else {
                cache.getOrCreateNote(event.id)
            }

        val relayList = computeRelayListToBroadcast(note)

        client.publish(event, relayList)

        broadcast.forEach { client.publish(it, relayList) }

        return event
    }

    /**
     * Creates a post event without sending it.
     * Returns the event, target relays, and extra events to broadcast.
     * For use with tracked broadcasting.
     */
    suspend fun <T : Event> createPostEvent(
        template: EventTemplate<T>,
        extraNotesToBroadcast: List<Event> = emptyList(),
    ): Triple<T, Set<NormalizedRelayUrl>, List<Event>> {
        val event = signer.sign(template)

        // Use event-based relay computation (not note-based, since note is empty)
        val relayList = computeRelayListToBroadcast(event)

        return Triple(event, relayList, extraNotesToBroadcast)
    }

    /**
     * Consumes a post event into local cache and sends extra events.
     * Called when tracked broadcasting succeeds.
     */
    fun consumePostEvent(
        event: Event,
        relays: Set<NormalizedRelayUrl>,
        extraNotesToBroadcast: List<Event>,
    ) {
        cache.justConsumeMyOwnEvent(event)
        extraNotesToBroadcast.forEach { client.publish(it, relays) }
    }

    /**
     * The live [AddressableNote] backing a draft tag for this account. It is the same cached
     * note that draft events are consumed into, so its `event` tracks the draft over time. The
     * composer holds onto it (via DraftTagState) so [LocalCache]'s weak reference can't collect
     * it before a deletion needs it, which would otherwise orphan the draft on the relays.
     */
    fun getOrCreateDraftNote(draftTag: String): AddressableNote = cache.getOrCreateAddressableNote(DraftWrapEvent.createAddress(signer.pubKey, draftTag))

    suspend fun createAndSendDraftIgnoreErrors(
        draftTag: String,
        template: EventTemplate<out Event>,
        broadcast: Set<Event> = emptySet(),
    ) {
        try {
            createAndSendDraftInner(draftTag, template, broadcast)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    suspend fun createAndSendDraftInner(
        draftTag: String,
        template: EventTemplate<out Event>,
        broadcast: Set<Event> = emptySet(),
    ) {
        if (!isWriteable()) return

        val extraRelays = cache.getAddressableNoteIfExists(DraftWrapEvent.createAddressTag(signer.pubKey, draftTag))?.relays ?: emptyList()

        val rumor = RumorAssembler.assembleRumor(signer.pubKey, template)
        val draftEvent = DraftWrapEvent.create(draftTag, rumor, signer)
        draftsDecryptionCache.preload(draftEvent, rumor)

        cache.justConsumeMyOwnEvent(draftEvent)

        val relayList = (privateStorageRelayList.flow.value + localRelayList.flow.value + extraRelays).toSet()
        if (relayList.isNotEmpty()) {
            client.publish(draftEvent, relayList)
            broadcast.forEach {
                client.publish(it, relayList.toSet())
            }
        }
    }

    suspend fun deleteDraftIgnoreErrors(draftNote: AddressableNote?) {
        try {
            deleteDraftInner(draftNote)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    suspend fun deleteDraftInner(draftNote: AddressableNote?) {
        if (!isWriteable()) return

        // Only a real, still-present draft needs a deletion signed. The note's event is null when
        // no draft was ever saved (e.g. auto-drafts disabled) and already empty once it has been
        // deleted — in both cases there is nothing to delete, so we avoid prompting the signer.
        val draftEvent = draftNote?.event as? DraftWrapEvent
        if (draftEvent == null || draftEvent.isDeleted()) return

        val draftTag = draftNote.dTag()
        val extraRelays = draftNote.relays

        val deletedDraft = DraftWrapEvent.createDeletedEvent(draftTag, signer)
        val deletionEvent = signer.sign(DeletionEvent.build(listOf(deletedDraft)))

        val relayList = (privateStorageRelayList.flow.value + localRelayList.flow.value + extraRelays).toSet()

        cache.justConsumeMyOwnEvent(deletedDraft)
        cache.justConsumeMyOwnEvent(deletionEvent)

        if (relayList.isNotEmpty()) {
            client.publish(deletedDraft, relayList)
            client.publish(deletionEvent, relayList)
        }
    }

    suspend fun createInteractiveStoryReadingState(
        root: EventHintBundle<InteractiveStoryBaseEvent>,
        readingScene: EventHintBundle<InteractiveStoryBaseEvent>,
    ) {
        if (!isWriteable()) return

        val template =
            InteractiveStoryReadingStateEvent.build(
                root = root,
                currentScene = readingScene,
            )

        val event = signer.sign(template)

        // updates relays that already have this replaceable.
        val noteRelays = cache.getAddressableNoteIfExists(event.address())?.relays ?: emptyList()

        val relayList = privateStorageRelayList.flow.value + localRelayList.flow.value
        if (relayList.isNotEmpty()) {
            client.publish(event, relayList + noteRelays)
        } else {
            client.publish(event, outboxRelays.flow.value + noteRelays)
        }
        cache.justConsumeMyOwnEvent(event)
    }

    suspend fun updateInteractiveStoryReadingState(
        readingState: InteractiveStoryReadingStateEvent,
        readingScene: EventHintBundle<InteractiveStoryBaseEvent>,
    ) {
        if (!isWriteable()) return

        val template =
            InteractiveStoryReadingStateEvent.update(
                base = readingState,
                currentScene = readingScene,
            )

        val event = signer.sign(template)

        // updates relays that already have this replaceable.
        val noteRelays = cache.getAddressableNoteIfExists(event.address())?.relays ?: emptyList()

        val relayList = privateStorageRelayList.flow.value + localRelayList.flow.value
        if (relayList.isNotEmpty()) {
            client.publish(event, relayList + noteRelays)
        } else {
            client.publish(event, outboxRelays.flow.value + noteRelays)
        }
        cache.justConsumeMyOwnEvent(event)
    }

    fun mapEntitiesToNotes(entities: List<Entity>): List<Note> =
        entities.mapNotNull {
            when (it) {
                is NPub -> null
                is NProfile -> null
                is NNote -> cache.getOrCreateNote(it.hex)
                is NEvent -> cache.getOrCreateNote(it.hex)
                is NEmbed -> cache.getOrCreateNote(it.event.id)
                is NAddress -> cache.checkGetOrCreateAddressableNote(it.aTag())
                is NSec -> null
                is NRelay -> null
                else -> null
            }
        }

    suspend fun sendInteractiveStoryPrologue(
        baseId: String,
        title: String,
        content: String,
        options: List<StoryOptionTag>,
        summary: String? = null,
        image: String? = null,
        zapReceiver: List<ZapSplitSetup>? = null,
        contentWarningReason: String? = null,
        zapRaiserAmount: Long? = null,
        imetas: List<IMetaTag>? = null,
        draftTag: String? = null,
        relayList: Set<NormalizedRelayUrl>,
    ) {
        if (!isWriteable()) return

        val quotes = findNostrUris(content)

        val template =
            InteractiveStoryPrologueEvent.build(
                baseId = baseId,
                title = title,
                content = content,
                options = options,
            ) {
                summary?.let { summary(it) }
                image?.let { image(it) }
                hashtags(findHashtags(content))
                references(findURLs(content))
                quotes(quotes)
                zapRaiserAmount?.let { zapraiser(it) }
                zapReceiver?.let { zapSplits(it) }
                imetas?.let { imetas(it) }
                contentWarningReason?.let { contentWarning(contentWarningReason) }
            }

        if (draftTag != null) {
            createAndSendDraftIgnoreErrors(draftTag, template)
        } else {
            val it = signer.sign(template)
            cache.justConsumeMyOwnEvent(it)
            client.publish(it, relayList = relayList)

            mapEntitiesToNotes(quotes).forEach { it.event?.let { client.publish(it, relayList = relayList) } }
        }
    }

    suspend fun sendInteractiveStoryScene(
        baseId: String,
        title: String,
        content: String,
        options: List<StoryOptionTag>,
        zapReceiver: List<ZapSplitSetup>? = null,
        contentWarningReason: String? = null,
        zapRaiserAmount: Long? = null,
        imetas: List<IMetaTag>? = null,
        draftTag: String? = null,
        relayList: Set<NormalizedRelayUrl>,
    ) {
        if (!isWriteable()) return

        val quotes = findNostrUris(content)

        val template =
            InteractiveStorySceneEvent.build(
                baseId = baseId,
                title = title,
                content = content,
                options = options,
            ) {
                hashtags(findHashtags(content))
                references(findURLs(content))
                quotes(quotes)
                zapRaiserAmount?.let { zapraiser(it) }
                zapReceiver?.let { zapSplits(it) }
                imetas?.let { imetas(it) }
                contentWarningReason?.let { contentWarning(contentWarningReason) }
            }

        val broadcastNotes = mapEntitiesToNotes(quotes).toSet()

        if (draftTag != null) {
            createAndSendDraftIgnoreErrors(draftTag, template)
        } else {
            val it = signer.sign(template)
            cache.justConsumeMyOwnEvent(it)
            client.publish(it, relayList = relayList)

            broadcastNotes.forEach { it.event?.let { client.publish(it, relayList = relayList) } }
        }
    }

    suspend fun sendAddBounty(
        value: BigDecimal,
        bounty: Note,
    ) {
        if (!isWriteable()) return

        val bountyEvent = bounty.event as? TextNoteEvent ?: return
        val bountyAuthor = bounty.author ?: return

        val template =
            BountyAddValueEvent.build(
                amount = value,
                bountyRoot = EventHintBundle(bountyEvent, bounty.relayHintUrl()),
                bountyRootAuthor = bountyAuthor.toPTag(),
            )

        val relays = (bounty.relays + outboxRelays.flow.value).toSet()

        val newEvent = signer.sign(template)
        cache.justConsumeMyOwnEvent(newEvent)

        client.publish(newEvent, relayList = relays)
        client.publish(bountyEvent, relayList = relays)
    }

    suspend fun sendEdit(
        message: String,
        originalNote: Note,
        notify: HexKey?,
        summary: String? = null,
        broadcast: List<Event>,
    ) {
        if (!isWriteable()) return

        val idHex = originalNote.event?.id ?: return

        val event =
            TextNoteModificationEvent.create(
                content = message,
                eventId = idHex,
                notify = notify,
                summary = summary,
                signer = signer,
            )

        cache.justConsumeMyOwnEvent(event)
        val note = cache.getOrCreateNote(event.id)
        val relayList = computeRelayListToBroadcast(note)

        client.publish(event, relayList = relayList)

        broadcast.forEach { client.publish(it, relayList) }
    }

    override suspend fun sendNip04PrivateMessage(eventTemplate: EventTemplate<PrivateDmEvent>) {
        if (!isWriteable()) return

        val newEvent = signer.sign(eventTemplate)
        val recipient = newEvent.verifiedRecipientPubKey()
        val destinationRelays = recipient?.let { cache.getOrCreateUser(it).dmInboxRelays() } ?: emptyList()

        cache.justConsumeMyOwnEvent(newEvent)
        client.publish(newEvent, outboxRelays.flow.value + destinationRelays)

        // Index into the chatroom immediately (same rationale as
        // broadcastPrivately) instead of waiting for the newEventBundles
        // batcher; the later batched re-delivery is deduped by the chatroom.
        cache.getNoteIfExists(newEvent.id)?.let { newNotesPreProcessor.consume(it) }

        markDmRoomAsRead(newEvent)
    }

    override suspend fun sendNip17EncryptedFile(template: EventTemplate<ChatMessageEncryptedFileHeaderEvent>) {
        if (!isWriteable()) return

        val powDifficulty = powDifficultyFor(GiftWrapEvent.KIND)
        if (powDifficulty != null) {
            // Sign the inner event and every seal NOW, in the caller's
            // interaction context — an external signer (Amber/bunker) cannot
            // prompt from a background mining worker. Only the local-CPU
            // ephemeral-key wrap mining goes to the queue, checkpointed so a
            // process death mid-mine cannot lose the file announcement.
            val senderMessage = signer.sign(template)
            val seals = NIP17Factory().createSeals(senderMessage, senderMessage.groupMembers(), signer)
            if (mineWrapsInBackground(seals.seals, seals.expirationDelta, powDifficulty, displayedNoteId = senderMessage.id)) {
                // The wraps publish only after mining, but the user has already
                // replied — advance the read marker now.
                markDmRoomAsRead(senderMessage)
                return
            }
        }

        broadcastPrivately(NIP17Factory().createEncryptedFileNIP17(template, signer))
    }

    override suspend fun sendNip17PrivateMessage(template: EventTemplate<ChatMessageEvent>) {
        val powDifficulty = powDifficultyFor(GiftWrapEvent.KIND)
        if (powDifficulty != null) {
            // See sendNip17EncryptedFile: sign inline, queue only wrap mining.
            val senderMessage = signer.sign(template)
            val seals = NIP17Factory().createSeals(senderMessage, senderMessage.groupMembers(), signer)
            if (mineWrapsInBackground(seals.seals, seals.expirationDelta, powDifficulty, displayedNoteId = senderMessage.id)) {
                // The wraps publish only after mining, but the user has already
                // replied — advance the read marker now.
                markDmRoomAsRead(senderMessage)
                return
            }
        }

        broadcastPrivately(NIP17Factory().createMessageNIP17(template, signer))
    }

    /**
     * Publishes a kind-1 note privately: signs the template, then gift-wraps
     * the rumor to every p-tagged user plus a self-copy and sends each wrap
     * to the recipient's DM relays. Used for private replies (the parent's
     * author and participants are already p-tagged) and for private posts
     * (the Notify list is the audience). Nothing reaches public relays.
     *
     * [powOverrideDifficulty] is the composer chip's per-post override:
     * null follows the account's gift-wrap setting, 0 disables mining.
     */
    suspend fun sendPrivateNote(
        template: EventTemplate<TextNoteEvent>,
        powOverrideDifficulty: Int? = null,
    ) {
        if (!isWriteable()) return

        val powDifficulty = powDifficultyFor(GiftWrapEvent.KIND, powOverrideDifficulty)
        if (powDifficulty != null) {
            // See sendNip17EncryptedFile: sign inline, queue only wrap mining.
            val senderNote = signer.sign(template)
            val recipients = senderNote.taggedUserIds().plus(signer.pubKey).toSet()
            val seals = NIP17Factory().createSeals(senderNote, recipients, signer)
            if (mineWrapsInBackground(seals.seals, seals.expirationDelta, powDifficulty, displayedNoteId = senderNote.id)) return
        }

        broadcastPrivately(NIP17Factory().createNoteNIP17(template, signer))
    }

    override suspend fun sendGiftWraps(wraps: List<GiftWrapEvent>) {
        wraps.forEach { wrap ->
            val relayList = computeRelayListToBroadcast(wrap)
            client.publish(wrap, relayList)
        }
    }

    suspend fun broadcastPrivately(signedEvents: NIP17Factory.Result) {
        broadcastPrivately(signedEvents.wraps, signedEvents.msg.id)
        markDmRoomAsRead(signedEvents.msg)
    }

    /**
     * [displayedNoteId] is the inner rumor's id (the note the chat feed shows).
     * When present, each wrap registers with the delivery-ticks tracker — this is
     * the only place the recipient -> wrap -> target-relays mapping exists, before
     * the wraps are aliased onto a single note.
     */
    suspend fun broadcastPrivately(
        wraps: List<GiftWrapEvent>,
        displayedNoteId: HexKey? = null,
    ) {
        val mine = wraps.filter { (it.recipientPubKey() == signer.pubKey) }

        mine.forEach { giftWrap ->
            cache.justConsumeMyOwnEvent(giftWrap)
        }

        val id = mine.firstOrNull()?.id
        val mineNote = if (id == null) null else cache.getNoteIfExists(id)

        wraps.forEach { wrap ->
            // Creates an alias
            if (mineNote != null && wrap.recipientPubKey() != signer.pubKey) {
                cache.getOrAddAliasNote(wrap.id, mineNote)
            }

            val relayList = computeRelayListToBroadcast(wrap)

            if (displayedNoteId != null) {
                wrap.recipientPubKey()?.let { recipient ->
                    chatDeliveryTracker.trackWrap(
                        displayedNoteId = displayedNoteId,
                        recipient = recipient,
                        wrapId = wrap.id,
                        targetRelays = relayList,
                        isSelf = recipient == signer.pubKey,
                    )
                }
            }

            client.publish(wrap, relayList)
        }

        // Unwrap and index the self-copy right away instead of waiting for the
        // newEventBundles batcher (up to ~1s): the sent message reaches the
        // chatroom before the first relay OK, so acceptances land directly on
        // the rumor note the chat renders instead of parking on the wrap. The
        // batcher re-delivers this note later; the processor's replay path and
        // the chatroom add are both idempotent.
        mineNote?.let { newNotesPreProcessor.consume(it) }
    }

    /**
     * Sending a message into a DM room means the user has caught up with what the room
     * showed when they replied: advance the local read marker to the newest known message —
     * not just the sent one, whose local clock may lag behind a skew-ahead peer's — so the
     * unread indicators clear without requiring the conversation to be reopened
     * (#1286, #1287). No-op for private events that don't belong to a room (private notes,
     * reactions, deletions).
     */
    private fun markDmRoomAsRead(event: Event) {
        if (event is ChatroomKeyable) {
            val room = event.chatroomKey(signer.pubKey)
            val newestInRoom =
                chatroomList.rooms
                    .get(room)
                    ?.newestMessage
                    ?.createdAt() ?: 0L
            markAsRead(privateChatLastReadRoute(room), maxOf(event.createdAt, newestInRoom))
        }
    }

    // --- Marmot Group Messaging ---

    /**
     * Resolve the relay set for a Marmot group. Prefer the relays carried in
     * the MLS GroupContext metadata so every member converges on the same
     * canonical set; fall back to the account's outbox relays if the group
     * has none (e.g. a group joined before MIP-01 metadata existed).
     *
     * Lives on Account (not AccountViewModel) so that headless callers —
     * notifications' BroadcastReceiver, background workers — can resolve
     * relays without spinning up a ViewModel.
     */
    fun marmotGroupRelays(nostrGroupId: HexKey): Set<NormalizedRelayUrl> {
        val groupRelays =
            marmotManager
                ?.groupMetadata(nostrGroupId)
                ?.relays
                ?.mapNotNull {
                    com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
                        .normalizeOrNull(it)
                }?.toSet()
        return if (!groupRelays.isNullOrEmpty()) groupRelays else outboxRelays.flow.value
    }

    /**
     * Send a message to a Marmot MLS group.
     * Encrypts the inner event and publishes the GroupEvent to group relays.
     */
    suspend fun sendMarmotGroupMessage(
        nostrGroupId: HexKey,
        innerEvent: Event,
        groupRelays: Set<NormalizedRelayUrl>,
    ) {
        Log.d("MarmotDbg") {
            "sendMarmotGroupMessage: group=${nostrGroupId.take(8)}… innerKind=${innerEvent.kind} innerId=${innerEvent.id.take(8)}… " +
                "→ ${groupRelays.size} relay(s): ${groupRelays.map { it.url }}"
        }
        val manager = marmotManager ?: return
        if (!isWriteable()) return

        val outbound = manager.buildGroupMessage(nostrGroupId, innerEvent)
        Log.d("MarmotDbg") {
            "sendMarmotGroupMessage: built outer kind:${outbound.signedEvent.kind} id=${outbound.signedEvent.id.take(8)}…"
        }
        // Link the envelope to the inner message we just encrypted so relay
        // OK acceptances drill down to the note the chat renders (see
        // LocalCache.addRelayToNoteAndInners).
        outbound.signedEvent.innerEventId = innerEvent.id
        cache.justConsumeMyOwnEvent(outbound.signedEvent)
        // Sending a message moves the group out of "New Requests" into
        // "Known" — do this eagerly before relay round-trip so the UI
        // updates immediately.
        marmotGroupList.markAsKnown(nostrGroupId)
        if (groupRelays.isEmpty()) {
            Log.w("MarmotDbg") {
                "sendMarmotGroupMessage: NO group relays for group=${nostrGroupId.take(8)}… — message will be silently dropped"
            }
        }
        client.publish(outbound.signedEvent, groupRelays)
    }

    /**
     * Fetch a user's KeyPackage from relays and add them to a Marmot group.
     * Returns a status message describing the outcome.
     */
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    suspend fun fetchKeyPackageAndAddMember(
        nostrGroupId: HexKey,
        memberPubKey: HexKey,
    ): String {
        Log.d("MarmotDbg") {
            "fetchKeyPackageAndAddMember: group=${nostrGroupId.take(8)}… member=${memberPubKey.take(8)}…"
        }
        val manager = marmotManager ?: return "Error: Marmot not initialized"
        if (!isWriteable()) return "Error: Account is read-only"

        // Per MIP-00, invitees advertise the relays that host their
        // KeyPackages in a kind:10051 KeyPackageRelayListEvent. Look
        // there first, then fall back to the invitee's NIP-65 outbox
        // (where KeyPackages typically also land), and finally union
        // with our own outbox so we still find packages that ended up
        // on a shared relay.
        val myOutbox = outboxRelays.flow.value
        val memberKeyPackageRelays =
            (
                cache
                    .getAddressableNoteIfExists(
                        com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
                            .createAddress(memberPubKey),
                    )?.event as? com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
            )?.relays()?.toSet().orEmpty()
        val memberOutbox =
            cache
                .getOrCreateUser(memberPubKey)
                .outboxRelays()
                ?.toSet()
                .orEmpty()
        val fetchRelays =
            com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageFetcher
                .fetchRelaysFor(memberKeyPackageRelays, memberOutbox, myOutbox)

        Log.d("MarmotDbg") {
            "fetchKeyPackageAndAddMember: querying ${fetchRelays.size} relay(s) for ${memberPubKey.take(8)}… KeyPackage " +
                "(memberKeyPackageRelays=${memberKeyPackageRelays.size}, memberOutbox=${memberOutbox.size}, myOutbox=${myOutbox.size}): ${fetchRelays.map { it.url }}"
        }

        val event =
            com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageFetcher
                .fetchKeyPackage(client, memberPubKey, fetchRelays)

        if (event == null) {
            Log.w("MarmotDbg") {
                "fetchKeyPackageAndAddMember: NO KeyPackage found for ${memberPubKey.take(8)}… on any of ${fetchRelays.size} relay(s)"
            }
            return "Error: No KeyPackage found for this user. They may not have published one yet."
        }

        Log.d("MarmotDbg") {
            "fetchKeyPackageAndAddMember: got KeyPackage event id=${event.id.take(8)}… kind=${event.kind} authored=${event.pubKey.take(8)}…"
        }

        val keyPackageBase64 = event.keyPackageBase64()
        if (keyPackageBase64.isBlank()) {
            Log.w("MarmotDbg") { "fetchKeyPackageAndAddMember: KeyPackage event has empty content" }
            return "Error: KeyPackage event has empty content"
        }

        // The relays embedded in the WelcomeEvent tell the new member
        // where to subscribe for subsequent GroupEvents. Use our own
        // outbox — that's where we will publish them.
        val groupRelays = myOutbox.toList()

        Log.d("MarmotDbg") {
            "fetchKeyPackageAndAddMember: addMarmotGroupMember → groupRelays=${groupRelays.size}: ${groupRelays.map { it.url }}"
        }

        addMarmotGroupMember(
            nostrGroupId = nostrGroupId,
            keyPackageEvent = event,
            groupRelays = groupRelays,
        )

        return "Success: Member added to group"
    }

    /**
     * Add a member to a Marmot MLS group.
     * Publishes the commit GroupEvent, then sends the Welcome gift wrap.
     */
    suspend fun addMarmotGroupMember(
        nostrGroupId: HexKey,
        keyPackageEvent: com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent,
        groupRelays: List<NormalizedRelayUrl>,
    ) {
        val memberPubKey = keyPackageEvent.pubKey
        Log.d("MarmotDbg") {
            "addMarmotGroupMember: group=${nostrGroupId.take(8)}… member=${memberPubKey.take(8)}… " +
                "groupRelays=${groupRelays.size}"
        }
        val manager = marmotManager ?: return
        if (!isWriteable()) return

        val (commitEvent, welcomeDelivery) =
            manager.addMember(
                nostrGroupId = nostrGroupId,
                keyPackageEvent = keyPackageEvent,
                relays = groupRelays,
            )

        // The MLS commit has already been applied to the local group state —
        // surface the new member list in the chatroom now so observers (e.g.
        // MarmotGroupInfoScreen) update without waiting for our own commit to
        // loop back through the relay.
        val chatroom = marmotGroupList.getOrCreateGroup(nostrGroupId)
        manager.syncMetadataTo(nostrGroupId, chatroom)

        Log.d("MarmotDbg") {
            "addMarmotGroupMember: built commit kind=${commitEvent.signedEvent.kind} id=${commitEvent.signedEvent.id.take(8)}… " +
                "welcomeDelivery=${if (welcomeDelivery != null) "present(giftWrapId=${welcomeDelivery.giftWrapEvent.id.take(8)}…)" else "null"}"
        }

        // Publish commit first (critical ordering)
        Log.d("MarmotDbg") {
            "addMarmotGroupMember: publishing commit kind:${commitEvent.signedEvent.kind} to ${groupRelays.size} relay(s): ${groupRelays.map { it.url }}"
        }
        client.publish(commitEvent.signedEvent, groupRelays.toSet())

        // Then send the Welcome gift wrap to the new member.
        //
        // Use the same delivery path that NIP-17 DMs (kind:1059) take —
        // computeRelayListToBroadcast() — which has fallbacks for kind:10050
        // → NIP-65 read → relay hints. Empirically, NIP-17 DMs reach the
        // invitee, so this path is the one we know works. We also union
        // with our own outbox + the recipient's dmInboxRelays() as a
        // belt-and-braces measure in case the cache hasn't been hydrated
        // yet for this contact.
        if (welcomeDelivery != null) {
            val computed = computeRelayListToBroadcast(welcomeDelivery.giftWrapEvent)
            val recipientInbox =
                cache
                    .getOrCreateUser(memberPubKey)
                    .dmInboxRelays()
                    .orEmpty()
            val relayList = computed + outboxRelays.flow.value + recipientInbox
            Log.d("MarmotDbg") {
                "addMarmotGroupMember: welcome gift wrap relay sources " +
                    "computeRelayListToBroadcast=${computed.size} myOutbox=${outboxRelays.flow.value.size} " +
                    "recipientInbox=${recipientInbox.size} → union=${relayList.size}"
            }
            if (relayList.isEmpty()) {
                Log.w("MarmotDbg") {
                    "addMarmotGroupMember: NO relays to deliver welcome gift wrap to ${memberPubKey.take(8)}… — welcome will be silently dropped"
                }
            } else {
                Log.d("MarmotDbg") {
                    "addMarmotGroupMember: publishing welcome gift wrap id=${welcomeDelivery.giftWrapEvent.id.take(8)}… " +
                        "kind:${welcomeDelivery.giftWrapEvent.kind} → ${relayList.size} relay(s): ${relayList.map { it.url }}"
                }
            }
            client.publish(welcomeDelivery.giftWrapEvent, relayList)
        } else {
            Log.w("MarmotDbg") {
                "addMarmotGroupMember: welcomeDelivery is NULL — invitee ${memberPubKey.take(8)}… will receive nothing!"
            }
        }
    }

    /**
     * Relays where this account publishes kind:30443 KeyPackage events.
     * Per MIP-00: prefer kind:10051 KeyPackage Relay List; fall back to NIP-65 outbox.
     */
    fun keyPackagePublishRelays(): Set<NormalizedRelayUrl> =
        com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageFetcher
            .publishRelaysFor(keyPackageRelayList.flow.value, outboxRelays.flow.value)

    /**
     * Publish or rotate KeyPackage events.
     */
    suspend fun publishMarmotKeyPackages() {
        val manager =
            marmotManager ?: run {
                Log.w("MarmotDbg") { "publishMarmotKeyPackages: marmotManager is NULL — no-op" }
                return
            }
        if (!isWriteable()) {
            Log.w("MarmotDbg") { "publishMarmotKeyPackages: account is not writeable — no-op" }
            return
        }

        val relays = keyPackagePublishRelays()
        val needsRotation = manager.needsKeyPackageRotation()
        Log.d("MarmotDbg") {
            "publishMarmotKeyPackages: needsRotation=$needsRotation relays=${relays.size}"
        }

        if (needsRotation) {
            val rotatedEvents = manager.rotateConsumedKeyPackages(relays.toList())
            Log.d("MarmotDbg") {
                "publishMarmotKeyPackages: rotateConsumedKeyPackages produced ${rotatedEvents.size} event(s)"
            }
            rotatedEvents.forEach { event ->
                cache.justConsumeMyOwnEvent(event)
                Log.d("MarmotDbg") {
                    "publishMarmotKeyPackages: publishing rotated kind:${event.kind} id=${event.id.take(8)}… " +
                        "→ ${relays.size} relay(s): ${relays.map { it.url }}"
                }
                client.publish(event, relays)
            }
        }
    }

    /**
     * Generate and publish initial KeyPackage for this account.
     */
    suspend fun publishMarmotKeyPackage() {
        val manager = marmotManager ?: return
        if (!isWriteable()) return

        val relays = keyPackagePublishRelays()
        Log.d("MarmotDbg") {
            "publishMarmotKeyPackage: generating + publishing KeyPackage event → ${relays.size} relay(s): ${relays.map { it.url }}"
        }
        val event = manager.generateKeyPackageEvent(relays.toList())
        Log.d("MarmotDbg") {
            "publishMarmotKeyPackage: signed kind:${event.kind} id=${event.id.take(8)}… authored=${event.pubKey.take(8)}…"
        }
        cache.justConsumeMyOwnEvent(event)
        client.publish(event, relays)
    }

    /**
     * Ensure the local user has at least one active KeyPackage bundle and
     * a published KeyPackage event on relays. Called from [init] after
     * Marmot state has been restored from disk.
     *
     * - If [KeyPackageRotationManager] already has an active bundle (from
     *   the persisted snapshot), we trust the previous session and do
     *   nothing. The matching kind:30443 should already be on relays from
     *   when the bundle was first generated.
     * - Otherwise we generate a fresh bundle (which is now persisted to
     *   disk by [KeyPackageRotationManager.generateKeyPackage]) and
     *   publish the corresponding event.
     *
     * Best-effort: failures are logged but never propagated. We don't want
     * a flaky relay or missing outbox config at startup to crash account
     * initialization.
     */
    private suspend fun ensureMarmotKeyPackagePublished() {
        val manager = marmotManager ?: return
        if (!isWriteable()) return
        try {
            val hasBundle = manager.hasActiveKeyPackages()
            Log.d("MarmotDbg") {
                "ensureMarmotKeyPackagePublished: hasActiveKeyPackages=$hasBundle for ${signer.pubKey.take(8)}…"
            }
            if (hasBundle) {
                return
            }
            Log.d("MarmotDbg") {
                "ensureMarmotKeyPackagePublished: no active bundle — generating + publishing now"
            }
            publishMarmotKeyPackage()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("MarmotDbg", "ensureMarmotKeyPackagePublished failed: ${e.message}", e)
        }
    }

    /**
     * Check if a KeyPackage has been published in this session.
     * The d-tag is a randomly-generated value stored in the KeyPackageRotationManager's
     * persisted snapshot, so there is no fixed address to query in the cache.
     */
    suspend fun hasPublishedKeyPackage(): Boolean {
        val manager = marmotManager ?: return false
        return manager.hasActiveKeyPackages()
    }

    /**
     * Create a new Marmot MLS group.
     */
    suspend fun createMarmotGroup(nostrGroupId: HexKey) {
        val manager = marmotManager ?: return
        if (!isWriteable()) return
        manager.createGroup(nostrGroupId)
        // Creator owns the group — mark it as "known" immediately so it
        // doesn't appear under "New Requests" before the first message.
        marmotGroupList.markAsKnown(nostrGroupId)
    }

    /**
     * Leave a Marmot MLS group.
     * Publishes the SelfRemove proposal and removes local state.
     *
     * MIP-01/MIP-03: admins MUST first publish a GroupContextExtensions
     * commit dropping themselves from `admin_pubkeys` before issuing a
     * SelfRemove proposal. Without that, [MlsGroup.selfRemove] throws
     * `IllegalStateException("Admin must self-demote via GroupContextExtensions
     * before SelfRemove (MIP-01)")` and the leave aborts. Demote commit and
     * SelfRemove proposal both go to the same group relays, demote first so
     * peers apply it before they see the SelfRemove.
     */
    suspend fun leaveMarmotGroup(
        nostrGroupId: HexKey,
        groupRelays: Set<NormalizedRelayUrl>,
    ) {
        val manager = marmotManager ?: return
        if (!isWriteable()) return

        val metadata = manager.groupMetadata(nostrGroupId)
        if (metadata != null && metadata.adminPubkeys.contains(signer.pubKey)) {
            val remaining = metadata.adminPubkeys.filter { it != signer.pubKey }.toMutableList()
            // MIP-03 also rejects any GCE commit that leaves the group with zero
            // admins. If we're the only one, promote an arbitrary non-self
            // member to admin before stepping down.
            if (remaining.isEmpty()) {
                val heir =
                    manager
                        .memberPubkeys(nostrGroupId)
                        .map { it.pubkey }
                        .firstOrNull { it != signer.pubKey }
                if (heir != null) remaining.add(heir)
            }
            if (remaining.isNotEmpty()) {
                val demoted = metadata.copy(adminPubkeys = remaining)
                val demoteCommit = manager.updateGroupMetadata(nostrGroupId, demoted)
                client.publish(demoteCommit.signedEvent, groupRelays)
            }
        }

        val outbound = manager.leaveGroup(nostrGroupId)
        // manager.leaveGroup already wiped MLS state, relay subscriptions and
        // the persisted message log. Drop the in-memory chatroom too — that
        // releases the strong refs to the decrypted inner notes so LocalCache
        // (which holds them weakly) can GC them, and the Notification feed
        // (which iterates marmotGroupList.rooms) stops surfacing the group.
        marmotGroupList.removeGroup(nostrGroupId)
        client.publish(outbound.signedEvent, groupRelays)
    }

    /**
     * User-initiated "nuclear" reset for the Marmot subsystem.
     *
     * Wipes every MLS group, every retained epoch secret, every persisted
     * KeyPackage bundle, every relay subscription and every in-memory
     * chatroom associated with this account. Does NOT broadcast any
     * SelfRemove/leave commits to peers — if the user is in this flow at
     * all, local state may already be unusable and a graceful leave is
     * probably not possible. Peers will see the user as unresponsive until
     * their next commit evicts the stale leaf.
     *
     * A fresh KeyPackage will be republished lazily on the next
     * `ensureMarmotKeyPackagePublished` cycle, so the account remains
     * reachable for future group invites.
     */
    suspend fun resetMarmotState() {
        Log.w("MarmotDbg") { "resetMarmotState(): wiping all Marmot state for ${signer.pubKey.take(8)}…" }
        marmotManager?.resetAllState()
        for (groupId in marmotGroupList.allGroupIds()) {
            marmotGroupList.removeGroup(groupId)
        }
    }

    /**
     * Remove a member from a Marmot MLS group.
     * Publishes the commit GroupEvent to group relays.
     */
    suspend fun removeMarmotGroupMember(
        nostrGroupId: HexKey,
        targetLeafIndex: Int,
        groupRelays: Set<NormalizedRelayUrl>,
    ) {
        Log.d("MarmotDbg") {
            "removeMarmotGroupMember: group=${nostrGroupId.take(8)}… targetLeafIndex=$targetLeafIndex " +
                "groupRelays=${groupRelays.size}"
        }
        val manager =
            marmotManager ?: run {
                Log.w("MarmotDbg") { "removeMarmotGroupMember: marmotManager is NULL — no-op" }
                return
            }
        if (!isWriteable()) {
            Log.w("MarmotDbg") { "removeMarmotGroupMember: account is not writeable — no-op" }
            return
        }

        val outbound = manager.removeMember(nostrGroupId, targetLeafIndex)
        Log.d("MarmotDbg") {
            "removeMarmotGroupMember: built commit kind=${outbound.signedEvent.kind} id=${outbound.signedEvent.id.take(8)}…"
        }
        val chatroom = marmotGroupList.getOrCreateGroup(nostrGroupId)
        manager.syncMetadataTo(nostrGroupId, chatroom)
        Log.d("MarmotDbg") {
            "removeMarmotGroupMember: publishing commit id=${outbound.signedEvent.id.take(8)}… " +
                "to ${groupRelays.size} relay(s): ${groupRelays.map { it.url }}"
        }
        client.publish(outbound.signedEvent, groupRelays)
    }

    /**
     * Update a Marmot MLS group's metadata (name, description, etc.).
     * Publishes the commit GroupEvent to group relays.
     */
    suspend fun updateMarmotGroupMetadata(
        nostrGroupId: HexKey,
        metadata: com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData,
        groupRelays: Set<NormalizedRelayUrl>,
    ) {
        val manager = marmotManager ?: return
        if (!isWriteable()) return

        val outbound = manager.updateGroupMetadata(nostrGroupId, metadata)
        // The MLS commit has already been applied locally — surface the new
        // metadata in the chatroom now so the UI reflects it without waiting
        // for the relay round-trip.
        val chatroom = marmotGroupList.getOrCreateGroup(nostrGroupId)
        manager.syncMetadataTo(nostrGroupId, chatroom)
        client.publish(outbound.signedEvent, groupRelays)
    }

    /**
     * Grant admin privileges to [targetPubKey] in a Marmot MLS group by
     * appending them to `admin_pubkeys` via a GroupContextExtensions commit.
     *
     * No-op if the group has no prior metadata (shouldn't happen outside the
     * first bootstrap commit) or the target is already an admin. Callers
     * must be an admin themselves — the MLS engine enforces this via the
     * MIP-03 authorization gate in `enforceAuthorizedProposalSet`.
     */
    suspend fun grantMarmotGroupAdmin(
        nostrGroupId: HexKey,
        targetPubKey: HexKey,
        groupRelays: Set<NormalizedRelayUrl>,
    ) {
        val manager = marmotManager ?: return
        if (!isWriteable()) return

        val metadata = manager.groupMetadata(nostrGroupId) ?: return
        if (metadata.adminPubkeys.contains(targetPubKey)) return

        val outboxRelayStrings = outboxRelays.flow.value.map { it.url }
        val updated =
            metadata
                .copy(adminPubkeys = metadata.adminPubkeys + targetPubKey)
                .withMergedRelays(outboxRelayStrings)
        updateMarmotGroupMetadata(nostrGroupId, updated, groupRelays)
    }

    /**
     * Revoke admin privileges from [targetPubKey]. Rejects any change that
     * would leave the group with zero admins — MIP-03's admin-depletion guard
     * in [com.vitorpamplona.quartz.marmot.mls.group.MlsGroup] would otherwise
     * throw at commit time.
     */
    suspend fun revokeMarmotGroupAdmin(
        nostrGroupId: HexKey,
        targetPubKey: HexKey,
        groupRelays: Set<NormalizedRelayUrl>,
    ) {
        val manager = marmotManager ?: return
        if (!isWriteable()) return

        val metadata = manager.groupMetadata(nostrGroupId) ?: return
        if (!metadata.adminPubkeys.contains(targetPubKey)) return
        val remaining = metadata.adminPubkeys.filter { it != targetPubKey }
        check(remaining.isNotEmpty()) {
            "Cannot revoke the last admin from a Marmot group (MIP-03)"
        }

        val outboxRelayStrings = outboxRelays.flow.value.map { it.url }
        val updated =
            metadata
                .copy(adminPubkeys = remaining)
                .withMergedRelays(outboxRelayStrings)
        updateMarmotGroupMetadata(nostrGroupId, updated, groupRelays)
    }

    suspend fun createStatus(newStatus: String) = sendMyPublicAndPrivateOutbox(UserStatusAction.create(newStatus, signer))

    suspend fun publishCallSignaling(wrap: EphemeralGiftWrapEvent) {
        val relayList = computeRelayListToBroadcast(wrap)
        client.publish(wrap, relayList)
    }

    suspend fun updateStatus(
        oldStatus: AddressableNote,
        newStatus: String,
    ) = sendMyPublicAndPrivateOutbox(UserStatusAction.update(oldStatus, newStatus, signer))

    suspend fun deleteStatus(oldStatus: AddressableNote) = sendMyPublicAndPrivateOutbox(UserStatusAction.delete(oldStatus, signer))

    suspend fun removeEmojiPack(emojiPack: Note) = sendMyPublicAndPrivateOutbox(emoji.removeEmojiPack(emojiPack))

    suspend fun addEmojiPack(emojiPack: Note) = sendMyPublicAndPrivateOutbox(emoji.addEmojiPack(emojiPack))

    suspend fun createOwnedEmojiPack(
        title: String,
        description: String? = null,
        image: String? = null,
    ) = ownedEmojiPacks.createPack(title, description, image, this)

    suspend fun updateOwnedEmojiPackMetadata(
        dTag: String,
        newTitle: String,
        newDescription: String?,
        newImage: String?,
    ) = ownedEmojiPacks.updateMetadata(dTag, newTitle, newDescription, newImage, this)

    suspend fun addEmojiToOwnedPack(
        dTag: String,
        emoji: com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag,
        isPrivate: Boolean,
    ) = ownedEmojiPacks.addEmoji(dTag, emoji, isPrivate, this)

    suspend fun removeEmojiFromOwnedPack(
        dTag: String,
        shortcode: String,
        isPrivate: Boolean,
    ) = ownedEmojiPacks.removeEmoji(dTag, shortcode, isPrivate, this)

    suspend fun deleteOwnedEmojiPack(dTag: String) = ownedEmojiPacks.deletePack(dTag, this)

    suspend fun addToGallery(
        idHex: HexKey,
        url: String,
        relay: NormalizedRelayUrl?,
        blurhash: String?,
        dim: DimensionTag?,
        hash: String?,
        mimeType: String?,
        thumbhash: String? = null,
        image: String? = null,
    ) {
        val template =
            ProfileGalleryEntryEvent.build(url) {
                fromEvent(idHex, relay)
                hash?.let { hash(hash) }
                mimeType?.let { mimeType(it) }
                dim?.let { dimension(it) }
                blurhash?.let { blurhash(it) }
                thumbhash?.let { galleryThumbhash(it) }
                image?.let { image(it) }
            }

        val event = signer.sign(template)
        sendMyPublicAndPrivateOutbox(event)
    }

    suspend fun removeFromGallery(note: Note) {
        delete(note)
    }

    suspend fun addGitRepositoryBookmark(note: AddressableNote) {
        if (!isWriteable()) return
        sendMyPublicAndPrivateOutbox(gitRepositoryListState.addRepository(note))
    }

    suspend fun removeGitRepositoryBookmark(note: AddressableNote) {
        if (!isWriteable()) return
        gitRepositoryListState.removeRepository(note)?.let { sendMyPublicAndPrivateOutbox(it) }
    }

    suspend fun addBookmark(
        note: Note,
        isPrivate: Boolean,
    ) {
        if (!isWriteable() || note.isDraft()) return

        sendMyPublicAndPrivateOutbox(bookmarkState.addBookmark(note, isPrivate))
    }

    suspend fun removeBookmark(
        note: Note,
        isPrivate: Boolean,
    ) {
        if (!isWriteable() || note.isDraft()) return

        val event = bookmarkState.removeBookmark(note, isPrivate)
        if (event != null) {
            sendMyPublicAndPrivateOutbox(event)
        }
    }

    suspend fun removeBookmark(note: Note) {
        if (!isWriteable() || note.isDraft()) return

        val event = bookmarkState.removeBookmark(note)
        if (event != null) {
            sendMyPublicAndPrivateOutbox(event)
        }
    }

    suspend fun removeDeletedBookmarks(
        deletedEventIds: Set<String>,
        deletedAddresses: Set<Address>,
    ) {
        if (!isWriteable()) return
        val event = bookmarkState.removeDeletedBookmarks(deletedEventIds, deletedAddresses) ?: return
        sendMyPublicAndPrivateOutbox(event)
    }

    suspend fun removeDeletedOldBookmarks(
        deletedEventIds: Set<String>,
        deletedAddresses: Set<Address>,
    ) {
        if (!isWriteable()) return
        val event = oldBookmarkState.removeDeletedBookmarks(deletedEventIds, deletedAddresses) ?: return
        sendMyPublicAndPrivateOutbox(event)
    }

    /**
     * Creates a bookmark event without sending it.
     * Returns the event and target relays for tracked broadcasting.
     */
    suspend fun createAddBookmarkEvent(
        note: Note,
        isPrivate: Boolean,
    ): Pair<Event, Set<NormalizedRelayUrl>>? {
        if (!isWriteable() || note.isDraft()) return null

        val event = bookmarkState.addBookmark(note, isPrivate)
        val relays = outboxRelays.flow.value

        return event to relays
    }

    /**
     * Creates a remove bookmark event without sending it.
     * Returns the event and target relays for tracked broadcasting.
     */
    suspend fun createRemoveBookmarkEvent(
        note: Note,
        isPrivate: Boolean,
    ): Pair<Event, Set<NormalizedRelayUrl>>? {
        if (!isWriteable() || note.isDraft()) return null

        val event = bookmarkState.removeBookmark(note, isPrivate) ?: return null
        val relays = outboxRelays.flow.value

        return event to relays
    }

    /**
     * Consumes a bookmark event into local cache.
     * Called when tracked broadcasting succeeds.
     */
    fun consumeBookmarkEvent(event: Event) {
        cache.justConsumeMyOwnEvent(event)
    }

    suspend fun migrateOldBookmarksToNew() {
        if (!isWriteable()) return

        val oldList = oldBookmarkState.getBookmarkList() ?: return
        val oldPublic = oldList.publicBookmarks()
        val oldPrivate = oldList.privateBookmarks(signer) ?: emptyList()

        if (oldPublic.isEmpty() && oldPrivate.isEmpty()) return

        val existingNewList = bookmarkState.getBookmarkList()

        val newEvent =
            if (existingNewList != null) {
                val existingPublic = existingNewList.publicBookmarks()
                val existingPrivate = existingNewList.privateBookmarks(signer) ?: emptyList()

                val existingPublicIds = existingPublic.map { it.toTagIdOnly().toList() }.toSet()
                val existingPrivateIds = existingPrivate.map { it.toTagIdOnly().toList() }.toSet()

                val newPublic = oldPublic.filter { it.toTagIdOnly().toList() !in existingPublicIds }
                val newPrivate = oldPrivate.filter { it.toTagIdOnly().toList() !in existingPrivateIds }

                if (newPublic.isEmpty() && newPrivate.isEmpty()) return

                val mergedPublic = existingPublic + newPublic
                val mergedPrivate = existingPrivate + newPrivate

                BookmarkListEvent.create(
                    publicBookmarks = mergedPublic,
                    privateBookmarks = mergedPrivate,
                    signer = signer,
                )
            } else {
                BookmarkListEvent.create(
                    publicBookmarks = oldPublic,
                    privateBookmarks = oldPrivate,
                    signer = signer,
                )
            }

        sendMyPublicAndPrivateOutbox(newEvent)
    }

    suspend fun addPin(note: Note) {
        if (!isWriteable() || note.isDraft()) return

        sendMyPublicAndPrivateOutbox(pinState.addPin(note))
    }

    suspend fun removePin(note: Note) {
        if (!isWriteable() || note.isDraft()) return

        val event = pinState.removePin(note)
        if (event != null) {
            sendMyPublicAndPrivateOutbox(event)
        }
    }

    suspend fun removeDeletedPins(deletedNotes: Set<Note>) {
        if (!isWriteable()) return

        val event = pinState.removeDeletedPins(deletedNotes) ?: return
        sendMyPublicAndPrivateOutbox(event)
    }

    suspend fun createAddPinEvent(note: Note): Pair<Event, Set<NormalizedRelayUrl>>? {
        if (!isWriteable() || note.isDraft()) return null

        val event = pinState.addPin(note)
        val relays = outboxRelays.flow.value

        return event to relays
    }

    suspend fun createRemovePinEvent(note: Note): Pair<Event, Set<NormalizedRelayUrl>>? {
        if (!isWriteable() || note.isDraft()) return null

        val event = pinState.removePin(note) ?: return null
        val relays = outboxRelays.flow.value

        return event to relays
    }

    fun consumePinEvent(event: Event) {
        cache.justConsumeMyOwnEvent(event)
    }

    suspend fun createAuthEvent(
        relay: NormalizedRelayUrl,
        challenge: String,
    ): RelayAuthEvent = RelayAuthEvent.create(relay, challenge, signer)

    suspend fun hideWord(word: String) {
        sendMyPublicAndPrivateOutbox(muteList.hideWord(word))
    }

    suspend fun showWord(word: String) {
        sendMyPublicAndPrivateOutbox(blockPeopleList.showWord(word))
        sendMyPublicAndPrivateOutbox(muteList.showWord(word))
    }

    suspend fun hideHashtag(hashtag: String) {
        sendMyPublicAndPrivateOutbox(muteList.hideHashtag(hashtag))
    }

    suspend fun showHashtag(hashtag: String) {
        muteList.showHashtag(hashtag)?.let { sendMyPublicAndPrivateOutbox(it) }
    }

    suspend fun hideUser(pubkeyHex: HexKey) {
        sendMyPublicAndPrivateOutbox(muteList.hideUser(pubkeyHex))
    }

    /**
     * Nicknames a user by publishing the account's kind:30382 contact card about
     * them, with the petname, summary and their custom emoji mappings NIP-44
     * encrypted in the content. `null` clears a field. Goes out through the
     * account's extended outbox relays.
     */
    suspend fun updateContactCardPetName(
        pubkeyHex: HexKey,
        petName: String?,
        summary: String?,
    ) = sendMyPublicAndPrivateOutbox(contactCards.updatePetNameAndSummary(pubkeyHex, petName, summary))

    suspend fun showUser(pubkeyHex: HexKey) {
        sendMyPublicAndPrivateOutbox(blockPeopleList.showUser(pubkeyHex))
        sendMyPublicAndPrivateOutbox(muteList.showUser(pubkeyHex))
        hiddenUsers.showUser(pubkeyHex)
    }

    suspend fun showUsers(pubkeys: List<HexKey>) {
        if (pubkeys.isEmpty()) return
        sendMyPublicAndPrivateOutbox(blockPeopleList.showUsers(pubkeys))
        sendMyPublicAndPrivateOutbox(muteList.showUsers(pubkeys))
        pubkeys.forEach { hiddenUsers.showUser(it) }
    }

    suspend fun showWords(words: List<String>) {
        if (words.isEmpty()) return
        sendMyPublicAndPrivateOutbox(blockPeopleList.showWords(words))
        sendMyPublicAndPrivateOutbox(muteList.showWords(words))
    }

    suspend fun muteThread(rootHex: HexKey) {
        if (isThreadMuted(rootHex)) return
        sendMyPublicAndPrivateOutbox(muteList.hideThread(rootHex))
    }

    suspend fun unmuteThread(rootHex: HexKey) {
        if (!isThreadMuted(rootHex)) return
        muteList.showThread(rootHex)?.let { sendMyPublicAndPrivateOutbox(it) }
    }

    fun resolveThreadRoot(note: Note): HexKey = note.event?.threadRootIdOrSelf() ?: note.idHex

    fun isThreadMuted(rootHex: HexKey): Boolean = hiddenUsers.flow.value.isThreadMuted(rootHex)

    suspend fun requestDVMContentDiscovery(
        dvmPublicKey: User,
        onReady: (event: NIP90ContentDiscoveryRequestEvent, relays: Set<NormalizedRelayUrl>) -> Unit,
    ) {
        val relays = nip65RelayList.inboxFlow.value.toSet()
        val request = signer.sign<NIP90ContentDiscoveryRequestEvent>(NIP90ContentDiscoveryRequestEvent.build(dvmPublicKey.pubkeyHex, signer.pubKey, relays))

        val relayList =
            dvmPublicKey.inboxRelays()?.toSet()?.ifEmpty { null }
                ?: (dvmPublicKey.allUsedRelays() + cache.relayHints.hintsForKey(dvmPublicKey.pubkeyHex))

        cache.justConsumeMyOwnEvent(request)
        onReady(request, relayList.toSet())
        delay(100)
        client.publish(request, relayList)
    }

    fun cachedDecryptContent(note: Note): String? = cachedDecryptContent(note.event)

    fun cachedDecryptContent(event: Event?): String? {
        if (event == null) return null

        return if (isWriteable()) {
            when {
                event is PrivateDmEvent -> privateDMDecryptionCache.cachedDM(event)
                event is LnZapRequestEvent && event.isPrivateZap() -> privateZapsDecryptionCache.cachedPrivateZap(event)?.content
                event is DraftWrapEvent -> draftsDecryptionCache.preCachedDraft(event)?.content
                else -> event.content
            }
        } else {
            // A read-only (npub-only) account holds no key, so nothing above can run. Returning
            // `content` verbatim would push the raw NIP-04/NIP-44 base64 blob straight into the
            // UI (chat bubbles, Messages previews, ...). Callers treat null as "not readable".
            if (event.hasEncryptedContent()) null else event.content
        }
    }

    suspend fun decryptContent(note: Note): String? {
        val event = note.event
        return when {
            event is PrivateDmEvent && isWriteable() -> {
                privateDMDecryptionCache.decryptDM(event)
            }

            event is LnZapRequestEvent && isWriteable() -> {
                if (event.isPrivateZap()) {
                    if (isWriteable()) {
                        privateZapsDecryptionCache.decryptPrivateZap(event)?.content
                    } else {
                        null
                    }
                } else {
                    event.content
                }
            }

            event is DraftWrapEvent && isWriteable() -> {
                draftsDecryptionCache.cachedDraft(event)?.content
            }

            // Encrypted kinds that reached here did so because this account is not writeable
            // (every branch above is gated on isWriteable). Their `content` is ciphertext —
            // hand back null rather than let the blob render. See cachedDecryptContent.
            event != null && event.hasEncryptedContent() -> null

            else -> {
                event?.content
            }
        }
    }

    suspend fun decryptZapOrNull(event: LnZapRequestEvent): LnZapPrivateEvent? = if (event.isPrivateZap() && isWriteable()) privateZapsDecryptionCache.decryptPrivateZap(event) else null

    fun isAllHidden(users: Set<HexKey>): Boolean = users.all { isHidden(it) }

    override fun isHidden(user: User) = isHidden(user.pubkeyHex)

    fun isHidden(userHex: String): Boolean = hiddenUsers.flow.value.isUserHidden(userHex)

    override fun followingKeySet(): Set<HexKey> = kind3FollowList.flow.value.authors

    fun isAcceptable(user: User): Boolean {
        if (userProfile().pubkeyHex == user.pubkeyHex) {
            return true
        }

        if (user.pubkeyHex in followingKeySet()) {
            return true
        }

        if (!settings.syncedSettings.security.warnAboutPostsWithReports.value) {
            if (isHidden(user)) return false

            val reports = user.reportsOrNull() ?: return true

            return reports.reportsBy(userProfile()).isEmpty() // if user has not reported this post
        }

        if (isHidden(user)) return false

        val reports = user.reportsOrNull() ?: return true
        val reportWarningThreshold =
            settings.syncedSettings.security.reportWarningThreshold.value
                .coerceAtLeast(1)

        // if user hasn't hided this author
        return reports.reportsBy(userProfile()).isEmpty() &&
            // if user has not reported this post
            reports.countReportAuthorsBy(followingKeySet()) < reportWarningThreshold
    }

    private fun isAcceptableDirect(note: Note): Boolean {
        if (!settings.syncedSettings.security.warnAboutPostsWithReports.value) {
            return !note.hasReportsBy(userProfile())
        }
        val reportWarningThreshold =
            settings.syncedSettings.security.reportWarningThreshold.value
                .coerceAtLeast(1)
        return !note.hasReportsBy(userProfile()) &&
            // if user has not reported this post
            note.countReportAuthorsBy(followingKeySet()) < reportWarningThreshold
    }

    fun isDecryptedContentHidden(noteEvent: PrivateDmEvent): Boolean =
        if (hiddenUsers.flow.value.hiddenWordsCase
                .isNotEmpty()
        ) {
            val decrypted = privateDMDecryptionCache.cachedDM(noteEvent)
            decrypted?.containsAny(hiddenUsers.flow.value.hiddenWordsCase) == true
        } else {
            false
        }

    fun isFollowing(user: User): Boolean = user.pubkeyHex in followingKeySet()

    fun isFollowing(user: HexKey): Boolean = user in followingKeySet()

    fun isKnown(user: User): Boolean = user.pubkeyHex in allFollows.flow.value.authors

    fun isKnown(user: HexKey): Boolean = user in allFollows.flow.value.authors

    fun maxHashtagLimit(): Int = settings.syncedSettings.security.maxHashtagLimit.value

    fun hasExcessiveHashtags(note: Note): Boolean {
        val limit = maxHashtagLimit()
        return limit > 0 && note.event?.hasMoreHashtagsThan(limit) == true
    }

    /**
     * True if [note] is a Concord channel message whose author is banned in that
     * community's current fold. Bans are per-community (not global mutes), so they
     * are enforced here at read time — the same "filter, don't delete" approach the
     * rest of the app uses. A ban that arrives after a message is applied on the
     * next feed pass.
     */
    private fun isConcordBanned(note: Note): Boolean {
        val channel = note.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel } ?: return false
        val author = note.author?.pubkeyHex ?: note.event?.pubKey ?: return false
        val authority =
            concordSessions
                .sessionFor(channel.channelId.communityId)
                ?.state
                ?.value
                ?.authority ?: return false
        return authority.isBanned(author)
    }

    override fun isAcceptable(note: Note): Boolean {
        if (isConcordBanned(note)) return false
        val mutedThreads = hiddenUsers.flow.value.mutedThreads
        if (mutedThreads.isNotEmpty() && mutedThreads.contains(resolveThreadRoot(note))) return false
        return note.author?.let { isAcceptable(it) } ?: true &&
            // if user hasn't hided this author
            isAcceptableDirect(note) &&
            !hasExcessiveHashtags(note) &&
            (
                (note.event !is RepostEvent && note.event !is GenericRepostEvent) ||
                    (
                        note.replyTo?.firstOrNull { isAcceptableDirect(it) } !=
                            null
                    )
            ) // is not a reaction about a blocked post
    }

    fun getRelevantReports(note: Note): Set<Note> {
        val innerReports =
            if (note.event is RepostEvent || note.event is GenericRepostEvent) {
                note.replyTo?.flatMap { getRelevantReports(it) } ?: emptyList()
            } else {
                emptyList()
            }

        return (
            note.reportsBy(kind3FollowList.flow.value.authorsPlusMe) +
                (note.author?.reportsOrNull()?.reportsBy(kind3FollowList.flow.value.authorsPlusMe) ?: emptyList()) +
                innerReports
        ).toSet()
    }

    suspend fun saveDMRelayList(dmRelays: List<NormalizedRelayUrl>) = sendLiterallyEverywhere(dmRelayList.saveRelayList(dmRelays))

    suspend fun saveKeyPackageRelayList(keyPackageRelays: List<NormalizedRelayUrl>) {
        val oldRelays = keyPackageRelayList.flow.value
        val newRelays = keyPackageRelays.toSet()
        sendLiterallyEverywhere(keyPackageRelayList.saveRelayList(keyPackageRelays))
        if (oldRelays != newRelays) {
            republishEventsTo(myKeyPackageEvents(), newRelays)
        }
    }

    suspend fun savePrivateOutboxRelayList(relays: List<NormalizedRelayUrl>) {
        val oldRelays = privateStorageRelayList.flow.value
        val newRelays = relays.toSet()
        sendMyPublicAndPrivateOutbox(privateStorageRelayList.saveRelayList(relays))
        if (oldRelays != newRelays) {
            republishEventsTo(accountSettingsEvents(), newRelays)
        }
    }

    suspend fun saveSearchRelayList(searchRelays: List<NormalizedRelayUrl>) {
        val oldRelays = searchRelayList.flowNoDefaults.value
        val newRelays = searchRelays.toSet()
        sendMyPublicAndPrivateOutbox(searchRelayList.saveRelayList(searchRelays))
        if (oldRelays != newRelays) {
            republishEventsTo(
                listOfNotNull(userMetadata.getUserMetadataEvent()),
                newRelays,
            )
        }
    }

    suspend fun saveIndexerRelayList(trustedRelays: List<NormalizedRelayUrl>) {
        val oldRelays = indexerRelayList.flowNoDefaults.value
        val newRelays = trustedRelays.toSet()
        sendMyPublicAndPrivateOutbox(indexerRelayList.saveRelayList(trustedRelays))
        if (oldRelays != newRelays) {
            republishEventsTo(
                listOfNotNull(
                    userMetadata.getUserMetadataEvent(),
                    kind3FollowList.getFollowListEvent(),
                ),
                newRelays,
            )
        }
    }

    suspend fun saveBroadcastRelayList(trustedRelays: List<NormalizedRelayUrl>) {
        val oldRelays = broadcastRelayList.flow.value
        val newRelays = trustedRelays.toSet()
        sendMyPublicAndPrivateOutbox(broadcastRelayList.saveRelayList(trustedRelays))
        if (oldRelays != newRelays) {
            republishEventsTo(accountSettingsEvents(), newRelays)
        }
    }

    suspend fun saveLocalRelayList(relays: List<NormalizedRelayUrl>) {
        val oldRelays = localRelayList.flow.value
        val newRelays = relays.toSet()
        localRelayList.saveRelayList(relays) {}
        if (oldRelays != newRelays) {
            republishEventsTo(accountSettingsEvents(), newRelays)
        }
    }

    suspend fun saveProxyRelayList(trustedRelays: List<NormalizedRelayUrl>) = sendMyPublicAndPrivateOutbox(proxyRelayList.saveRelayList(trustedRelays))

    suspend fun saveTrustedRelayList(trustedRelays: List<NormalizedRelayUrl>) = sendMyPublicAndPrivateOutbox(trustedRelayList.saveRelayList(trustedRelays))

    suspend fun saveRelayFeedsList(trustedRelays: List<NormalizedRelayUrl>) = sendMyPublicAndPrivateOutbox(relayFeedsList.saveRelayList(trustedRelays))

    suspend fun followRelayFeed(url: NormalizedRelayUrl) = sendMyPublicAndPrivateOutbox(relayFeedsList.addRelay(url))

    suspend fun unfollowRelayFeed(url: NormalizedRelayUrl) = sendMyPublicAndPrivateOutbox(relayFeedsList.removeRelay(url))

    suspend fun saveBlockedRelayList(blockedRelays: List<NormalizedRelayUrl>) = sendMyPublicAndPrivateOutbox(blockedRelayList.saveRelayList(blockedRelays))

    /**
     * Returns all known signed replaceable events that configure this account
     * (profile, contact list, relay lists, mute list, bookmarks, etc.). Events
     * that have never been created or downloaded are omitted.
     */
    fun accountSettingsEvents(): List<Event> =
        listOfNotNull(
            userMetadata.getUserMetadataEvent(),
            userMetadata.getExternalIdentitiesEvent(),
            kind3FollowList.getFollowListEvent(),
            nip65RelayList.getNIP65RelayList(),
            dmRelayList.getDMRelayList(),
            keyPackageRelayList.getKeyPackageRelayList(),
            privateStorageRelayList.getPrivateOutboxRelayList(),
            searchRelayList.getSearchRelayList(),
            trustedRelayList.getTrustedRelayList(),
            proxyRelayList.getProxyRelayList(),
            broadcastRelayList.getBroadcastRelayList(),
            indexerRelayList.getIndexerRelayList(),
            relayFeedsList.getRelayFeedsList(),
            blockedRelayList.getBlockedRelayList(),
            muteList.getMuteList(),
            bookmarkState.getBookmarkList(),
            pinState.getPinList(),
            blossomServers.getBlossomServersList(),
            nestsServers.getNestsServersList(),
            paymentTargetsState.getPaymentTargetsEvent(),
            trustProviderList.getTrustProviderList(),
            cache.getAddressableNoteIfExists(appSpecific.getAppSpecificDataAddress())?.event,
        )

    /**
     * Returns all currently-known signed KeyPackage events authored by this account.
     */
    fun myKeyPackageEvents(): List<Event> =
        cache.addressables
            .filter(KeyPackageEvent.KIND, signer.pubKey)
            .mapNotNull { it.event }

    /** Publishes the given events to each of the given relays. No-op if either list is empty. */
    fun republishEventsTo(
        events: List<Event>,
        relays: Set<NormalizedRelayUrl>,
    ) {
        if (relays.isEmpty() || events.isEmpty()) return
        events.forEach { client.publish(it, relays) }
    }

    suspend fun requestToVanish(
        relays: List<NormalizedRelayUrl>,
        reason: String,
        createdAt: Long,
    ) {
        if (!isWriteable() || relays.isEmpty()) return

        val template = RequestToVanishEvent.build(relays, reason, createdAt)
        val signedEvent = signer.sign(template)
        cache.justConsumeMyOwnEvent(signedEvent)
        client.publish(signedEvent, outboxRelays.flow.value + relays.toSet())
    }

    suspend fun requestToVanishFromEverywhere(
        reason: String,
        createdAt: Long,
    ) {
        if (!isWriteable()) return

        val template = RequestToVanishEvent.buildVanishFromEverywhere(reason, createdAt)
        val signedEvent = signer.sign(template)
        cache.justConsumeMyOwnEvent(signedEvent)
        client.publish(signedEvent, followPlusAllMineWithIndex.flow.value + client.availableRelaysFlow().value)
    }

    suspend fun sendNip65RelayList(relays: List<AdvertisedRelayInfo>) {
        val oldOutbox = nip65RelayList.outboxFlowNoDefaults.value
        val oldInbox = nip65RelayList.inboxFlowNoDefaults.value
        val newOutbox =
            relays
                .filter { it.type.isWrite() }
                .map { it.relayUrl }
                .toSet()
        val newInbox =
            relays
                .filter { it.type.isRead() }
                .map { it.relayUrl }
                .toSet()
        sendLiterallyEverywhere(nip65RelayList.saveRelayList(relays))
        if (oldOutbox != newOutbox || oldInbox != newInbox) {
            republishEventsTo(accountSettingsEvents(), newOutbox)
        }
    }

    suspend fun sendBlossomServersList(servers: List<String>) = sendMyPublicAndPrivateOutbox(blossomServers.saveBlossomServersList(servers))

    suspend fun sendNestsServersList(servers: List<com.vitorpamplona.quartz.nip53LiveActivities.nestsServers.NestsServer>) = sendMyPublicAndPrivateOutbox(nestsServers.saveNestsServersList(servers))

    suspend fun savePaymentTargets(targets: List<PaymentTarget>) = sendMyPublicAndPrivateOutbox(paymentTargetsState.savePaymentTargets(targets))

    fun markAsRead(
        route: String,
        timestampInSecs: Long,
    ) = settings.markAsRead(route, timestampInSecs)

    fun loadLastRead(route: String): Long = settings.lastReadPerRoute.value[route]?.value ?: 0

    fun loadLastReadFlow(route: String) = settings.getLastReadFlow(route)

    fun hasDonatedInThisVersion() = settings.hasDonatedInVersion(BuildConfig.VERSION_NAME)

    fun observeDonatedInThisVersion() =
        settings
            .observeDonatedInVersion(BuildConfig.VERSION_NAME)
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, hasDonatedInThisVersion())

    fun markDonatedInThisVersion() = settings.markDonatedInThisVersion(BuildConfig.VERSION_NAME)

    fun dismissPollNotification(noteId: String) = settings.dismissPollNotification(noteId)

    fun hasViewedPollResults(noteId: String) = settings.hasViewedPollResults(noteId)

    fun markPollResultsViewed(
        noteId: String,
        pollEndsAt: Long?,
    ) = settings.markPollResultsViewed(noteId, pollEndsAt)

    init {
        Log.d("AccountRegisterObservers", "Init")

        // Start the Cashu wallet state observers AFTER all field initializers
        // complete — auto-redeem can fire as soon as start() returns, and it
        // calls back into sendLiterallyEverywhere which depends on
        // followPlusAllMineWithIndex (initialized after cashuWalletState).
        // Doing this in start() rather than in the state's own init { } closes
        // the race where a publish would land on a half-built Account.
        cashuWalletState.start { event -> sendLiterallyEverywhere(event) }

        // Restore Marmot MLS group state on startup
        if (marmotManager != null) {
            scope.launch(Dispatchers.IO) {
                marmotManager.restoreAll()

                // Ensure the local user has a KeyPackage published to relays
                // so other users can invite them to groups. Without this,
                // freshly installed accounts (and accounts that never opened
                // the Marmot Group screen) would never have an active
                // KeyPackage on the relays, and any inviter trying to add
                // them would fail with "No KeyPackage found".
                //
                // The KeyPackage bundle (private keys included) is persisted
                // by KeyPackageRotationManager via marmotKeyPackageStore, so
                // restoreAll() above has already restored any previously
                // generated bundles. Only generate-and-publish if no active
                // bundle exists in memory after restore.
                ensureMarmotKeyPackagePublished()

                // Sync MIP-01 metadata from restored groups to chatrooms and
                // re-hydrate decrypted messages from persistent storage.
                // Note: Marmot MLS application messages cannot be re-decrypted
                // after the ratchet advances, so persisted plaintext is the
                // only way to restore group history across restarts.
                marmotManager.activeGroupIds().forEach { groupId ->
                    val chatroom = marmotGroupList.getOrCreateGroup(groupId)
                    marmotManager.syncMetadataTo(groupId, chatroom)
                    // Force the kind:445 EOSE manager to re-poll its filter
                    // set so the restored group's per-`h`-tag subscription
                    // is actually sent to relays. Without this, restored
                    // groups would never receive new messages until the user
                    // explicitly created/joined another group.
                    marmotGroupList.notifyGroupChanged(groupId)

                    val storedMessages = marmotManager.loadStoredMessages(groupId)
                    if (storedMessages.isNotEmpty()) {
                        Log.d("Account") {
                            "Restoring ${storedMessages.size} Marmot message(s) for group $groupId"
                        }
                        storedMessages.forEach { json ->
                            try {
                                val innerEvent =
                                    com.vitorpamplona.quartz.nip01Core.core.Event
                                        .fromJson(json)
                                // wasVerified=true: MIP-03 inner events are
                                // unsigned rumors (empty sig), authenticated
                                // via the MLS credential-identity check in
                                // GroupEventHandler when first decrypted.
                                // Running Nostr sig verify here (justVerify
                                // via wasVerified=false) would silently drop
                                // kind:7 reactions / kind:5 deletions since
                                // they never carry a Schnorr signature.
                                val isNew = cache.justConsume(innerEvent, null, true)
                                val innerNote = cache.getOrCreateNote(innerEvent.id)
                                if (isNew) {
                                    innerNote.event = innerEvent
                                }
                                marmotGroupList.addMessage(groupId, innerNote)
                            } catch (e: Exception) {
                                Log.w(
                                    "Account",
                                    "Failed to restore persisted Marmot message for $groupId",
                                    e,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Keep Concord channel metadata (community name/icon, membership) live across the whole
        // app — not just the hub screen — so the Messages tab renders each channel's community
        // chip, and per-community bans apply, as soon as a Control Plane folds. The revision now
        // bumps only on *structural* change (a fold / membership / rekey, never a plain message),
        // so this fires rarely; sample() stays as a cheap coalescer for a burst of folds.
        scope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            concordSessions.revision.sample(500).collect {
                refreshConcordChannelIndex()
                // A revision also bumps when a base-rotation rekey lands; adopt ours if present.
                runCatching { drainConcordRekeys() }.onFailure { Log.w("Concord", "rekey drain failed", it) }
                // A rotation we were *excluded* from produces no rekey to drain, so it can only be
                // found by re-resolving the invite link we joined through. Rate-limited internally.
                runCatching { recoverStrandedConcordCommunities() }.onFailure { Log.w("Concord", "stranded recovery failed", it) }
            }
        }

        scope.launch {
            cache.antiSpam.flowSpam.collect {
                it.cache.spamMessages.snapshot().values.forEach { spammer ->
                    if (!hiddenUsers.isHidden(spammer.pubkeyHex) &&
                        spammer.shouldHide() &&
                        spammer.pubkeyHex != userProfile().pubkeyHex &&
                        spammer.pubkeyHex !in followingKeySet()
                    ) {
                        hiddenUsers.hideUser(spammer.pubkeyHex)
                    }
                }
            }
        }

        scope.launch {
            cache.live.newEventBundles.collect { newNotes ->
                logTime("Account ${userProfile().toBestDisplayName()} newEventBundle Update with ${newNotes.size} new notes") {
                    upgradeAttestations()
                    newNotesPreProcessor.runNew(newNotes)
                    peopleLists.newNotes(newNotes)
                    followLists.newNotes(newNotes)
                    labeledBookmarkLists.newNotes(newNotes)
                    interestSets.newNotes(newNotes)
                    ownedEmojiPacks.newNotes(newNotes)
                }
            }
        }

        scope.launch {
            cache.live.deletedEventBundles.collect { deletedNotes ->
                logTime("Account ${userProfile().toBestDisplayName()} deletedEventBundle Update with ${deletedNotes.size} new notes") {
                    newNotesPreProcessor.runDeleted(deletedNotes)
                    peopleLists.deletedNotes(deletedNotes)
                    followLists.deletedNotes(deletedNotes)
                    labeledBookmarkLists.deletedNotes(deletedNotes)
                    interestSets.deletedNotes(deletedNotes)
                    ownedEmojiPacks.deletedNotes(deletedNotes)
                }
            }
        }

        scope.launch(Dispatchers.IO) {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            settings.saveable.debounce(1000).collect {
                if (it.accountSettings != null) {
                    LocalPreferences.saveToEncryptedStorage(it.accountSettings)
                }
            }
        }
    }
}
