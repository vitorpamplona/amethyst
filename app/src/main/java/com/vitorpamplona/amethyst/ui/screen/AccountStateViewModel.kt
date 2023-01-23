package com.vitorpamplona.amethyst.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import com.vitorpamplona.amethyst.ServiceManager
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.DefaultChannels
import com.vitorpamplona.amethyst.model.toByteArray
import com.vitorpamplona.amethyst.service.NostrAccountDataSource
import com.vitorpamplona.amethyst.service.NostrChatroomListDataSource
import com.vitorpamplona.amethyst.service.NostrGlobalDataSource
import com.vitorpamplona.amethyst.service.NostrHomeDataSource
import com.vitorpamplona.amethyst.service.NostrNotificationDataSource
import com.vitorpamplona.amethyst.service.NostrSingleEventDataSource
import com.vitorpamplona.amethyst.service.NostrSingleUserDataSource
import com.vitorpamplona.amethyst.service.NostrThreadDataSource
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.ui.MainActivity
import fr.acinq.secp256k1.Hex
import java.util.regex.Pattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nostr.postr.Persona
import nostr.postr.bechToBytes
import nostr.postr.toHex

class AccountStateViewModel(
  private val encryptedPreferences: EncryptedSharedPreferences
): ViewModel() {
  private val _accountContent = MutableStateFlow<AccountState>(AccountState.LoggedOff)
  val accountContent = _accountContent.asStateFlow()

  init {
    // pulls account from storage.
    loadFromEncryptedStorage()?.let {
      login(it)
    }
  }

  fun login(key: String) {
    val pattern = Pattern.compile(".+@.+\\.[a-z]+")

    val account =
      if (key.startsWith("nsec")) {
        Account(Persona(privKey = key.bechToBytes()))
      } else if (key.startsWith("npub")) {
        Account(Persona(pubKey = key.bechToBytes()))
      } else if (pattern.matcher(key).matches()) {
        // Evaluate NIP-5
        Account(Persona())
      } else {
        Account(Persona(Hex.decode(key)))
      }

    saveToEncryptedStorage(account)

    login(account)
  }

  fun newKey() {
    val account = Account(Persona())
    saveToEncryptedStorage(account)
    login(account)
  }

  fun login(account: Account) {
    if (account.loggedIn.privKey != null)
      _accountContent.update { AccountState.LoggedIn ( account ) }
    else
      _accountContent.update { AccountState.LoggedInViewOnly ( account ) }

    viewModelScope.launch(Dispatchers.IO) {
      ServiceManager.start(account)
    }
  }

  fun logOff() {
    _accountContent.update { AccountState.LoggedOff }

    clearEncryptedStorage()
  }

  fun clearEncryptedStorage() {
    encryptedPreferences.edit().apply {
      remove("nostr_privkey")
      remove("nostr_pubkey")
      remove("following_channels")
    }.apply()
  }

  fun saveToEncryptedStorage(account: Account) {
    encryptedPreferences.edit().apply {
      account.loggedIn.privKey?.let { putString("nostr_privkey", it.toHex()) }
      account.loggedIn.pubKey.let { putString("nostr_pubkey", it.toHex()) }
      account.followingChannels.let { putStringSet("following_channels", account.followingChannels) }
    }.apply()
  }

  fun loadFromEncryptedStorage(): Account? {
    encryptedPreferences.apply {
      val privKey = getString("nostr_privkey", null)
      val pubKey = getString("nostr_pubkey", null)
      val followingChannels = getStringSet("following_channels", DefaultChannels)?.toMutableSet() ?: DefaultChannels.toMutableSet()

      if (pubKey != null) {
        return Account(Persona(privKey = privKey?.toByteArray(), pubKey = pubKey.toByteArray()), followingChannels)
      } else {
        return null
      }
    }
  }
}