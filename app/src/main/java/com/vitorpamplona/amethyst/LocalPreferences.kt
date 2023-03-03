package com.vitorpamplona.amethyst

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.RelaySetupInfo
import com.vitorpamplona.amethyst.model.toByteArray
import java.util.Locale
import nostr.postr.Persona
import nostr.postr.events.ContactListEvent
import nostr.postr.events.Event
import nostr.postr.events.Event.Companion.getRefinedEvent
import nostr.postr.toHex

class LocalPreferences(context: Context) {
    private object PrefKeys {
        const val NOSTR_PRIVKEY = "nostr_privkey"
        const val NOSTR_PUBKEY = "nostr_pubkey"
        const val FOLLOWING_CHANNELS = "following_channels"
        const val HIDDEN_USERS = "hidden_users"
        const val RELAYS = "relays"
        const val DONT_TRANSLATE_FROM = "dontTranslateFrom"
        const val LANGUAGE_PREFS = "languagePreferences"
        const val TRANSLATE_TO = "translateTo"
        const val ZAP_AMOUNTS = "zapAmounts"
        const val LATEST_CONTACT_LIST = "latestContactList"
        val LAST_READ: (String) -> String = { route -> "last_read_route_${route}" }
    }

    private val encryptedPreferences = EncryptedStorage().preferences(context)
    private val gson = GsonBuilder().create()

    fun clearEncryptedStorage() {
        encryptedPreferences.edit().apply {
            encryptedPreferences.all.keys.forEach { remove(it) }
        }.apply()
    }

    fun saveToEncryptedStorage(account: Account) {
        encryptedPreferences.edit().apply {
            account.loggedIn.privKey?.let { putString(PrefKeys.NOSTR_PRIVKEY, it.toHex()) }
            account.loggedIn.pubKey.let { putString(PrefKeys.NOSTR_PUBKEY, it.toHex()) }
            account.followingChannels.let { putStringSet(PrefKeys.FOLLOWING_CHANNELS, it) }
            account.hiddenUsers.let { putStringSet(PrefKeys.HIDDEN_USERS, it) }
            account.localRelays.let { putString(PrefKeys.RELAYS, gson.toJson(it)) }
            account.dontTranslateFrom.let { putStringSet(PrefKeys.DONT_TRANSLATE_FROM, it) }
            account.languagePreferences.let { putString(PrefKeys.LANGUAGE_PREFS, gson.toJson(it)) }
            account.translateTo.let { putString(PrefKeys.TRANSLATE_TO, it) }
            account.zapAmountChoices.let { putString(PrefKeys.ZAP_AMOUNTS, gson.toJson(it)) }
            account.backupContactList.let { putString(PrefKeys.LATEST_CONTACT_LIST, Event.gson.toJson(it)) }
        }.apply()
    }

    fun loadFromEncryptedStorage(): Account? {
        encryptedPreferences.apply {
            val privKey = getString(PrefKeys.NOSTR_PRIVKEY, null)
            val pubKey = getString(PrefKeys.NOSTR_PUBKEY, null)
            val followingChannels = getStringSet(PrefKeys.FOLLOWING_CHANNELS, null) ?: setOf()
            val hiddenUsers = getStringSet(PrefKeys.HIDDEN_USERS, emptySet()) ?: setOf()
            val localRelays = gson.fromJson(
                getString(PrefKeys.RELAYS, "[]"),
                object : TypeToken<Set<RelaySetupInfo>>() {}.type
            ) ?: setOf<RelaySetupInfo>()

            val dontTranslateFrom = getStringSet(PrefKeys.DONT_TRANSLATE_FROM, null) ?: setOf()
            val translateTo = getString(PrefKeys.TRANSLATE_TO, null) ?: Locale.getDefault().language

            val zapAmountChoices = gson.fromJson(
                getString(PrefKeys.ZAP_AMOUNTS, "[]"),
                object : TypeToken<List<Long>>() {}.type
            ) ?: listOf(500L, 1000L, 5000L)

            val latestContactList = try {
                getString(PrefKeys.LATEST_CONTACT_LIST, null)?.let {
                    Event.gson.fromJson(it, Event::class.java).getRefinedEvent(true) as ContactListEvent
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                null
            }

            val languagePreferences = try {
                getString(PrefKeys.LANGUAGE_PREFS, null)?.let {
                    gson.fromJson(it, object : TypeToken<Map<String, String>>() {}.type) as Map<String, String>
                } ?: mapOf<String,String>()
            } catch (e: Throwable) {
                e.printStackTrace()
                mapOf<String,String>()
            }

            if (pubKey != null) {
                return Account(
                    Persona(privKey = privKey?.toByteArray(), pubKey = pubKey.toByteArray()),
                    followingChannels,
                    hiddenUsers,
                    localRelays,
                    dontTranslateFrom,
                    languagePreferences,
                    translateTo,
                    zapAmountChoices,
                    latestContactList
                )
            } else {
                return null
            }
        }
    }

    fun saveLastRead(route: String, timestampInSecs: Long) {
        encryptedPreferences.edit().apply {
            putLong(PrefKeys.LAST_READ(route), timestampInSecs)
        }.apply()
    }

    fun loadLastRead(route: String): Long {
        encryptedPreferences.run {
            return getLong(PrefKeys.LAST_READ(route), 0)
        }
    }

}