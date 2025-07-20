/**
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
package com.vitorpamplona.amethyst.model

import android.util.Log
import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.ui.actions.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.tor.TorSettings
import com.vitorpamplona.amethyst.ui.tor.TorSettingsFlow
import com.vitorpamplona.ammolite.relays.Constants
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import com.vitorpamplona.quartz.experimental.edits.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.experimental.ephemChat.list.EphemeralChatListEvent
import com.vitorpamplona.quartz.experimental.tipping.TipEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip28PublicChat.list.ChannelListEvent
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.MuteListEvent
import com.vitorpamplona.quartz.nip55AndroidSigner.ExternalSignerLauncher
import com.vitorpamplona.quartz.nip55AndroidSigner.NostrSignerExternal
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.RelayUrlFormatter
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.Locale

val DefaultChannelSet =
    setOf(
        // Anigma's Nostr
        "25e5c82273a271cb1a840d0060391a0bf4965cafeb029d5ab55350b418953fbb",
        // Amethyst's Group
        "42224859763652914db53052103f0b744df79dfc4efef7e950fc0802fc3df3c5",
    )

val DefaultChannels =
    listOf(
        // Anigma's Nostr
        EventIdHint("25e5c82273a271cb1a840d0060391a0bf4965cafeb029d5ab55350b418953fbb", "wss://nos.lol"),
        // Amethyst's Group
        EventIdHint("42224859763652914db53052103f0b744df79dfc4efef7e950fc0802fc3df3c5", "wss://nos.lol"),
    )

val DefaultNIP65List =
    listOf(
        AdvertisedRelayListEvent.AdvertisedRelayInfo(RelayUrlFormatter.normalize("wss://nostr.mom/"), AdvertisedRelayListEvent.AdvertisedRelayType.BOTH),
        AdvertisedRelayListEvent.AdvertisedRelayInfo(RelayUrlFormatter.normalize("wss://nos.lol/"), AdvertisedRelayListEvent.AdvertisedRelayType.BOTH),
        AdvertisedRelayListEvent.AdvertisedRelayInfo(RelayUrlFormatter.normalize("wss://nostr.bitcoiner.social/"), AdvertisedRelayListEvent.AdvertisedRelayType.BOTH),
    )

val DefaultDMRelayList =
    listOf(
        RelayUrlFormatter.normalize("wss://auth.nostr1.com"),
        RelayUrlFormatter.normalize("wss://relay.0xchat.com"),
        RelayUrlFormatter.normalize("wss://nos.lol"),
    )

val DefaultSearchRelayList =
    listOf(
        RelayUrlFormatter.normalize("wss://relay.nostr.band"),
        RelayUrlFormatter.normalize("wss://nostr.wine"),
        RelayUrlFormatter.normalize("wss://relay.noswhere.com"),
        RelayUrlFormatter.normalize("wss://search.nos.today"),
    )

// This has spaces to avoid mixing with a potential NIP-51 list with the same name.
val GLOBAL_FOLLOWS = " Global "

// This has spaces to avoid mixing with a potential NIP-51 list with the same name.
val KIND3_FOLLOWS = " All Follows "

// This has spaces to avoid mixing with a potential NIP-51 list with the same name.
val AROUND_ME = " Around Me "

@Stable
class AccountSettings(
    val keyPair: KeyPair,
    val transientAccount: Boolean = false,
    var externalSignerPackageName: String? = null,
    var localRelays: Set<RelaySetupInfo> = Constants.defaultRelays.toSet(),
    var localRelayServers: Set<String> = setOf(),
    var defaultFileServer: ServerName = DEFAULT_MEDIA_SERVERS[0],
    val defaultHomeFollowList: MutableStateFlow<String> = MutableStateFlow(KIND3_FOLLOWS),
    val defaultStoriesFollowList: MutableStateFlow<String> = MutableStateFlow(GLOBAL_FOLLOWS),
    val defaultNotificationFollowList: MutableStateFlow<String> = MutableStateFlow(GLOBAL_FOLLOWS),
    val defaultDiscoveryFollowList: MutableStateFlow<String> = MutableStateFlow(GLOBAL_FOLLOWS),
    var zapPaymentRequest: Nip47WalletConnect.Nip47URI? = null,
    var hideDeleteRequestDialog: Boolean = false,
    var hideBlockAlertDialog: Boolean = false,
    var hideNIP17WarningDialog: Boolean = false,
    var backupUserMetadata: MetadataEvent? = null,
    var backupContactList: ContactListEvent? = null,
    var backupDMRelayList: ChatMessageRelayListEvent? = null,
    var backupNIP65RelayList: AdvertisedRelayListEvent? = null,
    var backupSearchRelayList: SearchRelayListEvent? = null,
    var backupMuteList: MuteListEvent? = null,
    var backupPrivateHomeRelayList: PrivateOutboxRelayListEvent? = null,
    var backupAppSpecificData: AppSpecificDataEvent? = null,
    var backupChannelList: ChannelListEvent? = null,
    var backupEphemeralChatList: EphemeralChatListEvent? = null,
    val torSettings: TorSettingsFlow = TorSettingsFlow(),
    val lastReadPerRoute: MutableStateFlow<Map<String, MutableStateFlow<Long>>> = MutableStateFlow(mapOf()),
    var hasDonatedInVersion: MutableStateFlow<Set<String>> = MutableStateFlow(setOf<String>()),
    val pendingAttestations: MutableStateFlow<Map<HexKey, String>> = MutableStateFlow<Map<HexKey, String>>(mapOf()),
) {
    val saveable = MutableStateFlow(AccountSettingsUpdater(null))
    val syncedSettings: AccountSyncedSettings = AccountSyncedSettings(AccountSyncedSettingsInternal())

    class AccountSettingsUpdater(
        val accountSettings: AccountSettings?,
    )

    fun saveAccountSettings() {
        saveable.update { AccountSettingsUpdater(this) }
    }

    fun isWriteable(): Boolean = keyPair.privKey != null || externalSignerPackageName != null

    fun createSigner() =
        if (keyPair.privKey != null) {
            NostrSignerInternal(keyPair)
        } else {
            when (val packageName = externalSignerPackageName) {
                null -> NostrSignerInternal(keyPair)
                else -> {
                    val externalSignerLauncher = ExternalSignerLauncher(keyPair.pubKey.toHexKey(), packageName)
                    // TODO: How to handle the launcher here?
                    try {
                        externalSignerLauncher.registerLauncher(
                            launcher = { },
                            contentResolver = Amethyst.instance::contentResolverFn,
                        )
                    } catch (e: Exception) {
                        Log.d("AccountSettings", "Failed to initialize external signer", e)
                    }
                    NostrSignerExternal(keyPair.pubKey.toHexKey(), externalSignerLauncher)
                }
            }
        }

    // ---
    // Zaps and Reactions
    // ---

    fun changeDefaultZapType(zapType: LnZapEvent.ZapType): Boolean {
        if (syncedSettings.zaps.defaultZapType.value != zapType) {
            syncedSettings.zaps.defaultZapType.tryEmit(zapType)
            saveAccountSettings()
            return true
        }
        return false
    }

    fun changeDefaultTipType(tipType: TipEvent.TipType): Boolean {
        if (syncedSettings.tips.defaultTipType.value != tipType) {
            syncedSettings.tips.defaultTipType.tryEmit(tipType)
            saveAccountSettings()
            return true
        }
        return false
    }

    fun changeTipAmounts(newAmounts: List<Double>): Boolean {
        if (syncedSettings.tips.tipAmountChoices.value != newAmounts) {
            syncedSettings.tips.tipAmountChoices.tryEmit(newAmounts.toImmutableList())
            saveAccountSettings()
            return true
        }
        return false
    }

    fun changeZapAmounts(newAmounts: List<Long>): Boolean {
        if (syncedSettings.zaps.zapAmountChoices.value != newAmounts) {
            syncedSettings.zaps.zapAmountChoices.tryEmit(newAmounts.toImmutableList())
            saveAccountSettings()
            return true
        }
        return false
    }

    fun changeReactionTypes(newTypes: List<String>): Boolean {
        if (syncedSettings.reactions.reactionChoices.value != newTypes) {
            syncedSettings.reactions.reactionChoices.tryEmit(newTypes.toImmutableList())
            saveAccountSettings()
            return true
        }
        return false
    }

    fun changeZapPaymentRequest(newServer: Nip47WalletConnect.Nip47URI?): Boolean {
        if (zapPaymentRequest != newServer) {
            zapPaymentRequest = newServer
            saveAccountSettings()
            return true
        }
        return false
    }

    // ---
    // file servers
    // ---

    fun changeDefaultFileServer(server: ServerName) {
        if (defaultFileServer != server) {
            defaultFileServer = server
            saveAccountSettings()
        }
    }

    // ---
    // list names
    // ---

    fun changeDefaultHomeFollowList(name: String) {
        if (defaultHomeFollowList.value != name) {
            defaultHomeFollowList.tryEmit(name)
            saveAccountSettings()
        }
    }

    fun changeDefaultStoriesFollowList(name: String) {
        if (defaultStoriesFollowList.value != name) {
            defaultStoriesFollowList.tryEmit(name)
            saveAccountSettings()
        }
    }

    fun changeDefaultNotificationFollowList(name: String) {
        if (defaultNotificationFollowList.value != name) {
            defaultNotificationFollowList.tryEmit(name)
            saveAccountSettings()
        }
    }

    fun changeDefaultDiscoveryFollowList(name: String) {
        if (defaultDiscoveryFollowList.value != name) {
            defaultDiscoveryFollowList.tryEmit(name)
            saveAccountSettings()
        }
    }

    // ---
    // proxy settings
    // ---
    fun setTorSettings(newTorSettings: TorSettings): Boolean {
        if (torSettings.update(newTorSettings)) {
            saveAccountSettings()
            return true
        } else {
            return false
        }
    }

    // ---
    // language services
    // ---
    fun toggleDontTranslateFrom(languageCode: String) {
        syncedSettings.languages.toggleDontTranslateFrom(languageCode)
        saveAccountSettings()
    }

    fun translateToContains(languageCode: Locale) = syncedSettings.languages.translateTo.contains(languageCode.language)

    fun updateTranslateTo(languageCode: Locale): Boolean {
        if (syncedSettings.languages.updateTranslateTo(languageCode)) {
            saveAccountSettings()
            return true
        }
        return false
    }

    fun prefer(
        source: String,
        target: String,
        preference: String,
    ) {
        syncedSettings.languages.prefer(source, target, preference)
        saveAccountSettings()
    }

    fun preferenceBetween(
        source: String,
        target: String,
    ): String? = syncedSettings.languages.preferenceBetween(source, target)

    // ----
    // Backup Lists
    // ----

    fun updateLocalRelayServers(servers: Set<String>) {
        if (localRelayServers != servers) {
            localRelayServers = servers
            saveAccountSettings()
        }
    }

    fun updateUserMetadata(newMetadata: MetadataEvent?) {
        if (newMetadata == null) return

        // Events might be different objects, we have to compare their ids.
        if (backupUserMetadata?.id != newMetadata.id) {
            backupUserMetadata = newMetadata
            saveAccountSettings()
        }
    }

    fun updateContactListTo(newContactList: ContactListEvent?) {
        if (newContactList == null || newContactList.tags.isEmpty()) return

        // Events might be different objects, we have to compare their ids.
        if (backupContactList?.id != newContactList.id) {
            backupContactList = newContactList
            saveAccountSettings()
        }
    }

    fun updateDMRelayList(newDMRelayList: ChatMessageRelayListEvent?) {
        if (newDMRelayList == null || newDMRelayList.tags.isEmpty()) return

        // Events might be different objects, we have to compare their ids.
        if (backupDMRelayList?.id != newDMRelayList.id) {
            backupDMRelayList = newDMRelayList
            saveAccountSettings()
        }
    }

    fun updateNIP65RelayList(newNIP65RelayList: AdvertisedRelayListEvent?) {
        if (newNIP65RelayList == null || newNIP65RelayList.tags.isEmpty()) return

        // Events might be different objects, we have to compare their ids.
        if (backupNIP65RelayList?.id != newNIP65RelayList.id) {
            backupNIP65RelayList = newNIP65RelayList
            saveAccountSettings()
        }
    }

    fun updateSearchRelayList(newSearchRelayList: SearchRelayListEvent?) {
        if (newSearchRelayList == null || newSearchRelayList.tags.isEmpty()) return

        // Events might be different objects, we have to compare their ids.
        if (backupSearchRelayList?.id != newSearchRelayList.id) {
            backupSearchRelayList = newSearchRelayList
            saveAccountSettings()
        }
    }

    fun updatePrivateHomeRelayList(newPrivateHomeRelayList: PrivateOutboxRelayListEvent?) {
        if (newPrivateHomeRelayList == null || newPrivateHomeRelayList.tags.isEmpty()) return

        // Events might be different objects, we have to compare their ids.
        if (backupPrivateHomeRelayList?.id != newPrivateHomeRelayList.id) {
            backupPrivateHomeRelayList = newPrivateHomeRelayList
            saveAccountSettings()
        }
    }

    fun updateChannelListTo(newChannelList: ChannelListEvent?) {
        if (newChannelList == null || newChannelList.tags.isEmpty()) return

        // Events might be different objects, we have to compare their ids.
        if (backupChannelList?.id != newChannelList.id) {
            backupChannelList = newChannelList
            saveAccountSettings()
        }
    }

    fun updateEphemeralChatListTo(newEphemeralChatList: EphemeralChatListEvent?) {
        if (newEphemeralChatList == null || newEphemeralChatList.tags.isEmpty()) return

        // Events might be different objects, we have to compare their ids.
        if (backupEphemeralChatList?.id != newEphemeralChatList.id) {
            backupEphemeralChatList = newEphemeralChatList
            saveAccountSettings()
        }
    }

    fun updateMuteList(newMuteList: MuteListEvent?) {
        if (newMuteList == null || newMuteList.tags.isEmpty()) return

        // Events might be different objects, we have to compare their ids.
        if (backupMuteList?.id != newMuteList.id) {
            backupMuteList = newMuteList
            saveAccountSettings()
        }
    }

    fun updateAppSpecificData(
        appSettings: AppSpecificDataEvent?,
        newSyncedSettings: AccountSyncedSettingsInternal,
    ) {
        if (appSettings == null || appSettings.content.isEmpty()) return

        // Events might be different objects, we have to compare their ids.
        if (backupAppSpecificData?.id != appSettings.id) {
            backupAppSpecificData = appSettings
            syncedSettings.updateFrom(newSyncedSettings)

            saveAccountSettings()
        }
    }

    // ----
    // Warning dialogs
    // ----

    fun setHideDeleteRequestDialog() {
        if (!hideDeleteRequestDialog) {
            hideDeleteRequestDialog = true
            saveAccountSettings()
        }
    }

    fun setHideNIP17WarningDialog() {
        if (!hideNIP17WarningDialog) {
            hideNIP17WarningDialog = true
            saveAccountSettings()
        }
    }

    fun setHideBlockAlertDialog() {
        if (!hideBlockAlertDialog) {
            hideBlockAlertDialog = true
            saveAccountSettings()
        }
    }

    // ---
    // donations
    // ---

    fun hasDonatedInVersion(versionName: String) = hasDonatedInVersion.value.contains(versionName)

    fun observeDonatedInVersion(versionName: String) =
        hasDonatedInVersion
            .map {
                it.contains(versionName)
            }

    fun markDonatedInThisVersion(versionName: String): Boolean {
        if (!hasDonatedInVersion.value.contains(versionName)) {
            hasDonatedInVersion.update {
                it + versionName
            }
            saveAccountSettings()
            return true
        }
        return false
    }

    // ----
    // last read flows
    // ----

    fun getLastReadFlow(route: String): StateFlow<Long> = lastReadPerRoute.value[route] ?: addLastRead(route, 0)

    private fun addLastRead(
        route: String,
        timestampInSecs: Long,
    ): MutableStateFlow<Long> =
        MutableStateFlow<Long>(timestampInSecs).also { newFlow ->
            lastReadPerRoute.update { it + Pair(route, newFlow) }
            saveAccountSettings()
        }

    fun markAsRead(
        route: String,
        timestampInSecs: Long,
    ): Boolean {
        val lastTime = lastReadPerRoute.value[route]
        return if (lastTime == null) {
            addLastRead(route, timestampInSecs)
            true
        } else if (timestampInSecs > lastTime.value) {
            lastTime.tryEmit(timestampInSecs)
            saveAccountSettings()
            true
        } else {
            false
        }
    }

    // ----
    // local relays
    // ----

    fun updateLocalRelays(newLocalRelays: Set<RelaySetupInfo>) {
        if (!localRelays.equals(newLocalRelays)) {
            localRelays = newLocalRelays
            saveAccountSettings()
        }
    }

    // ---
    // attestations
    // ---

    fun addPendingAttestation(
        id: HexKey,
        stamp: String,
    ) {
        val current = pendingAttestations.value.get(id)
        if (current == null) {
            pendingAttestations.update {
                it + Pair(id, stamp)
            }
            saveAccountSettings()
        } else {
            if (current != stamp) {
                pendingAttestations.update {
                    it + Pair(id, stamp)
                }
                saveAccountSettings()
            }
        }
    }

    // ---
    // filters
    // ---
    fun updateShowSensitiveContent(show: Boolean?): Boolean {
        if (syncedSettings.security.updateShowSensitiveContent(show)) {
            saveAccountSettings()
            return true
        }
        return false
    }

    fun updateWarnReports(warnReports: Boolean): Boolean =
        if (syncedSettings.security.updateWarnReports(warnReports)) {
            saveAccountSettings()
            true
        } else {
            false
        }

    fun updateFilterSpam(filterSpam: Boolean): Boolean =
        if (syncedSettings.security.updateFilterSpam(filterSpam)) {
            saveAccountSettings()
            true
        } else {
            false
        }
}
