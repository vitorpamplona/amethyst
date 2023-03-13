package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AccountState
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.lnurl.LightningAddressResolver
import com.vitorpamplona.amethyst.service.model.ReportEvent
import java.util.Locale

class AccountViewModel(private val account: Account) : ViewModel() {
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

    fun zap(note: Note, amount: Long, message: String, context: Context, onError: (String) -> Unit) {
        val lud16 = note.author?.info?.lud16?.trim() ?: note.author?.info?.lud06?.trim()

        if (lud16.isNullOrBlank()) {
            onError(context.getString(R.string.user_does_not_have_a_lightning_address_setup_to_receive_sats))
            return
        }

        val zapRequest = account.createZapRequestFor(note)

        LightningAddressResolver().lnAddressInvoice(
            lud16,
            amount,
            message,
            zapRequest?.toJson(),
            onSuccess = {
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("lightning:$it"))
                    ContextCompat.startActivity(context, intent, null)
                }
            },
            onError = onError
        )
    }

    fun report(note: Note, type: ReportEvent.ReportType) {
        account.report(note, type)
    }

    fun report(user: User, type: ReportEvent.ReportType) {
        account.report(user, type)
    }

    fun boost(note: Note) {
        account.boost(note)
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

    fun isFollowing(user: User): Boolean {
        return account.userProfile().isFollowing(user)
    }

    fun hideDeleteRequestInfo(): Boolean {
        return account.hideDeleteRequestInfo
    }

    fun setHideDeleteRequestInfo() {
        account.setHideDeleteRequestInfo()
    }
}
