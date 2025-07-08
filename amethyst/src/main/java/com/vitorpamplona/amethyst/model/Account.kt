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

import android.util.Log
import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.model.edits.PrivateStorageRelayListState
import com.vitorpamplona.amethyst.model.emphChat.EphemeralChatListState
import com.vitorpamplona.amethyst.model.localRelays.LocalRelayListState
import com.vitorpamplona.amethyst.model.nip01UserMetadata.AccountOutboxRelayState
import com.vitorpamplona.amethyst.model.nip01UserMetadata.NotificationInboxRelayState
import com.vitorpamplona.amethyst.model.nip01UserMetadata.UserMetadataState
import com.vitorpamplona.amethyst.model.nip02FollowLists.FollowListOutboxRelays
import com.vitorpamplona.amethyst.model.nip02FollowLists.FollowListState
import com.vitorpamplona.amethyst.model.nip02FollowLists.FollowsPerOutboxRelay
import com.vitorpamplona.amethyst.model.nip17Dms.DmInboxRelayState
import com.vitorpamplona.amethyst.model.nip17Dms.DmRelayListState
import com.vitorpamplona.amethyst.model.nip18Reposts.RepostAction
import com.vitorpamplona.amethyst.model.nip25Reactions.ReactionAction
import com.vitorpamplona.amethyst.model.nip28PublicChats.PublicChatListState
import com.vitorpamplona.amethyst.model.nip30CustomEmojis.EmojiPackState
import com.vitorpamplona.amethyst.model.nip38UserStatuses.UserStatusAction
import com.vitorpamplona.amethyst.model.nip50Search.SearchRelayListState
import com.vitorpamplona.amethyst.model.nip51Lists.BlockPeopleListState
import com.vitorpamplona.amethyst.model.nip51Lists.BlockedRelayListState
import com.vitorpamplona.amethyst.model.nip51Lists.GeohashListState
import com.vitorpamplona.amethyst.model.nip51Lists.HashtagListState
import com.vitorpamplona.amethyst.model.nip51Lists.HiddenUsersState
import com.vitorpamplona.amethyst.model.nip51Lists.MuteListState
import com.vitorpamplona.amethyst.model.nip51Lists.TrustedRelayListState
import com.vitorpamplona.amethyst.model.nip56Reports.ReportAction
import com.vitorpamplona.amethyst.model.nip65RelayList.Nip65RelayListState
import com.vitorpamplona.amethyst.model.nip72Communities.CommunityListState
import com.vitorpamplona.amethyst.model.nip78AppSpecific.AppSpecificState
import com.vitorpamplona.amethyst.model.nip96FileStorage.FileStorageServerListState
import com.vitorpamplona.amethyst.model.nipB7Blossom.BlossomServerListState
import com.vitorpamplona.amethyst.model.serverList.MergedFollowListsState
import com.vitorpamplona.amethyst.model.serverList.MergedFollowPlusMineRelayListsState
import com.vitorpamplona.amethyst.model.serverList.MergedServerListState
import com.vitorpamplona.amethyst.model.serverList.TrustedRelayListsState
import com.vitorpamplona.amethyst.model.topNavFeeds.FeedTopNavFilterState
import com.vitorpamplona.amethyst.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.OutboxLoaderState
import com.vitorpamplona.amethyst.model.torState.TorRelayState
import com.vitorpamplona.amethyst.service.location.LocationState
import com.vitorpamplona.amethyst.service.ots.OtsResolverBuilder
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.nwc.NWCPaymentQueryState
import com.vitorpamplona.amethyst.service.uploads.FileHeader
import com.vitorpamplona.amethyst.ui.tor.TorType
import com.vitorpamplona.quartz.experimental.bounties.BountyAddValueEvent
import com.vitorpamplona.quartz.experimental.edits.TextNoteModificationEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
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
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.acessories.downloadFirstEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.addressables.isTaggedAddressableNote
import com.vitorpamplona.quartz.nip01Core.tags.addressables.taggedAddresses
import com.vitorpamplona.quartz.nip01Core.tags.events.isTaggedEvent
import com.vitorpamplona.quartz.nip01Core.tags.events.taggedEventIds
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohash
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohashes
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.hasAnyTaggedUser
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUserIds
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip03Timestamp.OtsEvent
import com.vitorpamplona.quartz.nip03Timestamp.OtsResolver
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip04Dm.messages.reply
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
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NRelay
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.emojis
import com.vitorpamplona.quartz.nip35Torrents.TorrentCommentEvent
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning
import com.vitorpamplona.quartz.nip37Drafts.DraftBuilder
import com.vitorpamplona.quartz.nip37Drafts.DraftEvent
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.nip47WalletConnect.LnZapPaymentRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip47WalletConnect.Response
import com.vitorpamplona.quartz.nip51Lists.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.FollowListEvent
import com.vitorpamplona.quartz.nip51Lists.GeneralListEvent
import com.vitorpamplona.quartz.nip51Lists.PeopleListEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip56Reports.ReportType
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplits
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiser
import com.vitorpamplona.quartz.nip59Giftwrap.WrappedEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayInfo
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip68Picture.PictureMeta
import com.vitorpamplona.quartz.nip68Picture.pictureIMeta
import com.vitorpamplona.quartz.nip71Video.VideoHorizontalEvent
import com.vitorpamplona.quartz.nip71Video.VideoMeta
import com.vitorpamplona.quartz.nip71Video.VideoVerticalEvent
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
import com.vitorpamplona.quartz.nipB7Blossom.BlossomAuthorizationEvent
import com.vitorpamplona.quartz.utils.tryAndWait
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Base64
import java.util.Locale
import kotlin.Boolean
import kotlin.collections.filter
import kotlin.collections.flatten
import kotlin.collections.ifEmpty
import kotlin.collections.map
import kotlin.collections.plus
import kotlin.collections.toSet
import kotlin.coroutines.resume

@OptIn(DelicateCoroutinesApi::class)
@Stable
class Account(
    val settings: AccountSettings = AccountSettings(KeyPair()),
    val signer: NostrSigner = settings.createSigner(),
    val geolocationFlow: StateFlow<LocationState.LocationResult>,
    val cache: LocalCache,
    val client: NostrClient,
    val scope: CoroutineScope,
) {
    private var userProfileCache: User? = null

    fun userProfile(): User = userProfileCache ?: cache.getOrCreateUser(signer.pubKey).also { userProfileCache = it }

    val userMetadata = UserMetadataState(signer, cache, scope, settings)

    val nip65RelayList = Nip65RelayListState(signer, cache, scope, settings)
    val dmRelayList = DmRelayListState(signer, cache, scope, settings)
    val searchRelayList = SearchRelayListState(signer, cache, scope, settings)
    val privateStorageRelayList = PrivateStorageRelayListState(signer, cache, scope, settings)
    val localRelayList = LocalRelayListState(signer, cache, scope, settings)
    val trustedRelayList = TrustedRelayListState(signer, cache, scope, settings)
    val blockedRelayList = BlockedRelayListState(signer, cache, scope, settings)

    val kind3FollowList = FollowListState(signer, cache, scope, settings)
    val ephemeralChatList = EphemeralChatListState(signer, cache, scope, settings)
    val publicChatList = PublicChatListState(signer, cache, scope, settings)
    val communityList = CommunityListState(signer, cache, scope, settings)
    val hashtagList = HashtagListState(signer, cache, scope, settings)
    val geohashList = GeohashListState(signer, cache, scope, settings)

    val muteList = MuteListState(signer, cache, scope, settings)
    val blockPeopleList = BlockPeopleListState(signer, cache, scope)
    val hiddenUsers = HiddenUsersState(muteList.flow, blockPeopleList.flow, scope, settings)

    val emoji = EmojiPackState(signer, cache, scope)

    val appSpecific = AppSpecificState(signer, cache, scope, settings)

    val blossomServers = BlossomServerListState(signer, cache, scope, settings)
    val fileStorageServers = FileStorageServerListState(signer, cache, scope, settings)
    val serverLists = MergedServerListState(fileStorageServers, blossomServers, scope)

    // Relay settings
    val outboxRelays = AccountOutboxRelayState(nip65RelayList, privateStorageRelayList, localRelayList, scope)
    val dmRelays = DmInboxRelayState(dmRelayList, nip65RelayList, privateStorageRelayList, localRelayList, scope)
    val notificationRelays = NotificationInboxRelayState(nip65RelayList, localRelayList, scope)

    val trustedRelays = TrustedRelayListsState(nip65RelayList, privateStorageRelayList, localRelayList, dmRelayList, searchRelayList, trustedRelayList, scope)

    // Follows Relays
    val followOutboxes = FollowListOutboxRelays(kind3FollowList, blockedRelayList, cache, scope)
    val followPlusAllMine = MergedFollowPlusMineRelayListsState(followOutboxes, nip65RelayList, privateStorageRelayList, localRelayList, trustedRelayList, scope)

    // keeps a cache of the outbox relays for each author
    val followsPerRelay = FollowsPerOutboxRelay(kind3FollowList, blockedRelayList, cache, scope).flow

    // Merges all follow lists to create a single All Follows feed.
    val allFollows = MergedFollowListsState(kind3FollowList, hashtagList, geohashList, communityList, scope)

    // App-ready Feeds
    val liveHomeFollowLists: StateFlow<IFeedTopNavFilter> =
        FeedTopNavFilterState(
            feedFilterListName = settings.defaultHomeFollowList,
            allFollows = allFollows.flow,
            locationFlow = geolocationFlow,
            followsRelays = followPlusAllMine.flow,
            blockedRelays = blockedRelayList.flow,
            signer = signer,
            scope = scope,
        ).flow

    val liveHomeFollowListsPerRelay = OutboxLoaderState(liveHomeFollowLists, cache, scope).flow

    val liveStoriesFollowLists: StateFlow<IFeedTopNavFilter> =
        FeedTopNavFilterState(
            feedFilterListName = settings.defaultStoriesFollowList,
            allFollows = allFollows.flow,
            locationFlow = geolocationFlow,
            followsRelays = followPlusAllMine.flow,
            blockedRelays = blockedRelayList.flow,
            signer = signer,
            scope = scope,
        ).flow

    val liveStoriesFollowListsPerRelay = OutboxLoaderState(liveStoriesFollowLists, cache, scope).flow

    val liveDiscoveryFollowLists: StateFlow<IFeedTopNavFilter> =
        FeedTopNavFilterState(
            feedFilterListName = settings.defaultDiscoveryFollowList,
            allFollows = allFollows.flow,
            locationFlow = geolocationFlow,
            followsRelays = followPlusAllMine.flow,
            blockedRelays = blockedRelayList.flow,
            signer = signer,
            scope = scope,
        ).flow

    val liveDiscoveryFollowListsPerRelay = OutboxLoaderState(liveDiscoveryFollowLists, cache, scope).flow

    val liveNotificationFollowLists: StateFlow<IFeedTopNavFilter> =
        FeedTopNavFilterState(
            feedFilterListName = settings.defaultNotificationFollowList,
            allFollows = allFollows.flow,
            locationFlow = geolocationFlow,
            followsRelays = followPlusAllMine.flow,
            blockedRelays = blockedRelayList.flow,
            signer = signer,
            scope = scope,
        ).flow

    val liveNotificationFollowListsPerRelay = OutboxLoaderState(liveNotificationFollowLists, cache, scope).flow

    /*
    val mergedTopFeedAuthorLists =
        MergedTopFeedAuthorListsState(
            liveHomeFollowListsPerRelay,
            liveStoriesFollowListsPerRelay,
            liveDiscoveryFollowListsPerRelay,
            liveNotificationFollowListsPerRelay,
            scope,
        ).flow

     */

    val torRelayState = TorRelayState(trustedRelays, dmRelayList, settings, scope)

    fun decryptPeopleList(
        event: GeneralListEvent,
        onReady: (Array<Array<String>>) -> Unit,
    ) = event.privateTags(signer, onReady)

    @OptIn(FlowPreview::class)
    val decryptBookmarks: Flow<BookmarkListEvent?> by lazy {
        userProfile()
            .flow()
            .bookmarks.stateFlow
            .map { userState ->
                if (userState.user.latestBookmarkList == null) {
                    null
                } else {
                    val result =
                        tryAndWait { continuation ->
                            userState.user.latestBookmarkList?.privateTags(signer) {
                                continuation.resume(userState.user.latestBookmarkList)
                            }
                        }

                    result
                }
            }.debounce(1000)
            .flowOn(Dispatchers.Default)
    }

    fun isWriteable(): Boolean = settings.isWriteable()

    fun updateWarnReports(warnReports: Boolean): Boolean {
        if (settings.updateWarnReports(warnReports)) {
            sendNewAppSpecificData()
            return true
        }
        return false
    }

    fun updateFilterSpam(filterSpam: Boolean): Boolean {
        if (settings.updateFilterSpam(filterSpam)) {
            if (!settings.syncedSettings.security.filterSpamFromStrangers.value) {
                hiddenUsers.resetTransientUsers()
            }

            sendNewAppSpecificData()
            return true
        }
        return false
    }

    fun updateShowSensitiveContent(show: Boolean?) {
        if (settings.updateShowSensitiveContent(show)) {
            sendNewAppSpecificData()
        }
    }

    fun changeReactionTypes(reactionSet: List<String>) {
        if (settings.changeReactionTypes(reactionSet)) {
            sendNewAppSpecificData()
        }
    }

    fun updateZapAmounts(
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

    fun toggleDontTranslateFrom(languageCode: String) {
        settings.toggleDontTranslateFrom(languageCode)
        sendNewAppSpecificData()
    }

    fun updateTranslateTo(languageCode: Locale) {
        if (settings.updateTranslateTo(languageCode)) {
            sendNewAppSpecificData()
        }
    }

    fun prefer(
        source: String,
        target: String,
        preference: String,
    ) {
        settings.prefer(source, target, preference)
        sendNewAppSpecificData()
    }

    private fun sendNewAppSpecificData() {
        if (!isWriteable()) return

        appSpecific.saveNewAppSpecificData(::sendMyPublicAndPrivateOutbox)
    }

    suspend fun countFollowersOf(pubkey: HexKey): Int = cache.users.count { _, it -> it.latestContactList?.isTaggedUser(pubkey) ?: false }

    suspend fun followerCount(): Int = countFollowersOf(signer.pubKey)

    fun sendNewUserMetadata(
        name: String? = null,
        picture: String? = null,
        banner: String? = null,
        website: String? = null,
        pronouns: String? = null,
        about: String? = null,
        nip05: String? = null,
        lnAddress: String? = null,
        lnURL: String? = null,
        twitter: String? = null,
        mastodon: String? = null,
        github: String? = null,
    ) {
        if (!isWriteable()) return

        userMetadata.sendNewUserMetadata(
            name,
            picture,
            banner,
            website,
            pronouns,
            about,
            nip05,
            lnAddress,
            lnURL,
            twitter,
            mastodon,
            github,
            ::sendMyPublicAndPrivateOutbox,
        )
    }

    fun reactionTo(
        note: Note,
        reaction: String,
    ): List<Note> = note.reactedBy(userProfile(), reaction)

    fun hasBoosted(note: Note): Boolean = boostsTo(note).isNotEmpty()

    fun boostsTo(note: Note): List<Note> = note.boostedBy(userProfile())

    fun hasReacted(
        note: Note,
        reaction: String,
    ): Boolean = note.hasReacted(userProfile(), reaction)

    suspend fun reactTo(
        note: Note,
        reaction: String,
    ) = ReactionAction.reactTo(
        note,
        reaction,
        userProfile(),
        signer,
        onPublic = {
            client.send(it, computeMyReactionToNote(note, it))
            cache.justConsumeMyOwnEvent(it)
        },
        onPrivate = ::broadcastPrivately,
    )

    fun createZapRequestFor(
        note: Note,
        pollOption: Int?,
        message: String = "",
        zapType: LnZapEvent.ZapType,
        toUser: User?,
        additionalRelays: Set<NormalizedRelayUrl>? = null,
        onReady: (LnZapRequestEvent) -> Unit,
    ) {
        if (!isWriteable()) return

        val relays = nip65RelayList.inboxFlow.value + (additionalRelays ?: emptySet())

        note.event?.let { event ->
            LnZapRequestEvent.create(
                event,
                relays = relays.mapTo(mutableSetOf()) { it.url },
                signer,
                pollOption,
                message,
                zapType,
                toUser?.pubkeyHex,
                onReady = onReady,
            )
        }
    }

    fun hasWalletConnectSetup(): Boolean = settings.zapPaymentRequest != null

    fun isNIP47Author(pubkeyHex: String?): Boolean = (getNIP47Signer().pubKey == pubkeyHex)

    fun getNIP47Signer(): NostrSigner =
        settings.zapPaymentRequest
            ?.secret
            ?.hexToByteArray()
            ?.let { NostrSignerInternal(KeyPair(it)) }
            ?: signer

    fun decryptZapPaymentResponseEvent(
        zapResponseEvent: LnZapPaymentResponseEvent,
        onReady: (Response) -> Unit,
    ) {
        val myNip47 = settings.zapPaymentRequest ?: return

        val signer =
            myNip47.secret?.hexToByteArray()?.let { NostrSignerInternal(KeyPair(it)) } ?: signer

        zapResponseEvent.response(signer, onReady)
    }

    suspend fun calculateIfNoteWasZappedByAccount(
        zappedNote: Note?,
        onWasZapped: () -> Unit,
    ) {
        zappedNote?.isZappedBy(userProfile(), this, onWasZapped)
    }

    suspend fun calculateZappedAmount(
        zappedNote: Note?,
        onReady: (BigDecimal) -> Unit,
    ) {
        zappedNote?.zappedAmountWithNWCPayments(getNIP47Signer(), onReady)
    }

    fun sendZapPaymentRequestFor(
        bolt11: String,
        zappedNote: Note?,
        onSent: () -> Unit,
        onResponse: (Response?) -> Unit,
    ) {
        if (!isWriteable()) return

        settings.zapPaymentRequest?.let { nip47 ->
            val signer =
                nip47.secret?.hexToByteArray()?.let { NostrSignerInternal(KeyPair(it)) } ?: signer

            LnZapPaymentRequestEvent.create(bolt11, nip47.pubKeyHex, signer) { event ->
                val filter =
                    NWCPaymentQueryState(
                        fromServiceHex = nip47.pubKeyHex,
                        toUserHex = event.pubKey,
                        replyingToHex = event.id,
                        relay = nip47.relayUri,
                    )

                Amethyst.instance.sources.nwc
                    .subscribe(filter)

                GlobalScope.launch(Dispatchers.IO) {
                    delay(60000) // waits 1 minute to complete payment.
                    Amethyst.instance.sources.nwc
                        .unsubscribe(filter)
                }

                cache.consume(event, zappedNote, true, nip47.relayUri) {
                    it.response(signer) { onResponse(it) }
                }

                client.send(event, setOf(nip47.relayUri))

                onSent()
            }
        }
    }

    fun createZapRequestFor(
        userPubKeyHex: String,
        message: String = "",
        zapType: LnZapEvent.ZapType,
        onReady: (LnZapRequestEvent) -> Unit,
    ) {
        LnZapRequestEvent.create(
            userPubKeyHex,
            nip65RelayList.inboxFlow.value.toSet(),
            signer,
            message,
            zapType,
            onReady = onReady,
        )
    }

    suspend fun report(
        note: Note,
        type: ReportType,
        content: String = "",
    ) = ReportAction.report(note, type, content, userProfile(), signer, ::sendMyPublicAndPrivateOutbox)

    suspend fun report(
        user: User,
        type: ReportType,
    ) = ReportAction.report(user, type, userProfile(), signer, ::sendMyPublicAndPrivateOutbox)

    fun delete(note: Note) {
        delete(listOf(note))
    }

    fun delete(notes: List<Note>) {
        if (!isWriteable()) return

        val myNotes = notes.filter { it.author == userProfile() && it.event != null }
        if (myNotes.isNotEmpty()) {
            // chunks in 200 elements to avoid going over the 65KB limit for events.
            myNotes.chunked(200).forEach { chunkedList ->
                signer.sign(
                    DeletionEvent.build(chunkedList.mapNotNull { it.event }),
                ) { deletionEvent ->
                    val myRelayList = outboxRelays.flow.value.toMutableSet()
                    chunkedList.forEach {
                        myRelayList.addAll(it.relays)
                    }

                    client.send(deletionEvent, outboxRelays.flow.value + myRelayList)
                    cache.justConsumeMyOwnEvent(deletionEvent)
                }
            }
        }
    }

    suspend fun createHTTPAuthorization(
        url: String,
        method: String,
        body: ByteArray? = null,
    ): HTTPAuthorizationEvent? {
        if (!isWriteable()) return null

        val template = HTTPAuthorizationEvent.build(url, method, body)

        return tryAndWait { continuation ->
            signer.sign(template) {
                continuation.resume(it)
            }
        }
    }

    suspend fun createBlossomUploadAuth(
        hash: HexKey,
        size: Long,
        alt: String,
    ): BlossomAuthorizationEvent? {
        if (!isWriteable()) return null

        return tryAndWait { continuation ->
            BlossomAuthorizationEvent.createUploadAuth(hash, size, alt, signer) {
                continuation.resume(it)
            }
        }
    }

    suspend fun createBlossomDeleteAuth(
        hash: HexKey,
        alt: String,
    ): BlossomAuthorizationEvent? {
        if (!isWriteable()) return null

        return tryAndWait { continuation ->
            BlossomAuthorizationEvent.createDeleteAuth(hash, alt, signer) {
                continuation.resume(it)
            }
        }
    }

    suspend fun boost(note: Note) {
        RepostAction.repost(note, signer) {
            client.send(it, computeMyReactionToNote(note, it))
            cache.justConsumeMyOwnEvent(it)
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
                    LocalCache
                        .getUserIfExists(pubkey)
                        ?.inboxRelays()
                        ?.ifEmpty { null }
                        ?.toSet()
                        ?: cache.relayHints.hintsForKey(pubkey).toSet()
                }
            }

        val isInChannel = note.channelHex()
        val channelRelays =
            if (isInChannel != null) {
                val channel = LocalCache.checkGetOrCreateChannel(isInChannel)
                channel?.relays() ?: emptySet()
            } else {
                emptySet()
            }

        val replyRelays =
            note.replyTo?.flatMapTo(mutableSetOf()) {
                val existingRelays = it.relays.toSet()

                val replyToAuthor = it.author

                val replyAuthorRelays =
                    if (replyToAuthor != null) {
                        if (replyToAuthor == userProfile()) {
                            outboxRelays.flow.value
                        } else {
                            replyToAuthor.outboxRelays().ifEmpty { null }?.toSet()
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
            user.inboxRelays().ifEmpty { null }?.toSet()
                ?: (cache.relayHints.hintsForKey(user.pubkeyHex).toSet() + user.relaysBeingUsed.keys)
        }

    private fun computeRelayListForLinkedUser(pubkey: HexKey): Set<NormalizedRelayUrl> =
        if (pubkey == userProfile().pubkeyHex) {
            notificationRelays.flow.value
        } else {
            LocalCache
                .getUserIfExists(pubkey)
                ?.inboxRelays()
                ?.ifEmpty { null }
                ?.toSet()
                ?: cache.relayHints.hintsForKey(pubkey).toSet()
        }

    private fun computeRelaysForChannels(event: Event): Set<NormalizedRelayUrl> {
        val isInChannel =
            if (
                event is ChannelMessageEvent ||
                event is ChannelMetadataEvent ||
                event is ChannelCreateEvent ||
                event is LiveActivitiesChatMessageEvent ||
                event is LiveActivitiesEvent ||
                event is EphemeralChatEvent
            ) {
                (event as? ChannelMessageEvent)?.channelId()
                    ?: (event as? ChannelMetadataEvent)?.channelId()
                    ?: (event as? ChannelCreateEvent)?.id
                    ?: (event as? LiveActivitiesChatMessageEvent)?.activity()?.toTag()
                    ?: (event as? LiveActivitiesEvent)?.aTag()?.toTag()
                    ?: (event as? EphemeralChatEvent)?.roomId()?.toKey()
            } else {
                null
            }

        return if (isInChannel != null) {
            val channel = LocalCache.checkGetOrCreateChannel(isInChannel)
            channel?.relays() ?: emptySet()
        } else {
            emptySet()
        }
    }

    fun computeRelayListToBroadcast(event: Event): Set<NormalizedRelayUrl> {
        if (event is MetadataEvent || event is AdvertisedRelayListEvent) {
            return followPlusAllMine.flow.value + client.relayStatusFlow().value.available
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
                relayList.addAll(
                    author.outboxRelays().ifEmpty { null }
                        ?: cache.relayHints.hintsForKey(author.pubkeyHex),
                )
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

    fun broadcast(note: Note) {
        note.event?.let {
            if (it is WrappedEvent && it.host != null) {
                // download the event and send it.
                it.host?.let { host ->
                    client.downloadFirstEvent(
                        filters =
                            note.relays.associateWith { relay ->
                                listOf(
                                    Filter(
                                        ids = listOf(host.id),
                                    ),
                                )
                            },
                        onResponse = {
                            client.send(it, computeRelayListToBroadcast(it))
                        },
                    )
                }
            } else {
                client.send(it, computeRelayListToBroadcast(note))
            }
        }
    }

    suspend fun updateAttestations() {
        Log.d("Pending Attestations", "Updating ${settings.pendingAttestations.value.size} pending attestations")

        val otsResolver = otsResolver()

        settings.pendingAttestations.value.forEach { pair ->
            val otsState = OtsEvent.upgrade(Base64.getDecoder().decode(pair.value), pair.key, otsResolver)

            if (otsState != null) {
                val hint = cache.getNoteIfExists(pair.key)?.toEventHint<Event>()

                val template =
                    if (hint != null) {
                        OtsEvent.build(hint, otsState)
                    } else {
                        OtsEvent.build(pair.key, otsState)
                    }

                signer.sign(template) {
                    cache.justConsumeMyOwnEvent(it)
                    client.send(it, computeRelayListToBroadcast(it))

                    settings.pendingAttestations.update {
                        it - pair.key
                    }
                }
            }
        }
    }

    fun hasPendingAttestations(note: Note): Boolean {
        val id = note.event?.id ?: note.idHex
        return settings.pendingAttestations.value[id] != null
    }

    fun timestamp(note: Note) {
        if (!isWriteable()) return
        if (note.isDraft()) return

        val id = note.event?.id ?: note.idHex
        val otsResolver = otsResolver()

        settings.addPendingAttestation(id, Base64.getEncoder().encodeToString(OtsEvent.stamp(id, otsResolver)))
    }

    fun follow(user: User) = kind3FollowList.follow(user, this::sendMyPublicAndPrivateOutbox)

    fun unfollow(user: User) = kind3FollowList.unfollow(user, this::sendMyPublicAndPrivateOutbox)

    fun follow(channel: PublicChatChannel) = publicChatList.follow(channel, ::sendToPrivateOutboxAndLocal)

    fun unfollow(channel: PublicChatChannel) = publicChatList.unfollow(channel, ::sendToPrivateOutboxAndLocal)

    fun follow(channel: EphemeralChatChannel) = ephemeralChatList.follow(channel, ::sendToPrivateOutboxAndLocal)

    fun unfollow(channel: EphemeralChatChannel) = ephemeralChatList.unfollow(channel, ::sendToPrivateOutboxAndLocal)

    fun follow(community: AddressableNote) = communityList.follow(community, ::sendToPrivateOutboxAndLocal)

    fun unfollow(community: AddressableNote) = communityList.unfollow(community, ::sendToPrivateOutboxAndLocal)

    fun followHashtag(tag: String) = hashtagList.follow(tag, ::sendMyPublicAndPrivateOutbox)

    fun unfollowHashtag(tag: String) = hashtagList.unfollow(tag, ::sendMyPublicAndPrivateOutbox)

    fun followGeohash(geohash: String) = geohashList.follow(geohash, ::sendMyPublicAndPrivateOutbox)

    fun unfollowGeohash(geohash: String) = geohashList.unfollow(geohash, ::sendMyPublicAndPrivateOutbox)

    fun sendMyPublicAndPrivateOutbox(event: Event) {
        client.send(event, outboxRelays.flow.value)
        cache.justConsumeMyOwnEvent(event)
    }

    fun createNip95(
        byteArray: ByteArray,
        headerInfo: FileHeader,
        alt: String?,
        contentWarningReason: String?,
        onReady: (Pair<FileStorageEvent, FileStorageHeaderEvent>) -> Unit,
    ) {
        if (!isWriteable()) return

        signer.sign(FileStorageEvent.build(byteArray, headerInfo.mimeType)) { data ->
            val template =
                FileStorageHeaderEvent.build(EventHintBundle(data, userProfile().bestRelayHint()), alt) {
                    hash(headerInfo.hash)
                    fileSize(headerInfo.size)

                    headerInfo.mimeType?.let { mimeType(it) }
                    headerInfo.dim?.let { dimension(it) }
                    headerInfo.blurHash?.let { blurhash(it.blurhash) }

                    contentWarningReason?.let { contentWarning(contentWarningReason) }
                }

            signer.sign(template) { signedEvent ->
                onReady(
                    Pair(data, signedEvent),
                )
            }
        }
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

    fun createHeader(
        imageUrl: String,
        magnetUri: String?,
        headerInfo: FileHeader,
        alt: String?,
        contentWarningReason: String? = null,
        originalHash: String? = null,
        onReady: (FileHeaderEvent) -> Unit,
    ) {
        if (!isWriteable()) return

        signer.sign(
            FileHeaderEvent.build(imageUrl, alt) {
                hash(headerInfo.hash)
                fileSize(headerInfo.size)

                headerInfo.mimeType?.let { mimeType(it) }
                headerInfo.dim?.let { dimension(it) }
                headerInfo.blurHash?.let { blurhash(it.blurhash) }

                originalHash?.let { originalHash(it) }
                magnetUri?.let { magnet(it) }

                contentWarningReason?.let { contentWarning(contentWarningReason) }
            },
            onReady,
        )
    }

    fun sendAllAsOnePictureEvent(
        urlHeaderInfo: Map<String, FileHeader>,
        caption: String?,
        contentWarningReason: String?,
        onReady: (Note) -> Unit,
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

        signAndComputeBroadcast(template) { event ->
            cache.getNoteIfExists(event.id)?.let { onReady(it) }
        }
    }

    fun sendHeader(
        url: String,
        magnetUri: String?,
        headerInfo: FileHeader,
        alt: String?,
        contentWarningReason: String?,
        originalHash: String? = null,
        onReady: (Note) -> Unit,
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
                    VideoVerticalEvent.build(videoMeta, alt ?: "") {
                        contentWarningReason?.let { contentWarning(contentWarningReason) }
                    }
                } else {
                    VideoHorizontalEvent.build(videoMeta, alt ?: "") {
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

        signAndComputeBroadcast(template) { event ->
            cache.getNoteIfExists(event.id)?.let { onReady(it) }
        }
    }

    fun <T : Event> signAndSendPrivately(
        template: EventTemplate<T>,
        relayList: Set<NormalizedRelayUrl>,
    ) {
        signer.sign(template) {
            cache.justConsumeMyOwnEvent(it)
            client.send(it, relayList)
        }
    }

    fun <T : Event> signAndSendPrivatelyOrBroadcast(
        template: EventTemplate<T>,
        relayList: (T) -> List<NormalizedRelayUrl>?,
        onDone: (T) -> Unit = {},
    ) {
        signer.sign(template) {
            cache.justConsumeMyOwnEvent(it)
            val relays = relayList(it)
            if (relays != null && relays.isNotEmpty()) {
                client.send(it, relays.toSet())
            } else {
                client.send(it, computeRelayListToBroadcast(it))
            }
            onDone(it)
        }
    }

    fun <T : Event> signAndComputeBroadcast(
        template: EventTemplate<T>,
        broadcast: List<Event> = emptyList(),
        onDone: (T) -> Unit = {},
    ) {
        signer.sign(template) { event ->
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

            onDone(event)
        }
    }

    fun <T : Event> signAndSend(
        template: EventTemplate<T>,
        relayList: Set<NormalizedRelayUrl>,
        broadcastNotes: Set<Note>,
    ) {
        signer.sign(template) {
            cache.justConsumeMyOwnEvent(it)
            client.send(it, relayList = relayList)

            broadcastNotes.forEach { it.event?.let { client.send(it, relayList = relayList) } }
        }
    }

    fun <T : Event> signAndSend(
        draftTag: String?,
        template: EventTemplate<T>,
        relayList: Set<NormalizedRelayUrl>,
        broadcastNotes: List<Entity>,
    ) = signAndSend(draftTag, template, relayList, mapEntitiesToNotes(broadcastNotes).toSet())

    fun <T : Event> signAndSend(
        draftTag: String?,
        template: EventTemplate<T>,
        relayList: Set<NormalizedRelayUrl>,
        broadcastNotes: Set<Note>,
    ) {
        if (draftTag != null) {
            signer.assembleRumor(template) { rumor ->
                DraftEvent.create(draftTag, rumor, emptyList(), signer) { draftEvent ->
                    sendDraftEvent(draftEvent)
                }
            }
        } else {
            signer.sign(template) {
                cache.justConsumeMyOwnEvent(it)
                client.send(it, relayList = relayList)

                broadcastNotes.forEach { it.event?.let { client.send(it, relayList = relayList) } }
            }
        }
    }

    fun <T : Event> signAndSendNIP04Message(
        draftTag: String?,
        template: EventTemplate<T>,
        relayList: Set<NormalizedRelayUrl>,
    ) {
        if (draftTag != null) {
            if (template.content.isEmpty()) {
                deleteDraft(draftTag)
            } else {
                signer.assembleRumor(template) { rumor ->
                    DraftEvent.create(draftTag, rumor, emptyList(), signer) { draftEvent ->
                        sendDraftEvent(draftEvent)
                    }
                }
            }
        } else {
            signer.sign(template) {
                cache.justConsumeMyOwnEvent(it)
                client.send(it, relayList)
            }
        }
    }

    fun <T : Event> signAndSendWithList(
        draftTag: String?,
        template: EventTemplate<T>,
        relayList: Collection<NormalizedRelayUrl>,
        broadcastNotes: Set<Note>,
    ) {
        if (draftTag != null) {
            signer.assembleRumor(template) { rumor ->
                DraftEvent.create(draftTag, rumor, emptyList(), signer) { draftEvent ->
                    sendDraftEvent(draftEvent)
                }
            }
        } else {
            signer.sign(template) {
                cache.justConsumeMyOwnEvent(it)

                val relaySet = relayList.toSet()

                client.send(it, relayList = relaySet)
                broadcastNotes.forEach { it.event?.let { client.send(it, relayList = relaySet) } }
            }
        }
    }

    fun sendTorrentComment(
        draftTag: String?,
        template: EventTemplate<TorrentCommentEvent>,
        broadcastNotes: Set<Note>,
        relayList: Set<NormalizedRelayUrl>,
    ) {
        if (!isWriteable()) return

        signAndSend(draftTag, template, relayList, broadcastNotes)
    }

    fun createAndSendDraft(
        draftTag: String,
        template: EventTemplate<out Event>,
    ) {
        val rumor = signer.assembleRumor(template)
        DraftBuilder.encryptAndSign(draftTag, rumor, signer) { draftEvent ->
            sendDraftEvent(draftEvent)
        }
    }

    fun deleteDraft(draftTag: String) {
        val key = DraftEvent.createAddressTag(userProfile().pubkeyHex, draftTag)
        cache.getAddressableNoteIfExists(key)?.let { note ->
            val noteEvent = note.event
            if (noteEvent is DraftEvent) {
                noteEvent.createDeletedEvent(signer) {
                    client.send(it, outboxRelays.flow.value + note.relays)
                    cache.justConsumeMyOwnEvent(it)
                }
            }
            delete(note)
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

        signer.sign(template, ::sendToPrivateOutboxAndLocal)
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

        signer.sign(template, ::sendToPrivateOutboxAndLocal)
    }

    fun mapEntitiesToNotes(entities: List<Entity>): List<Note> =
        entities.mapNotNull {
            when (it) {
                is NPub -> null
                is NProfile -> null
                is com.vitorpamplona.quartz.nip19Bech32.entities.NNote -> cache.getOrCreateNote(it.hex)
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

        signAndSend(draftTag, template, relayList, quotes)
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

        signAndSend(draftTag, template, relayList, mapEntitiesToNotes(quotes).toSet())
    }

    suspend fun sendAddBounty(
        value: BigDecimal,
        bounty: Note,
        draftTag: String?,
    ) {
        if (!isWriteable()) return

        val event = bounty.event as? TextNoteEvent ?: return
        val eventAuthor = bounty.author ?: return

        val template =
            BountyAddValueEvent.build(
                value,
                EventHintBundle(event, bounty.relayHintUrl()),
                eventAuthor.toPTag(),
            )

        val relays = bounty.relays + outboxRelays.flow.value

        signAndSendWithList(draftTag, template, relays, setOf(bounty))
    }

    fun sendEdit(
        message: String,
        originalNote: Note,
        notify: HexKey?,
        summary: String? = null,
        broadcast: List<Event>,
    ) {
        if (!isWriteable()) return

        val idHex = originalNote.event?.id ?: return

        TextNoteModificationEvent.create(
            content = message,
            eventId = idHex,
            notify = notify,
            summary = summary,
            signer = signer,
        ) { event ->
            cache.justConsumeMyOwnEvent(event)
            val note = cache.getOrCreateNote(event.id)
            val relayList = computeRelayListToBroadcast(note)

            client.send(event, relayList = relayList)

            broadcast.forEach { client.send(it, relayList) }
        }
    }

    fun sendPrivateMessage(
        message: String,
        toUser: PTag,
        replyingTo: Note? = null,
        zapReceiver: List<ZapSplitSetup>? = null,
        contentWarningReason: String? = null,
        zapRaiserAmount: Long? = null,
        geohash: String? = null,
        imetas: List<IMetaTag>? = null,
        emojis: List<EmojiUrlTag>? = null,
        draftTag: String?,
    ) {
        if (!isWriteable()) return

        signer.nip04Encrypt(
            PrivateDmEvent.prepareMessageToEncrypt(message, imetas),
            toUser.pubKey,
        ) { encryptedContent ->
            val template =
                PrivateDmEvent.build(toUser, encryptedContent) {
                    replyingTo?.let { reply(it.toEId()) }

                    geohash?.let { geohash(it) }
                    zapRaiserAmount?.let { zapraiser(it) }
                    zapReceiver?.let { zapSplits(it) }
                    emojis?.let { emojis(it) }
                    contentWarningReason?.let { contentWarning(contentWarningReason) }
                }

            val destinationRelays = cache.getOrCreateUser(toUser.pubKey).dmInboxRelays()

            signAndSendNIP04Message(draftTag, template, outboxRelays.flow.value + destinationRelays)
        }
    }

    fun sendNIP17EncryptedFile(template: EventTemplate<ChatMessageEncryptedFileHeaderEvent>) {
        if (!isWriteable()) return

        NIP17Factory().createEncryptedFileNIP17(template, signer) {
            broadcastPrivately(it)
        }
    }

    fun sendNIP17PrivateMessage(
        template: EventTemplate<ChatMessageEvent>,
        draftTag: String? = null,
    ) {
        if (!isWriteable()) return

        if (draftTag != null) {
            if (template.content.isEmpty()) {
                deleteDraft(draftTag)
            } else {
                signer.assembleRumor(template) {
                    DraftEvent.create(draftTag, it, emptyList(), signer) { draftEvent ->
                        sendDraftEvent(draftEvent)
                    }
                }
            }
        } else {
            NIP17Factory().createMessageNIP17(template, signer) {
                broadcastPrivately(it)
            }
        }
    }

    fun sendDraftEvent(draftEvent: DraftEvent) {
        sendToPrivateOutboxAndLocal(draftEvent)
    }

    fun sendToPrivateOutboxAndLocal(event: Event) {
        val relayList = privateStorageRelayList.flow.value + localRelayList.flow.value
        if (relayList.isNotEmpty()) {
            client.send(event, relayList.toSet())
        } else {
            client.send(event, outboxRelays.flow.value)
        }
        cache.justConsumeMyOwnEvent(event)
    }

    fun broadcastPrivately(signedEvents: NIP17Factory.Result) {
        val mine = signedEvents.wraps.filter { (it.recipientPubKey() == signer.pubKey) }

        mine.forEach { giftWrap ->
            giftWrap.unwrap(signer) { gift ->
                if (gift is SealedRumorEvent) {
                    gift.unseal(signer) { rumor ->
                        cache.justConsumeMyOwnEvent(rumor)
                    }
                }

                cache.justConsumeMyOwnEvent(gift)
            }

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

    fun createStatus(newStatus: String) = UserStatusAction.create(newStatus, signer, ::sendMyPublicAndPrivateOutbox)

    fun updateStatus(
        oldStatus: AddressableNote,
        newStatus: String,
    ) = UserStatusAction.update(oldStatus, newStatus, signer, ::sendMyPublicAndPrivateOutbox)

    fun deleteStatus(oldStatus: AddressableNote) = UserStatusAction.delete(oldStatus, signer, ::sendMyPublicAndPrivateOutbox)

    fun removeEmojiPack(emojiPack: Note) = emoji.removeEmojiPack(emojiPack, ::sendMyPublicAndPrivateOutbox)

    fun addEmojiPack(emojiPack: Note) = emoji.addEmojiPack(emojiPack, ::sendMyPublicAndPrivateOutbox)

    fun addToGallery(
        idHex: HexKey,
        url: String,
        relay: NormalizedRelayUrl?,
        blurhash: String?,
        dim: DimensionTag?,
        hash: String?,
        mimeType: String?,
    ) {
        if (!isWriteable()) return

        signer.sign(
            ProfileGalleryEntryEvent.build(url) {
                fromEvent(idHex, relay)
                hash?.let { hash(hash) }
                mimeType?.let { mimeType(it) }
                dim?.let { dimension(it) }
                blurhash?.let { blurhash(it) }
            },
        ) {
            client.send(it, outboxRelays.flow.value)
            cache.justConsumeMyOwnEvent(it)
        }
    }

    fun removeFromGallery(note: Note) {
        delete(note)
    }

    fun addBookmark(
        note: Note,
        isPrivate: Boolean,
    ) {
        if (!isWriteable()) return
        if (note.isDraft()) return

        if (note is AddressableNote) {
            BookmarkListEvent.addReplaceable(
                userProfile().latestBookmarkList,
                note.toATag(),
                isPrivate,
                signer,
            ) {
                client.send(it, outboxRelays.flow.value)
                cache.justConsumeMyOwnEvent(it)
            }
        } else {
            BookmarkListEvent.addEvent(
                userProfile().latestBookmarkList,
                note.idHex,
                isPrivate,
                signer,
            ) {
                client.send(it, outboxRelays.flow.value)
                cache.justConsumeMyOwnEvent(it)
            }
        }
    }

    fun removeBookmark(
        note: Note,
        isPrivate: Boolean,
    ) {
        if (!isWriteable()) return

        val bookmarks = userProfile().latestBookmarkList ?: return

        if (note is AddressableNote) {
            BookmarkListEvent.removeReplaceable(
                bookmarks,
                note.toATag(),
                isPrivate,
                signer,
            ) {
                client.send(it, outboxRelays.flow.value)
                cache.justConsumeMyOwnEvent(it)
            }
        } else {
            BookmarkListEvent.removeEvent(
                bookmarks,
                note.idHex,
                isPrivate,
                signer,
            ) {
                client.send(it, outboxRelays.flow.value)
                cache.justConsumeMyOwnEvent(it)
            }
        }
    }

    fun sendAuthEvent(
        relay: IRelayClient,
        challenge: String,
    ) {
        createAuthEvent(relay.url, challenge) {
            client.sendIfExists(it, relay.url)
        }
    }

    fun createAuthEvent(
        relayUrl: NormalizedRelayUrl,
        challenge: String,
        onReady: (RelayAuthEvent) -> Unit,
    ) {
        if (!isWriteable()) return

        RelayAuthEvent.create(relayUrl, challenge, signer, onReady = onReady)
    }

    fun createAuthEvent(
        relayUrls: List<NormalizedRelayUrl>,
        challenge: String,
        onReady: (RelayAuthEvent) -> Unit,
    ) {
        if (!isWriteable()) return

        RelayAuthEvent.create(relayUrls, challenge, signer, onReady = onReady)
    }

    fun isInPrivateBookmarks(
        note: Note,
        onReady: (Boolean) -> Unit,
    ) {
        if (!isWriteable()) {
            onReady(false)
            false
        }
        if (userProfile().latestBookmarkList == null) {
            onReady(false)
            false
        }

        if (note is AddressableNote) {
            userProfile().latestBookmarkList?.privateAddress(signer) {
                onReady(it.contains(note.address))
            }
        } else {
            userProfile().latestBookmarkList?.privateTaggedEvents(signer) {
                onReady(it.contains(note.idHex))
            }
        }
    }

    fun isInPublicBookmarks(note: Note): Boolean {
        if (!isWriteable()) return false

        return if (note is AddressableNote) {
            userProfile().latestBookmarkList?.isTaggedAddressableNote(note.idHex) == true
        } else {
            userProfile().latestBookmarkList?.isTaggedEvent(note.idHex) == true
        }
    }

    fun hideWord(word: String) {
        if (!isWriteable()) return

        muteList.hideWord(word, ::sendMyPublicAndPrivateOutbox)
    }

    fun showWord(word: String) {
        if (!isWriteable()) return

        blockPeopleList.showWord(word, ::sendMyPublicAndPrivateOutbox)
        muteList.showWord(word, ::sendMyPublicAndPrivateOutbox)
    }

    fun hideUser(pubkeyHex: HexKey) {
        if (!isWriteable()) return

        muteList.hideUser(pubkeyHex, ::sendMyPublicAndPrivateOutbox)
    }

    fun showUser(pubkeyHex: HexKey) {
        if (!isWriteable()) return

        blockPeopleList.showUser(pubkeyHex, ::sendMyPublicAndPrivateOutbox)
        muteList.showUser(pubkeyHex, ::sendMyPublicAndPrivateOutbox)
        hiddenUsers.showUser(pubkeyHex)
    }

    fun requestDVMContentDiscovery(
        dvmPublicKey: User,
        onReady: (event: NIP90ContentDiscoveryRequestEvent) -> Unit,
    ) {
        val relays = nip65RelayList.inboxFlow.value.toSet()
        NIP90ContentDiscoveryRequestEvent.create(dvmPublicKey.pubkeyHex, signer.pubKey, relays, signer) {
            val relayList =
                dvmPublicKey
                    .inboxRelays()
                    .ifEmpty {
                        LocalCache.relayHints.hintsForKey(dvmPublicKey.pubkeyHex)
                    }.toSet()

            client.send(it, relayList)
            cache.justConsumeMyOwnEvent(it)
            onReady(it)
        }
    }

    fun unwrap(
        event: GiftWrapEvent,
        onReady: (Event) -> Unit,
    ) {
        if (!isWriteable()) return

        return event.unwrap(signer, onReady)
    }

    fun unseal(
        event: SealedRumorEvent,
        onReady: (Event) -> Unit,
    ) {
        if (!isWriteable()) return

        return event.unseal(signer, onReady)
    }

    fun cachedDecryptContent(note: Note): String? = cachedDecryptContent(note.event)

    fun cachedDecryptContent(event: Event?): String? {
        if (event == null) return null

        return if (isWriteable()) {
            if (event is PrivateDmEvent) {
                event.cachedContentFor(signer)
            } else if (event is LnZapRequestEvent && event.isPrivateZap()) {
                event.cachedPrivateZap()?.content
            } else if (event is DraftEvent) {
                event.preCachedDraft(signer)?.content
            } else {
                event.content
            }
        } else {
            event.content
        }
    }

    fun decryptContent(
        note: Note,
        onReady: (String) -> Unit,
    ) {
        val event = note.event
        if (event is PrivateDmEvent && isWriteable()) {
            event.plainContent(signer, onReady)
        } else if (event is LnZapRequestEvent) {
            decryptZapContentAuthor(note) { onReady(it.content) }
        } else if (event is DraftEvent) {
            event.cachedDraft(signer) {
                onReady(it.content)
            }
        } else {
            event?.content?.let { onReady(it) }
        }
    }

    fun decryptZapContentAuthor(
        note: Note,
        onReady: (Event) -> Unit,
    ) {
        val event = note.event
        if (event is LnZapRequestEvent) {
            if (event.isPrivateZap()) {
                if (isWriteable()) {
                    event.decryptPrivateZap(signer) { onReady(it) }
                }
            } else {
                onReady(event)
            }
        }
    }

    fun isAllHidden(users: Set<HexKey>): Boolean = users.all { isHidden(it) }

    fun isHidden(user: User) = isHidden(user.pubkeyHex)

    fun isHidden(userHex: String): Boolean = hiddenUsers.flow.value.isUserHidden(userHex)

    fun followingKeySet(): Set<HexKey> = kind3FollowList.flow.value.authors

    fun isAcceptable(user: User): Boolean {
        if (userProfile().pubkeyHex == user.pubkeyHex) {
            return true
        }

        if (user.pubkeyHex in followingKeySet()) {
            return true
        }

        if (!settings.syncedSettings.security.warnAboutPostsWithReports) {
            return !isHidden(user) &&
                // if user hasn't hided this author
                user.reportsBy(userProfile()).isEmpty() // if user has not reported this post
        }
        return !isHidden(user) &&
            // if user hasn't hided this author
            user.reportsBy(userProfile()).isEmpty() &&
            // if user has not reported this post
            user.countReportAuthorsBy(followingKeySet()) < 5
    }

    private fun isAcceptableDirect(note: Note): Boolean {
        if (!settings.syncedSettings.security.warnAboutPostsWithReports) {
            return !note.hasReportsBy(userProfile())
        }
        return !note.hasReportsBy(userProfile()) &&
            // if user has not reported this post
            note.countReportAuthorsBy(followingKeySet()) < 5 // if it has 5 reports by reliable users
    }

    fun isFollowing(user: User): Boolean = user.pubkeyHex in followingKeySet()

    fun isFollowing(user: HexKey): Boolean = user in followingKeySet()

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
                (note.author?.reportsBy(kind3FollowList.flow.value.authorsPlusMe) ?: emptyList()) +
                innerReports
        ).toSet()
    }

    fun saveDMRelayList(dmRelays: List<NormalizedRelayUrl>) {
        if (!isWriteable()) return
        dmRelayList.saveRelayList(dmRelays, ::sendMyPublicAndPrivateOutbox)
    }

    fun savePrivateOutboxRelayList(relays: List<NormalizedRelayUrl>) {
        if (!isWriteable()) return
        privateStorageRelayList.saveRelayList(relays, ::sendMyPublicAndPrivateOutbox)
    }

    fun saveSearchRelayList(searchRelays: List<NormalizedRelayUrl>) {
        if (!isWriteable()) return
        searchRelayList.saveRelayList(searchRelays, ::sendMyPublicAndPrivateOutbox)
    }

    fun saveTrustedRelayList(trustedRelays: List<NormalizedRelayUrl>) {
        if (!isWriteable()) return
        trustedRelayList.saveRelayList(trustedRelays, ::sendMyPublicAndPrivateOutbox)
    }

    fun saveBlockedRelayList(blockedRelays: List<NormalizedRelayUrl>) {
        if (!isWriteable()) return
        blockedRelayList.saveRelayList(blockedRelays, ::sendMyPublicAndPrivateOutbox)
    }

    fun sendNip65RelayList(relays: List<AdvertisedRelayInfo>) {
        if (!isWriteable()) return
        nip65RelayList.saveRelayList(relays, ::sendMyPublicAndPrivateOutbox)
    }

    fun sendFileServersList(servers: List<String>) {
        if (!isWriteable()) return
        fileStorageServers.saveFileServersList(servers, ::sendMyPublicAndPrivateOutbox)
    }

    fun sendBlossomServersList(servers: List<String>) {
        if (!isWriteable()) return
        blossomServers.saveBlossomServersList(servers, ::sendMyPublicAndPrivateOutbox)
    }

    fun getAllPeopleLists(): List<AddressableNote> = getAllPeopleLists(signer.pubKey)

    fun getAllPeopleLists(pubkey: HexKey): List<AddressableNote> =
        cache.addressables
            .filter { _, addressableNote ->
                val noteEvent = addressableNote.event

                if (noteEvent is PeopleListEvent) {
                    noteEvent.pubKey == pubkey && (noteEvent.hasAnyTaggedUser() || noteEvent.cachedPrivateTags()?.isNotEmpty() == true)
                } else if (noteEvent is FollowListEvent) {
                    noteEvent.pubKey == pubkey && noteEvent.hasAnyTaggedUser()
                } else {
                    false
                }
            }

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
            .flowOn(Dispatchers.Default)
            .stateIn(scope, SharingStarted.Eagerly, hasDonatedInThisVersion())

    fun markDonatedInThisVersion() = settings.markDonatedInThisVersion(BuildConfig.VERSION_NAME)

    fun shouldUseTorForImageDownload(url: String) =
        shouldUseTorFor(
            url,
            settings.torSettings.torType.value,
            settings.torSettings.imagesViaTor.value,
        )

    fun shouldUseTorFor(
        url: String,
        torType: TorType,
        imagesViaTor: Boolean,
    ) = when (torType) {
        TorType.OFF -> false
        TorType.INTERNAL -> shouldUseTor(url, imagesViaTor)
        TorType.EXTERNAL -> shouldUseTor(url, imagesViaTor)
    }

    private fun shouldUseTor(
        normalizedUrl: String,
        final: Boolean,
    ): Boolean =
        if (RelayUrlNormalizer.isLocalHost(normalizedUrl)) {
            false
        } else if (RelayUrlNormalizer.isOnion(normalizedUrl)) {
            true
        } else {
            final
        }

    fun shouldUseTorForVideoDownload() =
        when (settings.torSettings.torType.value) {
            TorType.OFF -> false
            TorType.INTERNAL -> settings.torSettings.videosViaTor.value
            TorType.EXTERNAL -> settings.torSettings.videosViaTor.value
        }

    fun shouldUseTorForVideoDownload(url: String) =
        when (settings.torSettings.torType.value) {
            TorType.OFF -> false
            TorType.INTERNAL -> checkLocalHostOnionAndThen(url, settings.torSettings.videosViaTor.value)
            TorType.EXTERNAL -> checkLocalHostOnionAndThen(url, settings.torSettings.videosViaTor.value)
        }

    fun shouldUseTorForPreviewUrl(url: String) =
        when (settings.torSettings.torType.value) {
            TorType.OFF -> false
            TorType.INTERNAL -> checkLocalHostOnionAndThen(url, settings.torSettings.urlPreviewsViaTor.value)
            TorType.EXTERNAL -> checkLocalHostOnionAndThen(url, settings.torSettings.urlPreviewsViaTor.value)
        }

    fun shouldUseTorForTrustedRelays() =
        when (settings.torSettings.torType.value) {
            TorType.OFF -> false
            TorType.INTERNAL -> settings.torSettings.trustedRelaysViaTor.value
            TorType.EXTERNAL -> settings.torSettings.trustedRelaysViaTor.value
        }

    fun shouldUseTorForClean(relay: NormalizedRelayUrl) = torRelayState.flow.value.useTor(relay)

    private fun checkLocalHostOnionAndThen(
        url: String,
        final: Boolean,
    ): Boolean = checkLocalHostOnionAndThen(url, settings.torSettings.onionRelaysViaTor.value, final)

    private fun checkLocalHostOnionAndThen(
        normalizedUrl: String,
        isOnionRelaysActive: Boolean,
        final: Boolean,
    ): Boolean =
        if (RelayUrlNormalizer.isLocalHost(normalizedUrl)) {
            false
        } else if (RelayUrlNormalizer.isOnion(normalizedUrl)) {
            isOnionRelaysActive
        } else {
            final
        }

    fun shouldUseTorForMoneyOperations(url: String) =
        when (settings.torSettings.torType.value) {
            TorType.OFF -> false
            TorType.INTERNAL -> checkLocalHostOnionAndThen(url, settings.torSettings.moneyOperationsViaTor.value)
            TorType.EXTERNAL -> checkLocalHostOnionAndThen(url, settings.torSettings.moneyOperationsViaTor.value)
        }

    fun shouldUseTorForNIP05(url: String) =
        when (settings.torSettings.torType.value) {
            TorType.OFF -> false
            TorType.INTERNAL -> checkLocalHostOnionAndThen(url, settings.torSettings.nip05VerificationsViaTor.value)
            TorType.EXTERNAL -> checkLocalHostOnionAndThen(url, settings.torSettings.nip05VerificationsViaTor.value)
        }

    fun shouldUseTorForNIP96(url: String) =
        when (settings.torSettings.torType.value) {
            TorType.OFF -> false
            TorType.INTERNAL -> checkLocalHostOnionAndThen(url, settings.torSettings.nip96UploadsViaTor.value)
            TorType.EXTERNAL -> checkLocalHostOnionAndThen(url, settings.torSettings.nip96UploadsViaTor.value)
        }

    fun otsResolver(): OtsResolver =
        OtsResolverBuilder().build(
            Amethyst.instance.okHttpClients,
            ::shouldUseTorForMoneyOperations,
            Amethyst.instance.otsBlockHeightCache,
        )

    init {
        Log.d("AccountRegisterObservers", "Init")

        scope.launch(Dispatchers.Default) {
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

        scope.launch(Dispatchers.Default) {
            delay(1000 * 60 * 1)
            // waits 5 minutes before migrating the list.
            val contactList = userProfile().latestContactList
            val oldChannels = contactList?.taggedEventIds()?.toSet()?.mapNotNull { cache.getChannelIfExists(it) as? PublicChatChannel }

            if (oldChannels != null && oldChannels.isNotEmpty()) {
                Log.d("DB UPGRADE", "Migrating List with ${oldChannels.size} old channels ")
                val existingChannels = publicChatList.flowSet.value

                val needsToUpgrade = oldChannels.filter { it.idHex !in existingChannels }

                Log.d("DB UPGRADE", "Migrating List with ${needsToUpgrade.size} needsToUpgrade ")

                if (needsToUpgrade.isNotEmpty()) {
                    Log.d("DB UPGRADE", "Migrating List")
                    publicChatList.follow(oldChannels, ::sendMyPublicAndPrivateOutbox)
                }
            }

            val oldCommunities = contactList?.taggedAddresses()?.toSet()?.map { cache.getOrCreateAddressableNote(it) }

            if (oldCommunities != null && oldCommunities.isNotEmpty()) {
                Log.d("DB UPGRADE", "Migrating List with ${oldCommunities.size} old communities ")
                val existingCommunities = communityList.flowSet.value

                val needsToUpgrade = oldCommunities.filter { it.idHex !in existingCommunities }

                Log.d("DB UPGRADE", "Migrating List with ${needsToUpgrade.size} needsToUpgrade ")

                if (needsToUpgrade.isNotEmpty()) {
                    Log.d("DB UPGRADE", "Migrating List")
                    communityList.follow(oldCommunities, ::sendMyPublicAndPrivateOutbox)
                }
            }

            val oldHashtags = contactList?.hashtags()?.toSet()

            if (oldHashtags != null && oldHashtags.isNotEmpty()) {
                Log.d("DB UPGRADE", "Migrating List with ${oldHashtags.size} old communities ")
                val existingHashtags = hashtagList.flow.value

                val needsToUpgrade = oldHashtags.filter { it !in existingHashtags }

                Log.d("DB UPGRADE", "Migrating List with ${needsToUpgrade.size} needsToUpgrade ")

                if (needsToUpgrade.isNotEmpty()) {
                    Log.d("DB UPGRADE", "Migrating List")
                    hashtagList.follow(oldHashtags.toList(), ::sendMyPublicAndPrivateOutbox)
                }
            }

            val oldGeohashes = contactList?.geohashes()?.toSet()

            if (oldGeohashes != null && oldGeohashes.isNotEmpty()) {
                Log.d("DB UPGRADE", "Migrating List with ${oldGeohashes.size} old communities ")
                val existingGeohashes = geohashList.flow.value

                val needsToUpgrade = oldGeohashes.filter { it !in existingGeohashes }

                Log.d("DB UPGRADE", "Migrating List with ${needsToUpgrade.size} needsToUpgrade ")

                if (needsToUpgrade.isNotEmpty()) {
                    Log.d("DB UPGRADE", "Migrating List")
                    geohashList.follow(oldGeohashes.toList(), ::sendMyPublicAndPrivateOutbox)
                }
            }
        }
    }
}
