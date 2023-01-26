package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AccountState
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User

class AccountViewModel(private val account: Account): ViewModel() {
  val accountLiveData: LiveData<AccountState> = account.live.map { it }

  fun reactTo(note: Note) {
    account.reactTo(note)
  }

  fun report(note: Note) {
    account.report(note)
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
}