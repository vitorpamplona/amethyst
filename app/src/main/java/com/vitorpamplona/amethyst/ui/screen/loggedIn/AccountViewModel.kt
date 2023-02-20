package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.lnurl.LightningAddressResolver
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AccountState
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.model.ReportEvent
import java.util.Locale

class AccountViewModel(private val account: Account): ViewModel() {
  val accountLiveData: LiveData<AccountState> = account.live.map { it }
  val accountLanguagesLiveData: LiveData<AccountState> = account.liveLanguages.map { it }

  fun reactTo(note: Note) {
    account.reactTo(note)
  }

  fun zap(note: Note, amount: Long, message: String, context: Context, onError: (String) -> Unit) {
    val lud16 = note.author?.info?.lud16?.trim() ?: note.author?.info?.lud06?.trim()

    if (lud16.isNullOrBlank()) {
      onError("User does not have a lightning address setup to receive sats")
      return
    }

    val zapRequest = account.createZapRequestFor(note)

    LightningAddressResolver().lnAddressInvoice(lud16, amount, message, zapRequest?.toJson(),
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

  fun decrypt(note: Note): String? {
    return account.decryptContent(note)
  }

  fun hide(user: User, ctx: Context) {
    account.hideUser(user.pubkeyHex)
    LocalPreferences(ctx).saveToEncryptedStorage(account)
  }

  fun show(user: User, ctx: Context) {
    account.showUser(user.pubkeyHex)
    LocalPreferences(ctx).saveToEncryptedStorage(account)
  }

  fun translateTo(lang: Locale, ctx: Context) {
    account.updateTranslateTo(lang.language)
    LocalPreferences(ctx).saveToEncryptedStorage(account)
  }

  fun dontTranslateFrom(lang: String, ctx: Context) {
    account.addDontTranslateFrom(lang)
    LocalPreferences(ctx).saveToEncryptedStorage(account)
  }
}