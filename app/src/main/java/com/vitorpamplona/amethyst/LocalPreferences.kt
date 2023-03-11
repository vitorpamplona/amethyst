package com.vitorpamplona.amethyst

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.RelaySetupInfo
import com.vitorpamplona.amethyst.model.toByteArray
import com.vitorpamplona.amethyst.service.model.ContactListEvent
import com.vitorpamplona.amethyst.service.model.Event
import com.vitorpamplona.amethyst.service.model.Event.Companion.getRefinedEvent
import nostr.postr.Persona
import nostr.postr.toHex
import nostr.postr.toNpub
import java.util.Locale

data class AccountInfo(val npub: String, val current: Boolean, val displayName: String?, val profilePicture: String?)

class LocalPreferences(context: Context) {

    private fun prefKeysForAccount(npub: String) = object {
        val NOSTR_PRIVKEY = "$npub/nostr_privkey"
        val NOSTR_PUBKEY = "$npub/nostr_pubkey"
        val DISPLAY_NAME = "$npub/display_name"
        val PROFILE_PICTURE_URL = "$npub/profile_picture"
        val FOLLOWING_CHANNELS = "$npub/following_channels"
        val HIDDEN_USERS = "$npub/hidden_users"
        val RELAYS = "$npub/relays"
        val DONT_TRANSLATE_FROM = "$npub/dontTranslateFrom"
        val LANGUAGE_PREFS = "$npub/languagePreferences"
        val TRANSLATE_TO = "$npub/translateTo"
        val ZAP_AMOUNTS = "$npub/zapAmounts"
        val LATEST_CONTACT_LIST = "$npub/latestContactList"
        val HIDE_DELETE_REQUEST_INFO = "$npub/hideDeleteRequestInfo"
//        val LAST_READ: (String) -> String = { route -> "$npub/last_read_route_$route" }
    }

    private object PrefKeys {
        const val CURRENT_ACCOUNT = "currentlyLoggedInAccount"

//        val NOSTR_PRIVKEY = "nostr_privkey"
//        val NOSTR_PUBKEY = "nostr_pubkey"
//        val FOLLOWING_CHANNELS = "following_channels"
//        val HIDDEN_USERS = "hidden_users"
//        val RELAYS = "relays"
//        val DONT_TRANSLATE_FROM = "dontTranslateFrom"
//        val LANGUAGE_PREFS = "languagePreferences"
//        val TRANSLATE_TO = "translateTo"
//        val ZAP_AMOUNTS = "zapAmounts"
//        val LATEST_CONTACT_LIST = "latestContactList"
//        val HIDE_DELETE_REQUEST_INFO = "hideDeleteRequestInfo"
        val LAST_READ: (String) -> String = { route -> "last_read_route_$route" }
    }

    private val encryptedPreferences = EncryptedStorage.preferences(context)
    private val gson = GsonBuilder().create()

    fun clearEncryptedStorage() {
        encryptedPreferences.edit().apply {
            encryptedPreferences.all.keys.forEach {
                remove(it)
            }
//            encryptedPreferences.all.keys.filter {
//                it.startsWith(npub)
//            }.forEach {
//                remove(it)
//            }
        }.apply()
    }

    fun findAllLocalAccounts(): List<AccountInfo> {
        encryptedPreferences.apply {
            val currentAccount = getString(PrefKeys.CURRENT_ACCOUNT, null)
            return encryptedPreferences.all.keys.filter {
                it.endsWith("nostr_pubkey")
            }.map {
                val npub = it.substringBefore("/")
                val myPrefs = prefKeysForAccount(npub)
                AccountInfo(
                    npub,
                    npub == currentAccount,
                    getString(myPrefs.DISPLAY_NAME, null),
                    getString(myPrefs.PROFILE_PICTURE_URL, null)
                )
            }
        }
    }

    fun saveToEncryptedStorage(account: Account) {
        val npub = account.loggedIn.pubKey.toNpub()
        val myPrefs = prefKeysForAccount(npub)

        encryptedPreferences.edit().apply {
            putString(PrefKeys.CURRENT_ACCOUNT, npub)
            account.loggedIn.privKey?.let { putString(myPrefs.NOSTR_PRIVKEY, it.toHex()) }
            account.loggedIn.pubKey.let { putString(myPrefs.NOSTR_PUBKEY, it.toHex()) }
            putStringSet(myPrefs.FOLLOWING_CHANNELS, account.followingChannels)
            putStringSet(myPrefs.HIDDEN_USERS, account.hiddenUsers)
            putString(myPrefs.RELAYS, gson.toJson(account.localRelays))
            putStringSet(myPrefs.DONT_TRANSLATE_FROM, account.dontTranslateFrom)
            putString(myPrefs.LANGUAGE_PREFS, gson.toJson(account.languagePreferences))
            putString(myPrefs.TRANSLATE_TO, account.translateTo)
            putString(myPrefs.ZAP_AMOUNTS, gson.toJson(account.zapAmountChoices))
            putString(myPrefs.LATEST_CONTACT_LIST, Event.gson.toJson(account.backupContactList))
            putBoolean(myPrefs.HIDE_DELETE_REQUEST_INFO, account.hideDeleteRequestInfo)
        }.apply()
    }

    fun saveCurrentAccountMetadata(account: Account) {
        val myPrefs = prefKeysForAccount(account.loggedIn.pubKey.toNpub())

        encryptedPreferences.edit().apply {
            putString(myPrefs.DISPLAY_NAME, account.userProfile().toBestDisplayName())
            putString(myPrefs.PROFILE_PICTURE_URL, account.userProfile().profilePicture())
        }.apply()
    }

    fun loadFromEncryptedStorage(): Account? {
        encryptedPreferences.apply {
            val npub = getString(PrefKeys.CURRENT_ACCOUNT, null) ?: return null
            val myPrefs = prefKeysForAccount(npub)

            val pubKey = getString(myPrefs.NOSTR_PUBKEY, null) ?: return null
            val privKey = getString(myPrefs.NOSTR_PRIVKEY, null)
            val followingChannels = getStringSet(myPrefs.FOLLOWING_CHANNELS, null) ?: setOf()
            val hiddenUsers = getStringSet(myPrefs.HIDDEN_USERS, emptySet()) ?: setOf()
            val localRelays = gson.fromJson(
                getString(myPrefs.RELAYS, "[]"),
                object : TypeToken<Set<RelaySetupInfo>>() {}.type
            ) ?: setOf<RelaySetupInfo>()

            val dontTranslateFrom = getStringSet(myPrefs.DONT_TRANSLATE_FROM, null) ?: setOf()
            val translateTo = getString(myPrefs.TRANSLATE_TO, null) ?: Locale.getDefault().language

            val zapAmountChoices = gson.fromJson(
                getString(myPrefs.ZAP_AMOUNTS, "[]"),
                object : TypeToken<List<Long>>() {}.type
            ) ?: listOf(500L, 1000L, 5000L)

            val latestContactList = try {
                getString(myPrefs.LATEST_CONTACT_LIST, null)?.let {
                    Event.gson.fromJson(it, Event::class.java).getRefinedEvent(true) as ContactListEvent
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                null
            }

            val languagePreferences = try {
                getString(myPrefs.LANGUAGE_PREFS, null)?.let {
                    gson.fromJson(it, object : TypeToken<Map<String, String>>() {}.type) as Map<String, String>
                } ?: mapOf()
            } catch (e: Throwable) {
                e.printStackTrace()
                mapOf()
            }

            val hideDeleteRequestInfo = getBoolean(myPrefs.HIDE_DELETE_REQUEST_INFO, false)

            return Account(
                Persona(privKey = privKey?.toByteArray(), pubKey = pubKey.toByteArray()),
                followingChannels,
                hiddenUsers,
                localRelays,
                dontTranslateFrom,
                languagePreferences,
                translateTo,
                zapAmountChoices,
                hideDeleteRequestInfo,
                latestContactList
            )
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
