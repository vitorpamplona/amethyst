package com.vitorpamplona.amethyst

import android.content.Context
import android.content.SharedPreferences
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
import java.util.Locale

// MUST BE SET TO FALSE FOR PRODUCTION!!!!!
const val DEBUG_PLAINTEXT_PREFERENCES = true

data class AccountInfo(val npub: String, val current: Boolean, val displayName: String?, val profilePicture: String?)

private object PrefKeys {
    const val CURRENT_ACCOUNT = "currently_logged_in_account"
    const val SAVED_ACCOUNTS = "all_saved_accounts"
    const val NOSTR_PRIVKEY = "nostr_privkey"
    const val NOSTR_PUBKEY = "nostr_pubkey"
    const val DISPLAY_NAME = "display_name"
    const val PROFILE_PICTURE_URL = "profile_picture"
    const val FOLLOWING_CHANNELS = "following_channels"
    const val HIDDEN_USERS = "hidden_users"
    const val RELAYS = "relays"
    const val DONT_TRANSLATE_FROM = "dontTranslateFrom"
    const val LANGUAGE_PREFS = "languagePreferences"
    const val TRANSLATE_TO = "translateTo"
    const val ZAP_AMOUNTS = "zapAmounts"
    const val LATEST_CONTACT_LIST = "latestContactList"
    const val HIDE_DELETE_REQUEST_INFO = "hideDeleteRequestInfo"
    val LAST_READ: (String) -> String = { route -> "last_read_route_$route" }
}

private val gson = GsonBuilder().create()

object LocalPreferences {
    private var currentAccount: String?
        get() = encryptedPreferences().getString(PrefKeys.CURRENT_ACCOUNT, null)
        set(npub) {
            val prefs = encryptedPreferences()
            prefs.edit().apply {
                putString(PrefKeys.CURRENT_ACCOUNT, npub)
            }.apply()
        }

    private val savedAccounts: Set<String>
        get() = encryptedPreferences().getStringSet(PrefKeys.SAVED_ACCOUNTS, null) ?: setOf()

    private fun addAccount(npub: String) {
        val accounts = savedAccounts.toMutableSet()
        accounts.add(npub)
        val prefs = encryptedPreferences()
        prefs.edit().apply {
            putStringSet(PrefKeys.SAVED_ACCOUNTS, accounts)
        }.apply()
    }

    private fun removeAccount(npub: String) {
        val accounts = savedAccounts.toMutableSet()
        accounts.remove(npub)
        val prefs = encryptedPreferences()
        prefs.edit().apply {
            putStringSet(PrefKeys.SAVED_ACCOUNTS, accounts)
        }.apply()
    }

    private fun encryptedPreferences(npub: String? = null): SharedPreferences {
        return if (DEBUG_PLAINTEXT_PREFERENCES) {
            val preferenceFile = if (npub == null) "testing_only" else "testing_only_$npub"
            Amethyst.instance.getSharedPreferences(preferenceFile, Context.MODE_PRIVATE)
        } else {
            return EncryptedStorage.preferences(npub)
        }
    }

    fun clearEncryptedStorage(npub: String? = null) {
        val encPrefs = encryptedPreferences(npub)
        encPrefs.edit().apply {
            encPrefs.all.keys.forEach {
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
        return savedAccounts.map { npub ->
            val prefs = encryptedPreferences(npub)

            AccountInfo(
                npub = npub,
                current = npub == currentAccount,
                displayName = prefs.getString(PrefKeys.DISPLAY_NAME, null),
                profilePicture = prefs.getString(PrefKeys.PROFILE_PICTURE_URL, null)
            )
        }
    }

    fun setCurrentAccount(account: Account) {
        val npub = account.userProfile().pubkeyNpub()
        currentAccount = npub
        addAccount(npub)
    }

    fun saveToEncryptedStorage(account: Account) {
        val prefs = encryptedPreferences(account.userProfile().pubkeyNpub())
        prefs.edit().apply {
            account.loggedIn.privKey?.let { putString(PrefKeys.NOSTR_PRIVKEY, it.toHex()) }
            account.loggedIn.pubKey.let { putString(PrefKeys.NOSTR_PUBKEY, it.toHex()) }
            putStringSet(PrefKeys.FOLLOWING_CHANNELS, account.followingChannels)
            putStringSet(PrefKeys.HIDDEN_USERS, account.hiddenUsers)
            putString(PrefKeys.RELAYS, gson.toJson(account.localRelays))
            putStringSet(PrefKeys.DONT_TRANSLATE_FROM, account.dontTranslateFrom)
            putString(PrefKeys.LANGUAGE_PREFS, gson.toJson(account.languagePreferences))
            putString(PrefKeys.TRANSLATE_TO, account.translateTo)
            putString(PrefKeys.ZAP_AMOUNTS, gson.toJson(account.zapAmountChoices))
            putString(PrefKeys.LATEST_CONTACT_LIST, Event.gson.toJson(account.backupContactList))
            putBoolean(PrefKeys.HIDE_DELETE_REQUEST_INFO, account.hideDeleteRequestInfo)
            putString(PrefKeys.DISPLAY_NAME, account.userProfile().toBestDisplayName())
            putString(PrefKeys.PROFILE_PICTURE_URL, account.userProfile().profilePicture())
        }.apply()
    }

    fun loadFromEncryptedStorage(): Account? {
        encryptedPreferences(currentAccount).apply {
            val pubKey = getString(PrefKeys.NOSTR_PUBKEY, null) ?: return null
            val privKey = getString(PrefKeys.NOSTR_PRIVKEY, null)
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
                } ?: mapOf()
            } catch (e: Throwable) {
                e.printStackTrace()
                mapOf()
            }

            val hideDeleteRequestInfo = getBoolean(PrefKeys.HIDE_DELETE_REQUEST_INFO, false)

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
        encryptedPreferences(currentAccount).edit().apply {
            putLong(PrefKeys.LAST_READ(route), timestampInSecs)
        }.apply()
    }

    fun loadLastRead(route: String): Long {
        encryptedPreferences(currentAccount).run {
            return getLong(PrefKeys.LAST_READ(route), 0)
        }
    }
}
