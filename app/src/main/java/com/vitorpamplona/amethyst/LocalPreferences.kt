package com.vitorpamplona.amethyst

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.GLOBAL_FOLLOWS
import com.vitorpamplona.amethyst.model.KIND3_FOLLOWS
import com.vitorpamplona.amethyst.model.RelaySetupInfo
import com.vitorpamplona.amethyst.model.hexToByteArray
import com.vitorpamplona.amethyst.service.HttpClient
import com.vitorpamplona.amethyst.service.model.ContactListEvent
import com.vitorpamplona.amethyst.service.model.Event
import com.vitorpamplona.amethyst.service.model.Event.Companion.getRefinedEvent
import com.vitorpamplona.amethyst.service.model.LnZapEvent
import com.vitorpamplona.amethyst.ui.actions.ServersAvailable
import com.vitorpamplona.amethyst.ui.note.Nip47URI
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
private const val DEBUG_PREFERENCES_NAME = "debug_prefs"

@Immutable
data class AccountInfo(
    val npub: String,
    val hasPrivKey: Boolean = false
)

private object PrefKeys {
    const val CURRENT_ACCOUNT = "currently_logged_in_account"
    const val SAVED_ACCOUNTS = "all_saved_accounts"
    const val NOSTR_PRIVKEY = "nostr_privkey"
    const val NOSTR_PUBKEY = "nostr_pubkey"
    const val FOLLOWING_CHANNELS = "following_channels"
    const val HIDDEN_USERS = "hidden_users"
    const val RELAYS = "relays"
    const val DONT_TRANSLATE_FROM = "dontTranslateFrom"
    const val LANGUAGE_PREFS = "languagePreferences"
    const val TRANSLATE_TO = "translateTo"
    const val ZAP_AMOUNTS = "zapAmounts"
    const val DEFAULT_ZAPTYPE = "defaultZapType"
    const val DEFAULT_FILE_SERVER = "defaultFileServer"
    const val DEFAULT_HOME_FOLLOW_LIST = "defaultHomeFollowList"
    const val DEFAULT_STORIES_FOLLOW_LIST = "defaultStoriesFollowList"
    const val DEFAULT_NOTIFICATION_FOLLOW_LIST = "defaultNotificationFollowList"
    const val ZAP_PAYMENT_REQUEST_SERVER = "zapPaymentServer"
    const val LATEST_CONTACT_LIST = "latestContactList"
    const val HIDE_DELETE_REQUEST_DIALOG = "hide_delete_request_dialog"
    const val HIDE_BLOCK_ALERT_DIALOG = "hide_block_alert_dialog"
    const val USE_PROXY = "use_proxy"
    const val PROXY_PORT = "proxy_port"
    const val SHOW_SENSITIVE_CONTENT = "show_sensitive_content"
    val LAST_READ: (String) -> String = { route -> "last_read_route_$route" }
}

private val gson = GsonBuilder().create()

object LocalPreferences {
    private const val comma = ","

    private var _currentAccount: String? = null

    private fun currentAccount(): String? {
        if (_currentAccount == null) {
            _currentAccount = encryptedPreferences().getString(PrefKeys.CURRENT_ACCOUNT, null)
        }
        return _currentAccount
    }

    private fun updateCurrentAccount(npub: String) {
        if (_currentAccount != npub) {
            _currentAccount = npub

            encryptedPreferences().edit().apply {
                putString(PrefKeys.CURRENT_ACCOUNT, npub)
            }.apply()
        }
    }

    private var _savedAccounts: List<String>? = null

    private fun savedAccounts(): List<String> {
        if (_savedAccounts == null) {
            _savedAccounts = encryptedPreferences()
                .getString(PrefKeys.SAVED_ACCOUNTS, null)?.split(comma) ?: listOf()
        }
        return _savedAccounts!!
    }

    private fun updateSavedAccounts(accounts: List<String>) {
        if (_savedAccounts != accounts) {
            _savedAccounts = accounts

            encryptedPreferences().edit().apply {
                putString(PrefKeys.SAVED_ACCOUNTS, accounts.joinToString(comma).ifBlank { null })
            }.apply()
        }
    }

    private val prefsDirPath: String
        get() = "${Amethyst.instance.filesDir.parent}/shared_prefs/"

    private fun addAccount(npub: String) {
        val accounts = savedAccounts().toMutableList()
        if (npub !in accounts) {
            accounts.add(npub)
            updateSavedAccounts(accounts)
        }
    }

    private fun setCurrentAccount(account: Account) {
        val npub = account.userProfile().pubkeyNpub()
        updateCurrentAccount(npub)
        addAccount(npub)
    }

    fun switchToAccount(npub: String) {
        updateCurrentAccount(npub)
    }

    /**
     * Removes the account from the app level shared preferences
     */
    private fun removeAccount(npub: String) {
        val accounts = savedAccounts().toMutableList()
        if (accounts.remove(npub)) {
            updateSavedAccounts(accounts)
        }
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
            val preferenceFile = if (npub == null) DEBUG_PREFERENCES_NAME else "${DEBUG_PREFERENCES_NAME}_$npub"
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

        if (savedAccounts().isEmpty()) {
            val appPrefs = encryptedPreferences()
            appPrefs.edit().clear().apply()
        } else if (currentAccount() == npub) {
            updateCurrentAccount(savedAccounts().elementAt(0))
        }
    }

    fun updatePrefsForLogin(account: Account) {
        setCurrentAccount(account)
        saveToEncryptedStorage(account)
    }

    fun allSavedAccounts(): List<AccountInfo> {
        return savedAccounts().map { npub ->
            AccountInfo(npub = npub)
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
            putString(PrefKeys.DEFAULT_ZAPTYPE, gson.toJson(account.defaultZapType))
            putString(PrefKeys.DEFAULT_FILE_SERVER, gson.toJson(account.defaultFileServer))
            putString(PrefKeys.DEFAULT_HOME_FOLLOW_LIST, account.defaultHomeFollowList)
            putString(PrefKeys.DEFAULT_STORIES_FOLLOW_LIST, account.defaultStoriesFollowList)
            putString(PrefKeys.DEFAULT_NOTIFICATION_FOLLOW_LIST, account.defaultNotificationFollowList)
            putString(PrefKeys.ZAP_PAYMENT_REQUEST_SERVER, gson.toJson(account.zapPaymentRequest))
            putString(PrefKeys.LATEST_CONTACT_LIST, Event.gson.toJson(account.backupContactList))
            putBoolean(PrefKeys.HIDE_DELETE_REQUEST_DIALOG, account.hideDeleteRequestDialog)
            putBoolean(PrefKeys.HIDE_BLOCK_ALERT_DIALOG, account.hideBlockAlertDialog)
            putBoolean(PrefKeys.USE_PROXY, account.proxy != null)
            putInt(PrefKeys.PROXY_PORT, account.proxyPort)

            if (account.showSensitiveContent == null) {
                remove(PrefKeys.SHOW_SENSITIVE_CONTENT)
            } else {
                putBoolean(PrefKeys.SHOW_SENSITIVE_CONTENT, account.showSensitiveContent!!)
            }
        }.apply()
    }

    fun loadFromEncryptedStorage(): Account? {
        val acc = loadFromEncryptedStorage(currentAccount())
        acc?.registerObservers()
        return acc
    }

    fun loadFromEncryptedStorage(npub: String?): Account? {
        encryptedPreferences(npub).apply {
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
            val defaultHomeFollowList = getString(PrefKeys.DEFAULT_HOME_FOLLOW_LIST, null) ?: KIND3_FOLLOWS
            val defaultStoriesFollowList = getString(PrefKeys.DEFAULT_STORIES_FOLLOW_LIST, null) ?: GLOBAL_FOLLOWS
            val defaultNotificationFollowList = getString(PrefKeys.DEFAULT_NOTIFICATION_FOLLOW_LIST, null) ?: GLOBAL_FOLLOWS

            val zapAmountChoices = gson.fromJson(
                getString(PrefKeys.ZAP_AMOUNTS, "[]"),
                object : TypeToken<List<Long>>() {}.type
            ) ?: listOf(500L, 1000L, 5000L)

            val defaultZapType = gson.fromJson(
                getString(PrefKeys.DEFAULT_ZAPTYPE, "PUBLIC"),
                object : TypeToken<LnZapEvent.ZapType>() {}.type
            ) ?: LnZapEvent.ZapType.PUBLIC

            val defaultFileServer = gson.fromJson(
                getString(PrefKeys.DEFAULT_FILE_SERVER, "NOSTR_BUILD"),
                object : TypeToken<ServersAvailable>() {}.type
            ) ?: ServersAvailable.NOSTR_BUILD

            val zapPaymentRequestServer = try {
                getString(PrefKeys.ZAP_PAYMENT_REQUEST_SERVER, null)?.let {
                    gson.fromJson(it, Nip47URI::class.java)
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                null
            }

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

            val hideDeleteRequestDialog = getBoolean(PrefKeys.HIDE_DELETE_REQUEST_DIALOG, false)
            val hideBlockAlertDialog = getBoolean(PrefKeys.HIDE_BLOCK_ALERT_DIALOG, false)
            val useProxy = getBoolean(PrefKeys.USE_PROXY, false)
            val proxyPort = getInt(PrefKeys.PROXY_PORT, 9050)
            val proxy = HttpClient.initProxy(useProxy, "127.0.0.1", proxyPort)

            val showSensitiveContent = if (contains(PrefKeys.SHOW_SENSITIVE_CONTENT)) {
                getBoolean(PrefKeys.SHOW_SENSITIVE_CONTENT, false)
            } else {
                null
            }

            val a = Account(
                Persona(privKey = privKey?.hexToByteArray(), pubKey = pubKey.hexToByteArray()),
                followingChannels,
                hiddenUsers,
                localRelays,
                dontTranslateFrom,
                languagePreferences,
                translateTo,
                zapAmountChoices,
                defaultZapType,
                defaultFileServer,
                defaultHomeFollowList,
                defaultStoriesFollowList,
                defaultNotificationFollowList,
                zapPaymentRequestServer,
                hideDeleteRequestDialog,
                hideBlockAlertDialog,
                latestContactList,
                proxy,
                proxyPort,
                showSensitiveContent
            )

            return a
        }
    }

    fun saveLastRead(route: String, timestampInSecs: Long) {
        encryptedPreferences(currentAccount()).edit().apply {
            putLong(PrefKeys.LAST_READ(route), timestampInSecs)
        }.apply()
    }

    fun loadLastRead(route: String): Long {
        encryptedPreferences(currentAccount()).run {
            return getLong(PrefKeys.LAST_READ(route), 0)
        }
    }

    fun migrateSingleUserPrefs() {
        if (currentAccount() != null) return

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
                    PrefKeys.HIDE_DELETE_REQUEST_DIALOG,
                    appPrefs.getBoolean(PrefKeys.HIDE_DELETE_REQUEST_DIALOG, false)
                )
            }.apply()
        }

        encryptedPreferences().edit().clear().apply()
        addAccount(npub)
        updateCurrentAccount(npub)
    }
}
