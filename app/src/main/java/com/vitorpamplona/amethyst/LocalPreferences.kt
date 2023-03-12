package com.vitorpamplona.amethyst

import android.annotation.SuppressLint
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
import fr.acinq.secp256k1.Hex
import nostr.postr.Persona
import nostr.postr.toHex
import nostr.postr.toNpub
import java.io.File
import java.util.Locale

// Release mode (!BuildConfig.DEBUG) always uses encrypted preferences
// To use plaintext SharedPreferences for debugging, set this to true
// It will only apply in Debug builds
private const val DEBUG_PLAINTEXT_PREFERENCES = false
private const val OLD_PREFS_FILENAME = "secret_keeper"

data class AccountInfo(
    val npub: String,
    val current: Boolean,
    val displayName: String?,
    val profilePicture: String?
)

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

    private val prefsDirPath: String
        get() = "${Amethyst.instance.filesDir.parent}/shared_prefs/"

    private fun addAccount(npub: String) {
        val accounts = savedAccounts.toMutableSet()
        accounts.add(npub)
        val prefs = encryptedPreferences()
        prefs.edit().apply {
            putStringSet(PrefKeys.SAVED_ACCOUNTS, accounts)
        }.apply()
    }

    private fun setCurrentAccount(account: Account) {
        val npub = account.userProfile().pubkeyNpub()
        currentAccount = npub
        addAccount(npub)
    }

    fun switchToAccount(npub: String) {
        currentAccount = npub
    }

    /**
     * Removes the account from the app level shared preferences
     */
    private fun removeAccount(npub: String) {
        val accounts = savedAccounts.toMutableSet()
        accounts.remove(npub)
        val prefs = encryptedPreferences()
        prefs.edit().apply {
            putStringSet(PrefKeys.SAVED_ACCOUNTS, accounts)
        }.apply()
    }

    /**
     * Deletes the npub-specific shared preference file
     */
    private fun deleteUserPreferenceFile(npub: String) {
        val prefsDir = File(prefsDirPath)
        prefsDir.list()?.forEach {
            if (it.contains(npub)) {
                File(prefsDir, it).delete()
            }
        }
    }

    private fun encryptedPreferences(npub: String? = null): SharedPreferences {
        return if (BuildConfig.DEBUG && DEBUG_PLAINTEXT_PREFERENCES) {
            val preferenceFile = if (npub == null) "debug_prefs" else "debug_prefs_$npub"
            Amethyst.instance.getSharedPreferences(preferenceFile, Context.MODE_PRIVATE)
        } else {
            return EncryptedStorage.preferences(npub)
        }
    }

    /**
     * Clears the preferences for a given npub, deletes the preferences xml file,
     * and switches the user to the first account in the list if it exists
     *
     * We need to use `commit()` to write changes to disk and release the file
     * lock so that it can be deleted. If we use `apply()` there is a race
     * condition and the file will probably not be deleted
     */
    @SuppressLint("ApplySharedPref")
    fun updatePrefsForLogout(npub: String) {
        val userPrefs = encryptedPreferences(npub)
        userPrefs.edit().clear().commit()
        removeAccount(npub)
        deleteUserPreferenceFile(npub)

        if (savedAccounts.isEmpty()) {
            val appPrefs = encryptedPreferences()
            appPrefs.edit().clear().apply()
        } else if (currentAccount == npub) {
            currentAccount = savedAccounts.elementAt(0)
        }
    }

    fun updatePrefsForLogin(account: Account) {
        setCurrentAccount(account)
        saveToEncryptedStorage(account)
    }

    fun allSavedAccounts(): List<AccountInfo> {
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
                    Event.gson.fromJson(it, Event::class.java)
                        .getRefinedEvent(true) as ContactListEvent
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                null
            }

            val languagePreferences = try {
                getString(PrefKeys.LANGUAGE_PREFS, null)?.let {
                    gson.fromJson(
                        it,
                        object : TypeToken<Map<String, String>>() {}.type
                    ) as Map<String, String>
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

    fun migrateSingleUserPrefs() {
        if (currentAccount != null) return

        val pubkey = encryptedPreferences().getString(PrefKeys.NOSTR_PUBKEY, null) ?: return
        val npub = Hex.decode(pubkey).toNpub()

        val stringPrefs = listOf(
            PrefKeys.NOSTR_PRIVKEY,
            PrefKeys.NOSTR_PUBKEY,
            PrefKeys.RELAYS,
            PrefKeys.LANGUAGE_PREFS,
            PrefKeys.TRANSLATE_TO,
            PrefKeys.ZAP_AMOUNTS,
            PrefKeys.LATEST_CONTACT_LIST
        )

        val stringSetPrefs = listOf(
            PrefKeys.FOLLOWING_CHANNELS,
            PrefKeys.HIDDEN_USERS,
            PrefKeys.DONT_TRANSLATE_FROM
        )

        encryptedPreferences().apply {
            val appPrefs = this
            encryptedPreferences(npub).edit().apply {
                val userPrefs = this

                stringPrefs.forEach { userPrefs.putString(it, appPrefs.getString(it, null)) }
                stringSetPrefs.forEach { userPrefs.putStringSet(it, appPrefs.getStringSet(it, null)) }
                userPrefs.putBoolean(
                    PrefKeys.HIDE_DELETE_REQUEST_INFO,
                    appPrefs.getBoolean(PrefKeys.HIDE_DELETE_REQUEST_INFO, false)
                )
            }.apply()
        }

        encryptedPreferences().edit().clear().apply()
        addAccount(npub)
        currentAccount = npub
    }
}
