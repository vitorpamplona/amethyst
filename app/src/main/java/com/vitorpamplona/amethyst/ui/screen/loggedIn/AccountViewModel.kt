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
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.lnurl.LightningAddressResolver
import com.vitorpamplona.amethyst.service.model.Event
import com.vitorpamplona.amethyst.service.model.LnZapEvent
import com.vitorpamplona.amethyst.service.model.PayInvoiceErrorResponse
import com.vitorpamplona.amethyst.service.model.ReportEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Locale

@Stable
class AccountViewModel(val account: Account) : ViewModel() {
    val accountLiveData: LiveData<AccountState> = account.live.map { it }
    val accountLanguagesLiveData: LiveData<AccountState> = account.liveLanguages.map { it }

    fun isWriteable(): Boolean {
        return account.isWriteable()
    }

    fun userProfile(): User {
        return account.userProfile()
    }

    fun reactTo(note: Note) {
        account.reactTo(note)
    }

    fun hasReactedTo(baseNote: Note): Boolean {
        return account.hasReacted(baseNote)
    }

    fun deleteReactionTo(note: Note) {
        account.delete(account.reactionTo(note))
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

    class Factory(val account: Account) : ViewModelProvider.Factory {
        override fun <AccountViewModel : ViewModel> create(modelClass: Class<AccountViewModel>): AccountViewModel {
            return AccountViewModel(account) as AccountViewModel
        }
    }
}
