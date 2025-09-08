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
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import com.vitorpamplona.amethyst.AccountInfo
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.compose.GenericBaseCache
import com.vitorpamplona.amethyst.commons.compose.GenericBaseCacheAsync
import com.vitorpamplona.amethyst.logTime
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.UrlCachedPreviewer
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.model.nip51Lists.HiddenUsersState
import com.vitorpamplona.amethyst.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.model.observables.CreatedAtComparator
import com.vitorpamplona.amethyst.service.Nip05NostrAddressVerifier
import com.vitorpamplona.amethyst.service.Nip11CachedRetriever
import com.vitorpamplona.amethyst.service.Nip11Retriever
import com.vitorpamplona.amethyst.service.OnlineChecker
import com.vitorpamplona.amethyst.service.ZapPaymentHandler
import com.vitorpamplona.amethyst.service.cashu.CashuToken
import com.vitorpamplona.amethyst.service.cashu.melt.MeltProcessor
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.lnurl.LightningAddressResolver
import com.vitorpamplona.amethyst.service.location.LocationState
import com.vitorpamplona.amethyst.service.okhttp.EmptyHttpClientManager
import com.vitorpamplona.amethyst.service.okhttp.IHttpClientManager
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.RelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.nwc.NWCPaymentFilterAssembler
import com.vitorpamplona.amethyst.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.service.uploads.UploadingState
import com.vitorpamplona.amethyst.ui.actions.Dao
import com.vitorpamplona.amethyst.ui.actions.MediaSaverToDisk
import com.vitorpamplona.amethyst.ui.actions.uploads.RecordingResult
import com.vitorpamplona.amethyst.ui.components.UrlPreviewState
import com.vitorpamplona.amethyst.ui.components.toasts.ToastManager
import com.vitorpamplona.amethyst.ui.feeds.FeedState
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.ZapAmountCommentNotification
import com.vitorpamplona.amethyst.ui.note.ZapraiserStatus
import com.vitorpamplona.amethyst.ui.note.showAmount
import com.vitorpamplona.amethyst.ui.note.showAmountInteger
import com.vitorpamplona.amethyst.ui.screen.SharedPreferencesViewModel
import com.vitorpamplona.amethyst.ui.screen.SharedSettingsState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.CardFeedState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.CombinedZap
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.tor.TorSettings
import com.vitorpamplona.quartz.experimental.ephemChat.chat.RoomId
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryBaseEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryReadingStateEvent
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.metadata.UserMetadata
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address
import com.vitorpamplona.quartz.nip01Core.tags.people.PubKeyReferenceTag
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
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
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90ContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.utils.Hex
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
import okhttp3.OkHttpClient
import java.util.Locale

@Stable
class AccountViewModel(
    val account: Account,
    val settings: SharedSettingsState,
    val dataSources: RelaySubscriptionsCoordinator,
    val okHttpClient: IHttpClientManager,
) : ViewModel(),
    Dao {
    var firstRoute: Route? = null

    val toastManager = ToastManager()
    val feedStates = AccountFeedContentStates(account, viewModelScope)

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
        }

    val notificationHasNewItemsFlow =
        notificationHasNewItems
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val messagesHasNewItems =
        feedStates.dmKnown.feedContent
            .flatMapLatest {
                if (it is FeedState.Loaded) {
                    it.feed
                } else {
                    MutableStateFlow(null)
                }
            }.flatMapLatest {
                val flows =
                    it?.list?.mapNotNull { chat ->
                        (chat.event as? ChatroomKeyable)?.let { event ->
                            val room = event.chatroomKey(account.signer.pubKey)
                            account.settings.getLastReadFlow("Room/${room.hashCode()}").map {
                                (chat.event?.createdAt ?: 0) > it
                            }
                        }
                    }

                if (!flows.isNullOrEmpty()) {
                    combine(flows) {
                        it.any { it }
                    }
                } else {
                    MutableStateFlow(false)
                }
            }

    val messagesHasNewItemsFlow =
        messagesHasNewItems
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

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
                }.map {
                    it?.list?.firstOrNull { it.event != null && it.event !is GenericRepostEvent && it.event !is RepostEvent }?.createdAt()
                },
        ) { lastRead, newestItemCreatedAt ->
            emit(newestItemCreatedAt != null && newestItemCreatedAt > lastRead)
        }

    val homeHasNewItemsFlow =
        homeHasNewItems
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val hasNewItems =
        mapOf(
            Route.Home to homeHasNewItemsFlow,
            Route.Message to messagesHasNewItemsFlow,
            Route.Notification to notificationHasNewItemsFlow,
        )

    fun isWriteable(): Boolean = account.isWriteable()

    fun userProfile(): User = account.userProfile()

    fun <T : Event> observeByETag(
        kind: Int,
        eTag: HexKey,
    ): StateFlow<T?> = LocalCache.observeETag<T>(kind = kind, eventId = eTag, viewModelScope).latest

    fun <T : Event> observeByAuthor(
        kind: Int,
        pubkeyHex: HexKey,
    ): StateFlow<T?> = LocalCache.observeAuthor<T>(kind = kind, pubkey = pubkeyHex, viewModelScope).latest

    fun reactToOrDelete(
        note: Note,
        reaction: String,
    ) {
        runIOCatching {
            val currentReactions = note.allReactionsOfContentByAuthor(userProfile(), reaction)
            if (currentReactions.isNotEmpty()) {
                account.delete(currentReactions)
            } else {
                account.reactTo(note, reaction)
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
        accountChoices: HiddenUsersState.LiveHiddenUsers,
        followUsers: Set<HexKey>,
    ): NoteComposeReportState {
        checkNotInMainThread()

        val isFromLoggedIn = note.author?.pubkeyHex == userProfile().pubkeyHex
        val isFromLoggedInFollow = note.author?.let { followUsers.contains(it.pubkeyHex) } ?: true
        val isPostHidden = note.isHiddenFor(accountChoices)
        val isHiddenAuthor = note.author?.let { account.isHidden(it) } == true

        return if (isPostHidden) {
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
            ) { hiddenUsers, followingUsers, autor, metadata, reports ->
                emit(isNoteAcceptable(metadata.note, hiddenUsers, followingUsers.authors))
            }.onStart {
                emit(
                    isNoteAcceptable(
                        note,
                        account.hiddenUsers.flow.value,
                        account.kind3FollowList.flow.value.authors,
                    ),
                )
            }.flowOn(Dispatchers.Default)
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
                .flowOn(Dispatchers.Default)
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
            withContext(Dispatchers.Default) {
                val it = account.calculateZappedAmount(zappedNote)
                showAmount(it)
            }
        } else {
            showAmount(zappedNote.zapsAmount)
        }

    suspend fun calculateZapraiser(zappedNote: Note): ZapraiserStatus {
        val zapraiserAmount = zappedNote.event?.zapraiserAmount() ?: 0
        return if (zappedNote.zapPayments.isNotEmpty()) {
            withContext(Dispatchers.Default) {
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
    ) = runIOCatching {
        ZapPaymentHandler(account).zap(
            note = note,
            amountMilliSats = amountInMillisats,
            pollOption = pollOption,
            message = message,
            context = context,
            showErrorIfNoLnAddress = showErrorIfNoLnAddress,
            okHttpClient = ::okHttpClientForMoney,
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
    ) = runIOCatching { account.report(note, type, content) }

    fun report(
        user: User,
        type: ReportType,
    ) {
        runIOCatching {
            account.report(user, type)
            account.hideUser(user.pubkeyHex)
        }
    }

    fun boost(note: Note) = runIOCatching { account.boost(note) }

    fun removeEmojiPack(emojiPack: Note) = runIOCatching { account.removeEmojiPack(emojiPack) }

    fun addEmojiPack(emojiPack: Note) = runIOCatching { account.addEmojiPack(emojiPack) }

    fun addMediaToGallery(
        hex: String,
        url: String,
        relay: NormalizedRelayUrl?,
        blurhash: String?,
        dim: DimensionTag?,
        hash: String?,
        mimeType: String?,
    ) = runIOCatching { account.addToGallery(hex, url, relay, blurhash, dim, hash, mimeType) }

    fun removeFromMediaGallery(note: Note) = runIOCatching { account.removeFromGallery(note) }

    fun hashtagFollows(user: User): Note = LocalCache.getOrCreateAddressableNote(HashtagListEvent.createAddress(user.pubkeyHex))

    fun bookmarks(user: User): Note = LocalCache.getOrCreateAddressableNote(BookmarkListEvent.createBookmarkAddress(user.pubkeyHex))

    fun addPrivateBookmark(note: Note) = runIOCatching { account.addBookmark(note, true) }

    fun addPublicBookmark(note: Note) = runIOCatching { account.addBookmark(note, false) }

    fun removePrivateBookmark(note: Note) = runIOCatching { account.removeBookmark(note, true) }

    fun removePublicBookmark(note: Note) = runIOCatching { account.removeBookmark(note, false) }

    fun broadcast(note: Note) = runIOCatching { account.broadcast(note) }

    fun timestamp(note: Note) = runIOCatching { account.otsState.timestamp(note) }

    fun delete(notes: List<Note>) = runIOCatching { account.delete(notes) }

    fun delete(note: Note) = runIOCatching { account.delete(note) }

    fun cachedDecrypt(note: Note): String? = account.cachedDecryptContent(note)

    fun cachedDecrypt(event: Event?): String? = account.cachedDecryptContent(event)

    fun decrypt(
        note: Note,
        onReady: (String) -> Unit,
    ) = runIOCatching {
        account.decryptContent(note)?.let { onReady(it) }
    }

    inline fun runIOCatching(crossinline action: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                action()
            } catch (e: SignerExceptions.ReadOnlyException) {
                toastManager.toast(
                    R.string.read_only_user,
                    R.string.login_with_a_private_key_to_be_able_to_sign_events,
                )
            } catch (e: SignerExceptions.UnauthorizedDecryptionException) {
                toastManager.toast(
                    R.string.unauthorized_exception,
                    R.string.unauthorized_exception_description,
                )
            } catch (e: SignerExceptions.SignerNotFoundException) {
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
    ) = runIOCatching { account.approveCommunityPost(post, community) }

    fun follow(community: AddressableNote) = runIOCatching { account.follow(community) }

    fun follow(channel: PublicChatChannel) = runIOCatching { account.follow(channel) }

    fun follow(channel: EphemeralChatChannel) = runIOCatching { account.follow(channel) }

    fun unfollow(community: AddressableNote) = runIOCatching { account.unfollow(community) }

    fun unfollow(channel: PublicChatChannel) = runIOCatching { account.unfollow(channel) }

    fun unfollow(channel: EphemeralChatChannel) = runIOCatching { account.unfollow(channel) }

    fun follow(user: User) = runIOCatching { account.follow(user) }

    fun unfollow(user: User) = runIOCatching { account.unfollow(user) }

    fun followGeohash(tag: String) = runIOCatching { account.followGeohash(tag) }

    fun unfollowGeohash(tag: String) = runIOCatching { account.unfollowGeohash(tag) }

    fun followHashtag(tag: String) = runIOCatching { account.followHashtag(tag) }

    fun unfollowHashtag(tag: String) = runIOCatching { account.unfollowHashtag(tag) }

    fun showWord(word: String) = runIOCatching { account.showWord(word) }

    fun hideWord(word: String) = runIOCatching { account.hideWord(word) }

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

    fun updateWarnReports(warnReports: Boolean) = runIOCatching { account.updateWarnReports(warnReports) }

    fun updateFilterSpam(filterSpam: Boolean) =
        runIOCatching {
            if (account.updateFilterSpam(filterSpam)) {
                LocalCache.antiSpam.active = filterSpamFromStrangers().value
            }
        }

    fun updateShowSensitiveContent(show: Boolean?) = runIOCatching { account.updateShowSensitiveContent(show) }

    fun changeReactionTypes(
        reactionSet: List<String>,
        onDone: () -> Unit,
    ) = runIOCatching {
        account.changeReactionTypes(reactionSet)
        onDone()
    }

    fun updateZapAmounts(
        amountSet: List<Long>,
        selectedZapType: LnZapEvent.ZapType,
        nip47Update: Nip47WalletConnect.Nip47URINorm?,
    ) = runIOCatching { account.updateZapAmounts(amountSet, selectedZapType, nip47Update) }

    fun toggleDontTranslateFrom(languageCode: String) = runIOCatching { account.toggleDontTranslateFrom(languageCode) }

    fun updateTranslateTo(languageCode: Locale) = runIOCatching { account.updateTranslateTo(languageCode) }

    fun prefer(
        source: String,
        target: String,
        preference: String,
    ) = runIOCatching { account.prefer(source, target, preference) }

    fun show(user: User) = runIOCatching { account.showUser(user.pubkeyHex) }

    fun hide(user: User) = runIOCatching { account.hideUser(user.pubkeyHex) }

    fun hide(word: String) = runIOCatching { account.hideWord(word) }

    fun showUser(pubkeyHex: String) = runIOCatching { account.showUser(pubkeyHex) }

    fun createStatus(newStatus: String) = runIOCatching { account.createStatus(newStatus) }

    fun updateStatus(
        address: Address,
        newStatus: String,
    ) = runIOCatching {
        account.updateStatus(LocalCache.getOrCreateAddressableNote(address), newStatus)
    }

    fun deleteStatus(address: Address) =
        runIOCatching {
            account.deleteStatus(LocalCache.getOrCreateAddressableNote(address))
        }

    fun urlPreview(
        url: String,
        onResult: suspend (UrlPreviewState) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            UrlCachedPreviewer.previewInfo(url, ::okHttpClientForPreview, onResult)
        }
    }

    fun loadReactionTo(note: Note?): String? {
        if (note == null) return null

        return note.getReactionBy(userProfile())
    }

    fun verifyNip05(
        userMetadata: UserMetadata,
        pubkeyHex: String,
        onResult: (Boolean) -> Unit,
    ) {
        val nip05 = userMetadata.nip05?.ifBlank { null } ?: return

        viewModelScope.launch(Dispatchers.IO) {
            Nip05NostrAddressVerifier()
                .verifyNip05(
                    nip05,
                    okHttpClient = {
                        okHttpClient.getHttpClient(account.privacyState.shouldUseTorForNIP05(it))
                    },
                    onSuccess = {
                        // Marks user as verified
                        if (it == pubkeyHex) {
                            userMetadata.nip05Verified = true
                            userMetadata.nip05LastVerificationTime = TimeUtils.now()

                            onResult(userMetadata.nip05Verified)
                        } else {
                            userMetadata.nip05Verified = false
                            userMetadata.nip05LastVerificationTime = 0

                            onResult(userMetadata.nip05Verified)
                        }
                    },
                    onError = {
                        userMetadata.nip05LastVerificationTime = 0
                        userMetadata.nip05Verified = false

                        Log.d("NIP05 Error", it)

                        onResult(userMetadata.nip05Verified)
                    },
                )
        }
    }

    fun retrieveRelayDocument(
        relay: NormalizedRelayUrl,
        onInfo: (Nip11RelayInformation) -> Unit,
        onError: (NormalizedRelayUrl, Nip11Retriever.ErrorCode, String?) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            Nip11CachedRetriever.loadRelayInfo(
                relay,
                okHttpClient = { okHttpClientForClean(relay) },
                onInfo,
                onError,
            )
        }
    }

    fun runOnIO(runOnIO: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { runOnIO() }
    }

    fun checkGetOrCreateUser(key: HexKey): User? = LocalCache.checkGetOrCreateUser(key)

    override suspend fun getOrCreateUser(key: HexKey): User = LocalCache.getOrCreateUser(key)

    fun checkGetOrCreateUser(
        key: HexKey,
        onResult: (User?) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) { onResult(checkGetOrCreateUser(key)) }
    }

    fun getUserIfExists(hex: HexKey): User? = LocalCache.getUserIfExists(hex)

    private fun checkGetOrCreateNote(key: HexKey): Note? = LocalCache.checkGetOrCreateNote(key)

    override suspend fun getOrCreateNote(key: HexKey): Note = LocalCache.getOrCreateNote(key)

    fun checkGetOrCreateNote(
        key: HexKey,
        onResult: (Note?) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) { onResult(checkGetOrCreateNote(key)) }
    }

    fun checkGetOrCreateNote(
        event: Event,
        onResult: (Note?) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            var note = checkGetOrCreateNote(event.id)

            if (note == null) {
                LocalCache.justConsume(event, null, false)
                note = checkGetOrCreateNote(event.id)
            }

            onResult(note)
        }
    }

    fun getNoteIfExists(hex: HexKey): Note? = LocalCache.getNoteIfExists(hex)

    override suspend fun getOrCreateAddressableNote(address: Address): AddressableNote = LocalCache.getOrCreateAddressableNote(address)

    fun getOrCreateAddressableNote(
        key: Address,
        onResult: (AddressableNote?) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) { onResult(getOrCreateAddressableNote(key)) }
    }

    fun getAddressableNoteIfExists(key: String): AddressableNote? = LocalCache.getAddressableNoteIfExists(key)

    fun getAddressableNoteIfExists(key: Address): AddressableNote? = LocalCache.getAddressableNoteIfExists(key)

    suspend fun findStatusesForUser(myUser: User) =
        withContext(Dispatchers.IO) {
            LocalCache.findStatusesForUser(myUser)
        }

    suspend fun findOtsEventsForNote(note: Note) =
        withContext(Dispatchers.Default) {
            LocalCache.findEarliestOtsForNote(note, account.otsResolverBuilder)
        }

    fun cachedModificationEventsForNote(note: Note) = LocalCache.cachedModificationEventsForNote(note)

    suspend fun findModificationEventsForNote(note: Note): List<Note> =
        withContext(Dispatchers.Default) {
            LocalCache.findLatestModificationForNote(note)
        }

    fun checkGetOrCreatePublicChatChannel(key: HexKey): PublicChatChannel? = LocalCache.getOrCreatePublicChatChannel(key)

    fun checkGetOrCreateLiveActivityChannel(key: Address): LiveActivitiesChannel? = LocalCache.getOrCreateLiveChannel(key)

    fun checkGetOrCreateEphemeralChatChannel(key: RoomId): EphemeralChatChannel? = LocalCache.getOrCreateEphemeralChannel(key)

    fun checkGetOrCreateChannel(
        key: HexKey,
        onResult: (Channel?) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) { onResult(checkGetOrCreatePublicChatChannel(key)) }
    }

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
        viewModelScope.launch(Dispatchers.Default) {
            onReady(loadUsersSync(hexList).toImmutableList())
        }
    }

    fun loadUsersSync(hexList: List<String>): List<User> =
        hexList
            .mapNotNull { hex -> checkGetOrCreateUser(hex) }
            .sortedBy { account.isFollowing(it) }
            .reversed()

    suspend fun checkVideoIsOnline(videoUrl: String): Boolean =
        withContext(Dispatchers.IO) {
            OnlineChecker.isOnline(videoUrl, ::okHttpClientForVideo)
        }

    fun loadAndMarkAsRead(
        routeForLastRead: String,
        createdAt: Long?,
    ): Boolean {
        if (createdAt == null) return false

        val lastTime = account.loadLastRead(routeForLastRead)

        val onIsNew = createdAt > lastTime

        if (onIsNew) {
            viewModelScope.launch(Dispatchers.Default) {
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
                }
            }
        }
    }

    fun setTorSettings(newTorSettings: TorSettings) = runIOCatching { account.settings.setTorSettings(newTorSettings) }

    class Factory(
        val account: Account,
        val settings: SharedSettingsState,
        val dataSources: RelaySubscriptionsCoordinator,
        val okHttpClient: IHttpClientManager,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AccountViewModel(
                account,
                settings,
                dataSources,
                okHttpClient,
            ) as T
    }

    init {
        Log.d("Init", "AccountViewModel")
        viewModelScope.launch(Dispatchers.Default) {
            feedStates.init()
            // awaits for init to finish before starting to capture new events.
            LocalCache.live.newEventBundles.collect { newNotes ->
                logTime("AccountViewModel newEventBundle Update with ${newNotes.size} new notes") {
                    feedStates.updateFeedsWith(newNotes)
                }
            }
        }

        viewModelScope.launch(Dispatchers.Default) {
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

    fun sendVoiceReply(
        note: Note,
        recording: RecordingResult,
        context: Context,
    ) {
        if (isWriteable()) {
            val hint = note.toEventHint<VoiceEvent>()
            if (hint == null) return

            runIOCatching {
                val uploader = UploadOrchestrator()
                val result =
                    uploader.upload(
                        uri = recording.file.toUri(),
                        mimeType = recording.mimeType,
                        alt = null,
                        contentWarningReason = null,
                        compressionQuality = CompressorQuality.UNCOMPRESSED,
                        server = account.settings.defaultFileServer,
                        account = account,
                        context = context,
                    )

                if (result is UploadingState.Finished && result.result is UploadOrchestrator.OrchestratorResult.ServerResult) {
                    account.sendVoiceReplyMessage(
                        result.result.url,
                        result.result.fileHeader.mimeType ?: recording.mimeType,
                        result.result.fileHeader.hash,
                        recording.duration,
                        recording.amplitudes,
                        hint,
                    )
                } else if (result is UploadingState.Error) {
                    toastManager.toast(
                        R.string.failed_to_upload_media_no_details,
                        result.errorResource,
                        *result.params,
                    )
                }
            }
        } else {
            toastManager.toast(
                R.string.read_only_user,
                R.string.login_with_a_private_key_to_be_able_to_reply,
            )
        }
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
                runIOCatching {
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
        val lud16 = account.userProfile().info?.lud16
        if (lud16 != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val meltResult = MeltProcessor().melt(token, lud16, ::okHttpClientForMoney, context)
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
                val newEvent = event.unwrapOrNull(account.signer)

                if (newEvent == null) return null

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
    ) = runIOCatching {
        val noteEvent = note?.event
        if (noteEvent != null) {
            val resultingNote = unwrapIfNeeded(noteEvent)
            if (resultingNote != null && resultingNote != note) {
                onReady(resultingNote)
            }
        }
    }

    fun proxyPortForVideo(url: String): Int? = okHttpClient.getCurrentProxyPort(account.privacyState.shouldUseTorForVideoDownload(url))

    fun okHttpClientForNip96(url: String): OkHttpClient = okHttpClient.getHttpClient(account.privacyState.shouldUseTorForUploads(url))

    fun okHttpClientForImage(url: String): OkHttpClient = okHttpClient.getHttpClient(account.privacyState.shouldUseTorForImageDownload(url))

    fun okHttpClientForVideo(url: String): OkHttpClient = okHttpClient.getHttpClient(account.privacyState.shouldUseTorForVideoDownload(url))

    fun okHttpClientForMoney(url: String): OkHttpClient = okHttpClient.getHttpClient(account.privacyState.shouldUseTorForMoneyOperations(url))

    fun okHttpClientForPreview(url: String): OkHttpClient = okHttpClient.getHttpClient(account.privacyState.shouldUseTorForPreviewUrl(url))

    fun okHttpClientForClean(url: NormalizedRelayUrl): OkHttpClient = okHttpClient.getHttpClient(account.torRelayState.shouldUseTorForClean(url))

    fun okHttpClientForPushRegistration(url: String): OkHttpClient = okHttpClient.getHttpClient(account.privacyState.shouldUseTorForTrustedRelays())

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
        runIOCatching {
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
                    filter = { key, note ->
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
    ) = runIOCatching {
        account.sendZapPaymentRequestFor(bolt11, zappedNote, onResponse)
        onSent()
    }

    fun getRelayListFor(user: User): AdvertisedRelayListEvent? = (getRelayListNoteFor(user)?.event as? AdvertisedRelayListEvent?)

    fun getRelayListNoteFor(user: User): AddressableNote? =
        LocalCache.getAddressableNoteIfExists(
            AdvertisedRelayListEvent.createAddressTag(user.pubkeyHex),
        )

    fun getInteractiveStoryReadingState(dATag: String): AddressableNote = LocalCache.getOrCreateAddressableNote(InteractiveStoryReadingStateEvent.createAddress(account.signer.pubKey, dATag))

    fun updateInteractiveStoryReadingState(
        root: InteractiveStoryBaseEvent,
        readingScene: InteractiveStoryBaseEvent,
    ) {
        runIOCatching {
            val sceneNoteRelayHint = LocalCache.getOrCreateAddressableNote(readingScene.address()).relayHintUrl()

            val readingState = getInteractiveStoryReadingState(root.addressTag())
            val readingStateEvent = readingState.event as? InteractiveStoryReadingStateEvent

            if (readingStateEvent != null) {
                account.updateInteractiveStoryReadingState(readingStateEvent, readingScene, sceneNoteRelayHint)
            } else {
                val rootNoteRelayHint = LocalCache.getOrCreateAddressableNote(root.address()).relayHintUrl()

                account.createInteractiveStoryReadingState(root, rootNoteRelayHint, readingScene, sceneNoteRelayHint)
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
                        okHttpClient = ::okHttpClientForMoney,
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
                okHttpClient = ::okHttpClientForVideo,
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

    fun findUsersStartingWithSync(prefix: String) = LocalCache.findUsersStartingWith(prefix, account)

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
            withContext(Dispatchers.Default) {
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
                        is NEmbed ->
                            withContext(Dispatchers.Default) {
                                val baseNote = LocalCache.getOrCreateNote(parsed.event)
                                if (baseNote.event == null) {
                                    launch(Dispatchers.Default) {
                                        LocalCache.justConsume(parsed.event, null, false)
                                    }
                                }

                                returningNote = baseNote
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

    val sharedPreferencesViewModel: SharedPreferencesViewModel = viewModel()
    sharedPreferencesViewModel.init()
    val keyPair =
        KeyPair(
            privKey = Hex.decode("0f761f8a5a481e26f06605a1d9b3e9eba7a107d351f43c43a57469b788274499"),
            pubKey = Hex.decode("989c3734c46abac7ce3ce229971581a5a6ee39cdd6aa7261a55823fa7f8c4799"),
            forceReplacePubkey = false,
        )

    val scope = rememberCoroutineScope()

    val client = EmptyNostrClient

    val nwcFilters = NWCPaymentFilterAssembler(client)

    val account =
        Account(
            settings = AccountSettings(keyPair),
            signer = NostrSignerInternal(keyPair),
            geolocationFlow = MutableStateFlow<LocationState.LocationResult>(LocationState.LocationResult.Loading),
            nwcFilterAssembler = nwcFilters,
            cache = LocalCache,
            client = client,
            scope = scope,
        )

    return AccountViewModel(
        account = account,
        settings = sharedPreferencesViewModel.sharedPrefs,
        okHttpClient = EmptyHttpClientManager,
        dataSources = RelaySubscriptionsCoordinator(LocalCache, client, scope),
    ).also {
        mockedCache = it
    }
}

var vitorCache: AccountViewModel? = null

@SuppressLint("ViewModelConstructorInComposable")
@Composable
fun mockVitorAccountViewModel(): AccountViewModel {
    mockedCache?.let { return it }

    val sharedPreferencesViewModel: SharedPreferencesViewModel = viewModel()
    sharedPreferencesViewModel.init()
    val keyPair =
        KeyPair(
            pubKey = Hex.decode("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"),
        )

    val client = EmptyNostrClient

    val nwcFilters = NWCPaymentFilterAssembler(client)

    val scope = rememberCoroutineScope()

    val account =
        Account(
            settings = AccountSettings(keyPair),
            signer = NostrSignerInternal(keyPair),
            geolocationFlow = MutableStateFlow<LocationState.LocationResult>(LocationState.LocationResult.Loading),
            nwcFilterAssembler = nwcFilters,
            cache = LocalCache,
            client = EmptyNostrClient,
            scope = scope,
        )

    return AccountViewModel(
        account = account,
        settings = sharedPreferencesViewModel.sharedPrefs,
        okHttpClient = EmptyHttpClientManager,
        dataSources = RelaySubscriptionsCoordinator(LocalCache, client, scope),
    ).also {
        vitorCache = it
    }
}
