package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AccountState
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.ConnectivityType
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.RelayInformation
import com.vitorpamplona.amethyst.model.UrlCachedPreviewer
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.UserState
import com.vitorpamplona.amethyst.service.Nip05Verifier
import com.vitorpamplona.amethyst.service.Nip11CachedRetriever
import com.vitorpamplona.amethyst.service.Nip11Retriever
import com.vitorpamplona.amethyst.service.OnlineChecker
import com.vitorpamplona.amethyst.service.lnurl.LightningAddressResolver
import com.vitorpamplona.amethyst.ui.components.UrlPreviewState
import com.vitorpamplona.amethyst.ui.note.ZapAmountCommentNotification
import com.vitorpamplona.amethyst.ui.note.ZapraiserStatus
import com.vitorpamplona.amethyst.ui.note.showAmount
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.GiftWrapEvent
import com.vitorpamplona.quartz.events.LnZapEvent
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import com.vitorpamplona.quartz.events.PayInvoiceErrorResponse
import com.vitorpamplona.quartz.events.ReportEvent
import com.vitorpamplona.quartz.events.SealedGossipEvent
import com.vitorpamplona.quartz.events.UserMetadata
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Locale

@Stable
class AccountViewModel(val account: Account) : ViewModel() {
    val accountLiveData: LiveData<AccountState> = account.live.map { it }
    val accountLanguagesLiveData: LiveData<AccountState> = account.liveLanguages.map { it }
    val accountLastReadLiveData: LiveData<AccountState> = account.liveLastRead.map { it }

    val userFollows: LiveData<UserState> = account.userProfile().live().follows.map { it }
    val userRelays: LiveData<UserState> = account.userProfile().live().relays.map { it }

    val discoveryListLiveData = account.live.map {
        it.account.defaultDiscoveryFollowList
    }.distinctUntilChanged()

    val homeListLiveData = account.live.map {
        it.account.defaultHomeFollowList
    }.distinctUntilChanged()

    val notificationListLiveData = account.live.map {
        it.account.defaultNotificationFollowList
    }.distinctUntilChanged()

    val storiesListLiveData = account.live.map {
        it.account.defaultStoriesFollowList
    }.distinctUntilChanged()

    val showSensitiveContentChanges = account.live.map {
        it.account.showSensitiveContent
    }.distinctUntilChanged()

    fun updateAutomaticallyStartPlayback(
        automaticallyStartPlayback: ConnectivityType
    ) {
        account.updateAutomaticallyStartPlayback(automaticallyStartPlayback)
    }

    fun updateAutomaticallyShowUrlPreview(
        automaticallyShowUrlPreview: ConnectivityType
    ) {
        account.updateAutomaticallyShowUrlPreview(automaticallyShowUrlPreview)
    }

    fun updateAutomaticallyShowImages(
        automaticallyShowImages: ConnectivityType
    ) {
        account.updateAutomaticallyShowImages(automaticallyShowImages)
    }

    fun isWriteable(): Boolean {
        return account.isWriteable()
    }

    fun userProfile(): User {
        return account.userProfile()
    }

    fun reactTo(note: Note, reaction: String) {
        account.reactTo(note, reaction)
    }

    fun reactToOrDelete(note: Note, reaction: String) {
        val currentReactions = account.reactionTo(note, reaction)
        if (currentReactions.isNotEmpty()) {
            account.delete(currentReactions)
        } else {
            account.reactTo(note, reaction)
        }
    }

    fun isNoteHidden(note: Note): Boolean {
        val isSensitive = note.event?.isSensitive() ?: false
        return account.isHidden(note.author!!) || (isSensitive && account.showSensitiveContent == false)
    }

    fun hasReactedTo(baseNote: Note, reaction: String): Boolean {
        return account.hasReacted(baseNote, reaction)
    }

    fun deleteReactionTo(note: Note, reaction: String) {
        account.delete(account.reactionTo(note, reaction))
    }

    fun hasBoosted(baseNote: Note): Boolean {
        return account.hasBoosted(baseNote)
    }

    fun deleteBoostsTo(note: Note) {
        account.delete(account.boostsTo(note))
    }

    fun calculateIfNoteWasZappedByAccount(zappedNote: Note, onWasZapped: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.Default) {
            onWasZapped(account.calculateIfNoteWasZappedByAccount(zappedNote))
        }
    }

    suspend fun calculateZapAmount(zappedNote: Note): BigDecimal {
        return account.calculateZappedAmount(zappedNote)
    }

    fun calculateZapAmount(zappedNote: Note, onZapAmount: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            onZapAmount(showAmount(account.calculateZappedAmount(zappedNote)))
        }
    }

    fun calculateZapraiser(zappedNote: Note, onZapraiserStatus: (ZapraiserStatus) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val zapraiserAmount = zappedNote.event?.zapraiserAmount() ?: 0
            val newZapAmount = calculateZapAmount(zappedNote)
            var percentage = newZapAmount.div(zapraiserAmount.toBigDecimal()).toFloat()

            if (percentage > 1) {
                percentage = 1f
            }

            val newZapraiserProgress = percentage
            val newZapraiserLeft = if (percentage > 0.99) {
                "0"
            } else {
                showAmount((zapraiserAmount * (1 - percentage)).toBigDecimal())
            }
            onZapraiserStatus(ZapraiserStatus(newZapraiserProgress, newZapraiserLeft))
        }
    }

    fun decryptAmountMessage(
        zapRequest: Note,
        zapEvent: Note?,
        onNewState: (ZapAmountCommentNotification?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            onNewState(innerDecryptAmountMessage(zapRequest, zapEvent))
        }
    }

    private suspend fun innerDecryptAmountMessage(
        zapRequest: Note,
        zapEvent: Note?
    ): ZapAmountCommentNotification? {
        (zapRequest.event as? LnZapRequestEvent)?.let {
            val decryptedContent = decryptZap(zapRequest)
            val amount = (zapEvent?.event as? LnZapEvent)?.amount
            if (decryptedContent != null) {
                val newAuthor = LocalCache.getOrCreateUser(decryptedContent.pubKey)
                return ZapAmountCommentNotification(
                    newAuthor,
                    decryptedContent.content.ifBlank { null },
                    showAmountAxis(amount)
                )
            } else {
                if (!zapRequest.event?.content().isNullOrBlank() || amount != null) {
                    return ZapAmountCommentNotification(
                        zapRequest.author,
                        zapRequest.event?.content()?.ifBlank { null },
                        showAmountAxis(amount)
                    )
                }
            }
        }
        return null
    }

    fun zap(note: Note, amount: Long, pollOption: Int?, message: String, context: Context, onError: (String) -> Unit, onProgress: (percent: Float) -> Unit, zapType: LnZapEvent.ZapType) {
        viewModelScope.launch(Dispatchers.IO) {
            innerZap(note, amount, pollOption, message, context, onError, onProgress, zapType)
        }
    }

    private suspend fun innerZap(note: Note, amount: Long, pollOption: Int?, message: String, context: Context, onError: (String) -> Unit, onProgress: (percent: Float) -> Unit, zapType: LnZapEvent.ZapType) {
        val lud16 = note.event?.zapAddress() ?: note.author?.info?.lud16?.trim() ?: note.author?.info?.lud06?.trim()

        if (lud16.isNullOrBlank()) {
            onError(context.getString(R.string.user_does_not_have_a_lightning_address_setup_to_receive_sats))
            return
        }

        var zapRequestJson = ""

        if (zapType != LnZapEvent.ZapType.NONZAP) {
            val zapRequest = account.createZapRequestFor(note, pollOption, message, zapType)
            if (zapRequest != null) {
                zapRequestJson = zapRequest.toJson()
            }
        }

        onProgress(0.10f)

        LightningAddressResolver().lnAddressInvoice(
            lud16,
            amount,
            message,
            zapRequestJson,
            onSuccess = {
                onProgress(0.7f)
                if (account.hasWalletConnectSetup()) {
                    account.sendZapPaymentRequestFor(
                        bolt11 = it,
                        note,
                        onResponse = { response ->
                            if (response is PayInvoiceErrorResponse) {
                                onProgress(0.0f)
                                onError(
                                    response.error?.message
                                        ?: response.error?.code?.toString()
                                        ?: "Error parsing error message"
                                )
                            } else {
                                onProgress(1f)
                            }
                        }
                    )
                    onProgress(0.8f)

                    // Awaits for the event to come back to LocalCache.
                    viewModelScope.launch(Dispatchers.IO) {
                        delay(5000)
                        onProgress(0f)
                    }
                } else {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("lightning:$it"))
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        ContextCompat.startActivity(context, intent, null)
                    } catch (e: Exception) {
                        onError(context.getString(R.string.lightning_wallets_not_found))
                    }
                    onProgress(0f)
                }
            },
            onError = onError,
            onProgress = onProgress
        )
    }

    fun report(note: Note, type: ReportEvent.ReportType, content: String = "") {
        account.report(note, type, content)
    }

    fun report(user: User, type: ReportEvent.ReportType) {
        viewModelScope.launch(Dispatchers.IO) {
            account.report(user, type)
            account.hideUser(user.pubkeyHex)
        }
    }

    fun boost(note: Note) {
        account.boost(note)
    }

    fun removeEmojiPack(usersEmojiList: Note, emojiList: Note) {
        account.removeEmojiPack(usersEmojiList, emojiList)
    }

    fun addEmojiPack(usersEmojiList: Note, emojiList: Note) {
        account.addEmojiPack(usersEmojiList, emojiList)
    }

    fun addPrivateBookmark(note: Note) {
        account.addPrivateBookmark(note)
    }

    fun addPublicBookmark(note: Note) {
        account.addPublicBookmark(note)
    }

    fun removePrivateBookmark(note: Note) {
        account.removePrivateBookmark(note)
    }

    fun removePublicBookmark(note: Note) {
        account.removePublicBookmark(note)
    }

    fun isInPrivateBookmarks(note: Note): Boolean {
        return account.isInPrivateBookmarks(note)
    }

    fun isInPublicBookmarks(note: Note): Boolean {
        return account.isInPublicBookmarks(note)
    }

    fun broadcast(note: Note) {
        account.broadcast(note)
    }

    fun delete(note: Note) {
        account.delete(note)
    }

    fun decrypt(note: Note): String? {
        return account.decryptContent(note)
    }

    fun decryptZap(note: Note): Event? {
        return account.decryptZapContentAuthor(note)
    }

    fun translateTo(lang: Locale) {
        account.updateTranslateTo(lang.language)
    }

    fun dontTranslateFrom(lang: String) {
        account.addDontTranslateFrom(lang)
    }

    fun prefer(source: String, target: String, preference: String) {
        account.prefer(source, target, preference)
    }

    fun follow(user: User) {
        account.follow(user)
    }

    fun unfollow(user: User) {
        account.unfollow(user)
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
        account.setHideDeleteRequestDialog()
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

    fun defaultZapType(): LnZapEvent.ZapType {
        return account.defaultZapType
    }

    @Immutable
    data class NoteComposeReportState(
        val isAcceptable: Boolean = true,
        val canPreview: Boolean = true,
        val isHiddenAuthor: Boolean = false,
        val relevantReports: ImmutableSet<Note> = persistentSetOf()
    )

    fun isNoteAcceptable(note: Note, onReady: (NoteComposeReportState) -> Unit) {
        viewModelScope.launch {
            val isFromLoggedIn = note.author?.pubkeyHex == userProfile().pubkeyHex
            val isFromLoggedInFollow = note.author?.let { userProfile().isFollowingCached(it) } ?: true

            if (isFromLoggedIn || isFromLoggedInFollow) {
                // No need to process if from trusted people
                onReady(NoteComposeReportState(true, true, false, persistentSetOf()))
            } else if (note.author?.let { account.isHidden(it) } == true) {
                onReady(NoteComposeReportState(false, false, true, persistentSetOf()))
            } else {
                val newCanPreview = !note.hasAnyReports()

                val newIsAcceptable = account.isAcceptable(note)

                if (newCanPreview && newIsAcceptable) {
                    // No need to process reports if nothing is wrong
                    onReady(NoteComposeReportState(true, true, false, persistentSetOf()))
                } else {
                    val newRelevantReports = account.getRelevantReports(note)

                    onReady(
                        NoteComposeReportState(
                            newIsAcceptable,
                            newCanPreview,
                            false,
                            newRelevantReports.toImmutableSet()
                        )
                    )
                }
            }
        }
    }

    fun unwrap(event: GiftWrapEvent): Event? {
        return account.unwrap(event)
    }
    fun unseal(event: SealedGossipEvent): Event? {
        return account.unseal(event)
    }

    fun show(user: User) {
        viewModelScope.launch(Dispatchers.IO) {
            account.showUser(user.pubkeyHex)
        }
    }

    fun hide(user: User) {
        viewModelScope.launch(Dispatchers.IO) {
            account.hideUser(user.pubkeyHex)
        }
    }

    fun showUser(pubkeyHex: String) {
        viewModelScope.launch(Dispatchers.IO) {
            account.showUser(pubkeyHex)
        }
    }

    fun createStatus(newStatus: String) {
        viewModelScope.launch(Dispatchers.IO) {
            account.createStatus(newStatus)
        }
    }

    fun updateStatus(it: AddressableNote, newStatus: String) {
        viewModelScope.launch(Dispatchers.IO) {
            account.updateStatus(it, newStatus)
        }
    }

    fun deleteStatus(it: AddressableNote) {
        viewModelScope.launch(Dispatchers.IO) {
            account.deleteStatus(it)
        }
    }

    fun checkIfOnline(url: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val isOnline = OnlineChecker.isOnline(url)
            onResult(isOnline)
        }
    }

    fun urlPreview(url: String, onResult: suspend (UrlPreviewState) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            UrlCachedPreviewer.previewInfo(url, onResult)
        }
    }

    fun loadReactionTo(note: Note?, onNewReactionType: (String?) -> Unit) {
        if (note == null) return

        viewModelScope.launch(Dispatchers.Default) {
            onNewReactionType(note.getReactionBy(userProfile()))
        }
    }

    fun verifyNip05(userMetadata: UserMetadata, pubkeyHex: String, onResult: (Boolean) -> Unit) {
        val nip05 = userMetadata.nip05?.ifBlank { null } ?: return

        viewModelScope.launch(Dispatchers.IO) {
            Nip05Verifier().verifyNip05(
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
                }
            )
        }
    }

    fun retrieveRelayDocument(
        dirtyUrl: String,
        onInfo: (RelayInformation) -> Unit,
        onError: (String, Nip11Retriever.ErrorCode, String?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            Nip11CachedRetriever.loadRelayInfo(dirtyUrl, onInfo, onError)
        }
    }

    class Factory(val account: Account) : ViewModelProvider.Factory {
        override fun <AccountViewModel : ViewModel> create(modelClass: Class<AccountViewModel>): AccountViewModel {
            return AccountViewModel(account) as AccountViewModel
        }
    }
}
