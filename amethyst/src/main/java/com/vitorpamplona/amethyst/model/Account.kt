/**
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
import com.vitorpamplona.amethyst.model.edits.PrivateStorageRelayListDecryptionCache
import com.vitorpamplona.amethyst.model.edits.PrivateStorageRelayListState
import com.vitorpamplona.amethyst.model.localRelays.LocalRelayListState
import com.vitorpamplona.amethyst.model.nip01UserMetadata.AccountHomeRelayState
import com.vitorpamplona.amethyst.model.nip01UserMetadata.AccountOutboxRelayState
import com.vitorpamplona.amethyst.model.nip01UserMetadata.NotificationInboxRelayState
import com.vitorpamplona.amethyst.model.nip01UserMetadata.UserMetadataState
import com.vitorpamplona.amethyst.model.nip02FollowLists.DeclaredFollowsPerOutboxRelay
import com.vitorpamplona.amethyst.model.nip02FollowLists.FollowListOutboxOrProxyRelays
import com.vitorpamplona.amethyst.model.nip02FollowLists.FollowListReusedOutboxOrProxyRelays
import com.vitorpamplona.amethyst.model.nip02FollowLists.FollowsPerOutboxRelay
import com.vitorpamplona.amethyst.model.nip02FollowLists.Kind3FollowListState
import com.vitorpamplona.amethyst.model.nip03Timestamp.OtsState
import com.vitorpamplona.amethyst.model.nip17Dms.DmInboxRelayState
import com.vitorpamplona.amethyst.model.nip17Dms.DmRelayListState
import com.vitorpamplona.amethyst.model.nip47WalletConnect.NwcSignerState
import com.vitorpamplona.amethyst.model.nip51Lists.BookmarkListState
import com.vitorpamplona.amethyst.model.nip51Lists.HiddenUsersState
import com.vitorpamplona.amethyst.model.nip51Lists.blockPeopleList.BlockPeopleListState
import com.vitorpamplona.amethyst.model.nip51Lists.blockedRelays.BlockedRelayListDecryptionCache
import com.vitorpamplona.amethyst.model.nip51Lists.blockedRelays.BlockedRelayListState
import com.vitorpamplona.amethyst.model.nip51Lists.broadcastRelays.BroadcastRelayListDecryptionCache
import com.vitorpamplona.amethyst.model.nip51Lists.broadcastRelays.BroadcastRelayListState
import com.vitorpamplona.amethyst.model.nip51Lists.geohashLists.GeohashListDecryptionCache
import com.vitorpamplona.amethyst.model.nip51Lists.geohashLists.GeohashListState
import com.vitorpamplona.amethyst.model.nip51Lists.hashtagLists.HashtagListDecryptionCache
import com.vitorpamplona.amethyst.model.nip51Lists.hashtagLists.HashtagListState
import com.vitorpamplona.amethyst.model.nip51Lists.indexerRelays.IndexerRelayListDecryptionCache
import com.vitorpamplona.amethyst.model.nip51Lists.indexerRelays.IndexerRelayListState
import com.vitorpamplona.amethyst.model.nip51Lists.labeledBookmarkLists.LabeledBookmarkListsState
import com.vitorpamplona.amethyst.model.nip51Lists.muteList.MuteListDecryptionCache
import com.vitorpamplona.amethyst.model.nip51Lists.muteList.MuteListState
import com.vitorpamplona.amethyst.model.nip51Lists.peopleList.FollowListsState
import com.vitorpamplona.amethyst.model.nip51Lists.peopleList.PeopleListDecryptionCache
import com.vitorpamplona.amethyst.model.nip51Lists.peopleList.PeopleListsState
import com.vitorpamplona.amethyst.model.nip51Lists.proxyRelays.ProxyRelayListDecryptionCache
import com.vitorpamplona.amethyst.model.nip51Lists.proxyRelays.ProxyRelayListState
import com.vitorpamplona.amethyst.model.nip51Lists.searchRelays.SearchRelayListDecryptionCache
import com.vitorpamplona.amethyst.model.nip51Lists.searchRelays.SearchRelayListState
import com.vitorpamplona.amethyst.model.nip51Lists.trustedRelays.TrustedRelayListDecryptionCache
import com.vitorpamplona.amethyst.model.nip51Lists.trustedRelays.TrustedRelayListState
import com.vitorpamplona.amethyst.model.nip65RelayList.Nip65RelayListState
import com.vitorpamplona.amethyst.model.nip72Communities.CommunityListDecryptionCache
import com.vitorpamplona.amethyst.model.nip72Communities.CommunityListState
import com.vitorpamplona.amethyst.model.nip78AppSpecific.AppSpecificState
import com.vitorpamplona.amethyst.model.nip96FileStorage.FileStorageServerListState
import com.vitorpamplona.amethyst.model.nipB7Blossom.BlossomServerListState
import com.vitorpamplona.amethyst.model.serverList.MergedFollowListsState
import com.vitorpamplona.amethyst.model.serverList.MergedFollowPlusMineRelayListsState
import com.vitorpamplona.amethyst.model.serverList.MergedFollowPlusMineWithIndexRelayListsState
import com.vitorpamplona.amethyst.model.serverList.MergedFollowPlusMineWithSearchRelayListsState
import com.vitorpamplona.amethyst.model.serverList.MergedServerListState
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
import com.vitorpamplona.quartz.experimental.profileGallery.ProfileGalleryEntryEvent
import com.vitorpamplona.quartz.experimental.profileGallery.blurhash
import com.vitorpamplona.quartz.experimental.profileGallery.dimension
import com.vitorpamplona.quartz.experimental.profileGallery.fromEvent
import com.vitorpamplona.quartz.experimental.profileGallery.hash
import com.vitorpamplona.quartz.experimental.profileGallery.mimeType
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
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.downloadFirstEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUserIds
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip03Timestamp.OtsResolverBuilder
import com.vitorpamplona.quartz.nip04Dm.PrivateDMCache
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip17Dm.NIP17Factory
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
import com.vitorpamplona.quartz.nip47WalletConnect.Response
import com.vitorpamplona.quartz.nip56Reports.ReportType
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapCache
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplits
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiser
import com.vitorpamplona.quartz.nip59Giftwrap.WrappedEvent
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
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
import com.vitorpamplona.quartz.nip90Dvms.NIP90ContentDiscoveryRequestEvent
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
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.BaseVoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceReplyEvent
import com.vitorpamplona.quartz.utils.DualCase
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.containsAny
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

@OptIn(DelicateCoroutinesApi::class)
@Stable
class Account(
    val settings: AccountSettings = AccountSettings(KeyPair()),
    val signer: NostrSigner,
    val geolocationFlow: StateFlow<LocationState.LocationResult>,
    val nwcFilterAssembler: NWCPaymentFilterAssembler,
    val otsResolverBuilder: OtsResolverBuilder,
    val cache: LocalCache,
    val client: INostrClient,
    val scope: CoroutineScope,
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

    val dmRelayList = DmRelayListState(signer, cache, scope, settings)

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
    val bookmarkState = BookmarkListState(signer, cache, scope)
    val emoji = EmojiPackState(signer, cache, scope)

    val appSpecific = AppSpecificState(signer, cache, scope, settings)

    val blossomServers = BlossomServerListState(signer, cache, scope, settings)
    val fileStorageServers = FileStorageServerListState(signer, cache, scope, settings)
    val serverLists = MergedServerListState(fileStorageServers, blossomServers, scope)

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
    val declaredFollowsPerRelay = DeclaredFollowsPerOutboxRelay(kind3FollowList, cache, scope).flow

    // keeps a cache of the outbox relays for each author
    val followsPerRelay = FollowsPerOutboxRelay(kind3FollowList, blockedRelayList, proxyRelayList, cache, scope).flow

    // Merges all follow lists to create a single All Follows feed.
    val allFollows = MergedFollowListsState(kind3FollowList, peopleLists, followLists, hashtagList, geohashList, communityList, scope)

    val privateDMDecryptionCache = PrivateDMCache(signer)
    override val privateZapsDecryptionCache = PrivateZapCache(signer)
    val draftsDecryptionCache = DraftEventCache(signer)

    val chatroomList = cache.getOrCreateChatroomList(signer.pubKey)

    val newNotesPreProcessor = EventProcessor(this, cache)

    val otsState = OtsState(signer, cache, otsResolverBuilder, scope, settings)

    val feedDecryptionCaches =
        FeedDecryptionCaches(
            peopleListCache = peopleListDecryptionCache,
            muteListCache = muteListDecryptionCache,
            communityListCache = communityListDecryptionCache,
            hashtagCache = hashtagListDecryptionCache,
            geohashCache = geohashListDecryptionCache,
        )

    // App-ready Feeds
    val liveHomeFollowLists: StateFlow<IFeedTopNavFilter> =
        FeedTopNavFilterState(
            feedFilterListName = settings.defaultHomeFollowList,
            kind3Follows = kind3FollowList.flow,
            allFollows = allFollows.flow,
            locationFlow = geolocationFlow,
            followsRelays = defaultGlobalRelays.flow,
            blockedRelays = blockedRelayList.flow,
            proxyRelays = proxyRelayList.flow,
            caches = feedDecryptionCaches,
            signer = signer,
            scope = scope,
        ).flow

    val liveHomeFollowListsPerRelay = OutboxLoaderState(liveHomeFollowLists, cache, scope).flow

    val liveStoriesFollowLists: StateFlow<IFeedTopNavFilter> =
        FeedTopNavFilterState(
            feedFilterListName = settings.defaultStoriesFollowList,
            kind3Follows = kind3FollowList.flow,
            allFollows = allFollows.flow,
            locationFlow = geolocationFlow,
            followsRelays = defaultGlobalRelays.flow,
            blockedRelays = blockedRelayList.flow,
            proxyRelays = proxyRelayList.flow,
            caches = feedDecryptionCaches,
            signer = signer,
            scope = scope,
        ).flow

    val liveStoriesFollowListsPerRelay = OutboxLoaderState(liveStoriesFollowLists, cache, scope).flow

    val liveDiscoveryFollowLists: StateFlow<IFeedTopNavFilter> =
        FeedTopNavFilterState(
            feedFilterListName = settings.defaultDiscoveryFollowList,
            kind3Follows = kind3FollowList.flow,
            allFollows = allFollows.flow,
            locationFlow = geolocationFlow,
            followsRelays = defaultGlobalRelays.flow,
            blockedRelays = blockedRelayList.flow,
            proxyRelays = proxyRelayList.flow,
            caches = feedDecryptionCaches,
            signer = signer,
            scope = scope,
        ).flow

    val liveDiscoveryFollowListsPerRelay = OutboxLoaderState(liveDiscoveryFollowLists, cache, scope).flow

    val liveNotificationFollowLists: StateFlow<IFeedTopNavFilter> =
        FeedTopNavFilterState(
            feedFilterListName = settings.defaultNotificationFollowList,
            kind3Follows = kind3FollowList.flow,
            allFollows = allFollows.flow,
            locationFlow = geolocationFlow,
            followsRelays = defaultGlobalRelays.flow,
            blockedRelays = blockedRelayList.flow,
            proxyRelays = proxyRelayList.flow,
            caches = feedDecryptionCaches,
            signer = signer,
            scope = scope,
        ).flow

    val liveNotificationFollowListsPerRelay = OutboxLoaderState(liveNotificationFollowLists, cache, scope).flow

    override fun isWriteable(): Boolean = settings.isWriteable()

    suspend fun updateWarnReports(warnReports: Boolean): Boolean {
        if (settings.updateWarnReports(warnReports)) {
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

    suspend fun changeReactionTypes(reactionSet: List<String>) {
        if (settings.changeReactionTypes(reactionSet)) {
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

    suspend fun updateTranslateTo(languageCode: Locale) {
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

    suspend fun sendZapPaymentRequestFor(
        bolt11: String,
        zappedNote: Note?,
        onResponse: (Response?) -> Unit,
    ) {
        val (event, relay) = nip47SignerState.sendZapPaymentRequestFor(bolt11, zappedNote, onResponse)
        client.send(event, setOf(relay))
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

                client.send(deletionEvent, myRelayList)
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
        client.send(deletionEvent, outboxRelays.flow.value + additionalRelays)
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
            client.send(event, computeMyReactionToNote(note, event))
            cache.justConsumeMyOwnEvent(event)
        }
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
                                ?: replyToAuthor.relaysBeingUsed.keys.ifEmpty { null }
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
                ?: (cache.relayHints.hintsForKey(user.pubkeyHex).toSet() + user.relaysBeingUsed.keys)
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
                if (relayList != null) {
                    client.send(event, relayList.toSet())
                } else {
                    val publicRelayList = computeRelayListForLinkedUser(receiver)
                    client.send(event, publicRelayList)
                }
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
                        ?: author.relaysBeingUsed.keys.ifEmpty { null }
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
                        .downloadFirstEvent(
                            filters =
                                note.relays.associateWith { relay ->
                                    listOf(
                                        Filter(
                                            ids = listOf(host.id),
                                        ),
                                    )
                                },
                        )?.let { downloadedEvent ->
                            client.send(downloadedEvent, computeRelayListToBroadcast(downloadedEvent))
                        }
                }
            } else {
                client.send(noteEvent, computeRelayListToBroadcast(note))
            }
        }
    }

    fun upgradeAttestations() = otsState.upgradeAttestationsIfNeeded(::sendAutomatic)

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
        client.send(signedEvent, relays)
    }

    fun sendAutomatic(events: List<Event>) = events.forEach { sendAutomatic(it) }

    fun sendAutomatic(event: Event?) {
        if (event == null) return
        cache.justConsumeMyOwnEvent(event)
        client.send(event, computeRelayListToBroadcast(event))
    }

    fun sendMyPublicAndPrivateOutbox(event: Event?) {
        if (event == null) return
        cache.justConsumeMyOwnEvent(event)
        client.send(event, outboxRelays.flow.value)
    }

    fun sendMyPublicAndPrivateOutbox(events: List<Event>) {
        events.forEach {
            client.send(it, outboxRelays.flow.value)
            cache.justConsumeMyOwnEvent(it)
        }
    }

    fun sendLiterallyEverywhere(event: Event) {
        client.send(event, followPlusAllMineWithIndex.flow.value + client.availableRelaysFlow().value)
        cache.justConsumeMyOwnEvent(event)
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

        client.send(data, relayList = relayList)
        cache.justConsumeMyOwnEvent(data)

        client.send(signedEvent, relayList = relayList)
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
        client.send(data, relayList = relayList)
        client.send(signedEvent, relayList = relayList)
    }

    fun sendHeader(
        signedEvent: Event,
        relayList: Set<NormalizedRelayUrl>,
        onReady: (Note) -> Unit,
    ) {
        client.send(signedEvent, relayList = relayList)
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
                    it.key,
                    it.value.mimeType,
                    it.value.blurHash?.blurhash,
                    it.value.dim,
                    caption,
                    it.value.hash,
                    it.value.size,
                    null,
                    emptyList(),
                    emptyList(),
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
                        url,
                        headerInfo.mimeType,
                        headerInfo.blurHash?.blurhash,
                        headerInfo.dim,
                        headerInfo.hash,
                        headerInfo.size,
                        alt,
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
        client.send(event, relayList)
    }

    suspend fun <T : Event> signAndSendPrivatelyOrBroadcast(
        template: EventTemplate<T>,
        relayList: (T) -> List<NormalizedRelayUrl>?,
    ): T {
        val event = signer.sign(template)
        cache.justConsumeMyOwnEvent(event)
        val relays = relayList(event)
        if (relays != null && relays.isNotEmpty()) {
            client.send(event, relays.toSet())
        } else {
            client.send(event, computeRelayListToBroadcast(event))
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

        client.send(event, relayList)

        broadcast.forEach { client.send(it, relayList) }

        return event
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
            client.send(draftEvent, relayList)
            broadcast.forEach {
                client.send(it, relayList.toSet())
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
            client.send(deletedDraft, relayList)
            client.send(deletionEvent, relayList)
        }
    }

    suspend fun createInteractiveStoryReadingState(
        root: InteractiveStoryBaseEvent,
        rootRelay: NormalizedRelayUrl?,
        readingScene: InteractiveStoryBaseEvent,
        readingSceneRelay: NormalizedRelayUrl?,
    ) {
        if (!isWriteable()) return

        val template =
            InteractiveStoryReadingStateEvent.build(
                root = root,
                rootRelay = rootRelay,
                currentScene = readingScene,
                currentSceneRelay = readingSceneRelay,
            )

        val event = signer.sign(template)

        // updates relays that already have this replaceable.
        val noteRelays = cache.getAddressableNoteIfExists(event.address())?.relays ?: emptyList()

        val relayList = privateStorageRelayList.flow.value + localRelayList.flow.value
        if (relayList.isNotEmpty()) {
            client.send(event, relayList + noteRelays)
        } else {
            client.send(event, outboxRelays.flow.value + noteRelays)
        }
        cache.justConsumeMyOwnEvent(event)
    }

    suspend fun updateInteractiveStoryReadingState(
        readingState: InteractiveStoryReadingStateEvent,
        readingScene: InteractiveStoryBaseEvent,
        readingSceneRelay: NormalizedRelayUrl?,
    ) {
        if (!isWriteable()) return

        val template =
            InteractiveStoryReadingStateEvent.update(
                base = readingState,
                currentScene = readingScene,
                currentSceneRelay = readingSceneRelay,
            )

        val event = signer.sign(template)

        // updates relays that already have this replaceable.
        val noteRelays = cache.getAddressableNoteIfExists(event.address())?.relays ?: emptyList()

        val relayList = privateStorageRelayList.flow.value + localRelayList.flow.value
        if (relayList.isNotEmpty()) {
            client.send(event, relayList + noteRelays)
        } else {
            client.send(event, outboxRelays.flow.value + noteRelays)
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
            client.send(it, relayList = relayList)

            mapEntitiesToNotes(quotes).forEach { it.event?.let { client.send(it, relayList = relayList) } }
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
            client.send(it, relayList = relayList)

            broadcastNotes.forEach { it.event?.let { client.send(it, relayList = relayList) } }
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

        client.send(newEvent, relayList = relays)
        client.send(bountyEvent, relayList = relays)
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

        client.send(event, relayList = relayList)

        broadcast.forEach { client.send(it, relayList) }
    }

    suspend fun sendNip04PrivateMessage(eventTemplate: EventTemplate<PrivateDmEvent>) {
        if (!isWriteable()) return

        val newEvent = signer.sign(eventTemplate)
        val recipient = newEvent.verifiedRecipientPubKey()
        val destinationRelays = recipient?.let { cache.getOrCreateUser(it).dmInboxRelays() } ?: emptyList()

        cache.justConsumeMyOwnEvent(newEvent)
        client.send(newEvent, outboxRelays.flow.value + destinationRelays)
    }

    suspend fun sendNip17EncryptedFile(template: EventTemplate<ChatMessageEncryptedFileHeaderEvent>) {
        if (!isWriteable()) return

        val wraps = NIP17Factory().createEncryptedFileNIP17(template, signer)
        broadcastPrivately(wraps)
    }

    suspend fun sendNip17PrivateMessage(template: EventTemplate<ChatMessageEvent>) {
        val events = NIP17Factory().createMessageNIP17(template, signer)
        broadcastPrivately(events)
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
            client.send(wrap, relayList)
        }
    }

    suspend fun createStatus(newStatus: String) = sendMyPublicAndPrivateOutbox(UserStatusAction.create(newStatus, signer))

    suspend fun updateStatus(
        oldStatus: AddressableNote,
        newStatus: String,
    ) = sendMyPublicAndPrivateOutbox(UserStatusAction.update(oldStatus, newStatus, signer))

    suspend fun deleteStatus(oldStatus: AddressableNote) = sendMyPublicAndPrivateOutbox(UserStatusAction.delete(oldStatus, signer))

    suspend fun removeEmojiPack(emojiPack: Note) = sendMyPublicAndPrivateOutbox(emoji.removeEmojiPack(emojiPack))

    suspend fun addEmojiPack(emojiPack: Note) = sendMyPublicAndPrivateOutbox(emoji.addEmojiPack(emojiPack))

    suspend fun addToGallery(
        idHex: HexKey,
        url: String,
        relay: NormalizedRelayUrl?,
        blurhash: String?,
        dim: DimensionTag?,
        hash: String?,
        mimeType: String?,
    ) {
        val template =
            ProfileGalleryEntryEvent.build(url) {
                fromEvent(idHex, relay)
                hash?.let { hash(hash) }
                mimeType?.let { mimeType(it) }
                dim?.let { dimension(it) }
                blurhash?.let { blurhash(it) }
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
        onReady: (event: NIP90ContentDiscoveryRequestEvent) -> Unit,
    ) {
        val relays = nip65RelayList.inboxFlow.value.toSet()
        val request = NIP90ContentDiscoveryRequestEvent.create(dvmPublicKey.pubkeyHex, signer.pubKey, relays, signer)

        val relayList =
            dvmPublicKey.inboxRelays()?.toSet()?.ifEmpty { null }
                ?: (dvmPublicKey.relaysBeingUsed.keys + cache.relayHints.hintsForKey(dvmPublicKey.pubkeyHex))

        cache.justConsumeMyOwnEvent(request)
        onReady(request)
        delay(100)
        client.send(request, relayList)
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

    fun isAcceptable(note: Note): Boolean {
        return note.author?.let { isAcceptable(it) } ?: true &&
            // if user hasn't hided this author
            isAcceptableDirect(note) &&
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
                note.replyTo?.map { getRelevantReports(it) }?.flatten() ?: emptyList()
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

    suspend fun savePrivateOutboxRelayList(relays: List<NormalizedRelayUrl>) = sendMyPublicAndPrivateOutbox(privateStorageRelayList.saveRelayList(relays))

    suspend fun saveSearchRelayList(searchRelays: List<NormalizedRelayUrl>) = sendMyPublicAndPrivateOutbox(searchRelayList.saveRelayList(searchRelays))

    suspend fun saveIndexerRelayList(trustedRelays: List<NormalizedRelayUrl>) = sendMyPublicAndPrivateOutbox(indexerRelayList.saveRelayList(trustedRelays))

    suspend fun saveBroadcastRelayList(trustedRelays: List<NormalizedRelayUrl>) = sendMyPublicAndPrivateOutbox(broadcastRelayList.saveRelayList(trustedRelays))

    suspend fun saveProxyRelayList(trustedRelays: List<NormalizedRelayUrl>) = sendMyPublicAndPrivateOutbox(proxyRelayList.saveRelayList(trustedRelays))

    suspend fun saveTrustedRelayList(trustedRelays: List<NormalizedRelayUrl>) = sendMyPublicAndPrivateOutbox(trustedRelayList.saveRelayList(trustedRelays))

    suspend fun saveBlockedRelayList(blockedRelays: List<NormalizedRelayUrl>) = sendMyPublicAndPrivateOutbox(blockedRelayList.saveRelayList(blockedRelays))

    suspend fun sendNip65RelayList(relays: List<AdvertisedRelayInfo>) = sendLiterallyEverywhere(nip65RelayList.saveRelayList(relays))

    suspend fun sendFileServersList(servers: List<String>) = sendMyPublicAndPrivateOutbox(fileStorageServers.saveFileServersList(servers))

    suspend fun sendBlossomServersList(servers: List<String>) = sendMyPublicAndPrivateOutbox(blossomServers.saveBlossomServersList(servers))

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

    init {
        Log.d("AccountRegisterObservers", "Init")

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
                }
            }
        }

        scope.launch(Dispatchers.IO) {
            settings.saveable.debounce(1000).collect {
                if (it.accountSettings != null) {
                    LocalPreferences.saveToEncryptedStorage(it.accountSettings)
                }
            }
        }
    }
}
