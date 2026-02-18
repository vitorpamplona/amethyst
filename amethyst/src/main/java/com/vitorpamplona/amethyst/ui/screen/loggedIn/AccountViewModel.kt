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
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import com.vitorpamplona.amethyst.AccountInfo
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.compose.GenericBaseCache
import com.vitorpamplona.amethyst.commons.compose.GenericBaseCacheAsync
import com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.commons.model.observables.CreatedAtComparator
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.commons.ui.notifications.CardFeedState
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
import com.vitorpamplona.amethyst.service.OnlineChecker
import com.vitorpamplona.amethyst.service.ZapPaymentHandler
import com.vitorpamplona.amethyst.service.broadcast.BroadcastTracker
import com.vitorpamplona.amethyst.service.cashu.CashuToken
import com.vitorpamplona.amethyst.service.cashu.melt.MeltProcessor
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.lnurl.LightningAddressResolver
import com.vitorpamplona.amethyst.service.location.LocationState
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.RelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.nwc.NWCPaymentFilterAssembler
import com.vitorpamplona.amethyst.ui.actions.Dao
import com.vitorpamplona.amethyst.ui.actions.MediaSaverToDisk
import com.vitorpamplona.amethyst.ui.components.UrlPreviewState
import com.vitorpamplona.amethyst.ui.components.toasts.ToastManager
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.ZapAmountCommentNotification
import com.vitorpamplona.amethyst.ui.note.ZapraiserStatus
import com.vitorpamplona.amethyst.ui.note.showAmount
import com.vitorpamplona.amethyst.ui.note.showAmountInteger
import com.vitorpamplona.amethyst.ui.screen.UiSettingsState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.CombinedZap
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.tor.TorSettingsFlow
import com.vitorpamplona.amethyst.ui.tor.TorType
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
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayOfflineTracker
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.EmptyIAuthStatus
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip01Core.tags.people.PubKeyReferenceTag
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip03Timestamp.EmptyOtsResolverBuilder
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
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
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip47WalletConnect.Response
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.hashtagList.HashtagListEvent
import com.vitorpamplona.quartz.nip56Reports.ReportType
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiserAmount
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90ContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.util.Locale

@Stable
class AccountViewModel(
    val account: Account,
    val settings: UiSettingsState,
    val torSettings: TorSettingsFlow,
    val dataSources: RelaySubscriptionsCoordinator,
    val httpClientBuilder: IRoleBasedHttpClientBuilder,
) : ViewModel(),
    Dao {
    var firstRoute: Route? = null

    val toastManager = ToastManager()
    val broadcastTracker = BroadcastTracker()
    val feedStates = AccountFeedContentStates(account, viewModelScope)

    val tempManualPaymentCache = LruCache<String, List<ZapPaymentHandler.Payable>>(5)

    @OptIn(ExperimentalCoroutinesApi::class)
    val notificationHasNewItems =
        combineTransform(
            account.loadLastReadFlow("Notification"),
            feedStates.notifications.feedContent
                .flatMapLatest {
                    if (it is CardFeedState.Loaded) {
                        it.feed
                    } else {
                        MutableStateFlow(null)
                    }
                }.map { it?.list?.firstOrNull()?.createdAt() },
        ) { lastRead, newestItemCreatedAt ->
            emit(newestItemCreatedAt != null && newestItemCreatedAt > lastRead)
        }.onStart {
            val lastRead = account.loadLastReadFlow("Notification").value
            val cards = feedStates.notifications.feedContent.value
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
                        (chat.event as? ChatroomKeyable)?.let { event ->
                            val room = event.chatroomKey(account.signer.pubKey)
                            account.settings.getLastReadFlow("Room/${room.hashCode()}").map { lastReadAt ->
                                (chat.event?.createdAt ?: 0) > lastReadAt
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
                            (chat.event as? ChatroomKeyable)?.let { event ->
                                val room = event.chatroomKey(account.signer.pubKey)
                                val lastReadAt =
                                    account.settings.lastReadPerRoute.value["Room/${room.hashCode()}"]
                                        ?.value ?: 0L
                                (chat.event?.createdAt ?: 0) > lastReadAt
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
    val homeHasNewItems =
        combineTransform(
            account.loadLastReadFlow("HomeFollows"),
            feedStates.homeNewThreads.feedContent
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
        }.onStart {
            val lastRead = account.loadLastReadFlow("HomeFollows").value
            val feed = feedStates.homeNewThreads.feedContent.value
            if (feed is FeedState.Loaded) {
                val newestItemCreatedAt =
                    feed.feed.value.list
                        .firstOrNull { it.event != null && it.event !is GenericRepostEvent && it.event !is RepostEvent }
                        ?.createdAt()
                emit(newestItemCreatedAt != null && newestItemCreatedAt > lastRead)
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
            Route.Notification to notificationHasNewItemsFlow,
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
                account.delete(currentReactions)
            } else {
                if (settings.isCompleteUIMode()) {
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
            NoteComposeReportState(isPostHidden, false, false, isHiddenAuthor)
        } else if (isFromLoggedIn || isFromLoggedInFollow) {
            // No need to process if from trusted people
            NoteComposeReportState(isPostHidden, true, true, isHiddenAuthor)
        } else {
            val newCanPreview = !note.hasAnyReports()

            val newIsAcceptable = account.isAcceptable(note)

            if (newCanPreview && newIsAcceptable) {
                // No need to process reports if nothing is wrong
                NoteComposeReportState(isPostHidden, true, true, false)
            } else {
                NoteComposeReportState(
                    isPostHidden,
                    newIsAcceptable,
                    newCanPreview,
                    false,
                    account.getRelevantReports(note).toImmutableSet(),
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
                note.flow().reports.stateFlow,
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

    suspend fun calculateZapAmount(zappedNote: Note): String =
        if (zappedNote.zapPayments.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                val it = account.calculateZappedAmount(zappedNote)
                showAmount(it)
            }
        } else {
            showAmount(zappedNote.zapsAmount)
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
                            )
                    }.toMutableMap()

            val results =
                mapNotNullAsync<CombinedZap, DecryptedInfo>(
                    zaps.filter { (it.request.event as? LnZapRequestEvent)?.isPrivateZap() == true },
                ) { next ->
                    val info = innerDecryptAmountMessage(next.request, next.response)
                    if (info != null) {
                        DecryptedInfo(next.request, next.response, info)
                    } else {
                        null
                    }
                }

            results.forEach { decrypted -> initialResults[decrypted.zapRequest] = decrypted.info }

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
                        )
                    } else {
                        ZapAmountCommentNotification(
                            it.request.author,
                            it.request.event
                                ?.content
                                ?.ifBlank { null },
                            showAmountInteger((it.response.event as? LnZapEvent)?.amount),
                        )
                    }
                } else {
                    ZapAmountCommentNotification(
                        it.request.author,
                        it.request.event
                            ?.content
                            ?.ifBlank { null },
                        showAmountInteger((it.response.event as? LnZapEvent)?.amount),
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
                        )
                    } else {
                        ZapAmountCommentNotification(
                            it.first.author,
                            it.first.event
                                ?.content
                                ?.ifBlank { null },
                            showAmountInteger((it.second?.event as? LnZapEvent)?.amount),
                        )
                    }
                } else {
                    ZapAmountCommentNotification(
                        it.first.author,
                        it.first.event
                            ?.content
                            ?.ifBlank { null },
                        showAmountInteger((it.second?.event as? LnZapEvent)?.amount),
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
                            )
                    }.toMutableMap()

            val decryptedInfo =
                mapNotNullAsync(myList) { next ->
                    val info = innerDecryptAmountMessage(next.first, next.second)
                    if (info != null) {
                        DecryptedInfo(next.first, next.second, info)
                    } else {
                        null
                    }
                }

            decryptedInfo.forEach { decrypted -> initialResults[decrypted.zapRequest] = decrypted.info }

            onNewState(initialResults.values.toImmutableList())
        }
    }

    fun decryptAmountMessage(
        zapRequest: Note,
        zapEvent: Note?,
        onNewState: (ZapAmountCommentNotification?) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            onNewState(innerDecryptAmountMessage(zapRequest, zapEvent))
        }
    }

    private suspend fun innerDecryptAmountMessage(
        zapRequest: Note,
        zapEvent: Note?,
    ): ZapAmountCommentNotification? =
        (zapRequest.event as? LnZapRequestEvent)?.let {
            val amount = showAmountInteger((zapEvent?.event as? LnZapEvent)?.amount)
            if (it.isPrivateZap()) {
                val decryptedContent = account.decryptZapOrNull(it)
                if (decryptedContent != null) {
                    ZapAmountCommentNotification(
                        LocalCache.checkGetOrCreateUser(decryptedContent.pubKey),
                        decryptedContent.content.ifBlank { null },
                        amount,
                    )
                } else {
                    ZapAmountCommentNotification(
                        zapRequest.author,
                        null,
                        amount,
                    )
                }
            } else {
                ZapAmountCommentNotification(
                    zapRequest.author,
                    zapRequest.event?.content?.ifBlank { null },
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
            zapType = zapType ?: defaultZapType(),
        )
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

    fun boost(note: Note) {
        if (settings.isCompleteUIMode()) {
            // Tracked broadcasting with progress feedback
            launchSigner {
                account.createBoostEvent(note)?.let { (event, relays) ->
                    broadcastTracker.trackBroadcast(
                        event = event,
                        relays = relays,
                        client = account.client,
                    )
                    account.consumeBoostEvent(event)
                }
            }
        } else {
            // Fire-and-forget (original behavior)
            launchSigner { account.boost(note) }
        }
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
    ) = launchSigner { account.addToGallery(hex, url, relay, blurhash, dim, hash, mimeType) }

    fun removeFromMediaGallery(note: Note) = launchSigner { account.removeFromGallery(note) }

    fun follows(user: User): Note = LocalCache.getOrCreateAddressableNote(ContactListEvent.createAddress(user.pubkeyHex))

    fun hashtagFollows(user: User): Note = LocalCache.getOrCreateAddressableNote(HashtagListEvent.createAddress(user.pubkeyHex))

    fun bookmarks(user: User): Note = LocalCache.getOrCreateAddressableNote(BookmarkListEvent.createBookmarkAddress(user.pubkeyHex))

    fun addPrivateBookmark(note: Note) {
        if (settings.isCompleteUIMode()) {
            launchSigner {
                account.createAddBookmarkEvent(note, true)?.let { (event, relays) ->
                    broadcastTracker.trackBroadcast(
                        event = event,
                        relays = relays,
                        client = account.client,
                    )
                    account.consumeBookmarkEvent(event)
                }
            }
        } else {
            launchSigner { account.addBookmark(note, true) }
        }
    }

    fun addPublicBookmark(note: Note) {
        if (settings.isCompleteUIMode()) {
            launchSigner {
                account.createAddBookmarkEvent(note, false)?.let { (event, relays) ->
                    broadcastTracker.trackBroadcast(
                        event = event,
                        relays = relays,
                        client = account.client,
                    )
                    account.consumeBookmarkEvent(event)
                }
            }
        } else {
            launchSigner { account.addBookmark(note, false) }
        }
    }

    fun removePrivateBookmark(note: Note) {
        if (settings.isCompleteUIMode()) {
            launchSigner {
                account.createRemoveBookmarkEvent(note, true)?.let { (event, relays) ->
                    broadcastTracker.trackBroadcast(
                        event = event,
                        relays = relays,
                        client = account.client,
                    )

                    account.consumeBookmarkEvent(event)
                }
            }
        } else {
            launchSigner { account.removeBookmark(note, true) }
        }
    }

    fun removePublicBookmark(note: Note) {
        if (settings.isCompleteUIMode()) {
            launchSigner {
                account.createRemoveBookmarkEvent(note, false)?.let { (event, relays) ->
                    broadcastTracker.trackBroadcast(
                        event = event,
                        relays = relays,
                        client = account.client,
                    )
                    account.consumeBookmarkEvent(event)
                }
            }
        } else {
            launchSigner { account.removeBookmark(note, false) }
        }
    }

    fun broadcast(note: Note) = launchSigner { account.broadcast(note) }

    fun timestamp(note: Note) = launchSigner { account.otsState.timestamp(note) }

    fun delete(notes: List<Note>) = launchSigner { account.delete(notes) }

    fun delete(note: Note) = launchSigner { account.delete(note) }

    fun cachedDecrypt(note: Note): String? = account.cachedDecryptContent(note)

    fun decrypt(
        note: Note,
        onReady: (String) -> Unit,
    ) = launchSigner {
        account.decryptContent(note)?.let { onReady(it) }
    }

    inline fun launchSigner(crossinline action: suspend () -> Unit) {
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
            }
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

    fun follow(user: User) = launchSigner { account.follow(user) }

    fun unfollow(user: User) = launchSigner { account.unfollow(user) }

    fun followGeohash(tag: String) = launchSigner { account.followGeohash(tag) }

    fun unfollowGeohash(tag: String) = launchSigner { account.unfollowGeohash(tag) }

    fun followHashtag(tag: String) = launchSigner { account.followHashtag(tag) }

    fun unfollowHashtag(tag: String) = launchSigner { account.unfollowHashtag(tag) }

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

    fun dontTranslateFrom() = account.settings.syncedSettings.languages.dontTranslateFrom

    fun dontTranslateFromFilteredBySpokenLanguages() = account.settings.syncedSettings.dontTranslateFromFilteredBySpokenLanguages()

    fun translateTo() = account.settings.syncedSettings.languages.translateTo

    fun defaultZapType() = account.settings.syncedSettings.zaps.defaultZapType.value

    fun showSensitiveContent(): MutableStateFlow<Boolean?> = account.settings.syncedSettings.security.showSensitiveContent

    fun zapAmountChoicesFlow() = account.settings.syncedSettings.zaps.zapAmountChoices

    fun zapAmountChoices() = zapAmountChoicesFlow().value

    fun reactionChoicesFlow() = account.settings.syncedSettings.reactions.reactionChoices

    fun reactionChoices() = reactionChoicesFlow().value

    fun filterSpamFromStrangers() = account.settings.syncedSettings.security.filterSpamFromStrangers

    fun updateWarnReports(warnReports: Boolean) = launchSigner { account.updateWarnReports(warnReports) }

    fun updateFilterSpam(filterSpam: Boolean) =
        launchSigner {
            if (account.updateFilterSpam(filterSpam)) {
                LocalCache.antiSpam.active = filterSpamFromStrangers().value
            }
        }

    fun updateShowSensitiveContent(show: Boolean?) = launchSigner { account.updateShowSensitiveContent(show) }

    fun changeReactionTypes(
        reactionSet: List<String>,
        onDone: () -> Unit,
    ) = launchSigner {
        account.changeReactionTypes(reactionSet)
        onDone()
    }

    fun updateZapAmounts(
        amountSet: List<Long>,
        selectedZapType: LnZapEvent.ZapType,
        nip47Update: Nip47WalletConnect.Nip47URINorm?,
    ) = launchSigner { account.updateZapAmounts(amountSet, selectedZapType, nip47Update) }

    fun toggleDontTranslateFrom(languageCode: String) = launchSigner { account.toggleDontTranslateFrom(languageCode) }

    fun updateTranslateTo(languageCode: Locale) = launchSigner { account.updateTranslateTo(languageCode) }

    fun prefer(
        source: String,
        target: String,
        preference: String,
    ) = launchSigner { account.prefer(source, target, preference) }

    fun show(user: User) = launchSigner { account.showUser(user.pubkeyHex) }

    fun hide(user: User) = launchSigner { account.hideUser(user.pubkeyHex) }

    fun hide(word: String) = launchSigner { account.hideWord(word) }

    fun showUser(pubkeyHex: String) = launchSigner { account.showUser(pubkeyHex) }

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

    override suspend fun getOrCreateAddressableNote(address: Address): AddressableNote = LocalCache.getOrCreateAddressableNote(address)

    fun getAddressableNoteIfExists(key: String): AddressableNote? = LocalCache.getAddressableNoteIfExists(key)

    fun getAddressableNoteIfExists(key: Address): AddressableNote? = LocalCache.getAddressableNoteIfExists(key)

    fun cachedModificationEventsForNote(note: Note) = LocalCache.cachedModificationEventsForNote(note)

    suspend fun findModificationEventsForNote(note: Note): List<Note> =
        withContext(Dispatchers.IO) {
            LocalCache.findLatestModificationForNote(note)
        }

    fun checkGetOrCreatePublicChatChannel(key: HexKey): PublicChatChannel = LocalCache.getOrCreatePublicChatChannel(key)

    fun checkGetOrCreateLiveActivityChannel(key: Address): LiveActivitiesChannel = LocalCache.getOrCreateLiveChannel(key)

    fun checkGetOrCreateEphemeralChatChannel(key: RoomId): EphemeralChatChannel = LocalCache.getOrCreateEphemeralChannel(key)

    fun getPublicChatChannelIfExists(hex: HexKey) = LocalCache.getPublicChatChannelIfExists(hex)

    fun getEphemeralChatChannelIfExists(key: RoomId) = LocalCache.getEphemeralChatChannelIfExists(key)

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
    ): Boolean {
        if (createdAt == null) return false

        val lastTime = account.loadLastRead(routeForLastRead)

        val onIsNew = createdAt > lastTime

        if (onIsNew) {
            viewModelScope.launch(Dispatchers.IO) {
                account.markAsRead(routeForLastRead, createdAt)
            }
        }

        return onIsNew
    }

    fun markAllChatNotesAsRead(notes: List<Note>) {
        viewModelScope.launch(Dispatchers.IO) {
            for (note in notes) {
                val noteEvent = note.event
                if (noteEvent is IsInPublicChatChannel) {
                    account.markAsRead("Channel/${noteEvent.channelId()}", noteEvent.createdAt)
                } else if (noteEvent is ChatroomKeyable) {
                    account.markAsRead("Room/${noteEvent.chatroomKey(account.signer.pubKey).hashCode()}", noteEvent.createdAt)
                } else if (noteEvent is DraftWrapEvent) {
                    val innerEvent = account.draftsDecryptionCache.preCachedDraft(noteEvent)
                    if (innerEvent is IsInPublicChatChannel) {
                        account.markAsRead("Channel/${innerEvent.channelId()}", noteEvent.createdAt)
                    } else if (innerEvent is ChatroomKeyable) {
                        account.markAsRead("Room/${innerEvent.chatroomKey(account.signer.pubKey).hashCode()}", noteEvent.createdAt)
                    }
                }
            }
        }
    }

    class Factory(
        val account: Account,
        val settings: UiSettingsState,
        val torSettings: TorSettingsFlow,
        val dataSources: RelaySubscriptionsCoordinator,
        val okHttpClient: RoleBasedHttpClientBuilder,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AccountViewModel(
                account,
                settings,
                torSettings,
                dataSources,
                okHttpClient,
            ) as T
    }

    init {
        Log.d("Init", "AccountViewModel")
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

    override fun onCleared() {
        Log.d("Init", "AccountViewModel onCleared")
        feedStates.destroy()
        super.onCleared()
    }

    fun loadThumb(
        context: Context,
        thumbUri: String,
        onReady: (Drawable?) -> Unit,
        onError: (String?) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context).data(thumbUri).build()
                val myCover =
                    context.imageLoader
                        .execute(request)
                        .image
                        ?.asDrawable(context.resources)
                onReady(myCover)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("VideoView", "Fail to load cover $thumbUri", e)
                onError(e.message)
            }
        }
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
            account.requestDVMContentDiscovery(dvmPublicKey) {
                onReady(LocalCache.getOrCreateNote(it.id))
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
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val zapRequest =
                    if (defaultZapType() != LnZapEvent.ZapType.NONZAP) {
                        account.createZapRequestFor(user, message, defaultZapType())
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
                onSuccess = {
                    toastManager.toast(R.string.video_saved_to_the_gallery, R.string.video_saved_to_the_gallery)
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
                emptySet<HexKey>(),
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

    val client = EmptyNostrClient
    val authenticator = EmptyIAuthStatus

    val nwcFilters = NWCPaymentFilterAssembler(client)
    val failureTracker = RelayOfflineTracker(client)

    val account =
        Account(
            settings = AccountSettings(keyPair),
            signer = NostrSignerInternal(keyPair),
            geolocationFlow = MutableStateFlow<LocationState.LocationResult>(LocationState.LocationResult.Loading),
            nwcFilterAssembler = nwcFilters,
            otsResolverBuilder = EmptyOtsResolverBuilder,
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

    val client = EmptyNostrClient
    val authenticator = EmptyIAuthStatus

    val nwcFilters = NWCPaymentFilterAssembler(client)
    val failureTracker = RelayOfflineTracker(client)

    val account =
        Account(
            settings = AccountSettings(keyPair),
            signer = NostrSignerInternal(keyPair),
            geolocationFlow = MutableStateFlow<LocationState.LocationResult>(LocationState.LocationResult.Loading),
            nwcFilterAssembler = nwcFilters,
            otsResolverBuilder = EmptyOtsResolverBuilder,
            cache = LocalCache,
            client = EmptyNostrClient,
            scope = scope,
        )

    return AccountViewModel(
        account = account,
        settings = uiState,
        torSettings = TorSettingsFlow(torType = MutableStateFlow(TorType.OFF)),
        httpClientBuilder = EmptyRoleBasedHttpClientBuilder(),
        dataSources = RelaySubscriptionsCoordinator(LocalCache, client, authenticator, failureTracker, scope),
    ).also {
        vitorCache = it
    }
}
