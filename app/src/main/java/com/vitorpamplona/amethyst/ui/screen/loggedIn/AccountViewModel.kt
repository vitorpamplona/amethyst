package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AccountState
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.UserState
import com.vitorpamplona.amethyst.service.lnurl.LightningAddressResolver
import com.vitorpamplona.amethyst.service.model.Event
import com.vitorpamplona.amethyst.service.model.LnZapEvent
import com.vitorpamplona.amethyst.service.model.PayInvoiceErrorResponse
import com.vitorpamplona.amethyst.service.model.ReportEvent
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

    fun calculateIfNoteWasZappedByAccount(zappedNote: Note): Boolean {
        return account.calculateIfNoteWasZappedByAccount(zappedNote)
    }

    fun calculateZapAmount(zappedNote: Note): BigDecimal {
        return account.calculateZappedAmount(zappedNote)
    }

    fun zap(note: Note, amount: Long, pollOption: Int?, message: String, context: Context, onError: (String) -> Unit, onProgress: (percent: Float) -> Unit, zapType: LnZapEvent.ZapType) {
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
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("lightning:$it"))
                        ContextCompat.startActivity(context, intent, null)
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
        account.report(user, type)
    }

    fun boost(note: Note) {
        account.boost(note)
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

    fun hide(user: User) {
        account.hideUser(user.pubkeyHex)
    }

    fun show(user: User) {
        account.showUser(user.pubkeyHex)
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

    fun isNoteAcceptable(note: Note, onReady: (Boolean, Boolean, ImmutableSet<Note>) -> Unit) {
        val isFromLoggedIn = note.author?.pubkeyHex == userProfile().pubkeyHex
        val isFromLoggedInFollow = note.author?.let { userProfile().isFollowingCached(it) } ?: true

        if (isFromLoggedIn || isFromLoggedInFollow) {
            // No need to process if from trusted people
            onReady(true, true, persistentSetOf())
        } else {
            val newCanPreview = !note.hasAnyReports()

            val newIsAcceptable = account.isAcceptable(note)

            if (newCanPreview && newIsAcceptable) {
                // No need to process reports if nothing is wrong
                onReady(true, true, persistentSetOf())
            } else {
                val newRelevantReports = account.getRelevantReports(note)

                onReady(newIsAcceptable, newCanPreview, newRelevantReports.toImmutableSet())
            }
        }
    }

    class Factory(val account: Account) : ViewModelProvider.Factory {
        override fun <AccountViewModel : ViewModel> create(modelClass: Class<AccountViewModel>): AccountViewModel {
            return AccountViewModel(account) as AccountViewModel
        }
    }
}
