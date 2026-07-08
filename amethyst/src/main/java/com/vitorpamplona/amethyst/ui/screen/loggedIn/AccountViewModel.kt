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
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.AccountInfo
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.audio.VisualizerStyle
import com.vitorpamplona.amethyst.commons.cashu.ops.describeMintError
import com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.commons.model.observables.CreatedAtComparator
import com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.CallManager
import com.vitorpamplona.amethyst.commons.service.broadcast.BroadcastTracker
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.amethyst.commons.ui.components.UrlPreviewState
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.commons.ui.notifications.CardFeedState
import com.vitorpamplona.amethyst.commons.ui.state.GenericBaseCache
import com.vitorpamplona.amethyst.commons.ui.state.GenericBaseCacheAsync
import com.vitorpamplona.amethyst.logTime
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.UiSettingsFlow
import com.vitorpamplona.amethyst.model.UrlCachedPreviewer
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.privacyOptions.EmptyRoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.model.privacyOptions.IRoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.model.privacyOptions.RoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.service.ClinkDebitPayer
import com.vitorpamplona.amethyst.service.OnlineChecker
import com.vitorpamplona.amethyst.service.V4VPaymentHandler
import com.vitorpamplona.amethyst.service.ZapPaymentHandler
import com.vitorpamplona.amethyst.service.cashu.melt.MeltProcessor
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.lnurl.LightningAddressResolver
import com.vitorpamplona.amethyst.service.location.LocationState
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.dismissNotificationForEvent
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.RelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.nwc.NWCPaymentFilterAssembler
import com.vitorpamplona.amethyst.ui.actions.Dao
import com.vitorpamplona.amethyst.ui.actions.MediaSaverToDisk
import com.vitorpamplona.amethyst.ui.actions.NewMessageTagger
import com.vitorpamplona.amethyst.ui.components.toasts.ToastManager
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.ZapAmountCommentNotification
import com.vitorpamplona.amethyst.ui.note.ZapraiserStatus
import com.vitorpamplona.amethyst.ui.note.payViaIntent
import com.vitorpamplona.amethyst.ui.note.showAmount
import com.vitorpamplona.amethyst.ui.note.showAmountInteger
import com.vitorpamplona.amethyst.ui.screen.UiSettingsState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.CombinedZap
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.NOTIFICATION_LAST_READ_KEY
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.eventsync.EventSync
import com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet.ReloadMintRequest
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.tor.TorSettingsFlow
import com.vitorpamplona.quartz.experimental.clink.debits.DebitResponse
import com.vitorpamplona.quartz.experimental.clink.pointers.NDebit
import com.vitorpamplona.quartz.experimental.ephemChat.chat.RoomId
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryBaseEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryReadingStateEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayOfflineTracker
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.EmptyIAuthStatus
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthenticator
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CachingEventDecoder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.people.PubKeyReferenceTag
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip03Timestamp.EmptyOtsResolverBuilder
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip05DnsIdentifiers.EmptyNip05Client
import com.vitorpamplona.quartz.nip05DnsIdentifiers.INip05Client
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Client
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip17Dm.base.NIP17Group
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NRelay
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.nip28PublicChat.base.IsInPublicChatChannel
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Response
import com.vitorpamplona.quartz.nip51Lists.PinListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.OldBookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark
import com.vitorpamplona.quartz.nip51Lists.hashtagList.HashtagListEvent
import com.vitorpamplona.quartz.nip56Reports.ReportType
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip57Zaps.validate.LnurlForm
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiserAmount
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip60Cashu.token.CashuToken
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryResponse.NIP90ContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip92IMeta.imeta
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.podcasts.PodcastBoostagram
import com.vitorpamplona.quartz.podcasts.PodcastEpisode
import com.vitorpamplona.quartz.podcasts.PodcastShow
import com.vitorpamplona.quartz.podcasts.PodcastValue
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.mapNotNullAsync
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Stable
class AccountViewModel(
    val account: Account,
    val settings: UiSettingsState,
    val torSettings: TorSettingsFlow,
    val dataSources: RelaySubscriptionsCoordinator,
    val httpClientBuilder: IRoleBasedHttpClientBuilder,
    val nip05ClientBuilder: () -> INip05Client,
) : ViewModel(),
    Dao {
    var firstRoute: Route? = null

    val toastManager = ToastManager()
    val broadcastTracker = BroadcastTracker()
    val feedStates = AccountFeedContentStates(account, viewModelScope)

    /**
     * `true` when feed/note media (images and videos in `MediaUrlContent`)
     * should be routed through the local Blossom cache. Requires the master
     * toggle on, the probe up, AND the profile-pictures-only restriction
     * to be off.
     */
    val useLocalBlossomBridge: StateFlow<Boolean> =
        try {
            combine(
                account.settings.useLocalBlossomCache,
                account.settings.localBlossomCacheProfilePicturesOnly,
                Amethyst.instance.localBlossomCacheProbe.available,
            ) { toggle, profileOnly, probeUp -> toggle && probeUp && !profileOnly }.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                false,
            )
        } catch (e: UninitializedPropertyAccessException) {
            MutableStateFlow(false)
        }

    /**
     * `true` when profile pictures should be routed through the local
     * Blossom cache. Requires only the master toggle and the probe to be
     * up; the profile-pictures-only restriction does not gate this flow.
     */
    val useLocalBlossomBridgeForProfilePics: StateFlow<Boolean> =
        try {
            combine(
                account.settings.useLocalBlossomCache,
                Amethyst.instance.localBlossomCacheProbe.available,
            ) { toggle, probeUp -> toggle && probeUp }.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                false,
            )
        } catch (e: UninitializedPropertyAccessException) {
            MutableStateFlow(false)
        }

    val callManager =
        CallManager(
            signer = account.signer,
            scope = viewModelScope,
            isFollowing = { account.isFollowing(it) },
            publishEvent = { wrap ->
                viewModelScope.launch {
                    account.publishCallSignaling(wrap)
                }
            },
            isCallsEnabled = { account.settings.callsEnabled.value },
        )

    init {
        // Wire the signaling event processor eagerly so incoming call
        // events are routed to CallManager even before any CallActivity
        // is launched. Previously this was deferred to initCallController,
        // which could miss events if the UI hadn't mounted yet.
        account.newNotesPreProcessor.callManager = callManager

        // Populate CallSessionBridge so CallActivity and background
        // receivers can reach callManager + accountViewModel.
        com.vitorpamplona.amethyst.service.call.CallSessionBridge
            .set(callManager, this)
    }

    /**
     * Every known relay worth crawling for a full-history sweep (Event Sync,
     * Cashu wallet discovery): relays that have connected at least once or were
     * never tried, ordered busiest-first so the most fruitful are queried first.
     */
    fun crawlRelayDb(): List<NormalizedRelayUrl> {
        val stats = Amethyst.instance.relayStats.snapshot()

        val relays =
            account.cache.relayHints.relayDB
                .keys()
                .filter { url ->
                    val relayStat = stats[url]
                    // has connected at least once OR never tried.
                    if (relayStat != null) {
                        relayStat.connectionCompleted > 0 || relayStat.connectionTentatives == 0
                    } else {
                        true
                    }
                }

        val sortMap = relays.associateWith { stats.get(it)?.receivedBytes }

        return relays.sortedByDescending { sortMap[it] }
    }

    /**
     * A fresh, relay-authenticated [INostrClient] whose received events do NOT
     * land in the production [com.vitorpamplona.amethyst.model.LocalCache] — for
     * crawls (Event Sync, Cashu wallet discovery). The caller must `close()` it.
     */
    fun buildCrawlClient(): INostrClient {
        // Create a new scope that inherits the ViewModel's lifecycle
        // but uses a SupervisorJob so child failures are independent.
        val customScope = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob())

        // Provides a relay pool. Crawls hit many relays with overlapping
        // filters, so the duplicate-frame decoder pays off most here.
        val newClient = NostrClient(Amethyst.instance.websocketBuilder, customScope, CachingEventDecoder())

        // Authenticates with relays (registers itself with the client).
        RelayAuthenticator(
            newClient,
            customScope,
            signWithAllLoggedInUsers = { _, authTemplate ->
                if (account.signer.isWriteable()) {
                    try {
                        listOf(account.signer.sign(authTemplate))
                    } catch (e: Exception) {
                        Log.e("AuthCoordinator", "Failed trying to authenticate a writeable account", e)
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            },
        )

        return newClient
    }

    val eventSync =
        EventSync(
            accountPubKey = account.signer.pubKey,
            relayDb = { crawlRelayDb() },
            outboxTargets = { account.nip65RelayList.outboxFlow.value },
            inboxTargets = { account.nip65RelayList.inboxFlow.value },
            dmTargets = { account.dmRelayList.flow.value },
            // creates a new client to make sure these events don't end up polluting the local cache.
            clientBuilder = { buildCrawlClient() },
            scope = viewModelScope,
        )

    val tempManualPaymentCache = LruCache<String, List<ZapPaymentHandler.Payable>>(5)

    /** Hand-off for the Reload Mint screen — keyed by a uid put on the back stack. */
    val tempReloadRequestCache = LruCache<String, ReloadMintRequest>(5)

    @OptIn(ExperimentalCoroutinesApi::class)
    val notificationHasNewItems =
        // When split-notifications is on, the badge tracks only the Following feed.
        account.settings.splitNotificationsEnabled
            .flatMapLatest { isSplit ->
                val source =
                    if (isSplit) feedStates.notificationsFollowing else feedStates.notifications
                combineTransform(
                    account.loadLastReadFlow(NOTIFICATION_LAST_READ_KEY),
                    source.feedContent
                        .flatMapLatest {
                            if (it is CardFeedState.Loaded) {
                                it.feed
                            } else {
                                MutableStateFlow(null)
                            }
                        }.map { it?.list?.firstOrNull()?.createdAt() },
                ) { lastRead, newestItemCreatedAt ->
                    emit(newestItemCreatedAt != null && newestItemCreatedAt > lastRead)
                }
            }.onStart {
                val source =
                    if (account.settings.splitNotificationsEnabled.value) {
                        feedStates.notificationsFollowing
                    } else {
                        feedStates.notifications
                    }
                val lastRead = account.loadLastReadFlow(NOTIFICATION_LAST_READ_KEY).value
                val cards = source.feedContent.value
                if (cards is CardFeedState.Loaded) {
                    val newestItemCreatedAt =
                        cards.feed.value.list
                            .firstOrNull()
                            ?.createdAt()
                    emit(newestItemCreatedAt != null && newestItemCreatedAt > lastRead)
                }
            }

    val notificationHasNewItemsFlow =
        notificationHasNewItems
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(30000), false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val messagesHasNewItems =
        feedStates.dmKnown.feedContent
            .flatMapLatest {
                if (it is FeedState.Loaded) {
                    it.feed
                } else {
                    MutableStateFlow(null)
                }
            }.flatMapLatest { loadedFeedState ->
                val flows =
                    loadedFeedState?.list?.mapNotNull { chat ->
                        unreadPrivateChatRoute(chat)?.let { (route, createdAt) ->
                            account.settings.getLastReadFlow(route).map { lastReadAt ->
                                createdAt > lastReadAt
                            }
                        }
                    }

                if (!flows.isNullOrEmpty()) {
                    combine(flows) { newItems ->
                        newItems.any { it }
                    }
                } else {
                    MutableStateFlow(false)
                }
            }.onStart {
                val feed = feedStates.dmKnown.feedContent.value
                if (feed is FeedState.Loaded) {
                    val newItems =
                        feed.feed.value.list.any { chat ->
                            unreadPrivateChatRoute(chat)?.let { (route, createdAt) ->
                                val lastReadAt =
                                    account.settings.lastReadPerRoute.value[route]
                                        ?.value ?: 0L
                                createdAt > lastReadAt
                            } == true
                        }
                    emit(newItems)
                }
            }

    val messagesHasNewItemsFlow =
        messagesHasNewItems
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(30000), false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun tabHasNewItems(
        route: String,
        feedContent: StateFlow<FeedState>,
    ): Flow<Boolean> =
        combineTransform(
            account.loadLastReadFlow(route),
            feedContent
                .flatMapLatest {
                    if (it is FeedState.Loaded) {
                        it.feed
                    } else {
                        MutableStateFlow(null)
                    }
                }.map { loadedFeedState ->
                    loadedFeedState?.list?.firstOrNull { it.event != null && it.event !is GenericRepostEvent && it.event !is RepostEvent }?.createdAt()
                },
        ) { lastRead, newestItemCreatedAt ->
            emit(newestItemCreatedAt != null && newestItemCreatedAt > lastRead)
        }

    val homeHasNewItems: Flow<Boolean> =
        combine(
            settings.uiSettingsFlow.showHomeNewThreadsTab,
            settings.uiSettingsFlow.showHomeConversationsTab,
            settings.uiSettingsFlow.showHomeEverythingTab,
            tabHasNewItems("HomeFollows", feedStates.homeNewThreads.feedContent),
            tabHasNewItems("HomeFollowsReplies", feedStates.homeReplies.feedContent),
            tabHasNewItems("HomeFollowsEverything", feedStates.homeEverything.feedContent),
        ) { values ->
            val showThreads = values[0]
            val showReplies = values[1]
            val showEverything = values[2]
            val threadsHas = values[3]
            val repliesHas = values[4]
            val everythingHas = values[5]
            val anyEnabled = showThreads || showReplies || showEverything
            if (!anyEnabled) {
                // HomeScreen falls back to the New Threads tab when the user disables every tab.
                threadsHas
            } else {
                // Dot stays lit only when every enabled tab still has unread items.
                // Reaching the top of any single tab marks its newest item read and clears the dot.
                (!showThreads || threadsHas) &&
                    (!showReplies || repliesHas) &&
                    (!showEverything || everythingHas)
            }
        }

    val homeHasNewItemsFlow =
        homeHasNewItems
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(30000), false)

    val hasNewItems =
        mapOf(
            Route.Home to homeHasNewItemsFlow,
            Route.Message to messagesHasNewItemsFlow,
            Route.Notification() to notificationHasNewItemsFlow,
        )

    fun isWriteable(): Boolean = account.isWriteable()

    fun userProfile(): User = account.userProfile()

    fun reactToOrDelete(
        note: Note,
        reaction: String,
    ) {
        launchSigner {
            val currentReactions = note.allReactionsOfContentByAuthor(userProfile(), reaction)
            if (currentReactions.isNotEmpty()) {
                // Gift-wrapped reactions are retracted with a gift-wrapped
                // deletion to the same participants — a public NIP-09 would
                // e-tag the private rumor id onto public relays.
                val (privateRumors, publicReactions) = currentReactions.partition { it.isPrivateRumor() }
                if (publicReactions.isNotEmpty()) {
                    account.delete(publicReactions)
                }
                if (privateRumors.isNotEmpty()) {
                    account.deletePrivately(privateRumors, note)
                }
            } else {
                if (settings.useTrackedBroadcasts() && note.event !is NIP17Group && !note.isPrivateRumor()) {
                    // Tracked broadcasting with progress feedback
                    account.createReactionEvent(note, reaction)?.let { (event, relays) ->
                        broadcastTracker.trackBroadcast(
                            event = event,
                            relays = relays,
                            client = account.client,
                        )

                        account.consumeReactionEvent(event)
                    }
                } else {
                    // Fire-and-forget (original behavior)
                    account.reactTo(note, reaction)
                }
            }
        }
    }

    fun reactToOrDelete(note: Note) {
        val reaction = reactionChoices().first()
        reactToOrDelete(note, reaction)
    }

    @Immutable
    data class NoteComposeReportState(
        val isPostHidden: Boolean = false,
        val isAcceptable: Boolean = true,
        val canPreview: Boolean = true,
        val isHiddenAuthor: Boolean = false,
        val relevantReports: ImmutableSet<Note> = persistentSetOf(),
        val hasExcessiveHashtags: Boolean = false,
        val hashtagLimit: Int = 0,
    )

    fun isNoteAcceptable(
        note: Note,
        accountChoices: LiveHiddenUsers,
        followUsers: Set<HexKey>,
    ): NoteComposeReportState {
        checkNotInMainThread()

        val isFromLoggedIn = note.author?.pubkeyHex == userProfile().pubkeyHex
        val isFromLoggedInFollow = note.author?.let { followUsers.contains(it.pubkeyHex) } ?: true
        val isPostHidden = note.isHiddenFor(accountChoices)
        val isHiddenAuthor = note.author?.let { account.isHidden(it) } == true

        val noteEvent = note.event
        val isDecryptedPostHidden = if (noteEvent is PrivateDmEvent) account.isDecryptedContentHidden(noteEvent) else false

        return if (isPostHidden || isDecryptedPostHidden) {
            // Spam + Blocked Users + Hidden Words + Sensitive Content
            NoteComposeReportState(isPostHidden, isAcceptable = false, canPreview = false, isHiddenAuthor = isHiddenAuthor)
        } else if (isFromLoggedIn || isFromLoggedInFollow) {
            // No need to process if from trusted people
            NoteComposeReportState(isPostHidden, isAcceptable = true, canPreview = true, isHiddenAuthor = isHiddenAuthor)
        } else {
            val newCanPreview = !note.hasAnyReports()

            val newIsAcceptable = account.isAcceptable(note)

            if (newCanPreview && newIsAcceptable) {
                // No need to process reports if nothing is wrong
                NoteComposeReportState(isPostHidden, isAcceptable = true, canPreview = true, isHiddenAuthor = false)
            } else {
                val hashtagLimit = account.maxHashtagLimit()
                val hasExcessiveHashtags = account.hasExcessiveHashtags(note)
                NoteComposeReportState(
                    isPostHidden = isPostHidden,
                    isAcceptable = newIsAcceptable,
                    canPreview = newCanPreview,
                    isHiddenAuthor = false,
                    relevantReports = account.getRelevantReports(note).toImmutableSet(),
                    hasExcessiveHashtags = hasExcessiveHashtags,
                    hashtagLimit = hashtagLimit,
                )
            }
        }
    }

    private val noteIsHiddenFlows = LruCache<Note, StateFlow<NoteComposeReportState>>(300)

    fun createIsHiddenFlow(note: Note): StateFlow<NoteComposeReportState> =
        noteIsHiddenFlows.get(note)
            ?: combineTransform(
                account.hiddenUsers.flow,
                account.kind3FollowList.flow,
                note.flow().author(),
                note.flow().metadata.stateFlow,
                combine(
                    note.flow().reports.stateFlow,
                    account.settings.syncedSettings.security.warnAboutPostsWithReports,
                    account.settings.syncedSettings.security.reportWarningThreshold,
                ) { reports, _, _ -> reports },
            ) { hiddenUsers, followingUsers, _, metadata, _ ->
                emit(isNoteAcceptable(metadata.note, hiddenUsers, followingUsers.authors))
            }.onStart {
                emit(
                    isNoteAcceptable(
                        note,
                        account.hiddenUsers.flow.value,
                        account.kind3FollowList.flow.value.authors,
                    ),
                )
            }.flowOn(Dispatchers.IO)
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(10000, 10000),
                    NoteComposeReportState(),
                ).also {
                    noteIsHiddenFlows.put(note, it)
                }

    private val noteMustShowExpandButtonFlows = LruCache<Note, StateFlow<Boolean>>(300)

    fun createMustShowExpandButtonFlows(note: Note): StateFlow<Boolean> =
        noteMustShowExpandButtonFlows.get(note)
            ?: note
                .flow()
                .relays
                .stateFlow
                .map { it.note.relays.size > 3 }
                .flowOn(Dispatchers.IO)
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(10000, 10000),
                    note.relays.size > 3,
                ).also {
                    noteMustShowExpandButtonFlows.put(note, it)
                }

    suspend fun calculateIfNoteWasZappedByAccount(
        zappedNote: Note,
        afterTimeInSeconds: Long,
    ): Boolean =
        withContext(Dispatchers.IO) {
            account.calculateIfNoteWasZappedByAccount(zappedNote, afterTimeInSeconds)
        }

    suspend fun calculateZapAmount(zappedNote: Note): String {
        // The signed-in user's own outgoing onchain zaps that aren't
        // yet CONFIRMED still need to show in the counter — the user
        // knows what they sent, so make the counter reflect reality
        // immediately instead of waiting for chain confirmation.
        val ownPendingOnchain = zappedNote.extraOwnPendingOnchainSats(account.userProfile().pubkeyHex)
        return if (zappedNote.zapPayments.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                val nwc = account.calculateZappedAmount(zappedNote)
                showAmount(nwc + java.math.BigDecimal(ownPendingOnchain))
            }
        } else {
            showAmount(zappedNote.zapsAmount + java.math.BigDecimal(ownPendingOnchain))
        }
    }

    suspend fun calculateZapraiser(zappedNote: Note): ZapraiserStatus {
        val zapraiserAmount = zappedNote.event?.zapraiserAmount() ?: 0
        return if (zappedNote.zapPayments.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                val newZapAmount = account.calculateZappedAmount(zappedNote)
                var percentage = newZapAmount.div(zapraiserAmount.toBigDecimal()).toFloat()

                if (percentage > 1) {
                    percentage = 1f
                }

                val newZapraiserProgress = percentage
                val newZapraiserLeft =
                    if (percentage > 0.99) {
                        "0"
                    } else {
                        showAmount((zapraiserAmount * (1 - percentage)).toBigDecimal())
                    }

                ZapraiserStatus(newZapraiserProgress, newZapraiserLeft)
            }
        } else {
            var percentage = zappedNote.zapsAmount.div(zapraiserAmount.toBigDecimal()).toFloat()

            if (percentage > 1) {
                percentage = 1f
            }

            val newZapraiserProgress = percentage
            val newZapraiserLeft =
                if (percentage > 0.99) {
                    "0"
                } else {
                    showAmount((zapraiserAmount * (1 - percentage)).toBigDecimal())
                }

            ZapraiserStatus(newZapraiserProgress, newZapraiserLeft)
        }
    }

    class DecryptedInfo(
        val zapRequest: Note,
        val zapEvent: Note?,
        val info: ZapAmountCommentNotification,
    )

    fun decryptAmountMessageInGroup(
        zaps: ImmutableList<CombinedZap>,
        onNewState: (ImmutableList<ZapAmountCommentNotification>) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val initialResults =
                zaps
                    .associate {
                        it.request to
                            ZapAmountCommentNotification(
                                it.request.author,
                                it.request.event
                                    ?.content
                                    ?.ifBlank { null },
                                showAmountInteger((it.response.event as? LnZapEvent)?.amount),
                                it.response,
                            )
                    }.toMutableMap()

            val results =
                mapNotNullAsync(
                    zaps.filter { (it.request.event as? LnZapRequestEvent)?.isPrivateZap() == true },
                ) { next ->
                    val info = innerDecryptAmountMessage(next.request, next.response)
                    if (info != null) {
                        DecryptedInfo(next.request, next.response, info)
                    } else {
                        null
                    }
                }

            results.forEach { decrypted -> initialResults[decrypted.zapRequest] = decrypted.info.copy(zapNote = decrypted.zapEvent) }

            onNewState(initialResults.values.toImmutableList())
        }
    }

    fun cachedDecryptAmountMessageInGroup(zapNotes: List<CombinedZap>): ImmutableList<ZapAmountCommentNotification> =
        zapNotes
            .map {
                val request = it.request.event as? LnZapRequestEvent
                if (request?.isPrivateZap() == true) {
                    val cachedPrivateRequest = account.privateZapsDecryptionCache.cachedPrivateZap(request)
                    if (cachedPrivateRequest != null) {
                        ZapAmountCommentNotification(
                            LocalCache.getUserIfExists(cachedPrivateRequest.pubKey) ?: it.request.author,
                            cachedPrivateRequest.content.ifBlank { null },
                            showAmountInteger((it.response.event as? LnZapEvent)?.amount),
                            it.response,
                        )
                    } else {
                        ZapAmountCommentNotification(
                            it.request.author,
                            it.request.event
                                ?.content
                                ?.ifBlank { null },
                            showAmountInteger((it.response.event as? LnZapEvent)?.amount),
                            it.response,
                        )
                    }
                } else {
                    ZapAmountCommentNotification(
                        it.request.author,
                        it.request.event
                            ?.content
                            ?.ifBlank { null },
                        showAmountInteger((it.response.event as? LnZapEvent)?.amount),
                        it.response,
                    )
                }
            }.toImmutableList()

    fun cachedDecryptAmountMessageInGroup(baseNote: Note): ImmutableList<ZapAmountCommentNotification> {
        val myList = baseNote.zaps.toList()

        return myList
            .map {
                val request = it.first.event as? LnZapRequestEvent
                if (request?.isPrivateZap() == true) {
                    val cachedPrivateRequest = account.privateZapsDecryptionCache.cachedPrivateZap(request)
                    if (cachedPrivateRequest != null) {
                        ZapAmountCommentNotification(
                            LocalCache.getUserIfExists(cachedPrivateRequest.pubKey) ?: it.first.author,
                            cachedPrivateRequest.content.ifBlank { null },
                            showAmountInteger((it.second?.event as? LnZapEvent)?.amount),
                            it.second,
                        )
                    } else {
                        ZapAmountCommentNotification(
                            it.first.author,
                            it.first.event
                                ?.content
                                ?.ifBlank { null },
                            showAmountInteger((it.second?.event as? LnZapEvent)?.amount),
                            it.second,
                        )
                    }
                } else {
                    ZapAmountCommentNotification(
                        it.first.author,
                        it.first.event
                            ?.content
                            ?.ifBlank { null },
                        showAmountInteger((it.second?.event as? LnZapEvent)?.amount),
                        it.second,
                    )
                }
            }.toImmutableList()
    }

    fun decryptAmountMessageInGroup(
        baseNote: Note,
        onNewState: (ImmutableList<ZapAmountCommentNotification>) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val myList = baseNote.zaps.toList()

            val initialResults =
                myList
                    .associate {
                        it.first to
                            ZapAmountCommentNotification(
                                it.first.author,
                                it.first.event
                                    ?.content
                                    ?.ifBlank { null },
                                showAmountInteger((it.second?.event as? LnZapEvent)?.amount),
                                it.second,
                            )
                    }.toMutableMap()

            val decryptedInfo =
                mapNotNullAsync(myList) { next ->
                    val zap = next.second
                    if (zap != null) {
                        val info = innerDecryptAmountMessage(next.first, zap)
                        if (info != null) {
                            DecryptedInfo(next.first, zap, info)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }

            decryptedInfo.forEach { decrypted -> initialResults[decrypted.zapRequest] = decrypted.info.copy(zapNote = decrypted.zapEvent) }

            onNewState(initialResults.values.toImmutableList())
        }
    }

    fun decryptAmountMessage(
        zapRequest: Note,
        zapEvent: Note,
        onNewState: (ZapAmountCommentNotification?) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            onNewState(innerDecryptAmountMessage(zapRequest, zapEvent)?.copy(zapNote = zapEvent))
        }
    }

    suspend fun innerDecryptAmountMessage(zapNote: Note): ZapAmountCommentNotification? {
        val zapEvent = zapNote.event as? LnZapEvent ?: return null
        val zapRequest = zapEvent.zapRequest ?: return null

        return innerDecryptAmountMessage(zapRequest, zapEvent)?.copy(zapNote = zapNote)
    }

    suspend fun innerDecryptAmountMessage(
        zapRequest: Note,
        zapEvent: Note,
    ): ZapAmountCommentNotification? {
        val zapEvent = zapEvent.event as? LnZapEvent ?: return null
        val zapRequest = zapRequest.event as? LnZapRequestEvent ?: return null
        return innerDecryptAmountMessage(zapRequest, zapEvent)
    }

    suspend fun innerDecryptAmountMessage(
        zapRequestEvent: LnZapRequestEvent,
        zapEvent: LnZapEvent,
    ): ZapAmountCommentNotification? {
        val amount = showAmountInteger(zapEvent.amount)
        return if (zapRequestEvent.isPrivateZap()) {
            val decryptedContent = account.decryptZapOrNull(zapRequestEvent)
            if (decryptedContent != null) {
                ZapAmountCommentNotification(
                    LocalCache.checkGetOrCreateUser(decryptedContent.pubKey),
                    decryptedContent.content.ifBlank { null },
                    amount,
                )
            } else {
                ZapAmountCommentNotification(
                    account.cache.getOrCreateUser(zapRequestEvent.pubKey),
                    null,
                    amount,
                )
            }
        } else {
            ZapAmountCommentNotification(
                account.cache.getOrCreateUser(zapRequestEvent.pubKey),
                zapRequestEvent.content.ifBlank { null },
                amount,
            )
        }
    }

    fun zap(
        note: Note,
        amountInMillisats: Long,
        pollOption: Int?,
        message: String,
        context: Context,
        showErrorIfNoLnAddress: Boolean = true,
        onError: (String, String, User?) -> Unit,
        onProgress: (percent: Float) -> Unit,
        onPayViaIntent: (ImmutableList<ZapPaymentHandler.Payable>) -> Unit,
        zapType: LnZapEvent.ZapType? = null,
    ) = launchSigner {
        // A podcast note (episode or show) can carry a Podcasting-2.0 value-for-value block. When it
        // does, "zapping" it means paying that split — lnaddress recipients go out as real zaps (with
        // receipts, which drive this same button's icon/counter), node recipients go out as keysend.
        // This makes the standard zap button the single payment action for V4V content.
        val v4v =
            (note.event as? PodcastEpisode)?.episodeValue()
                ?: (note.event as? PodcastShow)?.showValue()
        if (v4v != null && v4v.recipients.any { it.split > 0 && !it.address.isNullOrBlank() }) {
            executeV4V(
                value = v4v,
                totalMilliSats = amountInMillisats,
                podcastName = (note.event as? PodcastShow)?.showTitle(),
                episodeName = (note.event as? PodcastEpisode)?.episodeTitle(),
                zappedNote = note,
                context = context,
                streaming = false,
                onProgress = onProgress,
            )
            return@launchSigner
        }

        val requestedType = zapType ?: defaultZapType()

        // Zaps on private rumors are forced to PRIVATE so the sender and
        // comment stay encrypted. NONZAP is kept: paying without a zap
        // request produces no receipt at all, which is even more private.
        val effectiveType =
            if (note.isPrivateRumor() && requestedType != LnZapEvent.ZapType.NONZAP) {
                LnZapEvent.ZapType.PRIVATE
            } else {
                requestedType
            }

        ZapPaymentHandler(account).zap(
            note = note,
            amountMilliSats = amountInMillisats,
            pollOption = pollOption,
            message = message,
            context = context,
            showErrorIfNoLnAddress = showErrorIfNoLnAddress,
            okHttpClient = httpClientBuilder::okHttpClientForMoney,
            onError = onError,
            onProgress = onProgress,
            onPayViaIntent = onPayViaIntent,
            zapType = effectiveType,
        )
    }

    /**
     * Executes a Podcasting-2.0 value-for-value split for [totalSats] sats: pays every recipient in
     * the show/episode's [PodcastValue] block their weighted share (lnaddress via LNURL-pay, node via
     * NWC keysend with the boostagram TLV).
     *
     * [streaming] marks this as a per-minute streaming payment rather than a one-off boost: the
     * boostagram action becomes "stream" and errors are swallowed instead of toasted — a streaming
     * session fires once a minute and we don't want per-minute toast spam. One-off boosts surface
     * errors on [toastManager]. The external-wallet intent fallback is skipped while [streaming]
     * (you can't auto-fire a wallet app every minute); streaming is gated to NWC/CLINK callers.
     */
    fun payV4V(
        value: PodcastValue,
        totalSats: Long,
        podcastName: String?,
        episodeName: String?,
        zappedNote: Note?,
        context: Context,
        streaming: Boolean = false,
        onProgress: (Float) -> Unit = {},
    ) = launchSigner {
        executeV4V(
            value = value,
            totalMilliSats = totalSats * 1000,
            podcastName = podcastName,
            episodeName = episodeName,
            zappedNote = zappedNote,
            context = context,
            streaming = streaming,
            onProgress = onProgress,
        )
    }

    /**
     * Shared V4V execution used by both [payV4V] and the V4V reroute inside [zap]. Must be called
     * from within a [launchSigner] block (it does signing). [streaming] = true marks per-minute
     * payments: errors are swallowed (no per-minute toast spam), the external-wallet intent fallback
     * is skipped (can't auto-launch a wallet every minute), and lnaddress shares are paid WITHOUT a
     * zap request so streaming doesn't publish a receipt every minute. One-off boosts ([streaming] =
     * false) pay lnaddress shares as real zaps, producing receipts that feed the zap button's UI.
     */
    private suspend fun executeV4V(
        value: PodcastValue,
        totalMilliSats: Long,
        podcastName: String?,
        episodeName: String?,
        zappedNote: Note?,
        context: Context,
        streaming: Boolean,
        onProgress: (Float) -> Unit,
    ) {
        val boostagram =
            PodcastBoostagram(
                podcast = podcastName,
                episode = episodeName,
                action = if (streaming) PodcastBoostagram.ACTION_STREAM else PodcastBoostagram.ACTION_BOOST,
                appName = "Amethyst",
                valueMsatTotal = totalMilliSats,
                senderName = account.userProfile().toBestDisplayName(),
            )

        V4VPaymentHandler(account).pay(
            value = value,
            totalMilliSats = totalMilliSats,
            boostagram = boostagram,
            zappedNote = zappedNote,
            context = context,
            asZap = !streaming,
            zapType = LnZapEvent.ZapType.PUBLIC,
            okHttpClient = httpClientBuilder::okHttpClientForMoney,
            onError = { title, message ->
                if (!streaming) toastManager.toast(title, message)
            },
            onProgress = onProgress,
            onPayInvoicesViaIntent = { invoices ->
                if (!streaming) {
                    invoices.forEach { invoice ->
                        payViaIntent(invoice, context, onPaid = {}, onError = {
                            toastManager.toast(stringRes(context, R.string.error_dialog_zap_error), it)
                        })
                    }
                }
            },
        )
    }

    /**
     * Fire-and-forget NIP-61 nutzap from the zap picker. Picks a mint the
     * recipient accepts (via their kind:10019) that we also have proofs at,
     * swaps proofs to P2PK-locked outputs, and publishes a kind:9321
     * referencing [note]. Errors are surfaced to [onError]; success is
     * indicated by the resulting kind:9321 landing in the cache.
     */
    fun sendNutzap(
        baseNote: Note,
        amountSats: Long,
        message: String,
        onError: (String, String, User?) -> Unit,
        onProgress: (Float) -> Unit = {},
    ) = launchSigner {
        // Nutzap events (kind 9321) are public and e-tag the zapped note —
        // on a private rumor that would leak the rumor id to public relays.
        if (baseNote.isPrivateRumor()) {
            onError(
                stringRes(com.vitorpamplona.amethyst.Amethyst.instance.appContext, R.string.nutzap_failed_title),
                stringRes(com.vitorpamplona.amethyst.Amethyst.instance.appContext, R.string.nutzap_failed_private_note),
                baseNote.author,
            )
            return@launchSigner
        }
        val recipient = baseNote.author?.pubkeyHex
        if (recipient == null) {
            onError(
                stringRes(com.vitorpamplona.amethyst.Amethyst.instance.appContext, R.string.nutzap_failed_title),
                stringRes(com.vitorpamplona.amethyst.Amethyst.instance.appContext, R.string.nutzap_failed_no_recipient),
                null,
            )
            return@launchSigner
        }
        val zappedEvent = baseNote.toEventHint<com.vitorpamplona.quartz.nip01Core.core.Event>()
        if (zappedEvent == null) {
            onError(
                stringRes(com.vitorpamplona.amethyst.Amethyst.instance.appContext, R.string.nutzap_failed_title),
                stringRes(com.vitorpamplona.amethyst.Amethyst.instance.appContext, R.string.nutzap_failed_no_event),
                baseNote.author,
            )
            return@launchSigner
        }
        try {
            account.cashuWalletState.sendNutzap(
                amountSats = amountSats,
                recipientPubKey = recipient,
                zappedEvent = zappedEvent,
                message = message,
                onProgress = onProgress,
            )
            // No success toast — the kind:9321 round-trips through the
            // cache, attaches to the target Note via addNutzap, and the
            // reaction row's zap counter + the icon-highlight state both
            // light up automatically. A toast on top would be redundant
            // noise.
        } catch (e: Exception) {
            onError(
                stringRes(com.vitorpamplona.amethyst.Amethyst.instance.appContext, R.string.nutzap_failed_title),
                describeMintError(e),
                baseNote.author,
            )
        }
    }

    /**
     * NIP-61 nutzap aimed at a profile rather than an event: the kind:9321
     * carries only the `p` tag. Used by the profile Send Payment screen, which
     * needs explicit success/error callbacks to drive its in-screen feedback.
     */
    fun sendNutzapToUser(
        recipientPubKey: HexKey,
        amountSats: Long,
        message: String,
        onError: (String, String, User?) -> Unit,
        onProgress: (Float) -> Unit = {},
        onSuccess: () -> Unit = {},
    ) = launchSigner {
        try {
            account.cashuWalletState.sendNutzap(
                amountSats = amountSats,
                recipientPubKey = recipientPubKey,
                zappedEvent = null,
                message = message,
                onProgress = onProgress,
            )
            onSuccess()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            onError(
                stringRes(com.vitorpamplona.amethyst.Amethyst.instance.appContext, R.string.nutzap_failed_title),
                describeMintError(e),
                getUserIfExists(recipientPubKey),
            )
        }
    }

    fun report(
        note: Note,
        type: ReportType,
        content: String = "",
    ) = launchSigner { account.report(note, type, content) }

    fun report(
        user: User,
        type: ReportType,
        content: String = "",
    ) {
        launchSigner {
            account.report(user, type, content)
            account.hideUser(user.pubkeyHex)
        }
    }

    fun boost(note: Note) =
        launchTrackedOrDirect(
            createTracked = { account.createBoostEvent(note) },
            consumeTracked = account::consumeBoostEvent,
            direct = { account.boost(note) },
        )

    fun removeEmojiPack(emojiPack: Note) = launchSigner { account.removeEmojiPack(emojiPack) }

    fun addEmojiPack(emojiPack: Note) = launchSigner { account.addEmojiPack(emojiPack) }

    fun addMediaToGallery(
        hex: String,
        url: String,
        relay: NormalizedRelayUrl?,
        blurhash: String?,
        dim: DimensionTag?,
        hash: String?,
        mimeType: String?,
        thumbhash: String? = null,
        image: String? = null,
    ) = launchSigner { account.addToGallery(hex, url, relay, blurhash, dim, hash, mimeType, thumbhash = thumbhash, image = image) }

    fun removeFromMediaGallery(note: Note) = launchSigner { account.removeFromGallery(note) }

    fun follows(user: User): Note = LocalCache.getOrCreateAddressableNote(ContactListEvent.createAddress(user.pubkeyHex))

    fun hashtagFollows(user: User): Note = LocalCache.getOrCreateAddressableNote(HashtagListEvent.createAddress(user.pubkeyHex))

    fun bookmarks(user: User): Note = LocalCache.getOrCreateAddressableNote(BookmarkListEvent.createBookmarkAddress(user.pubkeyHex))

    fun oldBookmarks(user: User): Note = LocalCache.getOrCreateAddressableNote(OldBookmarkListEvent.createBookmarkAddress(user.pubkeyHex))

    fun pinnedNotes(user: User): Note = LocalCache.getOrCreateAddressableNote(PinListEvent.createPinAddress(user.pubkeyHex))

    fun addPin(note: Note) =
        launchTrackedOrDirect(
            createTracked = { account.createAddPinEvent(note) },
            consumeTracked = account::consumePinEvent,
            direct = { account.addPin(note) },
        )

    fun removePin(note: Note) =
        launchTrackedOrDirect(
            createTracked = { account.createRemovePinEvent(note) },
            consumeTracked = account::consumePinEvent,
            direct = { account.removePin(note) },
        )

    fun removeDeletedPins(deletedNotes: Set<Note>) {
        launchSigner { account.removeDeletedPins(deletedNotes) }
    }

    fun addPrivateBookmark(note: Note) =
        launchTrackedOrDirect(
            createTracked = { account.createAddBookmarkEvent(note, true) },
            consumeTracked = account::consumeBookmarkEvent,
            direct = { account.addBookmark(note, true) },
        )

    fun addPublicBookmark(note: Note) =
        launchTrackedOrDirect(
            createTracked = { account.createAddBookmarkEvent(note, false) },
            consumeTracked = account::consumeBookmarkEvent,
            direct = { account.addBookmark(note, false) },
        )

    fun removePrivateBookmark(note: Note) =
        launchTrackedOrDirect(
            createTracked = { account.createRemoveBookmarkEvent(note, true) },
            consumeTracked = account::consumeBookmarkEvent,
            direct = { account.removeBookmark(note, true) },
        )

    fun removePublicBookmark(note: Note) =
        launchTrackedOrDirect(
            createTracked = { account.createRemoveBookmarkEvent(note, false) },
            consumeTracked = account::consumeBookmarkEvent,
            direct = { account.removeBookmark(note, false) },
        )

    /** Stars/unstars a git repository in the user's NIP-51 kind 10018 list. */
    fun toggleRepositoryBookmark(
        note: AddressableNote,
        isBookmarked: Boolean,
    ) = launchSigner {
        if (isBookmarked) {
            account.removeGitRepositoryBookmark(note)
        } else {
            account.addGitRepositoryBookmark(note)
        }
    }

    /** NIP-32: tags [note] with [hashtag] by publishing a kind 1985 label event. */
    fun labelWithHashtag(
        note: Note,
        hashtag: String,
    ) = launchTrackedOrDirect(
        createTracked = { account.createLabelHashtagEvent(note, hashtag) },
        consumeTracked = account::consumeLabelEvent,
        direct = { account.labelHashtag(note, hashtag) },
    )

    fun removeDeletedBookmarks(
        deletedEventIds: Set<String>,
        deletedAddresses: Set<Address>,
    ) {
        launchSigner { account.removeDeletedBookmarks(deletedEventIds, deletedAddresses) }
    }

    fun removeDeletedOldBookmarks(
        deletedEventIds: Set<String>,
        deletedAddresses: Set<Address>,
    ) {
        launchSigner { account.removeDeletedOldBookmarks(deletedEventIds, deletedAddresses) }
    }

    fun broadcast(note: Note) = launchSigner { account.broadcast(note) }

    /**
     * Broadcast republishes public events directly and rumors as their
     * delivering kind-1059 wrap. A rumor whose wrap is unknown can't be
     * broadcast at all — publishing the unsigned event would disclose the
     * private content.
     */
    fun canBroadcast(note: Note): Boolean {
        val event = note.event ?: return false
        return event.sig.isNotEmpty() || note.rumorHost != null
    }

    fun timestamp(note: Note) = launchSigner { account.otsState.timestamp(note) }

    fun delete(notes: List<Note>) = launchSigner { account.delete(notes) }

    fun delete(note: Note) = launchSigner { account.delete(note) }

    fun requestToVanish(
        relays: List<NormalizedRelayUrl>,
        reason: String,
        createdAt: Long,
    ) = launchSigner { account.requestToVanish(relays, reason, createdAt) }

    fun requestToVanishFromEverywhere(
        reason: String,
        createdAt: Long,
    ) = launchSigner { account.requestToVanishFromEverywhere(reason, createdAt) }

    fun cachedDecrypt(note: Note): String? = account.cachedDecryptContent(note)

    fun decrypt(
        note: Note,
        onReady: (String) -> Unit,
    ) = launchSigner {
        account.decryptContent(note)?.let { onReady(it) }
    }

    /**
     * Runs an action that has both a tracked and a direct broadcast variant,
     * picking the path the user selected via the "Tracked broadcasts" setting.
     */
    inline fun launchTrackedOrDirect(
        crossinline createTracked: suspend () -> Pair<Event, Set<NormalizedRelayUrl>>?,
        crossinline consumeTracked: (Event) -> Unit,
        crossinline direct: suspend () -> Unit,
    ) = launchSigner {
        if (settings.useTrackedBroadcasts()) {
            createTracked()?.let { (event, relays) ->
                broadcastTracker.trackBroadcast(
                    event = event,
                    relays = relays,
                    client = account.client,
                )
                consumeTracked(event)
            }
        } else {
            direct()
        }
    }

    inline fun launchSigner(crossinline action: suspend () -> Unit) =
        viewModelScope.launch(Dispatchers.IO) {
            try {
                action()
            } catch (_: SignerExceptions.ReadOnlyException) {
                toastManager.toast(
                    R.string.read_only_user,
                    R.string.login_with_a_private_key_to_be_able_to_sign_events,
                )
            } catch (_: SignerExceptions.UnauthorizedDecryptionException) {
                toastManager.toast(
                    R.string.unauthorized_exception,
                    R.string.unauthorized_exception_description,
                )
            } catch (_: SignerExceptions.SignerNotFoundException) {
                toastManager.toast(
                    R.string.signer_not_found_exception,
                    R.string.signer_not_found_exception_description,
                )
            } catch (e: SignerExceptions.TimedOutException) {
                Log.w("AccountViewModel", "TimedOutException", e)
            } catch (e: SignerExceptions.NothingToDecrypt) {
                Log.w("AccountViewModel", "NothingToDecrypt", e)
            } catch (e: SignerExceptions.CouldNotPerformException) {
                Log.w("AccountViewModel", "CouldNotPerformException", e)
            } catch (e: SignerExceptions.ManuallyUnauthorizedException) {
                Log.w("AccountViewModel", "ManuallyUnauthorizedException", e)
            } catch (e: SignerExceptions.AutomaticallyUnauthorizedException) {
                Log.w("AccountViewModel", "AutomaticallyUnauthorizedException", e)
            } catch (e: SignerExceptions.RunningOnBackgroundWithoutAutomaticPermissionException) {
                Log.w("AccountViewModel", "TimedOutRunningOnBackgroundWithoutAutomaticPermissionExceptionException", e)
            } catch (e: IllegalStateException) {
                toastManager.toast(
                    R.string.signer_not_found_exception,
                    R.string.signer_illegal_state_exception_description,
                    e,
                )
            }
        }

    fun approveCommunityPost(
        post: Note,
        community: AddressableNote,
    ) = launchSigner { account.approveCommunityPost(post, community) }

    fun follow(community: AddressableNote) = launchSigner { account.follow(community) }

    fun follow(channel: PublicChatChannel) = launchSigner { account.follow(channel) }

    fun follow(channel: EphemeralChatChannel) = launchSigner { account.follow(channel) }

    fun unfollow(community: AddressableNote) = launchSigner { account.unfollow(community) }

    fun unfollow(channel: PublicChatChannel) = launchSigner { account.unfollow(channel) }

    fun unfollow(channel: EphemeralChatChannel) = launchSigner { account.unfollow(channel) }

    fun joinRelayGroup(
        channel: RelayGroupChannel,
        code: String? = null,
    ) = launchSigner { account.joinRelayGroup(channel, code) }

    fun leaveRelayGroup(channel: RelayGroupChannel) = launchSigner { account.leaveRelayGroup(channel) }

    fun createRelayGroup(
        relay: NormalizedRelayUrl,
        groupId: String,
        name: String,
        about: String?,
        picture: String?,
        isPrivate: Boolean,
        isClosed: Boolean,
        isHidden: Boolean,
        isRestricted: Boolean,
    ) = launchSigner {
        account.createRelayGroup(relay, groupId, name, about, picture, isPrivate, isClosed, isHidden, isRestricted)
    }

    fun createRelayGroupInvite(
        channel: RelayGroupChannel,
        code: String,
    ) = launchSigner { account.createRelayGroupInvite(channel, code) }

    fun postRelayGroupThread(
        channel: RelayGroupChannel,
        title: String,
        body: String,
    ) = launchSigner { account.postRelayGroupThread(channel, title, body) }

    fun removeRelayGroupUser(
        channel: RelayGroupChannel,
        pubkey: HexKey,
    ) = launchSigner { account.removeRelayGroupUser(channel, pubkey) }

    fun putRelayGroupUser(
        channel: RelayGroupChannel,
        pubkey: HexKey,
        roles: List<String>,
    ) = launchSigner { account.putRelayGroupUser(channel, pubkey, roles) }

    fun editRelayGroupMetadata(
        channel: RelayGroupChannel,
        name: String?,
        about: String?,
        picture: String?,
        isPrivate: Boolean,
        isClosed: Boolean,
        isHidden: Boolean,
        isRestricted: Boolean,
    ) = launchSigner {
        account.editRelayGroupMetadata(channel, name, about, picture, isPrivate, isClosed, isHidden, isRestricted)
    }

    fun follow(users: List<User>) = launchSigner { account.follow(users) }

    fun follow(user: User) = launchSigner { account.follow(user) }

    fun unfollow(user: User) = launchSigner { account.unfollow(user) }

    fun followGeohash(tag: String) = launchSigner { account.followGeohash(tag) }

    fun unfollowGeohash(tag: String) = launchSigner { account.unfollowGeohash(tag) }

    fun followHashtag(tag: String) = launchSigner { account.followHashtag(tag) }

    fun unfollowHashtag(tag: String) = launchSigner { account.unfollowHashtag(tag) }

    fun followFavoriteAlgoFeed(dvm: AddressBookmark) = launchSigner { account.followFavoriteAlgoFeed(dvm) }

    fun unfollowFavoriteAlgoFeed(dvm: Address) = launchSigner { account.unfollowFavoriteAlgoFeed(dvm) }

    fun refreshFavoriteAlgoFeed(dvm: Address) = account.favoriteAlgoFeedsOrchestrator.refresh(dvm)

    fun followRelayFeed(url: NormalizedRelayUrl) = launchSigner { account.followRelayFeed(url) }

    fun unfollowRelayFeed(url: NormalizedRelayUrl) = launchSigner { account.unfollowRelayFeed(url) }

    fun showWord(word: String) = launchSigner { account.showWord(word) }

    fun hideWord(word: String) = launchSigner { account.hideWord(word) }

    fun isLoggedUser(pubkeyHex: HexKey?): Boolean = account.signer.pubKey == pubkeyHex

    fun isLoggedUser(user: User?): Boolean = isLoggedUser(user?.pubkeyHex)

    fun isFollowing(user: User?): Boolean {
        if (user == null) return false
        return account.isFollowing(user)
    }

    fun isFollowing(user: HexKey): Boolean = account.isFollowing(user)

    fun markDonatedInThisVersion() = account.markDonatedInThisVersion()

    fun dismissPollNotification(noteId: String) = account.dismissPollNotification(noteId)

    fun hasViewedPollResults(noteId: String) = account.hasViewedPollResults(noteId)

    fun markPollResultsViewed(
        noteId: String,
        pollEndsAt: Long?,
    ) = account.markPollResultsViewed(noteId, pollEndsAt)

    fun dontTranslateFrom() = account.settings.syncedSettings.languages.dontTranslateFrom.value

    fun translateTo() = account.settings.syncedSettings.languages.translateTo.value

    fun defaultZapType() = account.settings.syncedSettings.zaps.defaultZapType.value

    fun showSensitiveContent(): MutableStateFlow<Boolean?> = account.settings.syncedSettings.security.showSensitiveContent

    fun zapAmountChoicesFlow() = account.settings.syncedSettings.zaps.zapAmountChoices

    fun zapAmountChoices() = zapAmountChoicesFlow().value

    fun reactionChoicesFlow() = account.settings.syncedSettings.reactions.reactionChoices

    fun reactionChoices() = reactionChoicesFlow().value

    fun filterSpamFromStrangers() = account.settings.syncedSettings.security.filterSpamFromStrangers

    fun toggleSendKind0ToLocalRelay(enabled: Boolean) = launchSigner { account.updateSendKind0EventsToLocalRelay(enabled) }

    fun updateWarnReports(warnReports: Boolean) = launchSigner { account.updateWarnReports(warnReports) }

    fun updateReportWarningThreshold(threshold: Int) = launchSigner { account.updateReportWarningThreshold(threshold) }

    fun updateAddClientTag(add: Boolean) = launchSigner { account.updateAddClientTag(add) }

    fun updateFilterSpam(filterSpam: Boolean) =
        launchSigner {
            if (account.updateFilterSpam(filterSpam)) {
                LocalCache.antiSpam.active = filterSpamFromStrangers().value
            }
        }

    fun updateShowSensitiveContent(show: Boolean?) = launchSigner { account.updateShowSensitiveContent(show) }

    fun updateMaxHashtagLimit(limit: Int) = launchSigner { account.updateMaxHashtagLimit(limit) }

    fun changeReactionTypes(
        reactionSet: List<String>,
        onDone: () -> Unit,
    ) = launchSigner {
        account.changeReactionTypes(reactionSet)
        onDone()
    }

    fun reactionRowItemsFlow() = account.settings.syncedSettings.reactions.reactionRowItems

    fun changeReactionRowItems(items: List<com.vitorpamplona.amethyst.model.ReactionRowItem>) =
        launchSigner {
            account.changeReactionRowItems(items)
        }

    fun videoPlayerButtonItemsFlow() = account.settings.syncedSettings.videoPlayer.buttonItems

    fun changeVideoPlayerButtonItems(items: List<com.vitorpamplona.amethyst.model.VideoPlayerButtonItem>) =
        launchSigner {
            account.changeVideoPlayerButtonItems(items)
        }

    fun audioVisualizerFlow(): StateFlow<VisualizerStyle> = account.settings.syncedSettings.media.audioVisualizer

    fun changeAudioVisualizer(style: VisualizerStyle) =
        launchSigner {
            account.changeAudioVisualizer(style)
        }

    fun pinnedChatroomsFlow(): StateFlow<Set<ChatroomKey>> = account.settings.syncedSettings.chats.pinnedChatrooms

    fun toggleChatroomPin(room: ChatroomKey) =
        launchSigner {
            account.toggleChatroomPin(room)
        }

    fun updateZapAmounts(
        amountSet: List<Long>,
        selectedZapType: LnZapEvent.ZapType,
        nip47Update: Nip47WalletConnect.Nip47URINorm?,
    ) = launchSigner { account.updateZapAmounts(amountSet, selectedZapType, nip47Update) }

    fun toggleDontTranslateFrom(languageCode: String) = launchSigner { account.toggleDontTranslateFrom(languageCode) }

    fun addDontTranslateFrom(languageCode: String) = launchSigner { account.addDontTranslateFrom(languageCode) }

    fun removeDontTranslateFrom(languageCode: String) = launchSigner { account.removeDontTranslateFrom(languageCode) }

    fun updateTranslateTo(languageCode: String) = launchSigner { account.updateTranslateTo(languageCode) }

    fun prefer(
        source: String,
        target: String,
        preference: String,
    ) = launchSigner { account.prefer(source, target, preference) }

    fun show(user: User) = launchSigner { account.showUser(user.pubkeyHex) }

    fun hide(user: User) = launchSigner { account.hideUser(user.pubkeyHex) }

    fun hide(word: String) = launchSigner { account.hideWord(word) }

    fun showUser(pubkeyHex: String) = launchSigner { account.showUser(pubkeyHex) }

    fun showUsers(pubkeys: List<HexKey>) = launchSigner { account.showUsers(pubkeys) }

    fun showWords(words: List<String>) = launchSigner { account.showWords(words) }

    fun muteThread(note: Note) {
        launchSigner {
            account.muteThread(account.resolveThreadRoot(note))
        }
    }

    fun unmuteThread(note: Note) {
        launchSigner {
            account.unmuteThread(account.resolveThreadRoot(note))
        }
    }

    fun unmuteThread(rootHex: HexKey) {
        launchSigner {
            account.unmuteThread(rootHex)
        }
    }

    fun isThreadMutedFor(note: Note): Boolean = account.isThreadMuted(account.resolveThreadRoot(note))

    fun createStatus(newStatus: String) = launchSigner { account.createStatus(newStatus) }

    fun updateStatus(
        address: Address,
        newStatus: String,
    ) = launchSigner {
        account.updateStatus(LocalCache.getOrCreateAddressableNote(address), newStatus)
    }

    fun deleteStatus(address: Address) =
        launchSigner {
            account.deleteStatus(LocalCache.getOrCreateAddressableNote(address))
        }

    fun urlPreview(
        url: String,
        onResult: suspend (UrlPreviewState) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            UrlCachedPreviewer.previewInfo(url, httpClientBuilder::okHttpClientForPreview, onResult)
        }
    }

    fun loadReactionTo(note: Note?): String? {
        if (note == null) return null

        return note.getReactionBy(userProfile())
    }

    fun runOnIO(runOnIO: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { runOnIO() }
    }

    fun checkGetOrCreateUser(key: HexKey): User? = LocalCache.checkGetOrCreateUser(key)

    override suspend fun getOrCreateUser(hex: HexKey): User = LocalCache.getOrCreateUser(hex)

    fun getUserIfExists(hex: HexKey): User? = LocalCache.getUserIfExists(hex)

    fun checkGetOrCreateNote(key: HexKey): Note? = LocalCache.checkGetOrCreateNote(key)

    override suspend fun getOrCreateNote(hex: HexKey): Note = LocalCache.getOrCreateNote(hex)

    fun noteFromEvent(event: Event): Note? {
        var note = checkGetOrCreateNote(event.id)

        if (note == null) {
            LocalCache.justConsume(event, null, false)
            note = checkGetOrCreateNote(event.id)
        }

        return note
    }

    fun getNoteIfExists(hex: HexKey): Note? = LocalCache.getNoteIfExists(hex)

    /**
     * Fixes author and relay hints in MarkedETag list by looking up notes from cache.
     * This ensures reply tags have proper author pubkeys and relay hints for threading.
     */
    fun fixReplyTagHints(tags: List<MarkedETag>) {
        tags.forEach { tag ->
            val note = getNoteIfExists(tag.eventId)
            val cachedAuthor = note?.author?.pubkeyHex
            val cachedRelay = note?.relayHintUrl()

            // Fix author if missing or different from cached
            if (tag.author.isNullOrBlank() && cachedAuthor != null) {
                tag.author = cachedAuthor
            } else if (cachedAuthor != null && tag.author != cachedAuthor) {
                tag.author = cachedAuthor
            }

            // Fix relay hint if missing or different from cached
            if (tag.relay == null && cachedRelay != null) {
                tag.relay = cachedRelay
            } else if (cachedRelay != null && tag.relay != cachedRelay) {
                tag.relay = cachedRelay
            }
        }
    }

    override fun getOrCreateAddressableNote(address: Address): AddressableNote = LocalCache.getOrCreateAddressableNote(address)

    fun getAddressableNoteIfExists(key: String): AddressableNote? = LocalCache.getAddressableNoteIfExists(key)

    fun getAddressableNoteIfExists(key: Address): AddressableNote? = LocalCache.getAddressableNoteIfExists(key)

    fun cachedModificationEventsForNote(note: Note) = LocalCache.cachedModificationEventsForNote(note)

    fun checkGetOrCreatePublicChatChannel(key: HexKey): PublicChatChannel = LocalCache.getOrCreatePublicChatChannel(key)

    fun checkGetOrCreateLiveActivityChannel(key: Address): LiveActivitiesChannel = LocalCache.getOrCreateLiveChannel(key)

    fun checkGetOrCreateEphemeralChatChannel(key: RoomId): EphemeralChatChannel = LocalCache.getOrCreateEphemeralChannel(key)

    fun getPublicChatChannelIfExists(hex: HexKey) = LocalCache.getPublicChatChannelIfExists(hex)

    fun getEphemeralChatChannelIfExists(key: RoomId) = LocalCache.getEphemeralChatChannelIfExists(key)

    fun checkGetOrCreateRelayGroupChannel(key: GroupId): RelayGroupChannel = LocalCache.getOrCreateRelayGroupChannel(key)

    fun getRelayGroupChannelIfExists(key: GroupId) = LocalCache.getRelayGroupChannelIfExists(key)

    fun getRelayGroupChannelsOnRelay(relay: NormalizedRelayUrl) = LocalCache.getRelayGroupChannelsOnRelay(relay)

    fun getLiveActivityChannelIfExists(key: Address) = LocalCache.getLiveActivityChannelIfExists(key)

    fun <T : PubKeyReferenceTag> loadParticipants(
        participants: List<T>,
        onReady: (ImmutableList<Pair<T, User>>) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val participantUsers =
                participants
                    .mapNotNull { part ->
                        checkGetOrCreateUser(part.pubKey)?.let {
                            Pair(
                                part,
                                it,
                            )
                        }
                    }.toImmutableList()

            onReady(participantUsers)
        }
    }

    fun loadUsers(
        hexList: List<String>,
        onReady: (ImmutableList<User>) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            onReady(loadUsersSync(hexList).toImmutableList())
        }
    }

    fun sortUsersSync(hexList: List<HexKey>): List<HexKey> = hexList.sortedByDescending { account.isKnown(it) }

    fun loadUsersSync(hexList: List<String>): List<User> =
        hexList
            .mapNotNull { hex -> checkGetOrCreateUser(hex) }
            .sortedByDescending { account.isKnown(it) }

    suspend fun checkVideoIsOnline(videoUrl: String): Boolean =
        withContext(Dispatchers.IO) {
            OnlineChecker.isOnline(videoUrl, httpClientBuilder::okHttpClientForVideo)
        }

    fun loadAndMarkAsRead(
        routeForLastRead: String,
        createdAt: Long?,
        dismissNotificationId: HexKey? = null,
    ): Boolean {
        if (createdAt == null) return false

        val lastTime = account.loadLastRead(routeForLastRead)

        val onIsNew = createdAt > lastTime

        if (onIsNew) {
            viewModelScope.launch(Dispatchers.IO) {
                account.markAsRead(routeForLastRead, createdAt)
                // The user is now looking at this event in-app, so clear any tray
                // notification that was posted for it while the app was backgrounded.
                dismissNotificationId?.let { dismissTrayNotificationFor(it) }
            }
        }

        return onIsNew
    }

    private fun dismissTrayNotificationFor(eventId: HexKey) {
        ContextCompat
            .getSystemService(Amethyst.instance.appContext, NotificationManager::class.java)
            ?.dismissNotificationForEvent(eventId)
    }

    fun markAllChatNotesAsRead(notes: List<Note>) {
        viewModelScope.launch(Dispatchers.IO) {
            for (note in notes) {
                val noteEvent = note.event
                when {
                    noteEvent is IsInPublicChatChannel -> {
                        account.markAsRead("Channel/${noteEvent.channelId()}", noteEvent.createdAt)
                    }

                    noteEvent is ChatroomKeyable -> {
                        account.markAsRead("Room/${noteEvent.chatroomKey(account.signer.pubKey).hashCode()}", noteEvent.createdAt)
                    }

                    noteEvent is DraftWrapEvent -> {
                        val innerEvent = account.draftsDecryptionCache.preCachedDraft(noteEvent)
                        if (innerEvent is IsInPublicChatChannel) {
                            account.markAsRead("Channel/${innerEvent.channelId()}", noteEvent.createdAt)
                        } else if (innerEvent is ChatroomKeyable) {
                            account.markAsRead("Room/${innerEvent.chatroomKey(account.signer.pubKey).hashCode()}", noteEvent.createdAt)
                        }
                    }
                }
            }

            markHiddenChatroomsAsRead()
        }
    }

    private fun unreadPrivateChatRoute(chat: Note): Pair<String, Long>? {
        val noteEvent = chat.event ?: return null
        val room = (noteEvent as? ChatroomKeyable)?.chatroomKey(account.signer.pubKey) ?: return null
        if (account.isAllHidden(room.users)) return null
        return privateChatRoute(room) to noteEvent.createdAt
    }

    private fun markHiddenChatroomsAsRead() {
        account.chatroomList.rooms.forEach { roomKey, chatroom ->
            if (account.isAllHidden(roomKey.users)) {
                chatroom.newestMessage?.createdAt()?.let {
                    account.markAsRead(privateChatRoute(roomKey), it)
                }
            }
        }
    }

    private fun privateChatRoute(room: ChatroomKey) = "Room/${room.hashCode()}"

    class Factory(
        val account: Account,
        val settings: UiSettingsState,
        val torSettings: TorSettingsFlow,
        val dataSources: RelaySubscriptionsCoordinator,
        val okHttpClient: RoleBasedHttpClientBuilder,
        val nip05ClientBuilder: () -> Nip05Client,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AccountViewModel(
                account,
                settings,
                torSettings,
                dataSources,
                okHttpClient,
                nip05ClientBuilder,
            ) as T
    }

    init {
        Log.d("AccountViewModel", "Init")
        viewModelScope.launch(Dispatchers.IO) {
            feedStates.init()
            // awaits for init to finish before starting to capture new events.
            LocalCache.live.newEventBundles.collect { newNotes ->
                logTime("AccountViewModel newEventBundle Update with ${newNotes.size} new notes") {
                    feedStates.updateFeedsWith(newNotes)
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            LocalCache.live.deletedEventBundles.collect { newNotes ->
                logTime("AccountViewModel deletedEventBundle Update with ${newNotes.size} new notes") {
                    feedStates.deleteNotes(newNotes)
                }
            }
        }
    }

    // --- Marmot Group Messaging ---

    suspend fun sendMarmotGroupMessage(
        nostrGroupId: String,
        text: String,
        replyToInnerEventId: HexKey? = null,
        replyToInnerAuthorPubKey: HexKey? = null,
    ) {
        // Rewrites @npub…/@nprofile… mentions into nostr: URIs and collects
        // the referenced users as p-tags. Lives here (not in the composer) so
        // every send path gets mention handling.
        val tagger = NewMessageTagger(text, null, null, this)
        tagger.run()
        // Inner event construction lives on MarmotManager so CLI and UI don't drift.
        // persistOwn=false because Account.sendMarmotGroupMessage routes the outer
        // event through LocalCache which already handles own-message display.
        val bundle =
            account.marmotManager
                ?.buildTextMessage(
                    nostrGroupId = nostrGroupId,
                    text = tagger.message,
                    replyToEventId = replyToInnerEventId,
                    replyToAuthorPubKey = replyToInnerAuthorPubKey,
                    persistOwn = false,
                    mentions = tagger.pTags?.map { it.toPTag() } ?: emptyList(),
                )
                ?: return
        val relays = account.marmotGroupRelays(nostrGroupId)
        account.sendMarmotGroupMessage(nostrGroupId, bundle.innerEvent, relays)
    }

    suspend fun sendMarmotGroupMediaMessage(
        nostrGroupId: String,
        url: String,
        imeta: com.vitorpamplona.quartz.nip92IMeta.IMetaTag,
    ) {
        val template =
            eventTemplate(
                kind = 9,
                description = url,
            ) {
                imeta(imeta)
            }
        // MIP-03: inner events MUST remain unsigned (no `sig`) so a leaked
        // plaintext can't be replayed as a valid public kind:9. Authorship
        // is authenticated by the MLS sender's LeafNode + the pubkey↔
        // credential-identity equality check on the receive side.
        val innerEvent =
            com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
                .assembleRumor<com.vitorpamplona.quartz.nip01Core.core.Event>(
                    account.signer.pubKey,
                    template,
                )
        val relays = account.marmotGroupRelays(nostrGroupId)
        account.sendMarmotGroupMessage(nostrGroupId, innerEvent, relays)
    }

    fun marmotMediaExporterSecret(nostrGroupId: String): ByteArray? = account.marmotManager?.mediaExporterSecret(nostrGroupId)

    suspend fun createMarmotGroup(nostrGroupId: String) {
        account.createMarmotGroup(nostrGroupId)
    }

    suspend fun publishMarmotKeyPackage() {
        account.publishMarmotKeyPackage()
    }

    suspend fun hasPublishedKeyPackage(): Boolean = account.hasPublishedKeyPackage()

    /**
     * Whether this account has a kind:10051 KeyPackage Relay List (MIP-00)
     * advertising where it publishes KeyPackages.
     */
    fun hasKeyPackageRelayList(): Boolean =
        account.keyPackageRelayList.flow.value
            .isNotEmpty()

    /**
     * Publishes a kind:10051 KeyPackage Relay List seeded from the account's
     * current outbox relays. Used when the user opts in from the
     * "Create Group" warning dialog.
     */
    suspend fun saveKeyPackageRelayListFromOutbox() {
        val outbox =
            account.outboxRelays.flow.value
                .toList()
        if (outbox.isEmpty()) return
        account.saveKeyPackageRelayList(outbox)
    }

    suspend fun leaveMarmotGroup(nostrGroupId: String) {
        val relays = account.marmotGroupRelays(nostrGroupId)
        account.leaveMarmotGroup(nostrGroupId, relays)
    }

    suspend fun resetMarmotState() {
        account.resetMarmotState()
    }

    fun marmotGroupMembers(nostrGroupId: String): List<com.vitorpamplona.amethyst.commons.marmot.GroupMemberInfo> = account.marmotManager?.memberPubkeys(nostrGroupId) ?: emptyList()

    suspend fun addMarmotGroupMember(
        nostrGroupId: String,
        memberPubKey: String,
    ): String = account.fetchKeyPackageAndAddMember(nostrGroupId, memberPubKey)

    suspend fun removeMarmotGroupMember(
        nostrGroupId: String,
        targetLeafIndex: Int,
    ) {
        val relays = account.marmotGroupRelays(nostrGroupId)
        account.removeMarmotGroupMember(nostrGroupId, targetLeafIndex, relays)
    }

    suspend fun grantMarmotGroupAdmin(
        nostrGroupId: String,
        targetPubKey: String,
    ) {
        val relays = account.marmotGroupRelays(nostrGroupId)
        account.grantMarmotGroupAdmin(nostrGroupId, targetPubKey, relays)
    }

    suspend fun revokeMarmotGroupAdmin(
        nostrGroupId: String,
        targetPubKey: String,
    ) {
        val relays = account.marmotGroupRelays(nostrGroupId)
        account.revokeMarmotGroupAdmin(nostrGroupId, targetPubKey, relays)
    }

    suspend fun updateMarmotGroupMetadata(
        nostrGroupId: String,
        name: String,
        description: String,
    ) {
        // Stamp the inviter's outbox relays into the group metadata so that
        // every member ends up with a single canonical relay set for kind:445
        // GroupEvents. Without this, both the inviter and the invitee fall
        // back to their *own* home/outbox relays — which usually do not
        // overlap, so kind:445 messages never reach the other side. The
        // welcome carries the metadata, so the invitee learns the relays at
        // join time.
        val outboxRelayStrings =
            account.outboxRelays.flow.value
                .map { it.url }
        val currentMetadata = account.marmotManager?.groupMetadata(nostrGroupId)
        val updatedMetadata =
            currentMetadata
                ?.copy(name = name, description = description)
                ?.withMergedRelays(outboxRelayStrings)
                ?: com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
                    .bootstrap(
                        nostrGroupId = nostrGroupId,
                        creatorPubKey = account.signer.pubKey,
                        outboxRelays = outboxRelayStrings,
                        name = name,
                        description = description,
                    )
        val relays = account.marmotGroupRelays(nostrGroupId)
        account.updateMarmotGroupMetadata(nostrGroupId, updatedMetadata, relays)
    }

    override fun onCleared() {
        Log.d("AccountViewModel", "onCleared")
        callManager.dispose()
        com.vitorpamplona.amethyst.service.call.CallSessionBridge
            .clear()
        com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.activity.NestBridge
            .clear()
        feedStates.destroy()
        super.onCleared()
    }

    fun loadMentions(
        mentions: ImmutableList<String>,
        onReady: (ImmutableList<User>) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val newSortedMentions =
                mentions
                    .mapNotNull { LocalCache.checkGetOrCreateUser(it) }
                    .toSet()
                    .sortedBy { account.isFollowing(it) }
                    .toImmutableList()

            onReady(newSortedMentions)
        }
    }

    fun tryBoost(
        baseNote: Note,
        onMore: () -> Unit,
    ) {
        if (baseNote.isDraft()) {
            toastManager.toast(
                R.string.draft_note,
                R.string.it_s_not_possible_to_quote_to_a_draft_note,
            )
            return
        }

        if (isWriteable()) {
            val boosts = baseNote.boostedBy(userProfile())
            if (boosts.isNotEmpty()) {
                launchSigner {
                    account.delete(boosts)
                }
            } else {
                onMore()
            }
        } else {
            toastManager.toast(
                R.string.read_only_user,
                R.string.login_with_a_private_key_to_be_able_to_boost_posts,
            )
        }
    }

    fun meltCashu(
        token: CashuToken,
        context: Context,
        onDone: (String, String) -> Unit,
    ) {
        val lud16 =
            account
                .userProfile()
                .metadataOrNull()
                ?.flow
                ?.value
                ?.info
                ?.lud16
        if (lud16 != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val meltResult = MeltProcessor().melt(token, lud16, httpClientBuilder::okHttpClientForMoney, context)
                    onDone(
                        stringRes(context, R.string.cashu_successful_redemption),
                        stringRes(
                            context,
                            R.string.cashu_successful_redemption_explainer,
                            token.totalAmount.toString(),
                            meltResult.fees.toString(),
                        ),
                    )
                } catch (e: LightningAddressResolver.LightningAddressError) {
                    onDone(e.title, e.msg)
                } catch (e: Exception) {
                    if (e is kotlin.coroutines.cancellation.CancellationException) throw e
                    onDone(
                        stringRes(context, R.string.cashu_failed_redemption),
                        stringRes(context, R.string.cashu_failed_redemption_explainer_error_msg, e.message),
                    )
                }
            }
        } else {
            onDone(
                stringRes(context, R.string.no_lightning_address_set),
                stringRes(
                    context,
                    R.string.user_x_does_not_have_a_lightning_address_setup_to_receive_sats,
                    account.userProfile().toBestDisplayName(),
                ),
            )
        }
    }

    private suspend fun unwrapGiftWrap(event: GiftWrapEvent): Note? {
        val cacheInnerEventId = event.innerEventId
        return if (cacheInnerEventId != null) {
            val existingNoteEvent = LocalCache.getNoteIfExists(cacheInnerEventId)?.event
            if (existingNoteEvent != null) {
                unwrapIfNeeded(existingNoteEvent)
            } else {
                val newEvent = event.unwrapOrNull(account.signer) ?: return null

                // clear the encrypted payload to save memory
                LocalCache.getOrCreateNote(event.id).event = event.copyNoContent()

                LocalCache.justConsume(newEvent, null, false)

                unwrapIfNeeded(newEvent)
            }
        } else {
            val newEvent = event.unwrapThrowing(account.signer)
            // clear the encrypted payload to save memory
            LocalCache.getOrCreateNote(event.id).event = event.copyNoContent()

            val existingNoteEvent = LocalCache.getNoteIfExists(newEvent.id)?.event

            if (existingNoteEvent != null) {
                unwrapIfNeeded(existingNoteEvent)
            } else {
                LocalCache.justConsume(newEvent, null, false)
                unwrapIfNeeded(newEvent)
            }
        }
    }

    private suspend fun unwrapSeal(event: SealedRumorEvent): Note? {
        val cacheInnerEventId = event.innerEventId
        return if (cacheInnerEventId != null) {
            val existingNoteEvent = LocalCache.getNoteIfExists(cacheInnerEventId)?.event
            if (existingNoteEvent != null) {
                unwrapIfNeeded(existingNoteEvent)
            } else {
                val newEvent = event.unsealThrowing(account.signer)
                // clear the encrypted payload to save memory
                LocalCache.getOrCreateNote(event.id).event = event.copyNoContent()

                // this is not verifiable
                LocalCache.justConsume(newEvent, null, true)
                unwrapIfNeeded(newEvent)
            }
        } else {
            val newEvent = event.unsealThrowing(account.signer)
            // clear the encrypted payload to save memory
            LocalCache.getOrCreateNote(event.id).event = event.copyNoContent()

            val existingNoteEvent = LocalCache.getNoteIfExists(newEvent.id)?.event
            if (existingNoteEvent != null) {
                unwrapIfNeeded(existingNoteEvent)
            } else {
                // this is not verifiable
                LocalCache.justConsume(newEvent, null, true)
                unwrapIfNeeded(newEvent)
            }
        }
    }

    private suspend fun unwrapIfNeeded(event: Event): Note? =
        when (event) {
            is GiftWrapEvent -> unwrapGiftWrap(event)
            is SealedRumorEvent -> unwrapSeal(event)
            else -> LocalCache.getNoteIfExists(event.id)
        }

    fun unwrapIfNeeded(
        note: Note?,
        onReady: (Note) -> Unit = {},
    ) = launchSigner {
        val noteEvent = note?.event
        if (noteEvent != null) {
            val resultingNote = unwrapIfNeeded(noteEvent)
            if (resultingNote != null && resultingNote != note) {
                onReady(resultingNote)
            }
        }
    }

    fun dataSources() = dataSources

    suspend fun createTempDraftNote(noteEvent: DraftWrapEvent): Note? = draftNoteCache.update(noteEvent)

    fun createTempDraftNote(
        innerEvent: Event,
        author: User,
    ): Note {
        val note =
            if (innerEvent is AddressableEvent) {
                AddressableNote(innerEvent.address())
            } else {
                Note(innerEvent.id)
            }
        note.loadEvent(innerEvent, author, LocalCache.computeReplyTo(innerEvent))
        return note
    }

    fun requestDVMContentDiscovery(
        dvmPublicKey: User,
        onReady: (event: Note) -> Unit,
    ) {
        launchSigner {
            account.requestDVMContentDiscovery(dvmPublicKey) { request, _ ->
                onReady(LocalCache.getOrCreateNote(request.id))
            }
        }
    }

    suspend fun cachedDVMContentDiscovery(pubkeyHex: String): Note? =
        withContext(Dispatchers.IO) {
            val fifteenMinsAgo = TimeUtils.fifteenMinutesAgo()
            // First check if we have an actual response from the DVM in LocalCache
            val response =
                LocalCache.notes.maxOrNullOf(
                    filter = { _, note ->
                        val noteEvent = note.event
                        noteEvent is NIP90ContentDiscoveryResponseEvent &&
                            noteEvent.pubKey == pubkeyHex &&
                            noteEvent.isTaggedUser(account.signer.pubKey) &&
                            noteEvent.createdAt > fifteenMinsAgo
                    },
                    comparator = CreatedAtComparator,
                )

            // If we have a response, get the tagged Request Event otherwise null
            return@withContext response?.event?.tags?.firstOrNull { it.size > 1 && it[0] == "e" }?.get(1)?.let {
                LocalCache.getOrCreateNote(it)
            }
        }

    fun sendZapPaymentRequestFor(
        bolt11: String,
        zappedNote: Note?,
        onSent: () -> Unit = {},
        onResponse: (Response?) -> Unit,
    ) = launchSigner {
        account.sendZapPaymentRequestFor(bolt11, zappedNote, onResponse)
        onSent()
    }

    /**
     * Pays a single BOLT-11 through a CLINK debit pointer (kind 21002) — the debit-rail
     * counterpart of [sendZapPaymentRequestFor]. [onResult] receives the decrypted
     * response (`isOk()` with optional preimage, or a GFY failure), or null on timeout,
     * delivered on the main dispatcher so UI callbacks (toasts, dialogs) are safe.
     * Untested end-to-end.
     */
    fun payInvoiceViaClinkDebit(
        pointer: NDebit,
        bolt11: String,
        onResult: (DebitResponse?) -> Unit,
    ) = launchSigner {
        val response = ClinkDebitPayer.payInvoice(account, pointer, bolt11)
        withContext(Dispatchers.Main) { onResult(response) }
    }

    fun getInteractiveStoryReadingState(dATag: String): AddressableNote = LocalCache.getOrCreateAddressableNote(InteractiveStoryReadingStateEvent.createAddress(account.signer.pubKey, dATag))

    fun updateInteractiveStoryReadingState(
        rootHint: EventHintBundle<InteractiveStoryBaseEvent>,
        readingSceneHint: EventHintBundle<InteractiveStoryBaseEvent>,
    ) {
        launchSigner {
            val readingState = getInteractiveStoryReadingState(rootHint.event.addressTag())
            val readingStateEvent = readingState.event as? InteractiveStoryReadingStateEvent

            if (readingStateEvent != null) {
                account.updateInteractiveStoryReadingState(readingStateEvent, readingSceneHint)
            } else {
                account.createInteractiveStoryReadingState(rootHint, readingSceneHint)
            }
        }
    }

    fun sendSats(
        lnAddress: String,
        user: User,
        milliSats: Long,
        message: String,
        onNewInvoice: (String) -> Unit,
        onError: (String, String) -> Unit,
        onProgress: (percent: Float) -> Unit,
        context: Context,
        zapType: LnZapEvent.ZapType? = null,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val effectiveZapType = zapType ?: defaultZapType()
                val zapRequest =
                    if (effectiveZapType != LnZapEvent.ZapType.NONZAP) {
                        // NIP-57 Appendix F: include amount + lnurl so the receipt can be validated.
                        val splitLnurl = LnurlForm.toUrl(lnAddress)?.let(LnurlForm::urlToBech32)
                        account.createZapRequestFor(
                            user = user,
                            message = message,
                            zapType = effectiveZapType,
                            amountMillisats = milliSats,
                            lnurl = splitLnurl,
                        )
                    } else {
                        null
                    }

                val invoice =
                    LightningAddressResolver().lnAddressInvoice(
                        lnAddress = lnAddress,
                        milliSats = milliSats,
                        message = message,
                        nostrRequest = zapRequest,
                        okHttpClient = httpClientBuilder::okHttpClientForMoney,
                        onProgress = onProgress,
                        context = context,
                    )

                onNewInvoice(invoice)
            } catch (e: LightningAddressResolver.LightningAddressError) {
                onError(e.title, e.msg)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                onError("Error", e.message ?: "Unknown error")
            }
        }
    }

    fun saveMediaToGallery(
        videoUri: String?,
        mimeType: String?,
        localContext: Context,
    ) {
        viewModelScope.launch {
            MediaSaverToDisk.saveDownloadingIfNeeded(
                videoUri = videoUri,
                okHttpClient = httpClientBuilder::okHttpClientForVideo,
                mimeType = mimeType,
                localContext = localContext,
                resolveBlossom = {
                    Amethyst.instance.blossomResolver
                        .findServers(it)
                        ?.serverUrl
                },
                onSuccess = {
                    Handler(Looper.getMainLooper()).post {
                        Toast
                            .makeText(localContext.applicationContext, R.string.video_saved_to_the_gallery, Toast.LENGTH_SHORT)
                            .show()
                    }
                },
                onError = {
                    toastManager.toast(R.string.failed_to_save_the_video, null, it)
                },
            )
        }
    }

    fun convertAccounts(loggedInAccounts: List<AccountInfo>?): Set<HexKey> =
        loggedInAccounts
            ?.mapNotNull {
                try {
                    it.npub.bechToBytes().toHexKey()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    null
                }
            }?.toSet() ?: emptySet()

    val trustedAccounts: StateFlow<Set<HexKey>> =
        LocalPreferences
            .accountsFlow()
            .map { loggedInAccounts ->
                convertAccounts(loggedInAccounts)
            }.onStart {
                emit(convertAccounts(LocalPreferences.allSavedAccounts()))
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptySet(),
            )

    val draftNoteCache = CachedDraftNotes(this)

    class CachedDraftNotes(
        val accountViewModel: AccountViewModel,
    ) : GenericBaseCacheAsync<DraftWrapEvent, Note>(100) {
        override suspend fun compute(key: DraftWrapEvent): Note? =
            withContext(Dispatchers.IO) {
                val decrypted = accountViewModel.account.draftsDecryptionCache.cachedDraft(key)
                if (decrypted != null) {
                    val author = LocalCache.getOrCreateUser(key.pubKey)
                    accountViewModel.createTempDraftNote(decrypted, author)
                } else {
                    null
                }
            }
    }

    val bechLinkCache = CachedLoadedBechLink(this)

    class CachedLoadedBechLink(
        val accountViewModel: AccountViewModel,
    ) : GenericBaseCache<String, LoadedBechLink>(20) {
        override suspend fun compute(key: String): LoadedBechLink? =
            withContext(Dispatchers.IO) {
                Nip19Parser.uriToRoute(key)?.let {
                    var returningNote: Note? = null

                    when (val parsed = it.entity) {
                        is NSec -> {}

                        is NPub -> {}

                        is NProfile -> {}

                        is NNote -> {
                            LocalCache.checkGetOrCreateNote(parsed.hex)?.let { note ->
                                returningNote = note
                            }
                        }

                        is NEvent -> {
                            LocalCache.consume(parsed)
                            LocalCache.checkGetOrCreateNote(parsed.hex)?.let { note ->
                                returningNote = note
                            }
                        }

                        is NEmbed -> {
                            withContext(Dispatchers.IO) {
                                val baseNote = LocalCache.getOrCreateNote(parsed.event)
                                if (baseNote.event == null) {
                                    launch(Dispatchers.IO) {
                                        LocalCache.justConsume(parsed.event, null, false)
                                    }
                                }

                                returningNote = baseNote
                            }
                        }

                        is NRelay -> {}

                        is NAddress -> {
                            LocalCache.checkGetOrCreateNote(parsed.aTag())?.let { note ->
                                returningNote = note
                            }
                        }

                        else -> {}
                    }

                    LoadedBechLink(returningNote, it)
                }
            }
    }
}

@Immutable data class LoadedBechLink(
    val baseNote: Note?,
    val nip19: Nip19Parser.ParseReturn,
)

var mockedCache: AccountViewModel? = null

@SuppressLint("ViewModelConstructorInComposable")
@Composable
fun mockAccountViewModel(): AccountViewModel {
    mockedCache?.let { return it }

    val scope = rememberCoroutineScope()

    val uiState =
        UiSettingsState(
            uiSettingsFlow = UiSettingsFlow(),
            isMobileOrMeteredConnection = MutableStateFlow(false),
            scope = scope,
        )

    val keyPair =
        KeyPair(
            privKey = Hex.decode("0f761f8a5a481e26f06605a1d9b3e9eba7a107d351f43c43a57469b788274499"),
            pubKey = Hex.decode("989c3734c46abac7ce3ce229971581a5a6ee39cdd6aa7261a55823fa7f8c4799"),
            forceReplacePubkey = false,
        )

    val client = EmptyNostrClient()
    val authenticator = EmptyIAuthStatus

    val nwcFilters = NWCPaymentFilterAssembler(client)
    val failureTracker = RelayOfflineTracker(client)

    val account =
        Account(
            settings = AccountSettings(keyPair),
            signer = NostrSignerInternal(keyPair),
            geolocationFlow = { MutableStateFlow<LocationState.LocationResult>(LocationState.LocationResult.Loading) },
            nwcFilterAssembler = { nwcFilters },
            cashuWalletFilterAssembler = {
                com.vitorpamplona.amethyst.commons.relayClient.assemblers
                    .CashuWalletFilterAssembler(client)
            },
            cashuMintDirectoryFilterAssembler = {
                com.vitorpamplona.amethyst.commons.relayClient.assemblers
                    .CashuMintDirectoryFilterAssembler(client)
            },
            okHttpClientForMoney = { okhttp3.OkHttpClient() },
            otsResolverBuilder = { EmptyOtsResolverBuilder.build() },
            cache = LocalCache,
            client = client,
            scope = scope,
        )

    return AccountViewModel(
        account = account,
        settings = uiState,
        torSettings = TorSettingsFlow(torType = MutableStateFlow(TorType.OFF)),
        httpClientBuilder = EmptyRoleBasedHttpClientBuilder(),
        dataSources = RelaySubscriptionsCoordinator(LocalCache, client, authenticator, failureTracker, scope),
        nip05ClientBuilder = { EmptyNip05Client() },
    ).also {
        mockedCache = it
    }
}

var vitorCache: AccountViewModel? = null

@SuppressLint("ViewModelConstructorInComposable")
@Composable
fun mockVitorAccountViewModel(): AccountViewModel {
    mockedCache?.let { return it }

    val scope = rememberCoroutineScope()

    val uiState =
        UiSettingsState(
            uiSettingsFlow = UiSettingsFlow(),
            isMobileOrMeteredConnection = MutableStateFlow(false),
            scope = scope,
        )

    val keyPair =
        KeyPair(
            pubKey = Hex.decode("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"),
        )

    val client = EmptyNostrClient()
    val authenticator = EmptyIAuthStatus

    val nwcFilters = NWCPaymentFilterAssembler(client)
    val failureTracker = RelayOfflineTracker(client)

    val account =
        Account(
            settings = AccountSettings(keyPair),
            signer = NostrSignerInternal(keyPair),
            geolocationFlow = { MutableStateFlow<LocationState.LocationResult>(LocationState.LocationResult.Loading) },
            nwcFilterAssembler = { nwcFilters },
            cashuWalletFilterAssembler = {
                com.vitorpamplona.amethyst.commons.relayClient.assemblers
                    .CashuWalletFilterAssembler(client)
            },
            cashuMintDirectoryFilterAssembler = {
                com.vitorpamplona.amethyst.commons.relayClient.assemblers
                    .CashuMintDirectoryFilterAssembler(client)
            },
            okHttpClientForMoney = { okhttp3.OkHttpClient() },
            otsResolverBuilder = { EmptyOtsResolverBuilder.build() },
            cache = LocalCache,
            client = EmptyNostrClient(),
            scope = scope,
        )

    return AccountViewModel(
        account = account,
        settings = uiState,
        torSettings = TorSettingsFlow(torType = MutableStateFlow(TorType.OFF)),
        httpClientBuilder = EmptyRoleBasedHttpClientBuilder(),
        dataSources = RelaySubscriptionsCoordinator(LocalCache, client, authenticator, failureTracker, scope),
        nip05ClientBuilder = { EmptyNip05Client() },
    ).also {
        vitorCache = it
    }
}
