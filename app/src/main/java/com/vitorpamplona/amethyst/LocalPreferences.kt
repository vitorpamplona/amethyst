package com.vitorpamplona.amethyst

import android.content.Context
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.DefaultChannels
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.toByteArray
import com.vitorpamplona.amethyst.ui.navigation.Route
import nostr.postr.Persona
import nostr.postr.toHex

class LocalPreferences(context: Context) {
  val encryptedPreferences = EncryptedStorage().preferences(context)

  fun clearEncryptedStorage() {
    encryptedPreferences.edit().apply {
      remove("nostr_privkey")
      remove("nostr_pubkey")
      remove("following_channels")
      remove("hidden_users")
    }.apply()
  }

  fun saveToEncryptedStorage(account: Account) {
    encryptedPreferences.edit().apply {
      account.loggedIn.privKey?.let { putString("nostr_privkey", it.toHex()) }
      account.loggedIn.pubKey.let { putString("nostr_pubkey", it.toHex()) }
      account.followingChannels.let { putStringSet("following_channels", it) }
      account.hiddenUsers.let { putStringSet("hidden_users", it) }
    }.apply()
  }

  fun loadFromEncryptedStorage(): Account? {
    encryptedPreferences.apply {
      val privKey = getString("nostr_privkey", null)
      val pubKey = getString("nostr_pubkey", null)
      val followingChannels = getStringSet("following_channels", DefaultChannels)?.toMutableSet() ?: DefaultChannels.toMutableSet()
      val hiddenUsers = getStringSet("hidden_users", emptySet())?.toMutableSet() ?: mutableSetOf()

      if (pubKey != null) {
        return Account(
          Persona(privKey = privKey?.toByteArray(), pubKey = pubKey.toByteArray()),
          followingChannels,
          hiddenUsers
        )
      } else {
        return null
      }
    }
  }

  fun saveLastRead(route: String, timestampInSecs: Long) {
    encryptedPreferences.edit().apply {
      putLong("last_read_route_${route}", timestampInSecs)
    }.apply()
  }

  fun loadLastRead(route: String): Long {
    encryptedPreferences.run {
      return getLong("last_read_route_${route}", 0)
    }
  }

}