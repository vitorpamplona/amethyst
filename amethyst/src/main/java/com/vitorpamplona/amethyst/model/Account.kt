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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.fasterxml.jackson.module.kotlin.readValue
import com.fonfon.kgeohash.GeoHash
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.model.emphChat.EphemeralChatListState
import com.vitorpamplona.amethyst.model.nip28PublicChats.PublicChatListState
import com.vitorpamplona.amethyst.model.nip30CustomEmojis.EmojiPackState
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.location.LocationState
import com.vitorpamplona.amethyst.service.ots.OtsResolverBuilder
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.nwc.NWCPaymentQueryState
import com.vitorpamplona.amethyst.service.uploads.FileHeader
import com.vitorpamplona.amethyst.tryAndWait
import com.vitorpamplona.amethyst.ui.actions.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerType
import com.vitorpamplona.amethyst.ui.tor.TorType
import com.vitorpamplona.ammolite.relays.Constants
import com.vitorpamplona.ammolite.relays.FeedType
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.ammolite.relays.RelayBriefInfoCache
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import com.vitorpamplona.ammolite.relays.RelaySetupInfoToConnect
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.blossom.BlossomAuthorizationEvent
import com.vitorpamplona.quartz.blossom.BlossomServersEvent
import com.vitorpamplona.quartz.experimental.bounties.BountyAddValueEvent
import com.vitorpamplona.quartz.experimental.edits.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.experimental.edits.TextNoteModificationEvent
import com.vitorpamplona.quartz.experimental.ephemChat.list.EphemeralChatListEvent
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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.jackson.EventMapper
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.addressables.isTaggedAddressableNote
import com.vitorpamplona.quartz.nip01Core.tags.addressables.taggedATags
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
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip02FollowList.ReadWrite
import com.vitorpamplona.quartz.nip02FollowList.tags.ContactTag
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
import com.vitorpamplona.quartz.nip17Dm.base.NIP17Group
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
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
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.list.ChannelListEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.emojis
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.selection.EmojiPackSelectionEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentCommentEvent
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning
import com.vitorpamplona.quartz.nip37Drafts.DraftBuilder
import com.vitorpamplona.quartz.nip37Drafts.DraftEvent
import com.vitorpamplona.quartz.nip38UserStatus.StatusEvent
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.nip47WalletConnect.LnZapPaymentRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip47WalletConnect.Response
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.FollowListEvent
import com.vitorpamplona.quartz.nip51Lists.GeneralListEvent
import com.vitorpamplona.quartz.nip51Lists.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.PeopleListEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
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
import com.vitorpamplona.quartz.nip65RelayList.RelayUrlFormatter
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip68Picture.PictureMeta
import com.vitorpamplona.quartz.nip68Picture.pictureIMeta
import com.vitorpamplona.quartz.nip71Video.VideoHorizontalEvent
import com.vitorpamplona.quartz.nip71Video.VideoMeta
import com.vitorpamplona.quartz.nip71Video.VideoVerticalEvent
import com.vitorpamplona.quartz.nip73ExternalIds.location.GeohashId
import com.vitorpamplona.quartz.nip73ExternalIds.topics.HashtagId
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
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
import com.vitorpamplona.quartz.nip96FileStorage.config.FileServersEvent
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import com.vitorpamplona.quartz.utils.DualCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.czeal.rfc3986.URIReference
import java.math.BigDecimal
import java.util.Base64
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

@OptIn(DelicateCoroutinesApi::class)
@Stable
class Account(
    val settings: AccountSettings = AccountSettings(KeyPair()),
    val signer: NostrSigner = settings.createSigner(),
    val scope: CoroutineScope,
) {
    companion object {
        const val APP_SPECIFIC_DATA_D_TAG = "AmethystSettings"
    }

    var transientHiddenUsers: MutableStateFlow<Set<String>> = MutableStateFlow(setOf())

    @Immutable
    class LiveFollowList(
        val authors: Set<String> = emptySet(),
        val authorsPlusMe: Set<String>,
        val hashtags: Set<String> = emptySet(),
        val geotags: Set<String> = emptySet(),
        val addresses: Set<String> = emptySet(),
    ) {
        val geotagScopes: Set<String> = geotags.mapTo(mutableSetOf<String>()) { GeohashId.toScope(it) }
        val hashtagScopes: Set<String> = hashtags.mapTo(mutableSetOf<String>()) { HashtagId.toScope(it) }
    }

    class FeedsBaseFlows(
        val listName: String,
        val peopleList: StateFlow<NoteState> = MutableStateFlow(NoteState(Note(" "))),
        val kind3: StateFlow<LiveFollowList?> = MutableStateFlow(null),
        val location: StateFlow<LocationState.LocationResult?> = MutableStateFlow(null),
    )

    val connectToRelaysFlow =
        combineTransform(
            getNIP65RelayListFlow(),
            getDMRelayListFlow(),
            getSearchRelayListFlow(),
            getPrivateOutboxRelayListFlow(),
            userProfile().flow().relays.stateFlow,
        ) { nip65RelayList, dmRelayList, searchRelayList, privateOutBox, userProfile ->
            checkNotInMainThread()
            emit(
                normalizeAndCombineRelayListsWithFallbacks(
                    kind3RelayList = kind3Relays(),
                    newDMRelayEvent = dmRelayList.note.event as? ChatMessageRelayListEvent,
                    searchRelayEvent = searchRelayList.note.event as? SearchRelayListEvent,
                    privateOutboxRelayEvent = privateOutBox.note.event as? PrivateOutboxRelayListEvent,
                    nip65RelayEvent = nip65RelayList.note.event as? AdvertisedRelayListEvent,
                ).toTypedArray(),
            )
        }

    private fun normalizeAndCombineRelayListsWithFallbacks(
        kind3RelayList: Array<RelaySetupInfo>? = null,
        newDMRelayEvent: ChatMessageRelayListEvent? = null,
        searchRelayEvent: SearchRelayListEvent? = null,
        privateOutboxRelayEvent: PrivateOutboxRelayListEvent? = null,
        nip65RelayEvent: AdvertisedRelayListEvent? = null,
        localRelayList: Set<String>? = null,
    ) = normalizeAndCombineRelayLists(
        baseRelaySet = kind3RelayList ?: convertLocalRelays(),
        newDMRelayEvent = newDMRelayEvent ?: settings.backupDMRelayList,
        searchRelayEvent = searchRelayEvent ?: settings.backupSearchRelayList,
        privateOutboxRelayEvent = privateOutboxRelayEvent ?: settings.backupPrivateHomeRelayList,
        nip65RelayEvent = nip65RelayEvent ?: settings.backupNIP65RelayList,
        localRelayList = localRelayList ?: settings.localRelayServers,
    )

    private fun normalizeAndCombineRelayLists(
        baseRelaySet: Array<RelaySetupInfo>,
        newDMRelayEvent: ChatMessageRelayListEvent?,
        searchRelayEvent: SearchRelayListEvent?,
        privateOutboxRelayEvent: PrivateOutboxRelayListEvent?,
        nip65RelayEvent: AdvertisedRelayListEvent?,
        localRelayList: Set<String>,
    ): List<RelaySetupInfo> {
        val newDMRelaySet = newDMRelayEvent?.relays()?.map { RelayUrlFormatter.normalize(it) }?.toSet() ?: emptySet()
        val searchRelaySet = (searchRelayEvent?.relays() ?: DefaultSearchRelayList).map { RelayUrlFormatter.normalize(it) }.toSet()
        val nip65RelaySet =
            nip65RelayEvent?.relays()?.map {
                AdvertisedRelayListEvent.AdvertisedRelayInfo(
                    RelayUrlFormatter.normalize(it.relayUrl),
                    it.type,
                )
            }
        val privateOutboxRelaySet = privateOutboxRelayEvent?.relays()?.map { RelayUrlFormatter.normalize(it) }?.toSet() ?: emptySet()
        val localRelaySet = localRelayList.map { RelayUrlFormatter.normalize(it) }.toSet()

        return combineRelayLists(
            baseRelaySet = baseRelaySet,
            newDMRelaySet = newDMRelaySet,
            searchRelaySet = searchRelaySet,
            privateOutboxRelaySet = privateOutboxRelaySet,
            nip65RelaySet = nip65RelaySet,
            localRelaySet = localRelaySet,
        )
    }

    private fun combineRelayLists(
        baseRelaySet: Array<RelaySetupInfo>,
        newDMRelaySet: Set<String>,
        searchRelaySet: Set<String>,
        privateOutboxRelaySet: Set<String>,
        nip65RelaySet: List<AdvertisedRelayListEvent.AdvertisedRelayInfo>?,
        localRelaySet: Set<String>,
    ): List<RelaySetupInfo> {
        // ------
        // DMs
        // ------
        var mappedRelaySet =
            baseRelaySet.map {
                if (newDMRelaySet.contains(it.url)) {
                    RelaySetupInfo(it.url, true, true, it.feedTypes + FeedType.PRIVATE_DMS)
                } else {
                    it
                }
            }

        newDMRelaySet.forEach { newUrl ->
            if (mappedRelaySet.none { it.url == newUrl }) {
                mappedRelaySet = mappedRelaySet +
                    RelaySetupInfo(
                        newUrl,
                        true,
                        true,
                        setOf(
                            FeedType.PRIVATE_DMS,
                        ),
                    )
            }
        }

        // ------
        // SEARCH
        // ------

        mappedRelaySet =
            mappedRelaySet.map {
                if (searchRelaySet.contains(it.url)) {
                    RelaySetupInfo(it.url, true, it.write || false, it.feedTypes + FeedType.SEARCH)
                } else {
                    it
                }
            }

        searchRelaySet.forEach { newUrl ->
            if (mappedRelaySet.none { it.url == newUrl }) {
                mappedRelaySet = mappedRelaySet +
                    RelaySetupInfo(
                        newUrl,
                        true,
                        false,
                        setOf(
                            FeedType.SEARCH,
                        ),
                    )
            }
        }

        // --------------
        // PRIVATE OUTBOX
        // --------------

        mappedRelaySet =
            mappedRelaySet.map {
                if (privateOutboxRelaySet.contains(it.url)) {
                    RelaySetupInfo(it.url, true, true, it.feedTypes + setOf(FeedType.FOLLOWS, FeedType.PUBLIC_CHATS, FeedType.GLOBAL, FeedType.PRIVATE_DMS))
                } else {
                    it
                }
            }

        privateOutboxRelaySet.forEach { newUrl ->
            if (mappedRelaySet.none { it.url == newUrl }) {
                mappedRelaySet = mappedRelaySet +
                    RelaySetupInfo(
                        newUrl,
                        true,
                        true,
                        setOf(
                            FeedType.FOLLOWS,
                            FeedType.PUBLIC_CHATS,
                            FeedType.GLOBAL,
                            FeedType.PRIVATE_DMS,
                        ),
                    )
            }
        }

        // --------------
        // Local Storage
        // --------------

        mappedRelaySet =
            mappedRelaySet.map {
                if (localRelaySet.contains(it.url)) {
                    RelaySetupInfo(it.url, true, true, it.feedTypes + setOf(FeedType.FOLLOWS, FeedType.PUBLIC_CHATS, FeedType.GLOBAL, FeedType.PRIVATE_DMS))
                } else {
                    it
                }
            }

        localRelaySet.forEach { newUrl ->
            if (mappedRelaySet.none { it.url == newUrl }) {
                mappedRelaySet = mappedRelaySet +
                    RelaySetupInfo(
                        newUrl,
                        true,
                        true,
                        setOf(
                            FeedType.FOLLOWS,
                            FeedType.PUBLIC_CHATS,
                            FeedType.GLOBAL,
                            FeedType.PRIVATE_DMS,
                        ),
                    )
            }
        }

        // --------------
        // NIP-65 Public Inbox/Outbox
        // --------------

        mappedRelaySet =
            mappedRelaySet.map { relay ->
                val nip65setup = nip65RelaySet?.firstOrNull { relay.url == it.relayUrl }
                if (nip65setup != null) {
                    val write = nip65setup.type == AdvertisedRelayListEvent.AdvertisedRelayType.BOTH || nip65setup.type == AdvertisedRelayListEvent.AdvertisedRelayType.WRITE

                    RelaySetupInfo(
                        relay.url,
                        true,
                        relay.write || write,
                        relay.feedTypes +
                            setOf(
                                FeedType.FOLLOWS,
                                FeedType.GLOBAL,
                                FeedType.PUBLIC_CHATS,
                            ),
                    )
                } else {
                    relay
                }
            }

        nip65RelaySet?.forEach { newNip65Setup ->
            if (mappedRelaySet.none { it.url == newNip65Setup.relayUrl }) {
                val write = newNip65Setup.type == AdvertisedRelayListEvent.AdvertisedRelayType.BOTH || newNip65Setup.type == AdvertisedRelayListEvent.AdvertisedRelayType.WRITE

                mappedRelaySet = mappedRelaySet +
                    RelaySetupInfo(
                        newNip65Setup.relayUrl,
                        true,
                        write,
                        setOf(
                            FeedType.FOLLOWS,
                            FeedType.PUBLIC_CHATS,
                        ),
                    )
            }
        }
        return mappedRelaySet
    }

    val connectToRelays =
        connectToRelaysFlow
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                normalizeAndCombineRelayListsWithFallbacks(
                    kind3Relays(),
                    getDMRelayList(),
                    getSearchRelayList(),
                    getPrivateOutboxRelayList(),
                    getNIP65RelayList(),
                ).toTypedArray(),
            )

    val connectToRelaysWithProxy =
        combineTransform(
            connectToRelays,
            settings.torSettings.torType,
            settings.torSettings.onionRelaysViaTor,
            settings.torSettings.trustedRelaysViaTor,
        ) { relays, torType, useTorForOnionRelays, useTorForTrustedRelays ->
            emit(
                relays
                    .map {
                        RelaySetupInfoToConnect(
                            it.url,
                            torType != TorType.OFF && checkLocalHostOnionAndThen(it.url, useTorForOnionRelays, useTorForTrustedRelays),
                            it.read,
                            it.write,
                            it.feedTypes,
                        )
                    }.toTypedArray(),
            )
        }.flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                normalizeAndCombineRelayListsWithFallbacks(
                    kind3Relays(),
                    getDMRelayList(),
                    getSearchRelayList(),
                    getPrivateOutboxRelayList(),
                    getNIP65RelayList(),
                ).map {
                    RelaySetupInfoToConnect(
                        it.url,
                        settings.torSettings.torType.value != TorType.OFF &&
                            checkLocalHostOnionAndThen(
                                it.url,
                                settings.torSettings.onionRelaysViaTor.value,
                                settings.torSettings.trustedRelaysViaTor.value,
                            ),
                        it.read,
                        it.write,
                        it.feedTypes,
                    )
                }.toTypedArray(),
            )

    fun buildFollowLists(latestContactList: ContactListEvent?): LiveFollowList {
        // makes sure the output include only valid p tags
        val verifiedFollowingUsers = latestContactList?.verifiedFollowKeySet() ?: emptySet()

        return LiveFollowList(
            authors = verifiedFollowingUsers,
            authorsPlusMe = verifiedFollowingUsers + signer.pubKey,
            hashtags =
                latestContactList
                    ?.unverifiedFollowTagSet()
                    ?.map { it.lowercase() }
                    ?.toSet() ?: emptySet(),
            geotags =
                latestContactList
                    ?.geohashes()
                    ?.toSet() ?: emptySet(),
            addresses =
                latestContactList
                    ?.verifiedFollowAddressSet()
                    ?.toSet() ?: emptySet(),
        )
    }

    fun normalizeDMRelayListWithBackup(note: Note): Set<String> {
        val event = note.event as? ChatMessageRelayListEvent ?: settings.backupDMRelayList
        return event?.relays()?.map { RelayUrlFormatter.normalize(it) }?.toSet() ?: emptySet()
    }

    val normalizedDmRelaySet =
        getDMRelayListFlow()
            .map { normalizeDMRelayListWithBackup(it.note) }
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                normalizeDMRelayListWithBackup(getDMRelayListNote()),
            )

    fun normalizePrivateOutboxRelayListWithBackup(note: Note): Set<String> {
        val event = note.event as? PrivateOutboxRelayListEvent ?: settings.backupPrivateHomeRelayList
        return event?.relays()?.map { RelayUrlFormatter.normalize(it) }?.toSet() ?: emptySet()
    }

    val normalizedPrivateOutBoxRelaySet =
        getPrivateOutboxRelayListFlow()
            .map { normalizePrivateOutboxRelayListWithBackup(it.note) }
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                normalizePrivateOutboxRelayListWithBackup(getPrivateOutboxRelayListNote()),
            )

    fun normalizeNIP65WriteRelayListWithBackup(note: Note): Set<String> {
        val event = note.event as? AdvertisedRelayListEvent ?: settings.backupNIP65RelayList
        return event?.writeRelays()?.map { RelayUrlFormatter.normalize(it) }?.toSet() ?: emptySet()
    }

    val normalizedNIP65WriteRelayList =
        getNIP65RelayListFlow()
            .map { normalizeNIP65WriteRelayListWithBackup(it.note) }
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                normalizeNIP65WriteRelayListWithBackup(getNIP65RelayListNote()),
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val liveKind3FollowsFlow: Flow<LiveFollowList> =
        userProfile().flow().follows.stateFlow.transformLatest {
            emit(buildFollowLists(it.user.latestContactList))
        }

    val liveKind3Follows =
        liveKind3FollowsFlow
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                buildFollowLists(userProfile().latestContactList ?: settings.backupContactList),
            )

    fun loadFlowsFor(listName: String): FeedsBaseFlows =
        when (listName) {
            GLOBAL_FOLLOWS -> FeedsBaseFlows(listName)
            KIND3_FOLLOWS -> FeedsBaseFlows(listName, kind3 = liveKind3Follows)
            AROUND_ME ->
                FeedsBaseFlows(
                    listName,
                    location = Amethyst.instance.locationManager.geohashStateFlow,
                )
            else -> {
                val note = LocalCache.checkGetOrCreateAddressableNote(listName)
                if (note != null) {
                    FeedsBaseFlows(
                        listName,
                        peopleList =
                            note
                                .flow()
                                .metadata.stateFlow,
                    )
                } else {
                    FeedsBaseFlows(listName)
                }
            }
        }

    fun compute50kmLine(geoHash: GeoHash): List<String> {
        val hashes = mutableListOf<String>()

        hashes.add(geoHash.toString())

        var currentGeoHash = geoHash
        repeat(5) {
            currentGeoHash = currentGeoHash.westernNeighbour
            hashes.add(currentGeoHash.toString())
        }

        currentGeoHash = geoHash
        repeat(5) {
            currentGeoHash = currentGeoHash.easternNeighbour
            hashes.add(currentGeoHash.toString())
        }

        return hashes
    }

    fun compute50kmRange(geoHash: GeoHash): List<String> {
        val hashes = mutableListOf<String>()

        hashes.addAll(compute50kmLine(geoHash))

        var currentGeoHash = geoHash
        repeat(5) {
            currentGeoHash = currentGeoHash.northernNeighbour
            hashes.addAll(compute50kmLine(currentGeoHash))
        }

        currentGeoHash = geoHash
        repeat(5) {
            currentGeoHash = currentGeoHash.southernNeighbour
            hashes.addAll(compute50kmLine(currentGeoHash))
        }

        return hashes
    }

    suspend fun mapIntoFollowLists(
        listName: String,
        kind3: LiveFollowList?,
        noteState: NoteState,
        location: LocationState.LocationResult?,
    ): LiveFollowList? =
        if (listName == GLOBAL_FOLLOWS) {
            null
        } else if (listName == KIND3_FOLLOWS) {
            kind3
        } else if (listName == AROUND_ME) {
            val geohashResult = location ?: Amethyst.instance.locationManager.geohashStateFlow.value
            if (geohashResult is LocationState.LocationResult.Success) {
                // 2 neighbors deep = 25x25km
                LiveFollowList(
                    authorsPlusMe = setOf(signer.pubKey),
                    geotags = compute50kmRange(geohashResult.geoHash).toSet(),
                )
            } else {
                LiveFollowList(authorsPlusMe = setOf(signer.pubKey))
            }
        } else {
            val noteEvent = noteState.note.event
            if (noteEvent is GeneralListEvent) {
                waitToDecrypt(noteEvent) ?: LiveFollowList(authorsPlusMe = setOf(signer.pubKey))
            } else if (noteEvent is FollowListEvent) {
                LiveFollowList(authors = noteEvent.pubKeys().toSet(), authorsPlusMe = setOf(signer.pubKey) + noteEvent.pubKeys())
            } else {
                LiveFollowList(authorsPlusMe = setOf(signer.pubKey))
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun combinePeopleListFlows(peopleListFollowsSource: Flow<String>): Flow<LiveFollowList?> =
        peopleListFollowsSource
            .transformLatest { listName ->
                val followList = loadFlowsFor(listName)
                emitAll(
                    combine(followList.kind3, followList.peopleList, followList.location) { kind3, peopleList, location ->
                        mapIntoFollowLists(followList.listName, kind3, peopleList, location)
                    },
                )
            }

    val liveHomeFollowLists: StateFlow<LiveFollowList?> by lazy {
        combinePeopleListFlows(settings.defaultHomeFollowList)
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                runBlocking {
                    loadAndCombineFlows(settings.defaultHomeFollowList.value)
                },
            )
    }

    val liveServerList: StateFlow<List<ServerName>> by lazy {
        combine(getFileServersListFlow(), getBlossomServersListFlow()) { nip96, blossom ->
            mergeServerList(nip96.note.event as? FileServersEvent, blossom.note.event as? BlossomServersEvent)
        }.flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                runBlocking {
                    mergeServerList(getFileServersList(), getBlossomServersList())
                },
            )
    }

    suspend fun loadAndCombineFlows(listName: String): LiveFollowList? {
        val flows = loadFlowsFor(listName)
        return mapIntoFollowLists(
            flows.listName,
            flows.kind3.value,
            flows.peopleList.value,
            flows.location.value,
        )
    }

    /**
     * filter onion and local host from write relays
     * for each user pubkey, a list of valid relays.
     */
    private fun assembleAuthorsPerWriteRelay(
        userList: Map<HexKey, List<String>>,
        hasOnionConnection: Boolean = false,
    ): Map<String, List<HexKey>> {
        checkNotInMainThread()

        val authorsPerRelayUrl = mutableMapOf<String, MutableSet<HexKey>>()
        val relayUrlsPerAuthor = mutableMapOf<HexKey, MutableSet<String>>()

        userList.forEach { userWriteRelayListPair ->
            userWriteRelayListPair.value.forEach { relayUrl ->
                if (!RelayUrlFormatter.isLocalHost(relayUrl) && (hasOnionConnection || !RelayUrlFormatter.isOnion(relayUrl))) {
                    RelayUrlFormatter.normalizeOrNull(relayUrl)?.let { normRelayUrl ->
                        val userSet = authorsPerRelayUrl[normRelayUrl]
                        if (userSet != null) {
                            userSet.add(userWriteRelayListPair.key)
                        } else {
                            authorsPerRelayUrl[normRelayUrl] = mutableSetOf(userWriteRelayListPair.key)
                        }

                        val relaySet = authorsPerRelayUrl[userWriteRelayListPair.key]
                        if (relaySet != null) {
                            relaySet.add(normRelayUrl)
                        } else {
                            relayUrlsPerAuthor[userWriteRelayListPair.key] = mutableSetOf(normRelayUrl)
                        }
                    }
                }
            }
        }

        // for each relay, authors that only use this relay go first.
        // then keeps order by pubkey asc
        val comparator = compareByDescending<HexKey> { relayUrlsPerAuthor[it]?.size ?: 0 }.thenBy { it }

        return authorsPerRelayUrl.mapValues {
            it.value.sortedWith(comparator)
        }
    }

    fun authorsPerRelay(
        followsNIP65RelayLists: List<Note>,
        defaultRelayList: List<String>,
        torType: TorType,
    ): Map<String, List<HexKey>> = authorsPerRelay(followsNIP65RelayLists, defaultRelayList, torType != TorType.OFF)

    fun authorsPerRelay(
        followsNIP65RelayLists: List<Note>,
        defaultRelayList: List<String>,
        acceptOnion: Boolean,
    ): Map<String, List<HexKey>> {
        checkNotInMainThread()

        val defaultSet = defaultRelayList.toSet()

        return assembleAuthorsPerWriteRelay(
            followsNIP65RelayLists
                .mapNotNull
                {
                    val author = (it as? AddressableNote)?.address?.pubKeyHex
                    val event = (it.event as? AdvertisedRelayListEvent)

                    if (event != null) {
                        val authorWriteRelays =
                            event.writeRelays().map {
                                RelayUrlFormatter.normalize(it)
                            }

                        val commonRelaysToMe = authorWriteRelays.filter { it in defaultSet }
                        if (commonRelaysToMe.isNotEmpty()) {
                            event.pubKey to commonRelaysToMe
                        } else {
                            event.pubKey to defaultRelayList
                        }
                    } else {
                        if (author != null) {
                            author to defaultRelayList
                        } else {
                            Log.e("Account", "This author should NEVER be null. Note: ${it.idHex}")
                            null
                        }
                    }
                }.toMap(),
            hasOnionConnection = acceptOnion,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val liveHomeFollowListAdvertizedRelayListFlow: Flow<Array<NoteState>?> =
        liveHomeFollowLists
            .transformLatest { followList ->
                if (followList != null) {
                    emitAll(combine(followList.authorsPlusMe.map { getNIP65RelayListFlow(it) }) { it })
                } else {
                    emit(null)
                }
            }

    val liveHomeListAuthorsPerRelayFlow: Flow<Map<String, List<HexKey>>?> by lazy {
        combineTransform(liveHomeFollowListAdvertizedRelayListFlow, connectToRelays, settings.torSettings.torType) { adverisedRelayList, existing, torStatus ->
            if (adverisedRelayList != null) {
                emit(
                    authorsPerRelay(
                        adverisedRelayList.map { it.note },
                        existing.filter { it.feedTypes.contains(FeedType.FOLLOWS) && it.read }.map { it.url },
                        torStatus,
                    ),
                )
            } else {
                emit(null)
            }
        }
    }

    val liveHomeListAuthorsPerRelay: StateFlow<Map<String, List<HexKey>>?> by lazy {
        liveHomeListAuthorsPerRelayFlow
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                authorsPerRelay(
                    liveHomeFollowLists.value?.authorsPlusMe?.map { getNIP65RelayListNote(it) } ?: emptyList(),
                    connectToRelays.value.filter { it.feedTypes.contains(FeedType.FOLLOWS) && it.read }.map { it.url },
                    settings.torSettings.torType.value,
                ).ifEmpty { null },
            )
    }

    val liveNotificationFollowLists: StateFlow<LiveFollowList?> by lazy {
        combinePeopleListFlows(settings.defaultNotificationFollowList)
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                runBlocking {
                    loadAndCombineFlows(settings.defaultNotificationFollowList.value)
                },
            )
    }

    val liveStoriesFollowLists: StateFlow<LiveFollowList?> by lazy {
        combinePeopleListFlows(settings.defaultStoriesFollowList)
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                runBlocking {
                    loadAndCombineFlows(settings.defaultStoriesFollowList.value)
                },
            )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val liveStoriesFollowListAdvertizedRelayListFlow: Flow<Array<NoteState>?> =
        liveStoriesFollowLists
            .transformLatest { followList ->
                if (followList != null) {
                    emitAll(combine(followList.authorsPlusMe.map { getNIP65RelayListFlow(it) }) { it })
                } else {
                    emit(null)
                }
            }

    val liveStoriesListAuthorsPerRelayFlow: Flow<Map<String, List<String>>?> by lazy {
        combineTransform(liveStoriesFollowListAdvertizedRelayListFlow, connectToRelays, settings.torSettings.torType) { adverisedRelayList, existing, torState ->
            if (adverisedRelayList != null) {
                emit(
                    authorsPerRelay(
                        adverisedRelayList.map { it.note },
                        existing.filter { it.feedTypes.contains(FeedType.FOLLOWS) && it.read }.map { it.url },
                        torState,
                    ),
                )
            } else {
                emit(null)
            }
        }
    }

    val liveStoriesListAuthorsPerRelay: StateFlow<Map<String, List<String>>?> by lazy {
        liveStoriesListAuthorsPerRelayFlow.flowOn(Dispatchers.Default).stateIn(
            scope,
            SharingStarted.Eagerly,
            authorsPerRelay(
                liveStoriesFollowLists.value?.authorsPlusMe?.map { getNIP65RelayListNote(it) } ?: emptyList(),
                connectToRelays.value.filter { it.feedTypes.contains(FeedType.FOLLOWS) && it.read }.map { it.url },
                settings.torSettings.torType.value,
            ).ifEmpty { null },
        )
    }

    val liveDiscoveryFollowLists: StateFlow<LiveFollowList?> by lazy {
        combinePeopleListFlows(settings.defaultDiscoveryFollowList)
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                runBlocking {
                    loadAndCombineFlows(settings.defaultDiscoveryFollowList.value)
                },
            )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val liveDiscoveryFollowListAdvertizedRelayListFlow: Flow<Array<NoteState>?> =
        liveDiscoveryFollowLists
            .transformLatest { followList ->
                if (followList != null) {
                    emitAll(combine(followList.authorsPlusMe.map { getNIP65RelayListFlow(it) }) { it })
                } else {
                    emit(null)
                }
            }

    val liveDiscoveryListAuthorsPerRelayFlow: Flow<Map<String, List<String>>?> by lazy {
        combineTransform(liveDiscoveryFollowListAdvertizedRelayListFlow, connectToRelays, settings.torSettings.torType) { adverisedRelayList, existing, torState ->
            if (adverisedRelayList != null) {
                emit(
                    authorsPerRelay(
                        adverisedRelayList.map { it.note },
                        existing.filter { it.read }.map { it.url },
                        torState,
                    ),
                )
            } else {
                emit(null)
            }
        }
    }

    val liveDiscoveryListAuthorsPerRelay: StateFlow<Map<String, List<String>>?> by lazy {
        liveDiscoveryListAuthorsPerRelayFlow.flowOn(Dispatchers.Default).stateIn(
            scope,
            SharingStarted.Eagerly,
            authorsPerRelay(
                liveDiscoveryFollowLists.value?.authorsPlusMe?.map { getNIP65RelayListNote(it) } ?: emptyList(),
                connectToRelays.value.filter { it.read }.map { it.url },
                settings.torSettings.torType.value,
            ).ifEmpty { null },
        )
    }

    private fun decryptLiveFollows(
        listEvent: GeneralListEvent,
        onReady: (LiveFollowList) -> Unit,
    ) {
        listEvent.privateTags(signer) { privateTagList ->
            val users = (listEvent.taggedUserIds() + listEvent.filterUsers(privateTagList)).toSet()
            onReady(
                LiveFollowList(
                    authors = users,
                    authorsPlusMe = users + userProfile().pubkeyHex,
                    hashtags = (listEvent.hashtags() + listEvent.filterHashtags(privateTagList)).toSet(),
                    geotags = (listEvent.geohashes() + listEvent.filterGeohashes(privateTagList)).toSet(),
                    addresses =
                        (listEvent.taggedATags() + listEvent.filterATags(privateTagList))
                            .map { it.toTag() }
                            .toSet(),
                ),
            )
        }
    }

    fun decryptPeopleList(
        event: GeneralListEvent,
        onReady: (Array<Array<String>>) -> Unit,
    ) = event.privateTags(signer, onReady)

    suspend fun waitToDecrypt(peopleListFollows: GeneralListEvent): LiveFollowList? =
        tryAndWait { continuation ->
            decryptLiveFollows(peopleListFollows) {
                continuation.resume(it)
            }
        }

    @Immutable
    class LiveHiddenUsers(
        val hiddenUsers: Set<String>,
        val spammers: Set<String>,
        val hiddenWords: Set<String>,
        val showSensitiveContent: Boolean?,
    ) {
        // speeds up isHidden calculations
        val hiddenUsersHashCodes = hiddenUsers.mapTo(HashSet()) { it.hashCode() }
        val spammersHashCodes = spammers.mapTo(HashSet()) { it.hashCode() }
        val hiddenWordsCase = hiddenWords.map { DualCase(it.lowercase(), it.uppercase()) }
    }

    suspend fun decryptPeopleList(event: PeopleListEvent?): PeopleListEvent.UsersAndWords {
        if (event == null || !isWriteable()) return PeopleListEvent.UsersAndWords()

        return tryAndWait { continuation ->
            event.publicAndPrivateUsersAndWords(signer) {
                continuation.resume(it)
            }
        } ?: PeopleListEvent.UsersAndWords()
    }

    suspend fun decryptMuteList(event: MuteListEvent?): PeopleListEvent.UsersAndWords {
        if (event == null || !isWriteable()) return PeopleListEvent.UsersAndWords()

        return tryAndWait { continuation ->
            event.publicAndPrivateUsersAndWords(signer) {
                continuation.resume(it)
            }
        } ?: PeopleListEvent.UsersAndWords()
    }

    suspend fun assembleLiveHiddenUsers(
        blockList: Note,
        muteList: Note,
        transientHiddenUsers: Set<String>,
        showSensitiveContent: Boolean?,
    ): LiveHiddenUsers {
        val resultBlockList = decryptPeopleList(blockList.event as? PeopleListEvent)
        val resultMuteList = decryptMuteList(muteList.event as? MuteListEvent)

        return LiveHiddenUsers(
            hiddenUsers = resultBlockList.users + resultMuteList.users,
            hiddenWords = resultBlockList.words + resultMuteList.words,
            spammers = transientHiddenUsers,
            showSensitiveContent = showSensitiveContent,
        )
    }

    val flowHiddenUsers: StateFlow<LiveHiddenUsers> by lazy {
        combineTransform(
            getBlockListNote().flow().metadata.stateFlow,
            getMuteListNote().flow().metadata.stateFlow,
            transientHiddenUsers,
            settings.syncedSettings.security.showSensitiveContent,
        ) { blockList, muteList, transientHiddenUsers, showSensitiveContent ->
            checkNotInMainThread()
            emit(assembleLiveHiddenUsers(blockList.note, muteList.note, transientHiddenUsers, showSensitiveContent))
        }.flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                runBlocking {
                    assembleLiveHiddenUsers(
                        getBlockListNote(),
                        getMuteListNote(),
                        transientHiddenUsers.value,
                        settings.syncedSettings.security.showSensitiveContent.value,
                    )
                },
            )
    }

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

    val emoji = EmojiPackState(signer, LocalCache, scope)
    val ephemeralChatList = EphemeralChatListState(signer, LocalCache, scope)
    val publicChatList = PublicChatListState(signer, LocalCache, scope)

    private var userProfileCache: User? = null

    fun userProfile(): User = userProfileCache ?: LocalCache.getOrCreateUser(signer.pubKey).also { userProfileCache = it }

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
                transientHiddenUsers.update {
                    emptySet()
                }
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
        nip47Update: Nip47WalletConnect.Nip47URI?,
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
        sendNewAppSpecificData(settings.syncedSettings.toInternal())
    }

    private fun sendNewAppSpecificData(toInternal: AccountSyncedSettingsInternal) {
        signer.nip44Encrypt(EventMapper.mapper.writeValueAsString(toInternal), signer.pubKey) { encrypted ->
            AppSpecificDataEvent.create(
                dTag = APP_SPECIFIC_DATA_D_TAG,
                description = encrypted,
                otherTags = emptyArray(),
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        }
    }

    fun sendKind3RelayList(relays: Map<String, ReadWrite>) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList

        if (contactList != null && contactList.tags.isNotEmpty()) {
            ContactListEvent.updateRelayList(
                earlierVersion = contactList,
                relayUse = relays,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        } else {
            ContactListEvent.createFromScratch(
                followUsers = listOf(),
                followTags = listOf(),
                followGeohashes = listOf(),
                followCommunities = listOf(),
                relayUse = relays,
                signer = signer,
            ) {
                // Keep this local to avoid erasing a good contact list.
                // Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        }
    }

    suspend fun countFollowersOf(pubkey: HexKey): Int = LocalCache.users.count { _, it -> it.latestContactList?.isTaggedUser(pubkey) ?: false }

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

        val latest = userProfile().latestMetadata

        val template =
            if (latest != null) {
                MetadataEvent.updateFromPast(
                    latest = latest,
                    name = name,
                    displayName = name,
                    picture = picture,
                    banner = banner,
                    website = website,
                    pronouns = pronouns,
                    about = about,
                    nip05 = nip05,
                    lnAddress = lnAddress,
                    lnURL = lnURL,
                    twitter = twitter,
                    mastodon = mastodon,
                    github = github,
                )
            } else {
                MetadataEvent.createNew(
                    name = name,
                    displayName = name,
                    picture = picture,
                    banner = banner,
                    website = website,
                    pronouns = pronouns,
                    about = about,
                    nip05 = nip05,
                    lnAddress = lnAddress,
                    lnURL = lnURL,
                    twitter = twitter,
                    mastodon = mastodon,
                    github = github,
                )
            }

        signer.sign(template) {
            Amethyst.instance.client.send(it)
            LocalCache.justConsumeMyOwnEvent(it)
        }
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
    ) {
        if (!isWriteable()) return

        if (hasReacted(note, reaction)) {
            // has already liked this note
            return
        }

        val noteEvent = note.event
        if (noteEvent is NIP17Group) {
            val users = noteEvent.groupMembers().toList()

            if (reaction.startsWith(":")) {
                val emojiUrl = EmojiUrlTag.decode(reaction)
                if (emojiUrl != null) {
                    note.toEventHint<Event>()?.let {
                        NIP17Factory().createReactionWithinGroup(
                            emojiUrl = emojiUrl,
                            originalNote = it,
                            to = users,
                            signer = signer,
                        ) {
                            broadcastPrivately(it)
                        }
                    }

                    return
                }
            }

            note.toEventHint<Event>()?.let {
                NIP17Factory().createReactionWithinGroup(
                    content = reaction,
                    originalNote = it,
                    to = users,
                    signer = signer,
                ) {
                    broadcastPrivately(it)
                }
            }
            return
        } else {
            if (reaction.startsWith(":")) {
                val emojiUrl = EmojiUrlTag.decode(reaction)
                if (emojiUrl != null) {
                    note.event?.let {
                        signer.sign(
                            ReactionEvent.build(emojiUrl, EventHintBundle(it, note.relayHintUrl())),
                        ) {
                            Amethyst.instance.client.send(it)
                            LocalCache.justConsumeMyOwnEvent(it)
                        }
                    }

                    return
                }
            }

            note.toEventHint<Event>()?.let {
                signer.sign(
                    ReactionEvent.build(reaction, it),
                ) {
                    Amethyst.instance.client.send(it)
                    LocalCache.justConsumeMyOwnEvent(it)
                }
            }
        }
    }

    fun createZapRequestFor(
        note: Note,
        pollOption: Int?,
        message: String = "",
        zapType: LnZapEvent.ZapType,
        toUser: User?,
        additionalRelays: Set<String>? = null,
        onReady: (LnZapRequestEvent) -> Unit,
    ) {
        if (!isWriteable()) return

        note.event?.let { event ->
            LnZapRequestEvent.create(
                event,
                relays = getReceivingRelays() + (additionalRelays ?: emptySet()),
                signer,
                pollOption,
                message,
                zapType,
                toUser?.pubkeyHex,
                onReady = onReady,
            )
        }
    }

    fun getReceivingRelays(): Set<String> =
        getNIP65RelayList()?.readRelays()?.toSet()
            ?: userProfile()
                .latestContactList
                ?.relays()
                ?.filter { it.value.read }
                ?.keys
                ?.ifEmpty { null }
            ?: settings.localRelays
                .filter { it.read }
                .map { it.url }
                .toSet()

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
                    )

                Amethyst.instance.sources.nwc
                    .subscribe(filter)

                LocalCache.consume(event, zappedNote, true) { it.response(signer) { onResponse(it) } }

                Amethyst.instance.client.sendSingle(
                    signedEvent = event,
                    relayTemplate =
                        RelaySetupInfoToConnect(
                            url = nip47.relayUri,
                            forceProxy = shouldUseTorForTrustedRelays(),
                            read = true,
                            write = true,
                            feedTypes = setOf(FeedType.WALLET_CONNECT),
                        ),
                    onDone = {
                        Amethyst.instance.sources.nwc
                            .unsubscribe(filter)
                    },
                )

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
            userProfile()
                .latestContactList
                ?.relays()
                ?.keys
                ?.ifEmpty { null }
                ?: settings.localRelays.map { it.url }.toSet(),
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
    ) {
        if (!isWriteable()) return

        if (note.hasReport(userProfile(), type)) {
            // has already reported this note
            return
        }

        note.event?.let {
            signer.sign(ReportEvent.build(it, type)) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        }
    }

    suspend fun report(
        user: User,
        type: ReportType,
    ) {
        if (!isWriteable()) return

        if (user.hasReport(userProfile(), type)) {
            // has already reported this note
            return
        }

        val template = ReportEvent.build(user.pubkeyHex, type)
        signer.sign(template) {
            Amethyst.instance.client.send(it)
            LocalCache.justConsumeMyOwnEvent(it)
        }
    }

    fun delete(note: Note) {
        delete(listOf(note))
    }

    fun delete(notes: List<Note>) {
        if (!isWriteable()) return

        val myNoteVersions = notes.filter { it.author == userProfile() }.mapNotNull { it.event as? Event }
        if (myNoteVersions.isNotEmpty()) {
            // chunks in 200 elements to avoid going over the 65KB limit for events.
            myNoteVersions.chunked(200).forEach { chunkedList ->
                signer.sign(
                    DeletionEvent.build(chunkedList),
                ) { deletionEvent ->
                    Amethyst.instance.client.send(deletionEvent)
                    LocalCache.justConsumeMyOwnEvent(deletionEvent)
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
        if (!isWriteable()) return
        val noteEvent = note.event ?: return

        if (note.hasBoostedInTheLast5Minutes(userProfile())) {
            // has already bosted in the past 5mins
            return
        }

        val noteHint = note.relayHintUrl()
        val authorHint = note.author?.bestRelayHint()

        val template =
            if (noteEvent.kind == 1) {
                RepostEvent.build(noteEvent, noteHint, authorHint)
            } else {
                GenericRepostEvent.build(noteEvent, noteHint, authorHint)
            }

        signer.sign(template) {
            Amethyst.instance.client.send(it)
            LocalCache.justConsumeMyOwnEvent(it)
        }
    }

    fun broadcast(note: Note) {
        note.event?.let {
            if (it is WrappedEvent && it.host != null) {
                // download the event and send it.
                it.host?.let {
                    Amethyst.instance.client.sendFilterAndStopOnFirstResponse(
                        filters =
                            listOf(
                                TypedFilter(
                                    setOf(FeedType.FOLLOWS, FeedType.PRIVATE_DMS, FeedType.GLOBAL),
                                    SincePerRelayFilter(
                                        ids = listOf(it.id),
                                    ),
                                ),
                            ),
                        onResponse = {
                            Amethyst.instance.client.send(it)
                        },
                    )
                }
            } else {
                Amethyst.instance.client.send(it)
            }
        }
    }

    suspend fun updateAttestations() {
        Log.d("Pending Attestations", "Updating ${settings.pendingAttestations.value.size} pending attestations")

        val otsResolver = otsResolver()

        settings.pendingAttestations.value.forEach { pair ->
            val otsState = OtsEvent.upgrade(Base64.getDecoder().decode(pair.value), pair.key, otsResolver)

            if (otsState != null) {
                val hint = LocalCache.getNoteIfExists(pair.key)?.toEventHint<Event>()

                val template =
                    if (hint != null) {
                        OtsEvent.build(hint, otsState)
                    } else {
                        OtsEvent.build(pair.key, otsState)
                    }

                signer.sign(template) {
                    LocalCache.justConsumeMyOwnEvent(it)
                    Amethyst.instance.client.send(it)

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

    fun follow(user: User) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList

        if (contactList != null) {
            ContactListEvent.followUser(contactList, user.pubkeyHex, signer) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        } else {
            ContactListEvent.createFromScratch(
                followUsers = listOf(ContactTag(user.pubkeyHex, user.bestRelayHint(), null)),
                followTags = emptyList(),
                followGeohashes = emptyList(),
                followCommunities = emptyList(),
                relayUse =
                    Constants.defaultRelays.associate {
                        it.url to ReadWrite(it.read, it.write)
                    },
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        }
    }

    fun follow(channel: PublicChatChannel) {
        if (!isWriteable()) return

        publicChatList.follow(channel) {
            sendToPrivateOutboxAndLocal(it)
            LocalCache.justConsumeMyOwnEvent(it)
        }
    }

    fun unfollow(channel: PublicChatChannel) {
        if (!isWriteable()) return

        publicChatList.unfollow(channel) {
            sendToPrivateOutboxAndLocal(it)
            LocalCache.justConsumeMyOwnEvent(it)
        }
    }

    fun follow(channel: EphemeralChatChannel) {
        if (!isWriteable()) return

        ephemeralChatList.follow(channel) {
            sendToPrivateOutboxAndLocal(it)
            LocalCache.justConsumeInner(it, RelayBriefInfoCache.get(channel.roomId.relayUrl), true)
        }
    }

    fun unfollow(channel: EphemeralChatChannel) {
        if (!isWriteable()) return

        ephemeralChatList.unfollow(channel) {
            sendToPrivateOutboxAndLocal(it)
            LocalCache.justConsumeInner(it, RelayBriefInfoCache.get(channel.roomId.relayUrl), true)
        }
    }

    fun follow(community: AddressableNote) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList

        if (contactList != null) {
            ContactListEvent.followAddressableEvent(contactList, community.toATag(), signer) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        } else {
            val relays =
                Constants.defaultRelays.associate {
                    it.url to ReadWrite(it.read, it.write)
                }
            ContactListEvent.createFromScratch(
                followUsers = emptyList(),
                followTags = emptyList(),
                followGeohashes = emptyList(),
                followCommunities = listOf(community.toATag()),
                relayUse = relays,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        }
    }

    fun followHashtag(tag: String) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList

        if (contactList != null) {
            ContactListEvent.followHashtag(
                contactList,
                tag,
                signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        } else {
            ContactListEvent.createFromScratch(
                followUsers = emptyList(),
                followTags = listOf(tag),
                followGeohashes = emptyList(),
                followCommunities = emptyList(),
                relayUse =
                    Constants.defaultRelays.associate {
                        it.url to ReadWrite(it.read, it.write)
                    },
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        }
    }

    fun followGeohash(geohash: String) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList

        if (contactList != null) {
            ContactListEvent.followGeohash(
                contactList,
                geohash,
                signer,
                onReady = this::onNewEventCreated,
            )
        } else {
            ContactListEvent.createFromScratch(
                followUsers = emptyList(),
                followTags = emptyList(),
                followGeohashes = listOf(geohash),
                followCommunities = emptyList(),
                relayUse =
                    Constants.defaultRelays.associate {
                        it.url to ReadWrite(it.read, it.write)
                    },
                signer = signer,
                onReady = this::onNewEventCreated,
            )
        }
    }

    fun onNewEventCreated(event: Event) {
        Amethyst.instance.client.send(event)
        LocalCache.justConsumeMyOwnEvent(event)
    }

    fun unfollow(user: User) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList

        if (contactList != null && contactList.tags.isNotEmpty()) {
            ContactListEvent.unfollowUser(
                contactList,
                user.pubkeyHex,
                signer,
                onReady = this::onNewEventCreated,
            )
        }
    }

    suspend fun unfollowHashtag(tag: String) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList

        if (contactList != null && contactList.tags.isNotEmpty()) {
            ContactListEvent.unfollowHashtag(
                contactList,
                tag,
                signer,
                onReady = this::onNewEventCreated,
            )
        }
    }

    suspend fun unfollowGeohash(geohash: String) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList

        if (contactList != null && contactList.tags.isNotEmpty()) {
            ContactListEvent.unfollowGeohash(
                contactList,
                geohash,
                signer,
                onReady = this::onNewEventCreated,
            )
        }
    }

    suspend fun unfollow(community: AddressableNote) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList

        if (contactList != null && contactList.tags.isNotEmpty()) {
            ContactListEvent.unfollowAddressableEvent(
                contactList,
                community.toATag(),
                signer,
                onReady = this::onNewEventCreated,
            )
        }
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
        relayList: List<RelaySetupInfo>,
    ): Note? {
        if (!isWriteable()) return null

        Amethyst.instance.client.send(data, relayList = relayList)
        LocalCache.justConsumeMyOwnEvent(data)

        Amethyst.instance.client.send(signedEvent, relayList = relayList)
        LocalCache.justConsumeMyOwnEvent(signedEvent)

        return LocalCache.getNoteIfExists(signedEvent.id)
    }

    fun consumeNip95(
        data: FileStorageEvent,
        signedEvent: FileStorageHeaderEvent,
    ): Note? {
        LocalCache.justConsumeMyOwnEvent(data)
        LocalCache.justConsumeMyOwnEvent(signedEvent)

        return LocalCache.getNoteIfExists(signedEvent.id)
    }

    fun sendNip95(
        data: FileStorageEvent,
        signedEvent: FileStorageHeaderEvent,
        relayList: List<RelaySetupInfo>,
    ) {
        Amethyst.instance.client.send(data, relayList = relayList)
        Amethyst.instance.client.send(signedEvent, relayList = relayList)
    }

    fun sendNip95Privately(
        data: FileStorageEvent,
        signedEvent: FileStorageHeaderEvent,
        relayList: List<String>,
    ) {
        val connect =
            relayList.map {
                val normalizedUrl = RelayUrlFormatter.normalize(it)
                RelaySetupInfoToConnect(
                    normalizedUrl,
                    shouldUseTorForClean(normalizedUrl),
                    true,
                    true,
                    setOf(FeedType.GLOBAL),
                )
            }

        Amethyst.instance.client.sendPrivately(data, relayList = connect)
        Amethyst.instance.client.sendPrivately(signedEvent, relayList = connect)
    }

    fun sendHeader(
        signedEvent: Event,
        relayList: List<RelaySetupInfo>,
        onReady: (Note) -> Unit,
    ) {
        Amethyst.instance.client.send(signedEvent, relayList = relayList)
        LocalCache.justConsumeMyOwnEvent(signedEvent)

        LocalCache.getNoteIfExists(signedEvent.id)?.let { onReady(it) }
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
        relayList: List<RelaySetupInfo>,
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

        signer.sign(
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
            },
        ) {
            sendHeader(it, relayList = relayList, onReady)
        }
    }

    fun sendHeader(
        url: String,
        magnetUri: String?,
        headerInfo: FileHeader,
        alt: String?,
        contentWarningReason: String?,
        originalHash: String? = null,
        relayList: List<RelaySetupInfo>,
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

        signer.sign(template) {
            sendHeader(it, relayList = relayList, onReady)
        }
    }

    fun <T : Event> signAndSend(
        draftTag: String?,
        template: EventTemplate<T>,
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
                LocalCache.justConsumeMyOwnEvent(it)
                Amethyst.instance.client.send(it)
            }
        }
    }

    fun <T : Event> signAndSendPrivately(
        template: EventTemplate<T>,
        relayList: List<String>,
        onDone: (T) -> Unit = {},
    ) {
        signer.sign(template) {
            LocalCache.justConsumeMyOwnEvent(it)
            Amethyst.instance.client.sendPrivately(it, relayList = convertRelayList(relayList))
            onDone(it)
        }
    }

    fun <T : Event> signAndSendPrivatelyOrBroadcast(
        template: EventTemplate<T>,
        relayList: (T) -> List<String>?,
        onDone: (T) -> Unit = {},
    ) {
        signer.sign(template) {
            LocalCache.justConsumeMyOwnEvent(it)
            val relays = relayList(it)
            if (relays != null) {
                Amethyst.instance.client.sendPrivately(it, relayList = convertRelayList(relays))
            } else {
                Amethyst.instance.client.send(it)
            }
            onDone(it)
        }
    }

    fun <T : Event> signAndSend(
        template: EventTemplate<T>,
        relayList: List<RelaySetupInfo>,
        broadcastNotes: Set<Note>,
    ) {
        signer.sign(template) {
            LocalCache.justConsumeMyOwnEvent(it)
            Amethyst.instance.client.send(it, relayList = relayList)

            broadcastNotes.forEach { it.event?.let { Amethyst.instance.client.send(it, relayList = relayList) } }
        }
    }

    fun <T : Event> signAndSend(
        draftTag: String?,
        template: EventTemplate<T>,
        relayList: List<RelaySetupInfo>,
        broadcastNotes: List<Entity>,
    ) = signAndSend(draftTag, template, relayList, mapEntitiesToNotes(broadcastNotes).toSet())

    fun <T : Event> signAndSend(
        draftTag: String?,
        template: EventTemplate<T>,
        relayList: List<RelaySetupInfo>,
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
                LocalCache.justConsumeMyOwnEvent(it)
                Amethyst.instance.client.send(it, relayList = relayList)

                broadcastNotes.forEach { it.event?.let { Amethyst.instance.client.send(it, relayList = relayList) } }
            }
        }
    }

    fun <T : Event> signAndSendWithList(
        draftTag: String?,
        template: EventTemplate<T>,
        relayList: List<String>,
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
                val connect =
                    relayList.map {
                        val normalizedUrl = RelayUrlFormatter.normalize(it)
                        RelaySetupInfoToConnect(
                            normalizedUrl,
                            shouldUseTorForClean(normalizedUrl),
                            true,
                            true,
                            setOf(FeedType.GLOBAL),
                        )
                    }

                LocalCache.justConsumeMyOwnEvent(it)
                Amethyst.instance.client.sendPrivately(it, relayList = connect)
                broadcastNotes.forEach { it.event?.let { Amethyst.instance.client.sendPrivately(it, relayList = connect) } }
            }
        }
    }

    fun sendTorrentComment(
        draftTag: String?,
        template: EventTemplate<TorrentCommentEvent>,
        broadcastNotes: Set<Note>,
        relayList: List<RelaySetupInfo>,
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
        LocalCache.getAddressableNoteIfExists(key)?.let { note ->
            val noteEvent = note.event
            if (noteEvent is DraftEvent) {
                noteEvent.createDeletedEvent(signer) {
                    Amethyst.instance.client.sendPrivately(
                        it,
                        note.relays.map { it.url }.map {
                            RelaySetupInfoToConnect(
                                it,
                                shouldUseTorForClean(it),
                                false,
                                true,
                                emptySet(),
                            )
                        },
                    )
                    LocalCache.justConsumeMyOwnEvent(it)
                }
            }
            delete(note)
        }
    }

    suspend fun createInteractiveStoryReadingState(
        root: InteractiveStoryBaseEvent,
        rootRelay: String?,
        readingScene: InteractiveStoryBaseEvent,
        readingSceneRelay: String?,
    ) {
        if (!isWriteable()) return

        val template =
            InteractiveStoryReadingStateEvent.build(
                root = root,
                rootRelay = rootRelay,
                currentScene = readingScene,
                currentSceneRelay = readingSceneRelay,
            )

        signer.sign(template) {
            sendToPrivateOutboxAndLocal(it)
        }
    }

    suspend fun updateInteractiveStoryReadingState(
        readingState: InteractiveStoryReadingStateEvent,
        readingScene: InteractiveStoryBaseEvent,
        readingSceneRelay: String?,
    ) {
        if (!isWriteable()) return

        val template =
            InteractiveStoryReadingStateEvent.update(
                base = readingState,
                currentScene = readingScene,
                currentSceneRelay = readingSceneRelay,
            )

        signer.sign(template) {
            sendToPrivateOutboxAndLocal(it)
        }
    }

    fun mapEntitiesToNotes(entities: List<Entity>): List<Note> =
        entities.mapNotNull {
            when (it) {
                is NPub -> null
                is NProfile -> null
                is com.vitorpamplona.quartz.nip19Bech32.entities.Note -> LocalCache.getOrCreateNote(it.hex)
                is NEvent -> LocalCache.getOrCreateNote(it.hex)
                is NEmbed -> LocalCache.getOrCreateNote(it.event.id)
                is NAddress -> LocalCache.checkGetOrCreateAddressableNote(it.aTag())
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
        relayList: List<RelaySetupInfo>,
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
        relayList: List<RelaySetupInfo>,
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
        relayList: List<RelaySetupInfo>,
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

        signAndSend(draftTag, template, relayList, setOf(bounty))
    }

    fun sendEdit(
        message: String,
        originalNote: Note,
        notify: HexKey?,
        summary: String? = null,
        relayList: List<RelaySetupInfo>,
    ) {
        if (!isWriteable()) return

        val idHex = originalNote.event?.id ?: return

        TextNoteModificationEvent.create(
            content = message,
            eventId = idHex,
            notify = notify,
            summary = summary,
            signer = signer,
        ) {
            LocalCache.justConsumeMyOwnEvent(it)
            Amethyst.instance.client.send(it, relayList = relayList)
        }
    }

    fun sendPrivateMessage(
        message: String,
        toUser: User,
        replyingTo: Note? = null,
        zapReceiver: List<ZapSplitSetup>? = null,
        contentWarningReason: String? = null,
        zapRaiserAmount: Long? = null,
        geohash: String? = null,
        imetas: List<IMetaTag>? = null,
        emojis: List<EmojiUrlTag>? = null,
        draftTag: String?,
    ) {
        sendPrivateMessage(
            message,
            toUser.toPTag(),
            replyingTo,
            zapReceiver,
            contentWarningReason,
            zapRaiserAmount,
            geohash,
            imetas,
            emojis,
            draftTag,
        )
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

            signAndSend(draftTag, template)
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

    fun getPrivateOutBoxRelayList(): List<RelaySetupInfoToConnect> =
        normalizedPrivateOutBoxRelaySet.value.map {
            RelaySetupInfoToConnect(
                it,
                shouldUseTorForClean(it),
                true,
                true,
                emptySet(),
            )
        }

    fun sendDraftEvent(draftEvent: DraftEvent) {
        sendToPrivateOutboxAndLocal(draftEvent)
    }

    fun sendToPrivateOutboxAndLocal(event: Event) {
        val relayList = normalizedPrivateOutBoxRelaySet.value + settings.localRelayServers
        if (relayList.isNotEmpty()) {
            Amethyst.instance.client.sendPrivately(event, convertRelayList(relayList.toList()))
        } else {
            Amethyst.instance.client.send(event)
        }
        LocalCache.justConsumeMyOwnEvent(event)
    }

    fun convertRelayList(broadcast: List<String>): List<RelaySetupInfoToConnect> =
        broadcast.map {
            val normalizedUrl = RelayUrlFormatter.normalize(it)
            RelaySetupInfoToConnect(
                normalizedUrl,
                shouldUseTorForClean(normalizedUrl),
                true,
                true,
                setOf(FeedType.GLOBAL),
            )
        }

    fun broadcastPrivately(signedEvents: NIP17Factory.Result) {
        val mine = signedEvents.wraps.filter { (it.recipientPubKey() == signer.pubKey) }

        mine.forEach { giftWrap ->
            giftWrap.unwrap(signer) { gift ->
                if (gift is SealedRumorEvent) {
                    gift.unseal(signer) { rumor ->
                        LocalCache.justConsumeMyOwnEvent(rumor)
                    }
                }

                LocalCache.justConsumeMyOwnEvent(gift)
            }

            LocalCache.justConsumeMyOwnEvent(giftWrap)
        }

        val id = mine.firstOrNull()?.id
        val mineNote = if (id == null) null else LocalCache.getNoteIfExists(id)

        signedEvents.wraps.forEach { wrap ->
            // Creates an alias
            if (mineNote != null && wrap.recipientPubKey() != signer.pubKey) {
                LocalCache.getOrAddAliasNote(wrap.id, mineNote)
            }

            val receiver = wrap.recipientPubKey()
            if (receiver != null) {
                val relayList =
                    (
                        LocalCache
                            .getAddressableNoteIfExists(ChatMessageRelayListEvent.createAddressTag(receiver))
                            ?.event as? ChatMessageRelayListEvent
                    )?.relays()?.ifEmpty { null }?.map {
                        val normalizedUrl = RelayUrlFormatter.normalize(it)
                        RelaySetupInfoToConnect(
                            normalizedUrl,
                            shouldUseTorForClean(normalizedUrl),
                            false,
                            true,
                            feedTypes = setOf(FeedType.PRIVATE_DMS),
                        )
                    }

                if (relayList != null) {
                    Amethyst.instance.client.sendPrivately(signedEvent = wrap, relayList = relayList)
                } else {
                    Amethyst.instance.client.send(wrap)
                }
            } else {
                Amethyst.instance.client.send(wrap)
            }
        }
    }

    fun updateStatus(
        oldStatus: AddressableNote,
        newStatus: String,
    ) {
        if (!isWriteable()) return
        val oldEvent = oldStatus.event as? StatusEvent ?: return

        StatusEvent.update(oldEvent, newStatus, signer) {
            Amethyst.instance.client.send(it)
            LocalCache.justConsumeMyOwnEvent(it)
        }
    }

    fun createStatus(newStatus: String) {
        if (!isWriteable()) return

        StatusEvent.create(newStatus, "general", expiration = null, signer) {
            Amethyst.instance.client.send(it)
            LocalCache.justConsumeMyOwnEvent(it)
        }
    }

    fun deleteStatus(oldStatus: AddressableNote) {
        if (!isWriteable()) return
        val oldEvent = oldStatus.event as? StatusEvent ?: return

        StatusEvent.clear(oldEvent, signer) { event ->
            Amethyst.instance.client.send(event)
            LocalCache.justConsumeMyOwnEvent(event)

            signer.sign(
                DeletionEvent.buildForVersionOnly(listOf(event)),
            ) { event2 ->
                Amethyst.instance.client.send(event2)
                LocalCache.justConsumeMyOwnEvent(event2)
            }
        }
    }

    fun removeEmojiPack(
        usersEmojiList: Note,
        emojiPack: Note,
    ) {
        if (!isWriteable()) return

        val noteEvent = usersEmojiList.event
        if (noteEvent !is EmojiPackSelectionEvent) return
        val emojiPackEvent = emojiPack.event
        if (emojiPackEvent !is EmojiPackEvent) return

        signer.sign(EmojiPackSelectionEvent.remove(noteEvent, emojiPackEvent)) {
            Amethyst.instance.client.send(it)
            LocalCache.justConsumeMyOwnEvent(it)
        }
    }

    fun addEmojiPack(
        usersEmojiList: Note,
        emojiPack: Note,
    ) {
        if (!isWriteable()) return
        val emojiPackEvent = emojiPack.event
        if (emojiPackEvent !is EmojiPackEvent) return

        val eventHint = emojiPack.toEventHint<EmojiPackEvent>() ?: return

        if (usersEmojiList.event == null) {
            signer.sign(EmojiPackSelectionEvent.build(listOf(eventHint))) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        } else {
            val noteEvent = usersEmojiList.event
            if (noteEvent !is EmojiPackSelectionEvent) return

            signer.sign(EmojiPackSelectionEvent.add(noteEvent, eventHint)) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        }
    }

    fun addToGallery(
        idHex: HexKey,
        url: String,
        relay: String?,
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
            Amethyst.instance.client.send(it)
            LocalCache.justConsumeMyOwnEvent(it)
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
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        } else {
            BookmarkListEvent.addEvent(
                userProfile().latestBookmarkList,
                note.idHex,
                isPrivate,
                signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
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
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        } else {
            BookmarkListEvent.removeEvent(
                bookmarks,
                note.idHex,
                isPrivate,
                signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        }
    }

    fun sendAuthEvent(
        relay: Relay,
        challenge: String,
    ) {
        createAuthEvent(relay.url, challenge) {
            Amethyst.instance.client.sendIfExists(it, relay)
        }
    }

    fun createAuthEvent(
        relayUrl: String,
        challenge: String,
        onReady: (RelayAuthEvent) -> Unit,
    ) {
        if (!isWriteable()) return

        RelayAuthEvent.create(relayUrl, challenge, signer, onReady = onReady)
    }

    fun createAuthEvent(
        relayUrls: List<String>,
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

        if (note is AddressableNote) {
            return userProfile().latestBookmarkList?.isTaggedAddressableNote(note.idHex) == true
        } else {
            return userProfile().latestBookmarkList?.isTaggedEvent(note.idHex) == true
        }
    }

    fun getAppSpecificDataNote() = LocalCache.getOrCreateAddressableNote(AppSpecificDataEvent.createAddress(userProfile().pubkeyHex, APP_SPECIFIC_DATA_D_TAG))

    fun getAppSpecificDataFlow(): StateFlow<NoteState> = getAppSpecificDataNote().flow().metadata.stateFlow

    fun getBlockListNote() = LocalCache.getOrCreateAddressableNote(PeopleListEvent.createBlockAddress(userProfile().pubkeyHex))

    fun getMuteListNote() = LocalCache.getOrCreateAddressableNote(MuteListEvent.createAddress(userProfile().pubkeyHex))

    fun getMuteListFlow(): StateFlow<NoteState> = getMuteListNote().flow().metadata.stateFlow

    fun getBlockList(): PeopleListEvent? = getBlockListNote().event as? PeopleListEvent

    fun getMuteList(): MuteListEvent? = getMuteListNote().event as? MuteListEvent

    fun hideWord(word: String) {
        val muteList = getMuteList()

        if (muteList != null) {
            MuteListEvent.addWord(
                earlierVersion = muteList,
                word = word,
                isPrivate = true,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        } else {
            MuteListEvent.createListWithWord(
                word = word,
                isPrivate = true,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        }
    }

    fun showWord(word: String) {
        val blockList = getBlockList()

        if (blockList != null) {
            PeopleListEvent.removeWord(
                earlierVersion = blockList,
                word = word,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        }

        val muteList = getMuteList()

        if (muteList != null) {
            MuteListEvent.removeWord(
                earlierVersion = muteList,
                word = word,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        }
    }

    fun hideUser(pubkeyHex: String) {
        val muteList = getMuteList()

        if (muteList != null) {
            MuteListEvent.addUser(
                earlierVersion = muteList,
                pubKeyHex = pubkeyHex,
                isPrivate = true,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        } else {
            MuteListEvent.createListWithUser(
                pubKeyHex = pubkeyHex,
                isPrivate = true,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        }
    }

    fun showUser(pubkeyHex: String) {
        val blockList = getBlockList()

        if (blockList != null) {
            PeopleListEvent.removeUser(
                earlierVersion = blockList,
                pubKeyHex = pubkeyHex,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        }

        val muteList = getMuteList()

        if (muteList != null) {
            MuteListEvent.removeUser(
                earlierVersion = muteList,
                pubKeyHex = pubkeyHex,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        }

        transientHiddenUsers.update {
            it - pubkeyHex
        }
    }

    fun requestDVMContentDiscovery(
        dvmPublicKey: String,
        onReady: (event: NIP90ContentDiscoveryRequestEvent) -> Unit,
    ) {
        NIP90ContentDiscoveryRequestEvent.create(dvmPublicKey, signer.pubKey, getReceivingRelays(), signer) {
            val relayList =
                (
                    LocalCache
                        .getAddressableNoteIfExists(
                            AdvertisedRelayListEvent.createAddressTag(dvmPublicKey),
                        )?.event as? AdvertisedRelayListEvent
                )?.readRelays()?.ifEmpty { null }?.map {
                    val normalizedUrl = RelayUrlFormatter.normalize(it)
                    RelaySetupInfoToConnect(
                        normalizedUrl,
                        shouldUseTorForClean(normalizedUrl),
                        true,
                        true,
                        setOf(FeedType.GLOBAL),
                    )
                }

            if (relayList != null) {
                Amethyst.instance.client.sendPrivately(it, relayList)
            } else {
                Amethyst.instance.client.send(it)
            }
            LocalCache.justConsumeMyOwnEvent(it)
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

    // Takes a User's relay list and adds the types of feeds they are active for.
    fun kind3Relays(): Array<RelaySetupInfo>? {
        val usersRelayList =
            (userProfile().latestContactList ?: settings.backupContactList)
                ?.relays()
                ?.map {
                    val url = RelayUrlFormatter.normalize(it.key)

                    val localFeedTypes =
                        settings.localRelays
                            .firstOrNull { localRelay -> RelayUrlFormatter.normalize(localRelay.url) == url }
                            ?.feedTypes
                            ?.minus(setOf(FeedType.SEARCH, FeedType.WALLET_CONNECT))
                            ?: Constants.defaultRelays
                                .filter { defaultRelay -> RelayUrlFormatter.normalize(defaultRelay.url) == url }
                                .firstOrNull()
                                ?.feedTypes
                            ?: Constants.activeTypesGlobalChats

                    RelaySetupInfo(url, it.value.read, it.value.write, localFeedTypes)
                }?.ifEmpty { null } ?: return null

        return usersRelayList.toTypedArray()
    }

    fun convertLocalRelays(): Array<RelaySetupInfo> =
        settings.localRelays
            .map {
                RelaySetupInfo(
                    RelayUrlFormatter.normalize(it.url),
                    it.read,
                    it.write,
                    it.feedTypes.minus(setOf(FeedType.SEARCH, FeedType.WALLET_CONNECT)),
                )
            }.toTypedArray()

    fun activeGlobalRelays(): Array<String> =
        connectToRelays.value
            .filter { it.feedTypes.contains(FeedType.GLOBAL) }
            .map { it.url }
            .toTypedArray()

    fun activeWriteRelays(): List<RelaySetupInfo> = connectToRelays.value.filter { it.write }

    fun isAllHidden(users: Set<HexKey>): Boolean = users.all { isHidden(it) }

    fun isHidden(user: User) = isHidden(user.pubkeyHex)

    fun isHidden(userHex: String): Boolean =
        flowHiddenUsers.value.hiddenUsers.contains(userHex) ||
            flowHiddenUsers.value.spammers.contains(userHex)

    fun followingKeySet(): Set<HexKey> = liveKind3Follows.value.authors

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
            note.reportsBy(liveKind3Follows.value.authorsPlusMe) +
                (note.author?.reportsBy(liveKind3Follows.value.authorsPlusMe) ?: emptyList()) +
                innerReports
        ).toSet()
    }

    fun saveKind3RelayList(value: List<RelaySetupInfo>) {
        settings.updateLocalRelays(value.toSet())
        sendKind3RelayList(
            value.associate { it.url to ReadWrite(it.read, it.write) },
        )
    }

    fun getDMRelayListNote(): AddressableNote = LocalCache.getOrCreateAddressableNote(ChatMessageRelayListEvent.createAddress(signer.pubKey))

    fun getDMRelayListFlow(): StateFlow<NoteState> = getDMRelayListNote().flow().metadata.stateFlow

    fun getDMRelayList(): ChatMessageRelayListEvent? = getDMRelayListNote().event as? ChatMessageRelayListEvent

    fun saveDMRelayList(dmRelays: List<String>) {
        if (!isWriteable()) return

        val relayListForDMs = getDMRelayList()
        if (relayListForDMs != null && relayListForDMs.tags.isNotEmpty()) {
            ChatMessageRelayListEvent.updateRelayList(
                earlierVersion = relayListForDMs,
                relays = dmRelays,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        } else {
            ChatMessageRelayListEvent.createFromScratch(
                relays = dmRelays,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        }
    }

    fun getPrivateOutboxRelayListNote(): AddressableNote =
        LocalCache.getOrCreateAddressableNote(
            PrivateOutboxRelayListEvent.createAddress(signer.pubKey),
        )

    fun getPrivateOutboxRelayListFlow(): StateFlow<NoteState> = getPrivateOutboxRelayListNote().flow().metadata.stateFlow

    fun getPrivateOutboxRelayList(): PrivateOutboxRelayListEvent? = getPrivateOutboxRelayListNote().event as? PrivateOutboxRelayListEvent

    fun savePrivateOutboxRelayList(relays: List<String>) {
        if (!isWriteable()) return

        val relayListForPrivateOutbox = getPrivateOutboxRelayList()

        if (relayListForPrivateOutbox != null && !relayListForPrivateOutbox.cachedPrivateTags().isNullOrEmpty()) {
            PrivateOutboxRelayListEvent.updateRelayList(
                earlierVersion = relayListForPrivateOutbox,
                relays = relays,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        } else {
            PrivateOutboxRelayListEvent.createFromScratch(
                relays = relays,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        }
    }

    fun getSearchRelayListNote(): AddressableNote =
        LocalCache.getOrCreateAddressableNote(
            SearchRelayListEvent.createAddress(signer.pubKey),
        )

    fun getSearchRelayListFlow(): StateFlow<NoteState> = getSearchRelayListNote().flow().metadata.stateFlow

    fun getSearchRelayList(): SearchRelayListEvent? = getSearchRelayListNote().event as? SearchRelayListEvent

    fun saveSearchRelayList(searchRelays: List<String>) {
        if (!isWriteable()) return

        val relayListForSearch = getSearchRelayList()

        if (relayListForSearch != null && relayListForSearch.tags.isNotEmpty()) {
            SearchRelayListEvent.updateRelayList(
                earlierVersion = relayListForSearch,
                relays = searchRelays,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        } else {
            SearchRelayListEvent.createFromScratch(
                relays = searchRelays,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        }
    }

    fun getNIP65RelayListNote(pubkey: HexKey = signer.pubKey): AddressableNote =
        LocalCache.getOrCreateAddressableNote(
            AdvertisedRelayListEvent.createAddress(pubkey),
        )

    fun getNIP65RelayListFlow(pubkey: HexKey = signer.pubKey): StateFlow<NoteState> = getNIP65RelayListNote(pubkey).flow().metadata.stateFlow

    fun getNIP65RelayList(pubkey: HexKey = signer.pubKey): AdvertisedRelayListEvent? = getNIP65RelayListNote(pubkey).event as? AdvertisedRelayListEvent

    fun sendNip65RelayList(relays: List<AdvertisedRelayListEvent.AdvertisedRelayInfo>) {
        if (!isWriteable()) return

        val nip65RelayList = getNIP65RelayList()

        if (nip65RelayList != null) {
            AdvertisedRelayListEvent.updateRelayList(
                earlierVersion = nip65RelayList,
                relays = relays,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        } else {
            AdvertisedRelayListEvent.createFromScratch(
                relays = relays,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        }
    }

    fun getFileServersList(): FileServersEvent? = getFileServersNote().event as? FileServersEvent

    fun getFileServersListFlow(): StateFlow<NoteState> = getFileServersNote().flow().metadata.stateFlow

    fun getFileServersNote(): AddressableNote = LocalCache.getOrCreateAddressableNote(FileServersEvent.createAddress(userProfile().pubkeyHex))

    fun getBlossomServersList(): BlossomServersEvent? = getBlossomServersNote().event as? BlossomServersEvent

    fun getBlossomServersListFlow(): StateFlow<NoteState> = getBlossomServersNote().flow().metadata.stateFlow

    fun getBlossomServersNote(): AddressableNote = LocalCache.getOrCreateAddressableNote(BlossomServersEvent.createAddress(userProfile().pubkeyHex))

    fun host(url: String): String =
        try {
            URIReference.parse(url).host.value
        } catch (e: Exception) {
            url
        }

    fun mergeServerList(
        nip96: FileServersEvent?,
        blossom: BlossomServersEvent?,
    ): List<ServerName> {
        val nip96servers = nip96?.servers()?.map { ServerName(host(it), it, ServerType.NIP96) } ?: emptyList()
        val blossomServers = blossom?.servers()?.map { ServerName(host(it), it, ServerType.Blossom) } ?: emptyList()

        val result = (nip96servers + blossomServers).ifEmpty { DEFAULT_MEDIA_SERVERS }

        return result + ServerName("NIP95", "", ServerType.NIP95)
    }

    fun sendFileServersList(servers: List<String>) {
        if (!isWriteable()) return

        val serverList = getFileServersList()

        val template =
            if (serverList != null && serverList.tags.isNotEmpty()) {
                FileServersEvent.replaceServers(serverList, servers)
            } else {
                FileServersEvent.build(servers)
            }

        signer.sign(template) {
            Amethyst.instance.client.send(it)
            LocalCache.justConsumeMyOwnEvent(it)
        }
    }

    fun sendBlossomServersList(servers: List<String>) {
        if (!isWriteable()) return

        val serverList = getBlossomServersList()

        if (serverList != null && serverList.tags.isNotEmpty()) {
            BlossomServersEvent.updateRelayList(
                earlierVersion = serverList,
                relays = servers,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        } else {
            BlossomServersEvent.createFromScratch(
                relays = servers,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsumeMyOwnEvent(it)
            }
        }
    }

    fun getAllPeopleLists(): List<AddressableNote> = getAllPeopleLists(signer.pubKey)

    fun getAllPeopleLists(pubkey: HexKey): List<AddressableNote> =
        LocalCache.addressables
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
        if (isLocalHost(normalizedUrl)) {
            false
        } else if (isOnionUrl(normalizedUrl)) {
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

    fun shouldUseTorForDirty(dirtyUrl: String) = shouldUseTorForClean(RelayUrlFormatter.normalize(dirtyUrl))

    fun shouldUseTorForClean(normalizedUrl: String) =
        when (settings.torSettings.torType.value) {
            TorType.OFF -> false
            TorType.INTERNAL -> shouldUseTor(normalizedUrl)
            TorType.EXTERNAL -> shouldUseTor(normalizedUrl)
        }

    private fun checkLocalHostOnionAndThen(
        normalizedUrl: String,
        final: Boolean,
    ): Boolean = checkLocalHostOnionAndThen(normalizedUrl, settings.torSettings.onionRelaysViaTor.value, final)

    private fun checkLocalHostOnionAndThen(
        normalizedUrl: String,
        isOnionRelaysActive: Boolean,
        final: Boolean,
    ): Boolean =
        if (isLocalHost(normalizedUrl)) {
            false
        } else if (isOnionUrl(normalizedUrl)) {
            isOnionRelaysActive
        } else {
            final
        }

    private fun shouldUseTor(normalizedUrl: String): Boolean =
        if (isLocalHost(normalizedUrl)) {
            false
        } else if (isOnionUrl(normalizedUrl)) {
            settings.torSettings.onionRelaysViaTor.value
        } else if (isDMRelay(normalizedUrl)) {
            settings.torSettings.dmRelaysViaTor.value
        } else if (isTrustedRelay(normalizedUrl)) {
            settings.torSettings.trustedRelaysViaTor.value
        } else {
            settings.torSettings.newRelaysViaTor.value
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

    fun isLocalHost(url: String) = url.contains("//127.0.0.1") || url.contains("//localhost")

    fun isOnionUrl(url: String) = url.contains(".onion")

    fun isDMRelay(url: String) = url in normalizedDmRelaySet.value

    fun isTrustedRelay(url: String): Boolean = connectToRelays.value.any { it.url == url } || url == settings.zapPaymentRequest?.relayUri

    fun otsResolver(): OtsResolver =
        OtsResolverBuilder().build(
            Amethyst.instance.okHttpClients,
            ::shouldUseTorForMoneyOperations,
            Amethyst.instance.otsBlockHeightCache,
        )

    init {
        Log.d("AccountRegisterObservers", "Init")
        settings.backupContactList?.let {
            Log.d("AccountRegisterObservers", "Loading saved contacts ${it.toJson()}")

            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) { LocalCache.justConsumeMyOwnEvent(it) }
        }

        settings.backupUserMetadata?.let {
            Log.d("AccountRegisterObservers", "Loading saved user metadata ${it.toJson()}")

            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) { LocalCache.justConsumeMyOwnEvent(it) }
        }

        settings.backupDMRelayList?.let {
            Log.d("AccountRegisterObservers", "Loading saved DM Relay List ${it.toJson()}")
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) { LocalCache.justConsumeMyOwnEvent(it) }
        }

        settings.backupNIP65RelayList?.let {
            Log.d("AccountRegisterObservers", "Loading saved nip65 relay list ${it.toJson()}")
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) { LocalCache.justConsumeMyOwnEvent(it) }
        }

        settings.backupSearchRelayList?.let {
            Log.d("AccountRegisterObservers", "Loading saved search relay list ${it.toJson()}")
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) { LocalCache.justConsumeMyOwnEvent(it) }
        }

        settings.backupPrivateHomeRelayList?.let { event ->
            Log.d("AccountRegisterObservers", "Loading saved private home relay list ${event.toJson()}")
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) {
                event.privateTags(signer) {
                    LocalCache.justConsumeMyOwnEvent(event)
                }
            }
        }

        settings.backupAppSpecificData?.let { event ->
            Log.d("AccountRegisterObservers", "Loading saved app specific data ${event.toJson()}")
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) {
                LocalCache.justConsumeMyOwnEvent(event)
                signer.decrypt(event.content, event.pubKey) { decrypted ->
                    try {
                        val syncedSettings = EventMapper.mapper.readValue<AccountSyncedSettingsInternal>(decrypted)
                        settings.syncedSettings.updateFrom(syncedSettings)
                    } catch (e: Throwable) {
                        if (e is CancellationException) throw e
                        Log.w("LocalPreferences", "Error Decoding latestAppSpecificData from Preferences with value $decrypted", e)
                        e.printStackTrace()
                        AccountSyncedSettingsInternal()
                    }
                }
            }
        }

        settings.backupMuteList?.let { event ->
            Log.d("AccountRegisterObservers", "Loading saved mute list ${event.toJson()}")
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) {
                event.privateTags(signer) {
                    LocalCache.justConsumeMyOwnEvent(event)
                }
            }
        }

        settings.backupEphemeralChatList?.let { event ->
            Log.d("AccountRegisterObservers", "Loading saved ephemeral chat list ${event.toJson()}")
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) {
                event.privateTags(signer) {
                    LocalCache.justConsumeMyOwnEvent(event)
                }
            }
        }

        settings.backupChannelList?.let { event ->
            Log.d("AccountRegisterObservers", "Loading saved channel list ${event.toJson()}")
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) {
                event.privateTags(signer) {
                    LocalCache.justConsumeMyOwnEvent(event)
                }
            }
        }

        // saves contact list for the next time.
        scope.launch(Dispatchers.Default) {
            Log.d("AccountRegisterObservers", "Kind 0 Collector Start")
            userProfile().flow().metadata.stateFlow.collect {
                Log.d("AccountRegisterObservers", "Updating Kind 0 ${userProfile().toBestDisplayName()}")
                settings.updateUserMetadata(userProfile().latestMetadata)
            }
        }

        // saves contact list for the next time.
        scope.launch(Dispatchers.Default) {
            Log.d("AccountRegisterObservers", "Kind 3 Collector Start")
            userProfile().flow().follows.stateFlow.collect {
                Log.d("AccountRegisterObservers", "Updating Kind 3 ${userProfile().toBestDisplayName()}")
                settings.updateContactListTo(userProfile().latestContactList)
            }
        }

        scope.launch(Dispatchers.Default) {
            Log.d("AccountRegisterObservers", "NIP-17 Relay List Collector Start")
            getDMRelayListFlow().collect {
                Log.d("AccountRegisterObservers", "Updating DM Relay List for ${userProfile().toBestDisplayName()}")
                (it.note.event as? ChatMessageRelayListEvent)?.let {
                    settings.updateDMRelayList(it)
                }
            }
        }

        scope.launch(Dispatchers.Default) {
            Log.d("AccountRegisterObservers", "NIP-65 Relay List Collector Start")
            getNIP65RelayListFlow().collect {
                Log.d("AccountRegisterObservers", "Updating NIP-65 List for ${userProfile().toBestDisplayName()}")
                (it.note.event as? AdvertisedRelayListEvent)?.let {
                    settings.updateNIP65RelayList(it)
                }
            }
        }

        scope.launch(Dispatchers.Default) {
            Log.d("AccountRegisterObservers", "Search Relay List Collector Start")
            getSearchRelayListFlow().collect {
                Log.d("AccountRegisterObservers", "Updating Search Relay List for ${userProfile().toBestDisplayName()}")
                (it.note.event as? SearchRelayListEvent)?.let {
                    settings.updateSearchRelayList(it)
                }
            }
        }

        scope.launch(Dispatchers.Default) {
            Log.d("AccountRegisterObservers", "Private Home Relay List Collector Start")
            getPrivateOutboxRelayListFlow().collect {
                Log.d("AccountRegisterObservers", "Updating Private Home Relay List for ${userProfile().toBestDisplayName()}")
                (it.note.event as? PrivateOutboxRelayListEvent)?.let {
                    settings.updatePrivateHomeRelayList(it)
                }
            }
        }

        scope.launch(Dispatchers.Default) {
            Log.d("AccountRegisterObservers", "Mute List Collector Start")
            getMuteListFlow().collect {
                Log.d("AccountRegisterObservers", "Updating Mute List for ${userProfile().toBestDisplayName()}")
                (it.note.event as? MuteListEvent)?.let {
                    settings.updateMuteList(it)
                }
            }
        }

        scope.launch(Dispatchers.Default) {
            Log.d("AccountRegisterObservers", "Channel List Collector Start")
            publicChatList.getChannelListFlow().collect {
                Log.d("AccountRegisterObservers", "Channel List for ${userProfile().toBestDisplayName()}")
                (it.note.event as? ChannelListEvent)?.let {
                    settings.updateChannelListTo(it)
                }
            }
        }

        scope.launch(Dispatchers.Default) {
            Log.d("AccountRegisterObservers", "EphemeralChatList Collector Start")
            ephemeralChatList.getEphemeralChatListFlow().collect {
                Log.d("AccountRegisterObservers", "EphemeralChatList List for ${userProfile().toBestDisplayName()}")
                (it.note.event as? EphemeralChatListEvent)?.let {
                    settings.updateEphemeralChatListTo(it)
                }
            }
        }

        scope.launch(Dispatchers.Default) {
            Log.d("AccountRegisterObservers", "AppSpecificData Collector Start")
            getAppSpecificDataFlow().collect {
                Log.d("AccountRegisterObservers", "Updating AppSpecificData for ${userProfile().toBestDisplayName()}")
                (it.note.event as? AppSpecificDataEvent)?.let {
                    signer.decrypt(it.content, it.pubKey) { decrypted ->
                        val syncedSettings =
                            try {
                                EventMapper.mapper.readValue<AccountSyncedSettingsInternal>(decrypted)
                            } catch (e: Throwable) {
                                if (e is CancellationException) throw e
                                Log.w("LocalPreferences", "Error Decoding latestAppSpecificData from Preferences with value $decrypted", e)
                                e.printStackTrace()
                                AccountSyncedSettingsInternal()
                            }

                        settings.updateAppSpecificData(it, syncedSettings)
                    }
                }
            }
        }

        scope.launch(Dispatchers.Default) {
            LocalCache.antiSpam.flowSpam.collect {
                it.cache.spamMessages.snapshot().values.forEach { spammer ->
                    if (spammer.pubkeyHex !in transientHiddenUsers.value && spammer.duplicatedMessages.size >= 5) {
                        if (spammer.pubkeyHex != userProfile().pubkeyHex && spammer.pubkeyHex !in followingKeySet()) {
                            transientHiddenUsers.update {
                                it + spammer.pubkeyHex
                            }
                        }
                    }
                }
            }
        }

        scope.launch(Dispatchers.Default) {
            delay(1000 * 60 * 1)
            // waits 5 minutes before migrating the list.
            val contactList = userProfile().latestContactList
            val oldChannels = contactList?.taggedEventIds()?.toSet()?.mapNotNull { LocalCache.getChannelIfExists(it) as? PublicChatChannel }

            if (oldChannels != null && oldChannels.isNotEmpty()) {
                Log.d("DB UPGRADE", "Migrating List with ${oldChannels.size} old channels ")
                val existingChannels = publicChatList.livePublicChatEventIdSet.value

                val needsToUpgrade = oldChannels.filter { it.idHex !in existingChannels }

                Log.d("DB UPGRADE", "Migrating List with ${needsToUpgrade.size} needsToUpgrade ")

                if (needsToUpgrade.isNotEmpty()) {
                    Log.d("DB UPGRADE", "Migrating List")
                    publicChatList.follow(oldChannels) {
                        sendToPrivateOutboxAndLocal(it)
                        LocalCache.justConsumeMyOwnEvent(it)
                    }
                }
            }
        }
    }
}
