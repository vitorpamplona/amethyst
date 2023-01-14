package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AccountState
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.UserState
import com.vitorpamplona.amethyst.service.relays.Client
import nostr.postr.events.PrivateDmEvent

class AccountViewModel(private val account: Account): ViewModel() {
  val accountLiveData: LiveData<AccountState> = Transformations.map(account.live) { it }
  val userLiveData: LiveData<UserState> = Transformations.map(account.userProfile().live) { it }

  fun reactTo(note: Note) {
    account.reactTo(note)
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
}