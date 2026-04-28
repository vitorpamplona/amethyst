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
import com.vitorpamplona.amethyst.model.nip47WalletConnect.NwcWalletEntryNorm
import com.vitorpamplona.amethyst.ui.actions.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.screen.FeedDefinition
import com.vitorpamplona.quartz.experimental.ephemChat.list.EphemeralChatListEvent
import com.vitorpamplona.quartz.experimental.nipA3.PaymentTargetsEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip28PublicChat.list.ChannelListEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip37Drafts.privateOutbox.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.favoriteAlgoFeedsList.FavoriteAlgoFeedsListEvent
import com.vitorpamplona.quartz.nip51Lists.geohashList.GeohashListEvent
import com.vitorpamplona.quartz.nip51Lists.hashtagList.HashtagListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BlockedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.IndexerRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.RelayFeedsListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.TrustedRelayListEvent
import com.vitorpamplona.quartz.nip55AndroidSigner.api.CommandType
import com.vitorpamplona.quartz.nip55AndroidSigner.api.permission.Permission
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip72ModCommunities.follow.CommunityListEvent
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

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

@Serializable
sealed class TopFilter(
    val code: String,
) {
    @Serializable
    object Global : TopFilter(" Global ")

    @Serializable
    object AllFollows : TopFilter(" All Follows ")

    @Serializable
    object AllUserFollows : TopFilter(" All User Follows ")

    @Serializable
    object DefaultFollows : TopFilter(" Main User Follows ")

    @Serializable
    object AroundMe : TopFilter(" Around Me ")

    @Serializable
    object Mine : TopFilter(" Mine ")

    @Serializable
    class PeopleList(
        val address: Address,
    ) : TopFilter(address.toValue())

    @Serializable
    class MuteList(
        val address: Address,
    ) : TopFilter(address.toValue())

    @Serializable
    class Community(
        val address: Address,
    ) : TopFilter("Community/${address.toValue()}")

    @Serializable
    class Hashtag(
        val tag: String,
    ) : TopFilter("Hashtag/$tag")

    @Serializable
    class Geohash(
        val tag: String,
    ) : TopFilter("Geohash/$tag")

    @Serializable
    class Relay(
        val url: String,
    ) : TopFilter("Relay/$url")

    @Serializable
    class FavoriteAlgoFeed(
        val address: Address,
    ) : TopFilter("FavoriteAlgoFeed/${address.toValue()}")

    @Serializable object AllFavoriteAlgoFeeds : TopFilter(" All Favourite DVMs ")

    @Serializable
    class InterestSet(
        val address: Address,
    ) : TopFilter("InterestSet/${address.toValue()}")
}

@Stable
class AccountSettings(
    val keyPair: KeyPair,
    val transientAccount: Boolean = false,
    var externalSignerPackageName: String? = null,
    var localRelayServers: MutableStateFlow<Set<String>> = MutableStateFlow(setOf()),
    var defaultFileServer: ServerName = DEFAULT_MEDIA_SERVERS[0],
    var stripLocationOnUpload: Boolean = true,
    val defaultHomeFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.AllFollows),
    val defaultStoriesFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultNotificationFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultDiscoveryFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultPollsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultPicturesFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultProductsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.AroundMe),
    val defaultShortsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultPublicChatsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultLiveStreamsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultLongsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultArticlesFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.AllFollows),
    val defaultBadgesFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Mine),
    val defaultBrowseEmojiSetsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultCommunitiesFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.AllFollows),
    val defaultFollowPacksFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val nwcWallets: MutableStateFlow<List<NwcWalletEntryNorm>> = MutableStateFlow(emptyList()),
    val defaultNwcWalletId: MutableStateFlow<String?> = MutableStateFlow(null),
    var hideDeleteRequestDialog: Boolean = false,
    var hideBlockAlertDialog: Boolean = false,
    var hideNIP17WarningDialog: Boolean = false,
    val alwaysOnNotificationService: MutableStateFlow<Boolean> = MutableStateFlow(false),
    var backupUserMetadata: MetadataEvent? = null,
    var backupContactList: ContactListEvent? = null,
    var backupDMRelayList: ChatMessageRelayListEvent? = null,
    var backupKeyPackageRelayList: KeyPackageRelayListEvent? = null,
    var backupNIP65RelayList: AdvertisedRelayListEvent? = null,
    var backupSearchRelayList: SearchRelayListEvent? = null,
    var backupIndexRelayList: IndexerRelayListEvent? = null,
    var backupRelayFeedsList: RelayFeedsListEvent? = null,
    var backupBlockedRelayList: BlockedRelayListEvent? = null,
    var backupTrustedRelayList: TrustedRelayListEvent? = null,
    var backupMuteList: MuteListEvent? = null,
    var backupPrivateHomeRelayList: PrivateOutboxRelayListEvent? = null,
    var backupAppSpecificData: AppSpecificDataEvent? = null,
    var backupChannelList: ChannelListEvent? = null,
    var backupCommunityList: CommunityListEvent? = null,
    var backupHashtagList: HashtagListEvent? = null,
    var backupFavoriteAlgoFeedsList: FavoriteAlgoFeedsListEvent? = null,
    var backupGeohashList: GeohashListEvent? = null,
    var backupEphemeralChatList: EphemeralChatListEvent? = null,
    var backupTrustProviderList: TrustProviderListEvent? = null,
    val lastReadPerRoute: MutableStateFlow<Map<String, MutableStateFlow<Long>>> = MutableStateFlow(mapOf()),
    val hasDonatedInVersion: MutableStateFlow<Set<String>> = MutableStateFlow(setOf()),
    val dismissedPollNoteIds: MutableStateFlow<Set<String>> = MutableStateFlow(setOf()),
    val viewedPollResultNoteIds: MutableStateFlow<Map<String, Long>> = MutableStateFlow(mapOf()),
    val pendingAttestations: MutableStateFlow<Map<HexKey, String>> = MutableStateFlow(mapOf()),
    var backupNipA3PaymentTargets: PaymentTargetsEvent? = null,
    var callTurnServers: List<CallTurnServer> = emptyList(),
    var callVideoResolution: CallVideoResolution = CallVideoResolution.HD_720,
    var callMaxBitrateBps: Int = 1_500_000,
    val callsEnabled: MutableStateFlow<Boolean> = MutableStateFlow(true),
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
    // Always-on Notification Service
    // ---

    fun toggleAlwaysOnNotificationService(): Boolean {
        val newValue = !alwaysOnNotificationService.value
        alwaysOnNotificationService.tryEmit(newValue)
        saveAccountSettings()
        return newValue
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

    fun changeReactionRowItems(newItems: List<ReactionRowItem>): Boolean {
        if (syncedSettings.reactions.reactionRowItems.value != newItems) {
            syncedSettings.reactions.reactionRowItems.tryEmit(newItems.toImmutableList())
            saveAccountSettings()
            return true
        }
        return false
    }

    fun changeVideoPlayerButtonItems(newItems: List<VideoPlayerButtonItem>): Boolean {
        if (syncedSettings.videoPlayer.buttonItems.value != newItems) {
            syncedSettings.videoPlayer.buttonItems.tryEmit(newItems.toImmutableList())
            saveAccountSettings()
            return true
        }
        return false
    }

    fun defaultNwcWallet(): NwcWalletEntryNorm? {
        val id = defaultNwcWalletId.value
        val wallets = nwcWallets.value
        return if (id != null) {
            wallets.firstOrNull { it.id == id }
        } else {
            wallets.firstOrNull()
        }
    }

    fun defaultZapPaymentRequest(): Nip47WalletConnect.Nip47URINorm? = defaultNwcWallet()?.uri

    fun addNwcWallet(wallet: NwcWalletEntryNorm): Boolean {
        val existing = nwcWallets.value.indexOfFirst { it.id == wallet.id }
        if (existing >= 0) {
            nwcWallets.tryEmit(nwcWallets.value.toMutableList().apply { set(existing, wallet) })
        } else {
            nwcWallets.tryEmit(nwcWallets.value + wallet)
            if (nwcWallets.value.size == 1) {
                defaultNwcWalletId.tryEmit(wallet.id)
            }
        }
        saveAccountSettings()
        return true
    }

    fun removeNwcWallet(walletId: String): Boolean {
        val wallets = nwcWallets.value.filter { it.id != walletId }
        nwcWallets.tryEmit(wallets)
        if (defaultNwcWalletId.value == walletId) {
            defaultNwcWalletId.tryEmit(wallets.firstOrNull()?.id)
        }
        saveAccountSettings()
        return true
    }

    fun setDefaultNwcWallet(walletId: String): Boolean {
        if (defaultNwcWalletId.value != walletId && nwcWallets.value.any { it.id == walletId }) {
            defaultNwcWalletId.tryEmit(walletId)
            saveAccountSettings()
            return true
        }
        return false
    }

    fun renameNwcWallet(
        walletId: String,
        newName: String,
    ): Boolean {
        val wallets = nwcWallets.value.toMutableList()
        val index = wallets.indexOfFirst { it.id == walletId }
        if (index >= 0) {
            wallets[index] = wallets[index].copy(name = newName)
            nwcWallets.tryEmit(wallets)
            saveAccountSettings()
            return true
        }
        return false
    }

    fun moveNwcWallet(
        fromIndex: Int,
        toIndex: Int,
    ): Boolean {
        val wallets = nwcWallets.value.toMutableList()
        if (fromIndex in wallets.indices && toIndex in wallets.indices && fromIndex != toIndex) {
            val item = wallets.removeAt(fromIndex)
            wallets.add(toIndex, item)
            nwcWallets.tryEmit(wallets)
            saveAccountSettings()
            return true
        }
        return false
    }

    fun changeZapPaymentRequest(newServer: Nip47WalletConnect.Nip47URINorm?): Boolean {
        if (newServer == null) {
            if (nwcWallets.value.isNotEmpty()) {
                nwcWallets.tryEmit(emptyList())
                defaultNwcWalletId.tryEmit(null)
                saveAccountSettings()
                return true
            }
            return false
        }
        val current = defaultZapPaymentRequest()
        if (current != newServer) {
            val defaultWallet = defaultNwcWallet()
            if (defaultWallet != null) {
                val updated = defaultWallet.copy(uri = newServer)
                addNwcWallet(updated)
            } else {
                val entry =
                    NwcWalletEntryNorm(
                        id =
                            java.util.UUID
                                .randomUUID()
                                .toString(),
                        name = "Wallet",
                        uri = newServer,
                    )
                addNwcWallet(entry)
            }
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

    fun changeStripLocationOnUpload(strip: Boolean) {
        if (stripLocationOnUpload != strip) {
            stripLocationOnUpload = strip
            saveAccountSettings()
        }
    }

    // ---
    // list names
    // ---

    fun changeDefaultHomeFollowList(name: FeedDefinition) {
        changeDefaultHomeFollowList(name.code)
    }

    fun changeDefaultHomeFollowList(name: TopFilter) {
        if (defaultHomeFollowList.value != name) {
            defaultHomeFollowList.tryEmit(name)
            saveAccountSettings()
        }
    }

    fun changeDefaultStoriesFollowList(name: FeedDefinition) {
        changeDefaultStoriesFollowList(name.code)
    }

    fun changeDefaultStoriesFollowList(name: TopFilter) {
        if (defaultStoriesFollowList.value != name) {
            defaultStoriesFollowList.tryEmit(name)
            saveAccountSettings()
        }
    }

    fun changeDefaultNotificationFollowList(name: FeedDefinition) {
        changeDefaultNotificationFollowList(name.code)
    }

    fun changeDefaultNotificationFollowList(name: TopFilter) {
        if (defaultNotificationFollowList.value != name) {
            defaultNotificationFollowList.tryEmit(name)
            saveAccountSettings()
        }
    }

    fun changeDefaultDiscoveryFollowList(name: FeedDefinition) {
        changeDefaultDiscoveryFollowList(name.code)
    }

    fun changeDefaultDiscoveryFollowList(name: TopFilter) {
        if (defaultDiscoveryFollowList.value != name) {
            defaultDiscoveryFollowList.tryEmit(name)
            saveAccountSettings()
        }
    }

    fun changeDefaultPollsFollowList(name: FeedDefinition) {
        changeDefaultPollsFollowList(name.code)
    }

    fun changeDefaultPollsFollowList(name: TopFilter) {
        if (defaultPollsFollowList.value != name) {
            defaultPollsFollowList.tryEmit(name)
            saveAccountSettings()
        }
    }

    fun changeDefaultCommunitiesFollowList(name: FeedDefinition) {
        changeDefaultCommunitiesFollowList(name.code)
    }

    fun changeDefaultCommunitiesFollowList(name: TopFilter) {
        if (defaultCommunitiesFollowList.value != name) {
            defaultCommunitiesFollowList.tryEmit(name)
            saveAccountSettings()
        }
    }

    fun changeDefaultPicturesFollowList(name: FeedDefinition) {
        changeDefaultPicturesFollowList(name.code)
    }

    fun changeDefaultPicturesFollowList(name: TopFilter) {
        if (defaultPicturesFollowList.value != name) {
            defaultPicturesFollowList.tryEmit(name)
            saveAccountSettings()
        }
    }

    fun changeDefaultProductsFollowList(name: FeedDefinition) {
        changeDefaultProductsFollowList(name.code)
    }

    fun changeDefaultProductsFollowList(name: TopFilter) {
        if (defaultProductsFollowList.value != name) {
            defaultProductsFollowList.tryEmit(name)
            saveAccountSettings()
        }
    }

    fun changeDefaultShortsFollowList(name: FeedDefinition) {
        changeDefaultShortsFollowList(name.code)
    }

    fun changeDefaultShortsFollowList(name: TopFilter) {
        if (defaultShortsFollowList.value != name) {
            defaultShortsFollowList.tryEmit(name)
            saveAccountSettings()
        }
    }

    fun changeDefaultPublicChatsFollowList(name: FeedDefinition) {
        changeDefaultPublicChatsFollowList(name.code)
    }

    fun changeDefaultPublicChatsFollowList(name: TopFilter) {
        if (defaultPublicChatsFollowList.value != name) {
            defaultPublicChatsFollowList.tryEmit(name)
            saveAccountSettings()
        }
    }

    fun changeDefaultLiveStreamsFollowList(name: FeedDefinition) {
        changeDefaultLiveStreamsFollowList(name.code)
    }

    fun changeDefaultLiveStreamsFollowList(name: TopFilter) {
        if (defaultLiveStreamsFollowList.value != name) {
            defaultLiveStreamsFollowList.tryEmit(name)
            saveAccountSettings()
        }
    }

    fun changeDefaultLongsFollowList(name: FeedDefinition) {
        changeDefaultLongsFollowList(name.code)
    }

    fun changeDefaultLongsFollowList(name: TopFilter) {
        if (defaultLongsFollowList.value != name) {
            defaultLongsFollowList.tryEmit(name)
            saveAccountSettings()
        }
    }

    fun changeDefaultArticlesFollowList(name: FeedDefinition) {
        changeDefaultArticlesFollowList(name.code)
    }

    fun changeDefaultArticlesFollowList(name: TopFilter) {
        if (defaultArticlesFollowList.value != name) {
            defaultArticlesFollowList.tryEmit(name)
            saveAccountSettings()
        }
    }

    fun changeDefaultBadgesFollowList(name: FeedDefinition) {
        changeDefaultBadgesFollowList(name.code)
    }

    fun changeDefaultBadgesFollowList(name: TopFilter) {
        if (defaultBadgesFollowList.value != name) {
            defaultBadgesFollowList.tryEmit(name)
            saveAccountSettings()
        }
    }

    fun changeDefaultBrowseEmojiSetsFollowList(name: FeedDefinition) {
        changeDefaultBrowseEmojiSetsFollowList(name.code)
    }

    fun changeDefaultBrowseEmojiSetsFollowList(name: TopFilter) {
        if (defaultBrowseEmojiSetsFollowList.value != name) {
            defaultBrowseEmojiSetsFollowList.tryEmit(name)
            saveAccountSettings()
        }
    }

    fun changeDefaultFollowPacksFollowList(name: FeedDefinition) {
        changeDefaultFollowPacksFollowList(name.code)
    }

    fun changeDefaultFollowPacksFollowList(name: TopFilter) {
        if (defaultFollowPacksFollowList.value != name) {
            defaultFollowPacksFollowList.tryEmit(name)
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

    fun addDontTranslateFrom(languageCode: String) {
        syncedSettings.languages.addDontTranslateFrom(languageCode)
        saveAccountSettings()
    }

    fun removeDontTranslateFrom(languageCode: String) {
        syncedSettings.languages.removeDontTranslateFrom(languageCode)
        saveAccountSettings()
    }

    fun translateToContains(languageCode: String) =
        syncedSettings.languages.translateTo.value
            .contains(languageCode)

    fun updateTranslateTo(languageCode: String): Boolean {
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

    fun changeSendKind0EventsToLocalRelay(send: Boolean): Boolean {
        if (syncedSettings.security.updateSendKind0EventsToLocalRelay(send)) {
            saveAccountSettings()
            return true
        }
        return false
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

    fun updateKeyPackageRelayList(newKeyPackageRelayList: KeyPackageRelayListEvent?) {
        if (newKeyPackageRelayList == null || newKeyPackageRelayList.tags.isEmpty()) return

        // Events might be different objects, we have to compare their ids.
        if (backupKeyPackageRelayList?.id != newKeyPackageRelayList.id) {
            backupKeyPackageRelayList = newKeyPackageRelayList
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

    fun updateRelayFeedList(newRelayFeedList: RelayFeedsListEvent?) {
        if (newRelayFeedList == null || newRelayFeedList.tags.isEmpty()) return

        // Events might be different objects, we have to compare their ids.
        if (backupRelayFeedsList?.id != newRelayFeedList.id) {
            backupRelayFeedsList = newRelayFeedList
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

    fun updateFavoriteAlgoFeedsListTo(newFavoriteDvmList: FavoriteAlgoFeedsListEvent?) {
        if (newFavoriteDvmList == null || newFavoriteDvmList.tags.isEmpty()) return

        // Events might be different objects, we have to compare their ids.
        if (backupFavoriteAlgoFeedsList?.id != newFavoriteDvmList.id) {
            backupFavoriteAlgoFeedsList = newFavoriteDvmList
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

    // ---
    // dismissed polls
    // ---

    fun isDismissedPoll(noteId: String) = dismissedPollNoteIds.value.contains(noteId)

    fun dismissPollNotification(noteId: String) {
        if (!dismissedPollNoteIds.value.contains(noteId)) {
            dismissedPollNoteIds.update {
                it + noteId
            }
            saveAccountSettings()
        }
    }

    // ---
    // viewed poll results
    // ---

    fun hasViewedPollResults(noteId: String): Boolean {
        val expiresAt = viewedPollResultNoteIds.value[noteId] ?: return false
        return expiresAt > TimeUtils.now()
    }

    fun markPollResultsViewed(
        noteId: String,
        pollEndsAt: Long?,
    ) {
        if (noteId !in viewedPollResultNoteIds.value) {
            val expiresAt =
                if (pollEndsAt != null && pollEndsAt > TimeUtils.now()) {
                    pollEndsAt
                } else {
                    TimeUtils.now() + TimeUtils.ONE_DAY
                }
            viewedPollResultNoteIds.update {
                pruneExpiredViews(it) + (noteId to expiresAt)
            }
            saveAccountSettings()
        }
    }

    private fun pruneExpiredViews(views: Map<String, Long>): Map<String, Long> {
        val now = TimeUtils.now()
        return views.filterValues { it > now }
    }

    // ----
    // last read flows
    // ----

    fun getLastReadFlow(route: String): StateFlow<Long> = lastReadPerRoute.value[route] ?: addLastRead(route, 0)

    private fun addLastRead(
        route: String,
        timestampInSecs: Long,
    ): MutableStateFlow<Long> =
        MutableStateFlow(timestampInSecs).also { newFlow ->
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

    fun updateMaxHashtagLimit(limit: Int): Boolean =
        if (syncedSettings.security.updateMaxHashtagLimit(limit)) {
            saveAccountSettings()
            true
        } else {
            false
        }

    // ---
    // Call settings
    // ---

    fun changeCallTurnServers(servers: List<CallTurnServer>) {
        callTurnServers = servers
        saveAccountSettings()
    }

    fun changeCallVideoResolution(resolution: CallVideoResolution) {
        callVideoResolution = resolution
        saveAccountSettings()
    }

    fun changeCallMaxBitrateBps(bitrate: Int) {
        callMaxBitrateBps = bitrate
        saveAccountSettings()
    }

    fun changeCallsEnabled(enabled: Boolean) {
        if (callsEnabled.value != enabled) {
            callsEnabled.tryEmit(enabled)
            saveAccountSettings()
        }
    }
}

@Serializable
data class CallTurnServer(
    val url: String,
    val username: String,
    val credential: String,
)

@Serializable
enum class CallVideoResolution(
    val width: Int,
    val height: Int,
    val fps: Int,
    val label: String,
) {
    SD_480(640, 480, 30, "480p"),
    HD_720(1280, 720, 30, "720p (default)"),
    FHD_1080(1920, 1080, 30, "1080p"),
}
