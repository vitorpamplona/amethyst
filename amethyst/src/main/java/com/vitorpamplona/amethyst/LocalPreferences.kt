/*
 * Copyright (c) 2025 Vitor Pamplona
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
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.TopFilter
import com.vitorpamplona.amethyst.model.UiSettings
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.ui.actions.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.quartz.experimental.ephemChat.list.EphemeralChatListEvent
import com.vitorpamplona.quartz.experimental.nipA3.PaymentTargetsEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip28PublicChat.list.ChannelListEvent
import com.vitorpamplona.quartz.nip37Drafts.privateOutbox.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.geohashList.GeohashListEvent
import com.vitorpamplona.quartz.nip51Lists.hashtagList.HashtagListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BlockedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.IndexerRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.RelayFeedsListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.TrustedRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip72ModCommunities.follow.CommunityListEvent
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

// Release mode (!BuildConfig.DEBUG) always uses encrypted preferences
// To use plaintext SharedPreferences for debugging, set this to true
// It will only apply in Debug builds
private const val DEBUG_PLAINTEXT_PREFERENCES = false
private const val DEBUG_PREFERENCES_NAME = "debug_prefs"

@Immutable
@Serializable
data class AccountInfo(
    val npub: String,
    val hasPrivKey: Boolean = false,
    val loggedInWithExternalSigner: Boolean = false,
    val isTransient: Boolean = false,
)

private object PrefKeys {
    const val CURRENT_ACCOUNT = "currently_logged_in_account"
    const val SAVED_ACCOUNTS = "all_saved_accounts"
    const val NOSTR_PRIVKEY = "nostr_privkey"
    const val NOSTR_PUBKEY = "nostr_pubkey"
    const val LOCAL_RELAY_SERVERS = "localRelayServers"
    const val DEFAULT_FILE_SERVER = "defaultFileServer"
    const val STRIP_LOCATION_ON_UPLOAD = "stripLocationOnUpload"
    const val DEFAULT_HOME_FOLLOW_LIST = "defaultHomeFollowList"
    const val DEFAULT_STORIES_FOLLOW_LIST = "defaultStoriesFollowList"
    const val DEFAULT_NOTIFICATION_FOLLOW_LIST = "defaultNotificationFollowList"
    const val DEFAULT_DISCOVERY_FOLLOW_LIST = "defaultDiscoveryFollowList"
    const val DEFAULT_POLLS_FOLLOW_LIST = "defaultPollsFollowList"
    const val DEFAULT_PICTURES_FOLLOW_LIST = "defaultPicturesFollowList"
    const val DEFAULT_SHORTS_FOLLOW_LIST = "defaultShortsFollowList"
    const val DEFAULT_LONGS_FOLLOW_LIST = "defaultLongsFollowList"
    const val ZAP_PAYMENT_REQUEST_SERVER = "zapPaymentServer"
    const val LATEST_USER_METADATA = "latestUserMetadata"
    const val LATEST_CONTACT_LIST = "latestContactList"
    const val LATEST_DM_RELAY_LIST = "latestDMRelayList"
    const val LATEST_NIP65_RELAY_LIST = "latestNIP65RelayList"
    const val LATEST_SEARCH_RELAY_LIST = "latestSearchRelayList"
    const val LATEST_INDEX_RELAY_LIST = "latestIndexRelayList"
    const val LATEST_RELAY_FEEDS_LIST = "latestRelayFeedsList"
    const val LATEST_BLOCKED_RELAY_LIST = "latestBlockedRelayList"
    const val LATEST_TRUSTED_RELAY_LIST = "latestTrustedRelayList"
    const val LATEST_MUTE_LIST = "latestMuteList"
    const val LATEST_PRIVATE_HOME_RELAY_LIST = "latestPrivateHomeRelayList"
    const val LATEST_APP_SPECIFIC_DATA = "latestAppSpecificData"
    const val LATEST_CHANNEL_LIST = "latestChannelList"
    const val LATEST_COMMUNITY_LIST = "latestCommunityList"
    const val LATEST_HASHTAG_LIST = "latestHashtagList"
    const val LATEST_GEOHASH_LIST = "latestGeohashList"
    const val LATEST_EPHEMERAL_LIST = "latestEphemeralChatList"
    const val LATEST_TRUST_PROVIDER_LIST = "latestTrustProviderList"
    const val HIDE_DELETE_REQUEST_DIALOG = "hide_delete_request_dialog"
    const val HIDE_BLOCK_ALERT_DIALOG = "hide_block_alert_dialog"
    const val HIDE_NIP_17_WARNING_DIALOG = "hide_nip24_warning_dialog" // delete later
    const val TOR_SETTINGS = "tor_settings"
    const val USE_PROXY = "use_proxy"
    const val PROXY_PORT = "proxy_port"
    const val LAST_READ_PER_ROUTE = "last_read_route_per_route"
    const val LOGIN_WITH_EXTERNAL_SIGNER = "login_with_external_signer"
    const val SIGNER_PACKAGE_NAME = "signer_package_name"
    const val HAS_DONATED_IN_VERSION = "has_donated_in_version"
    const val DISMISSED_POLL_NOTE_IDS = "dismissed_poll_note_ids"
    const val PENDING_ATTESTATIONS = "pending_attestations"

    const val ALL_ACCOUNT_INFO = "all_saved_accounts_info"
    const val SHARED_SETTINGS = "shared_settings"
    const val LATEST_PAYMENT_TARGETS = "latestPaymentTargets"
}

object LocalPreferences {
    private const val COMMA = ","

    private var currentAccount: String? = null
    private val savedAccounts: MutableStateFlow<List<AccountInfo>?> = MutableStateFlow(null)
    private val cachedAccounts: MutableMap<String, AccountSettings?> = mutableMapOf()

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
                encryptedPreferences().edit { clear() }
            }
        } else if (currentAccount != info.npub) {
            currentAccount = info.npub
            if (!info.isTransient) {
                withContext(Dispatchers.IO) {
                    encryptedPreferences().edit { putString(PrefKeys.CURRENT_ACCOUNT, info.npub) }
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
                            JsonMapper.fromJson<List<AccountInfo>>(it)
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

                        edit {
                            putString(PrefKeys.ALL_ACCOUNT_INFO, JsonMapper.toJson(migrated))
                        }
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
                    .edit {
                        putString(
                            PrefKeys.ALL_ACCOUNT_INFO,
                            JsonMapper.toJson(accounts.filter { !it.isTransient }),
                        )
                    }
            }
        }

    private val prefsDirPath: String
        get() = "${Amethyst.instance.appContext.filesDir.parent}/shared_prefs/"

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
                if (it.contains(npub) && !File(prefsDir, it).delete()) {
                    Log.w("LocalPreferences") { "Failed to delete preference file: $it" }
                }
            }
        }
    }

    private fun encryptedPreferences(npub: String? = null): SharedPreferences {
        checkNotInMainThread()

        return if (BuildConfig.DEBUG && DEBUG_PLAINTEXT_PREFERENCES) {
            val preferenceFile =
                if (npub == null) DEBUG_PREFERENCES_NAME else "${DEBUG_PREFERENCES_NAME}_$npub"
            Amethyst.instance.appContext.getSharedPreferences(preferenceFile, Context.MODE_PRIVATE)
        } else {
            Amethyst.instance.encryptedStorage(npub)
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
    suspend fun deleteAccount(accountInfo: AccountInfo) {
        Log.d("LocalPreferences") { "Saving to encrypted storage updatePrefsForLogout ${accountInfo.npub}" }
        withContext(Dispatchers.IO) {
            encryptedPreferences(accountInfo.npub).edit(commit = true) { clear() }
            removeAccount(accountInfo)
            deleteUserPreferenceFile(accountInfo.npub)

            if (savedAccounts().isEmpty()) {
                updateCurrentAccount(null)
            } else if (currentAccount() == accountInfo.npub) {
                updateCurrentAccount(savedAccounts().elementAt(0))
            }
        }
    }

    suspend fun setDefaultAccount(accountSettings: AccountSettings) {
        setCurrentAccount(accountSettings)
        saveToEncryptedStorage(accountSettings)
    }

    suspend fun allSavedAccounts(): List<AccountInfo> = savedAccounts()

    suspend fun saveToEncryptedStorage(settings: AccountSettings) {
        Log.d("LocalPreferences", "Saving to encrypted storage")
        if (!settings.transientAccount) {
            withContext(Dispatchers.IO) {
                val prefs = encryptedPreferences(settings.keyPair.pubKey.toNpub())
                prefs.edit {
                    putBoolean(PrefKeys.LOGIN_WITH_EXTERNAL_SIGNER, settings.externalSignerPackageName != null)
                    if (settings.externalSignerPackageName != null) {
                        remove(PrefKeys.NOSTR_PRIVKEY)
                        putString(PrefKeys.SIGNER_PACKAGE_NAME, settings.externalSignerPackageName)
                    } else {
                        remove(PrefKeys.SIGNER_PACKAGE_NAME)
                        settings.keyPair.privKey?.let { putString(PrefKeys.NOSTR_PRIVKEY, it.toHexKey()) }
                    }
                    settings.keyPair.pubKey.let { putString(PrefKeys.NOSTR_PUBKEY, it.toHexKey()) }

                    putString(
                        PrefKeys.DEFAULT_FILE_SERVER,
                        JsonMapper.toJson(settings.defaultFileServer),
                    )

                    putBoolean(PrefKeys.STRIP_LOCATION_ON_UPLOAD, settings.stripLocationOnUpload)

                    putString(PrefKeys.DEFAULT_HOME_FOLLOW_LIST, JsonMapper.toJson(settings.defaultHomeFollowList.value))
                    putString(PrefKeys.DEFAULT_STORIES_FOLLOW_LIST, JsonMapper.toJson(settings.defaultStoriesFollowList.value))
                    putString(PrefKeys.DEFAULT_NOTIFICATION_FOLLOW_LIST, JsonMapper.toJson(settings.defaultNotificationFollowList.value))
                    putString(PrefKeys.DEFAULT_DISCOVERY_FOLLOW_LIST, JsonMapper.toJson(settings.defaultDiscoveryFollowList.value))

                    putString(PrefKeys.DEFAULT_POLLS_FOLLOW_LIST, JsonMapper.toJson(settings.defaultPollsFollowList.value))
                    putString(PrefKeys.DEFAULT_PICTURES_FOLLOW_LIST, JsonMapper.toJson(settings.defaultPicturesFollowList.value))
                    putString(PrefKeys.DEFAULT_SHORTS_FOLLOW_LIST, JsonMapper.toJson(settings.defaultShortsFollowList.value))
                    putString(PrefKeys.DEFAULT_LONGS_FOLLOW_LIST, JsonMapper.toJson(settings.defaultLongsFollowList.value))

                    putOrRemove(PrefKeys.ZAP_PAYMENT_REQUEST_SERVER, settings.zapPaymentRequest.value?.denormalize())

                    putOrRemove(PrefKeys.LATEST_CONTACT_LIST, settings.backupContactList)

                    putOrRemove(PrefKeys.LATEST_USER_METADATA, settings.backupUserMetadata)
                    putOrRemove(PrefKeys.LATEST_DM_RELAY_LIST, settings.backupDMRelayList)
                    putOrRemove(PrefKeys.LATEST_NIP65_RELAY_LIST, settings.backupNIP65RelayList)
                    putOrRemove(PrefKeys.LATEST_SEARCH_RELAY_LIST, settings.backupSearchRelayList)
                    putOrRemove(PrefKeys.LATEST_INDEX_RELAY_LIST, settings.backupIndexRelayList)
                    putOrRemove(PrefKeys.LATEST_RELAY_FEEDS_LIST, settings.backupRelayFeedsList)
                    putOrRemove(PrefKeys.LATEST_BLOCKED_RELAY_LIST, settings.backupBlockedRelayList)
                    putOrRemove(PrefKeys.LATEST_TRUSTED_RELAY_LIST, settings.backupTrustedRelayList)

                    if (settings.localRelayServers.value.isNotEmpty()) {
                        putStringSet(PrefKeys.LOCAL_RELAY_SERVERS, settings.localRelayServers.value)
                    } else {
                        remove(PrefKeys.LOCAL_RELAY_SERVERS)
                    }

                    putOrRemove(PrefKeys.LATEST_MUTE_LIST, settings.backupMuteList)
                    putOrRemove(PrefKeys.LATEST_PRIVATE_HOME_RELAY_LIST, settings.backupPrivateHomeRelayList)
                    putOrRemove(PrefKeys.LATEST_APP_SPECIFIC_DATA, settings.backupAppSpecificData)

                    putOrRemove(PrefKeys.LATEST_CHANNEL_LIST, settings.backupChannelList)
                    putOrRemove(PrefKeys.LATEST_COMMUNITY_LIST, settings.backupCommunityList)
                    putOrRemove(PrefKeys.LATEST_HASHTAG_LIST, settings.backupHashtagList)
                    putOrRemove(PrefKeys.LATEST_GEOHASH_LIST, settings.backupGeohashList)
                    putOrRemove(PrefKeys.LATEST_EPHEMERAL_LIST, settings.backupEphemeralChatList)
                    putOrRemove(PrefKeys.LATEST_TRUST_PROVIDER_LIST, settings.backupTrustProviderList)
                    putOrRemove(PrefKeys.LATEST_PAYMENT_TARGETS, settings.backupNipA3PaymentTargets)

                    putBoolean(PrefKeys.HIDE_DELETE_REQUEST_DIALOG, settings.hideDeleteRequestDialog)
                    putBoolean(PrefKeys.HIDE_NIP_17_WARNING_DIALOG, settings.hideNIP17WarningDialog)
                    putBoolean(PrefKeys.HIDE_BLOCK_ALERT_DIALOG, settings.hideBlockAlertDialog)

                    // migrating from previous design
                    remove(PrefKeys.USE_PROXY)
                    remove(PrefKeys.PROXY_PORT)

                    val regularMap =
                        settings.lastReadPerRoute.value.mapValues {
                            it.value.value
                        }

                    putString(
                        PrefKeys.LAST_READ_PER_ROUTE,
                        JsonMapper.toJson(regularMap),
                    )
                    putStringSet(PrefKeys.HAS_DONATED_IN_VERSION, settings.hasDonatedInVersion.value)
                    putStringSet(PrefKeys.DISMISSED_POLL_NOTE_IDS, settings.dismissedPollNoteIds.value)

                    putString(
                        PrefKeys.PENDING_ATTESTATIONS,
                        JsonMapper.toJson(settings.pendingAttestations.value),
                    )
                }
            }
        }
        Log.d("LocalPreferences", "Saved to encrypted storage")
    }

    suspend fun loadAccountConfigFromEncryptedStorage(): AccountSettings? = currentAccount()?.let { loadAccountConfigFromEncryptedStorage(it) }

    fun saveSharedSettings(
        sharedSettings: UiSettings,
        prefs: SharedPreferences = encryptedPreferences(),
    ) {
        Log.d("LocalPreferences", "Saving to shared settings")
        prefs.edit {
            putString(PrefKeys.SHARED_SETTINGS, JsonMapper.toJson(sharedSettings))
        }
    }

    fun loadSharedSettings(prefs: SharedPreferences = encryptedPreferences()): UiSettings? {
        Log.d("LocalPreferences", "Load shared settings")
        with(prefs) {
            return try {
                getString(PrefKeys.SHARED_SETTINGS, "{}")?.let {
                    JsonMapper.fromJson<UiSettings>(it)
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Log.w(
                    "LocalPreferences",
                    "Unable to decode shared preferences: ${getString(PrefKeys.SHARED_SETTINGS, null)}",
                    e,
                )
                null
            }
        }
    }

    val mutex = Mutex()

    suspend fun loadAccountConfigFromEncryptedStorage(npub: String): AccountSettings? {
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
        Log.d("LocalPreferences") { "Load account from file $npub" }
        val result =
            withContext(Dispatchers.IO) {
                return@withContext with(encryptedPreferences(npub)) {
                    Log.d("LocalPreferences") { "Load account from file $npub - opened file" }
                    val privKey = getString(PrefKeys.NOSTR_PRIVKEY, null)
                    val pubKey = getString(PrefKeys.NOSTR_PUBKEY, null) ?: return@with null
                    val externalSignerPackageName = getString(PrefKeys.SIGNER_PACKAGE_NAME, null) ?: if (getBoolean(PrefKeys.LOGIN_WITH_EXTERNAL_SIGNER, false)) "com.greenart7c3.nostrsigner" else null

                    val keyPair = KeyPair(privKey = privKey?.hexToByteArray(), pubKey = pubKey.hexToByteArray())

                    Log.d("LocalPreferences") { "Load account from file $npub - keys ready" }

                    val stripLocationOnUpload = getBoolean(PrefKeys.STRIP_LOCATION_ON_UPLOAD, true)
                    val hideDeleteRequestDialog = getBoolean(PrefKeys.HIDE_DELETE_REQUEST_DIALOG, false)
                    val hideBlockAlertDialog = getBoolean(PrefKeys.HIDE_BLOCK_ALERT_DIALOG, false)
                    val hideNIP17WarningDialog = getBoolean(PrefKeys.HIDE_NIP_17_WARNING_DIALOG, false)
                    val hasDonatedInVersion = getStringSet(PrefKeys.HAS_DONATED_IN_VERSION, null) ?: setOf()
                    val dismissedPollNoteIds = getStringSet(PrefKeys.DISMISSED_POLL_NOTE_IDS, null) ?: setOf()
                    val localRelayServers = getStringSet(PrefKeys.LOCAL_RELAY_SERVERS, null) ?: setOf()

                    val defaultHomeFollowListStr = getString(PrefKeys.DEFAULT_HOME_FOLLOW_LIST, null)
                    val defaultStoriesFollowListStr = getString(PrefKeys.DEFAULT_STORIES_FOLLOW_LIST, null)
                    val defaultNotificationFollowListStr = getString(PrefKeys.DEFAULT_NOTIFICATION_FOLLOW_LIST, null)
                    val defaultDiscoveryFollowListStr = getString(PrefKeys.DEFAULT_DISCOVERY_FOLLOW_LIST, null)

                    val defaultPollsFollowListStr = getString(PrefKeys.DEFAULT_POLLS_FOLLOW_LIST, null)
                    val defaultPicturesFollowListStr = getString(PrefKeys.DEFAULT_PICTURES_FOLLOW_LIST, null)
                    val defaultShortsFollowListStr = getString(PrefKeys.DEFAULT_SHORTS_FOLLOW_LIST, null)
                    val defaultLongsFollowListStr = getString(PrefKeys.DEFAULT_LONGS_FOLLOW_LIST, null)

                    val zapPaymentRequestServerStr = getString(PrefKeys.ZAP_PAYMENT_REQUEST_SERVER, null)
                    val defaultFileServerStr = getString(PrefKeys.DEFAULT_FILE_SERVER, null)

                    val pendingAttestationsStr = getString(PrefKeys.PENDING_ATTESTATIONS, null)
                    val latestUserMetadataStr = getString(PrefKeys.LATEST_USER_METADATA, null)
                    val latestContactListStr = getString(PrefKeys.LATEST_CONTACT_LIST, null)
                    val latestDmRelayListStr = getString(PrefKeys.LATEST_DM_RELAY_LIST, null)
                    val latestNip65RelayListStr = getString(PrefKeys.LATEST_NIP65_RELAY_LIST, null)
                    val latestSearchRelayListStr = getString(PrefKeys.LATEST_SEARCH_RELAY_LIST, null)
                    val latestIndexRelayListStr = getString(PrefKeys.LATEST_INDEX_RELAY_LIST, null)
                    val latestRelayFeedsListStr = getString(PrefKeys.LATEST_RELAY_FEEDS_LIST, null)
                    val latestBlockedRelayListStr = getString(PrefKeys.LATEST_BLOCKED_RELAY_LIST, null)
                    val latestTrustedRelayListStr = getString(PrefKeys.LATEST_TRUSTED_RELAY_LIST, null)
                    val latestMuteListStr = getString(PrefKeys.LATEST_MUTE_LIST, null)
                    val latestPrivateHomeRelayListStr = getString(PrefKeys.LATEST_PRIVATE_HOME_RELAY_LIST, null)
                    val latestAppSpecificDataStr = getString(PrefKeys.LATEST_APP_SPECIFIC_DATA, null)
                    val latestChannelListStr = getString(PrefKeys.LATEST_CHANNEL_LIST, null)
                    val latestCommunityListStr = getString(PrefKeys.LATEST_COMMUNITY_LIST, null)
                    val latestHashtagListStr = getString(PrefKeys.LATEST_HASHTAG_LIST, null)
                    val latestGeohashListStr = getString(PrefKeys.LATEST_GEOHASH_LIST, null)
                    val latestEphemeralListStr = getString(PrefKeys.LATEST_EPHEMERAL_LIST, null)
                    val latestTrustProviderListStr = getString(PrefKeys.LATEST_TRUST_PROVIDER_LIST, null)
                    val latestPaymentTargetsStr = getString(PrefKeys.LATEST_PAYMENT_TARGETS, null)
                    val lastReadPerRouteStr = getString(PrefKeys.LAST_READ_PER_ROUTE, null)

                    Log.d("LocalPreferences") { "Load account from file $npub - before parsing events" }

                    val defaultHomeFollowList = async { parseOrNull<TopFilter>(defaultHomeFollowListStr) ?: TopFilter.AllFollows }
                    val defaultStoriesFollowList = async { parseOrNull<TopFilter>(defaultStoriesFollowListStr) ?: TopFilter.Global }
                    val defaultNotificationFollowList = async { parseOrNull<TopFilter>(defaultNotificationFollowListStr) ?: TopFilter.Global }
                    val defaultDiscoveryFollowList = async { parseOrNull<TopFilter>(defaultDiscoveryFollowListStr) ?: TopFilter.Global }

                    val defaultPollsFollowList = async { parseOrNull<TopFilter>(defaultPollsFollowListStr) ?: TopFilter.Global }
                    val defaultPicturesFollowList = async { parseOrNull<TopFilter>(defaultPicturesFollowListStr) ?: TopFilter.Global }
                    val defaultShortsFollowList = async { parseOrNull<TopFilter>(defaultShortsFollowListStr) ?: TopFilter.Global }
                    val defaultLongsFollowList = async { parseOrNull<TopFilter>(defaultLongsFollowListStr) ?: TopFilter.Global }

                    val zapPaymentRequestServer = async { parseOrNull<Nip47WalletConnect.Nip47URI>(zapPaymentRequestServerStr) }
                    val defaultFileServer = async { parseOrNull<ServerName>(defaultFileServerStr) ?: DEFAULT_MEDIA_SERVERS[0] }

                    val pendingAttestations = async { parseOrNull<Map<HexKey, String>>(pendingAttestationsStr) ?: mapOf() }
                    val latestUserMetadata = async { parseEventOrNull<MetadataEvent>(latestUserMetadataStr) }
                    val latestContactList = async { parseEventOrNull<ContactListEvent>(latestContactListStr) }
                    val latestDmRelayList = async { parseEventOrNull<ChatMessageRelayListEvent>(latestDmRelayListStr) }
                    val latestNip65RelayList = async { parseEventOrNull<AdvertisedRelayListEvent>(latestNip65RelayListStr) }
                    val latestSearchRelayList = async { parseEventOrNull<SearchRelayListEvent>(latestSearchRelayListStr) }
                    val latestIndexRelayList = async { parseEventOrNull<IndexerRelayListEvent>(latestIndexRelayListStr) }
                    val latestRelayFeedsList = async { parseEventOrNull<RelayFeedsListEvent>(latestRelayFeedsListStr) }
                    val latestBlockedRelayList = async { parseEventOrNull<BlockedRelayListEvent>(latestBlockedRelayListStr) }
                    val latestTrustedRelayList = async { parseEventOrNull<TrustedRelayListEvent>(latestTrustedRelayListStr) }
                    val latestMuteList = async { parseEventOrNull<MuteListEvent>(latestMuteListStr) }
                    val latestPrivateHomeRelayList = async { parseEventOrNull<PrivateOutboxRelayListEvent>(latestPrivateHomeRelayListStr) }
                    val latestAppSpecificData = async { parseEventOrNull<AppSpecificDataEvent>(latestAppSpecificDataStr) }
                    val latestChannelList = async { parseEventOrNull<ChannelListEvent>(latestChannelListStr) }
                    val latestCommunityList = async { parseEventOrNull<CommunityListEvent>(latestCommunityListStr) }
                    val latestHashtagList = async { parseEventOrNull<HashtagListEvent>(latestHashtagListStr) }
                    val latestGeohashList = async { parseEventOrNull<GeohashListEvent>(latestGeohashListStr) }
                    val latestEphemeralList = async { parseEventOrNull<EphemeralChatListEvent>(latestEphemeralListStr) }
                    val latestTrustProviderList = async { parseEventOrNull<TrustProviderListEvent>(latestTrustProviderListStr) }
                    val latestPaymentTargets = async { parseEventOrNull<PaymentTargetsEvent>(latestPaymentTargetsStr) }

                    val lastReadPerRoute =
                        async {
                            parseOrNull<Map<String, Long>>(lastReadPerRouteStr)?.mapValues {
                                MutableStateFlow(it.value)
                            } ?: mapOf()
                        }

                    Log.d("LocalPreferences") { "Load account from file $npub - asyncs created" }

                    return@with AccountSettings(
                        keyPair = keyPair,
                        transientAccount = false,
                        externalSignerPackageName = externalSignerPackageName,
                        localRelayServers = MutableStateFlow(localRelayServers),
                        defaultFileServer = defaultFileServer.await(),
                        stripLocationOnUpload = stripLocationOnUpload,
                        defaultHomeFollowList = MutableStateFlow(defaultHomeFollowList.await()),
                        defaultStoriesFollowList = MutableStateFlow(defaultStoriesFollowList.await()),
                        defaultNotificationFollowList = MutableStateFlow(defaultNotificationFollowList.await()),
                        defaultDiscoveryFollowList = MutableStateFlow(defaultDiscoveryFollowList.await()),
                        defaultPollsFollowList = MutableStateFlow(defaultPollsFollowList.await()),
                        defaultPicturesFollowList = MutableStateFlow(defaultPicturesFollowList.await()),
                        defaultShortsFollowList = MutableStateFlow(defaultShortsFollowList.await()),
                        defaultLongsFollowList = MutableStateFlow(defaultLongsFollowList.await()),
                        zapPaymentRequest = MutableStateFlow(zapPaymentRequestServer.await()?.normalize()),
                        hideDeleteRequestDialog = hideDeleteRequestDialog,
                        hideBlockAlertDialog = hideBlockAlertDialog,
                        hideNIP17WarningDialog = hideNIP17WarningDialog,
                        backupUserMetadata = latestUserMetadata.await(),
                        backupContactList = latestContactList.await(),
                        backupNIP65RelayList = latestNip65RelayList.await(),
                        backupDMRelayList = latestDmRelayList.await(),
                        backupSearchRelayList = latestSearchRelayList.await(),
                        backupIndexRelayList = latestIndexRelayList.await(),
                        backupRelayFeedsList = latestRelayFeedsList.await(),
                        backupBlockedRelayList = latestBlockedRelayList.await(),
                        backupTrustedRelayList = latestTrustedRelayList.await(),
                        backupPrivateHomeRelayList = latestPrivateHomeRelayList.await(),
                        backupMuteList = latestMuteList.await(),
                        backupAppSpecificData = latestAppSpecificData.await(),
                        backupChannelList = latestChannelList.await(),
                        backupCommunityList = latestCommunityList.await(),
                        backupHashtagList = latestHashtagList.await(),
                        backupGeohashList = latestGeohashList.await(),
                        backupEphemeralChatList = latestEphemeralList.await(),
                        backupTrustProviderList = latestTrustProviderList.await(),
                        lastReadPerRoute = MutableStateFlow(lastReadPerRoute.await()),
                        hasDonatedInVersion = MutableStateFlow(hasDonatedInVersion),
                        dismissedPollNoteIds = MutableStateFlow(dismissedPollNoteIds),
                        pendingAttestations = MutableStateFlow(pendingAttestations.await()),
                        backupNipA3PaymentTargets = latestPaymentTargets.await(),
                    )
                }
            }
        Log.d("LocalPreferences") { "Loaded account from file $npub" }
        return result
    }

    private inline fun <reified T : Any> parseOrNull(value: String?): T? {
        if (value.isNullOrEmpty() || value == "null") {
            return null
        }
        return try {
            if (T::class.java.isInstance(Event::class.java)) {
                Event.fromJson(value) as T?
            } else {
                JsonMapper.fromJson<T>(value)
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Log.w("LocalPreferences", "Error Decoding ${T::class.java} from Preferences with value $value", e)
            null
        }
    }

    private inline fun <reified T> parseEventOrNull(value: String?): T? {
        if (value.isNullOrEmpty() || value == "null") {
            return null
        }
        return try {
            Event.fromJson(value) as T?
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Log.w("LocalPreferences", "Error Decoding ${T::class.java} from Preferences with value $value", e)
            null
        }
    }

    fun SharedPreferences.Editor.putOrRemove(
        key: String,
        event: Any?,
    ) {
        if (event != null) {
            putString(key, JsonMapper.toJson(event))
        } else {
            remove(key)
        }
    }

    fun SharedPreferences.Editor.putOrRemove(
        key: String,
        event: Event?,
    ) {
        if (event != null) {
            putString(key, OptimizedJsonMapper.toJson(event))
        } else {
            remove(key)
        }
    }

    fun SharedPreferences.Editor.putOrRemove(
        key: String,
        nwc: Nip47WalletConnect.Nip47URI?,
    ) {
        if (nwc != null) {
            putString(key, JsonMapper.toJson(nwc))
        } else {
            remove(key)
        }
    }
}
