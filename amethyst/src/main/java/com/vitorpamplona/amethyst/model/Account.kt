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
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.commons.marmot.MarmotManager
import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatListDecryptionCache
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatListState
import com.vitorpamplona.amethyst.commons.model.nip18Reposts.RepostAction
import com.vitorpamplona.amethyst.commons.model.nip25Reactions.ReactionAction
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatListDecryptionCache
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatListState
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.UserStatusAction
import com.vitorpamplona.amethyst.commons.model.nip56Reports.ReportAction
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.logTime
import com.vitorpamplona.amethyst.model.algoFeeds.FavoriteAlgoFeedsOrchestrator
import com.vitorpamplona.amethyst.model.edits.PrivateStorageRelayListDecryptionCache
import com.vitorpamplona.amethyst.model.edits.PrivateStorageRelayListState
import com.vitorpamplona.amethyst.model.localRelays.ForwardKind0ToLocalRelayState
import com.vitorpamplona.amethyst.model.localRelays.LocalRelayListState
import com.vitorpamplona.amethyst.model.marmot.KeyPackageRelayListState
import com.vitorpamplona.amethyst.model.nip01UserMetadata.AccountHomeRelayState
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
import com.vitorpamplona.amethyst.model.nip47WalletConnect.NwcSignerState
import com.vitorpamplona.amethyst.model.nip51Lists.BookmarkListState
import com.vitorpamplona.amethyst.model.nip51Lists.HiddenUsersState
import com.vitorpamplona.amethyst.model.nip51Lists.OldBookmarkListState
import com.vitorpamplona.amethyst.model.nip51Lists.PinListState
import com.vitorpamplona.amethyst.model.nip51Lists.blockPeopleList.BlockPeopleListState
import com.vitorpamplona.amethyst.model.nip51Lists.blockedRelays.BlockedRelayListDecryptionCache
import com.vitorpamplona.amethyst.model.nip51Lists.blockedRelays.BlockedRelayListState
import com.vitorpamplona.amethyst.model.nip51Lists.broadcastRelays.BroadcastRelayListDecryptionCache
import com.vitorpamplona.amethyst.model.nip51Lists.broadcastRelays.BroadcastRelayListState
import com.vitorpamplona.amethyst.model.nip51Lists.favoriteAlgoFeedsLists.FavoriteAlgoFeedsListDecryptionCache
import com.vitorpamplona.amethyst.model.nip51Lists.favoriteAlgoFeedsLists.FavoriteAlgoFeedsListState
import com.vitorpamplona.amethyst.model.nip51Lists.geohashLists.GeohashListDecryptionCache
import com.vitorpamplona.amethyst.model.nip51Lists.geohashLists.GeohashListState
import com.vitorpamplona.amethyst.model.nip51Lists.hashtagLists.HashtagListDecryptionCache
import com.vitorpamplona.amethyst.model.nip51Lists.hashtagLists.HashtagListState
import com.vitorpamplona.amethyst.model.nip51Lists.indexerRelays.IndexerRelayListDecryptionCache
import com.vitorpamplona.amethyst.model.nip51Lists.indexerRelays.IndexerRelayListState
import com.vitorpamplona.amethyst.model.nip51Lists.interestSets.InterestSetsState
import com.vitorpamplona.amethyst.model.nip51Lists.labeledBookmarkLists.LabeledBookmarkListsState
import com.vitorpamplona.amethyst.model.nip51Lists.muteList.MuteListDecryptionCache
import com.vitorpamplona.amethyst.model.nip51Lists.muteList.MuteListState
import com.vitorpamplona.amethyst.model.nip51Lists.peopleList.FollowListsState
import com.vitorpamplona.amethyst.model.nip51Lists.peopleList.PeopleListDecryptionCache
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
import com.vitorpamplona.amethyst.model.nip72Communities.CommunityListDecryptionCache
import com.vitorpamplona.amethyst.model.nip72Communities.CommunityListState
import com.vitorpamplona.amethyst.model.nip78AppSpecific.AppSpecificState
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
import com.vitorpamplona.amethyst.model.trustedAssertions.TrustProviderListDecryptionCache
import com.vitorpamplona.amethyst.model.trustedAssertions.TrustProviderListState
import com.vitorpamplona.amethyst.service.location.LocationState
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.nwc.NWCPaymentFilterAssembler
import com.vitorpamplona.amethyst.service.uploads.FileHeader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.EventProcessor
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
import com.vitorpamplona.quartz.experimental.profileGallery.mimeType
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupStateStore
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchFirst
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.countHashtags
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
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
import com.vitorpamplona.quartz.nip17Dm.NIP17Factory
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
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning
import com.vitorpamplona.quartz.nip37Drafts.DraftEventCache
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Request
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Response
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark
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
import com.vitorpamplona.quartz.nip59Giftwrap.WrappedEvent
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.EphemeralGiftWrapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
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
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
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
import com.vitorpamplona.quartz.utils.DualCase
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import kotlin.coroutines.cancellation.CancellationException
import com.vitorpamplona.quartz.experimental.nip95.header.thumbhash as nip95thumbhash
import com.vitorpamplona.quartz.experimental.profileGallery.thumbhash as galleryThumbhash

@OptIn(DelicateCoroutinesApi::class)
@Stable
class Account(
    val settings: AccountSettings = AccountSettings(KeyPair()),
    override val signer: NostrSigner,
    val geolocationFlow: () -> StateFlow<LocationState.LocationResult>,
    val nwcFilterAssembler: () -> NWCPaymentFilterAssembler,
    val otsResolverBuilder: () -> OtsResolver,
    val cache: LocalCache,
    val client: INostrClient,
    val scope: CoroutineScope,
    val mlsGroupStateStore: MlsGroupStateStore? = null,
    val marmotMessageStore: com.vitorpamplona.quartz.marmot.mls.group.MarmotMessageStore? = null,
    val marmotKeyPackageStore: com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageBundleStore? = null,
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

    override val nip47SignerState = NwcSignerState(signer, nwcFilterAssembler, cache, scope, settings)

    val nip65RelayList = Nip65RelayListState(signer, cache, scope, settings)
    val localRelayList = LocalRelayListState(signer, cache, scope, settings)

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
    val oldBookmarkState = OldBookmarkListState(signer, cache, scope)
    val bookmarkState = BookmarkListState(signer, cache, scope)
    val pinState = PinListState(signer, cache, scope)
    val emoji = EmojiPackState(signer, cache, scope)
    val ownedEmojiPacks = OwnedEmojiPacksState(signer, cache, scope)

    val vanish = VanishRequestsState(signer, cache, client, scope)

    val appSpecific = AppSpecificState(signer, cache, scope, settings)

    val blossomServers = BlossomServerListState(signer, cache, scope, settings)

    // Relay settings
    val homeRelays = AccountHomeRelayState(nip65RelayList, privateStorageRelayList, localRelayList, scope)
    val outboxRelays = AccountOutboxRelayState(nip65RelayList, privateStorageRelayList, localRelayList, broadcastRelayList, scope)
    val dmRelays = DmInboxRelayState(dmRelayList, nip65RelayList, privateStorageRelayList, localRelayList, scope)
    val notificationRelays = NotificationInboxRelayState(nip65RelayList, localRelayList, scope)

    val trustedRelays = TrustedRelayListsState(nip65RelayList, privateStorageRelayList, localRelayList, dmRelayList, searchRelayList, trustedRelayList, broadcastRelayList, scope)

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

    val liveProductsFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultProductsFollowList)
    val liveProductsFollowListsPerRelay = OutboxLoaderState(liveProductsFollowLists, cache, scope).flow

    val liveShortsFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultShortsFollowList)
    val liveShortsFollowListsPerRelay = OutboxLoaderState(liveShortsFollowLists, cache, scope).flow

    val livePublicChatsFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultPublicChatsFollowList)
    val livePublicChatsFollowListsPerRelay = OutboxLoaderState(livePublicChatsFollowLists, cache, scope).flow

    val liveLiveStreamsFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultLiveStreamsFollowList)
    val liveLiveStreamsFollowListsPerRelay = OutboxLoaderState(liveLiveStreamsFollowLists, cache, scope).flow

    val liveLongsFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultLongsFollowList)
    val liveLongsFollowListsPerRelay = OutboxLoaderState(liveLongsFollowLists, cache, scope).flow

    val liveArticlesFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultArticlesFollowList)
    val liveArticlesFollowListsPerRelay = OutboxLoaderState(liveArticlesFollowLists, cache, scope).flow

    val liveBadgesFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultBadgesFollowList)
    val liveBadgesFollowListsPerRelay = OutboxLoaderState(liveBadgesFollowLists, cache, scope).flow

    val liveBrowseEmojiSetsFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultBrowseEmojiSetsFollowList)
    val liveBrowseEmojiSetsFollowListsPerRelay = OutboxLoaderState(liveBrowseEmojiSetsFollowLists, cache, scope).flow

    val liveCommunitiesFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultCommunitiesFollowList)
    val liveCommunitiesFollowListsPerRelay = OutboxLoaderState(liveCommunitiesFollowLists, cache, scope).flow

    val liveFollowPacksFollowLists: StateFlow<IFeedTopNavFilter> = topNavFilterFlow(settings.defaultFollowPacksFollowList)
    val liveFollowPacksFollowListsPerRelay = OutboxLoaderState(liveFollowPacksFollowLists, cache, scope).flow

    override fun isWriteable(): Boolean = settings.isWriteable()

    suspend fun updateWarnReports(warnReports: Boolean): Boolean {
        if (settings.updateWarnReports(warnReports)) {
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

    suspend fun reactTo(
        note: Note,
        reaction: String,
    ) = ReactionAction.reactTo(
        note = note,
        reaction = reaction,
        by = userProfile(),
        signer = signer,
        onPublic = ::sendAutomatic,
        onPrivate = ::broadcastPrivately,
    )

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

        // For NIP-17 private groups, we don't support tracked mode (too complex)
        if (eventHint.event is NIP17Group) return null

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

    suspend fun createZapRequestFor(
        event: Event,
        pollOption: Int?,
        message: String = "",
        zapType: LnZapEvent.ZapType,
        toUser: User?,
        additionalRelays: Set<NormalizedRelayUrl>? = null,
    ) = LnZapRequestEvent.create(
        zappedEvent = event,
        relays = nip65RelayList.inboxFlow.value + (additionalRelays ?: emptySet()),
        signer = signer,
        pollOption = pollOption,
        message = message,
        zapType = zapType,
        toUserPubHex = toUser?.pubkeyHex,
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
    ) {
        val (event, relay) = nip47SignerState.sendNwcRequestToWallet(walletUri, request, onResponse)
        client.publish(event, setOf(relay))
    }

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
    ): LnZapRequestEvent {
        val zapRequest =
            LnZapRequestEvent.create(
                userHex = user.pubkeyHex,
                relays = nip65RelayList.inboxFlow.value + (user.inboxRelays() ?: emptyList()),
                signer = signer,
                message = message,
                zapType = zapType,
            )

        cache.justConsumeMyOwnEvent(zapRequest)
        return zapRequest
    }

    suspend fun report(
        note: Note,
        type: ReportType,
        content: String = "",
    ) = sendMyPublicAndPrivateOutbox(ReportAction.report(note, type, content, userProfile(), signer))

    suspend fun report(
        user: User,
        type: ReportType,
        content: String = "",
    ) = sendMyPublicAndPrivateOutbox(ReportAction.report(user, type, content, userProfile(), signer))

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
    ) = blossomServers.createBlossomUploadAuth(hash, size, alt)

    suspend fun createBlossomDeleteAuth(
        hash: HexKey,
        alt: String,
    ) = blossomServers.createBlossomDeleteAuth(hash, alt)

    suspend fun boost(note: Note) {
        RepostAction.repost(note, signer)?.let { event ->
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

    fun computeRelayListToBroadcast(event: Event): Set<NormalizedRelayUrl> {
        if (event is MetadataEvent || event is AdvertisedRelayListEvent) {
            // everywhere
            return followPlusAllMineWithIndex.flow.value + client.availableRelaysFlow().value
        }
        if (event is GiftWrapEvent) {
            val receiver = event.recipientPubKey()
            if (receiver != null) {
                val relayList =
                    cache
                        .getOrCreateUser(receiver)
                        .dmInboxRelayList()
                        ?.relays()
                        ?.ifEmpty { null }
                return relayList?.toSet() ?: computeRelayListForLinkedUser(receiver)
            } else {
                return emptySet()
            }
        }
        if (event is WrappedEvent) {
            return emptySet()
        }

        val relayList = mutableSetOf<NormalizedRelayUrl>()

        val author = cache.getUserIfExists(event.pubKey)

        if (author != null) {
            if (author == userProfile()) {
                relayList.addAll(outboxRelays.flow.value)
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
                        relayList.addAll(computeRelaysForChannels(linkedEvent))
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
                        relayList.addAll(computeRelaysForChannels(linkedEvent))
                    }
                }
            }
        }

        if (event is PollEvent) {
            relayList.addAll(event.relays())
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
            if (noteEvent is WrappedEvent && noteEvent.host != null) {
                // download the event and send it.
                noteEvent.host?.let { host ->
                    client
                        .fetchFirst(
                            filters =
                                note.relays.associateWith { relay ->
                                    listOf(
                                        Filter(
                                            kinds = listOf(host.kind),
                                            tags = mapOf("p" to listOf(pubKey)),
                                            ids = listOf(host.id),
                                        ),
                                    )
                                },
                        )?.let { downloadedEvent ->
                            val toRelays = computeRelayListToBroadcast(downloadedEvent)
                            client.publish(downloadedEvent, toRelays)
                        }
                }
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

    suspend fun <T : Event> signAndSendPrivatelyOrBroadcast(
        template: EventTemplate<T>,
        relayList: (T) -> List<NormalizedRelayUrl>?,
    ): T {
        val event = signer.sign(template)
        cache.justConsumeMyOwnEvent(event)
        val relays = relayList(event)
        if (!relays.isNullOrEmpty()) {
            client.publish(event, relays.toSet())
        } else {
            client.publish(event, computeRelayListToBroadcast(event))
        }
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
    ): T {
        val anonymousSigner = NostrSignerInternal(KeyPair())
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

    suspend fun deleteDraftIgnoreErrors(draftTag: String) {
        try {
            deleteDraftInner(draftTag)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    suspend fun deleteDraftInner(draftTag: String) {
        if (!isWriteable()) return

        val extraRelays = cache.getAddressableNoteIfExists(DraftWrapEvent.createAddressTag(signer.pubKey, draftTag))?.relays ?: emptyList()

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
    }

    override suspend fun sendNip17EncryptedFile(template: EventTemplate<ChatMessageEncryptedFileHeaderEvent>) {
        if (!isWriteable()) return

        val wraps = NIP17Factory().createEncryptedFileNIP17(template, signer)
        broadcastPrivately(wraps)
    }

    override suspend fun sendNip17PrivateMessage(template: EventTemplate<ChatMessageEvent>) {
        val events = NIP17Factory().createMessageNIP17(template, signer)
        broadcastPrivately(events)
    }

    override suspend fun sendGiftWraps(wraps: List<GiftWrapEvent>) {
        wraps.forEach { wrap ->
            val relayList = computeRelayListToBroadcast(wrap)
            client.publish(wrap, relayList)
        }
    }

    suspend fun broadcastPrivately(signedEvents: NIP17Factory.Result) {
        val mine = signedEvents.wraps.filter { (it.recipientPubKey() == signer.pubKey) }

        mine.forEach { giftWrap ->
            cache.justConsumeMyOwnEvent(giftWrap)
        }

        val id = mine.firstOrNull()?.id
        val mineNote = if (id == null) null else cache.getNoteIfExists(id)

        signedEvents.wraps.forEach { wrap ->
            // Creates an alias
            if (mineNote != null && wrap.recipientPubKey() != signer.pubKey) {
                cache.getOrAddAliasNote(wrap.id, mineNote)
            }

            val relayList = computeRelayListToBroadcast(wrap)
            client.publish(wrap, relayList)
        }
    }

    // --- Marmot Group Messaging ---

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
        client.publish(outbound.signedEvent, groupRelays)
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
    ) {
        val template =
            ProfileGalleryEntryEvent.build(url) {
                fromEvent(idHex, relay)
                hash?.let { hash(hash) }
                mimeType?.let { mimeType(it) }
                dim?.let { dimension(it) }
                blurhash?.let { blurhash(it) }
                thumbhash?.let { galleryThumbhash(it) }
            }

        val event = signer.sign(template)
        sendMyPublicAndPrivateOutbox(event)
    }

    suspend fun removeFromGallery(note: Note) {
        delete(note)
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

    suspend fun hideUser(pubkeyHex: HexKey) {
        sendMyPublicAndPrivateOutbox(muteList.hideUser(pubkeyHex))
    }

    suspend fun showUser(pubkeyHex: HexKey) {
        sendMyPublicAndPrivateOutbox(blockPeopleList.showUser(pubkeyHex))
        sendMyPublicAndPrivateOutbox(muteList.showUser(pubkeyHex))
        hiddenUsers.showUser(pubkeyHex)
    }

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
            if (event is PrivateDmEvent) {
                privateDMDecryptionCache.cachedDM(event)
            } else if (event is LnZapRequestEvent && event.isPrivateZap()) {
                privateZapsDecryptionCache.cachedPrivateZap(event)?.content
            } else if (event is DraftWrapEvent) {
                draftsDecryptionCache.preCachedDraft(event)?.content
            } else {
                event.content
            }
        } else {
            event.content
        }
    }

    suspend fun decryptContent(note: Note): String? {
        val event = note.event
        return if (event is PrivateDmEvent && isWriteable()) {
            privateDMDecryptionCache.decryptDM(event)
        } else if (event is LnZapRequestEvent && isWriteable()) {
            if (event.isPrivateZap()) {
                if (isWriteable()) {
                    privateZapsDecryptionCache.decryptPrivateZap(event)?.content
                } else {
                    null
                }
            } else {
                event.content
            }
        } else if (event is DraftWrapEvent && isWriteable()) {
            draftsDecryptionCache.cachedDraft(event)?.content
        } else {
            event?.content
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

        if (!settings.syncedSettings.security.warnAboutPostsWithReports) {
            if (isHidden(user)) return false

            val reports = user.reportsOrNull() ?: return true

            return reports.reportsBy(userProfile()).isEmpty() // if user has not reported this post
        }

        if (isHidden(user)) return false

        val reports = user.reportsOrNull() ?: return true

        // if user hasn't hided this author
        return reports.reportsBy(userProfile()).isEmpty() &&
            // if user has not reported this post
            reports.countReportAuthorsBy(followingKeySet()) < 5
    }

    private fun isAcceptableDirect(note: Note): Boolean {
        if (!settings.syncedSettings.security.warnAboutPostsWithReports) {
            return !note.hasReportsBy(userProfile())
        }
        return !note.hasReportsBy(userProfile()) &&
            // if user has not reported this post
            note.countReportAuthorsBy(followingKeySet()) < 5 // if it has 5 reports by reliable users
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

    private fun hasExcessiveHashtags(note: Note): Boolean {
        val limit = settings.syncedSettings.security.maxHashtagLimit.value
        return limit > 0 && (note.event?.countHashtags() ?: 0) > limit
    }

    override fun isAcceptable(note: Note): Boolean {
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
                                marmotGroupList.restoreMessage(groupId, innerNote)
                            } catch (e: Exception) {
                                Log.w(
                                    "Account",
                                    "Failed to restore persisted Marmot message for $groupId: ${e.message}",
                                )
                            }
                        }
                    }
                }
            }
        }

        scope.launch {
            cache.antiSpam.flowSpam.collect {
                it.cache.spamMessages.snapshot().values.forEach { spammer ->
                    if (!hiddenUsers.isHidden(spammer.pubkeyHex) && spammer.shouldHide()) {
                        if (spammer.pubkeyHex != userProfile().pubkeyHex && spammer.pubkeyHex !in followingKeySet()) {
                            hiddenUsers.hideUser(spammer.pubkeyHex)
                        }
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
