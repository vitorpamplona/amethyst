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
package com.vitorpamplona.amethyst.model

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatRepository
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatListRepository
import com.vitorpamplona.amethyst.ui.actions.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.screen.FeedDefinition
import com.vitorpamplona.quartz.experimental.ephemChat.list.EphemeralChatListEvent
import com.vitorpamplona.quartz.experimental.nipA3.PaymentTargetsEvent
import com.vitorpamplona.quartz.experimental.trustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip28PublicChat.list.ChannelListEvent
import com.vitorpamplona.quartz.nip28PublicChat.list.tags.ChannelTag
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip37Drafts.privateOutbox.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.geohashList.GeohashListEvent
import com.vitorpamplona.quartz.nip51Lists.hashtagList.HashtagListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BlockedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.IndexerRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.TrustedRelayListEvent
import com.vitorpamplona.quartz.nip55AndroidSigner.api.CommandType
import com.vitorpamplona.quartz.nip55AndroidSigner.api.permission.Permission
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayInfo
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayType
import com.vitorpamplona.quartz.nip72ModCommunities.follow.CommunityListEvent
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.Locale

val DefaultChannels =
    listOf(
        // Anigma's Nostr
        ChannelTag("25e5c82273a271cb1a840d0060391a0bf4965cafeb029d5ab55350b418953fbb", Constants.nos),
        // Amethyst's Group
        ChannelTag("42224859763652914db53052103f0b744df79dfc4efef7e950fc0802fc3df3c5", Constants.nos),
    )

val DefaultNIP65RelaySet = setOf(Constants.mom, Constants.nos, Constants.bitcoiner)

val DefaultNIP65List =
    listOf(
        AdvertisedRelayInfo(Constants.mom, AdvertisedRelayType.BOTH),
        AdvertisedRelayInfo(Constants.nos, AdvertisedRelayType.BOTH),
        AdvertisedRelayInfo(Constants.bitcoiner, AdvertisedRelayType.BOTH),
    )

val DefaultDMRelayList = listOf(Constants.auth, Constants.oxchat, Constants.nos)

val DefaultSearchRelayList = setOf(Constants.wine, Constants.where, Constants.nostoday, Constants.antiprimal, Constants.ditto)

val DefaultIndexerRelayList = setOf(Constants.purplepages, Constants.coracle, Constants.userkinds, Constants.yabu, Constants.nostr1)

val DefaultSignerPermissions =
    listOf(
        Permission(CommandType.SIGN_EVENT, RelayAuthEvent.KIND),
        Permission(CommandType.SIGN_EVENT, DraftWrapEvent.KIND),
        Permission(CommandType.NIP04_ENCRYPT),
        Permission(CommandType.NIP04_DECRYPT),
        Permission(CommandType.NIP44_DECRYPT),
        Permission(CommandType.NIP44_DECRYPT),
        Permission(CommandType.DECRYPT_ZAP_EVENT),
    )

// This has spaces to avoid mixing with a potential NIP-51 list with the same name.
val GLOBAL_FOLLOWS = " Global "

// This has spaces to avoid mixing with a potential NIP-51 list with the same name.
val ALL_FOLLOWS = " All Follows "

// This has spaces to avoid mixing with a potential NIP-51 list with the same name.
val ALL_USER_FOLLOWS = " All User Follows "

// This has spaces to avoid mixing with a potential NIP-51 list with the same name.
val KIND3_FOLLOWS = " Main User Follows "

// This has spaces to avoid mixing with a potential NIP-51 list with the same name.
val AROUND_ME = " Around Me "

// This has spaces to avoid mixing with a potential NIP-51 list with the same name.
val CHESS = " Chess "

@Stable
class AccountSettings(
    val keyPair: KeyPair,
    val transientAccount: Boolean = false,
    var externalSignerPackageName: String? = null,
    var localRelayServers: MutableStateFlow<Set<String>> = MutableStateFlow(setOf()),
    var defaultFileServer: ServerName = DEFAULT_MEDIA_SERVERS[0],
    val defaultHomeFollowList: MutableStateFlow<String> = MutableStateFlow(ALL_FOLLOWS),
    val defaultStoriesFollowList: MutableStateFlow<String> = MutableStateFlow(GLOBAL_FOLLOWS),
    val defaultNotificationFollowList: MutableStateFlow<String> = MutableStateFlow(GLOBAL_FOLLOWS),
    val defaultDiscoveryFollowList: MutableStateFlow<String> = MutableStateFlow(GLOBAL_FOLLOWS),
    val zapPaymentRequest: MutableStateFlow<Nip47WalletConnect.Nip47URINorm?> = MutableStateFlow(null),
    var hideDeleteRequestDialog: Boolean = false,
    var hideBlockAlertDialog: Boolean = false,
    var hideNIP17WarningDialog: Boolean = false,
    var backupUserMetadata: MetadataEvent? = null,
    var backupContactList: ContactListEvent? = null,
    var backupDMRelayList: ChatMessageRelayListEvent? = null,
    var backupNIP65RelayList: AdvertisedRelayListEvent? = null,
    var backupSearchRelayList: SearchRelayListEvent? = null,
    var backupIndexRelayList: IndexerRelayListEvent? = null,
    var backupBlockedRelayList: BlockedRelayListEvent? = null,
    var backupTrustedRelayList: TrustedRelayListEvent? = null,
    var backupMuteList: MuteListEvent? = null,
    var backupPrivateHomeRelayList: PrivateOutboxRelayListEvent? = null,
    var backupAppSpecificData: AppSpecificDataEvent? = null,
    var backupChannelList: ChannelListEvent? = null,
    var backupCommunityList: CommunityListEvent? = null,
    var backupHashtagList: HashtagListEvent? = null,
    var backupGeohashList: GeohashListEvent? = null,
    var backupEphemeralChatList: EphemeralChatListEvent? = null,
    var backupTrustProviderList: TrustProviderListEvent? = null,
    val lastReadPerRoute: MutableStateFlow<Map<String, MutableStateFlow<Long>>> = MutableStateFlow(mapOf()),
    val hasDonatedInVersion: MutableStateFlow<Set<String>> = MutableStateFlow(setOf<String>()),
    val pendingAttestations: MutableStateFlow<Map<HexKey, String>> = MutableStateFlow<Map<HexKey, String>>(mapOf()),
    var backupNipA3PaymentTargets: PaymentTargetsEvent? = null,
) : EphemeralChatRepository,
    PublicChatListRepository {
    val saveable = MutableStateFlow(AccountSettingsUpdater(null))
    val syncedSettings: AccountSyncedSettings = AccountSyncedSettings(AccountSyncedSettingsInternal())

    class AccountSettingsUpdater(
        val accountSettings: AccountSettings?,
    )

    fun saveAccountSettings() {
        saveable.update { AccountSettingsUpdater(this) }
    }

    fun isWriteable(): Boolean = keyPair.privKey != null || externalSignerPackageName != null

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

    fun changeZapPaymentRequest(newServer: Nip47WalletConnect.Nip47URINorm?): Boolean {
        if (zapPaymentRequest.value != newServer) {
            zapPaymentRequest.tryEmit(newServer)
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

    fun changeDefaultHomeFollowList(name: FeedDefinition) {
        changeDefaultHomeFollowList(name.code)
    }

    fun changeDefaultHomeFollowList(name: String) {
        if (defaultHomeFollowList.value != name) {
            defaultHomeFollowList.tryEmit(name)
            saveAccountSettings()
        }
    }

    fun changeDefaultStoriesFollowList(name: FeedDefinition) {
        changeDefaultStoriesFollowList(name.code)
    }

    fun changeDefaultStoriesFollowList(name: String) {
        if (defaultStoriesFollowList.value != name) {
            defaultStoriesFollowList.tryEmit(name)
            saveAccountSettings()
        }
    }

    fun changeDefaultNotificationFollowList(name: FeedDefinition) {
        changeDefaultNotificationFollowList(name.code)
    }

    fun changeDefaultNotificationFollowList(name: String) {
        if (defaultNotificationFollowList.value != name) {
            defaultNotificationFollowList.tryEmit(name)
            saveAccountSettings()
        }
    }

    fun changeDefaultDiscoveryFollowList(name: FeedDefinition) {
        changeDefaultDiscoveryFollowList(name.code)
    }

    fun changeDefaultDiscoveryFollowList(name: String) {
        if (defaultDiscoveryFollowList.value != name) {
            defaultDiscoveryFollowList.tryEmit(name)
            saveAccountSettings()
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
        if (localRelayServers.value != servers) {
            localRelayServers.update { servers }
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

    fun updateNIPA3PaymentTargets(newNIPA3PaymentTargets: PaymentTargetsEvent?) {
        if (newNIPA3PaymentTargets == null || newNIPA3PaymentTargets.tags.isEmpty()) return

        // Events might be different objects, we have to compare their ids.
        if (backupNipA3PaymentTargets?.id != newNIPA3PaymentTargets.id) {
            backupNipA3PaymentTargets = newNIPA3PaymentTargets
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

    fun updateIndexRelayList(newIndexRelayList: IndexerRelayListEvent?) {
        if (newIndexRelayList == null || newIndexRelayList.tags.isEmpty()) return

        // Events might be different objects, we have to compare their ids.
        if (backupIndexRelayList?.id != newIndexRelayList.id) {
            backupIndexRelayList = newIndexRelayList
            saveAccountSettings()
        }
    }

    fun updateBlockedRelayList(newBlockedRelayList: BlockedRelayListEvent?) {
        if (newBlockedRelayList == null || newBlockedRelayList.tags.isEmpty()) return

        // Events might be different objects, we have to compare their ids.
        if (backupBlockedRelayList?.id != newBlockedRelayList.id) {
            backupBlockedRelayList = newBlockedRelayList
            saveAccountSettings()
        }
    }

    fun updateTrustedRelayList(newTrustedRelayList: TrustedRelayListEvent?) {
        if (newTrustedRelayList == null || newTrustedRelayList.tags.isEmpty()) return

        // Events might be different objects, we have to compare their ids.
        if (backupTrustedRelayList?.id != newTrustedRelayList.id) {
            backupTrustedRelayList = newTrustedRelayList
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

    override fun channelList() = backupChannelList

    override fun updateChannelListTo(newChannelList: ChannelListEvent?) {
        if (newChannelList == null || newChannelList.tags.isEmpty()) return

        // Events might be different objects, we have to compare their ids.
        if (backupChannelList?.id != newChannelList.id) {
            backupChannelList = newChannelList
            saveAccountSettings()
        }
    }

    fun updateGeohashListTo(newGeohashList: GeohashListEvent?) {
        if (newGeohashList == null || newGeohashList.tags.isEmpty()) return

        // Events might be different objects, we have to compare their ids.
        if (backupGeohashList?.id != newGeohashList.id) {
            backupGeohashList = newGeohashList
            saveAccountSettings()
        }
    }

    fun updateHashtagListTo(newHashtagList: HashtagListEvent?) {
        if (newHashtagList == null || newHashtagList.tags.isEmpty()) return

        // Events might be different objects, we have to compare their ids.
        if (backupHashtagList?.id != newHashtagList.id) {
            backupHashtagList = newHashtagList
            saveAccountSettings()
        }
    }

    fun updateCommunityListTo(newCommunityList: CommunityListEvent?) {
        if (newCommunityList == null || newCommunityList.tags.isEmpty()) return

        // Events might be different objects, we have to compare their ids.
        if (backupCommunityList?.id != newCommunityList.id) {
            backupCommunityList = newCommunityList
            saveAccountSettings()
        }
    }

    override fun ephemeralChatList() = backupEphemeralChatList

    override fun updateEphemeralChatListTo(newEphemeralChatList: EphemeralChatListEvent?) {
        if (newEphemeralChatList == null || newEphemeralChatList.tags.isEmpty()) return

        // Events might be different objects, we have to compare their ids.
        if (backupEphemeralChatList?.id != newEphemeralChatList.id) {
            backupEphemeralChatList = newEphemeralChatList
            saveAccountSettings()
        }
    }

    fun updateTrustProviderListTo(trustProviderList: TrustProviderListEvent?) {
        if (trustProviderList == null || trustProviderList.tags.isEmpty()) return

        // Events might be different objects, we have to compare their ids.
        if (backupTrustProviderList?.id != trustProviderList.id) {
            backupTrustProviderList = trustProviderList
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
