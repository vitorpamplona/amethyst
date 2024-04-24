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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ServiceManager
import com.vitorpamplona.amethyst.commons.compose.GenericBaseCache
import com.vitorpamplona.amethyst.commons.compose.GenericBaseCacheAsync
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AccountState
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.UrlCachedPreviewer
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.UserState
import com.vitorpamplona.amethyst.service.CashuProcessor
import com.vitorpamplona.amethyst.service.CashuToken
import com.vitorpamplona.amethyst.service.HttpClientManager
import com.vitorpamplona.amethyst.service.Nip05NostrAddressVerifier
import com.vitorpamplona.amethyst.service.Nip11CachedRetriever
import com.vitorpamplona.amethyst.service.Nip11Retriever
import com.vitorpamplona.amethyst.service.OnlineChecker
import com.vitorpamplona.amethyst.service.ZapPaymentHandler
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.ui.actions.Dao
import com.vitorpamplona.amethyst.ui.components.BundledInsert
import com.vitorpamplona.amethyst.ui.components.UrlPreviewState
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.navigation.bottomNavigationItems
import com.vitorpamplona.amethyst.ui.note.ZapAmountCommentNotification
import com.vitorpamplona.amethyst.ui.note.ZapraiserStatus
import com.vitorpamplona.amethyst.ui.note.showAmount
import com.vitorpamplona.amethyst.ui.screen.CombinedZap
import com.vitorpamplona.amethyst.ui.screen.SettingsState
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.Nip11RelayInformation
import com.vitorpamplona.quartz.encoders.Nip19Bech32
import com.vitorpamplona.quartz.events.AddressableEvent
import com.vitorpamplona.quartz.events.ChatroomKey
import com.vitorpamplona.quartz.events.ChatroomKeyable
import com.vitorpamplona.quartz.events.DraftEvent
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.EventInterface
import com.vitorpamplona.quartz.events.GiftWrapEvent
import com.vitorpamplona.quartz.events.LnZapEvent
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import com.vitorpamplona.quartz.events.Participant
import com.vitorpamplona.quartz.events.ReportEvent
import com.vitorpamplona.quartz.events.SealedGossipEvent
import com.vitorpamplona.quartz.events.UserMetadata
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.time.measureTimedValue

@Immutable open class ToastMsg()

@Immutable class StringToastMsg(val title: String, val msg: String) : ToastMsg()

@Immutable class ResourceToastMsg(
    val titleResId: Int,
    val resourceId: Int,
    val params: Array<out String>? = null,
) : ToastMsg()

@Stable
class AccountViewModel(val account: Account, val settings: SettingsState) : ViewModel(), Dao {
    val accountLiveData: LiveData<AccountState> = account.live.map { it }
    val accountLanguagesLiveData: LiveData<AccountState> = account.liveLanguages.map { it }
    val accountMarkAsReadUpdates = mutableIntStateOf(0)

    val userFollows: LiveData<UserState> = account.userProfile().live().follows.map { it }
    val userRelays: LiveData<UserState> = account.userProfile().live().relays.map { it }

    val toasts = MutableSharedFlow<ToastMsg?>(0, 3, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    var serviceManager: ServiceManager? = null

    val showSensitiveContentChanges =
        account.live.map { it.account.showSensitiveContent }.distinctUntilChanged()

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
        resourceId: Int,
        vararg params: String,
    ) {
        viewModelScope.launch { toasts.emit(ResourceToastMsg(titleResId, resourceId, params)) }
    }

    fun isWriteable(): Boolean {
        return account.isWriteable()
    }

    fun userProfile(): User {
        return account.userProfile()
    }

    suspend fun reactTo(
        note: Note,
        reaction: String,
    ) {
        account.reactTo(note, reaction)
    }

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
            val reaction = account.reactionChoices.first()
            if (hasReactedTo(note, reaction)) {
                deleteReactionTo(note, reaction)
            } else {
                reactTo(note, reaction)
            }
        }
    }

    fun isNoteHidden(note: Note): Boolean {
        return note.isHiddenFor(account.flowHiddenUsers.value)
    }

    fun hasReactedTo(
        baseNote: Note,
        reaction: String,
    ): Boolean {
        return account.hasReacted(baseNote, reaction)
    }

    suspend fun deleteReactionTo(
        note: Note,
        reaction: String,
    ) {
        account.delete(account.reactionTo(note, reaction))
    }

    fun hasBoosted(baseNote: Note): Boolean {
        return account.hasBoosted(baseNote)
    }

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
            withContext(Dispatchers.IO) {
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
            withContext(Dispatchers.IO) {
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
                zaps.associate {
                    it.request to
                        ZapAmountCommentNotification(
                            it.request.author,
                            it.request.event?.content()?.ifBlank { null },
                            showAmountAxis((it.response.event as? LnZapEvent)?.amount),
                        )
                }
                    .toMutableMap()

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

    fun cachedDecryptAmountMessageInGroup(zapNotes: List<CombinedZap>): ImmutableList<ZapAmountCommentNotification> {
        return zapNotes
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
                            it.request.event?.content()?.ifBlank { null },
                            showAmountAxis((it.response.event as? LnZapEvent)?.amount),
                        )
                    }
                } else {
                    ZapAmountCommentNotification(
                        it.request.author,
                        it.request.event?.content()?.ifBlank { null },
                        showAmountAxis((it.response.event as? LnZapEvent)?.amount),
                    )
                }
            }
            .toImmutableList()
    }

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
                            it.first.event?.content()?.ifBlank { null },
                            showAmountAxis((it.second?.event as? LnZapEvent)?.amount),
                        )
                    }
                } else {
                    ZapAmountCommentNotification(
                        it.first.author,
                        it.first.event?.content()?.ifBlank { null },
                        showAmountAxis((it.second?.event as? LnZapEvent)?.amount),
                    )
                }
            }
            .toImmutableList()
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
                                it.first.event?.content()?.ifBlank { null },
                                showAmountAxis((it.second?.event as? LnZapEvent)?.amount),
                            )
                    }
                    .toMutableMap()

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
        amount: Long,
        pollOption: Int?,
        message: String,
        context: Context,
        onError: (String, String) -> Unit,
        onProgress: (percent: Float) -> Unit,
        onPayViaIntent: (ImmutableList<ZapPaymentHandler.Payable>) -> Unit,
        zapType: LnZapEvent.ZapType,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            ZapPaymentHandler(account)
                .zap(
                    note,
                    amount,
                    pollOption,
                    message,
                    context,
                    onError,
                    onProgress = {
                        onProgress(it)
                    },
                    onPayViaIntent,
                    zapType,
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

    fun isInPublicBookmarks(note: Note): Boolean {
        return account.isInPublicBookmarks(note)
    }

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

    fun delete(note: Note) {
        viewModelScope.launch(Dispatchers.IO) { account.delete(note) }
    }

    fun cachedDecrypt(note: Note): String? {
        return account.cachedDecryptContent(note)
    }

    fun cachedDecrypt(event: EventInterface?): String? {
        return account.cachedDecryptContent(event)
    }

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

    fun translateTo(lang: Locale) {
        account.updateTranslateTo(lang.language)
    }

    fun dontTranslateFrom(lang: String) {
        account.addDontTranslateFrom(lang)
    }

    fun prefer(
        source: String,
        target: String,
        preference: String,
    ) {
        account.prefer(source, target, preference)
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

    fun isLoggedUser(user: User?): Boolean {
        return account.userProfile().pubkeyHex == user?.pubkeyHex
    }

    fun isFollowing(user: User?): Boolean {
        if (user == null) return false
        return account.userProfile().isFollowingCached(user)
    }

    fun isFollowing(user: HexKey): Boolean {
        return account.userProfile().isFollowingCached(user)
    }

    val hideDeleteRequestDialog: Boolean
        get() = account.hideDeleteRequestDialog

    fun dontShowDeleteRequestDialog() {
        viewModelScope.launch(Dispatchers.IO) { account.setHideDeleteRequestDialog() }
    }

    val hideNIP24WarningDialog: Boolean
        get() = account.hideNIP24WarningDialog

    fun dontShowNIP24WarningDialog() {
        account.setHideNIP24WarningDialog()
    }

    val hideBlockAlertDialog: Boolean
        get() = account.hideBlockAlertDialog

    fun dontShowBlockAlertDialog() {
        account.setHideBlockAlertDialog()
    }

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

    fun defaultZapType(): LnZapEvent.ZapType {
        return account.defaultZapType
    }

    @Immutable
    data class NoteComposeReportState(
        val isAcceptable: Boolean = true,
        val canPreview: Boolean = true,
        val isHiddenAuthor: Boolean = false,
        val relevantReports: ImmutableSet<Note> = persistentSetOf(),
    )

    suspend fun isNoteAcceptable(
        note: Note,
        onReady: (NoteComposeReportState) -> Unit,
    ) {
        val newState =
            withContext(Dispatchers.IO) {
                val isFromLoggedIn = note.author?.pubkeyHex == userProfile().pubkeyHex
                val isFromLoggedInFollow = note.author?.let { userProfile().isFollowingCached(it) } ?: true

                if (isFromLoggedIn || isFromLoggedInFollow) {
                    // No need to process if from trusted people
                    NoteComposeReportState(true, true, false, persistentSetOf())
                } else if (note.author?.let { account.isHidden(it) } == true) {
                    NoteComposeReportState(false, false, true, persistentSetOf())
                } else {
                    val newCanPreview = !note.hasAnyReports()

                    val newIsAcceptable = account.isAcceptable(note)

                    if (newCanPreview && newIsAcceptable) {
                        // No need to process reports if nothing is wrong
                        NoteComposeReportState(true, true, false, persistentSetOf())
                    } else {
                        val newRelevantReports = account.getRelevantReports(note)

                        NoteComposeReportState(
                            newIsAcceptable,
                            newCanPreview,
                            false,
                            newRelevantReports.toImmutableSet(),
                        )
                    }
                }
            }

        onReady(newState)
    }

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
        viewModelScope.launch(Dispatchers.IO) { UrlCachedPreviewer.previewInfo(url, onResult) }
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
            Nip11CachedRetriever.loadRelayInfo(dirtyUrl, onInfo, onError)
        }
    }

    fun runOnIO(runOnIO: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { runOnIO() }
    }

    suspend fun checkGetOrCreateUser(key: HexKey): User? {
        return LocalCache.checkGetOrCreateUser(key)
    }

    override suspend fun getOrCreateUser(key: HexKey): User {
        return LocalCache.getOrCreateUser(key)
    }

    fun checkGetOrCreateUser(
        key: HexKey,
        onResult: (User?) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) { onResult(checkGetOrCreateUser(key)) }
    }

    fun getUserIfExists(hex: HexKey): User? {
        return LocalCache.getUserIfExists(hex)
    }

    private suspend fun checkGetOrCreateNote(key: HexKey): Note? {
        return LocalCache.checkGetOrCreateNote(key)
    }

    override suspend fun getOrCreateNote(key: HexKey): Note {
        return LocalCache.getOrCreateNote(key)
    }

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

    fun getNoteIfExists(hex: HexKey): Note? {
        return LocalCache.getNoteIfExists(hex)
    }

    override suspend fun checkGetOrCreateAddressableNote(key: HexKey): AddressableNote? {
        return LocalCache.checkGetOrCreateAddressableNote(key)
    }

    fun checkGetOrCreateAddressableNote(
        key: HexKey,
        onResult: (AddressableNote?) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) { onResult(checkGetOrCreateAddressableNote(key)) }
    }

    private suspend fun getOrCreateAddressableNote(key: ATag): AddressableNote? {
        return LocalCache.getOrCreateAddressableNote(key)
    }

    fun getOrCreateAddressableNote(
        key: ATag,
        onResult: (AddressableNote?) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) { onResult(getOrCreateAddressableNote(key)) }
    }

    fun getAddressableNoteIfExists(key: String): AddressableNote? {
        return LocalCache.getAddressableNoteIfExists(key)
    }

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
        withContext(Dispatchers.IO) {
            onResult(LocalCache.findEarliestOtsForNote(note))
        }
    }

    fun cachedModificationEventsForNote(note: Note) = LocalCache.cachedModificationEventsForNote(note)

    suspend fun findModificationEventsForNote(
        note: Note,
        onResult: (List<Note>) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            onResult(LocalCache.findLatestModificationForNote(note))
        }
    }

    private suspend fun checkGetOrCreateChannel(key: HexKey): Channel? {
        return LocalCache.checkGetOrCreateChannel(key)
    }

    fun checkGetOrCreateChannel(
        key: HexKey,
        onResult: (Channel?) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) { onResult(checkGetOrCreateChannel(key)) }
    }

    fun getChannelIfExists(hex: HexKey): Channel? {
        return LocalCache.getChannelIfExists(hex)
    }

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
                    }
                    .toImmutableList()

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

    fun checkIsOnline(
        media: String?,
        onDone: (Boolean) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) { onDone(OnlineChecker.isOnline(media)) }
    }

    suspend fun refreshMarkAsReadObservers() {
        updateNotificationDots()
        accountMarkAsReadUpdates.value++
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
                if (account.markAsRead(routeForLastRead, createdAt)) {
                    refreshMarkAsReadObservers()
                }
            }
        }

        return onIsNew
    }

    fun markAllAsRead(
        notes: ImmutableList<Note>,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            var atLeastOne = false

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
                        if (account.markAsRead(route, noteEvent.createdAt())) {
                            atLeastOne = true
                        }
                    }
                }
            }

            if (atLeastOne) {
                refreshMarkAsReadObservers()
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

    fun enableTor(
        checked: Boolean,
        portNumber: MutableState<String>,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            account.proxyPort = portNumber.value.toInt()
            account.proxy = HttpClientManager.initProxy(checked, "127.0.0.1", account.proxyPort)
            account.saveable.invalidateData()
            serviceManager?.forceRestart()
        }
    }

    class Factory(val account: Account, val settings: SettingsState) : ViewModelProvider.Factory {
        override fun <AccountViewModel : ViewModel> create(modelClass: Class<AccountViewModel>): AccountViewModel {
            return AccountViewModel(account, settings) as AccountViewModel
        }
    }

    private var collectorJob: Job? = null
    val notificationDots = HasNotificationDot(bottomNavigationItems)
    private val bundlerInsert = BundledInsert<Set<Note>>(3000, Dispatchers.IO)

    fun invalidateInsertData(newItems: Set<Note>) {
        bundlerInsert.invalidateList(newItems) { updateNotificationDots(it.flatten().toSet()) }
    }

    fun updateNotificationDots(newNotes: Set<Note> = emptySet()) {
        val (value, elapsed) = measureTimedValue { notificationDots.update(newNotes, account) }
        Log.d(
            "Rendering Metrics",
            "Notification Dots Calculation in $elapsed for ${newNotes.size} new notes",
        )
    }

    init {
        Log.d("Init", "AccountViewModel")
        collectorJob =
            viewModelScope.launch(Dispatchers.IO) {
                LocalCache.live.newEventBundles.collect { newNotes ->
                    Log.d(
                        "Rendering Metrics",
                        "Notification Dots Calculation refresh ${this@AccountViewModel} for ${account.userProfile().toBestDisplayName()}",
                    )
                    invalidateInsertData(newNotes)
                    upgradeAttestations()
                }
            }
    }

    override fun onCleared() {
        Log.d("Init", "AccountViewModel onCleared")
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
                        onSuccess = { title, message -> onDone(title, message) },
                        onError = { title, message -> onDone(title, message) },
                        context,
                    )
            }
        } else {
            onDone(
                context.getString(R.string.no_lightning_address_set),
                context.getString(
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
                event.cachedGift(account.signer) {
                    val existingNote = LocalCache.getNoteIfExists(it.id)
                    if (existingNote != null) {
                        unwrapIfNeeded(existingNote.event, onReady)
                    } else {
                        LocalCache.verifyAndConsume(it, null)
                        unwrapIfNeeded(it, onReady)
                    }
                }
            }
            is SealedGossipEvent -> {
                event.cachedGossip(account.signer) {
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

    val draftNoteCache = CachedDraftNotes(this)

    class CachedDraftNotes(val accountViewModel: AccountViewModel) : GenericBaseCacheAsync<DraftEvent, Note>(20) {
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

    class CachedLoadedBechLink(val accountViewModel: AccountViewModel) : GenericBaseCache<String, LoadedBechLink>(20) {
        override suspend fun compute(key: String): LoadedBechLink? {
            return Nip19Bech32.uriToRoute(key)?.let {
                var returningNote: Note? = null

                when (val parsed = it.entity) {
                    is Nip19Bech32.NSec -> {}
                    is Nip19Bech32.NPub -> {}
                    is Nip19Bech32.NProfile -> {}
                    is Nip19Bech32.Note -> withContext(Dispatchers.IO) { LocalCache.checkGetOrCreateNote(parsed.hex)?.let { note -> returningNote = note } }
                    is Nip19Bech32.NEvent -> withContext(Dispatchers.IO) { LocalCache.checkGetOrCreateNote(parsed.hex)?.let { note -> returningNote = note } }
                    is Nip19Bech32.NEmbed ->
                        withContext(Dispatchers.IO) {
                            val baseNote = LocalCache.getOrCreateNote(parsed.event)
                            if (baseNote.event == null) {
                                launch(Dispatchers.IO) {
                                    LocalCache.verifyAndConsume(parsed.event, null)
                                }
                            }

                            returningNote = baseNote
                        }
                    is Nip19Bech32.NRelay -> {}
                    is Nip19Bech32.NAddress -> withContext(Dispatchers.IO) { LocalCache.checkGetOrCreateNote(parsed.atag)?.let { note -> returningNote = note } }
                    else -> {}
                }

                LoadedBechLink(returningNote, it)
            }
        }
    }
}

class HasNotificationDot(bottomNavigationItems: ImmutableList<Route>) {
    val hasNewItems = bottomNavigationItems.associateWith { MutableStateFlow(false) }

    fun update(
        newNotes: Set<Note>,
        account: Account,
    ) {
        checkNotInMainThread()

        hasNewItems.forEach {
            val (value, elapsed) =
                measureTimedValue {
                    val newResult = it.key.hasNewItems(account, newNotes)
                    if (newResult != it.value.value) {
                        it.value.value = newResult
                    }
                }
            Log.d(
                "Rendering Metrics",
                "Notification Dots Calculation for ${it.key.route} in $elapsed for ${newNotes.size} new notes",
            )
        }
    }
}

@Immutable data class LoadedBechLink(val baseNote: Note?, val nip19: Nip19Bech32.ParseReturn)

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
