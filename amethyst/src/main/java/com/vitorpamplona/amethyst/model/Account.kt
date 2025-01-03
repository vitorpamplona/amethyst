/**
 * Copyright (c) 2024 Vitor Pamplona
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
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.switchMap
import com.fasterxml.jackson.module.kotlin.readValue
import com.fonfon.kgeohash.GeoHash
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.service.LocationState
import com.vitorpamplona.amethyst.service.NostrLnZapPaymentResponseDataSource
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.okhttp.HttpClientManager
import com.vitorpamplona.amethyst.service.uploads.FileHeader
import com.vitorpamplona.amethyst.tryAndWait
import com.vitorpamplona.amethyst.ui.actions.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerType
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.FollowSet
import com.vitorpamplona.amethyst.ui.tor.TorType
import com.vitorpamplona.ammolite.relays.Constants
import com.vitorpamplona.ammolite.relays.FeedType
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import com.vitorpamplona.ammolite.relays.RelaySetupInfoToConnect
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.Dimension
import com.vitorpamplona.quartz.encoders.ETag
import com.vitorpamplona.quartz.encoders.EventHint
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.IMetaTag
import com.vitorpamplona.quartz.encoders.Nip47WalletConnect
import com.vitorpamplona.quartz.encoders.PTag
import com.vitorpamplona.quartz.encoders.RelayUrlFormatter
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.events.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.events.AppSpecificDataEvent
import com.vitorpamplona.quartz.events.BlossomAuthorizationEvent
import com.vitorpamplona.quartz.events.BlossomServersEvent
import com.vitorpamplona.quartz.events.BookmarkListEvent
import com.vitorpamplona.quartz.events.ChannelCreateEvent
import com.vitorpamplona.quartz.events.ChannelMessageEvent
import com.vitorpamplona.quartz.events.ChannelMetadataEvent
import com.vitorpamplona.quartz.events.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.events.ClassifiedsEvent
import com.vitorpamplona.quartz.events.CommentEvent
import com.vitorpamplona.quartz.events.Contact
import com.vitorpamplona.quartz.events.ContactListEvent
import com.vitorpamplona.quartz.events.DeletionEvent
import com.vitorpamplona.quartz.events.DraftEvent
import com.vitorpamplona.quartz.events.EmojiPackEvent
import com.vitorpamplona.quartz.events.EmojiPackSelectionEvent
import com.vitorpamplona.quartz.events.EmojiUrl
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.EventInterface
import com.vitorpamplona.quartz.events.FileHeaderEvent
import com.vitorpamplona.quartz.events.FileServersEvent
import com.vitorpamplona.quartz.events.FileStorageEvent
import com.vitorpamplona.quartz.events.FileStorageHeaderEvent
import com.vitorpamplona.quartz.events.GeneralListEvent
import com.vitorpamplona.quartz.events.GenericRepostEvent
import com.vitorpamplona.quartz.events.GiftWrapEvent
import com.vitorpamplona.quartz.events.GitReplyEvent
import com.vitorpamplona.quartz.events.HTTPAuthorizationEvent
import com.vitorpamplona.quartz.events.InteractiveStoryBaseEvent
import com.vitorpamplona.quartz.events.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.events.InteractiveStoryReadingStateEvent
import com.vitorpamplona.quartz.events.InteractiveStorySceneEvent
import com.vitorpamplona.quartz.events.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.events.LnZapEvent
import com.vitorpamplona.quartz.events.LnZapPaymentRequestEvent
import com.vitorpamplona.quartz.events.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import com.vitorpamplona.quartz.events.MetadataEvent
import com.vitorpamplona.quartz.events.MuteListEvent
import com.vitorpamplona.quartz.events.NIP17Factory
import com.vitorpamplona.quartz.events.NIP17Group
import com.vitorpamplona.quartz.events.NIP90ContentDiscoveryRequestEvent
import com.vitorpamplona.quartz.events.OtsEvent
import com.vitorpamplona.quartz.events.PeopleListEvent
import com.vitorpamplona.quartz.events.PictureEvent
import com.vitorpamplona.quartz.events.PictureMeta
import com.vitorpamplona.quartz.events.PollNoteEvent
import com.vitorpamplona.quartz.events.Price
import com.vitorpamplona.quartz.events.PrivateDmEvent
import com.vitorpamplona.quartz.events.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.events.ProfileGalleryEntryEvent
import com.vitorpamplona.quartz.events.ReactionEvent
import com.vitorpamplona.quartz.events.RelayAuthEvent
import com.vitorpamplona.quartz.events.ReportEvent
import com.vitorpamplona.quartz.events.RepostEvent
import com.vitorpamplona.quartz.events.Response
import com.vitorpamplona.quartz.events.SealedGossipEvent
import com.vitorpamplona.quartz.events.SearchRelayListEvent
import com.vitorpamplona.quartz.events.StatusEvent
import com.vitorpamplona.quartz.events.StoryOption
import com.vitorpamplona.quartz.events.TextNoteEvent
import com.vitorpamplona.quartz.events.TextNoteModificationEvent
import com.vitorpamplona.quartz.events.TorrentCommentEvent
import com.vitorpamplona.quartz.events.VideoHorizontalEvent
import com.vitorpamplona.quartz.events.VideoVerticalEvent
import com.vitorpamplona.quartz.events.WrappedEvent
import com.vitorpamplona.quartz.events.ZapSplitSetup
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.DualCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.czeal.rfc3986.URIReference
import java.math.BigDecimal
import java.util.Locale
import java.util.UUID
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

    data class PaymentRequest(
        val relayUrl: String,
        val description: String,
    )

    var transientPaymentRequestDismissals: Set<PaymentRequest> = emptySet()
    val transientPaymentRequests: MutableStateFlow<Set<PaymentRequest>> = MutableStateFlow(emptySet())

    @Immutable
    class LiveFollowList(
        val authors: Set<String> = emptySet(),
        val authorsPlusMe: Set<String>,
        val hashtags: Set<String> = emptySet(),
        val geotags: Set<String> = emptySet(),
        val addresses: Set<String> = emptySet(),
    )

    class FeedsBaseFlows(
        val listName: String,
        val peopleList: StateFlow<NoteState> = MutableStateFlow(NoteState(Note(" "))),
        val kind3: StateFlow<Account.LiveFollowList?> = MutableStateFlow(null),
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
                    ?.unverifiedFollowGeohashSet()
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val liveKind3FollowsFlow: Flow<LiveFollowList> =
        userProfile().flow().follows.stateFlow.transformLatest {
            checkNotInMainThread()
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
            val peopleList = noteState.note.event as? GeneralListEvent
            if (peopleList != null) {
                waitToDecrypt(peopleList) ?: LiveFollowList(authorsPlusMe = setOf(signer.pubKey))
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
        liveHomeListAuthorsPerRelayFlow.flowOn(Dispatchers.Default).stateIn(
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
            val users = (listEvent.bookmarkedPeople() + listEvent.filterUsers(privateTagList)).toSet()
            onReady(
                LiveFollowList(
                    authors = users,
                    authorsPlusMe = users + userProfile().pubkeyHex,
                    hashtags = (listEvent.hashtags() + listEvent.filterHashtags(privateTagList)).toSet(),
                    geotags = (listEvent.geohashes() + listEvent.filterGeohashes(privateTagList)).toSet(),
                    addresses =
                        (listEvent.taggedAddresses() + listEvent.filterAddresses(privateTagList))
                            .map { it.toTag() }
                            .toSet(),
                ),
            )
        }
    }

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

    val liveHiddenUsers = flowHiddenUsers.asLiveData()

    val decryptBookmarks: LiveData<BookmarkListEvent?> by lazy {
        userProfile().live().innerBookmarks.switchMap { userState ->
            liveData(Dispatchers.IO) {
                if (userState.user.latestBookmarkList == null) {
                    emit(null)
                } else {
                    emit(
                        tryAndWait { continuation ->
                            userState.user.latestBookmarkList?.privateTags(signer) {
                                continuation.resume(userState.user.latestBookmarkList)
                            }
                        },
                    )
                }
            }
        }
    }

    fun addPaymentRequestIfNew(paymentRequest: PaymentRequest) {
        if (
            !this.transientPaymentRequests.value.contains(paymentRequest) &&
            !this.transientPaymentRequestDismissals.contains(paymentRequest)
        ) {
            this.transientPaymentRequests.value += paymentRequest
        }
    }

    fun dismissPaymentRequest(request: PaymentRequest) {
        if (this.transientPaymentRequests.value.contains(request)) {
            this.transientPaymentRequests.value -= request
            this.transientPaymentRequestDismissals += request
        }
    }

    private var userProfileCache: User? = null

    fun userProfile(): User = userProfileCache ?: LocalCache.getOrCreateUser(signer.pubKey).also { userProfileCache = it }

    fun isWriteable(): Boolean = settings.isWriteable()

    fun updateOptOutOptions(
        warnReports: Boolean,
        filterSpam: Boolean,
    ): Boolean {
        if (settings.updateOptOutOptions(warnReports, filterSpam)) {
            if (!settings.syncedSettings.security.filterSpamFromStrangers) {
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
        signer.nip44Encrypt(Event.mapper.writeValueAsString(toInternal), signer.pubKey) { encrypted ->
            AppSpecificDataEvent.create(
                dTag = APP_SPECIFIC_DATA_D_TAG,
                description = encrypted,
                otherTags = emptyArray(),
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsume(it, null)
            }
        }
    }

    fun sendKind3RelayList(relays: Map<String, ContactListEvent.ReadWrite>) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList

        if (contactList != null && contactList.tags.isNotEmpty()) {
            ContactListEvent.updateRelayList(
                earlierVersion = contactList,
                relayUse = relays,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsume(it, null)
            }
        } else {
            ContactListEvent.createFromScratch(
                followUsers = listOf(),
                followTags = listOf(),
                followGeohashes = listOf(),
                followCommunities = listOf(),
                followEvents = DefaultChannels.toList(),
                relayUse = relays,
                signer = signer,
            ) {
                // Keep this local to avoid erasing a good contact list.
                // Amethyst.instance.client.send(it)
                LocalCache.justConsume(it, null)
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

        MetadataEvent.updateFromPast(
            latest = userProfile().latestMetadata,
            name = name,
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
            signer = signer,
        ) {
            Amethyst.instance.client.send(it)
            LocalCache.justConsume(it, null)
        }

        return
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
                val emojiUrl = EmojiUrl.decode(reaction)
                if (emojiUrl != null) {
                    note.event?.let {
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

            note.event?.let {
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
                val emojiUrl = EmojiUrl.decode(reaction)
                if (emojiUrl != null) {
                    note.event?.let {
                        ReactionEvent.create(emojiUrl, it, signer) {
                            Amethyst.instance.client.send(it)
                            LocalCache.consume(it)
                        }
                    }

                    return
                }
            }

            note.event?.let {
                ReactionEvent.create(reaction, it, signer) {
                    Amethyst.instance.client.send(it)
                    LocalCache.consume(it)
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
                val wcListener =
                    NostrLnZapPaymentResponseDataSource(
                        fromServiceHex = nip47.pubKeyHex,
                        toUserHex = event.pubKey,
                        replyingToHex = event.id,
                        authSigner = signer,
                    )
                wcListener.startSync()

                LocalCache.consume(event, zappedNote) { it.response(signer) { onResponse(it) } }

                Amethyst.instance.client.sendSingle(
                    signedEvent = event,
                    relayTemplate =
                        RelaySetupInfoToConnect(
                            url = nip47.relayUri,
                            forceProxy = shouldUseTorForTrustedRelays(), // this is trusted.
                            read = true,
                            write = true,
                            feedTypes = wcListener.feedTypes,
                        ),
                    onDone = { wcListener.destroy() },
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
        type: ReportEvent.ReportType,
        content: String = "",
    ) {
        if (!isWriteable()) return

        if (note.hasReacted(userProfile(), "⚠️")) {
            // has already liked this note
            return
        }

        note.event?.let {
            ReactionEvent.createWarning(it, signer) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsume(it, null)
            }
        }

        note.event?.let {
            ReportEvent.create(it, type, signer, content) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsume(it, null)
            }
        }
    }

    suspend fun report(
        user: User,
        type: ReportEvent.ReportType,
    ) {
        if (!isWriteable()) return

        if (user.hasReport(userProfile(), type)) {
            // has already reported this note
            return
        }

        ReportEvent.create(user.pubkeyHex, type, signer) {
            Amethyst.instance.client.send(it)
            LocalCache.justConsume(it, null)
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
                DeletionEvent.create(chunkedList, signer) { deletionEvent ->
                    Amethyst.instance.client.send(deletionEvent)
                    LocalCache.justConsume(deletionEvent, null)
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

        return tryAndWait { continuation ->
            HTTPAuthorizationEvent.create(url, method, body, signer) {
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

        if (note.hasBoostedInTheLast5Minutes(userProfile())) {
            // has already bosted in the past 5mins
            return
        }

        note.event?.let {
            if (it.kind() == 1) {
                RepostEvent.create(it, signer) {
                    Amethyst.instance.client.send(it)
                    LocalCache.justConsume(it, null)
                }
            } else {
                GenericRepostEvent.create(it, signer) {
                    Amethyst.instance.client.send(it)
                    LocalCache.justConsume(it, null)
                }
            }
        }
    }

    fun broadcast(note: Note) {
        note.event?.let {
            if (it is WrappedEvent && it.host != null) {
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

        settings.pendingAttestations.value.forEach { pair ->
            val newAttestation = OtsEvent.upgrade(pair.value, pair.key)

            if (pair.value != newAttestation) {
                OtsEvent.create(pair.key, newAttestation, signer) {
                    LocalCache.justConsume(it, null)
                    Amethyst.instance.client.send(it)

                    settings.pendingAttestations.update {
                        it - pair.key
                    }
                }
            }
        }
    }

    fun hasPendingAttestations(note: Note): Boolean {
        val id = note.event?.id() ?: note.idHex
        return settings.pendingAttestations.value[id] != null
    }

    fun timestamp(note: Note) {
        if (!isWriteable()) return
        if (note.isDraft()) return

        val id = note.event?.id() ?: note.idHex

        settings.addPendingAttestation(id, OtsEvent.stamp(id))
    }

    fun follow(user: User) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList

        if (contactList != null) {
            ContactListEvent.followUser(contactList, user.pubkeyHex, signer) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsume(it, null)
            }
        } else {
            ContactListEvent.createFromScratch(
                followUsers = listOf(Contact(user.pubkeyHex, null)),
                followTags = emptyList(),
                followGeohashes = emptyList(),
                followCommunities = emptyList(),
                followEvents = DefaultChannels.toList(),
                relayUse =
                    Constants.defaultRelays.associate {
                        it.url to ContactListEvent.ReadWrite(it.read, it.write)
                    },
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsume(it, null)
            }
        }
    }

    fun follow(channel: Channel) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList

        if (contactList != null) {
            ContactListEvent.followEvent(contactList, channel.idHex, signer) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsume(it, null)
            }
        } else {
            ContactListEvent.createFromScratch(
                followUsers = emptyList(),
                followTags = emptyList(),
                followGeohashes = emptyList(),
                followCommunities = emptyList(),
                followEvents = DefaultChannels.toList().plus(channel.idHex),
                relayUse =
                    Constants.defaultRelays.associate {
                        it.url to ContactListEvent.ReadWrite(it.read, it.write)
                    },
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsume(it, null)
            }
        }
    }

    fun follow(community: AddressableNote) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList

        if (contactList != null) {
            ContactListEvent.followAddressableEvent(contactList, community.address, signer) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsume(it, null)
            }
        } else {
            val relays =
                Constants.defaultRelays.associate {
                    it.url to ContactListEvent.ReadWrite(it.read, it.write)
                }
            ContactListEvent.createFromScratch(
                followUsers = emptyList(),
                followTags = emptyList(),
                followGeohashes = emptyList(),
                followCommunities = listOf(community.address),
                followEvents = DefaultChannels.toList(),
                relayUse = relays,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsume(it, null)
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
                LocalCache.justConsume(it, null)
            }
        } else {
            ContactListEvent.createFromScratch(
                followUsers = emptyList(),
                followTags = listOf(tag),
                followGeohashes = emptyList(),
                followCommunities = emptyList(),
                followEvents = DefaultChannels.toList(),
                relayUse =
                    Constants.defaultRelays.associate {
                        it.url to ContactListEvent.ReadWrite(it.read, it.write)
                    },
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsume(it, null)
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
                followEvents = DefaultChannels.toList(),
                relayUse =
                    Constants.defaultRelays.associate {
                        it.url to ContactListEvent.ReadWrite(it.read, it.write)
                    },
                signer = signer,
                onReady = this::onNewEventCreated,
            )
        }
    }

    fun onNewEventCreated(event: Event) {
        Amethyst.instance.client.send(event)
        LocalCache.justConsume(event, null)
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

    suspend fun unfollow(channel: Channel) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList

        if (contactList != null && contactList.tags.isNotEmpty()) {
            ContactListEvent.unfollowEvent(
                contactList,
                channel.idHex,
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
                community.address,
                signer,
                onReady = this::onNewEventCreated,
            )
        }
    }

    fun createNip95(
        byteArray: ByteArray,
        headerInfo: FileHeader,
        alt: String?,
        sensitiveContent: Boolean,
        onReady: (Pair<FileStorageEvent, FileStorageHeaderEvent>) -> Unit,
    ) {
        if (!isWriteable()) return

        FileStorageEvent.create(
            mimeType = headerInfo.mimeType ?: "",
            data = byteArray,
            signer = signer,
        ) { data ->
            FileStorageHeaderEvent.create(
                data,
                mimeType = headerInfo.mimeType,
                hash = headerInfo.hash,
                size = headerInfo.size.toString(),
                dimensions = headerInfo.dim,
                blurhash = headerInfo.blurHash?.blurhash,
                alt = alt,
                sensitiveContent = sensitiveContent,
                signer = signer,
            ) { signedEvent ->
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
        LocalCache.consume(data, null)

        Amethyst.instance.client.send(signedEvent, relayList = relayList)
        LocalCache.consume(signedEvent, null)

        return LocalCache.getNoteIfExists(signedEvent.id)
    }

    fun consumeNip95(
        data: FileStorageEvent,
        signedEvent: FileStorageHeaderEvent,
    ): Note? {
        LocalCache.consume(data, null)
        LocalCache.consume(signedEvent, null)

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

    fun sendHeader(
        signedEvent: Event,
        relayList: List<RelaySetupInfo>,
        onReady: (Note) -> Unit,
    ) {
        Amethyst.instance.client.send(signedEvent, relayList = relayList)
        LocalCache.justConsume(signedEvent, null)

        LocalCache.getNoteIfExists(signedEvent.id)?.let { onReady(it) }
    }

    fun createHeader(
        imageUrl: String,
        magnetUri: String?,
        headerInfo: FileHeader,
        alt: String?,
        sensitiveContent: Boolean,
        originalHash: String? = null,
        onReady: (FileHeaderEvent) -> Unit,
    ) {
        if (!isWriteable()) return

        FileHeaderEvent.create(
            url = imageUrl,
            magnetUri = magnetUri,
            mimeType = headerInfo.mimeType,
            hash = headerInfo.hash,
            size = headerInfo.size.toString(),
            dimensions = headerInfo.dim,
            blurhash = headerInfo.blurHash?.blurhash,
            alt = alt,
            originalHash = originalHash,
            sensitiveContent = sensitiveContent,
            signer = signer,
        ) { event ->
            onReady(event)
        }
    }

    fun sendAllAsOnePictureEvent(
        urlHeaderInfo: Map<String, FileHeader>,
        caption: String?,
        sensitiveContent: Boolean,
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
                    it.value.size.toLong(),
                    emptyList(),
                    emptyList(),
                )
            }

        PictureEvent.create(
            images = iMetas,
            msg = caption,
            markAsSensitive = sensitiveContent,
            signer = signer,
        ) { event ->
            sendHeader(event, relayList = relayList, onReady)
        }
    }

    fun sendHeader(
        url: String,
        magnetUri: String?,
        headerInfo: FileHeader,
        alt: String?,
        sensitiveContent: Boolean,
        originalHash: String? = null,
        relayList: List<RelaySetupInfo>,
        onReady: (Note) -> Unit,
    ) {
        if (!isWriteable()) return

        val isImage = headerInfo.mimeType?.startsWith("image/") == true || RichTextParser.isImageUrl(url)
        val isVideo = headerInfo.mimeType?.startsWith("video/") == true || RichTextParser.isVideoUrl(url)

        if (isImage) {
            PictureEvent.create(
                url = url,
                msg = alt,
                mimeType = headerInfo.mimeType,
                hash = headerInfo.hash,
                size = headerInfo.size.toLong(),
                dimensions = headerInfo.dim,
                blurhash = headerInfo.blurHash?.blurhash,
                markAsSensitive = sensitiveContent,
                alt = alt,
                signer = signer,
            ) { event ->
                sendHeader(event, relayList = relayList, onReady)
            }
        } else if (isVideo && headerInfo.dim != null) {
            if (headerInfo.dim.height > headerInfo.dim.width) {
                VideoVerticalEvent.create(
                    url = url,
                    mimeType = headerInfo.mimeType,
                    hash = headerInfo.hash,
                    size = headerInfo.size,
                    dimensions = headerInfo.dim,
                    blurhash = headerInfo.blurHash?.blurhash,
                    alt = alt,
                    sensitiveContent = sensitiveContent,
                    signer = signer,
                ) { event ->
                    sendHeader(event, relayList = relayList, onReady)
                }
            } else {
                VideoHorizontalEvent.create(
                    url = url,
                    mimeType = headerInfo.mimeType,
                    hash = headerInfo.hash,
                    size = headerInfo.size,
                    dimensions = headerInfo.dim,
                    blurhash = headerInfo.blurHash?.blurhash,
                    alt = alt,
                    sensitiveContent = sensitiveContent,
                    signer = signer,
                ) { event ->
                    sendHeader(event, relayList = relayList, onReady)
                }
            }
        } else {
            FileHeaderEvent.create(
                url = url,
                magnetUri = magnetUri,
                mimeType = headerInfo.mimeType,
                hash = headerInfo.hash,
                size = headerInfo.size.toString(),
                dimensions = headerInfo.dim,
                blurhash = headerInfo.blurHash?.blurhash,
                alt = alt,
                originalHash = originalHash,
                sensitiveContent = sensitiveContent,
                signer = signer,
            ) { event ->
                sendHeader(event, relayList = relayList, onReady)
            }
        }
    }

    fun sendClassifieds(
        title: String,
        price: Price,
        condition: ClassifiedsEvent.CONDITION,
        location: String,
        category: String,
        message: String,
        replyTo: List<Note>?,
        mentions: List<User>?,
        directMentions: Set<HexKey>,
        zapReceiver: List<ZapSplitSetup>? = null,
        wantsToMarkAsSensitive: Boolean,
        zapRaiserAmount: Long? = null,
        relayList: List<RelaySetupInfo>,
        geohash: String? = null,
        imetas: List<IMetaTag>? = null,
        draftTag: String?,
    ) {
        if (!isWriteable()) return

        val repliesToHex = replyTo?.filter { it.address() == null }?.map { it.idHex }
        val mentionsHex = mentions?.map { it.pubkeyHex }
        val addresses = replyTo?.mapNotNull { it.address() }

        ClassifiedsEvent.create(
            dTag = UUID.randomUUID().toString(),
            title = title,
            price = price,
            condition = condition,
            summary = message,
            image = null,
            location = location,
            category = category,
            message = message,
            replyTos = repliesToHex,
            mentions = mentionsHex,
            addresses = addresses,
            zapReceiver = zapReceiver,
            markAsSensitive = wantsToMarkAsSensitive,
            zapRaiserAmount = zapRaiserAmount,
            directMentions = directMentions,
            geohash = geohash,
            imetas = imetas,
            signer = signer,
            isDraft = draftTag != null,
        ) {
            if (draftTag != null) {
                if (message.isBlank()) {
                    deleteDraft(draftTag)
                } else {
                    DraftEvent.create(draftTag, it, emptyList(), signer) { draftEvent ->
                        sendDraftEvent(draftEvent)
                    }
                }
            } else {
                Amethyst.instance.client.send(it, relayList = relayList)
                LocalCache.justConsume(it, null)

                replyTo?.forEach { it.event?.let { Amethyst.instance.client.send(it, relayList = relayList) } }
                addresses?.forEach {
                    LocalCache.getAddressableNoteIfExists(it.toTag())?.event?.let {
                        Amethyst.instance.client.send(it, relayList = relayList)
                    }
                }
            }
        }
    }

    fun sendGitReply(
        message: String,
        replyTo: List<Note>?,
        mentions: List<User>?,
        repository: ATag?,
        zapReceiver: List<ZapSplitSetup>? = null,
        wantsToMarkAsSensitive: Boolean,
        zapRaiserAmount: Long? = null,
        replyingTo: String?,
        root: String?,
        directMentions: Set<HexKey>,
        forkedFrom: Event?,
        relayList: List<RelaySetupInfo>,
        geohash: String? = null,
        imetas: List<IMetaTag>? = null,
        draftTag: String?,
    ) {
        if (!isWriteable()) return

        val repliesToHex = replyTo?.filter { it.address() == null }?.map { it.idHex }
        val mentionsHex = mentions?.map { it.pubkeyHex }
        val addresses = listOfNotNull(repository) + (replyTo?.mapNotNull { it.address() } ?: emptyList())

        GitReplyEvent.create(
            msg = message,
            replyTos = repliesToHex,
            mentions = mentionsHex,
            addresses = addresses,
            extraTags = null,
            zapReceiver = zapReceiver,
            markAsSensitive = wantsToMarkAsSensitive,
            zapRaiserAmount = zapRaiserAmount,
            replyingTo = replyingTo,
            root = root,
            directMentions = directMentions,
            geohash = geohash,
            imetas = imetas,
            forkedFrom = forkedFrom,
            signer = signer,
            isDraft = draftTag != null,
        ) {
            if (draftTag != null) {
                if (message.isBlank()) {
                    deleteDraft(draftTag)
                } else {
                    DraftEvent.create(draftTag, it, signer) { draftEvent ->
                        sendDraftEvent(draftEvent)
                    }
                }
            } else {
                Amethyst.instance.client.send(it, relayList = relayList)
                LocalCache.justConsume(it, null)

                // broadcast replied notes
                replyingTo?.let {
                    LocalCache.getNoteIfExists(replyingTo)?.event?.let {
                        Amethyst.instance.client.send(it, relayList = relayList)
                    }
                }
                replyTo?.forEach { it.event?.let { Amethyst.instance.client.send(it, relayList = relayList) } }
                addresses?.forEach {
                    LocalCache.getAddressableNoteIfExists(it.toTag())?.event?.let {
                        Amethyst.instance.client.send(it, relayList = relayList)
                    }
                }
            }
        }
    }

    fun sendTorrentComment(
        message: String,
        replyTo: List<Note>?,
        mentions: List<User>?,
        zapReceiver: List<ZapSplitSetup>? = null,
        wantsToMarkAsSensitive: Boolean,
        zapRaiserAmount: Long? = null,
        replyingTo: String?,
        root: String,
        directMentions: Set<HexKey>,
        forkedFrom: Event?,
        relayList: List<RelaySetupInfo>,
        geohash: String? = null,
        imetas: List<IMetaTag>? = null,
        draftTag: String?,
    ) {
        if (!isWriteable()) return

        val repliesToHex = replyTo?.filter { it.address() == null }?.map { it.idHex }
        val mentionsHex = mentions?.map { it.pubkeyHex }
        val addresses = replyTo?.mapNotNull { it.address() } ?: emptyList()

        TorrentCommentEvent.create(
            message = message,
            replyTos = repliesToHex,
            mentions = mentionsHex,
            zapReceiver = zapReceiver,
            markAsSensitive = wantsToMarkAsSensitive,
            zapRaiserAmount = zapRaiserAmount,
            replyingTo = replyingTo,
            torrent = root,
            directMentions = directMentions,
            geohash = geohash,
            imetas = imetas,
            forkedFrom = forkedFrom,
            signer = signer,
            isDraft = draftTag != null,
        ) {
            if (draftTag != null) {
                if (message.isBlank()) {
                    deleteDraft(draftTag)
                } else {
                    DraftEvent.create(draftTag, it, signer) { draftEvent ->
                        sendDraftEvent(draftEvent)
                    }
                }
            } else {
                Amethyst.instance.client.send(it, relayList = relayList)
                LocalCache.justConsume(it, null)

                // broadcast replied notes
                replyingTo?.let {
                    LocalCache.getNoteIfExists(replyingTo)?.event?.let {
                        Amethyst.instance.client.send(it, relayList = relayList)
                    }
                }
                replyTo?.forEach { it.event?.let { Amethyst.instance.client.send(it, relayList = relayList) } }
                addresses?.forEach {
                    LocalCache.getAddressableNoteIfExists(it.toTag())?.event?.let {
                        Amethyst.instance.client.send(it, relayList = relayList)
                    }
                }
            }
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
                    LocalCache.justConsume(it, null)
                }
            }
            delete(note)
        }
    }

    suspend fun sendReplyComment(
        message: String,
        replyingTo: Note,
        directMentionsUsers: Set<User> = emptySet(),
        directMentionsNotes: Set<Note> = emptySet(),
        imetas: List<IMetaTag>? = null,
        geohash: String? = null,
        zapReceiver: List<ZapSplitSetup>? = null,
        wantsToMarkAsSensitive: Boolean = false,
        zapRaiserAmount: Long? = null,
        relayList: List<RelaySetupInfo>,
        draftTag: String? = null,
    ) {
        if (!isWriteable()) return

        val usersMentioned =
            directMentionsUsers
                .mapTo(HashSet(directMentionsUsers.size)) {
                    PTag(it.pubkeyHex, it.latestMetadataRelay)
                }

        val addressesMentioned =
            directMentionsNotes
                .mapNotNullTo(HashSet(directMentionsNotes.size)) { note ->
                    if (note is AddressableNote) {
                        note.address
                    } else {
                        null
                    }
                }

        val eventsMentioned =
            directMentionsNotes
                .mapNotNullTo(HashSet(directMentionsNotes.size)) { note ->
                    if (note !is AddressableNote) {
                        ETag(note.idHex, note.author?.pubkeyHex, note.relayHintUrl())
                    } else {
                        null
                    }
                }

        if (replyingTo.event is CommentEvent) {
            CommentEvent.replyComment(
                msg = message,
                replyingTo = EventHint(replyingTo.event as CommentEvent, replyingTo.relayHintUrl()),
                usersMentioned = usersMentioned,
                addressesMentioned = addressesMentioned,
                eventsMentioned = eventsMentioned,
                imetas = imetas,
                geohash = geohash,
                zapReceiver = zapReceiver,
                markAsSensitive = wantsToMarkAsSensitive,
                zapRaiserAmount = zapRaiserAmount,
                isDraft = draftTag != null,
                signer = signer,
            ) {
                if (draftTag != null) {
                    if (message.isBlank()) {
                        deleteDraft(draftTag)
                    } else {
                        DraftEvent.create(draftTag, it, signer) { draftEvent ->
                            sendDraftEvent(draftEvent)
                        }
                    }
                } else {
                    Amethyst.instance.client.send(it, relayList = relayList)
                    LocalCache.justConsume(it, null)

                    replyingTo.event?.let {
                        Amethyst.instance.client.send(it, relayList = relayList)
                    }
                }
            }
        } else {
            CommentEvent.firstReplyToEvent(
                msg = message,
                replyingTo = EventHint(replyingTo.event as Event, replyingTo.relayHintUrl()),
                usersMentioned = usersMentioned,
                addressesMentioned = addressesMentioned,
                eventsMentioned = eventsMentioned,
                imetas = imetas,
                geohash = geohash,
                zapReceiver = zapReceiver,
                markAsSensitive = wantsToMarkAsSensitive,
                zapRaiserAmount = zapRaiserAmount,
                isDraft = draftTag != null,
                signer = signer,
            ) {
                if (draftTag != null) {
                    if (message.isBlank()) {
                        deleteDraft(draftTag)
                    } else {
                        DraftEvent.create(draftTag, it, signer) { draftEvent ->
                            sendDraftEvent(draftEvent)
                        }
                    }
                } else {
                    Amethyst.instance.client.send(it, relayList = relayList)
                    LocalCache.justConsume(it, null)

                    replyingTo.event?.let {
                        Amethyst.instance.client.send(it, relayList = relayList)
                    }
                }
            }
        }
    }

    suspend fun sendGeoComment(
        message: String,
        geohash: String,
        replyingTo: Note? = null,
        directMentionsUsers: Set<User> = emptySet(),
        directMentionsNotes: Set<Note> = emptySet(),
        imetas: List<IMetaTag>? = null,
        zapReceiver: List<ZapSplitSetup>? = null,
        wantsToMarkAsSensitive: Boolean = false,
        zapRaiserAmount: Long? = null,
        relayList: List<RelaySetupInfo>,
        draftTag: String? = null,
    ) {
        if (!isWriteable()) return

        val usersMentioned =
            directMentionsUsers
                .mapTo(HashSet(directMentionsUsers.size)) {
                    PTag(it.pubkeyHex, it.latestMetadataRelay)
                }

        val addressesMentioned =
            directMentionsNotes
                .mapNotNullTo(HashSet(directMentionsNotes.size)) { note ->
                    if (note is AddressableNote) {
                        note.address
                    } else {
                        null
                    }
                }

        val eventsMentioned =
            directMentionsNotes
                .mapNotNullTo(HashSet(directMentionsNotes.size)) { note ->
                    if (note !is AddressableNote) {
                        ETag(note.idHex, note.author?.pubkeyHex, note.relayHintUrl())
                    } else {
                        null
                    }
                }

        if (replyingTo != null) {
            CommentEvent.replyComment(
                msg = message,
                replyingTo = EventHint<CommentEvent>(replyingTo.event as CommentEvent, replyingTo.relayHintUrl()),
                usersMentioned = usersMentioned,
                addressesMentioned = addressesMentioned,
                eventsMentioned = eventsMentioned,
                imetas = imetas,
                zapReceiver = zapReceiver,
                markAsSensitive = wantsToMarkAsSensitive,
                zapRaiserAmount = zapRaiserAmount,
                isDraft = draftTag != null,
                signer = signer,
            ) {
                if (draftTag != null) {
                    if (message.isBlank()) {
                        deleteDraft(draftTag)
                    } else {
                        DraftEvent.create(draftTag, it, signer) { draftEvent ->
                            sendDraftEvent(draftEvent)
                        }
                    }
                } else {
                    Amethyst.instance.client.send(it, relayList = relayList)
                    LocalCache.justConsume(it, null)

                    replyingTo.event?.let {
                        Amethyst.instance.client.send(it, relayList = relayList)
                    }
                }
            }
        } else {
            CommentEvent.createGeoComment(
                msg = message,
                geohash = geohash,
                usersMentioned = usersMentioned,
                addressesMentioned = addressesMentioned,
                eventsMentioned = eventsMentioned,
                imetas = imetas,
                zapReceiver = zapReceiver,
                markAsSensitive = wantsToMarkAsSensitive,
                zapRaiserAmount = zapRaiserAmount,
                isDraft = draftTag != null,
                signer = signer,
            ) {
                if (draftTag != null) {
                    if (message.isBlank()) {
                        deleteDraft(draftTag)
                    } else {
                        DraftEvent.create(draftTag, it, signer) { draftEvent ->
                            sendDraftEvent(draftEvent)
                        }
                    }
                } else {
                    Amethyst.instance.client.send(it, relayList = relayList)
                    LocalCache.justConsume(it, null)
                }
            }
        }
    }

    suspend fun createInteractiveStoryReadingState(
        root: InteractiveStoryBaseEvent,
        rootRelay: String?,
        readingScene: InteractiveStoryBaseEvent,
        readingSceneRelay: String?,
    ) {
        if (!isWriteable()) return

        val relayList = getPrivateOutBoxRelayList()

        InteractiveStoryReadingStateEvent.create(
            root = root,
            rootRelay = rootRelay,
            currentScene = readingScene,
            currentSceneRelay = readingSceneRelay,
            signer = signer,
        ) {
            if (relayList.isNotEmpty()) {
                Amethyst.instance.client.sendPrivately(it, relayList = relayList)
            } else {
                Amethyst.instance.client.send(it)
            }
            LocalCache.justConsume(it, null)
        }
    }

    suspend fun updateInteractiveStoryReadingState(
        readingState: InteractiveStoryReadingStateEvent,
        readingScene: InteractiveStoryBaseEvent,
        readingSceneRelay: String?,
    ) {
        if (!isWriteable()) return

        val relayList = getPrivateOutBoxRelayList()

        InteractiveStoryReadingStateEvent.update(
            base = readingState,
            currentScene = readingScene,
            currentSceneRelay = readingSceneRelay,
            signer = signer,
        ) {
            if (relayList.isNotEmpty()) {
                Amethyst.instance.client.sendPrivately(it, relayList = relayList)
            } else {
                Amethyst.instance.client.send(it)
            }
            LocalCache.justConsume(it, null)
        }
    }

    suspend fun sendInteractiveStoryPrologue(
        baseId: String,
        title: String,
        content: String,
        options: List<StoryOption>,
        summary: String? = null,
        image: String? = null,
        zapReceiver: List<ZapSplitSetup>? = null,
        wantsToMarkAsSensitive: Boolean = false,
        zapRaiserAmount: Long? = null,
        imetas: List<IMetaTag>? = null,
        draftTag: String? = null,
        relayList: List<RelaySetupInfo>,
    ) {
        if (!isWriteable()) return

        InteractiveStoryPrologueEvent.create(
            baseId = baseId,
            title = title,
            content = content,
            options = options,
            summary = summary,
            image = image,
            zapReceiver = zapReceiver,
            markAsSensitive = wantsToMarkAsSensitive,
            zapRaiserAmount = zapRaiserAmount,
            imetas = imetas,
            signer = signer,
            isDraft = draftTag != null,
        ) {
            if (draftTag != null) {
                if (content.isBlank()) {
                    deleteDraft(draftTag)
                } else {
                    DraftEvent.create(draftTag, it, signer) { draftEvent ->
                        sendDraftEvent(draftEvent)
                    }
                }
            } else {
                Amethyst.instance.client.send(it, relayList = relayList)
                LocalCache.justConsume(it, null)
            }
        }
    }

    suspend fun sendInteractiveStoryScene(
        baseId: String,
        title: String,
        content: String,
        options: List<StoryOption>,
        zapReceiver: List<ZapSplitSetup>? = null,
        wantsToMarkAsSensitive: Boolean = false,
        zapRaiserAmount: Long? = null,
        imetas: List<IMetaTag>? = null,
        draftTag: String? = null,
        relayList: List<RelaySetupInfo>,
    ) {
        if (!isWriteable()) return

        InteractiveStorySceneEvent.create(
            baseId = baseId,
            title = title,
            content = content,
            options = options,
            zapReceiver = zapReceiver,
            markAsSensitive = wantsToMarkAsSensitive,
            zapRaiserAmount = zapRaiserAmount,
            imetas = imetas,
            signer = signer,
            isDraft = draftTag != null,
        ) {
            if (draftTag != null) {
                if (content.isBlank()) {
                    deleteDraft(draftTag)
                } else {
                    DraftEvent.create(draftTag, it, signer) { draftEvent ->
                        sendDraftEvent(draftEvent)
                    }
                }
            } else {
                Amethyst.instance.client.send(it, relayList = relayList)
                LocalCache.justConsume(it, null)
            }
        }
    }

    suspend fun sendPost(
        message: String,
        replyTo: List<Note>?,
        mentions: List<User>?,
        tags: List<String>? = null,
        zapReceiver: List<ZapSplitSetup>? = null,
        wantsToMarkAsSensitive: Boolean,
        zapRaiserAmount: Long? = null,
        replyingTo: String?,
        root: String?,
        directMentions: Set<HexKey>,
        forkedFrom: Event?,
        relayList: List<RelaySetupInfo>,
        geohash: String? = null,
        imetas: List<IMetaTag>? = null,
        draftTag: String?,
    ) {
        if (!isWriteable()) return

        val repliesToHex = replyTo?.filter { it.address() == null }?.map { it.idHex }
        val mentionsHex = mentions?.map { it.pubkeyHex }
        val addresses = replyTo?.mapNotNull { it.address() }

        TextNoteEvent.create(
            msg = message,
            replyTos = repliesToHex,
            mentions = mentionsHex,
            addresses = addresses,
            extraTags = tags,
            zapReceiver = zapReceiver,
            markAsSensitive = wantsToMarkAsSensitive,
            zapRaiserAmount = zapRaiserAmount,
            replyingTo = replyingTo,
            root = root,
            directMentions = directMentions,
            geohash = geohash,
            imetas = imetas,
            forkedFrom = forkedFrom,
            signer = signer,
            isDraft = draftTag != null,
        ) {
            if (draftTag != null) {
                if (message.isBlank()) {
                    deleteDraft(draftTag)
                } else {
                    DraftEvent.create(draftTag, it, signer) { draftEvent ->
                        sendDraftEvent(draftEvent)
                    }
                }
            } else {
                Amethyst.instance.client.send(it, relayList = relayList)
                LocalCache.justConsume(it, null)

                // broadcast replied notes
                replyingTo?.let {
                    LocalCache.getNoteIfExists(replyingTo)?.event?.let {
                        Amethyst.instance.client.send(it, relayList = relayList)
                    }
                }
                replyTo?.forEach { it.event?.let { Amethyst.instance.client.send(it, relayList = relayList) } }
                addresses?.forEach {
                    LocalCache.getAddressableNoteIfExists(it.toTag())?.event?.let {
                        Amethyst.instance.client.send(it, relayList = relayList)
                    }
                }
            }
        }
    }

    fun sendEdit(
        message: String,
        originalNote: Note,
        notify: HexKey?,
        summary: String? = null,
        relayList: List<RelaySetupInfo>,
    ) {
        if (!isWriteable()) return

        val idHex = originalNote.event?.id() ?: return

        TextNoteModificationEvent.create(
            content = message,
            eventId = idHex,
            notify = notify,
            summary = summary,
            signer = signer,
        ) {
            LocalCache.justConsume(it, null)
            Amethyst.instance.client.send(it, relayList = relayList)
        }
    }

    fun sendPoll(
        message: String,
        replyTo: List<Note>?,
        mentions: List<User>?,
        pollOptions: Map<Int, String>,
        valueMaximum: Int?,
        valueMinimum: Int?,
        consensusThreshold: Int?,
        closedAt: Int?,
        zapReceiver: List<ZapSplitSetup>? = null,
        wantsToMarkAsSensitive: Boolean,
        zapRaiserAmount: Long? = null,
        relayList: List<RelaySetupInfo>,
        geohash: String? = null,
        imetas: List<IMetaTag>? = null,
        draftTag: String?,
    ) {
        if (!isWriteable()) return

        val repliesToHex = replyTo?.map { it.idHex }
        val mentionsHex = mentions?.map { it.pubkeyHex }
        val addresses = replyTo?.mapNotNull { it.address() }

        PollNoteEvent.create(
            msg = message,
            replyTos = repliesToHex,
            mentions = mentionsHex,
            addresses = addresses,
            signer = signer,
            pollOptions = pollOptions,
            valueMaximum = valueMaximum,
            valueMinimum = valueMinimum,
            consensusThreshold = consensusThreshold,
            closedAt = closedAt,
            zapReceiver = zapReceiver,
            markAsSensitive = wantsToMarkAsSensitive,
            zapRaiserAmount = zapRaiserAmount,
            geohash = geohash,
            imetas = imetas,
            isDraft = draftTag != null,
        ) {
            if (draftTag != null) {
                if (message.isBlank()) {
                    deleteDraft(draftTag)
                } else {
                    DraftEvent.create(draftTag, it, signer) { draftEvent ->
                        sendDraftEvent(draftEvent)
                    }
                }
            } else {
                Amethyst.instance.client.send(it, relayList = relayList)
                LocalCache.justConsume(it, null)

                // Rebroadcast replies and tags to the current relay set
                replyTo?.forEach { it.event?.let { Amethyst.instance.client.send(it, relayList = relayList) } }
                addresses?.forEach {
                    LocalCache.getAddressableNoteIfExists(it.toTag())?.event?.let {
                        Amethyst.instance.client.send(it, relayList = relayList)
                    }
                }
            }
        }
    }

    fun sendChannelMessage(
        message: String,
        toChannel: String,
        replyTo: List<Note>?,
        mentions: List<User>?,
        zapReceiver: List<ZapSplitSetup>? = null,
        wantsToMarkAsSensitive: Boolean,
        zapRaiserAmount: Long? = null,
        directMentions: Set<HexKey>,
        geohash: String? = null,
        imetas: List<IMetaTag>? = null,
        draftTag: String?,
    ) {
        if (!isWriteable()) return

        val repliesToHex = replyTo?.map { it.idHex }
        val mentionsHex = mentions?.map { it.pubkeyHex }

        ChannelMessageEvent.create(
            message = message,
            channel = toChannel,
            replyTos = repliesToHex,
            mentions = mentionsHex,
            zapReceiver = zapReceiver,
            markAsSensitive = wantsToMarkAsSensitive,
            zapRaiserAmount = zapRaiserAmount,
            directMentions = directMentions,
            geohash = geohash,
            imetas = imetas,
            signer = signer,
            isDraft = draftTag != null,
        ) {
            if (draftTag != null) {
                if (message.isBlank()) {
                    deleteDraft(draftTag)
                } else {
                    DraftEvent.create(draftTag, it, signer) { draftEvent ->
                        sendDraftEvent(draftEvent)
                    }
                }
            } else {
                Amethyst.instance.client.send(it)
                LocalCache.justConsume(it, null)
            }
        }
    }

    fun sendLiveMessage(
        message: String,
        toChannel: ATag,
        replyTo: List<Note>?,
        mentions: List<User>?,
        zapReceiver: List<ZapSplitSetup>? = null,
        wantsToMarkAsSensitive: Boolean,
        zapRaiserAmount: Long? = null,
        geohash: String? = null,
        imetas: List<IMetaTag>? = null,
        draftTag: String?,
    ) {
        if (!isWriteable()) return

        // val repliesToHex = listOfNotNull(replyingTo?.idHex).ifEmpty { null }
        val repliesToHex = replyTo?.map { it.idHex }
        val mentionsHex = mentions?.map { it.pubkeyHex }

        LiveActivitiesChatMessageEvent.create(
            message = message,
            activity = toChannel,
            replyTos = repliesToHex,
            mentions = mentionsHex,
            zapReceiver = zapReceiver,
            markAsSensitive = wantsToMarkAsSensitive,
            zapRaiserAmount = zapRaiserAmount,
            geohash = geohash,
            imetas = imetas,
            signer = signer,
            isDraft = draftTag != null,
        ) {
            if (draftTag != null) {
                if (message.isBlank()) {
                    deleteDraft(draftTag)
                } else {
                    DraftEvent.create(draftTag, it, signer) { draftEvent ->
                        sendDraftEvent(draftEvent)
                    }
                }
            } else {
                Amethyst.instance.client.send(it)
                LocalCache.justConsume(it, null)
            }
        }
    }

    fun sendPrivateMessage(
        message: String,
        toUser: User,
        replyingTo: Note? = null,
        mentions: List<User>?,
        zapReceiver: List<ZapSplitSetup>? = null,
        wantsToMarkAsSensitive: Boolean,
        zapRaiserAmount: Long? = null,
        geohash: String? = null,
        imetas: List<IMetaTag>? = null,
        draftTag: String?,
    ) {
        sendPrivateMessage(
            message,
            toUser.pubkeyHex,
            replyingTo,
            mentions,
            zapReceiver,
            wantsToMarkAsSensitive,
            zapRaiserAmount,
            geohash,
            imetas,
            draftTag,
        )
    }

    fun sendPrivateMessage(
        message: String,
        toUser: HexKey,
        replyingTo: Note? = null,
        mentions: List<User>?,
        zapReceiver: List<ZapSplitSetup>? = null,
        wantsToMarkAsSensitive: Boolean,
        zapRaiserAmount: Long? = null,
        geohash: String? = null,
        imetas: List<IMetaTag>? = null,
        draftTag: String?,
    ) {
        if (!isWriteable()) return

        val repliesToHex = listOfNotNull(replyingTo?.idHex).ifEmpty { null }
        val mentionsHex = mentions?.map { it.pubkeyHex }

        PrivateDmEvent.create(
            recipientPubKey = toUser,
            publishedRecipientPubKey = toUser,
            msg = message,
            replyTos = repliesToHex,
            mentions = mentionsHex,
            zapReceiver = zapReceiver,
            markAsSensitive = wantsToMarkAsSensitive,
            zapRaiserAmount = zapRaiserAmount,
            geohash = geohash,
            imetas = imetas,
            signer = signer,
            advertiseNip18 = false,
            isDraft = draftTag != null,
        ) {
            if (draftTag != null) {
                if (message.isBlank()) {
                    deleteDraft(draftTag)
                } else {
                    DraftEvent.create(draftTag, it, emptyList(), signer) { draftEvent ->
                        sendDraftEvent(draftEvent)
                    }
                }
            } else {
                Amethyst.instance.client.send(it)
                LocalCache.consume(it, null)
            }
        }
    }

    fun sendNIP17EncryptedFile(
        url: String,
        toUsers: List<HexKey>,
        replyingTo: Note? = null,
        contentType: String?,
        algo: String,
        key: ByteArray,
        nonce: ByteArray? = null,
        originalHash: String? = null,
        hash: String? = null,
        size: Int? = null,
        dimensions: Dimension? = null,
        blurhash: String? = null,
        sensitiveContent: Boolean? = null,
        alt: String?,
    ) {
        if (!isWriteable()) return

        val repliesToHex = listOfNotNull(replyingTo?.idHex).ifEmpty { null }

        NIP17Factory().createEncryptedFileNIP17(
            url = url,
            to = toUsers,
            repliesToHex = repliesToHex,
            contentType = contentType,
            algo = algo,
            key = key,
            nonce = nonce,
            originalHash = originalHash,
            hash = hash,
            size = size,
            dimensions = dimensions,
            blurhash = blurhash,
            sensitiveContent = sensitiveContent,
            alt = alt,
            draftTag = null,
            signer = signer,
        ) {
            broadcastPrivately(it)
        }
    }

    fun sendNIP17PrivateMessage(
        message: String,
        toUsers: List<HexKey>,
        subject: String? = null,
        replyingTo: Note? = null,
        mentions: List<User>?,
        zapReceiver: List<ZapSplitSetup>? = null,
        wantsToMarkAsSensitive: Boolean,
        zapRaiserAmount: Long? = null,
        geohash: String? = null,
        imetas: List<IMetaTag>? = null,
        draftTag: String? = null,
    ) {
        if (!isWriteable()) return

        val repliesToHex = listOfNotNull(replyingTo?.idHex).ifEmpty { null }
        val mentionsHex = mentions?.map { it.pubkeyHex }

        NIP17Factory().createMsgNIP17(
            msg = message,
            to = toUsers,
            subject = subject,
            replyTos = repliesToHex,
            mentions = mentionsHex,
            zapReceiver = zapReceiver,
            markAsSensitive = wantsToMarkAsSensitive,
            zapRaiserAmount = zapRaiserAmount,
            geohash = geohash,
            imetas = imetas,
            draftTag = draftTag,
            signer = signer,
        ) {
            if (draftTag != null) {
                if (message.isBlank()) {
                    deleteDraft(draftTag)
                } else {
                    DraftEvent.create(draftTag, it.msg, emptyList(), signer) { draftEvent ->
                        sendDraftEvent(draftEvent)
                    }
                }
            } else {
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
        val relayList = getPrivateOutBoxRelayList()
        if (relayList.isNotEmpty()) {
            Amethyst.instance.client.sendPrivately(draftEvent, relayList)
        } else {
            Amethyst.instance.client.send(draftEvent)
        }
        LocalCache.justConsume(draftEvent, null)
    }

    fun broadcastPrivately(signedEvents: NIP17Factory.Result) {
        val mine = signedEvents.wraps.filter { (it.recipientPubKey() == signer.pubKey) }

        mine.forEach { giftWrap ->
            giftWrap.unwrap(signer) { gift ->
                if (gift is SealedGossipEvent) {
                    gift.unseal(signer) { gossip ->
                        LocalCache.justConsume(gossip, null)
                    }
                }

                LocalCache.justConsume(gift, null)
            }

            LocalCache.consume(giftWrap, null)
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

    fun sendCreateNewChannel(
        name: String,
        about: String,
        picture: String,
    ) {
        if (!isWriteable()) return

        ChannelCreateEvent.create(
            name = name,
            about = about,
            picture = picture,
            signer = signer,
        ) {
            Amethyst.instance.client.send(it)
            LocalCache.justConsume(it, null)

            LocalCache.getChannelIfExists(it.id)?.let { follow(it) }
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
            LocalCache.justConsume(it, null)
        }
    }

    fun createStatus(newStatus: String) {
        if (!isWriteable()) return

        StatusEvent.create(newStatus, "general", expiration = null, signer) {
            Amethyst.instance.client.send(it)
            LocalCache.justConsume(it, null)
        }
    }

    fun deleteStatus(oldStatus: AddressableNote) {
        if (!isWriteable()) return
        val oldEvent = oldStatus.event as? StatusEvent ?: return

        StatusEvent.clear(oldEvent, signer) { event ->
            Amethyst.instance.client.send(event)
            LocalCache.justConsume(event, null)

            DeletionEvent.createForVersionOnly(listOf(event), signer) { event2 ->
                Amethyst.instance.client.send(event2)
                LocalCache.justConsume(event2, null)
            }
        }
    }

    fun removeEmojiPack(
        usersEmojiList: Note,
        emojiList: Note,
    ) {
        if (!isWriteable()) return

        val noteEvent = usersEmojiList.event
        if (noteEvent !is EmojiPackSelectionEvent) return
        val emojiListEvent = emojiList.event
        if (emojiListEvent !is EmojiPackEvent) return

        EmojiPackSelectionEvent.create(
            noteEvent.taggedAddresses().filter { it != emojiListEvent.address() },
            signer,
        ) {
            Amethyst.instance.client.send(it)
            LocalCache.justConsume(it, null)
        }
    }

    fun addEmojiPack(
        usersEmojiList: Note,
        emojiList: Note,
    ) {
        if (!isWriteable()) return
        val emojiListEvent = emojiList.event
        if (emojiListEvent !is EmojiPackEvent) return

        if (usersEmojiList.event == null) {
            EmojiPackSelectionEvent.create(
                listOf(emojiListEvent.address()),
                signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsume(it, null)
            }
        } else {
            val noteEvent = usersEmojiList.event
            if (noteEvent !is EmojiPackSelectionEvent) return

            if (noteEvent.taggedAddresses().any { it == emojiListEvent.address() }) {
                return
            }

            EmojiPackSelectionEvent.create(
                noteEvent.taggedAddresses().plus(emojiListEvent.address()),
                signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsume(it, null)
            }
        }
    }

    fun addToGallery(
        idHex: String,
        url: String,
        relay: String?,
        blurhash: String?,
        dim: Dimension?,
        hash: String?,
        mimeType: String?,
    ) {
        if (!isWriteable()) return
        ProfileGalleryEntryEvent.create(
            url = url,
            eventid = idHex,
            relayhint = relay,
            blurhash = blurhash,
            hash = hash,
            dimensions = dim,
            mimeType = mimeType,
            /*magnetUri = magnetUri,
            size = headerInfo.size.toString(),
            dimensions = headerInfo.dim,
            alt = alt,
            originalHash = originalHash, */
            signer = signer,
        ) { event ->
            Amethyst.instance.client.send(event)
            LocalCache.consume(event, null)
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
                note.address,
                isPrivate,
                signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.consume(it)
            }
        } else {
            BookmarkListEvent.addEvent(
                userProfile().latestBookmarkList,
                note.idHex,
                isPrivate,
                signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.consume(it)
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
                note.address,
                isPrivate,
                signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.consume(it)
            }
        } else {
            BookmarkListEvent.removeEvent(
                bookmarks,
                note.idHex,
                isPrivate,
                signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.consume(it)
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
            userProfile().latestBookmarkList?.privateTaggedAddresses(signer) {
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
            return userProfile().latestBookmarkList?.taggedAddresses()?.contains(note.address) == true
        } else {
            return userProfile().latestBookmarkList?.taggedEvents()?.contains(note.idHex) == true
        }
    }

    fun getAppSpecificDataNote(): AddressableNote {
        val aTag = AppSpecificDataEvent.createTag(userProfile().pubkeyHex, APP_SPECIFIC_DATA_D_TAG)
        return LocalCache.getOrCreateAddressableNote(aTag)
    }

    fun getAppSpecificDataFlow(): StateFlow<NoteState> = getAppSpecificDataNote().flow().metadata.stateFlow

    fun getBlockListNote(): AddressableNote {
        val aTag =
            ATag(
                PeopleListEvent.KIND,
                userProfile().pubkeyHex,
                PeopleListEvent.BLOCK_LIST_D_TAG,
                null,
            )
        return LocalCache.getOrCreateAddressableNote(aTag)
    }

    fun getMuteListNote(): AddressableNote {
        val aTag =
            ATag(
                MuteListEvent.KIND,
                userProfile().pubkeyHex,
                "",
                null,
            )
        return LocalCache.getOrCreateAddressableNote(aTag)
    }

    suspend fun getFollowSetNotes() =
        withContext(Dispatchers.Default) {
            val followSetNotes = LocalCache.getFollowSetsFor(userProfile())
            userProfile().followSets = followSetNotes
            println("Number of follow sets: ${followSetNotes.size}")
        }

    fun mapNoteToFollowSet(note: Note): FollowSet =
        FollowSet
            .mapEventToSet(
                event = note.event as PeopleListEvent,
                signer,
            )

    fun followSetNotesFlow() = MutableStateFlow(userProfile().followSets)

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
                LocalCache.consume(it, null)
            }
        } else {
            MuteListEvent.createListWithWord(
                word = word,
                isPrivate = true,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.consume(it, null)
            }
        }
    }

    fun showWord(word: String) {
        val blockList = getBlockList()

        if (blockList != null) {
            PeopleListEvent.removeWord(
                earlierVersion = blockList,
                word = word,
                isPrivate = true,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.consume(it, null)
            }
        }

        val muteList = getMuteList()

        if (muteList != null) {
            MuteListEvent.removeWord(
                earlierVersion = muteList,
                word = word,
                isPrivate = true,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.consume(it, null)
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
                LocalCache.consume(it, null)
            }
        } else {
            MuteListEvent.createListWithUser(
                pubKeyHex = pubkeyHex,
                isPrivate = true,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.consume(it, null)
            }
        }
    }

    fun showUser(pubkeyHex: String) {
        val blockList = getBlockList()

        if (blockList != null) {
            PeopleListEvent.removeUser(
                earlierVersion = blockList,
                pubKeyHex = pubkeyHex,
                isPrivate = true,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.consume(it, null)
            }
        }

        val muteList = getMuteList()

        if (muteList != null) {
            MuteListEvent.removeUser(
                earlierVersion = muteList,
                pubKeyHex = pubkeyHex,
                isPrivate = true,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.consume(it, null)
            }
        }

        transientHiddenUsers.update {
            it - pubkeyHex
        }
    }

    fun selectedChatsFollowList(): Set<String> {
        val contactList = userProfile().latestContactList
        return contactList?.taggedEvents()?.toSet() ?: DefaultChannels
    }

    fun sendChangeChannel(
        name: String,
        about: String,
        picture: String,
        channel: Channel,
    ) {
        if (!isWriteable()) return

        ChannelMetadataEvent.create(
            name,
            about,
            picture,
            originalChannelIdHex = channel.idHex,
            signer = signer,
        ) {
            Amethyst.instance.client.send(it)
            LocalCache.justConsume(it, null)

            follow(channel)
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
            LocalCache.justConsume(it, null)
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
        event: SealedGossipEvent,
        onReady: (Event) -> Unit,
    ) {
        if (!isWriteable()) return

        return event.unseal(signer, onReady)
    }

    fun cachedDecryptContent(note: Note): String? = cachedDecryptContent(note.event)

    fun cachedDecryptContent(event: EventInterface?): String? {
        if (event == null) return null

        return if (event is PrivateDmEvent && isWriteable()) {
            event.cachedContentFor(signer)
        } else if (event is LnZapRequestEvent && event.isPrivateZap() && isWriteable()) {
            event.cachedPrivateZap()?.content
        } else {
            event.content()
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
            event?.content()?.let { onReady(it) }
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
            value.associate { it.url to ContactListEvent.ReadWrite(it.read, it.write) },
        )
    }

    fun getDMRelayListNote(): AddressableNote =
        LocalCache.getOrCreateAddressableNote(
            ChatMessageRelayListEvent.createAddressATag(signer.pubKey),
        )

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
                LocalCache.justConsume(it, null)
            }
        } else {
            ChatMessageRelayListEvent.createFromScratch(
                relays = dmRelays,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsume(it, null)
            }
        }
    }

    fun getPrivateOutboxRelayListNote(): AddressableNote =
        LocalCache.getOrCreateAddressableNote(
            PrivateOutboxRelayListEvent.createAddressATag(signer.pubKey),
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
                LocalCache.justConsume(it, null)
            }
        } else {
            PrivateOutboxRelayListEvent.createFromScratch(
                relays = relays,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsume(it, null)
            }
        }
    }

    fun getSearchRelayListNote(): AddressableNote =
        LocalCache.getOrCreateAddressableNote(
            SearchRelayListEvent.createAddressATag(signer.pubKey),
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
                LocalCache.justConsume(it, null)
            }
        } else {
            SearchRelayListEvent.createFromScratch(
                relays = searchRelays,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsume(it, null)
            }
        }
    }

    fun getNIP65RelayListNote(pubkey: HexKey = signer.pubKey): AddressableNote =
        LocalCache.getOrCreateAddressableNote(
            AdvertisedRelayListEvent.createAddressATag(pubkey),
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
                LocalCache.justConsume(it, null)
            }
        } else {
            AdvertisedRelayListEvent.createFromScratch(
                relays = relays,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsume(it, null)
            }
        }
    }

    fun getFileServersList(): FileServersEvent? = getFileServersNote().event as? FileServersEvent

    fun getFileServersListFlow(): StateFlow<NoteState> = getFileServersNote().flow().metadata.stateFlow

    fun getFileServersNote(): AddressableNote = LocalCache.getOrCreateAddressableNote(FileServersEvent.createAddressATag(userProfile().pubkeyHex))

    fun getBlossomServersList(): BlossomServersEvent? = getBlossomServersNote().event as? BlossomServersEvent

    fun getBlossomServersListFlow(): StateFlow<NoteState> = getBlossomServersNote().flow().metadata.stateFlow

    fun getBlossomServersNote(): AddressableNote = LocalCache.getOrCreateAddressableNote(BlossomServersEvent.createAddressATag(userProfile().pubkeyHex))

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

        if (serverList != null && serverList.tags.isNotEmpty()) {
            FileServersEvent.updateRelayList(
                earlierVersion = serverList,
                relays = servers,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsume(it, null)
            }
        } else {
            FileServersEvent.createFromScratch(
                relays = servers,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsume(it, null)
            }
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
                LocalCache.justConsume(it, null)
            }
        } else {
            BlossomServersEvent.createFromScratch(
                relays = servers,
                signer = signer,
            ) {
                Amethyst.instance.client.send(it)
                LocalCache.justConsume(it, null)
            }
        }
    }

    fun getAllPeopleLists(): List<AddressableNote> = getAllPeopleLists(signer.pubKey)

    fun getAllPeopleLists(pubkey: HexKey): List<AddressableNote> =
        LocalCache.addressables
            .filter { _, addressableNote ->
                val event = (addressableNote.event as? PeopleListEvent)
                event != null &&
                    event.pubKey == pubkey &&
                    (event.hasAnyTaggedUser() || event.publicAndPrivateUserCache?.isNotEmpty() == true)
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

    fun httpClientForCoil() = HttpClientManager.getHttpClient(shouldUseTorForImageDownload())

    fun httpClientForRelay(dirtyUrl: String) = HttpClientManager.getHttpClient(shouldUseTorForDirty(dirtyUrl))

    fun httpClientForPreviewUrl(url: String) = HttpClientManager.getHttpClient(shouldUseTorForPreviewUrl(url))

    fun shouldUseTorForImageDownload() =
        when (settings.torSettings.torType.value) {
            TorType.OFF -> false
            TorType.INTERNAL -> settings.torSettings.imagesViaTor.value
            TorType.EXTERNAL -> settings.torSettings.imagesViaTor.value
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

    init {
        Log.d("AccountRegisterObservers", "Init")
        settings.backupContactList?.let {
            Log.d("AccountRegisterObservers", "Loading saved contacts ${it.toJson()}")

            GlobalScope.launch(Dispatchers.IO) { LocalCache.consume(it) }
        }

        settings.backupUserMetadata?.let {
            Log.d("AccountRegisterObservers", "Loading saved user metadata ${it.toJson()}")

            GlobalScope.launch(Dispatchers.IO) { LocalCache.consume(it, null) }
        }

        settings.backupDMRelayList?.let {
            Log.d("AccountRegisterObservers", "Loading saved DM Relay List ${it.toJson()}")
            GlobalScope.launch(Dispatchers.IO) { LocalCache.verifyAndConsume(it, null) }
        }

        settings.backupNIP65RelayList?.let {
            Log.d("AccountRegisterObservers", "Loading saved nip65 relay list ${it.toJson()}")
            GlobalScope.launch(Dispatchers.IO) { LocalCache.verifyAndConsume(it, null) }
        }

        settings.backupSearchRelayList?.let {
            Log.d("AccountRegisterObservers", "Loading saved search relay list ${it.toJson()}")
            GlobalScope.launch(Dispatchers.IO) { LocalCache.verifyAndConsume(it, null) }
        }

        settings.backupPrivateHomeRelayList?.let { event ->
            Log.d("AccountRegisterObservers", "Loading saved private home relay list ${event.toJson()}")
            GlobalScope.launch(Dispatchers.IO) {
                event.privateTags(signer) {
                    LocalCache.verifyAndConsume(event, null)
                }
            }
        }

        settings.backupAppSpecificData?.let { event ->
            Log.d("AccountRegisterObservers", "Loading saved app specific data ${event.toJson()}")
            GlobalScope.launch(Dispatchers.IO) {
                LocalCache.verifyAndConsume(event, null)
                signer.decrypt(event.content, event.pubKey) { decrypted ->
                    try {
                        val syncedSettings = Event.mapper.readValue<AccountSyncedSettingsInternal>(decrypted)
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

        settings.backupMuteList?.let {
            Log.d("AccountRegisterObservers", "Loading saved mute list ${it.toJson()}")
            GlobalScope.launch(Dispatchers.IO) { LocalCache.verifyAndConsume(it, null) }
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
            Log.d("AccountRegisterObservers", "AppSpecificData Collector Start")
            getAppSpecificDataFlow().collect {
                Log.d("AccountRegisterObservers", "Updating AppSpecificData for ${userProfile().toBestDisplayName()}")
                (it.note.event as? AppSpecificDataEvent)?.let {
                    signer.decrypt(it.content, it.pubKey) { decrypted ->
                        val syncedSettings =
                            try {
                                Event.mapper.readValue<AccountSyncedSettingsInternal>(decrypted)
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
    }
}
