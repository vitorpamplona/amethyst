package com.vitorpamplona.amethyst

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.ConnectivityType
import com.vitorpamplona.amethyst.model.GLOBAL_FOLLOWS
import com.vitorpamplona.amethyst.model.KIND3_FOLLOWS
import com.vitorpamplona.amethyst.model.Nip47URI
import com.vitorpamplona.amethyst.model.RelaySetupInfo
import com.vitorpamplona.amethyst.model.ServersAvailable
import com.vitorpamplona.amethyst.model.Settings
import com.vitorpamplona.amethyst.model.parseConnectivityType
import com.vitorpamplona.amethyst.service.HttpClient
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.Hex
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.encoders.toNpub
import com.vitorpamplona.quartz.events.ContactListEvent
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.LnZapEvent
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
    const val FOLLOWING_COMMUNITIES = "following_communities"
    const val HIDDEN_USERS = "hidden_users"
    const val RELAYS = "relays"
    const val DONT_TRANSLATE_FROM = "dontTranslateFrom"
    const val LANGUAGE_PREFS = "languagePreferences"
    const val TRANSLATE_TO = "translateTo"
    const val ZAP_AMOUNTS = "zapAmounts"
    const val REACTION_CHOICES = "reactionChoices"
    const val DEFAULT_ZAPTYPE = "defaultZapType"
    const val DEFAULT_FILE_SERVER = "defaultFileServer"
    const val DEFAULT_HOME_FOLLOW_LIST = "defaultHomeFollowList"
    const val DEFAULT_STORIES_FOLLOW_LIST = "defaultStoriesFollowList"
    const val DEFAULT_NOTIFICATION_FOLLOW_LIST = "defaultNotificationFollowList"
    const val DEFAULT_DISCOVERY_FOLLOW_LIST = "defaultDiscoveryFollowList"
    const val ZAP_PAYMENT_REQUEST_SERVER = "zapPaymentServer"
    const val LATEST_CONTACT_LIST = "latestContactList"
    const val HIDE_DELETE_REQUEST_DIALOG = "hide_delete_request_dialog"
    const val HIDE_BLOCK_ALERT_DIALOG = "hide_block_alert_dialog"
    const val HIDE_NIP_24_WARNING_DIALOG = "hide_nip24_warning_dialog"
    const val USE_PROXY = "use_proxy"
    const val PROXY_PORT = "proxy_port"
    const val SHOW_SENSITIVE_CONTENT = "show_sensitive_content"
    const val WARN_ABOUT_REPORTS = "warn_about_reports"
    const val FILTER_SPAM_FROM_STRANGERS = "filter_spam_from_strangers"
    const val LAST_READ_PER_ROUTE = "last_read_route_per_route"
    const val AUTOMATICALLY_SHOW_IMAGES = "automatically_show_images"
    const val AUTOMATICALLY_START_PLAYBACK = "automatically_start_playback"
    const val THEME = "theme"
    const val PREFERRED_LANGUAGE = "preferred_Language"
    const val AUTOMATICALLY_LOAD_URL_PREVIEW = "automatically_load_url_preview"
    val LAST_READ: (String) -> String = { route -> "last_read_route_$route" }
}

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
            account.keyPair.privKey?.let { putString(PrefKeys.NOSTR_PRIVKEY, it.toHexKey()) }
            account.keyPair.pubKey.let { putString(PrefKeys.NOSTR_PUBKEY, it.toHexKey()) }
            putStringSet(PrefKeys.FOLLOWING_CHANNELS, account.followingChannels)
            putStringSet(PrefKeys.FOLLOWING_COMMUNITIES, account.followingCommunities)
            putStringSet(PrefKeys.HIDDEN_USERS, account.hiddenUsers)
            putString(PrefKeys.RELAYS, Event.mapper.writeValueAsString(account.localRelays))
            putStringSet(PrefKeys.DONT_TRANSLATE_FROM, account.dontTranslateFrom)
            putString(PrefKeys.LANGUAGE_PREFS, Event.mapper.writeValueAsString(account.languagePreferences))
            putString(PrefKeys.TRANSLATE_TO, account.translateTo)
            putString(PrefKeys.ZAP_AMOUNTS, Event.mapper.writeValueAsString(account.zapAmountChoices))
            putString(PrefKeys.REACTION_CHOICES, Event.mapper.writeValueAsString(account.reactionChoices))
            putString(PrefKeys.DEFAULT_ZAPTYPE, account.defaultZapType.name)
            putString(PrefKeys.DEFAULT_FILE_SERVER, account.defaultFileServer.name)
            putString(PrefKeys.DEFAULT_HOME_FOLLOW_LIST, account.defaultHomeFollowList)
            putString(PrefKeys.DEFAULT_STORIES_FOLLOW_LIST, account.defaultStoriesFollowList)
            putString(PrefKeys.DEFAULT_NOTIFICATION_FOLLOW_LIST, account.defaultNotificationFollowList)
            putString(PrefKeys.DEFAULT_DISCOVERY_FOLLOW_LIST, account.defaultDiscoveryFollowList)
            putString(PrefKeys.ZAP_PAYMENT_REQUEST_SERVER, Event.mapper.writeValueAsString(account.zapPaymentRequest))
            putString(PrefKeys.LATEST_CONTACT_LIST, Event.mapper.writeValueAsString(account.backupContactList))
            putBoolean(PrefKeys.HIDE_DELETE_REQUEST_DIALOG, account.hideDeleteRequestDialog)
            putBoolean(PrefKeys.HIDE_NIP_24_WARNING_DIALOG, account.hideNIP24WarningDialog)
            putBoolean(PrefKeys.HIDE_BLOCK_ALERT_DIALOG, account.hideBlockAlertDialog)
            putBoolean(PrefKeys.USE_PROXY, account.proxy != null)
            putInt(PrefKeys.PROXY_PORT, account.proxyPort)
            putBoolean(PrefKeys.WARN_ABOUT_REPORTS, account.warnAboutPostsWithReports)
            putBoolean(PrefKeys.FILTER_SPAM_FROM_STRANGERS, account.filterSpamFromStrangers)
            putString(PrefKeys.LAST_READ_PER_ROUTE, Event.mapper.writeValueAsString(account.lastReadPerRoute))

            if (account.showSensitiveContent == null) {
                remove(PrefKeys.SHOW_SENSITIVE_CONTENT)
            } else {
                putBoolean(PrefKeys.SHOW_SENSITIVE_CONTENT, account.showSensitiveContent!!)
            }
        }.apply()

        val globalPrefs = encryptedPreferences()
        globalPrefs.edit().apply {
            if (account.settings.automaticallyShowImages.prefCode == null) {
                remove(PrefKeys.AUTOMATICALLY_SHOW_IMAGES)
            } else {
                putBoolean(PrefKeys.AUTOMATICALLY_SHOW_IMAGES, account.settings.automaticallyShowImages.prefCode!!)
            }

            if (account.settings.automaticallyStartPlayback.prefCode == null) {
                remove(PrefKeys.AUTOMATICALLY_START_PLAYBACK)
            } else {
                putBoolean(PrefKeys.AUTOMATICALLY_START_PLAYBACK, account.settings.automaticallyStartPlayback.prefCode!!)
            }
            if (account.settings.automaticallyShowUrlPreview.prefCode == null) {
                remove(PrefKeys.AUTOMATICALLY_LOAD_URL_PREVIEW)
            } else {
                putBoolean(PrefKeys.AUTOMATICALLY_LOAD_URL_PREVIEW, account.settings.automaticallyShowUrlPreview.prefCode!!)
            }
            putString(PrefKeys.PREFERRED_LANGUAGE, account.settings.preferredLanguage ?: "")
        }.apply()
    }

    fun updateTheme(theme: Int) {
        encryptedPreferences().edit().apply {
            putInt(PrefKeys.THEME, theme)
        }.apply()
    }

    fun getTheme(): Int {
        encryptedPreferences().apply {
            return getInt(PrefKeys.THEME, 0)
        }
    }

    fun getPreferredLanguage(): String {
        var language = ""
        encryptedPreferences().apply {
            language = getString(PrefKeys.PREFERRED_LANGUAGE, "") ?: ""
        }
        return language
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
            val followingCommunities = getStringSet(PrefKeys.FOLLOWING_COMMUNITIES, null) ?: setOf()
            val hiddenUsers = getStringSet(PrefKeys.HIDDEN_USERS, emptySet()) ?: setOf()
            val localRelays = getString(PrefKeys.RELAYS, "[]")?.let {
                Event.mapper.readValue<Set<RelaySetupInfo>>(it)
            } ?: setOf<RelaySetupInfo>()

            val dontTranslateFrom = getStringSet(PrefKeys.DONT_TRANSLATE_FROM, null) ?: setOf()
            val translateTo = getString(PrefKeys.TRANSLATE_TO, null) ?: Locale.getDefault().language
            val defaultHomeFollowList = getString(PrefKeys.DEFAULT_HOME_FOLLOW_LIST, null) ?: KIND3_FOLLOWS
            val defaultStoriesFollowList = getString(PrefKeys.DEFAULT_STORIES_FOLLOW_LIST, null) ?: GLOBAL_FOLLOWS
            val defaultNotificationFollowList = getString(PrefKeys.DEFAULT_NOTIFICATION_FOLLOW_LIST, null) ?: GLOBAL_FOLLOWS
            val defaultDiscoveryFollowList = getString(PrefKeys.DEFAULT_DISCOVERY_FOLLOW_LIST, null) ?: GLOBAL_FOLLOWS

            val zapAmountChoices = getString(PrefKeys.ZAP_AMOUNTS, "[]")?.let {
                Event.mapper.readValue<List<Long>>(it)
            }?.ifEmpty { listOf(500L, 1000L, 5000L) } ?: listOf(500L, 1000L, 5000L)

            val reactionChoices = getString(PrefKeys.REACTION_CHOICES, "[]")?.let {
                Event.mapper.readValue<List<String>>(it)
            }?.ifEmpty { listOf("+") } ?: listOf("+")

            val defaultZapType = getString(PrefKeys.DEFAULT_ZAPTYPE, "")?.let { serverName ->
                LnZapEvent.ZapType.values().first { it.name == serverName }
            } ?: LnZapEvent.ZapType.PUBLIC

            val defaultFileServer = getString(PrefKeys.DEFAULT_FILE_SERVER, "")?.let { serverName ->
                ServersAvailable.values().first { it.name == serverName }
            } ?: ServersAvailable.NOSTR_BUILD

            val zapPaymentRequestServer = try {
                getString(PrefKeys.ZAP_PAYMENT_REQUEST_SERVER, null)?.let {
                    Event.mapper.readValue<Nip47URI>(it)
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                null
            }

            val latestContactList = try {
                getString(PrefKeys.LATEST_CONTACT_LIST, null)?.let {
                    Event.fromJson(it) as ContactListEvent?
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                null
            }

            val languagePreferences = try {
                getString(PrefKeys.LANGUAGE_PREFS, null)?.let {
                    Event.mapper.readValue<Map<String, String>>(it)
                } ?: mapOf()
            } catch (e: Throwable) {
                e.printStackTrace()
                mapOf()
            }

            val hideDeleteRequestDialog = getBoolean(PrefKeys.HIDE_DELETE_REQUEST_DIALOG, false)
            val hideBlockAlertDialog = getBoolean(PrefKeys.HIDE_BLOCK_ALERT_DIALOG, false)
            val hideNIP24WarningDialog = getBoolean(PrefKeys.HIDE_NIP_24_WARNING_DIALOG, false)
            val useProxy = getBoolean(PrefKeys.USE_PROXY, false)
            val proxyPort = getInt(PrefKeys.PROXY_PORT, 9050)
            val proxy = HttpClient.initProxy(useProxy, "127.0.0.1", proxyPort)

            val showSensitiveContent = if (contains(PrefKeys.SHOW_SENSITIVE_CONTENT)) {
                getBoolean(PrefKeys.SHOW_SENSITIVE_CONTENT, false)
            } else {
                null
            }
            val filterSpam = getBoolean(PrefKeys.FILTER_SPAM_FROM_STRANGERS, true)
            val warnAboutReports = getBoolean(PrefKeys.WARN_ABOUT_REPORTS, true)

            val lastReadPerRoute = try {
                getString(PrefKeys.LAST_READ_PER_ROUTE, null)?.let {
                    Event.mapper.readValue<Map<String, Long>>(it)
                } ?: mapOf()
            } catch (e: Throwable) {
                e.printStackTrace()
                mapOf()
            }

            val settings = Settings()
            encryptedPreferences().apply {
                settings.automaticallyShowImages = if (contains(PrefKeys.AUTOMATICALLY_SHOW_IMAGES)) {
                    parseConnectivityType(getBoolean(PrefKeys.AUTOMATICALLY_SHOW_IMAGES, false))
                } else {
                    ConnectivityType.ALWAYS
                }

                settings.automaticallyStartPlayback = if (contains(PrefKeys.AUTOMATICALLY_START_PLAYBACK)) {
                    parseConnectivityType(getBoolean(PrefKeys.AUTOMATICALLY_START_PLAYBACK, false))
                } else {
                    ConnectivityType.ALWAYS
                }
                settings.automaticallyShowUrlPreview = if (contains(PrefKeys.AUTOMATICALLY_LOAD_URL_PREVIEW)) {
                    parseConnectivityType(getBoolean(PrefKeys.AUTOMATICALLY_LOAD_URL_PREVIEW, false))
                } else {
                    ConnectivityType.ALWAYS
                }

                settings.preferredLanguage = getString(PrefKeys.PREFERRED_LANGUAGE, "")
            }

            val a = Account(
                keyPair = KeyPair(privKey = privKey?.hexToByteArray(), pubKey = pubKey.hexToByteArray()),
                followingChannels = followingChannels,
                followingCommunities = followingCommunities,
                hiddenUsers = hiddenUsers,
                localRelays = localRelays,
                dontTranslateFrom = dontTranslateFrom,
                languagePreferences = languagePreferences,
                translateTo = translateTo,
                zapAmountChoices = zapAmountChoices,
                reactionChoices = reactionChoices,
                defaultZapType = defaultZapType,
                defaultFileServer = defaultFileServer,
                defaultHomeFollowList = defaultHomeFollowList,
                defaultStoriesFollowList = defaultStoriesFollowList,
                defaultNotificationFollowList = defaultNotificationFollowList,
                defaultDiscoveryFollowList = defaultDiscoveryFollowList,
                zapPaymentRequest = zapPaymentRequestServer,
                hideDeleteRequestDialog = hideDeleteRequestDialog,
                hideBlockAlertDialog = hideBlockAlertDialog,
                hideNIP24WarningDialog = hideNIP24WarningDialog,
                backupContactList = latestContactList,
                proxy = proxy,
                proxyPort = proxyPort,
                showSensitiveContent = showSensitiveContent,
                warnAboutPostsWithReports = warnAboutReports,
                filterSpamFromStrangers = filterSpam,
                lastReadPerRoute = lastReadPerRoute,
                settings = settings
            )

            return a
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
