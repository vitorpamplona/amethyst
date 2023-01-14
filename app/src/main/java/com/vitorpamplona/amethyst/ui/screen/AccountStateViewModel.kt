package com.vitorpamplona.amethyst.ui.screen

import androidx.lifecycle.ViewModel
import androidx.security.crypto.EncryptedSharedPreferences
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.toByteArray
import com.vitorpamplona.amethyst.service.NostrAccountDataSource
import com.vitorpamplona.amethyst.service.NostrChatroomListDataSource
import com.vitorpamplona.amethyst.service.NostrGlobalDataSource
import com.vitorpamplona.amethyst.service.NostrHomeDataSource
import com.vitorpamplona.amethyst.service.NostrNotificationDataSource
import com.vitorpamplona.amethyst.service.NostrSingleEventDataSource
import com.vitorpamplona.amethyst.service.NostrSingleUserDataSource
import com.vitorpamplona.amethyst.service.NostrThreadDataSource
import fr.acinq.secp256k1.Hex
import java.util.regex.Pattern
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import nostr.postr.Persona
import nostr.postr.bechToBytes
import nostr.postr.toHex

class AccountStateViewModel(private val encryptedPreferences: EncryptedSharedPreferences): ViewModel() {
  private val _accountContent = MutableStateFlow<AccountState>(AccountState.LoggedOff)
  val accountContent = _accountContent.asStateFlow()

  init {
    // pulls account from storage.
    loadFromEncryptedStorage()?.let { login(it) }
  }

  fun login(key: String) {
    val pattern = Pattern.compile(".+@.+\\.[a-z]+")

    login(
      if (key.startsWith("nsec")) {
        Persona(privKey = key.bechToBytes())
      } else if (key.startsWith("npub")) {
        Persona(pubKey = key.bechToBytes())
      } else if (pattern.matcher(key).matches()) {
        // Evaluate NIP-5
        Persona()
      } else {
        Persona(Hex.decode(key))
      }
    )
  }

  fun login(person: Persona) {
    val loggedIn = Account(person)

    if (person.privKey != null)
      _accountContent.update { AccountState.LoggedIn ( loggedIn ) }
    else
      _accountContent.update { AccountState.LoggedInViewOnly ( Account(person) ) }

    saveToEncryptedStorage(person)

    NostrAccountDataSource.account = loggedIn
    NostrHomeDataSource.account = loggedIn
    NostrNotificationDataSource.account = loggedIn
    NostrChatroomListDataSource.account = loggedIn

    NostrAccountDataSource.start()
    NostrGlobalDataSource.start()
    NostrHomeDataSource.start()
    NostrNotificationDataSource.start()
    NostrSingleEventDataSource.start()
    NostrSingleUserDataSource.start()
    NostrThreadDataSource.start()
    NostrChatroomListDataSource.start()
  }

  fun newKey() {
    login(Persona())
  }

  fun logOff() {
    _accountContent.update { AccountState.LoggedOff }

    clearEncryptedStorage()
  }

  fun clearEncryptedStorage() {
    encryptedPreferences.edit().apply {
      remove("nostr_privkey")
      remove("nostr_pubkey")
    }.apply()
  }

  fun saveToEncryptedStorage(login: Persona) {
    encryptedPreferences.edit().apply {
      login.privKey?.let { putString("nostr_privkey", it.toHex()) }
      login.pubKey.let { putString("nostr_pubkey", it.toHex()) }
    }.apply()
  }

  fun loadFromEncryptedStorage(): Persona? {
    encryptedPreferences.apply {
      val privKey = getString("nostr_privkey", null)
      val pubKey = getString("nostr_pubkey", null)

      if (pubKey != null) {
        return Persona(privKey = privKey?.toByteArray(), pubKey = pubKey.toByteArray())
      } else {
        return null
      }
    }
  }
}