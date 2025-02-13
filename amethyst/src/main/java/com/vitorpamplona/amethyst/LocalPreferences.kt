/**
 * Copyright (c) 2024 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.amethyst.model.AccountLanguagePreferencesInternal
import com.vitorpamplona.amethyst.model.AccountReactionPreferencesInternal
import com.vitorpamplona.amethyst.model.AccountSecurityPreferencesInternal
import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.AccountSyncedSettingsInternal
import com.vitorpamplona.amethyst.model.AccountZapPreferencesInternal
import com.vitorpamplona.amethyst.model.DefaultReactions
import com.vitorpamplona.amethyst.model.DefaultZapAmounts
import com.vitorpamplona.amethyst.model.GLOBAL_FOLLOWS
import com.vitorpamplona.amethyst.model.KIND3_FOLLOWS
import com.vitorpamplona.amethyst.model.Settings
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.ui.actions.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.tor.TorSettings
import com.vitorpamplona.amethyst.ui.tor.TorSettingsFlow
import com.vitorpamplona.amethyst.ui.tor.TorType
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import com.vitorpamplona.quartz.experimental.edits.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.jackson.EventMapper
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.toHexKey
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.MuteListEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    val hasPrivKey: Boolean,
    val loggedInWithExternalSigner: Boolean,
    val isTransient: Boolean,
)

private object PrefKeys {
    const val CURRENT_ACCOUNT = "currently_logged_in_account"
    const val SAVED_ACCOUNTS = "all_saved_accounts"
    const val NOSTR_PRIVKEY = "nostr_privkey"
    const val NOSTR_PUBKEY = "nostr_pubkey"
    const val RELAYS = "relays"
    const val DONT_TRANSLATE_FROM = "dontTranslateFrom"
    const val LOCAL_RELAY_SERVERS = "localRelayServers"
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
    const val LATEST_USER_METADATA = "latestUserMetadata"
    const val LATEST_CONTACT_LIST = "latestContactList"
    const val LATEST_DM_RELAY_LIST = "latestDMRelayList"
    const val LATEST_NIP65_RELAY_LIST = "latestNIP65RelayList"
    const val LATEST_SEARCH_RELAY_LIST = "latestSearchRelayList"
    const val LATEST_MUTE_LIST = "latestMuteList"
    const val LATEST_PRIVATE_HOME_RELAY_LIST = "latestPrivateHomeRelayList"
    const val LATEST_APP_SPECIFIC_DATA = "latestAppSpecificData"
    const val HIDE_DELETE_REQUEST_DIALOG = "hide_delete_request_dialog"
    const val HIDE_BLOCK_ALERT_DIALOG = "hide_block_alert_dialog"
    const val HIDE_NIP_17_WARNING_DIALOG = "hide_nip24_warning_dialog" // delete later
    const val TOR_SETTINGS = "tor_settings"
    const val USE_PROXY = "use_proxy"
    const val PROXY_PORT = "proxy_port"
    const val SHOW_SENSITIVE_CONTENT = "show_sensitive_content"
    const val WARN_ABOUT_REPORTS = "warn_about_reports"
    const val FILTER_SPAM_FROM_STRANGERS = "filter_spam_from_strangers"
    const val LAST_READ_PER_ROUTE = "last_read_route_per_route"
    const val LOGIN_WITH_EXTERNAL_SIGNER = "login_with_external_signer"
    const val SIGNER_PACKAGE_NAME = "signer_package_name"
    const val HAS_DONATED_IN_VERSION = "has_donated_in_version"
    const val PENDING_ATTESTATIONS = "pending_attestations"

    const val ALL_ACCOUNT_INFO = "all_saved_accounts_info"
    const val SHARED_SETTINGS = "shared_settings"
}

object LocalPreferences {
    private const val COMMA = ","

    private var currentAccount: String? = null
    private var savedAccounts: MutableStateFlow<List<AccountInfo>?> = MutableStateFlow(null)
    private var cachedAccounts: MutableMap<String, AccountSettings?> = mutableMapOf()

    suspend fun currentAccount(): String? {
        if (currentAccount == null) {
            currentAccount =
                withContext(Dispatchers.IO) {
                    encryptedPreferences().getString(PrefKeys.CURRENT_ACCOUNT, null)
                }
        }
        return currentAccount
    }

    private suspend fun updateCurrentAccount(info: AccountInfo?) {
        if (info == null) {
            currentAccount = null
            withContext(Dispatchers.IO) {
                encryptedPreferences().edit().clear().apply()
            }
        } else if (currentAccount != info.npub) {
            currentAccount = info.npub
            if (!info.isTransient) {
                withContext(Dispatchers.IO) {
                    encryptedPreferences().edit().apply { putString(PrefKeys.CURRENT_ACCOUNT, info.npub) }.apply()
                }
            }
        }
    }

    private suspend fun savedAccounts(): List<AccountInfo> {
        if (savedAccounts.value == null) {
            withContext(Dispatchers.IO) {
                with(encryptedPreferences()) {
                    val newSystemOfAccounts =
                        getString(PrefKeys.ALL_ACCOUNT_INFO, "[]")?.let {
                            EventMapper.mapper.readValue<List<AccountInfo>>(it)
                        }

                    if (!newSystemOfAccounts.isNullOrEmpty()) {
                        savedAccounts.emit(newSystemOfAccounts)
                    } else {
                        val oldAccounts = getString(PrefKeys.SAVED_ACCOUNTS, null)?.split(COMMA) ?: listOf()

                        val migrated =
                            oldAccounts.map { npub ->
                                AccountInfo(
                                    npub,
                                    encryptedPreferences(npub).getBoolean(PrefKeys.LOGIN_WITH_EXTERNAL_SIGNER, false),
                                    (encryptedPreferences(npub).getString(PrefKeys.NOSTR_PRIVKEY, "") ?: "").isNotBlank(),
                                    false,
                                )
                            }

                        savedAccounts.emit(migrated)

                        edit().apply { putString(PrefKeys.ALL_ACCOUNT_INFO, EventMapper.mapper.writeValueAsString(savedAccounts.value)) }.apply()
                    }
                }
            }
        }
        // it's always not null when it gets here.
        return savedAccounts.value!!
    }

    fun accountsFlow() = savedAccounts

    private suspend fun updateSavedAccounts(accounts: List<AccountInfo>) =
        withContext(Dispatchers.IO) {
            if (savedAccounts != accounts) {
                savedAccounts.emit(accounts)

                encryptedPreferences()
                    .edit()
                    .apply {
                        putString(
                            PrefKeys.ALL_ACCOUNT_INFO,
                            EventMapper.mapper.writeValueAsString(accounts.filter { !it.isTransient }),
                        )
                    }.apply()
            }
        }

    private val prefsDirPath: String
        get() = "${Amethyst.instance.filesDir.parent}/shared_prefs/"

    private suspend fun addAccount(accInfo: AccountInfo) {
        val accounts = savedAccounts().filter { it.npub != accInfo.npub }.plus(accInfo)
        updateSavedAccounts(accounts)
    }

    private suspend fun setCurrentAccount(accountSettings: AccountSettings) {
        val npub = accountSettings.keyPair.pubKey.toNpub()
        val accInfo =
            AccountInfo(
                npub,
                accountSettings.isWriteable(),
                accountSettings.externalSignerPackageName != null,
                accountSettings.transientAccount,
            )
        updateCurrentAccount(accInfo)
        addAccount(accInfo)
    }

    suspend fun switchToAccount(accountInfo: AccountInfo) = updateCurrentAccount(accountInfo)

    /** Removes the account from the app level shared preferences */
    private suspend fun removeAccount(accountInfo: AccountInfo) {
        updateSavedAccounts(savedAccounts().filter { it.npub != accountInfo.npub })
    }

    /** Deletes the npub-specific shared preference file */
    private suspend fun deleteUserPreferenceFile(npub: String) {
        withContext(Dispatchers.IO) {
            val prefsDir = File(prefsDirPath)
            prefsDir.list()?.forEach {
                if (it.contains(npub)) {
                    File(prefsDir, it).delete()
                }
            }
        }
    }

    private fun encryptedPreferences(npub: String? = null): SharedPreferences {
        checkNotInMainThread()

        return if (BuildConfig.DEBUG && DEBUG_PLAINTEXT_PREFERENCES) {
            val preferenceFile =
                if (npub == null) DEBUG_PREFERENCES_NAME else "${DEBUG_PREFERENCES_NAME}_$npub"
            Amethyst.instance.getSharedPreferences(preferenceFile, Context.MODE_PRIVATE)
        } else {
            return Amethyst.instance.encryptedStorage(npub)
        }
    }

    /**
     * Clears the preferences for a given npub, deletes the preferences xml file, and switches the
     * user to the first account in the list if it exists
     *
     * We need to use `commit()` to write changes to disk and release the file lock so that it can be
     * deleted. If we use `apply()` there is a race condition and the file will probably not be
     * deleted
     */
    @SuppressLint("ApplySharedPref")
    suspend fun updatePrefsForLogout(accountInfo: AccountInfo) {
        Log.d("LocalPreferences", "Saving to encrypted storage updatePrefsForLogout ${accountInfo.npub}")
        withContext(Dispatchers.IO) {
            encryptedPreferences(accountInfo.npub).edit().clear().commit()
            removeAccount(accountInfo)
            deleteUserPreferenceFile(accountInfo.npub)

            if (savedAccounts().isEmpty()) {
                updateCurrentAccount(null)
            } else if (currentAccount() == accountInfo.npub) {
                updateCurrentAccount(savedAccounts().elementAt(0))
            }
        }
    }

    suspend fun updatePrefsForLogin(accountSettings: AccountSettings) {
        setCurrentAccount(accountSettings)
        saveToEncryptedStorage(accountSettings)
    }

    suspend fun allSavedAccounts(): List<AccountInfo> = savedAccounts()

    suspend fun saveToEncryptedStorage(settings: AccountSettings) {
        Log.d("LocalPreferences", "Saving to encrypted storage")
        if (!settings.transientAccount) {
            withContext(Dispatchers.IO) {
                val prefs = encryptedPreferences(settings.keyPair.pubKey.toNpub())
                prefs
                    .edit()
                    .apply {
                        putBoolean(PrefKeys.LOGIN_WITH_EXTERNAL_SIGNER, settings.externalSignerPackageName != null)
                        if (settings.externalSignerPackageName != null) {
                            remove(PrefKeys.NOSTR_PRIVKEY)
                            putString(PrefKeys.SIGNER_PACKAGE_NAME, settings.externalSignerPackageName)
                        } else {
                            remove(PrefKeys.SIGNER_PACKAGE_NAME)
                            settings.keyPair.privKey?.let { putString(PrefKeys.NOSTR_PRIVKEY, it.toHexKey()) }
                        }
                        settings.keyPair.pubKey.let { putString(PrefKeys.NOSTR_PUBKEY, it.toHexKey()) }
                        putString(PrefKeys.RELAYS, EventMapper.mapper.writeValueAsString(settings.localRelays))

                        putString(
                            PrefKeys.DEFAULT_FILE_SERVER,
                            EventMapper.mapper.writeValueAsString(settings.defaultFileServer),
                        )
                        putString(PrefKeys.DEFAULT_HOME_FOLLOW_LIST, settings.defaultHomeFollowList.value)
                        putString(PrefKeys.DEFAULT_STORIES_FOLLOW_LIST, settings.defaultStoriesFollowList.value)
                        putString(
                            PrefKeys.DEFAULT_NOTIFICATION_FOLLOW_LIST,
                            settings.defaultNotificationFollowList.value,
                        )
                        putString(
                            PrefKeys.DEFAULT_DISCOVERY_FOLLOW_LIST,
                            settings.defaultDiscoveryFollowList.value,
                        )
                        putString(
                            PrefKeys.ZAP_PAYMENT_REQUEST_SERVER,
                            EventMapper.mapper.writeValueAsString(settings.zapPaymentRequest),
                        )
                        if (settings.backupContactList != null) {
                            putString(
                                PrefKeys.LATEST_CONTACT_LIST,
                                EventMapper.mapper.writeValueAsString(settings.backupContactList),
                            )
                        } else {
                            remove(PrefKeys.LATEST_CONTACT_LIST)
                        }

                        if (settings.backupUserMetadata != null) {
                            putString(
                                PrefKeys.LATEST_USER_METADATA,
                                EventMapper.mapper.writeValueAsString(settings.backupUserMetadata),
                            )
                        } else {
                            remove(PrefKeys.LATEST_USER_METADATA)
                        }

                        if (settings.backupDMRelayList != null) {
                            putString(
                                PrefKeys.LATEST_DM_RELAY_LIST,
                                EventMapper.mapper.writeValueAsString(settings.backupDMRelayList),
                            )
                        } else {
                            remove(PrefKeys.LATEST_DM_RELAY_LIST)
                        }

                        if (settings.backupNIP65RelayList != null) {
                            putString(
                                PrefKeys.LATEST_NIP65_RELAY_LIST,
                                EventMapper.mapper.writeValueAsString(settings.backupNIP65RelayList),
                            )
                        } else {
                            remove(PrefKeys.LATEST_NIP65_RELAY_LIST)
                        }

                        if (settings.backupSearchRelayList != null) {
                            putString(
                                PrefKeys.LATEST_SEARCH_RELAY_LIST,
                                EventMapper.mapper.writeValueAsString(settings.backupSearchRelayList),
                            )
                        } else {
                            remove(PrefKeys.LATEST_SEARCH_RELAY_LIST)
                        }

                        if (settings.localRelayServers.isNotEmpty()) {
                            putStringSet(PrefKeys.LOCAL_RELAY_SERVERS, settings.localRelayServers)
                        } else {
                            remove(PrefKeys.LOCAL_RELAY_SERVERS)
                        }

                        if (settings.backupMuteList != null) {
                            putString(
                                PrefKeys.LATEST_MUTE_LIST,
                                EventMapper.mapper.writeValueAsString(settings.backupMuteList),
                            )
                        } else {
                            remove(PrefKeys.LATEST_MUTE_LIST)
                        }

                        if (settings.backupPrivateHomeRelayList != null) {
                            putString(
                                PrefKeys.LATEST_PRIVATE_HOME_RELAY_LIST,
                                EventMapper.mapper.writeValueAsString(settings.backupPrivateHomeRelayList),
                            )
                        } else {
                            remove(PrefKeys.LATEST_PRIVATE_HOME_RELAY_LIST)
                        }

                        if (settings.backupAppSpecificData != null) {
                            putString(
                                PrefKeys.LATEST_APP_SPECIFIC_DATA,
                                EventMapper.mapper.writeValueAsString(settings.backupAppSpecificData),
                            )
                        } else {
                            remove(PrefKeys.LATEST_APP_SPECIFIC_DATA)
                        }

                        putBoolean(PrefKeys.HIDE_DELETE_REQUEST_DIALOG, settings.hideDeleteRequestDialog)
                        putBoolean(PrefKeys.HIDE_NIP_17_WARNING_DIALOG, settings.hideNIP17WarningDialog)
                        putBoolean(PrefKeys.HIDE_BLOCK_ALERT_DIALOG, settings.hideBlockAlertDialog)

                        // migrating from previous design
                        remove(PrefKeys.USE_PROXY)
                        remove(PrefKeys.PROXY_PORT)

                        putString(PrefKeys.TOR_SETTINGS, EventMapper.mapper.writeValueAsString(settings.torSettings.toSettings()))

                        val regularMap =
                            settings.lastReadPerRoute.value.mapValues {
                                it.value.value
                            }

                        putString(
                            PrefKeys.LAST_READ_PER_ROUTE,
                            EventMapper.mapper.writeValueAsString(regularMap),
                        )
                        putStringSet(PrefKeys.HAS_DONATED_IN_VERSION, settings.hasDonatedInVersion.value)

                        putString(
                            PrefKeys.PENDING_ATTESTATIONS,
                            EventMapper.mapper.writeValueAsString(settings.pendingAttestations.value),
                        )
                    }.apply()
            }
        }
        Log.d("LocalPreferences", "Saved to encrypted storage")
    }

    suspend fun loadCurrentAccountFromEncryptedStorage(): AccountSettings? = currentAccount()?.let { loadCurrentAccountFromEncryptedStorage(it) }

    suspend fun saveSharedSettings(
        sharedSettings: Settings,
        prefs: SharedPreferences = encryptedPreferences(),
    ) {
        Log.d("LocalPreferences", "Saving to shared settings")
        with(prefs.edit()) {
            putString(PrefKeys.SHARED_SETTINGS, EventMapper.mapper.writeValueAsString(sharedSettings))
            apply()
        }
    }

    suspend fun loadSharedSettings(prefs: SharedPreferences = encryptedPreferences()): Settings? {
        Log.d("LocalPreferences", "Load shared settings")
        with(prefs) {
            return try {
                getString(PrefKeys.SHARED_SETTINGS, "{}")?.let { EventMapper.mapper.readValue<Settings>(it) }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Log.w(
                    "LocalPreferences",
                    "Unable to decode shared preferences: ${getString(PrefKeys.SHARED_SETTINGS, null)}",
                    e,
                )
                e.printStackTrace()
                null
            }
        }
    }

    val mutex = Mutex()

    suspend fun loadCurrentAccountFromEncryptedStorage(npub: String): AccountSettings? {
        // if already loaded, return right away
        if (cachedAccounts.containsKey(npub)) {
            return cachedAccounts[npub]
        }

        return withContext(Dispatchers.IO) {
            mutex.withLock {
                if (cachedAccounts.containsKey(npub)) {
                    return@withContext cachedAccounts.get(npub)
                }

                val accountSettings = innerLoadCurrentAccountFromEncryptedStorage(npub)

                cachedAccounts.put(npub, accountSettings)

                return@withContext accountSettings
            }
        }
    }

    private suspend fun innerLoadCurrentAccountFromEncryptedStorage(npub: String?): AccountSettings? {
        Log.d("LocalPreferences", "Load account from file $npub")

        return withContext(Dispatchers.IO) {
            checkNotInMainThread()

            return@withContext with(encryptedPreferences(npub)) {
                val privKey = getString(PrefKeys.NOSTR_PRIVKEY, null)
                val pubKey = getString(PrefKeys.NOSTR_PUBKEY, null) ?: return@with null
                val externalSignerPackageName =
                    getString(PrefKeys.SIGNER_PACKAGE_NAME, null)
                        ?: if (getBoolean(PrefKeys.LOGIN_WITH_EXTERNAL_SIGNER, false)) "com.greenart7c3.nostrsigner" else null

                val defaultHomeFollowList =
                    getString(PrefKeys.DEFAULT_HOME_FOLLOW_LIST, null) ?: KIND3_FOLLOWS
                val defaultStoriesFollowList =
                    getString(PrefKeys.DEFAULT_STORIES_FOLLOW_LIST, null) ?: GLOBAL_FOLLOWS
                val defaultNotificationFollowList =
                    getString(PrefKeys.DEFAULT_NOTIFICATION_FOLLOW_LIST, null) ?: GLOBAL_FOLLOWS
                val defaultDiscoveryFollowList =
                    getString(PrefKeys.DEFAULT_DISCOVERY_FOLLOW_LIST, null) ?: GLOBAL_FOLLOWS

                val defaultZapType =
                    getString(PrefKeys.DEFAULT_ZAPTYPE, "")?.let { serverName ->
                        LnZapEvent.ZapType.entries.firstOrNull { it.name == serverName }
                    } ?: LnZapEvent.ZapType.PUBLIC

                val localRelays = parseOrNull<Set<RelaySetupInfo>>(PrefKeys.RELAYS) ?: emptySet()

                val zapPaymentRequestServer = parseOrNull<Nip47WalletConnect.Nip47URI>(PrefKeys.ZAP_PAYMENT_REQUEST_SERVER)
                val defaultFileServer = parseOrNull<ServerName>(PrefKeys.DEFAULT_FILE_SERVER) ?: DEFAULT_MEDIA_SERVERS[0]

                val pendingAttestations = parseOrNull<Map<HexKey, String>>(PrefKeys.PENDING_ATTESTATIONS) ?: mapOf()
                val localRelayServers = getStringSet(PrefKeys.LOCAL_RELAY_SERVERS, null) ?: setOf()

                val latestUserMetadata = parseEventOrNull<MetadataEvent>(PrefKeys.LATEST_USER_METADATA)
                val latestContactList = parseEventOrNull<ContactListEvent>(PrefKeys.LATEST_CONTACT_LIST)
                val latestDmRelayList = parseEventOrNull<ChatMessageRelayListEvent>(PrefKeys.LATEST_DM_RELAY_LIST)
                val latestNip65RelayList = parseEventOrNull<AdvertisedRelayListEvent>(PrefKeys.LATEST_NIP65_RELAY_LIST)
                val latestSearchRelayList = parseEventOrNull<SearchRelayListEvent>(PrefKeys.LATEST_SEARCH_RELAY_LIST)
                val latestMuteList = parseEventOrNull<MuteListEvent>(PrefKeys.LATEST_MUTE_LIST)
                val latestPrivateHomeRelayList = parseEventOrNull<PrivateOutboxRelayListEvent>(PrefKeys.LATEST_PRIVATE_HOME_RELAY_LIST)
                val latestAppSpecificData = parseEventOrNull<AppSpecificDataEvent>(PrefKeys.LATEST_APP_SPECIFIC_DATA)

                val syncedSettings =
                    if (latestAppSpecificData != null) {
                        null
                    } else {
                        // previous version. Delete this when ready.
                        val reactionChoices = parseOrNull<List<String>>(PrefKeys.REACTION_CHOICES)?.ifEmpty { DefaultReactions } ?: DefaultReactions
                        val zapAmountChoices = parseOrNull<List<Long>>(PrefKeys.ZAP_AMOUNTS)?.ifEmpty { DefaultZapAmounts } ?: DefaultZapAmounts

                        val languagePreferences = parseOrNull<Map<String, String>>(PrefKeys.LANGUAGE_PREFS) ?: mapOf()

                        val showSensitiveContent =
                            if (contains(PrefKeys.SHOW_SENSITIVE_CONTENT)) {
                                getBoolean(PrefKeys.SHOW_SENSITIVE_CONTENT, false)
                            } else {
                                null
                            }
                        val filterSpam = getBoolean(PrefKeys.FILTER_SPAM_FROM_STRANGERS, true)
                        val warnAboutReports = getBoolean(PrefKeys.WARN_ABOUT_REPORTS, true)

                        val dontTranslateFrom = getStringSet(PrefKeys.DONT_TRANSLATE_FROM, null) ?: setOf()
                        val translateTo = getString(PrefKeys.TRANSLATE_TO, null) ?: Locale.getDefault().language

                        AccountSyncedSettingsInternal(
                            reactions =
                                AccountReactionPreferencesInternal(
                                    reactionChoices = reactionChoices,
                                ),
                            zaps =
                                AccountZapPreferencesInternal(
                                    zapAmountChoices = zapAmountChoices,
                                    defaultZapType = defaultZapType,
                                ),
                            languages =
                                AccountLanguagePreferencesInternal(
                                    dontTranslateFrom = dontTranslateFrom,
                                    languagePreferences = languagePreferences,
                                    translateTo = translateTo,
                                ),
                            security =
                                AccountSecurityPreferencesInternal(
                                    showSensitiveContent = showSensitiveContent,
                                    warnAboutPostsWithReports = warnAboutReports,
                                    filterSpamFromStrangers = filterSpam,
                                ),
                        )
                    }

                val hideDeleteRequestDialog = getBoolean(PrefKeys.HIDE_DELETE_REQUEST_DIALOG, false)
                val hideBlockAlertDialog = getBoolean(PrefKeys.HIDE_BLOCK_ALERT_DIALOG, false)
                val hideNIP17WarningDialog = getBoolean(PrefKeys.HIDE_NIP_17_WARNING_DIALOG, false)
                val useProxy = getBoolean(PrefKeys.USE_PROXY, false)

                val torSettings =
                    if (useProxy) {
                        // old settings, means Orbot
                        TorSettings(
                            TorType.EXTERNAL,
                            getInt(PrefKeys.PROXY_PORT, 9050),
                            true,
                            true,
                            true,
                            true,
                            true,
                            true,
                            true,
                            true,
                            true,
                            true,
                            true,
                        )
                    } else {
                        parseOrNull<TorSettings>(PrefKeys.TOR_SETTINGS) ?: TorSettings()
                    }

                val lastReadPerRoute =
                    parseOrNull<Map<String, Long>>(PrefKeys.LAST_READ_PER_ROUTE)?.mapValues {
                        MutableStateFlow(it.value)
                    } ?: mapOf()

                val keyPair = KeyPair(privKey = privKey?.hexToByteArray(), pubKey = pubKey.hexToByteArray())
                val hasDonatedInVersion = getStringSet(PrefKeys.HAS_DONATED_IN_VERSION, null) ?: setOf()

                return@with AccountSettings(
                    keyPair = keyPair,
                    transientAccount = false,
                    externalSignerPackageName = externalSignerPackageName,
                    localRelays = localRelays,
                    localRelayServers = localRelayServers,
                    defaultFileServer = defaultFileServer,
                    defaultHomeFollowList = MutableStateFlow(defaultHomeFollowList),
                    defaultStoriesFollowList = MutableStateFlow(defaultStoriesFollowList),
                    defaultNotificationFollowList = MutableStateFlow(defaultNotificationFollowList),
                    defaultDiscoveryFollowList = MutableStateFlow(defaultDiscoveryFollowList),
                    zapPaymentRequest = zapPaymentRequestServer,
                    hideDeleteRequestDialog = hideDeleteRequestDialog,
                    hideBlockAlertDialog = hideBlockAlertDialog,
                    hideNIP17WarningDialog = hideNIP17WarningDialog,
                    backupUserMetadata = latestUserMetadata,
                    backupContactList = latestContactList,
                    backupNIP65RelayList = latestNip65RelayList,
                    backupDMRelayList = latestDmRelayList,
                    backupSearchRelayList = latestSearchRelayList,
                    backupPrivateHomeRelayList = latestPrivateHomeRelayList,
                    backupMuteList = latestMuteList,
                    backupAppSpecificData = latestAppSpecificData,
                    backupSyncedSettings = syncedSettings,
                    torSettings = TorSettingsFlow.build(torSettings),
                    lastReadPerRoute = MutableStateFlow(lastReadPerRoute),
                    hasDonatedInVersion = MutableStateFlow(hasDonatedInVersion),
                    pendingAttestations = MutableStateFlow(pendingAttestations),
                )
            }
        }
    }

    private inline fun <reified T> SharedPreferences.parseOrNull(key: String): T? {
        val value = getString(key, null)
        if (value.isNullOrEmpty() || value == "null") {
            return null
        }
        return try {
            if (T::class.java.isInstance(Event::class.java)) {
                Event.fromJson(value) as T?
            } else {
                EventMapper.mapper.readValue<T?>(value)
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Log.w("LocalPreferences", "Error Decoding $key from Preferences with value $value", e)
            e.printStackTrace()
            null
        }
    }

    private inline fun <reified T> SharedPreferences.parseEventOrNull(key: String): T? {
        val value = getString(key, null)
        if (value.isNullOrEmpty() || value == "null") {
            return null
        }
        return try {
            Event.fromJson(value) as T?
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Log.w("LocalPreferences", "Error Decoding $key from Preferences with value $value", e)
            e.printStackTrace()
            null
        }
    }
}
