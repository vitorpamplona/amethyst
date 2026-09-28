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
package com.vitorpamplona.amethyst.commons.viewmodels

import androidx.collection.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.audio.VisualizerStyle
import com.vitorpamplona.amethyst.commons.cashu.ops.describeMintError
import com.vitorpamplona.amethyst.commons.chats.rooms.markRoomNoteAsRead
import com.vitorpamplona.amethyst.commons.chats.rooms.rowHasUnread
import com.vitorpamplona.amethyst.commons.feeds.CardFeedState
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.marmot.GroupMemberInfo
import com.vitorpamplona.amethyst.commons.marmot.MarmotGroupIconChange
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.CombinedZap
import com.vitorpamplona.amethyst.commons.model.Dao
import com.vitorpamplona.amethyst.commons.model.LatestKeyPackageOwner
import com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers
import com.vitorpamplona.amethyst.commons.model.NOTIFICATION_LAST_READ_KEY
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.ReactionRowItem
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.VideoPlayerButtonItem
import com.vitorpamplona.amethyst.commons.model.ZapAmountCommentNotification
import com.vitorpamplona.amethyst.commons.model.ZapraiserStatus
import com.vitorpamplona.amethyst.commons.model.backups.ReplaceableBackupConflict
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.composer.NewMessageTagger
import com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.commons.model.navigation.BottomBarEntry
import com.vitorpamplona.amethyst.commons.model.navigation.NavBarItem
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.nip56Reports.UserReportWarningState
import com.vitorpamplona.amethyst.commons.model.nip56Reports.dmReportWarningFor
import com.vitorpamplona.amethyst.commons.model.observables.CreatedAtComparator
import com.vitorpamplona.amethyst.commons.model.privateChatLastReadRoute
import com.vitorpamplona.amethyst.commons.model.zapraiserStatus
import com.vitorpamplona.amethyst.commons.nests.room.activity.NestBridge
import com.vitorpamplona.amethyst.commons.relayClient.BlockedRelayFilteringClient
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.RelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.commons.relays.eventsync.EventSync
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.bolt12_offers
import com.vitorpamplona.amethyst.commons.resources.bolt12_payment_failed
import com.vitorpamplona.amethyst.commons.resources.bolt12_payment_sent
import com.vitorpamplona.amethyst.commons.resources.cashu_failed_redemption
import com.vitorpamplona.amethyst.commons.resources.cashu_failed_redemption_explainer_error_msg
import com.vitorpamplona.amethyst.commons.resources.cashu_successful_redemption
import com.vitorpamplona.amethyst.commons.resources.cashu_successful_redemption_explainer
import com.vitorpamplona.amethyst.commons.resources.concord_members_roles_failed
import com.vitorpamplona.amethyst.commons.resources.concord_members_roles_title
import com.vitorpamplona.amethyst.commons.resources.draft_note
import com.vitorpamplona.amethyst.commons.resources.error_dialog_zap_error
import com.vitorpamplona.amethyst.commons.resources.it_s_not_possible_to_quote_to_a_draft_note
import com.vitorpamplona.amethyst.commons.resources.login_with_a_private_key_to_be_able_to_boost_posts
import com.vitorpamplona.amethyst.commons.resources.login_with_a_private_key_to_be_able_to_sign_events
import com.vitorpamplona.amethyst.commons.resources.no_lightning_address_set
import com.vitorpamplona.amethyst.commons.resources.no_wallet_found
import com.vitorpamplona.amethyst.commons.resources.nutzap_failed_no_event
import com.vitorpamplona.amethyst.commons.resources.nutzap_failed_no_recipient
import com.vitorpamplona.amethyst.commons.resources.nutzap_failed_private_note
import com.vitorpamplona.amethyst.commons.resources.nutzap_failed_title
import com.vitorpamplona.amethyst.commons.resources.pow_publish_failed
import com.vitorpamplona.amethyst.commons.resources.pow_publish_failed_retry
import com.vitorpamplona.amethyst.commons.resources.pow_settings_title
import com.vitorpamplona.amethyst.commons.resources.read_only_user
import com.vitorpamplona.amethyst.commons.resources.signer_illegal_state_exception_description
import com.vitorpamplona.amethyst.commons.resources.signer_not_found_exception
import com.vitorpamplona.amethyst.commons.resources.signer_not_found_exception_description
import com.vitorpamplona.amethyst.commons.resources.unauthorized_exception
import com.vitorpamplona.amethyst.commons.resources.unauthorized_exception_description
import com.vitorpamplona.amethyst.commons.resources.user_x_does_not_have_a_lightning_address_setup_to_receive_sats
import com.vitorpamplona.amethyst.commons.service.ClinkDebitPayer
import com.vitorpamplona.amethyst.commons.service.V4VPaymentHandler
import com.vitorpamplona.amethyst.commons.service.ZapPaymentHandler
import com.vitorpamplona.amethyst.commons.service.broadcast.BroadcastTracker
import com.vitorpamplona.amethyst.commons.service.call.CallSessionBridge
import com.vitorpamplona.amethyst.commons.service.cashu.melt.MeltProcessor
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.commons.service.lnurl.LightningInvoiceResolver
import com.vitorpamplona.amethyst.commons.service.pow.PoWCategory
import com.vitorpamplona.amethyst.commons.service.pow.powKindLabelRes
import com.vitorpamplona.amethyst.commons.state.UiSettingsState
import com.vitorpamplona.amethyst.commons.tor.TorSettingsFlow
import com.vitorpamplona.amethyst.commons.ui.components.toasts.ToastManager
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.state.GenericBaseCache
import com.vitorpamplona.amethyst.commons.ui.state.GenericBaseCacheAsync
import com.vitorpamplona.amethyst.commons.util.DebouncedPublisher
import com.vitorpamplona.amethyst.commons.util.logTime
import com.vitorpamplona.amethyst.commons.util.showAmount
import com.vitorpamplona.amethyst.commons.util.showAmountInteger
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModelHost
import com.vitorpamplona.amethyst.commons.wallet.ReloadMintRequest
import com.vitorpamplona.quartz.experimental.clink.debits.DebitResponse
import com.vitorpamplona.quartz.experimental.clink.pointers.NDebit
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryBaseEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryReadingStateEvent
import com.vitorpamplona.quartz.marmot.appComponents.EncryptedMediaReferenceV2
import com.vitorpamplona.quartz.marmot.appComponents.GroupBlossomImageV1
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthenticator
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CachingEventDecoder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.people.PubKeyReferenceTag
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip05DnsIdentifiers.INip05Client
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.base.NIP17Group
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NRelay
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.IErrorResponseLike
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PaySuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Response
import com.vitorpamplona.quartz.nip51Lists.PinListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.OldBookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark
import com.vitorpamplona.quartz.nip51Lists.interestList.InterestListEvent
import com.vitorpamplona.quartz.nip56Reports.ReportType
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip57Zaps.validate.LnurlForm
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiserAmount
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip60Cashu.token.CashuToken
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryResponse.DvmContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip92IMeta.imeta
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.podcasts.PodcastBoostagram
import com.vitorpamplona.quartz.podcasts.PodcastEpisode
import com.vitorpamplona.quartz.podcasts.PodcastShow
import com.vitorpamplona.quartz.podcasts.PodcastValue
import com.vitorpamplona.quartz.utils.BigDecimal
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.mapNotNullAsync
import com.vitorpamplona.quartz.utils.plus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * How long the navigation pickers wait for the toggles to stop before publishing. Long enough that a
 * run of taps is one publish, short enough that leaving the screen normally finds nothing to flush.
 */
private const val PICKER_PUBLISH_DEBOUNCE_MS = 2000L

@Stable
class AccountViewModel(
    val account: Account,
    val settings: UiSettingsState,
    val torSettings: TorSettingsFlow,
    val dataSources: RelaySubscriptionsCoordinator,
    val httpClientBuilder: IRoleBasedHttpClientBuilder,
    val nip05ClientBuilder: () -> INip05Client,
    val host: AccountViewModelHost,
) : ViewModel(),
    Dao {
    var firstRoute: Route? = null

    val toastManager = ToastManager()
    val broadcastTracker = BroadcastTracker()
    val feedStates = AccountFeedContentStates(account, viewModelScope, host.memoryPressure)

    /**
     * `true` when the local Blossom cache is enabled and the probe sees it up. Only drives the
     * "detected" chip in settings: the routing itself happens in LocalBlossomCacheRedirectInterceptor.
     */
    val localBlossomCacheDetected: StateFlow<Boolean> =
        combine(
            account.settings.useLocalBlossomCache,
            host.localBlossomCacheAvailable,
        ) { toggle, probeUp -> toggle && probeUp }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            false,
        )

    /**
     * The account's call state machine. Owned by [Account], not by this ViewModel: a call must
     * survive MainActivity being destroyed while it is backgrounded. See [Account.callManager].
     */
    val callManager = account.callManager

    init {
        // Populate CallSessionBridge so CallActivity and background
        // receivers can reach callManager + account + accountViewModel.
        CallSessionBridge
            .set(callManager, account, this)

        // A mined post that fails to sign or broadcast would otherwise die
        // silently — the composer already returned when it was enqueued.
        viewModelScope.launch {
            host.powPublishFailures.collect { failure ->
                val kindLabel = loadStringRes(powKindLabelRes(failure.kind))
                if (failure.willRetryOnRestart) {
                    toastManager.toast(Res.string.pow_settings_title, Res.string.pow_publish_failed_retry, kindLabel)
                } else {
                    toastManager.toast(Res.string.pow_settings_title, Res.string.pow_publish_failed, kindLabel, failure.message.orEmpty())
                }
            }
        }
    }

    /**
     * Every known relay worth crawling for a full-history sweep (Event Sync,
     * Cashu wallet discovery): relays that have connected at least once or were
     * never tried, ordered busiest-first so the most fruitful are queried first.
     */
    fun crawlRelayDb(): List<NormalizedRelayUrl> {
        val stats = host.relayStats.snapshot()

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
     * land in the production [com.vitorpamplona.amethyst.commons.model.cache.LocalCache] — for
     * crawls (Event Sync, Cashu wallet discovery). The caller must `close()` it.
     */
    fun buildCrawlClient(): INostrClient {
        // Create a new scope that inherits the ViewModel's lifecycle
        // but uses a SupervisorJob so child failures are independent.
        val customScope = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob())

        // Provides a relay pool. Crawls hit many relays with overlapping
        // filters, so the duplicate-frame decoder pays off most here.
        // Wrapped so crawls (Event Sync, Cashu discovery) never contact this
        // account's NIP-51 kind:10006 blocked relays either.
        val newClient =
            BlockedRelayFilteringClient(
                NostrClient(host.websocketBuilder, customScope, CachingEventDecoder()),
                blockedRelays = { account.blockedRelayList.flow.value },
            )

        // Authenticates with relays (registers itself with the client).
        RelayAuthenticator(
            newClient,
            customScope,
            signWithAllLoggedInUsers = { _, authTemplate, _ ->
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

    /**
     * The bottom-bar envelope dot: true when ANY Messages row is showing its blue dot.
     *
     * Per-row via [rowHasUnread], which mirrors what each row composable computes for itself.
     * This used to call `unreadPrivateChatRoute` directly, which returns null for anything that is not
     * `ChatroomKeyable` — so only NIP-17/NIP-04 DMs counted, and a public chat, ephemeral room, geohash
     * cell, Marmot group, NIP-29/Buzz channel or Concord channel could sit there with a visible dot
     * while the envelope stayed clean.
     */
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
                val flows = loadedFeedState?.list?.mapNotNull { chat -> rowHasUnread(chat, account)?.flow }

                if (!flows.isNullOrEmpty()) {
                    combine(flows) { newItems ->
                        newItems.any { it }
                    }
                } else {
                    MutableStateFlow(false)
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
        // Concord messages are encrypted: a public kind-7 would e-tag the private rumor id onto
        // public relays. Route the reaction through a channel-plane wrap instead. (Retraction of an
        // existing Concord reaction is a follow-up; for now this only adds one.)
        if (note.inGatherers?.any { it is ConcordChannel } == true) {
            launchSigner { account.concord.reactToConcordMessage(note, reaction) }
            return
        }

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
                val minesReactions = account.powDifficultyFor(ReactionEvent.KIND) != null
                if (!minesReactions && settings.useTrackedBroadcasts() && note.event !is NIP17Group && !note.isPrivateRumor()) {
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
                    // Fire-and-forget (original behavior). When PoW mining is
                    // on for reactions this path also routes through the
                    // mining queue, which has its own progress banner.
                    account.reactTo(note, reaction)
                }
            }
        }
    }

    fun reactToOrDelete(note: Note) {
        val reaction = reactionChoices().first()
        reactToOrDelete(note, reaction)
    }

    /**
     * Retracts one of your own notes the way its transport requires. Private rumors (NIP-17 DMs,
     * Marmot group messages) take a private deletion; a public NIP-09 would e-tag the rumor id
     * onto public relays.
     */
    fun deleteOwn(note: Note) {
        if (note.isPrivateRumor()) deletePrivately(note) else delete(note)
    }

    /**
     * Retracts the user's own private rumor (e.g. a NIP-17 DM message) with a
     * gift-wrapped NIP-09 deletion to the same participants. A public deletion
     * would e-tag the private rumor id onto public relays.
     */
    fun deletePrivately(note: Note) {
        launchSigner {
            account.deletePrivately(listOf(note), note)
        }
    }

    /** Ban the author of a Concord channel message (no-op unless this account may ban them). */
    fun banConcordMember(note: Note) {
        val (communityId, member) = account.concord.concordBanTarget(note) ?: return
        launchSigner { account.concord.banConcordMember(communityId, member) }
    }

    /** Toggle the Admin role on the author of a Concord channel message (owner only). */
    fun toggleConcordAdmin(note: Note) {
        val (communityId, member, isAdmin) = account.concord.concordAdminTarget(note) ?: return
        launchSigner {
            if (isAdmin) account.concord.removeConcordAdmin(communityId, member) else account.concord.makeConcordAdmin(communityId, member)
        }
    }

    /** Promote/demote [member] as an Admin of [communityId] (from the Members roster; owner only takes effect). */
    fun setConcordAdmin(
        communityId: String,
        member: HexKey,
        makeAdmin: Boolean,
    ) = launchSigner {
        if (makeAdmin) account.concord.makeConcordAdmin(communityId, member) else account.concord.removeConcordAdmin(communityId, member)
    }

    /**
     * Set [member]'s CORD-04 roles in [communityId] to exactly [roleIds] (empty revokes
     * everything). The Control Plane grant REPLACES the member's role set rather than
     * merging into it, so [roleIds] must be the *complete* list the member should end up
     * holding — the caller (the Members roster dialog) preselects their current roles for
     * that reason. Authority is re-checked at fold time by every client, so the caller must
     * also have offered only roles it strictly outranks on a member it strictly outranks.
     */
    fun setConcordRoles(
        communityId: String,
        member: HexKey,
        roleIds: List<String>,
    ) = launchSigner {
        if (!account.concord.grantConcordRole(communityId, member, roleIds)) {
            toastManager.toast(Res.string.concord_members_roles_title, Res.string.concord_members_roles_failed)
        }
    }

    /** Ban/unban [member] from [communityId] (from the Members roster). */
    fun setConcordBan(
        communityId: String,
        member: HexKey,
        ban: Boolean,
    ) = launchSigner {
        if (ban) account.concord.banConcordMember(communityId, member) else account.concord.unbanConcordMember(communityId, member)
    }

    /**
     * Remove [member] from [communityId] absolutely (CORD-06 Refounding): rotate the
     * community key so the member's key stops working for anything sent afterwards.
     * Heavier than a ban (re-keys every retained member); owner / BAN-holder only.
     */
    fun removeConcordMember(
        communityId: String,
        member: HexKey,
    ) = launchSigner {
        account.concord.refoundConcordCommunity(communityId, setOf(member))
    }

    /**
     * Pull the account's Concord community list (kind 13302) from the stock + own relays and, in
     * addition, from the bootstrap relays of every Concord community pinned to the bottom bar. The
     * private list frequently lives only on a community's own relays, so a pinned community opened
     * cold — never through the hub — is only reachable via the relays saved on its tab.
     */
    fun importConcordCommunities() =
        viewModelScope.launch(Dispatchers.IO) {
            val pinnedRelays =
                account.settings.syncedSettings.navigation.bottomBarItems.value
                    .flatMap {
                        when (it) {
                            is BottomBarEntry.Concord -> it.relays
                            is BottomBarEntry.ConcordChannel -> it.relays
                            else -> emptyList()
                        }
                    }.mapNotNullTo(HashSet()) { RelayUrlNormalizer.normalizeOrNull(it) }
            account.concord.importConcordCommunities(pinnedRelays)
        }

    /** Publish an ephemeral typing heartbeat to a Concord channel (throttled by the caller). */
    fun sendConcordTyping(
        communityId: String,
        channelIdHex: String,
    ) = viewModelScope.launch(Dispatchers.IO) {
        account.concord.sendConcordTyping(communityId, channelIdHex)
    }

    fun sendBuzzTyping(channel: RelayGroupChannel) =
        viewModelScope.launch(Dispatchers.IO) {
            account.relayGroups.sendBuzzTyping(channel)
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
        LocalCache.appHost.assertNotMainThread()

        val isFromLoggedIn = note.author?.pubkeyHex == userProfile().pubkeyHex
        val isFromLoggedInFollow = note.author?.let { followUsers.contains(it.pubkeyHex) } ?: true
        val isPostHidden = note.isHiddenFor(accountChoices)
        val isHiddenAuthor = note.author?.let { account.isHidden(it) } == true

        val noteEvent = note.event
        val isDecryptedPostHidden = if (noteEvent is EncryptedDmEvent) account.isDecryptedContentHidden(noteEvent) else false

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

    private val userReportWarningFlows = LruCache<User, StateFlow<UserReportWarningState>>(300)

    /**
     * Whether to warn about [user] as the counterpart of a 1:1 DM, and with what.
     *
     * Safe to cache: [User.reports] is never nulled once created, so the upstream this subscribes to
     * survives the idle gaps that `WhileSubscribed` introduces.
     */
    fun createUserReportWarningFlow(user: User): StateFlow<UserReportWarningState> =
        userReportWarningFlows.get(user)
            ?: combineTransform(
                user.reports().reportsNamingUser,
                account.kind3FollowList.flow,
                account.settings.syncedSettings.security.warnAboutPostsWithReports,
            ) { _, followList, warnAboutReports ->
                emit(
                    dmReportWarningFor(
                        counterpart = user,
                        loggedInPubKey = account.signer.pubKey,
                        followingKeySet = followList.authors,
                        warnAboutReports = warnAboutReports,
                    ),
                )
            }.flowOn(Dispatchers.IO)
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(10000, 10000),
                    UserReportWarningState.SILENT,
                ).also {
                    userReportWarningFlows.put(user, it)
                }

    private val noteMustShowExpandButtonFlows = LruCache<Note, StateFlow<Boolean>>(300)

    fun createMustShowExpandButtonFlows(note: Note): StateFlow<Boolean> =
        noteMustShowExpandButtonFlows.get(note)
            // Cold wrapper: WhileSubscribed drops the upstream when idle and a
            // memory trim may destroy the NoteFlowSet in between; re-resolving
            // `flow()` on every restart keeps this cached StateFlow alive.
            ?: flow { emitAll(note.flow().relays.stateFlow) }
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
            account.zaps.calculateIfNoteWasZappedByAccount(zappedNote, afterTimeInSeconds)
        }

    suspend fun calculateZapAmount(zappedNote: Note): String {
        // The signed-in user's own outgoing onchain zaps that aren't
        // yet CONFIRMED still need to show in the counter — the user
        // knows what they sent, so make the counter reflect reality
        // immediately instead of waiting for chain confirmation.
        val ownPendingOnchain = zappedNote.extraOwnPendingOnchainSats(account.userProfile().pubkeyHex)
        return if (zappedNote.zapPayments.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                val nwc = account.zaps.calculateZappedAmount(zappedNote)
                showAmount(nwc + BigDecimal(ownPendingOnchain))
            }
        } else {
            showAmount(zappedNote.zapsAmount + BigDecimal(ownPendingOnchain))
        }
    }

    suspend fun calculateZapraiser(zappedNote: Note): ZapraiserStatus {
        val zapraiserAmount = zappedNote.event?.zapraiserAmount() ?: 0
        return if (zappedNote.zapPayments.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                zapraiserStatus(account.zaps.calculateZappedAmount(zappedNote), zapraiserAmount)
            }
        } else {
            zapraiserStatus(zappedNote.zapsAmount, zapraiserAmount)
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
                                showAmountInteger((it.response.event as? ZapReceiptEvent)?.amount),
                                it.response,
                            )
                    }.toMutableMap()

            val results =
                mapNotNullAsync(
                    zaps.filter { (it.request.event as? ZapRequestEvent)?.isPrivateZap() == true },
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
                val request = it.request.event as? ZapRequestEvent
                if (request?.isPrivateZap() == true) {
                    val cachedPrivateRequest = account.privateZapsDecryptionCache.cachedPrivateZap(request)
                    if (cachedPrivateRequest != null) {
                        ZapAmountCommentNotification(
                            LocalCache.getUserIfExists(cachedPrivateRequest.pubKey) ?: it.request.author,
                            cachedPrivateRequest.content.ifBlank { null },
                            showAmountInteger((it.response.event as? ZapReceiptEvent)?.amount),
                            it.response,
                        )
                    } else {
                        ZapAmountCommentNotification(
                            it.request.author,
                            it.request.event
                                ?.content
                                ?.ifBlank { null },
                            showAmountInteger((it.response.event as? ZapReceiptEvent)?.amount),
                            it.response,
                        )
                    }
                } else {
                    ZapAmountCommentNotification(
                        it.request.author,
                        it.request.event
                            ?.content
                            ?.ifBlank { null },
                        showAmountInteger((it.response.event as? ZapReceiptEvent)?.amount),
                        it.response,
                    )
                }
            }.toImmutableList()

    fun cachedDecryptAmountMessageInGroup(baseNote: Note): ImmutableList<ZapAmountCommentNotification> {
        val myList = baseNote.zaps.toList()

        return myList
            .map {
                val request = it.first.event as? ZapRequestEvent
                if (request?.isPrivateZap() == true) {
                    val cachedPrivateRequest = account.privateZapsDecryptionCache.cachedPrivateZap(request)
                    if (cachedPrivateRequest != null) {
                        ZapAmountCommentNotification(
                            LocalCache.getUserIfExists(cachedPrivateRequest.pubKey) ?: it.first.author,
                            cachedPrivateRequest.content.ifBlank { null },
                            showAmountInteger((it.second?.event as? ZapReceiptEvent)?.amount),
                            it.second,
                        )
                    } else {
                        ZapAmountCommentNotification(
                            it.first.author,
                            it.first.event
                                ?.content
                                ?.ifBlank { null },
                            showAmountInteger((it.second?.event as? ZapReceiptEvent)?.amount),
                            it.second,
                        )
                    }
                } else {
                    ZapAmountCommentNotification(
                        it.first.author,
                        it.first.event
                            ?.content
                            ?.ifBlank { null },
                        showAmountInteger((it.second?.event as? ZapReceiptEvent)?.amount),
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
                                showAmountInteger((it.second?.event as? ZapReceiptEvent)?.amount),
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
        val zapEvent = zapNote.event as? ZapReceiptEvent ?: return null
        val zapRequest = zapEvent.zapRequest ?: return null

        return innerDecryptAmountMessage(zapRequest, zapEvent)?.copy(zapNote = zapNote)
    }

    suspend fun innerDecryptAmountMessage(
        zapRequest: Note,
        zapEvent: Note,
    ): ZapAmountCommentNotification? {
        val zapEvent = zapEvent.event as? ZapReceiptEvent ?: return null
        val zapRequest = zapRequest.event as? ZapRequestEvent ?: return null
        return innerDecryptAmountMessage(zapRequest, zapEvent)
    }

    suspend fun innerDecryptAmountMessage(
        zapRequestEvent: ZapRequestEvent,
        zapEvent: ZapReceiptEvent,
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

    // Notes the user is currently zapping. A zap receipt only reaches relays a moment
    // after the payment clears, so a surface (the chat bubble) can show an optimistic
    // "zapping" indicator in that gap. Cleared when the receipt's amount shows, on error,
    // when the payment hands off to an external wallet, or by a safety timeout.
    private val zapsInFlight = MutableStateFlow<Set<HexKey>>(emptySet())
    val zapsInFlightFlow: StateFlow<Set<HexKey>> get() = zapsInFlight

    fun markZapInFlight(noteId: HexKey) {
        zapsInFlight.value = zapsInFlight.value + noteId
        viewModelScope.launch {
            // Safety net: clear even if no receipt ever arrives (silent failure, or a
            // receipt that never propagates back to us).
            delay(45_000L)
            endZapInFlight(noteId)
        }
    }

    fun endZapInFlight(noteId: HexKey) {
        zapsInFlight.value = zapsInFlight.value - noteId
    }

    fun zap(
        note: Note,
        amountInMillisats: Long,
        pollOption: Int?,
        message: String,
        showErrorIfNoLnAddress: Boolean = true,
        onError: (String, String, User?) -> Unit,
        onProgress: (percent: Float) -> Unit,
        onPayViaIntent: (ImmutableList<ZapPaymentHandler.Payable>) -> Unit,
        zapType: ZapReceiptEvent.ZapType? = null,
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
            if (note.isPrivateRumor() && requestedType != ZapReceiptEvent.ZapType.NONZAP) {
                ZapReceiptEvent.ZapType.PRIVATE
            } else {
                requestedType
            }

        ZapPaymentHandler(account, host.moneyOpRelays).zap(
            note = note,
            amountMilliSats = amountInMillisats,
            pollOption = pollOption,
            message = message,
            showErrorIfNoLnAddress = showErrorIfNoLnAddress,
            lnurl = host.lnurlTransport,
            onError = onError,
            onProgress = onProgress,
            onPayViaIntent = onPayViaIntent,
            zapType = effectiveType,
        )
    }

    /** True when the account has at least one NIP-47 wallet configured. */
    suspend fun hasNwcWallet(): Boolean =
        account.settings.nwcWallets.value
            .isNotEmpty()

    /** True when a BOLT12 offer can be paid in-app: an NWC wallet is set and advertises `pay` (nwc#2). */
    fun canPayBolt12ViaNwc(): Boolean = account.zaps.canZapViaBolt12()

    /**
     * Pays a recipient's BOLT12 [offer] over the default NWC wallet using the nwc#2
     * `pay` method, wrapping it as a BIP321 `bitcoin:?lno=` instruction. This is a
     * plain payment, not a NIP-B1 zap (no Nostr receipt); the outcome is surfaced as
     * a toast. Callers should gate on [hasNwcWallet].
     */
    fun payBolt12OfferViaNwc(
        offer: String,
        amountMillisats: Long,
    ) = launchSigner {
        account.zaps.sendNwcRequest(PayMethod.create("bitcoin:?lno=$offer", amountMillisats)) { response ->
            when (response) {
                is PaySuccessResponse -> toastManager.toast(Res.string.bolt12_offers, Res.string.bolt12_payment_sent)
                is IErrorResponseLike ->
                    toastManager.toast(Res.string.bolt12_offers, Res.string.bolt12_payment_failed, response.errorMessage() ?: "")
                else -> toastManager.toast(Res.string.bolt12_offers, Res.string.bolt12_payment_failed, "")
            }
        }
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
    suspend fun payV4V(
        value: PodcastValue,
        totalSats: Long,
        podcastName: String?,
        episodeName: String?,
        zappedNote: Note?,
        streaming: Boolean = false,
        onProgress: (Float) -> Unit = {},
    ) = launchSigner {
        executeV4V(
            value = value,
            totalMilliSats = totalSats * 1000,
            podcastName = podcastName,
            episodeName = episodeName,
            zappedNote = zappedNote,
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

        // onPayInvoicesViaIntent is a plain callback the payment handler invokes later,
        // so both strings are resolved here, while still inside the signer coroutine.
        val noWalletFoundStr = loadStringRes(Res.string.no_wallet_found)
        val zapErrorTitle = loadStringRes(Res.string.error_dialog_zap_error)

        V4VPaymentHandler(account, host.moneyOpRelays).pay(
            value = value,
            totalMilliSats = totalMilliSats,
            boostagram = boostagram,
            zappedNote = zappedNote,
            asZap = !streaming,
            zapType = ZapReceiptEvent.ZapType.PUBLIC,
            lnurl = host.lnurlTransport,
            onError = { title, message ->
                if (!streaming) toastManager.toast(title, message)
            },
            onProgress = onProgress,
            onPayInvoicesViaIntent = { invoices ->
                if (!streaming) {
                    invoices.forEach { invoice ->
                        if (!host.openLightningWallet(invoice)) toastManager.toast(zapErrorTitle, noWalletFoundStr)
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
                loadStringRes(Res.string.nutzap_failed_title),
                loadStringRes(Res.string.nutzap_failed_private_note),
                baseNote.author,
            )
            return@launchSigner
        }
        val recipient = baseNote.author?.pubkeyHex
        if (recipient == null) {
            onError(
                loadStringRes(Res.string.nutzap_failed_title),
                loadStringRes(Res.string.nutzap_failed_no_recipient),
                null,
            )
            return@launchSigner
        }
        val zappedEvent = baseNote.toEventHint<Event>()
        if (zappedEvent == null) {
            onError(
                loadStringRes(Res.string.nutzap_failed_title),
                loadStringRes(Res.string.nutzap_failed_no_event),
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
                loadStringRes(Res.string.nutzap_failed_title),
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
                loadStringRes(Res.string.nutzap_failed_title),
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
        if (account.powDifficultyFor(RepostEvent.KIND) != null) {
            // Reposts are mined: route through the queue (which has its own
            // progress banner) instead of the inline tracked path.
            launchSigner { account.boost(note) }
        } else {
            launchTrackedOrDirect(
                createTracked = { account.createBoostEvent(note) },
                consumeTracked = account::consumeBoostEvent,
                direct = { account.boost(note) },
            )
        }

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

    fun hashtagFollows(user: User): Note = LocalCache.getOrCreateAddressableNote(InterestListEvent.createAddress(user.pubkeyHex))

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
     * [decrypt] that always answers: [onReady] gets null when the content can't be read — a
     * read-only account holding no key, a DM this account isn't part of, or a signer that
     * refused/timed out. [decrypt] stays silent in those cases, which strands callers that must
     * finish either way (a menu that only closes once the copy resolves, say).
     */
    fun decryptOrNull(
        note: Note,
        onReady: (String?) -> Unit,
    ) = launchSigner {
        val decrypted =
            try {
                account.decryptContent(note)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // launchSigner still gets the exception to toast/log the signer failure.
                onReady(null)
                throw e
            }
        onReady(decrypted)
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

    /**
     * Keeps another app's version of a backed-up list. Off the main thread: it waits for the
     * backup guard's lock, which the backup collectors hold while diffing large lists.
     */
    fun acceptExternalBackupVersion(conflict: ReplaceableBackupConflict) = viewModelScope.launch(Dispatchers.IO) { account.acceptExternalVersion(conflict) }

    inline fun launchSigner(crossinline action: suspend () -> Unit) =
        viewModelScope.launch(Dispatchers.IO) {
            reportSignerErrors { action() }
        }

    /**
     * The signer-failure reporting of [launchSigner], separated from its scope so work that must
     * outlive [viewModelScope] can still surface the same messages. Public because [launchSigner]
     * is inline.
     */
    suspend fun reportSignerErrors(action: suspend () -> Unit) {
        try {
            action()
        } catch (e: CancellationException) {
            // MUST stay ahead of the IllegalStateException arm below: java.util.concurrent
            // .CancellationException extends IllegalStateException, so without this every cancelled
            // signer coroutine — a superseded debounce, a scope torn down mid-sign — would be
            // swallowed there and shown to the user as a "signer" toast reading
            // "JobCancellationException: StandaloneCoroutine was cancelled". Cancellation is not a
            // signer failure and must propagate for structured concurrency to work.
            throw e
        } catch (_: SignerExceptions.ReadOnlyException) {
            toastManager.toast(
                Res.string.read_only_user,
                Res.string.login_with_a_private_key_to_be_able_to_sign_events,
            )
        } catch (_: SignerExceptions.UnauthorizedDecryptionException) {
            toastManager.toast(
                Res.string.unauthorized_exception,
                Res.string.unauthorized_exception_description,
            )
        } catch (_: SignerExceptions.SignerNotFoundException) {
            toastManager.toast(
                Res.string.signer_not_found_exception,
                Res.string.signer_not_found_exception_description,
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
                Res.string.signer_not_found_exception,
                Res.string.signer_illegal_state_exception_description,
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
    ) = launchSigner { account.relayGroups.joinRelayGroup(channel, code) }

    fun leaveRelayGroup(channel: RelayGroupChannel) = launchSigner { account.relayGroups.leaveRelayGroup(channel) }

    /** NIP-29 migration: point our kind-10009 entry for [channel] at [newRelay] instead. */
    fun moveRelayGroup(
        channel: RelayGroupChannel,
        newRelay: NormalizedRelayUrl,
    ) = launchSigner { account.relayGroups.moveRelayGroup(channel, newRelay) }

    /** Delete the channel/group for everyone (kind-9008). Owner/admin only; the relay enforces it. */
    fun deleteRelayGroup(channel: RelayGroupChannel) = launchSigner { account.relayGroups.deleteRelayGroup(channel) }

    /**
     * Archive/unarchive a Buzz channel (kind-9002 `archived` tag) — hides it from the sidebar without
     * destroying it, and is reversible. Owner/admin only; the relay enforces it.
     */
    fun archiveRelayGroup(
        channel: RelayGroupChannel,
        archived: Boolean,
    ) = launchSigner { account.relayGroups.archiveRelayGroup(channel, archived) }

    /**
     * Take a relay group off Messages WITHOUT leaving it: drop it from my kind-10009 list so it stops
     * showing, but send no kind-9022 — I stay in the relay roster and can still read/post, and re-joining
     * re-surfaces it instantly. Also records it in `dismissedChannelInvites` so a Buzz relay re-announcing
     * my membership (kind-44100) can't bounce it back in as a pending invite. This is the soft counterpart
     * to [leaveRelayGroup]; "Remove from Messages" vs "Leave" is the same split the invite card offers.
     */
    fun removeRelayGroupFromMessages(channel: RelayGroupChannel) =
        launchSigner {
            account.settings.dismissChannelInvite(channel.groupId.id)
            account.unfollow(channel)
        }

    /**
     * Put a relay group (back) on Messages: write it into my kind-10009 so it shows and follows me to
     * other devices, and clear the dismissal [removeRelayGroupFromMessages] left behind so a Buzz relay
     * re-announcing my membership isn't filtered out. No kind-9021 join — the relay roster is untouched
     * by both halves of this toggle; this only records *my* decision to surface the channel.
     */
    fun addRelayGroupToMessages(channel: RelayGroupChannel) =
        launchSigner {
            account.settings.undismissChannelInvite(channel.groupId.id)
            account.follow(channel)
        }

    /**
     * Accept a channel somebody added me to. Identical to [addRelayGroupToMessages] — accepting an
     * invite *is* surfacing the channel, since the relay already put me in the roster (which is why
     * the channel opens and accepts posts today).
     */
    fun acceptChannelInvite(channel: RelayGroupChannel) = addRelayGroupToMessages(channel)

    /**
     * Hide a Buzz DM from Messages (kind-41012). DM-specific — a DM has no kind-10009 entry; the relay
     * republishes my per-viewer 30622 hidden snapshot, dropping it from the inbox until I re-open it.
     */
    fun hideBuzzDm(channel: RelayGroupChannel) = launchSigner { account.relayGroups.hideBuzzDm(channel) }

    /**
     * Bring a hidden Buzz DM back to Messages: Buzz has no "unhide", so re-open the conversation with
     * the same [participants] (a kind-41010 resolving to the same canonical channel), which drops it
     * from the 30622 hidden snapshot.
     */
    fun unhideBuzzDm(
        relay: NormalizedRelayUrl,
        participants: List<HexKey>,
    ) = launchSigner { account.relayGroups.openBuzzDm(relay, participants) }

    /**
     * Keep the channel off Messages without touching membership. Local and reversible — I stay in the
     * roster and can still open and post; [leaveChannelInvite] is the one that actually removes me.
     */
    fun dismissChannelInvite(channelId: String) {
        account.settings.dismissChannelInvite(channelId)
    }

    /**
     * Actually leave: kind-9022 to the host relay, which answers with a kind-44101 that supersedes the
     * add, so the projection drops the card on its own.
     *
     * Deliberately does NOT record a local dismissal. That would clear the card a relay round-trip
     * sooner, but `dismissedChannelInvites` is keyed by channel id and persisted forever, so it would
     * also swallow a *later, legitimate* re-add to the same channel — the viewer would be put back in
     * and never told. Waiting for the relay's own withdrawal keeps "am I a member" answerable from the
     * events alone. Ignore is the action for "don't ask me again"; this one is for "take me out".
     */
    fun leaveChannelInvite(channel: RelayGroupChannel) =
        launchSigner {
            account.relayGroups.leaveRelayGroup(channel)
        }

    /**
     * Drop a Concord community from this account's private kind-13302 list. Fire-and-forget on the
     * signer dispatcher: the removal lands in the local cache (so the UI updates immediately) and the
     * new list event is best-effort published to our outbox. Nothing here waits on a relay, which is
     * what makes leaving a community whose own relays are dead work at all — the list lives in *our*
     * outbox, not in the community's relays.
     */
    fun leaveConcordCommunity(communityId: String) = launchSigner { account.concord.leaveConcordCommunity(communityId) }

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
        hashtags: List<String>,
        geohashes: List<String>,
    ) = launchSigner {
        account.relayGroups.createRelayGroup(
            relay,
            groupId,
            name,
            about,
            picture,
            isPrivate,
            isClosed,
            isHidden,
            isRestricted,
            hashtags,
            geohashes,
        )
    }

    fun createRelayGroupInvite(
        channel: RelayGroupChannel,
        code: String,
    ) = launchSigner { account.relayGroups.createRelayGroupInvite(channel, code) }

    fun postRelayGroupThread(
        channel: RelayGroupChannel,
        title: String,
        body: String,
    ) = launchSigner { account.relayGroups.postRelayGroupThread(channel, title, body) }

    fun pinRelayGroupMessage(
        channel: RelayGroupChannel,
        note: Note,
    ) = launchSigner { account.relayGroups.pinRelayGroupMessage(channel, note) }

    fun unpinRelayGroupMessage(
        channel: RelayGroupChannel,
        note: Note,
    ) = launchSigner { account.relayGroups.unpinRelayGroupMessage(channel, note) }

    fun removeRelayGroupUser(
        channel: RelayGroupChannel,
        pubkey: HexKey,
    ) = launchSigner { account.relayGroups.removeRelayGroupUser(channel, pubkey) }

    fun putRelayGroupUser(
        channel: RelayGroupChannel,
        pubkey: HexKey,
        roles: List<String>,
    ) = launchSigner { account.relayGroups.putRelayGroupUser(channel, pubkey, roles) }

    /** Add [pubkey] to a Buzz community (relay-wide, kind 9030). Owner/admin only; relay enforces. */
    fun addCommunityMember(
        relay: NormalizedRelayUrl,
        pubkey: HexKey,
        role: String? = null,
    ) = launchSigner { account.relayGroups.addCommunityMember(relay, pubkey, role) }

    /** Remove [pubkey] from a Buzz community (relay-wide, kind 9031). Owner/admin only. */
    fun removeCommunityMember(
        relay: NormalizedRelayUrl,
        pubkey: HexKey,
    ) = launchSigner { account.relayGroups.removeCommunityMember(relay, pubkey) }

    fun editRelayGroupMetadata(
        channel: RelayGroupChannel,
        name: String?,
        about: String?,
        picture: String?,
        isPrivate: Boolean,
        isClosed: Boolean,
        isHidden: Boolean,
        isRestricted: Boolean,
        hashtags: List<String>,
        geohashes: List<String>,
    ) = launchSigner {
        account.relayGroups.editRelayGroupMetadata(
            channel,
            name,
            about,
            picture,
            isPrivate,
            isClosed,
            isHidden,
            isRestricted,
            hashtags,
            geohashes,
        )
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

    fun followFavoriteFollowSet(followSet: AddressBookmark) = launchSigner { account.followFavoriteFollowSet(followSet) }

    fun unfollowFavoriteFollowSet(followSet: Address) = launchSigner { account.unfollowFavoriteFollowSet(followSet) }

    fun refreshFavoriteAlgoFeed(dvm: Address) = account.favoriteAlgoFeedsOrchestrator.refresh(dvm)

    fun followRelayFeed(url: NormalizedRelayUrl) = launchSigner { account.followRelayFeed(url) }

    fun unfollowRelayFeed(url: NormalizedRelayUrl) = launchSigner { account.unfollowRelayFeed(url) }

    fun showWord(word: String) = launchSigner { account.showWord(word) }

    fun hideWord(word: String) = launchSigner { account.hideWord(word) }

    fun hideHashtag(tag: String) = launchSigner { account.hideHashtag(tag) }

    fun showHashtag(tag: String) = launchSigner { account.showHashtag(tag) }

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

    fun updatePowDifficulty(difficulty: Int) = launchSigner { account.updatePowDifficulty(difficulty) }

    fun updatePowCategory(
        category: PoWCategory,
        enabled: Boolean,
    ) = launchSigner { account.updatePowCategory(category, enabled) }

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

    fun changeReactionRowItems(items: List<ReactionRowItem>) =
        launchSigner {
            account.changeReactionRowItems(items)
        }

    fun videoPlayerButtonItemsFlow() = account.settings.syncedSettings.videoPlayer.buttonItems

    fun captionsEnabledFlow() = account.settings.syncedSettings.videoPlayer.captionsEnabled

    fun setCaptionsEnabled(enabled: Boolean) {
        viewModelScope.launch { account.changeCaptionsEnabled(enabled) }
    }

    fun changeVideoPlayerButtonItems(items: List<VideoPlayerButtonItem>) =
        launchSigner {
            account.changeVideoPlayerButtonItems(items)
        }

    fun audioVisualizerFlow(): StateFlow<VisualizerStyle> = account.settings.syncedSettings.media.audioVisualizer

    fun changeAudioVisualizer(style: VisualizerStyle) =
        launchSigner {
            account.changeAudioVisualizer(style)
        }

    fun bottomBarItemsFlow(): StateFlow<List<BottomBarEntry>> = account.settings.syncedSettings.navigation.bottomBarItems

    fun hiddenDrawerItemsFlow(): StateFlow<Set<NavBarItem>> = account.settings.syncedSettings.navigation.hiddenDrawerItems

    /** Same ordering contract as [changeBottomBarItems]: apply on the caller's thread, publish off it. */
    fun changeHiddenDrawerItems(items: Set<NavBarItem>) {
        if (account.applyHiddenDrawerItems(items)) {
            pickerPublisher.schedule()
        }
    }

    fun changeBottomBarItems(items: List<BottomBarEntry>) {
        // Apply to the reactive flow synchronously on the caller (UI) thread so rapid edits stay
        // ordered — launchSigner dispatches on a multi-threaded pool, so wrapping the emit too would
        // let two quick edits complete out of order and revert the newer one (the settings screen
        // re-seeds its editable list from this flow). Only the sign + encrypt + publish runs off-thread,
        // and that part is debounced — see [pickerPublisher].
        if (account.applyBottomBarItems(items)) {
            pickerPublisher.schedule()
        }
    }

    /**
     * Publishes the account's synced settings once the picker edits stop, rather than once per toggle.
     *
     * The Side Menu and bottom-bar pickers are the only settings surfaces that write in bursts — a
     * configuring session is a run of discrete toggles over ~50 rows, and each one signs, encrypts
     * and publishes the whole blob to every outbox relay, which on an external signer is an IPC
     * round trip (and possibly a prompt) per switch. One publisher for both, since they edit the same
     * app-specific data event and a burst that crosses the two should still collapse.
     *
     * Only these two. Every other synced setting changes one at a time, and the published event is
     * its only durable copy, so delaying those would buy nothing and cost durability.
     *
     * On the account's scope, NOT [viewModelScope]. A synced setting's only durable copy is the
     * published event, so an edit waiting out the debounce must survive this ViewModel: AndroidX
     * closes [viewModelScope] *before* it calls [onCleared] (`ViewModel.clear()` closes the keyed
     * closeables, and `viewModelScope` is one of them), so a pending publish tied to it would already
     * be cancelled by the time any teardown hook here could notice — and no hook could rescue it.
     * The account's scope is cancelled only when the account is removed, so the publish simply
     * completes across an account switch or an Activity teardown.
     */
    private val pickerPublisher =
        DebouncedPublisher(
            debounceMs = PICKER_PUBLISH_DEBOUNCE_MS,
            launch = { block -> account.scope.launch(Dispatchers.IO) { reportSignerErrors { block() } } },
            publish = { account.sendNewAppSpecificData() },
        )

    /**
     * Publish any pending picker edits now. Called when a picker screen leaves the composition or the
     * app stops, so an edit is never left sitting only in memory: synced settings have no local copy
     * of their own — [com.vitorpamplona.amethyst.commons.model.AccountSettings.backupAppSpecificData], written
     * when the published event comes back through the collector, *is* the local copy.
     */
    fun flushPickerPublish() = pickerPublisher.flush()

    fun pinnedChatroomsFlow(): StateFlow<Set<ChatroomKey>> = account.settings.syncedSettings.chats.pinnedChatrooms

    fun toggleChatroomPin(room: ChatroomKey) =
        launchSigner {
            account.toggleChatroomPin(room)
        }

    fun mutedPublicChatsFlow(): StateFlow<Set<String>> = account.settings.mutedPublicChats

    fun toggleMutedPublicChat(channelId: String) =
        launchSigner {
            account.toggleMutedPublicChat(channelId)
        }

    fun updateZapAmounts(
        amountSet: List<Long>,
        selectedZapType: ZapReceiptEvent.ZapType,
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

    fun updateUserAssertionPetName(
        user: User,
        petName: String?,
        summary: String?,
    ) = launchSigner { account.updateUserAssertionPetName(user.pubkeyHex, petName, summary) }

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

    fun loadReactionTo(note: Note?): String? {
        if (note == null) return null

        return note.getReactionBy(userProfile())
    }

    fun runOnIO(runOnIO: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { runOnIO() }
    }

    fun checkGetOrCreateUser(key: HexKey): User? = LocalCache.checkGetOrCreateUser(key)

    override fun getOrCreateUser(pubkey: HexKey): User = LocalCache.getOrCreateUser(pubkey)

    fun getUserIfExists(hex: HexKey): User? = LocalCache.getUserIfExists(hex)

    fun checkGetOrCreateNote(key: HexKey): Note? = LocalCache.checkGetOrCreateNote(key)

    override fun getOrCreateNote(hex: HexKey): Note = LocalCache.getOrCreateNote(hex)

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

    private fun dismissTrayNotificationFor(eventId: HexKey) = host.dismissNotificationFor(eventId)

    fun markAllChatNotesAsRead(notes: List<Note>) {
        viewModelScope.launch(Dispatchers.IO) {
            // markRoomNoteAsRead resolves each row's last-read route the same way ChatroomEntry does,
            // so every room kind shown on the Messages screen — public chats, DMs, NIP-29 relay groups,
            // Concord, Marmot, geohash, ephemeral, and the collapsed per-server rows — is covered.
            notes.forEach { markRoomNoteAsRead(account, it) }

            markHiddenChatroomsAsRead()
        }
    }

    private fun markHiddenChatroomsAsRead() {
        account.chatroomList.rooms.forEach { roomKey, chatroom ->
            if (account.isAllHidden(roomKey.users)) {
                chatroom.newestMessage?.createdAt()?.let {
                    account.markAsRead(privateChatLastReadRoute(roomKey), it)
                }
            }
        }
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

    /**
     * Re-runs the send behind a chat bubble that failed before reaching the
     * relay pool. The action was registered by the send path itself
     * ([com.vitorpamplona.amethyst.commons.relayClient.chatDelivery.ChatDeliveryTracker.markSending]),
     * so this works for every chat surface without the UI knowing which one it
     * is looking at. A message old enough to have fallen out of the tracked
     * window has no action left and the tap is a no-op.
     */
    fun retryChatSend(displayedNoteId: HexKey) {
        val retry = account.chatDeliveryTracker.retryFor(displayedNoteId) ?: return
        // On the account scope, not the ViewModel's: a retry re-runs the whole
        // send inline, and leaving the screen must not abandon it half-done.
        account.scope.launch(Dispatchers.IO) {
            try {
                retry()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("AccountViewModel", "Retry of chat send $displayedNoteId failed", e)
            }
        }
    }

    // --- Marmot Group Messaging ---

    /**
     * Post a Marmot group message and return as soon as it is on screen.
     *
     * Only the cheap half runs here: mention rewriting and the inner kind:9
     * rumor, neither of which touches MLS. The bubble goes up immediately and
     * everything expensive — the ratchet step, the encrypted group-state
     * write, the outer wrap, the relay hand-off — runs on the account scope
     * afterwards. Deliberately NOT the caller's scope: the composer's
     * [androidx.compose.runtime.rememberCoroutineScope] dies when the user
     * navigates away, which would abandon a send that is already showing as
     * sent.
     */
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
        val manager = account.marmotManager ?: return
        val innerEvent =
            manager.buildTextRumor(
                text = tagger.message,
                replyToEventId = replyToInnerEventId,
                replyToAuthorPubKey = replyToInnerAuthorPubKey,
                mentions = tagger.pTags?.map { it.toPTag() } ?: emptyList(),
            )
        deliverMarmotGroupMessage(nostrGroupId, innerEvent)
    }

    /**
     * Replace the text of my own Marmot message [target] with a kind:1009 edit. The edit
     * overlays the original everywhere it is shown (here, and in White Noise), and goes
     * through the same show-then-publish path as a new message, so a failed send shows on
     * the edit's delivery state rather than silently.
     */
    suspend fun sendMarmotGroupMessageEdit(
        nostrGroupId: String,
        target: Note,
        text: String,
    ) {
        val tagger = NewMessageTagger(text, null, null, this)
        tagger.run()
        val manager = account.marmotManager ?: return
        deliverMarmotGroupMessage(nostrGroupId, manager.buildMessageEditRumor(target.idHex, tagger.message))
    }

    /**
     * Show [innerEvent] in the group's chat now and publish it on the account
     * scope. Shared by every Marmot send that originates in the UI.
     */
    private fun deliverMarmotGroupMessage(
        nostrGroupId: String,
        innerEvent: Event,
    ) {
        account.marmot.beginMarmotGroupMessage(nostrGroupId, innerEvent)
        account.scope.launch(Dispatchers.IO) {
            try {
                account.marmot.sendMarmotGroupMessage(
                    nostrGroupId,
                    innerEvent,
                    account.marmot.marmotGroupRelays(nostrGroupId),
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // The bubble already carries the failure and its retry; this
                // is only here so one bad send cannot take the scope down.
                Log.w("AccountViewModel", "Marmot send failed for $nostrGroupId", e)
            }
        }
    }

    suspend fun sendMarmotGroupMediaMessage(
        nostrGroupId: String,
        url: String,
        imeta: IMetaTag,
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
            RumorAssembler
                .assembleRumor<Event>(
                    account.signer.pubKey,
                    template,
                )
        deliverMarmotGroupMessage(nostrGroupId, innerEvent)
    }

    fun marmotMediaExporterSecret(nostrGroupId: String): ByteArray? = account.marmotManager?.mediaExporterSecret(nostrGroupId)

    /**
     * True when this group carries the `encrypted-media-v2` policy (`0x800b`)
     * and a sender should therefore produce v2 references.
     *
     * A group without it is not a licence to reinterpret the frozen v1 policy
     * at `0x8008` as v2 — they are different components — so this is a plain
     * "does the group say v2", and the sender falls back to MIP-04 when it
     * does not.
     */
    fun marmotUsesEncryptedMediaV2(nostrGroupId: String): Boolean = account.marmotManager?.encryptedMediaPolicy(nostrGroupId) != null

    /** True when this account has somewhere to upload a group's encrypted media. */
    fun hasBlossomServers(): Boolean = account.marmot.marmotMediaPolicyServers().isNotEmpty()

    /**
     * On the account scope, not the screen's: the reset drops local state, flags the group as
     * awaiting a re-invite and publishes a KeyPackage, and leaving the chat mid-way must not
     * stop it between those steps.
     */
    fun resetOutOfSyncMarmotGroup(nostrGroupId: String) {
        account.scope.launch(Dispatchers.IO) { account.marmot.resetOutOfSyncMarmotGroup(nostrGroupId) }
    }

    suspend fun enableMarmotEncryptedMediaV2(nostrGroupId: String) {
        account.marmot.enableMarmotEncryptedMediaV2(nostrGroupId)
    }

    /** Post the kind:9 carrying an `encrypted-media-v2` attachment. */
    suspend fun sendMarmotGroupEncryptedMediaV2(
        nostrGroupId: String,
        reference: EncryptedMediaReferenceV2,
        caption: String,
    ) {
        val manager = account.marmotManager ?: return
        deliverMarmotGroupMessage(nostrGroupId, manager.buildMediaRumor(reference, caption))
    }

    suspend fun createMarmotGroup(
        nostrGroupId: String,
        name: String = "",
        description: String = "",
        disappearingMessageSecs: ULong? = null,
    ) {
        account.marmot.createMarmotGroup(nostrGroupId, name, description, disappearingMessageSecs)
    }

    /** This group's disappearing-message duration in seconds; 0 is off. */
    fun marmotRetentionSeconds(nostrGroupId: String): Long = account.marmotManager?.retentionSeconds(nostrGroupId) ?: 0L

    suspend fun publishMarmotKeyPackage() {
        account.marmot.publishMarmotKeyPackage()
    }

    suspend fun hasPublishedKeyPackage(): Boolean = account.marmot.hasPublishedKeyPackage()

    /**
     * Which install currently owns this account's Marmot invites. See [LatestKeyPackageOwner].
     *
     * [maxAgeSeconds] lets a passive caller reuse a recent answer instead of
     * fanning a REQ across the write set; 0 always asks the relays.
     */
    suspend fun latestKeyPackageOwner(maxAgeSeconds: Long = 0L): LatestKeyPackageOwner = account.marmot.latestKeyPackageOwner(maxAgeSeconds)

    /** Republishes this device's KeyPackage; true only when a relay accepted it. */
    suspend fun republishKeyPackage(): Boolean = account.marmot.republishKeyPackageConfirmed()

    /** False for a read-only (pubkey-only) login, which cannot publish at all. */
    fun canPublish(): Boolean = account.isWriteable()

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
        val relays = account.marmot.marmotGroupRelays(nostrGroupId)
        account.marmot.leaveMarmotGroup(nostrGroupId, relays)
    }

    /**
     * Disband the group for everyone. Irreversible — the caller is responsible
     * for confirming with the user before this is reached.
     *
     * @return true when the group is terminal now, false when the request is
     *   still pending convergence, which is not a failure.
     */
    suspend fun disbandMarmotGroup(nostrGroupId: String): Boolean {
        val relays = account.marmot.marmotGroupRelays(nostrGroupId)
        return account.marmot.disbandMarmotGroup(nostrGroupId, relays)
    }

    /** Set (or, with a blank string, clear) the group's plain-https avatar link. */
    suspend fun setMarmotGroupAvatarUrl(
        nostrGroupId: String,
        url: String,
    ) {
        val relays = account.marmot.marmotGroupRelays(nostrGroupId)
        account.marmot.setMarmotGroupAvatarUrl(nostrGroupId, url, relays)
    }

    suspend fun resetMarmotState() {
        account.marmot.resetMarmotState()
    }

    fun marmotGroupMembers(nostrGroupId: String): List<GroupMemberInfo> = account.marmotManager?.memberPubkeys(nostrGroupId) ?: emptyList()

    suspend fun addMarmotGroupMember(
        nostrGroupId: String,
        memberPubKey: String,
    ): String = account.marmot.fetchKeyPackageAndAddMember(nostrGroupId, memberPubKey)

    suspend fun removeMarmotGroupMember(
        nostrGroupId: String,
        targetLeafIndex: Int,
    ) {
        val relays = account.marmot.marmotGroupRelays(nostrGroupId)
        account.marmot.removeMarmotGroupMember(nostrGroupId, targetLeafIndex, relays)
    }

    suspend fun grantMarmotGroupAdmin(
        nostrGroupId: String,
        targetPubKey: String,
    ) {
        val relays = account.marmot.marmotGroupRelays(nostrGroupId)
        account.marmot.grantMarmotGroupAdmin(nostrGroupId, targetPubKey, relays)
    }

    suspend fun revokeMarmotGroupAdmin(
        nostrGroupId: String,
        targetPubKey: String,
    ) {
        val relays = account.marmot.marmotGroupRelays(nostrGroupId)
        account.marmot.revokeMarmotGroupAdmin(nostrGroupId, targetPubKey, relays)
    }

    suspend fun updateMarmotGroupMetadata(
        nostrGroupId: String,
        name: String,
        description: String,
        icon: MarmotGroupIconChange = MarmotGroupIconChange.Keep,
    ) {
        // Stamp the inviter's outbox relays into the group metadata so that
        // every member ends up with a single canonical relay set for kind:445
        // GroupEvents. Without this, both the inviter and the invitee fall
        // back to their *own* home/outbox relays — which usually do not
        // overlap, so kind:445 messages never reach the other side. The
        // welcome carries the metadata, so the invitee learns the relays at
        // join time.
        val manager = account.marmotManager ?: return
        val relays = account.marmot.marmotGroupRelays(nostrGroupId)

        manager.setGroupProfile(nostrGroupId, name, description, relays.toList())
        when (icon) {
            is MarmotGroupIconChange.Keep -> Unit
            is MarmotGroupIconChange.Clear -> manager.setGroupImage(nostrGroupId, null, relays.toList())
            is MarmotGroupIconChange.Set ->
                manager.setGroupImage(
                    nostrGroupId,
                    GroupBlossomImageV1(
                        imageHash = icon.upload.imageHash.hexToByteArray(),
                        imageKey = icon.upload.imageKey,
                        imageNonce = icon.upload.imageNonce,
                        imageUploadKey = icon.upload.imageUploadKey,
                        mediaType = icon.upload.mediaType,
                    ),
                    relays.toList(),
                )
        }
        // The commits are canonical once published. Surface them now: a freshly created
        // group otherwise sat at "0 members" with no name until our own echo or a restart.
        account.marmot.syncAndNotify(nostrGroupId)
    }

    override fun onCleared() {
        Log.d("AccountViewModel", "onCleared")
        // Deliberately does NOT touch the call. This runs whenever MainActivity is destroyed —
        // including while a call is up, because Android reclaims the backgrounded MainActivity
        // (notably right after CallActivity enters picture-in-picture). Only the ViewModel
        // reference is dropped; the call itself is account-scoped and keeps running.
        // Real logout / account switch tears the call down via CallSessionBridge.clear(),
        // called from AccountSessionManager alongside NestBridge.clear().
        CallSessionBridge
            .clearViewModel()
        NestBridge
            .clear()
        feedStates.destroy()
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
                Res.string.draft_note,
                Res.string.it_s_not_possible_to_quote_to_a_draft_note,
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
                Res.string.read_only_user,
                Res.string.login_with_a_private_key_to_be_able_to_boost_posts,
            )
        }
    }

    fun meltCashu(
        token: CashuToken,
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
                    val meltResult =
                        MeltProcessor().melt(
                            token,
                            lud16,
                            account.cashuMintTransport,
                            host.lnurlTransport,
                            // Mints the user deliberately added are exempt from the
                            // private-address block (self-hosted LAN mints are legit).
                            knownWalletMints =
                                account.cashuWalletState.mints.value
                                    .toSet(),
                        )
                    onDone(
                        loadStringRes(Res.string.cashu_successful_redemption),
                        loadStringRes(
                            Res.string.cashu_successful_redemption_explainer,
                            token.totalAmount.toString(),
                            meltResult.fees.toString(),
                        ),
                    )
                } catch (e: LightningInvoiceResolver.LightningAddressError) {
                    onDone(e.title, e.msg)
                } catch (e: Exception) {
                    if (e is kotlin.coroutines.cancellation.CancellationException) throw e
                    onDone(
                        loadStringRes(Res.string.cashu_failed_redemption),
                        loadStringRes(Res.string.cashu_failed_redemption_explainer_error_msg, e.message),
                    )
                }
            }
        } else {
            viewModelScope.launch {
                onDone(
                    loadStringRes(Res.string.no_lightning_address_set),
                    loadStringRes(
                        Res.string.user_x_does_not_have_a_lightning_address_setup_to_receive_sats,
                        account.userProfile().toBestDisplayName(),
                    ),
                )
            }
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

    private suspend fun unwrapSeal(event: SealEvent): Note? {
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
            is SealEvent -> unwrapSeal(event)
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
                        noteEvent is DvmContentDiscoveryResponseEvent &&
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
        onTimeout: () -> Unit = {},
        metadata: Map<String, Any?>? = null,
        onResponse: (Response?) -> Unit,
    ) = launchSigner {
        account.zaps.sendZapPaymentRequestFor(bolt11, zappedNote, onTimeout, metadata, onResponse)
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
        val response = ClinkDebitPayer.payInvoice(account, host.moneyOpRelays, pointer, bolt11)
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
        zapType: ZapReceiptEvent.ZapType? = null,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val effectiveZapType = zapType ?: defaultZapType()
                val zapRequest =
                    if (effectiveZapType != ZapReceiptEvent.ZapType.NONZAP) {
                        // NIP-57 Appendix F: include amount + lnurl so the receipt can be validated.
                        val splitLnurl = LnurlForm.toUrl(lnAddress)?.let(LnurlForm::urlToBech32)
                        account.zaps.createZapRequestFor(
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
                    LightningInvoiceResolver(host.lnurlTransport).lnAddressInvoice(
                        lnAddress = lnAddress,
                        milliSats = milliSats,
                        message = message,
                        nostrRequest = zapRequest,
                        onProgress = onProgress,
                    )

                // Delivered on Main: every composer's onNewInvoice writes the invoice into
                // its message TextFieldState, which is UI-thread confined (see the KDoc on
                // commons' `onUiThread`). This whole block runs on Dispatchers.IO.
                withContext(Dispatchers.Main) { onNewInvoice(invoice) }
            } catch (e: LightningInvoiceResolver.LightningAddressError) {
                onError(e.title, e.msg)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                onError("Error", e.message ?: "Unknown error")
            }
        }
    }

    val trustedAccounts: StateFlow<Set<HexKey>> =
        host.savedAccounts.stateIn(
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
