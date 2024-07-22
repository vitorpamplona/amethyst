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

import android.content.res.Resources
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.switchMap
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.service.FileHeader
import com.vitorpamplona.amethyst.service.Nip96MediaServers
import com.vitorpamplona.amethyst.service.NostrLnZapPaymentResponseDataSource
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.ammolite.relays.BundledUpdate
import com.vitorpamplona.ammolite.relays.Client
import com.vitorpamplona.ammolite.relays.Constants
import com.vitorpamplona.ammolite.relays.FeedType
import com.vitorpamplona.ammolite.relays.Filter
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.Nip47WalletConnect
import com.vitorpamplona.quartz.encoders.RelayUrlFormatter
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.events.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.events.BookmarkListEvent
import com.vitorpamplona.quartz.events.ChannelCreateEvent
import com.vitorpamplona.quartz.events.ChannelMessageEvent
import com.vitorpamplona.quartz.events.ChannelMetadataEvent
import com.vitorpamplona.quartz.events.ChatMessageEvent
import com.vitorpamplona.quartz.events.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.events.ClassifiedsEvent
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
import com.vitorpamplona.quartz.events.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.events.LnZapEvent
import com.vitorpamplona.quartz.events.LnZapPaymentRequestEvent
import com.vitorpamplona.quartz.events.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import com.vitorpamplona.quartz.events.MetadataEvent
import com.vitorpamplona.quartz.events.MuteListEvent
import com.vitorpamplona.quartz.events.NIP17Factory
import com.vitorpamplona.quartz.events.NIP90ContentDiscoveryRequestEvent
import com.vitorpamplona.quartz.events.OtsEvent
import com.vitorpamplona.quartz.events.PeopleListEvent
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
import com.vitorpamplona.quartz.events.TextNoteEvent
import com.vitorpamplona.quartz.events.TextNoteModificationEvent
import com.vitorpamplona.quartz.events.WrappedEvent
import com.vitorpamplona.quartz.events.ZapSplitSetup
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.signers.NostrSignerExternal
import com.vitorpamplona.quartz.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.DualCase
import com.vitorpamplona.quartz.utils.MinimumRelayListProcessor
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import java.net.Proxy
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

val DefaultChannels =
    setOf(
        // Anigma's Nostr
        "25e5c82273a271cb1a840d0060391a0bf4965cafeb029d5ab55350b418953fbb",
        // Amethyst's Group
        "42224859763652914db53052103f0b744df79dfc4efef7e950fc0802fc3df3c5",
    )

val DefaultReactions =
    listOf(
        "\uD83D\uDE80",
        "\uD83E\uDEC2",
        "\uD83D\uDC40",
        "\uD83D\uDE02",
        "\uD83C\uDF89",
        "\uD83E\uDD14",
        "\uD83D\uDE31",
    )

val DefaultZapAmounts = listOf(100L, 500L, 1000L)

fun getLanguagesSpokenByUser(): Set<String> {
    val languageList = ConfigurationCompat.getLocales(Resources.getSystem().getConfiguration())
    val codedList = mutableSetOf<String>()
    for (i in 0 until languageList.size()) {
        languageList.get(i)?.let { codedList.add(it.language) }
    }
    return codedList
}

val GLOBAL_FOLLOWS =
    " Global " // This has spaces to avoid mixing with a potential NIP-51 list with the same name.
val KIND3_FOLLOWS =
    " All Follows " // This has spaces to avoid mixing with a potential NIP-51 list with the same
// name.

@OptIn(DelicateCoroutinesApi::class)
@Stable
class Account(
    val keyPair: KeyPair,
    val signer: NostrSigner = NostrSignerInternal(keyPair),
    var localRelays: Set<RelaySetupInfo> = Constants.defaultRelays.toSet(),
    var localRelayServers: Set<String> = setOf(),
    var dontTranslateFrom: Set<String> = getLanguagesSpokenByUser(),
    var languagePreferences: Map<String, String> = mapOf(),
    var translateTo: String = Locale.getDefault().language,
    var zapAmountChoices: List<Long> = DefaultZapAmounts,
    var reactionChoices: List<String> = DefaultReactions,
    var defaultZapType: LnZapEvent.ZapType = LnZapEvent.ZapType.PUBLIC,
    var defaultFileServer: Nip96MediaServers.ServerName = Nip96MediaServers.DEFAULT[0],
    var defaultHomeFollowList: MutableStateFlow<String> = MutableStateFlow(KIND3_FOLLOWS),
    var defaultStoriesFollowList: MutableStateFlow<String> = MutableStateFlow(GLOBAL_FOLLOWS),
    var defaultNotificationFollowList: MutableStateFlow<String> = MutableStateFlow(GLOBAL_FOLLOWS),
    var defaultDiscoveryFollowList: MutableStateFlow<String> = MutableStateFlow(GLOBAL_FOLLOWS),
    var zapPaymentRequest: Nip47WalletConnect.Nip47URI? = null,
    var hideDeleteRequestDialog: Boolean = false,
    var hideBlockAlertDialog: Boolean = false,
    var hideNIP17WarningDialog: Boolean = false,
    var backupContactList: ContactListEvent? = null,
    var backupDMRelayList: ChatMessageRelayListEvent? = null,
    var backupNIP65RelayList: AdvertisedRelayListEvent? = null,
    var proxy: Proxy? = null,
    var proxyPort: Int = 9050,
    var showSensitiveContent: MutableStateFlow<Boolean?> = MutableStateFlow(null),
    var warnAboutPostsWithReports: Boolean = true,
    var filterSpamFromStrangers: Boolean = true,
    var lastReadPerRoute: Map<String, Long> = mapOf<String, Long>(),
    var hasDonatedInVersion: Set<String> = setOf<String>(),
    var pendingAttestations: MutableStateFlow<Map<HexKey, String>> = MutableStateFlow<Map<HexKey, String>>(mapOf()),
    val scope: CoroutineScope = Amethyst.instance.applicationIOScope,
) {
    var transientHiddenUsers: MutableStateFlow<Set<String>> = MutableStateFlow(setOf())

    data class PaymentRequest(
        val relayUrl: String,
        val description: String,
    )

    var transientPaymentRequestDismissals: Set<PaymentRequest> = emptySet()
    val transientPaymentRequests: MutableStateFlow<Set<PaymentRequest>> = MutableStateFlow(emptySet())

    // Observers line up here.
    val live: AccountLiveData = AccountLiveData(this)
    val liveLanguages: AccountLiveData = AccountLiveData(this)
    val saveable: AccountLiveData = AccountLiveData(this)

    @Immutable
    class LiveFollowLists(
        val users: Set<String> = emptySet(),
        val hashtags: Set<String> = emptySet(),
        val geotags: Set<String> = emptySet(),
        val communities: Set<String> = emptySet(),
    )

    class ListNameNotePair(
        val listName: String,
        val event: GeneralListEvent?,
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

            val baseRelaySet = activeRelays() ?: convertLocalRelays()
            val newDMRelaySet = (dmRelayList.note.event as? ChatMessageRelayListEvent)?.relays()?.map { RelayUrlFormatter.normalize(it) }?.toSet() ?: emptySet()
            val searchRelaySet = (searchRelayList.note.event as? SearchRelayListEvent)?.relays()?.map { RelayUrlFormatter.normalize(it) }?.toSet() ?: Constants.defaultSearchRelaySet
            val nip65RelaySet =
                (nip65RelayList.note.event as? AdvertisedRelayListEvent)?.relays()?.map {
                    AdvertisedRelayListEvent.AdvertisedRelayInfo(
                        RelayUrlFormatter.normalize(it.relayUrl),
                        it.type,
                    )
                }
            val privateOutboxRelaySet = (privateOutBox.note.event as? PrivateOutboxRelayListEvent)?.relays()?.map { RelayUrlFormatter.normalize(it) }?.toSet() ?: emptySet()

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
            // PRIVATE OUTBOX
            // --------------

            mappedRelaySet =
                mappedRelaySet.map {
                    if (localRelayServers.contains(it.url)) {
                        RelaySetupInfo(it.url, true, true, it.feedTypes + setOf(FeedType.FOLLOWS, FeedType.PUBLIC_CHATS, FeedType.GLOBAL, FeedType.PRIVATE_DMS))
                    } else {
                        it
                    }
                }

            localRelayServers.forEach { newUrl ->
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
                        val write = nip65setup.type == AdvertisedRelayListEvent.AdvertisedRelayType.BOTH || nip65setup.type == AdvertisedRelayListEvent.AdvertisedRelayType.READ

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
                    val write = newNip65Setup.type == AdvertisedRelayListEvent.AdvertisedRelayType.BOTH || newNip65Setup.type == AdvertisedRelayListEvent.AdvertisedRelayType.READ

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

            emit(mappedRelaySet.toTypedArray())
        }

    val connectToRelays = connectToRelaysFlow.stateIn(scope, SharingStarted.Eagerly, activeRelays() ?: convertLocalRelays())

    @OptIn(ExperimentalCoroutinesApi::class)
    val liveKind3FollowsFlow: Flow<LiveFollowLists> =
        userProfile().flow().follows.stateFlow.transformLatest {
            checkNotInMainThread()
            emit(
                LiveFollowLists(
                    it.user.cachedFollowingKeySet(),
                    it.user.cachedFollowingTagSet(),
                    it.user.cachedFollowingGeohashSet(),
                    it.user.cachedFollowingCommunitiesSet(),
                ),
            )
        }

    val liveKind3Follows = liveKind3FollowsFlow.stateIn(scope, SharingStarted.Eagerly, LiveFollowLists())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val liveHomeList: Flow<ListNameNotePair> by lazy {
        defaultHomeFollowList.flatMapLatest { listName ->
            loadPeopleListFlowFromListName(listName)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun loadPeopleListFlowFromListName(listName: String): Flow<ListNameNotePair> =
        if (listName != GLOBAL_FOLLOWS && listName != KIND3_FOLLOWS) {
            val note = LocalCache.checkGetOrCreateAddressableNote(listName)
            note?.flow()?.metadata?.stateFlow?.mapLatest {
                val noteEvent = it.note.event as? GeneralListEvent
                ListNameNotePair(listName, noteEvent)
            } ?: MutableStateFlow(ListNameNotePair(listName, null))
        } else {
            MutableStateFlow(ListNameNotePair(listName, null))
        }

    fun combinePeopleListFlows(
        kind3FollowsSource: Flow<LiveFollowLists>,
        peopleListFollowsSource: Flow<ListNameNotePair>,
    ): Flow<LiveFollowLists?> =
        combineTransform(kind3FollowsSource, peopleListFollowsSource) { kind3Follows, peopleListFollows ->
            checkNotInMainThread()
            if (peopleListFollows.listName == GLOBAL_FOLLOWS) {
                emit(null)
            } else if (peopleListFollows.listName == KIND3_FOLLOWS) {
                emit(kind3Follows)
            } else if (peopleListFollows.event == null) {
                emit(LiveFollowLists())
            } else {
                val result = waitToDecrypt(peopleListFollows.event)
                if (result == null) {
                    emit(LiveFollowLists())
                } else {
                    emit(result)
                }
            }
        }

    val liveHomeFollowListFlow: Flow<LiveFollowLists?> by lazy {
        combinePeopleListFlows(liveKind3FollowsFlow, liveHomeList)
    }

    val liveHomeFollowLists: StateFlow<LiveFollowLists?> by lazy {
        liveHomeFollowListFlow.stateIn(scope, SharingStarted.Eagerly, LiveFollowLists())
    }

    fun relaysFromPeopleListFlows(
        currentFollowList: LiveFollowLists,
        relayUrlsToIgnore: Set<String>,
    ): Flow<List<MinimumRelayListProcessor.RelayRecommendation>> =
        combine(
            currentFollowList.users.map {
                getNIP65RelayListFlow(it)
            },
        ) { followsNIP65RelayLists ->
            MinimumRelayListProcessor
                .reliableRelaySetFor(
                    followsNIP65RelayLists.mapNotNull {
                        (it.note.event as? AdvertisedRelayListEvent)
                    },
                    relayUrlsToIgnore,
                    hasOnionConnection = proxy != null,
                ).sortedByDescending { it.users.size }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val liveHomeFollowRelayFlow: Flow<List<MinimumRelayListProcessor.RelayRecommendation>> by lazy {
        combineTransform(liveHomeFollowListFlow, connectToRelaysFlow) { followList, existing ->
            if (followList != null) {
                emit(
                    relaysFromPeopleListFlows(
                        followList,
                        existing.mapNotNullTo(HashSet()) {
                            if (it.read && FeedType.FOLLOWS in it.feedTypes) {
                                it.url
                            } else {
                                null
                            }
                        },
                    ),
                )
            } else {
                emit(MutableStateFlow(emptyList()))
            }
        }.flatMapLatest {
            it
        }
    }

    val liveHomeFollowRelays: StateFlow<List<MinimumRelayListProcessor.RelayRecommendation>> by lazy {
        liveHomeFollowRelayFlow.stateIn(scope, SharingStarted.Eagerly, emptyList())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val liveNotificationList: Flow<ListNameNotePair> by lazy {
        defaultNotificationFollowList.flatMapLatest { listName ->
            loadPeopleListFlowFromListName(listName)
        }
    }

    val liveNotificationFollowLists: StateFlow<LiveFollowLists?> by lazy {
        combinePeopleListFlows(liveKind3FollowsFlow, liveNotificationList)
            .stateIn(scope, SharingStarted.Eagerly, LiveFollowLists())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val liveStoriesList: Flow<ListNameNotePair> by lazy {
        defaultStoriesFollowList.flatMapLatest { listName ->
            loadPeopleListFlowFromListName(listName)
        }
    }

    val liveStoriesFollowLists: StateFlow<LiveFollowLists?> by lazy {
        combinePeopleListFlows(liveKind3FollowsFlow, liveStoriesList)
            .stateIn(scope, SharingStarted.Eagerly, LiveFollowLists())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val liveDiscoveryList: Flow<ListNameNotePair> by lazy {
        defaultDiscoveryFollowList.flatMapLatest { listName ->
            loadPeopleListFlowFromListName(listName)
        }
    }

    val liveDiscoveryFollowLists: StateFlow<LiveFollowLists?> by lazy {
        combinePeopleListFlows(liveKind3FollowsFlow, liveDiscoveryList)
            .stateIn(scope, SharingStarted.Eagerly, LiveFollowLists())
    }

    private fun decryptLiveFollows(
        listEvent: GeneralListEvent,
        onReady: (LiveFollowLists) -> Unit,
    ) {
        listEvent.privateTags(signer) { privateTagList ->
            onReady(
                LiveFollowLists(
                    users =
                        (listEvent.bookmarkedPeople() + listEvent.filterUsers(privateTagList)).toSet(),
                    hashtags =
                        (listEvent.hashtags() + listEvent.filterHashtags(privateTagList)).toSet(),
                    geotags =
                        (listEvent.geohashes() + listEvent.filterGeohashes(privateTagList)).toSet(),
                    communities =
                        (listEvent.taggedAddresses() + listEvent.filterAddresses(privateTagList))
                            .map { it.toTag() }
                            .toSet(),
                ),
            )
        }
    }

    suspend fun waitToDecrypt(peopleListFollows: GeneralListEvent): LiveFollowLists? =
        withTimeoutOrNull(1000) {
            suspendCancellableCoroutine { continuation ->
                decryptLiveFollows(peopleListFollows) {
                    continuation.resume(it)
                }
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

    val flowHiddenUsers: StateFlow<LiveHiddenUsers> by lazy {
        combineTransform(
            transientHiddenUsers,
            showSensitiveContent,
            getBlockListNote().flow().metadata.stateFlow,
            getMuteListNote().flow().metadata.stateFlow,
        ) { transientHiddenUsers, showSensitiveContent, blockList, muteList ->
            checkNotInMainThread()

            val resultBlockList =
                (blockList.note.event as? PeopleListEvent)?.let {
                    withTimeoutOrNull(1000) {
                        suspendCancellableCoroutine { continuation ->
                            it.publicAndPrivateUsersAndWords(signer) { continuation.resume(it) }
                        }
                    }
                }
                    ?: PeopleListEvent.UsersAndWords()

            val resultMuteList =
                (muteList.note.event as? MuteListEvent)?.let {
                    withTimeoutOrNull(1000) {
                        suspendCancellableCoroutine { continuation ->
                            it.publicAndPrivateUsersAndWords(signer) { continuation.resume(it) }
                        }
                    }
                }
                    ?: PeopleListEvent.UsersAndWords()

            val hiddenWords = resultBlockList.words + resultMuteList.words

            emit(
                LiveHiddenUsers(
                    hiddenUsers = (resultBlockList.users + resultMuteList.users),
                    hiddenWords = hiddenWords,
                    spammers = transientHiddenUsers,
                    showSensitiveContent = showSensitiveContent,
                ),
            )
        }.stateIn(
            scope,
            SharingStarted.Eagerly,
            LiveHiddenUsers(
                hiddenUsers = setOf(),
                hiddenWords = setOf(),
                spammers = transientHiddenUsers.value,
                showSensitiveContent = showSensitiveContent.value,
            ),
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
                        withTimeoutOrNull(1000) {
                            suspendCancellableCoroutine { continuation ->
                                userState.user.latestBookmarkList?.privateTags(signer) {
                                    continuation.resume(userState.user.latestBookmarkList)
                                }
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
            this.transientPaymentRequests.value = transientPaymentRequests.value + paymentRequest
        }
    }

    fun dismissPaymentRequest(request: PaymentRequest) {
        if (this.transientPaymentRequests.value.contains(request)) {
            this.transientPaymentRequests.value = transientPaymentRequests.value - request
            this.transientPaymentRequestDismissals = transientPaymentRequestDismissals + request
        }
    }

    var userProfileCache: User? = null

    fun updateOptOutOptions(
        warnReports: Boolean,
        filterSpam: Boolean,
    ) {
        warnAboutPostsWithReports = warnReports
        filterSpamFromStrangers = filterSpam
        LocalCache.antiSpam.active = filterSpamFromStrangers
        if (!filterSpamFromStrangers) {
            transientHiddenUsers.update {
                emptySet()
            }
        }
        live.invalidateData()
        saveable.invalidateData()
    }

    fun userProfile(): User =
        userProfileCache
            ?: run {
                val myUser: User = LocalCache.getOrCreateUser(keyPair.pubKey.toHexKey())
                userProfileCache = myUser
                myUser
            }

    fun isWriteable(): Boolean = keyPair.privKey != null || signer is NostrSignerExternal

    fun sendKind3RelayList(relays: Map<String, ContactListEvent.ReadWrite>) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList

        if (contactList != null && contactList.tags.isNotEmpty()) {
            ContactListEvent.updateRelayList(
                earlierVersion = contactList,
                relayUse = relays,
                signer = signer,
            ) {
                Client.send(it)
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
                // Client.send(it)
                LocalCache.justConsume(it, null)
            }
        }
    }

    fun sendNewUserMetadata(
        name: String? = null,
        picture: String? = null,
        banner: String? = null,
        website: String? = null,
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
            about = about,
            nip05 = nip05,
            lnAddress = lnAddress,
            lnURL = lnURL,
            twitter = twitter,
            mastodon = mastodon,
            github = github,
            signer = signer,
        ) {
            Client.send(it)
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

        if (note.event is ChatMessageEvent) {
            val event = note.event as ChatMessageEvent
            val users =
                event
                    .recipientsPubKey()
                    .plus(event.pubKey)
                    .toSet()
                    .toList()

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
                            Client.send(it)
                            LocalCache.consume(it)
                        }
                    }

                    return
                }
            }

            note.event?.let {
                ReactionEvent.create(reaction, it, signer) {
                    Client.send(it)
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
            ?: localRelays.filter { it.read }.map { it.url }.toSet()

    fun hasWalletConnectSetup(): Boolean = zapPaymentRequest != null

    fun isNIP47Author(pubkeyHex: String?): Boolean = (getNIP47Signer().pubKey == pubkeyHex)

    fun getNIP47Signer(): NostrSigner =
        zapPaymentRequest?.secret?.hexToByteArray()?.let { NostrSignerInternal(KeyPair(it)) }
            ?: signer

    fun decryptZapPaymentResponseEvent(
        zapResponseEvent: LnZapPaymentResponseEvent,
        onReady: (Response) -> Unit,
    ) {
        val myNip47 = zapPaymentRequest ?: return

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

    fun calculateZappedAmount(
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

        zapPaymentRequest?.let { nip47 ->
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
                wcListener.start()

                LocalCache.consume(event, zappedNote) { it.response(signer) { onResponse(it) } }

                Client.send(event, nip47.relayUri, wcListener.feedTypes) { wcListener.destroy() }

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
                ?: localRelays.map { it.url }.toSet(),
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
                Client.send(it)
                LocalCache.justConsume(it, null)
            }
        }

        note.event?.let {
            ReportEvent.create(it, type, signer, content) {
                Client.send(it)
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
            Client.send(it)
            LocalCache.justConsume(it, null)
        }
    }

    fun delete(note: Note) {
        delete(listOf(note))
    }

    fun delete(notes: List<Note>) {
        if (!isWriteable()) return

        val myEvents = notes.filter { it.author == userProfile() }
        val myNoteVersions = myEvents.mapNotNull { it.event as? Event }

        if (myNoteVersions.isNotEmpty()) {
            DeletionEvent.create(myNoteVersions, signer) {
                Client.send(it)
                LocalCache.justConsume(it, null)
            }
        }
    }

    fun createHTTPAuthorization(
        url: String,
        method: String,
        body: ByteArray? = null,
        onReady: (HTTPAuthorizationEvent) -> Unit,
    ) {
        if (!isWriteable()) return

        HTTPAuthorizationEvent.create(url, method, body, signer, onReady = onReady)
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
                    Client.send(it)
                    LocalCache.justConsume(it, null)
                }
            } else {
                GenericRepostEvent.create(it, signer) {
                    Client.send(it)
                    LocalCache.justConsume(it, null)
                }
            }
        }
    }

    fun broadcast(note: Note) {
        note.event?.let {
            if (it is WrappedEvent && it.host != null) {
                it.host?.let {
                    Client.sendFilterAndStopOnFirstResponse(
                        filters =
                            listOf(
                                TypedFilter(
                                    setOf(FeedType.FOLLOWS, FeedType.PRIVATE_DMS, FeedType.GLOBAL),
                                    Filter(
                                        ids = listOf(it.id),
                                    ),
                                ),
                            ),
                        onResponse = {
                            Client.send(it)
                        },
                    )
                }
            } else {
                Client.send(it)
            }
        }
    }

    suspend fun updateAttestations() {
        Log.d("Pending Attestations", "Updating ${pendingAttestations.value.size} pending attestations")

        pendingAttestations.value.forEach { pair ->
            val newAttestation = OtsEvent.upgrade(pair.value, pair.key)

            if (pair.value != newAttestation) {
                OtsEvent.create(pair.key, newAttestation, signer) {
                    LocalCache.justConsume(it, null)
                    Client.send(it)

                    pendingAttestations.update {
                        it - pair.key
                    }
                }
            }
        }
    }

    fun hasPendingAttestations(note: Note): Boolean {
        val id = note.event?.id() ?: note.idHex
        return pendingAttestations.value[id] != null
    }

    fun timestamp(note: Note) {
        if (!isWriteable()) return
        if (note.isDraft()) return

        val id = note.event?.id() ?: note.idHex

        pendingAttestations.update {
            it + Pair(id, OtsEvent.stamp(id))
        }

        saveable.invalidateData()
    }

    fun follow(user: User) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList

        if (contactList != null) {
            ContactListEvent.followUser(contactList, user.pubkeyHex, signer) {
                Client.send(it)
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
                Client.send(it)
                LocalCache.justConsume(it, null)
            }
        }
    }

    fun follow(channel: Channel) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList

        if (contactList != null) {
            ContactListEvent.followEvent(contactList, channel.idHex, signer) {
                Client.send(it)
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
                Client.send(it)
                LocalCache.justConsume(it, null)
            }
        }
    }

    fun follow(community: AddressableNote) {
        if (!isWriteable()) return

        val contactList = userProfile().latestContactList

        if (contactList != null) {
            ContactListEvent.followAddressableEvent(contactList, community.address, signer) {
                Client.send(it)
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
                Client.send(it)
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
                Client.send(it)
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
                Client.send(it)
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
        Client.send(event)
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
                blurhash = headerInfo.blurHash,
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
        relayList: List<RelaySetupInfo>? = null,
    ): Note? {
        if (!isWriteable()) return null

        Client.send(data, relayList = relayList)
        LocalCache.consume(data, null)

        Client.send(signedEvent, relayList = relayList)
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
        relayList: List<RelaySetupInfo>? = null,
    ) {
        Client.send(data, relayList = relayList)
        Client.send(signedEvent, relayList = relayList)
    }

    fun sendHeader(
        signedEvent: FileHeaderEvent,
        relayList: List<RelaySetupInfo>? = null,
        onReady: (Note) -> Unit,
    ) {
        Client.send(signedEvent, relayList = relayList)
        LocalCache.consume(signedEvent, null)

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
            blurhash = headerInfo.blurHash,
            alt = alt,
            originalHash = originalHash,
            sensitiveContent = sensitiveContent,
            signer = signer,
        ) { event ->
            onReady(event)
        }
    }

    fun sendHeader(
        imageUrl: String,
        magnetUri: String?,
        headerInfo: FileHeader,
        alt: String?,
        sensitiveContent: Boolean,
        originalHash: String? = null,
        relayList: List<RelaySetupInfo>? = null,
        onReady: (Note) -> Unit,
    ) {
        if (!isWriteable()) return

        FileHeaderEvent.create(
            url = imageUrl,
            magnetUri = magnetUri,
            mimeType = headerInfo.mimeType,
            hash = headerInfo.hash,
            size = headerInfo.size.toString(),
            dimensions = headerInfo.dim,
            blurhash = headerInfo.blurHash,
            alt = alt,
            originalHash = originalHash,
            sensitiveContent = sensitiveContent,
            signer = signer,
        ) { event ->
            sendHeader(event, relayList = relayList, onReady)
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
        relayList: List<RelaySetupInfo>? = null,
        geohash: String? = null,
        nip94attachments: List<Event>? = null,
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
            nip94attachments = nip94attachments,
            signer = signer,
            isDraft = draftTag != null,
        ) {
            if (draftTag != null) {
                if (message.isBlank()) {
                    deleteDraft(draftTag)
                } else {
                    DraftEvent.create(draftTag, it, emptyList(), signer) { draftEvent ->
                        val newRelayList = getPrivateOutboxRelayList()?.relays()
                        if (newRelayList != null) {
                            Client.sendPrivately(draftEvent, newRelayList)
                        } else {
                            Client.send(draftEvent, relayList = relayList)
                        }
                        LocalCache.justConsume(draftEvent, null)
                    }
                }
            } else {
                Client.send(it, relayList = relayList)
                LocalCache.justConsume(it, null)

                replyTo?.forEach { it.event?.let { Client.send(it, relayList = relayList) } }
                addresses?.forEach {
                    LocalCache.getAddressableNoteIfExists(it.toTag())?.event?.let {
                        Client.send(it, relayList = relayList)
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
        relayList: List<RelaySetupInfo>? = null,
        geohash: String? = null,
        nip94attachments: List<FileHeaderEvent>? = null,
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
            nip94attachments = nip94attachments,
            forkedFrom = forkedFrom,
            signer = signer,
            isDraft = draftTag != null,
        ) {
            if (draftTag != null) {
                if (message.isBlank()) {
                    deleteDraft(draftTag)
                } else {
                    DraftEvent.create(draftTag, it, signer) { draftEvent ->
                        val newRelayList = getPrivateOutboxRelayList()?.relays()
                        if (newRelayList != null) {
                            Client.sendPrivately(draftEvent, newRelayList)
                        } else {
                            Client.send(draftEvent, relayList = relayList)
                        }
                        LocalCache.justConsume(draftEvent, null)
                    }
                }
            } else {
                Client.send(it, relayList = relayList)
                LocalCache.justConsume(it, null)

                // broadcast replied notes
                replyingTo?.let {
                    LocalCache.getNoteIfExists(replyingTo)?.event?.let {
                        Client.send(it, relayList = relayList)
                    }
                }
                replyTo?.forEach { it.event?.let { Client.send(it, relayList = relayList) } }
                addresses?.forEach {
                    LocalCache.getAddressableNoteIfExists(it.toTag())?.event?.let {
                        Client.send(it, relayList = relayList)
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
                    Client.sendPrivately(it, relayList = note.relays.map { it.url })
                    LocalCache.justConsume(it, null)
                }
            }
            delete(note)
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
        relayList: List<RelaySetupInfo>? = null,
        geohash: String? = null,
        nip94attachments: List<FileHeaderEvent>? = null,
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
            nip94attachments = nip94attachments,
            forkedFrom = forkedFrom,
            signer = signer,
            isDraft = draftTag != null,
        ) {
            if (draftTag != null) {
                if (message.isBlank()) {
                    deleteDraft(draftTag)
                } else {
                    DraftEvent.create(draftTag, it, signer) { draftEvent ->
                        val newRelayList = getPrivateOutboxRelayList()?.relays()
                        if (newRelayList != null) {
                            Client.sendPrivately(draftEvent, newRelayList)
                        } else {
                            Client.send(draftEvent, relayList = relayList)
                        }
                        LocalCache.justConsume(draftEvent, null)
                    }
                }
            } else {
                Client.send(it, relayList = relayList)
                LocalCache.justConsume(it, null)

                // broadcast replied notes
                replyingTo?.let {
                    LocalCache.getNoteIfExists(replyingTo)?.event?.let {
                        Client.send(it, relayList = relayList)
                    }
                }
                replyTo?.forEach { it.event?.let { Client.send(it, relayList = relayList) } }
                addresses?.forEach {
                    LocalCache.getAddressableNoteIfExists(it.toTag())?.event?.let {
                        Client.send(it, relayList = relayList)
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
        relayList: List<RelaySetupInfo>? = null,
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
            Client.send(it, relayList = relayList)
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
        relayList: List<RelaySetupInfo>? = null,
        geohash: String? = null,
        nip94attachments: List<FileHeaderEvent>? = null,
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
            nip94attachments = nip94attachments,
            isDraft = draftTag != null,
        ) {
            if (draftTag != null) {
                if (message.isBlank()) {
                    deleteDraft(draftTag)
                } else {
                    DraftEvent.create(draftTag, it, signer) { draftEvent ->
                        val newRelayList = getPrivateOutboxRelayList()?.relays()
                        if (newRelayList != null) {
                            Client.sendPrivately(draftEvent, newRelayList)
                        } else {
                            Client.send(draftEvent, relayList = relayList)
                        }
                        LocalCache.justConsume(draftEvent, null)
                    }
                }
            } else {
                Client.send(it, relayList = relayList)
                LocalCache.justConsume(it, null)

                // Rebroadcast replies and tags to the current relay set
                replyTo?.forEach { it.event?.let { Client.send(it, relayList = relayList) } }
                addresses?.forEach {
                    LocalCache.getAddressableNoteIfExists(it.toTag())?.event?.let {
                        Client.send(it, relayList = relayList)
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
        geohash: String? = null,
        nip94attachments: List<FileHeaderEvent>? = null,
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
            geohash = geohash,
            nip94attachments = nip94attachments,
            signer = signer,
            isDraft = draftTag != null,
        ) {
            if (draftTag != null) {
                if (message.isBlank()) {
                    deleteDraft(draftTag)
                } else {
                    DraftEvent.create(draftTag, it, signer) { draftEvent ->
                        val newRelayList = getPrivateOutboxRelayList()?.relays()
                        if (newRelayList != null) {
                            Client.sendPrivately(draftEvent, newRelayList)
                        } else {
                            Client.send(draftEvent)
                        }
                        LocalCache.justConsume(draftEvent, null)
                    }
                }
            } else {
                Client.send(it)
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
        nip94attachments: List<FileHeaderEvent>? = null,
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
            nip94attachments = nip94attachments,
            signer = signer,
            isDraft = draftTag != null,
        ) {
            if (draftTag != null) {
                if (message.isBlank()) {
                    deleteDraft(draftTag)
                } else {
                    DraftEvent.create(draftTag, it, signer) { draftEvent ->
                        val newRelayList = getPrivateOutboxRelayList()?.relays()
                        if (newRelayList != null) {
                            Client.sendPrivately(draftEvent, newRelayList)
                        } else {
                            Client.send(draftEvent)
                        }
                        LocalCache.justConsume(draftEvent, null)
                    }
                }
            } else {
                Client.send(it)
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
        nip94attachments: List<FileHeaderEvent>? = null,
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
            nip94attachments,
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
        nip94attachments: List<FileHeaderEvent>? = null,
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
            nip94attachments = nip94attachments,
            signer = signer,
            advertiseNip18 = false,
            isDraft = draftTag != null,
        ) {
            if (draftTag != null) {
                if (message.isBlank()) {
                    deleteDraft(draftTag)
                } else {
                    DraftEvent.create(draftTag, it, emptyList(), signer) { draftEvent ->
                        val newRelayList = getPrivateOutboxRelayList()?.relays()
                        if (newRelayList != null) {
                            Client.sendPrivately(draftEvent, newRelayList)
                        } else {
                            Client.send(draftEvent)
                        }
                        LocalCache.justConsume(draftEvent, null)
                    }
                }
            } else {
                Client.send(it)
                LocalCache.consume(it, null)
            }
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
        nip94attachments: List<FileHeaderEvent>? = null,
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
            nip94attachments = nip94attachments,
            draftTag = draftTag,
            signer = signer,
        ) {
            if (draftTag != null) {
                if (message.isBlank()) {
                    deleteDraft(draftTag)
                } else {
                    DraftEvent.create(draftTag, it.msg, emptyList(), signer) { draftEvent ->
                        val newRelayList = getPrivateOutboxRelayList()?.relays()
                        if (newRelayList != null) {
                            Client.sendPrivately(draftEvent, newRelayList)
                        } else {
                            Client.send(draftEvent)
                        }
                        LocalCache.justConsume(draftEvent, null)
                    }
                }
            } else {
                broadcastPrivately(it)
            }
        }
    }

    fun broadcastPrivately(signedEvents: NIP17Factory.Result) {
        val mine = signedEvents.wraps.filter { (it.recipientPubKey() == signer.pubKey) }

        mine.forEach { giftWrap ->
            giftWrap.cachedGift(signer) { gift ->
                if (gift is SealedGossipEvent) {
                    gift.cachedGossip(signer) { gossip -> LocalCache.justConsume(gossip, null) }
                } else {
                    LocalCache.justConsume(gift, null)
                }
            }

            LocalCache.consume(giftWrap, null)
        }

        val id = mine.firstOrNull()?.id
        val mineNote = if (id == null) null else LocalCache.getNoteIfExists(id)

        signedEvents.wraps.forEach { wrap ->
            // Creates an alias
            if (mineNote != null && wrap.recipientPubKey() != keyPair.pubKey.toHexKey()) {
                LocalCache.getOrAddAliasNote(wrap.id, mineNote)
            }

            val receiver = wrap.recipientPubKey()
            if (receiver != null) {
                val relayList =
                    (
                        LocalCache
                            .getAddressableNoteIfExists(ChatMessageRelayListEvent.createAddressTag(receiver))
                            ?.event as? ChatMessageRelayListEvent
                    )?.relays()?.ifEmpty { null }

                if (relayList != null) {
                    Client.sendPrivately(signedEvent = wrap, relayList = relayList)
                } else {
                    Client.send(wrap)
                }
            } else {
                Client.send(wrap)
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
            Client.send(it)
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
            Client.send(it)
            LocalCache.justConsume(it, null)
        }
    }

    fun createStatus(newStatus: String) {
        if (!isWriteable()) return

        StatusEvent.create(newStatus, "general", expiration = null, signer) {
            Client.send(it)
            LocalCache.justConsume(it, null)
        }
    }

    fun deleteStatus(oldStatus: AddressableNote) {
        if (!isWriteable()) return
        val oldEvent = oldStatus.event as? StatusEvent ?: return

        StatusEvent.clear(oldEvent, signer) { event ->
            Client.send(event)
            LocalCache.justConsume(event, null)

            DeletionEvent.createForVersionOnly(listOf(event), signer) { event2 ->
                Client.send(event2)
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
            Client.send(it)
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
                Client.send(it)
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
                Client.send(it)
                LocalCache.justConsume(it, null)
            }
        }
    }

    fun addToGallery(
        idHex: String,
        url: String,
        relay: String?,
        blurhash: String?,
        dim: String?,
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
            Client.send(event)
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
                Client.send(it)
                LocalCache.consume(it)
            }
        } else {
            BookmarkListEvent.addEvent(
                userProfile().latestBookmarkList,
                note.idHex,
                isPrivate,
                signer,
            ) {
                Client.send(it)
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
                Client.send(it)
                LocalCache.consume(it)
            }
        } else {
            BookmarkListEvent.removeEvent(
                bookmarks,
                note.idHex,
                isPrivate,
                signer,
            ) {
                Client.send(it)
                LocalCache.consume(it)
            }
        }
    }

    fun createAuthEvent(
        relay: Relay,
        challenge: String,
        onReady: (RelayAuthEvent) -> Unit,
    ) = createAuthEvent(relay.url, challenge, onReady = onReady)

    fun createAuthEvent(
        relayUrl: String,
        challenge: String,
        onReady: (RelayAuthEvent) -> Unit,
    ) {
        if (!isWriteable()) return

        RelayAuthEvent.create(relayUrl, challenge, signer, onReady = onReady)
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
                Client.send(it)
                LocalCache.consume(it, null)
            }
        } else {
            MuteListEvent.createListWithWord(
                word = word,
                isPrivate = true,
                signer = signer,
            ) {
                Client.send(it)
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
                Client.send(it)
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
                Client.send(it)
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
                Client.send(it)
                LocalCache.consume(it, null)
            }
        } else {
            MuteListEvent.createListWithUser(
                pubKeyHex = pubkeyHex,
                isPrivate = true,
                signer = signer,
            ) {
                Client.send(it)
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
                Client.send(it)
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
                Client.send(it)
                LocalCache.consume(it, null)
            }
        }

        transientHiddenUsers.update {
            it - pubkeyHex
        }
        live.invalidateData()
        saveable.invalidateData()
    }

    fun changeDefaultZapType(zapType: LnZapEvent.ZapType) {
        defaultZapType = zapType
        live.invalidateData()
        saveable.invalidateData()
    }

    fun changeDefaultFileServer(server: Nip96MediaServers.ServerName) {
        defaultFileServer = server
        live.invalidateData()
        saveable.invalidateData()
    }

    fun changeDefaultHomeFollowList(name: String) {
        defaultHomeFollowList.tryEmit(name)
        live.invalidateData()
        saveable.invalidateData()
    }

    fun changeDefaultStoriesFollowList(name: String) {
        defaultStoriesFollowList.tryEmit(name)
        live.invalidateData()
        saveable.invalidateData()
    }

    fun changeDefaultNotificationFollowList(name: String) {
        defaultNotificationFollowList.tryEmit(name)
        live.invalidateData()
        saveable.invalidateData()
    }

    fun changeDefaultDiscoveryFollowList(name: String) {
        defaultDiscoveryFollowList.tryEmit(name)
        live.invalidateData()
        saveable.invalidateData()
    }

    fun changeZapAmounts(newAmounts: List<Long>) {
        zapAmountChoices = newAmounts
        live.invalidateData()
        saveable.invalidateData()
    }

    fun changeReactionTypes(newTypes: List<String>) {
        reactionChoices = newTypes
        live.invalidateData()
        saveable.invalidateData()
    }

    fun changeZapPaymentRequest(newServer: Nip47WalletConnect.Nip47URI?) {
        zapPaymentRequest = newServer
        live.invalidateData()
        saveable.invalidateData()
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
            Client.send(it)
            LocalCache.justConsume(it, null)

            follow(channel)
        }
    }

    fun requestDVMContentDiscovery(
        dvmPublicKey: String,
        onReady: (event: NIP90ContentDiscoveryRequestEvent) -> Unit,
    ) {
        NIP90ContentDiscoveryRequestEvent.create(dvmPublicKey, signer.pubKey, getReceivingRelays(), signer) {
            val relayList = (LocalCache.getAddressableNoteIfExists(AdvertisedRelayListEvent.createAddressTag(dvmPublicKey))?.event as? AdvertisedRelayListEvent)?.readRelays()

            if (relayList != null) {
                Client.sendPrivately(it, relayList)
            } else {
                Client.send(it)
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

        return event.cachedGift(signer, onReady)
    }

    fun unseal(
        event: SealedGossipEvent,
        onReady: (Event) -> Unit,
    ) {
        if (!isWriteable()) return

        return event.cachedGossip(signer, onReady)
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

    fun updateLocalRelayServers(servers: Set<String>) {
        localRelayServers = servers
        liveLanguages.invalidateData()
        saveable.invalidateData()
    }

    fun addDontTranslateFrom(languageCode: String) {
        dontTranslateFrom = dontTranslateFrom.plus(languageCode)
        liveLanguages.invalidateData()

        saveable.invalidateData()
    }

    fun updateTranslateTo(languageCode: String) {
        translateTo = languageCode
        liveLanguages.invalidateData()

        saveable.invalidateData()
    }

    fun prefer(
        source: String,
        target: String,
        preference: String,
    ) {
        languagePreferences = languagePreferences + Pair("$source,$target", preference)
        saveable.invalidateData()
    }

    fun preferenceBetween(
        source: String,
        target: String,
    ): String? = languagePreferences.get("$source,$target")

    private fun updateContactListTo(newContactList: ContactListEvent?) {
        if (newContactList == null || newContactList.tags.isEmpty()) return

        // Events might be different objects, we have to compare their ids.
        if (backupContactList?.id != newContactList.id) {
            backupContactList = newContactList
            saveable.invalidateData()
        }
    }

    private fun updateDMRelayList(newDMRelayList: ChatMessageRelayListEvent?) {
        if (newDMRelayList == null || newDMRelayList.tags.isEmpty()) return

        // Events might be different objects, we have to compare their ids.
        if (backupDMRelayList?.id != newDMRelayList.id) {
            backupDMRelayList = newDMRelayList
            saveable.invalidateData()
        }
    }

    private fun updateNIP65RelayList(newNIP65RelayList: AdvertisedRelayListEvent?) {
        if (newNIP65RelayList == null || newNIP65RelayList.tags.isEmpty()) return

        // Events might be different objects, we have to compare their ids.
        if (backupNIP65RelayList?.id != newNIP65RelayList.id) {
            backupNIP65RelayList = newNIP65RelayList
            saveable.invalidateData()
        }
    }

    // Takes a User's relay list and adds the types of feeds they are active for.
    fun activeRelays(): Array<RelaySetupInfo>? {
        val usersRelayList =
            userProfile()
                .latestContactList
                ?.relays()
                ?.map {
                    val url = RelayUrlFormatter.normalize(it.key)

                    val localFeedTypes =
                        localRelays.firstOrNull { localRelay -> RelayUrlFormatter.normalize(localRelay.url) == url }?.feedTypes?.minus(setOf(FeedType.SEARCH, FeedType.WALLET_CONNECT))
                            ?: Constants.defaultRelays
                                .filter { defaultRelay -> defaultRelay.url == url }
                                .firstOrNull()
                                ?.feedTypes
                            ?: Constants.activeTypesGlobalChats

                    RelaySetupInfo(url, it.value.read, it.value.write, localFeedTypes)
                }?.ifEmpty { null } ?: return null

        return usersRelayList.toTypedArray()
    }

    fun convertLocalRelays(): Array<RelaySetupInfo> = localRelays.map { RelaySetupInfo(RelayUrlFormatter.normalize(it.url), it.read, it.write, it.feedTypes.minus(setOf(FeedType.SEARCH, FeedType.WALLET_CONNECT))) }.toTypedArray()

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

    fun followingKeySet(): Set<HexKey> = userProfile().cachedFollowingKeySet()

    fun followingTagSet(): Set<HexKey> = userProfile().cachedFollowingTagSet()

    fun isAcceptable(user: User): Boolean {
        if (userProfile().pubkeyHex == user.pubkeyHex) {
            return true
        }

        if (user.pubkeyHex in followingKeySet()) {
            return true
        }

        if (!warnAboutPostsWithReports) {
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
        if (!warnAboutPostsWithReports) {
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
        val followsPlusMe = userProfile().latestContactList?.verifiedFollowKeySetAndMe ?: emptySet()

        val innerReports =
            if (note.event is RepostEvent || note.event is GenericRepostEvent) {
                note.replyTo?.map { getRelevantReports(it) }?.flatten() ?: emptyList()
            } else {
                emptyList()
            }

        return (
            note.reportsBy(followsPlusMe) +
                (note.author?.reportsBy(followsPlusMe) ?: emptyList()) +
                innerReports
        ).toSet()
    }

    fun saveKind3RelayList(value: List<RelaySetupInfo>) {
        try {
            localRelays = value.toSet()
            return sendKind3RelayList(
                value.associate { it.url to ContactListEvent.ReadWrite(it.read, it.write) },
            )
        } finally {
            saveable.invalidateData()
        }
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
                Client.send(it)
                LocalCache.justConsume(it, null)
            }
        } else {
            ChatMessageRelayListEvent.createFromScratch(
                relays = dmRelays,
                signer = signer,
            ) {
                Client.send(it)
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
                Client.send(it)
                LocalCache.justConsume(it, null)
            }
        } else {
            PrivateOutboxRelayListEvent.createFromScratch(
                relays = relays,
                signer = signer,
            ) {
                Client.send(it)
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
                Client.send(it)
                LocalCache.justConsume(it, null)
            }
        } else {
            SearchRelayListEvent.createFromScratch(
                relays = searchRelays,
                signer = signer,
            ) {
                Client.send(it)
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
                Client.send(it)
                LocalCache.justConsume(it, null)
            }
        } else {
            AdvertisedRelayListEvent.createFromScratch(
                relays = relays,
                signer = signer,
            ) {
                Client.send(it)
                LocalCache.justConsume(it, null)
            }
        }
    }

    fun getFileServersList(): FileServersEvent? = getFileServersNote().event as? FileServersEvent

    fun getFileServersListFlow(): StateFlow<NoteState> = getFileServersNote().flow().metadata.stateFlow

    fun getFileServersNote(): AddressableNote = LocalCache.getOrCreateAddressableNote(FileServersEvent.createAddressATag(userProfile().pubkeyHex))

    fun sendFileServersList(servers: List<String>) {
        if (!isWriteable()) return

        val serverList = getFileServersList()

        if (serverList != null && serverList.tags.isNotEmpty()) {
            FileServersEvent.updateRelayList(
                earlierVersion = serverList,
                relays = servers,
                signer = signer,
            ) {
                Client.send(it)
                LocalCache.justConsume(it, null)
            }
        } else {
            FileServersEvent.createFromScratch(
                relays = servers,
                signer = signer,
            ) {
                Client.send(it)
                LocalCache.justConsume(it, null)
            }
        }
    }

    fun setHideDeleteRequestDialog() {
        hideDeleteRequestDialog = true
        saveable.invalidateData()
    }

    fun setHideNIP17WarningDialog() {
        hideNIP17WarningDialog = true
        saveable.invalidateData()
    }

    fun setHideBlockAlertDialog() {
        hideBlockAlertDialog = true
        saveable.invalidateData()
    }

    fun updateShowSensitiveContent(show: Boolean?) {
        showSensitiveContent.update {
            show
        }
        saveable.invalidateData()
        live.invalidateData()
    }

    fun markAsRead(
        route: String,
        timestampInSecs: Long,
    ): Boolean {
        val lastTime = lastReadPerRoute[route]
        return if (lastTime == null || timestampInSecs > lastTime) {
            lastReadPerRoute = lastReadPerRoute + Pair(route, timestampInSecs)
            saveable.invalidateData()
            true
        } else {
            false
        }
    }

    fun loadLastRead(route: String): Long = lastReadPerRoute[route] ?: 0

    fun hasDonatedInThisVersion(): Boolean = hasDonatedInVersion.contains(BuildConfig.VERSION_NAME)

    fun markDonatedInThisVersion() {
        hasDonatedInVersion = hasDonatedInVersion + BuildConfig.VERSION_NAME
        saveable.invalidateData()
        live.invalidateData()
    }

    suspend fun registerObservers() =
        withContext(Dispatchers.Main) {
            // saves contact list for the next time.
            scope.launch {
                Log.d("AccountRegisterObservers", "Kind 3 Collector Start")
                userProfile().flow().follows.stateFlow.collect {
                    Log.d("AccountRegisterObservers", "Updating Kind 3 ${userProfile().toBestDisplayName()}")
                    updateContactListTo(userProfile().latestContactList)
                }
            }

            scope.launch {
                Log.d("AccountRegisterObservers", "NIP-17 Relay List Collector Start")
                getDMRelayListFlow().collect {
                    Log.d("AccountRegisterObservers", "Updating DM Relay List for ${userProfile().toBestDisplayName()}")
                    (it.note.event as? ChatMessageRelayListEvent)?.let {
                        updateDMRelayList(it)
                    }
                }
            }

            scope.launch {
                Log.d("AccountRegisterObservers", "NIP-65 Relay List Collector Start")
                getNIP65RelayListFlow().collect {
                    Log.d("AccountRegisterObservers", "Updating NIP-65 List for ${userProfile().toBestDisplayName()}")
                    (it.note.event as? AdvertisedRelayListEvent)?.let {
                        updateNIP65RelayList(it)
                    }
                }
            }

            // imports transient blocks due to spam.
            LocalCache.antiSpam.liveSpam.observeForever {
                GlobalScope.launch(Dispatchers.IO) {
                    it.cache.spamMessages.snapshot().values.forEach { spammer ->
                        if (spammer.pubkeyHex !in transientHiddenUsers.value && spammer.duplicatedMessages.size >= 5) {
                            if (spammer.pubkeyHex != userProfile().pubkeyHex && spammer.pubkeyHex !in followingKeySet()) {
                                transientHiddenUsers.update {
                                    it + spammer.pubkeyHex
                                }
                                live.invalidateData()
                            }
                        }
                    }
                }
            }
        }

    init {
        Log.d("Account", "Init")
        backupContactList?.let {
            println("Loading saved contacts ${it.toJson()}")

            if (userProfile().latestContactList == null) {
                GlobalScope.launch(Dispatchers.IO) { LocalCache.consume(it) }
            }
        }

        backupDMRelayList?.let {
            println("Loading DM Relay List ${it.toJson()}")
            GlobalScope.launch(Dispatchers.IO) { LocalCache.verifyAndConsume(it, null) }
        }

        backupNIP65RelayList?.let {
            println("Loading saved contacts ${it.toJson()}")
            GlobalScope.launch(Dispatchers.IO) { LocalCache.verifyAndConsume(it, null) }
        }
    }
}

class AccountLiveData(
    private val account: Account,
) : LiveData<AccountState>(AccountState(account)) {
    // Refreshes observers in batches.
    private val bundler = BundledUpdate(300, Dispatchers.Default)

    fun invalidateData() {
        bundler.invalidate {
            if (hasActiveObservers()) {
                refresh()
            }
        }
    }

    fun refresh() {
        postValue(AccountState(account))
    }
}

@Immutable class AccountState(
    val account: Account,
)
