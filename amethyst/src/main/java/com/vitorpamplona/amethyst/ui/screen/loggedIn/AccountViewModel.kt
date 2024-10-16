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
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.imageLoader
import coil.request.ImageRequest
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.compose.GenericBaseCache
import com.vitorpamplona.amethyst.commons.compose.GenericBaseCacheAsync
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.UrlCachedPreviewer
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.observables.CreatedAtComparator
import com.vitorpamplona.amethyst.service.CashuProcessor
import com.vitorpamplona.amethyst.service.CashuToken
import com.vitorpamplona.amethyst.service.Nip05NostrAddressVerifier
import com.vitorpamplona.amethyst.service.Nip11CachedRetriever
import com.vitorpamplona.amethyst.service.Nip11Retriever
import com.vitorpamplona.amethyst.service.OnlineChecker
import com.vitorpamplona.amethyst.service.ZapPaymentHandler
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.lnurl.LightningAddressResolver
import com.vitorpamplona.amethyst.ui.actions.Dao
import com.vitorpamplona.amethyst.ui.components.UrlPreviewState
import com.vitorpamplona.amethyst.ui.feeds.FeedState
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.note.ZapAmountCommentNotification
import com.vitorpamplona.amethyst.ui.note.ZapraiserStatus
import com.vitorpamplona.amethyst.ui.note.showAmount
import com.vitorpamplona.amethyst.ui.screen.SettingsState
import com.vitorpamplona.amethyst.ui.screen.SharedPreferencesViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.CardFeedState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.CombinedZap
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.showAmountAxis
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.tor.TorSettings
import com.vitorpamplona.ammolite.relays.BundledInsert
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.Nip11RelayInformation
import com.vitorpamplona.quartz.encoders.Nip19Bech32
import com.vitorpamplona.quartz.encoders.RelayUrlFormatter
import com.vitorpamplona.quartz.events.AddressableEvent
import com.vitorpamplona.quartz.events.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.events.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.events.ChatroomKey
import com.vitorpamplona.quartz.events.ChatroomKeyable
import com.vitorpamplona.quartz.events.DraftEvent
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.EventInterface
import com.vitorpamplona.quartz.events.GenericRepostEvent
import com.vitorpamplona.quartz.events.GiftWrapEvent
import com.vitorpamplona.quartz.events.LnZapEvent
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import com.vitorpamplona.quartz.events.NIP90ContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.events.Participant
import com.vitorpamplona.quartz.events.ReportEvent
import com.vitorpamplona.quartz.events.RepostEvent
import com.vitorpamplona.quartz.events.Response
import com.vitorpamplona.quartz.events.SealedGossipEvent
import com.vitorpamplona.quartz.events.SearchRelayListEvent
import com.vitorpamplona.quartz.events.UserMetadata
import com.vitorpamplona.quartz.utils.TimeUtils
import fr.acinq.secp256k1.Hex
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

@Immutable open class ToastMsg

@Immutable class StringToastMsg(
    val title: String,
    val msg: String,
) : ToastMsg()

@Immutable class ResourceToastMsg(
    val titleResId: Int,
    val resourceId: Int,
    val params: Array<out String>? = null,
) : ToastMsg()

@Immutable class ThrowableToastMsg(
    val titleResId: Int,
    val msg: String? = null,
    val throwable: Throwable,
) : ToastMsg()

@Stable
class AccountViewModel(
    accountSettings: AccountSettings,
    val settings: SettingsState,
) : ViewModel(),
    Dao {
    val account = Account(accountSettings, accountSettings.createSigner(), viewModelScope)

    // TODO: contact lists are not notes yet
    // val kind3Relays: StateFlow<ContactListEvent?> = observeByAuthor(ContactListEvent.KIND, account.signer.pubKey)

    val normalizedKind3RelaySetFlow =
        account
            .userProfile()
            .flow()
            .relays.stateFlow
            .map { contactListState ->
                checkNotInMainThread()
                contactListState.user.latestContactList?.relays()?.map {
                    RelayUrlFormatter.normalize(it.key)
                } ?: emptySet()
            }.flowOn(Dispatchers.Default)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(10000, 10000),
                emptySet(),
            )

    val dmRelays: StateFlow<ChatMessageRelayListEvent?> = observeByAuthor(ChatMessageRelayListEvent.KIND, account.signer.pubKey)
    val searchRelays: StateFlow<SearchRelayListEvent?> = observeByAuthor(SearchRelayListEvent.KIND, account.signer.pubKey)

    val toasts = MutableSharedFlow<ToastMsg?>(0, 3, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val feedStates = AccountFeedContentStates(this)

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

    val notificationHasNewItemsFlow = notificationHasNewItems.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, false)

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
                                (chat.event?.createdAt() ?: 0) > it
                            }
                        }
                    }

                if (flows != null) {
                    combine(flows) {
                        it.any { it }
                    }
                } else {
                    MutableStateFlow(false)
                }
            }

    val messagesHasNewItemsFlow = messagesHasNewItems.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, false)

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

    val homeHasNewItemsFlow = homeHasNewItems.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val hasNewItems =
        mapOf(
            Route.Home to homeHasNewItemsFlow,
            Route.Message to messagesHasNewItemsFlow,
            Route.Notification to notificationHasNewItemsFlow,
        )

    fun clearToasts() {
        viewModelScope.launch { toasts.emit(null) }
    }

    fun toast(
        title: String,
        message: String,
    ) {
        viewModelScope.launch { toasts.emit(StringToastMsg(title, message)) }
    }

    fun toast(
        titleResId: Int,
        resourceId: Int,
    ) {
        viewModelScope.launch { toasts.emit(ResourceToastMsg(titleResId, resourceId)) }
    }

    fun toast(
        titleResId: Int,
        message: String?,
        throwable: Throwable,
    ) {
        viewModelScope.launch { toasts.emit(ThrowableToastMsg(titleResId, message, throwable)) }
    }

    fun toast(
        titleResId: Int,
        resourceId: Int,
        vararg params: String,
    ) {
        viewModelScope.launch { toasts.emit(ResourceToastMsg(titleResId, resourceId, params)) }
    }

    fun isWriteable(): Boolean = account.isWriteable()

    fun userProfile(): User = account.userProfile()

    suspend fun reactTo(
        note: Note,
        reaction: String,
    ) {
        account.reactTo(note, reaction)
    }

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
        viewModelScope.launch(Dispatchers.IO) {
            val currentReactions = account.reactionTo(note, reaction)
            if (currentReactions.isNotEmpty()) {
                account.delete(currentReactions)
            } else {
                account.reactTo(note, reaction)
            }
        }
    }

    fun reactToOrDelete(note: Note) {
        viewModelScope.launch(Dispatchers.IO) {
            val reaction =
                account.settings.reactionChoices.value
                    .first()
            if (hasReactedTo(note, reaction)) {
                deleteReactionTo(note, reaction)
            } else {
                reactTo(note, reaction)
            }
        }
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
        accountChoices: Account.LiveHiddenUsers,
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
                account.flowHiddenUsers,
                account.liveKind3Follows,
                note.flow().author(),
                note.flow().metadata.stateFlow,
                note.flow().reports.stateFlow,
            ) { hiddenUsers, followingUsers, autor, metadata, reports ->
                emit(isNoteAcceptable(metadata.note, hiddenUsers, followingUsers.users))
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

    fun hasReactedTo(
        baseNote: Note,
        reaction: String,
    ): Boolean = account.hasReacted(baseNote, reaction)

    suspend fun deleteReactionTo(
        note: Note,
        reaction: String,
    ) {
        account.delete(account.reactionTo(note, reaction))
    }

    fun hasBoosted(baseNote: Note): Boolean = account.hasBoosted(baseNote)

    fun deleteBoostsTo(note: Note) {
        viewModelScope.launch(Dispatchers.IO) { account.delete(account.boostsTo(note)) }
    }

    suspend fun calculateIfNoteWasZappedByAccount(
        zappedNote: Note,
        onWasZapped: (Boolean) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            account.calculateIfNoteWasZappedByAccount(zappedNote) { onWasZapped(true) }
        }
    }

    suspend fun calculateZapAmount(
        zappedNote: Note,
        onZapAmount: (String) -> Unit,
    ) {
        if (zappedNote.zapPayments.isNotEmpty()) {
            withContext(Dispatchers.Default) {
                account.calculateZappedAmount(zappedNote) { onZapAmount(showAmount(it)) }
            }
        } else {
            onZapAmount(showAmount(zappedNote.zapsAmount))
        }
    }

    suspend fun calculateZapraiser(
        zappedNote: Note,
        onZapraiserStatus: (ZapraiserStatus) -> Unit,
    ) {
        val zapraiserAmount = zappedNote.event?.zapraiserAmount() ?: 0
        if (zappedNote.zapPayments.isNotEmpty()) {
            withContext(Dispatchers.Default) {
                account.calculateZappedAmount(zappedNote) { newZapAmount ->
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
                    onZapraiserStatus(ZapraiserStatus(newZapraiserProgress, newZapraiserLeft))
                }
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
            onZapraiserStatus(ZapraiserStatus(newZapraiserProgress, newZapraiserLeft))
        }
    }

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
                                    ?.content()
                                    ?.ifBlank { null },
                                showAmountAxis((it.response.event as? LnZapEvent)?.amount),
                            )
                    }.toMutableMap()

            collectSuccessfulSigningOperations<CombinedZap, ZapAmountCommentNotification>(
                operationsInput = zaps.filter { (it.request.event as? LnZapRequestEvent)?.isPrivateZap() == true },
                runRequestFor = { next, onReady ->
                    checkNotInMainThread()

                    innerDecryptAmountMessage(next.request, next.response, onReady)
                },
            ) {
                checkNotInMainThread()

                it.forEach { decrypted -> initialResults[decrypted.key.request] = decrypted.value }

                onNewState(initialResults.values.toImmutableList())
            }
        }
    }

    fun cachedDecryptAmountMessageInGroup(zapNotes: List<CombinedZap>): ImmutableList<ZapAmountCommentNotification> =
        zapNotes
            .map {
                val request = it.request.event as? LnZapRequestEvent
                if (request?.isPrivateZap() == true) {
                    val cachedPrivateRequest = request.cachedPrivateZap()
                    if (cachedPrivateRequest != null) {
                        ZapAmountCommentNotification(
                            LocalCache.getUserIfExists(cachedPrivateRequest.pubKey) ?: it.request.author,
                            cachedPrivateRequest.content.ifBlank { null },
                            showAmountAxis((it.response.event as? LnZapEvent)?.amount),
                        )
                    } else {
                        ZapAmountCommentNotification(
                            it.request.author,
                            it.request.event
                                ?.content()
                                ?.ifBlank { null },
                            showAmountAxis((it.response.event as? LnZapEvent)?.amount),
                        )
                    }
                } else {
                    ZapAmountCommentNotification(
                        it.request.author,
                        it.request.event
                            ?.content()
                            ?.ifBlank { null },
                        showAmountAxis((it.response.event as? LnZapEvent)?.amount),
                    )
                }
            }.toImmutableList()

    fun cachedDecryptAmountMessageInGroup(baseNote: Note): ImmutableList<ZapAmountCommentNotification> {
        val myList = baseNote.zaps.toList()

        return myList
            .map {
                val request = it.first.event as? LnZapRequestEvent
                if (request?.isPrivateZap() == true) {
                    val cachedPrivateRequest = request.cachedPrivateZap()
                    if (cachedPrivateRequest != null) {
                        ZapAmountCommentNotification(
                            LocalCache.getUserIfExists(cachedPrivateRequest.pubKey) ?: it.first.author,
                            cachedPrivateRequest.content.ifBlank { null },
                            showAmountAxis((it.second?.event as? LnZapEvent)?.amount),
                        )
                    } else {
                        ZapAmountCommentNotification(
                            it.first.author,
                            it.first.event
                                ?.content()
                                ?.ifBlank { null },
                            showAmountAxis((it.second?.event as? LnZapEvent)?.amount),
                        )
                    }
                } else {
                    ZapAmountCommentNotification(
                        it.first.author,
                        it.first.event
                            ?.content()
                            ?.ifBlank { null },
                        showAmountAxis((it.second?.event as? LnZapEvent)?.amount),
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
                                    ?.content()
                                    ?.ifBlank { null },
                                showAmountAxis((it.second?.event as? LnZapEvent)?.amount),
                            )
                    }.toMutableMap()

            collectSuccessfulSigningOperations<Pair<Note, Note?>, ZapAmountCommentNotification>(
                operationsInput = myList,
                runRequestFor = { next, onReady ->
                    innerDecryptAmountMessage(next.first, next.second, onReady)
                },
            ) {
                it.forEach { decrypted -> initialResults[decrypted.key.first] = decrypted.value }

                onNewState(initialResults.values.toImmutableList())
            }
        }
    }

    fun decryptAmountMessage(
        zapRequest: Note,
        zapEvent: Note?,
        onNewState: (ZapAmountCommentNotification?) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            innerDecryptAmountMessage(zapRequest, zapEvent, onNewState)
        }
    }

    private fun innerDecryptAmountMessage(
        zapRequest: Note,
        zapEvent: Note?,
        onReady: (ZapAmountCommentNotification) -> Unit,
    ) {
        checkNotInMainThread()

        (zapRequest.event as? LnZapRequestEvent)?.let {
            if (it.isPrivateZap()) {
                decryptZap(zapRequest) { decryptedContent ->
                    val amount = (zapEvent?.event as? LnZapEvent)?.amount
                    val newAuthor = LocalCache.getOrCreateUser(decryptedContent.pubKey)
                    onReady(
                        ZapAmountCommentNotification(
                            newAuthor,
                            decryptedContent.content.ifBlank { null },
                            showAmountAxis(amount),
                        ),
                    )
                }
            } else {
                val amount = (zapEvent?.event as? LnZapEvent)?.amount
                if (!zapRequest.event?.content().isNullOrBlank() || amount != null) {
                    onReady(
                        ZapAmountCommentNotification(
                            zapRequest.author,
                            zapRequest.event?.content()?.ifBlank { null },
                            showAmountAxis(amount),
                        ),
                    )
                }
            }
        }
    }

    fun zap(
        note: Note,
        amountInMillisats: Long,
        pollOption: Int?,
        message: String,
        context: Context,
        showErrorIfNoLnAddress: Boolean = true,
        onError: (String, String) -> Unit,
        onProgress: (percent: Float) -> Unit,
        onPayViaIntent: (ImmutableList<ZapPaymentHandler.Payable>) -> Unit,
        zapType: LnZapEvent.ZapType? = null,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            ZapPaymentHandler(account)
                .zap(
                    note = note,
                    amountMilliSats = amountInMillisats,
                    pollOption = pollOption,
                    message = message,
                    context = context,
                    showErrorIfNoLnAddress = showErrorIfNoLnAddress,
                    forceProxy = account::shouldUseTorForMoneyOperations,
                    onError = onError,
                    onProgress = {
                        onProgress(it)
                    },
                    onPayViaIntent = onPayViaIntent,
                    zapType = zapType ?: account.settings.defaultZapType.value,
                )
        }
    }

    fun report(
        note: Note,
        type: ReportEvent.ReportType,
        content: String = "",
    ) {
        viewModelScope.launch(Dispatchers.IO) { account.report(note, type, content) }
    }

    fun report(
        user: User,
        type: ReportEvent.ReportType,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            account.report(user, type)
            account.hideUser(user.pubkeyHex)
        }
    }

    fun boost(note: Note) {
        viewModelScope.launch(Dispatchers.IO) { account.boost(note) }
    }

    fun removeEmojiPack(
        usersEmojiList: Note,
        emojiList: Note,
    ) {
        viewModelScope.launch(Dispatchers.IO) { account.removeEmojiPack(usersEmojiList, emojiList) }
    }

    fun addEmojiPack(
        usersEmojiList: Note,
        emojiList: Note,
    ) {
        viewModelScope.launch(Dispatchers.IO) { account.addEmojiPack(usersEmojiList, emojiList) }
    }

    fun addMediaToGallery(
        hex: String,
        url: String,
        relay: String?,
        blurhash: String?,
        dim: String?,
        hash: String?,
        mimeType: String?,
    ) {
        viewModelScope.launch(Dispatchers.IO) { account.addToGallery(hex, url, relay, blurhash, dim, hash, mimeType) }
    }

    fun removefromMediaGallery(note: Note) {
        viewModelScope.launch(Dispatchers.IO) { account.removeFromGallery(note) }
    }

    fun addPrivateBookmark(note: Note) {
        viewModelScope.launch(Dispatchers.IO) { account.addBookmark(note, true) }
    }

    fun addPublicBookmark(note: Note) {
        viewModelScope.launch(Dispatchers.IO) { account.addBookmark(note, false) }
    }

    fun removePrivateBookmark(note: Note) {
        viewModelScope.launch(Dispatchers.IO) { account.removeBookmark(note, true) }
    }

    fun removePublicBookmark(note: Note) {
        viewModelScope.launch(Dispatchers.IO) { account.removeBookmark(note, false) }
    }

    fun isInPrivateBookmarks(
        note: Note,
        onReady: (Boolean) -> Unit,
    ) {
        account.isInPrivateBookmarks(note, onReady)
    }

    fun isInPublicBookmarks(note: Note): Boolean = account.isInPublicBookmarks(note)

    fun broadcast(note: Note) {
        viewModelScope.launch(Dispatchers.IO) { account.broadcast(note) }
    }

    fun timestamp(note: Note) {
        viewModelScope.launch(Dispatchers.IO) { account.timestamp(note) }
    }

    var lastTimeItTriedToUpdateAttestations: Long = 0

    fun upgradeAttestations() {
        // only tries to upgrade every hour
        val now = TimeUtils.now()
        if (now - lastTimeItTriedToUpdateAttestations > TimeUtils.ONE_HOUR) {
            lastTimeItTriedToUpdateAttestations = now
            viewModelScope.launch(Dispatchers.IO) { account.updateAttestations() }
        }
    }

    fun delete(notes: List<Note>) {
        viewModelScope.launch(Dispatchers.IO) { account.delete(notes) }
    }

    fun delete(note: Note) {
        viewModelScope.launch(Dispatchers.IO) { account.delete(note) }
    }

    fun cachedDecrypt(note: Note): String? = account.cachedDecryptContent(note)

    fun cachedDecrypt(event: EventInterface?): String? = account.cachedDecryptContent(event)

    fun decrypt(
        note: Note,
        onReady: (String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) { account.decryptContent(note, onReady) }
    }

    fun decryptZap(
        note: Note,
        onReady: (Event) -> Unit,
    ) {
        account.decryptZapContentAuthor(note, onReady)
    }

    fun follow(user: User) {
        viewModelScope.launch(Dispatchers.IO) { account.follow(user) }
    }

    fun unfollow(user: User) {
        viewModelScope.launch(Dispatchers.IO) { account.unfollow(user) }
    }

    fun followGeohash(tag: String) {
        viewModelScope.launch(Dispatchers.IO) { account.followGeohash(tag) }
    }

    fun unfollowGeohash(tag: String) {
        viewModelScope.launch(Dispatchers.IO) { account.unfollowGeohash(tag) }
    }

    fun followHashtag(tag: String) {
        viewModelScope.launch(Dispatchers.IO) { account.followHashtag(tag) }
    }

    fun unfollowHashtag(tag: String) {
        viewModelScope.launch(Dispatchers.IO) { account.unfollowHashtag(tag) }
    }

    fun showWord(word: String) {
        viewModelScope.launch(Dispatchers.IO) { account.showWord(word) }
    }

    fun hideWord(word: String) {
        viewModelScope.launch(Dispatchers.IO) { account.hideWord(word) }
    }

    fun isLoggedUser(pubkeyHex: HexKey?): Boolean = account.signer.pubKey == pubkeyHex

    fun isLoggedUser(user: User?): Boolean = isLoggedUser(user?.pubkeyHex)

    fun isFollowing(user: User?): Boolean {
        if (user == null) return false
        return account.isFollowing(user)
    }

    fun isFollowing(user: HexKey): Boolean = account.isFollowing(user)

    fun hideSensitiveContent() {
        account.updateShowSensitiveContent(false)
    }

    fun disableContentWarnings() {
        account.updateShowSensitiveContent(true)
    }

    fun seeContentWarnings() {
        account.updateShowSensitiveContent(null)
    }

    fun markDonatedInThisVersion() {
        viewModelScope.launch {
            account.markDonatedInThisVersion()
        }
    }

    fun defaultZapType(): LnZapEvent.ZapType = account.settings.defaultZapType.value

    fun unwrap(
        event: GiftWrapEvent,
        onReady: (Event) -> Unit,
    ) {
        account.unwrap(event, onReady)
    }

    fun unseal(
        event: SealedGossipEvent,
        onReady: (Event) -> Unit,
    ) {
        account.unseal(event, onReady)
    }

    fun show(user: User) {
        viewModelScope.launch(Dispatchers.IO) { account.showUser(user.pubkeyHex) }
    }

    fun hide(user: User) {
        viewModelScope.launch(Dispatchers.IO) { account.hideUser(user.pubkeyHex) }
    }

    fun hide(word: String) {
        viewModelScope.launch(Dispatchers.IO) { account.hideWord(word) }
    }

    fun showUser(pubkeyHex: String) {
        viewModelScope.launch(Dispatchers.IO) { account.showUser(pubkeyHex) }
    }

    fun createStatus(newStatus: String) {
        viewModelScope.launch(Dispatchers.IO) { account.createStatus(newStatus) }
    }

    fun updateStatus(
        it: ATag,
        newStatus: String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            account.updateStatus(LocalCache.getOrCreateAddressableNote(it), newStatus)
        }
    }

    fun deleteStatus(it: ATag) {
        viewModelScope.launch(Dispatchers.IO) {
            account.deleteStatus(LocalCache.getOrCreateAddressableNote(it))
        }
    }

    fun urlPreview(
        url: String,
        onResult: suspend (UrlPreviewState) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            UrlCachedPreviewer.previewInfo(url, account.shouldUseTorForPreviewUrl(url), onResult)
        }
    }

    suspend fun loadReactionTo(note: Note?): String? {
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
                    forceProxy = account::shouldUseTorForNIP05,
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
        dirtyUrl: String,
        onInfo: (Nip11RelayInformation) -> Unit,
        onError: (String, Nip11Retriever.ErrorCode, String?) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            Nip11CachedRetriever.loadRelayInfo(
                dirtyUrl,
                account.shouldUseTorForDirty(dirtyUrl),
                onInfo,
                onError,
            )
        }
    }

    fun runOnIO(runOnIO: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { runOnIO() }
    }

    suspend fun checkGetOrCreateUser(key: HexKey): User? = LocalCache.checkGetOrCreateUser(key)

    override suspend fun getOrCreateUser(key: HexKey): User = LocalCache.getOrCreateUser(key)

    fun checkGetOrCreateUser(
        key: HexKey,
        onResult: (User?) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) { onResult(checkGetOrCreateUser(key)) }
    }

    fun getUserIfExists(hex: HexKey): User? = LocalCache.getUserIfExists(hex)

    private suspend fun checkGetOrCreateNote(key: HexKey): Note? = LocalCache.checkGetOrCreateNote(key)

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
                LocalCache.verifyAndConsume(event, null)
                note = checkGetOrCreateNote(event.id)
            }

            onResult(note)
        }
    }

    fun getNoteIfExists(hex: HexKey): Note? = LocalCache.getNoteIfExists(hex)

    override suspend fun checkGetOrCreateAddressableNote(key: HexKey): AddressableNote? = LocalCache.checkGetOrCreateAddressableNote(key)

    fun checkGetOrCreateAddressableNote(
        key: HexKey,
        onResult: (AddressableNote?) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) { onResult(checkGetOrCreateAddressableNote(key)) }
    }

    suspend fun getOrCreateAddressableNote(key: ATag): AddressableNote? = LocalCache.getOrCreateAddressableNote(key)

    fun getOrCreateAddressableNote(
        key: ATag,
        onResult: (AddressableNote?) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) { onResult(getOrCreateAddressableNote(key)) }
    }

    fun getAddressableNoteIfExists(key: String): AddressableNote? = LocalCache.getAddressableNoteIfExists(key)

    suspend fun findStatusesForUser(
        myUser: User,
        onResult: (ImmutableList<AddressableNote>) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            onResult(LocalCache.findStatusesForUser(myUser))
        }
    }

    suspend fun findOtsEventsForNote(
        note: Note,
        onResult: (Long?) -> Unit,
    ) {
        onResult(
            withContext(Dispatchers.Default) {
                LocalCache.findEarliestOtsForNote(note)
            },
        )
    }

    fun cachedModificationEventsForNote(note: Note) = LocalCache.cachedModificationEventsForNote(note)

    suspend fun findModificationEventsForNote(
        note: Note,
        onResult: (List<Note>) -> Unit,
    ) {
        onResult(
            withContext(Dispatchers.Default) {
                LocalCache.findLatestModificationForNote(note)
            },
        )
    }

    private suspend fun checkGetOrCreateChannel(key: HexKey): Channel? = LocalCache.checkGetOrCreateChannel(key)

    fun checkGetOrCreateChannel(
        key: HexKey,
        onResult: (Channel?) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) { onResult(checkGetOrCreateChannel(key)) }
    }

    fun getChannelIfExists(hex: HexKey): Channel? = LocalCache.getChannelIfExists(hex)

    fun loadParticipants(
        participants: List<Participant>,
        onReady: (ImmutableList<Pair<Participant, User>>) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val participantUsers =
                participants
                    .mapNotNull { part ->
                        checkGetOrCreateUser(part.key)?.let {
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
            onReady(
                hexList
                    .mapNotNull { hex -> checkGetOrCreateUser(hex) }
                    .sortedBy { account.isFollowing(it) }
                    .reversed()
                    .toImmutableList(),
            )
        }
    }

    fun checkVideoIsOnline(
        videoUrl: String,
        onDone: (Boolean) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            onDone(OnlineChecker.isOnline(videoUrl, account.shouldUseTorForVideoDownload(videoUrl)))
        }
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

    fun markAllAsRead(
        notes: ImmutableList<Note>,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            for (note in notes) {
                note.event?.let { noteEvent ->
                    val channelHex = note.channelHex()
                    val route =
                        if (channelHex != null) {
                            "Channel/$channelHex"
                        } else if (note.event is ChatroomKeyable) {
                            val withKey = (note.event as ChatroomKeyable).chatroomKey(userProfile().pubkeyHex)
                            "Room/${withKey.hashCode()}"
                        } else {
                            null
                        }

                    route?.let {
                        account.markAsRead(route, noteEvent.createdAt())
                    }
                }
            }

            onDone()
        }
    }

    fun createChatRoomFor(
        user: User,
        then: (Int) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val withKey = ChatroomKey(persistentSetOf(user.pubkeyHex))
            account.userProfile().createChatroom(withKey)
            then(withKey.hashCode())
        }
    }

    fun setTorSettings(newTorSettings: TorSettings) {
        viewModelScope.launch(Dispatchers.IO) {
            // Only restart relay connections if port or type changes
            if (account.settings.setTorSettings(newTorSettings)) {
                Amethyst.instance.serviceManager.forceRestart()
            }
        }
    }

    fun restartServices() {
        viewModelScope.launch(Dispatchers.IO) {
            Amethyst.instance.serviceManager.restartIfDifferentAccount(account)
        }
    }

    class Factory(
        val accountSettings: AccountSettings,
        val settings: SettingsState,
    ) : ViewModelProvider.Factory {
        override fun <AccountViewModel : ViewModel> create(modelClass: Class<AccountViewModel>): AccountViewModel = AccountViewModel(accountSettings, settings) as AccountViewModel
    }

    private var collectorJob: Job? = null
    private val bundlerInsert = BundledInsert<Set<Note>>(3000, Dispatchers.Default)

    init {
        Log.d("Init", "AccountViewModel")
        collectorJob =
            viewModelScope.launch(Dispatchers.Default) {
                feedStates.init()
                // awaits for init to finish before starting to capture new events.
                LocalCache.live.newEventBundles.collect { newNotes ->
                    Log.d(
                        "Rendering Metrics",
                        "Update feeds ${this@AccountViewModel} for ${account.userProfile().toBestDisplayName()} with ${newNotes.size} new notes",
                    )
                    feedStates.updateFeedsWith(newNotes)
                    upgradeAttestations()
                }
            }
    }

    override fun onCleared() {
        Log.d("Init", "AccountViewModel onCleared")
        feedStates.destroy()
        bundlerInsert.cancel()
        collectorJob?.cancel()
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
                val myCover = context.imageLoader.execute(request).drawable
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
            toast(
                R.string.draft_note,
                R.string.it_s_not_possible_to_quote_to_a_draft_note,
            )
            return
        }

        if (isWriteable()) {
            if (hasBoosted(baseNote)) {
                deleteBoostsTo(baseNote)
            } else {
                onMore()
            }
        } else {
            toast(
                R.string.read_only_user,
                R.string.login_with_a_private_key_to_be_able_to_boost_posts,
            )
        }
    }

    fun dismissPaymentRequest(request: Account.PaymentRequest) {
        viewModelScope.launch(Dispatchers.IO) { account.dismissPaymentRequest(request) }
    }

    fun meltCashu(
        token: CashuToken,
        context: Context,
        onDone: (String, String) -> Unit,
    ) {
        val lud16 = account.userProfile().info?.lud16
        if (lud16 != null) {
            viewModelScope.launch(Dispatchers.IO) {
                CashuProcessor()
                    .melt(
                        token,
                        lud16,
                        forceProxy = account::shouldUseTorForMoneyOperations,
                        onSuccess = { title, message -> onDone(title, message) },
                        onError = { title, message -> onDone(title, message) },
                        context,
                    )
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

    fun unwrapIfNeeded(
        event: EventInterface?,
        onReady: (Note) -> Unit,
    ) {
        when (event) {
            is GiftWrapEvent -> {
                event.innerEventId?.let {
                    val existingNote = LocalCache.getNoteIfExists(it)
                    if (existingNote != null) {
                        unwrapIfNeeded(existingNote.event, onReady)
                    } else {
                        event.unwrap(account.signer) {
                            LocalCache.verifyAndConsume(it, null)
                            unwrapIfNeeded(it, onReady)
                        }
                    }
                } ?: run {
                    event.unwrap(account.signer) {
                        val existingNote = LocalCache.getNoteIfExists(it.id)
                        if (existingNote != null) {
                            unwrapIfNeeded(existingNote.event, onReady)
                        } else {
                            LocalCache.verifyAndConsume(it, null)
                            unwrapIfNeeded(it, onReady)
                        }
                    }
                }
            }
            is SealedGossipEvent -> {
                event.innerEventId?.let {
                    val existingNote = LocalCache.getNoteIfExists(it)
                    if (existingNote != null) {
                        unwrapIfNeeded(existingNote.event, onReady)
                    } else {
                        event.unseal(account.signer) {
                            // this is not verifiable
                            LocalCache.justConsume(it, null)
                            unwrapIfNeeded(it, onReady)
                        }
                    }
                } ?: run {
                    event.unseal(account.signer) {
                        val existingNote = LocalCache.getNoteIfExists(it.id)
                        if (existingNote != null) {
                            unwrapIfNeeded(existingNote.event, onReady)
                        } else {
                            // this is not verifiable
                            LocalCache.justConsume(it, null)
                            unwrapIfNeeded(it, onReady)
                        }
                    }
                }
            }
            else -> {
                event?.id()?.let {
                    LocalCache.getNoteIfExists(it)?.let {
                        onReady(it)
                    }
                }
            }
        }
    }

    fun unwrapIfNeeded(
        note: Note?,
        onReady: (Note) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            unwrapIfNeeded(note?.event) {
                onReady(it)
            }
        }
    }

    suspend fun deleteDraft(draftTag: String) {
        account.deleteDraft(draftTag)
    }

    suspend fun createTempDraftNote(
        noteEvent: DraftEvent,
        onReady: (Note?) -> Unit,
    ) {
        draftNoteCache.update(noteEvent, onReady)
    }

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
        dvmPublicKey: String,
        onReady: (event: Note) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
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
            return@withContext response?.event?.tags()?.firstOrNull { it.size > 1 && it[0] == "e" }?.get(1)?.let {
                LocalCache.getOrCreateNote(it)
            }
        }

    fun sendZapPaymentRequestFor(
        bolt11: String,
        zappedNote: Note?,
        onSent: () -> Unit,
        onResponse: (Response?) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            account.sendZapPaymentRequestFor(bolt11, zappedNote, onSent, onResponse)
        }
    }

    fun getRelayListFor(user: User): AdvertisedRelayListEvent? = (getRelayListNoteFor(user)?.event as? AdvertisedRelayListEvent?)

    fun getRelayListNoteFor(user: User): AddressableNote? =
        LocalCache.getAddressableNoteIfExists(
            AdvertisedRelayListEvent.createAddressTag(user.pubkeyHex),
        )

    fun sendSats(
        lnaddress: String,
        milliSats: Long,
        message: String,
        toUserPubKeyHex: HexKey,
        onSuccess: (String) -> Unit,
        onError: (String, String) -> Unit,
        onProgress: (percent: Float) -> Unit,
        context: Context,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (account.settings.defaultZapType.value == LnZapEvent.ZapType.NONZAP) {
                LightningAddressResolver()
                    .lnAddressInvoice(
                        lnaddress,
                        milliSats,
                        message,
                        null,
                        forceProxy = account::shouldUseTorForMoneyOperations,
                        onSuccess = onSuccess,
                        onError = onError,
                        onProgress = onProgress,
                        context = context,
                    )
            } else {
                account.createZapRequestFor(toUserPubKeyHex, message, account.settings.defaultZapType.value) { zapRequest ->
                    LocalCache.justConsume(zapRequest, null)
                    LightningAddressResolver()
                        .lnAddressInvoice(
                            lnaddress,
                            milliSats,
                            message,
                            zapRequest.toJson(),
                            forceProxy = account::shouldUseTorForMoneyOperations,
                            onSuccess = onSuccess,
                            onError = onError,
                            onProgress = onProgress,
                            context = context,
                        )
                }
            }
        }
    }

    val draftNoteCache = CachedDraftNotes(this)

    class CachedDraftNotes(
        val accountViewModel: AccountViewModel,
    ) : GenericBaseCacheAsync<DraftEvent, Note>(20) {
        override suspend fun compute(
            key: DraftEvent,
            onReady: (Note?) -> Unit,
        ) = withContext(Dispatchers.IO) {
            key.cachedDraft(accountViewModel.account.signer) {
                val author = LocalCache.getOrCreateUser(key.pubKey)
                val note = accountViewModel.createTempDraftNote(it, author)
                onReady(note)
            }
        }
    }

    val bechLinkCache = CachedLoadedBechLink(this)

    class CachedLoadedBechLink(
        val accountViewModel: AccountViewModel,
    ) : GenericBaseCache<String, LoadedBechLink>(20) {
        override suspend fun compute(key: String): LoadedBechLink? =
            withContext(Dispatchers.Default) {
                Nip19Bech32.uriToRoute(key)?.let {
                    var returningNote: Note? = null

                    when (val parsed = it.entity) {
                        is Nip19Bech32.NSec -> {}
                        is Nip19Bech32.NPub -> {}
                        is Nip19Bech32.NProfile -> {}
                        is Nip19Bech32.Note -> {
                            LocalCache.checkGetOrCreateNote(parsed.hex)?.let { note ->
                                returningNote = note
                            }
                        }
                        is Nip19Bech32.NEvent -> {
                            LocalCache.checkGetOrCreateNote(parsed.hex)?.let { note ->
                                returningNote = note
                            }
                        }
                        is Nip19Bech32.NEmbed ->
                            withContext(Dispatchers.Default) {
                                val baseNote = LocalCache.getOrCreateNote(parsed.event)
                                if (baseNote.event == null) {
                                    launch(Dispatchers.Default) {
                                        LocalCache.verifyAndConsume(parsed.event, null)
                                    }
                                }

                                returningNote = baseNote
                            }

                        is Nip19Bech32.NRelay -> {}
                        is Nip19Bech32.NAddress -> {
                            LocalCache.checkGetOrCreateNote(parsed.atag)?.let { note ->
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
    val nip19: Nip19Bech32.ParseReturn,
)

public suspend fun <T, K> collectSuccessfulSigningOperations(
    operationsInput: List<T>,
    runRequestFor: (T, (K) -> Unit) -> Unit,
    output: MutableMap<T, K> = mutableMapOf(),
    onReady: suspend (MutableMap<T, K>) -> Unit,
) {
    if (operationsInput.isEmpty()) {
        onReady(output)
        return
    }

    coroutineScope {
        val jobs =
            operationsInput.map {
                async {
                    val result =
                        withTimeoutOrNull(10000) {
                            suspendCancellableCoroutine { continuation ->
                                runRequestFor(it) { result: K -> continuation.resume(result) }
                            }
                        }
                    if (result != null) {
                        output[it] = result
                    }
                }
            }

        // runs in parallel to avoid overcrowding Amber.
        withTimeoutOrNull(15000) {
            jobs.joinAll()
        }
    }

    onReady(output)
}

@Composable
fun mockAccountViewModel(): AccountViewModel {
    val sharedPreferencesViewModel: SharedPreferencesViewModel = viewModel()
    sharedPreferencesViewModel.init()

    return AccountViewModel(
        AccountSettings(
            // blank keys
            keyPair =
                KeyPair(
                    privKey = Hex.decode("0f761f8a5a481e26f06605a1d9b3e9eba7a107d351f43c43a57469b788274499"),
                    pubKey = Hex.decode("989c3734c46abac7ce3ce229971581a5a6ee39cdd6aa7261a55823fa7f8c4799"),
                    forcePubKeyCheck = false,
                ),
        ),
        sharedPreferencesViewModel.sharedPrefs,
    )
}
