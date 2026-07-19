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
import com.vitorpamplona.amethyst.commons.audio.VisualizerStyle
import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.amethyst.commons.model.clink.ClinkDebitWalletEntryNorm
import com.vitorpamplona.amethyst.commons.model.concord.ConcordListRepository
import com.vitorpamplona.amethyst.commons.model.concord.ConcordViewMode
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatRepository
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatListRepository
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupRepository
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupViewMode
import com.vitorpamplona.amethyst.commons.model.nip47WalletConnect.NwcWalletEntryNorm
import com.vitorpamplona.amethyst.commons.model.payments.PaymentSource
import com.vitorpamplona.amethyst.commons.model.payments.PaymentSourceResolver
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPolicy
import com.vitorpamplona.amethyst.commons.service.pow.PoWCategory
import com.vitorpamplona.amethyst.model.nip60Cashu.CashuPreferences
import com.vitorpamplona.amethyst.ui.actions.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.screen.FeedDefinition
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEvent
import com.vitorpamplona.quartz.experimental.ephemChat.list.EphemeralChatListEvent
import com.vitorpamplona.quartz.experimental.nipA3.PaymentTargetsEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip19Bech32.toNpub
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
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.SimpleGroupListEvent
import com.vitorpamplona.quartz.nip55AndroidSigner.api.CommandType
import com.vitorpamplona.quartz.nip55AndroidSigner.api.permission.Permission
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
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
    interface AddressableTopFilter {
        val address: Address
    }

    @Serializable
    object Global : TopFilter(" Global ")

    /**
     * Notifications-only curated mode: like [Global] it admits authors the
     * user doesn't follow, but it also applies per-kind relevance heuristics
     * to remove less interesting notes (reactions/reposts that don't target
     * the user's own notes, unrelated thread replies, etc.). In Notifications,
     * [Global] shows every event that p-tags the user instead.
     */
    @Serializable
    object Selected : TopFilter(" Selected ")

    @Serializable
    object AllFollows : TopFilter(" All Follows ")

    @Serializable
    object AllUserFollows : TopFilter(" All User Follows ")

    @Serializable
    object DefaultFollows : TopFilter(" Main User Follows ")

    @Serializable
    object AroundMe : TopFilter(" Around Me ")

    /**
     * Not a real selection: a sentinel for the "Teleport" chip in the top-nav filter.
     * The spinner intercepts it to open the map picker and then applies the chosen
     * [Geohash] instead — it is never persisted or dispatched to a feed flow.
     */
    @Serializable
    object TeleportPicker : TopFilter(" Teleport ")

    @Serializable
    object Mine : TopFilter(" Mine ")

    @Serializable
    class PeopleList(
        override val address: Address,
    ) : TopFilter(address.toValue()),
        AddressableTopFilter

    @Serializable
    class MuteList(
        override val address: Address,
    ) : TopFilter(address.toValue()),
        AddressableTopFilter

    @Serializable
    class Community(
        override val address: Address,
    ) : TopFilter("Community/${address.toValue()}"),
        AddressableTopFilter

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
    val useLocalBlossomCache: MutableStateFlow<Boolean> = MutableStateFlow(true),
    val localBlossomCacheProfilePicturesOnly: MutableStateFlow<Boolean> = MutableStateFlow(false),
    /**
     * BUD-04: after uploading a blob to the primary Blossom server, replicate it to
     * the user's other configured servers (kind 10063) for redundancy.
     */
    val mirrorUploadsToAllServers: MutableStateFlow<Boolean> = MutableStateFlow(true),
    /**
     * BUD-05: upload media through the server's `/media` endpoint so the server may
     * strip metadata and optimize it, instead of the bit-exact `/upload`.
     */
    val optimizeMediaOnUpload: MutableStateFlow<Boolean> = MutableStateFlow(false),
    /**
     * NIP-46: when true, this account acts as a remote signer (a "bunker") for
     * other apps, listening on the user's inbox relays for kind:24133 requests.
     * See [com.vitorpamplona.amethyst.model.nip46Signer.Nip46SignerState].
     */
    val nip46SignerEnabled: MutableStateFlow<Boolean> = MutableStateFlow(false),
    /**
     * The active pairing secret advertised in this account's `bunker://` URI. An
     * app that connects with this secret is accepted and registered as a
     * connected app; regenerating it revokes the ability of not-yet-connected
     * apps to pair with an old string.
     */
    val nip46BunkerSecret: MutableStateFlow<String> = MutableStateFlow(""),
    /**
     * A dedicated per-account transport keypair (hex private key) for the NIP-46
     * bunker. The kind-24133 envelope is wrapped with THIS key, not the account's
     * identity key, so the bunker address and on-relay traffic don't reveal which
     * user the bunker belongs to (the identity is disclosed only to a connected
     * app via `get_public_key`). Generated once and kept stable so the advertised
     * `bunker://` address doesn't change.
     */
    val nip46TransportKey: MutableStateFlow<String> = MutableStateFlow(""),
    /**
     * The kind-24133 **event ids** this signer recently serviced. Persisted so that a relay replaying
     * stored ephemeral requests across an app restart doesn't make it sign the same request twice —
     * matched by exact event id, so it is immune to client clock skew (unlike a timestamp watermark,
     * a global timestamp would wrongly drop a second app whose clock lags). Bounded to a recent window.
     */
    val nip46SeenRequestIds: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet()),
    /**
     * NIP-9B opt-in: when true, community feeds drop events whose latest cached
     * `kind:34551` rules document fails [com.vitorpamplona.quartz.nip72ModCommunities.rules.CommunityRulesValidator].
     * Default false preserves pre-9A behaviour.
     */
    val hideCommunityRulesViolations: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val defaultHomeFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.AllFollows),
    val defaultStoriesFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultNotificationFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Selected),
    val defaultDiscoveryFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultPollsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultPicturesFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultNappletsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultNsitesFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultWorkoutsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultGitRepositoriesFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultCalendarsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultProductsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.AroundMe),
    val defaultShortsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultPublicChatsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultLiveStreamsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultNestsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultLongsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultArticlesFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.AllFollows),
    val defaultMusicTracksFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultMusicPlaylistsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultPodcastEpisodesFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultPodcastsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultSoftwareAppsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultBadgesFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Mine),
    val defaultBrowseEmojiSetsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultCommunitiesFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.AllFollows),
    val defaultFollowPacksFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultAppRecommendationsFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Global),
    val defaultRelayGroupsDiscoveryFollowList: MutableStateFlow<TopFilter> = MutableStateFlow(TopFilter.Mine),
    val nwcWallets: MutableStateFlow<List<NwcWalletEntryNorm>> = MutableStateFlow(emptyList()),
    val clinkDebitWallets: MutableStateFlow<List<ClinkDebitWalletEntryNorm>> = MutableStateFlow(emptyList()),
    // The unified default spend rail (an NWC wallet OR a CLINK debit). Persisted under a
    // new key, migrated from the legacy NWC-only `defaultNwcWalletId`.
    val defaultPaymentSourceId: MutableStateFlow<String?> = MutableStateFlow(null),
    var hideDeleteRequestDialog: Boolean = false,
    var hideBlockAlertDialog: Boolean = false,
    var hideNIP17WarningDialog: Boolean = false,
    val alwaysOnNotificationService: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val splitNotificationsEnabled: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val showMessagesInNotifications: MutableStateFlow<Boolean> = MutableStateFlow(true),
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
    var backupRelayGroupList: SimpleGroupListEvent? = null,
    var backupConcordList: ConcordCommunityListEvent? = null,
    var backupTrustProviderList: TrustProviderListEvent? = null,
    var backupCashuWallet: CashuWalletEvent? = null,
    var backupNutzapInfo: NutzapInfoEvent? = null,
    /**
     * NUT-13 deterministic-secret counter map, keyed by keyset id. The
     * wallet derives every blind message from `(seed, keysetId, counter)`,
     * incrementing the counter every time it consumes one; reusing a
     * counter would expose the secret. Persisted here so the counter
     * survives app restart even though the wallet's seed is also stored
     * in kind:17375 (which would otherwise be the only persistence).
     *
     * Empty map = no NUT-13 usage yet (e.g. wallet created before this
     * feature shipped). Per-keyset; the same counter under different
     * keysets is fine because the derivation includes the keyset id.
     */
    var cashuKeysetCounters: MutableMap<String, Long> = mutableMapOf(),
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
    val defaultRelayAuthPolicy: MutableStateFlow<RelayAuthPolicy> = MutableStateFlow(RelayAuthPolicy.CUSTOM),
    val relayGroupViewMode: MutableStateFlow<RelayGroupViewMode> = MutableStateFlow(RelayGroupViewMode.DEFAULT),
    val concordViewMode: MutableStateFlow<ConcordViewMode> = MutableStateFlow(ConcordViewMode.DEFAULT),
    // Which conversation protocols the Messages inbox loads and shows. A disabled type is both hidden
    // from the inbox and dropped from the always-on downloading routes. Defaults to everything on.
    val enabledChatFeeds: MutableStateFlow<Set<ChatFeedType>> = MutableStateFlow(ChatFeedType.ALL),
    // The per-situation toggles applied under RelayAuthPolicy.CUSTOM.
    val relayAuthTrustMyRelaysAndVenues: MutableStateFlow<Boolean> = MutableStateFlow(true),
    val relayAuthTrustReadFollows: MutableStateFlow<Boolean> = MutableStateFlow(true),
    val relayAuthTrustMessageFollows: MutableStateFlow<Boolean> = MutableStateFlow(true),
    val relayAuthTrustMessageStrangers: MutableStateFlow<Boolean> = MutableStateFlow(false),
) : EphemeralChatRepository,
    RelayGroupRepository,
    ConcordListRepository,
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

    fun updateRelayGroupViewMode(mode: RelayGroupViewMode) {
        if (relayGroupViewMode.value != mode) {
            relayGroupViewMode.tryEmit(mode)
            saveAccountSettings()
        }
    }

    fun updateConcordViewMode(mode: ConcordViewMode) {
        if (concordViewMode.value != mode) {
            concordViewMode.tryEmit(mode)
            saveAccountSettings()
        }
    }

    fun isChatFeedEnabled(type: ChatFeedType): Boolean = type in enabledChatFeeds.value

    fun setChatFeedEnabled(
        type: ChatFeedType,
        enabled: Boolean,
    ) {
        val current = enabledChatFeeds.value
        val next = if (enabled) current + type else current - type
        if (next != current) {
            enabledChatFeeds.tryEmit(next)
            saveAccountSettings()
        }
    }

    // ---
    // Always-on Notification Service
    // ---

    fun toggleAlwaysOnNotificationService(): Boolean {
        val newValue = !alwaysOnNotificationService.value
        alwaysOnNotificationService.tryEmit(newValue)
        saveAccountSettings()
        return newValue
    }

    fun toggleSplitNotificationsEnabled(): Boolean {
        val newValue = !splitNotificationsEnabled.value
        splitNotificationsEnabled.tryEmit(newValue)
        saveAccountSettings()
        return newValue
    }

    fun toggleShowMessagesInNotifications(): Boolean {
        val newValue = !showMessagesInNotifications.value
        showMessagesInNotifications.tryEmit(newValue)
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

    fun changeAudioVisualizer(style: VisualizerStyle): Boolean {
        if (syncedSettings.media.audioVisualizer.value != style) {
            syncedSettings.media.audioVisualizer.tryEmit(style)
            saveAccountSettings()
            return true
        }
        return false
    }

    /** The selected default spend rail across both NWC wallets and CLINK debits. */
    fun defaultPaymentSource(): PaymentSource? = PaymentSourceResolver.resolveDefault(nwcWallets.value, clinkDebitWallets.value, defaultPaymentSourceId.value)

    /**
     * The NWC wallet to use for NWC-only flows (balance display, mint top-up). Resolves
     * the unified default when it points at an NWC wallet, otherwise falls back to the
     * first NWC wallet so those flows keep working even when a debit is the zap default.
     */
    fun defaultNwcWallet(): NwcWalletEntryNorm? {
        val wallets = nwcWallets.value
        return wallets.firstOrNull { it.id == defaultPaymentSourceId.value } ?: wallets.firstOrNull()
    }

    fun defaultZapPaymentRequest(): Nip47WalletConnect.Nip47URINorm? = defaultNwcWallet()?.uri

    fun addNwcWallet(wallet: NwcWalletEntryNorm): Boolean {
        val existing = nwcWallets.value.indexOfFirst { it.id == wallet.id }
        if (existing >= 0) {
            nwcWallets.tryEmit(nwcWallets.value.toMutableList().apply { set(existing, wallet) })
        } else {
            nwcWallets.tryEmit(nwcWallets.value + wallet)
            // First configured source of any kind becomes the default; adding more never
            // silently changes an existing default.
            if (defaultPaymentSourceId.value == null) {
                defaultPaymentSourceId.tryEmit(wallet.id)
            }
        }
        saveAccountSettings()
        return true
    }

    fun removeNwcWallet(walletId: String): Boolean {
        val wallets = nwcWallets.value.filter { it.id != walletId }
        nwcWallets.tryEmit(wallets)
        reassignDefaultIfRemoved(walletId)
        saveAccountSettings()
        return true
    }

    fun addClinkDebitWallet(wallet: ClinkDebitWalletEntryNorm): Boolean {
        val existing = clinkDebitWallets.value.indexOfFirst { it.id == wallet.id }
        if (existing >= 0) {
            clinkDebitWallets.tryEmit(clinkDebitWallets.value.toMutableList().apply { set(existing, wallet) })
        } else {
            clinkDebitWallets.tryEmit(clinkDebitWallets.value + wallet)
            if (defaultPaymentSourceId.value == null) {
                defaultPaymentSourceId.tryEmit(wallet.id)
            }
        }
        saveAccountSettings()
        return true
    }

    fun removeClinkDebitWallet(walletId: String): Boolean {
        clinkDebitWallets.tryEmit(clinkDebitWallets.value.filter { it.id != walletId })
        reassignDefaultIfRemoved(walletId)
        saveAccountSettings()
        return true
    }

    fun renameClinkDebitWallet(
        walletId: String,
        newName: String,
    ): Boolean {
        val wallets = clinkDebitWallets.value.toMutableList()
        val index = wallets.indexOfFirst { it.id == walletId }
        if (index >= 0) {
            wallets[index] = wallets[index].copy(name = newName)
            clinkDebitWallets.tryEmit(wallets)
            saveAccountSettings()
            return true
        }
        return false
    }

    /** When the removed source was the default, fall back to the first remaining source. */
    private fun reassignDefaultIfRemoved(walletId: String) {
        if (defaultPaymentSourceId.value == walletId) {
            defaultPaymentSourceId.tryEmit(PaymentSourceResolver.resolveDefault(nwcWallets.value, clinkDebitWallets.value, null)?.id)
        }
    }

    /** Resets the default to the first remaining source if it no longer points at anything. */
    private fun reassignDefaultIfMissing() {
        val id = defaultPaymentSourceId.value ?: return
        val exists = nwcWallets.value.any { it.id == id } || clinkDebitWallets.value.any { it.id == id }
        if (!exists) {
            defaultPaymentSourceId.tryEmit(PaymentSourceResolver.resolveDefault(nwcWallets.value, clinkDebitWallets.value, null)?.id)
        }
    }

    /** Selects the unified default across both NWC wallets and CLINK debits. */
    fun setDefaultPaymentSource(sourceId: String): Boolean {
        val exists = nwcWallets.value.any { it.id == sourceId } || clinkDebitWallets.value.any { it.id == sourceId }
        if (defaultPaymentSourceId.value != sourceId && exists) {
            defaultPaymentSourceId.tryEmit(sourceId)
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

    fun changeZapPaymentRequest(newServer: Nip47WalletConnect.Nip47URINorm?): Boolean {
        if (newServer == null) {
            if (nwcWallets.value.isNotEmpty()) {
                nwcWallets.tryEmit(emptyList())
                reassignDefaultIfMissing()
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

    fun changeUseLocalBlossomCache(enabled: Boolean) {
        if (useLocalBlossomCache.value != enabled) {
            useLocalBlossomCache.tryEmit(enabled)
            saveAccountSettings()
        }
    }

    fun changeHideCommunityRulesViolations(enabled: Boolean) {
        if (hideCommunityRulesViolations.value != enabled) {
            hideCommunityRulesViolations.tryEmit(enabled)
            saveAccountSettings()
        }
    }

    fun changeNip46SignerEnabled(enabled: Boolean) {
        if (nip46SignerEnabled.value != enabled) {
            nip46SignerEnabled.tryEmit(enabled)
            saveAccountSettings()
        }
    }

    fun changeNip46BunkerSecret(secret: String) {
        if (nip46BunkerSecret.value != secret) {
            nip46BunkerSecret.tryEmit(secret)
            saveAccountSettings()
        }
    }

    fun changeNip46TransportKey(hexPrivKey: String) {
        if (nip46TransportKey.value != hexPrivKey) {
            nip46TransportKey.tryEmit(hexPrivKey)
            saveAccountSettings()
        }
    }

    /** Replaces the recent serviced-request id set (already bounded by the caller). */
    fun changeNip46SeenRequestIds(ids: Set<String>) {
        if (nip46SeenRequestIds.value != ids) {
            nip46SeenRequestIds.tryEmit(ids)
            saveAccountSettings()
        }
    }

    fun changeLocalBlossomCacheProfilePicturesOnly(enabled: Boolean) {
        if (localBlossomCacheProfilePicturesOnly.value != enabled) {
            localBlossomCacheProfilePicturesOnly.tryEmit(enabled)
            saveAccountSettings()
        }
    }

    fun changeMirrorUploadsToAllServers(enabled: Boolean) {
        if (mirrorUploadsToAllServers.value != enabled) {
            mirrorUploadsToAllServers.tryEmit(enabled)
            saveAccountSettings()
        }
    }

    fun changeOptimizeMediaOnUpload(enabled: Boolean) {
        if (optimizeMediaOnUpload.value != enabled) {
            optimizeMediaOnUpload.tryEmit(enabled)
            saveAccountSettings()
        }
    }

    fun updateAddClientTag(add: Boolean): Boolean =
        if (syncedSettings.security.updateAddClientTag(add)) {
            saveAccountSettings()
            true
        } else {
            false
        }

    fun updatePowDifficulty(difficulty: Int): Boolean =
        if (syncedSettings.proofOfWork.updateDifficulty(difficulty)) {
            saveAccountSettings()
            true
        } else {
            false
        }

    fun updatePowCategory(
        category: PoWCategory,
        enabled: Boolean,
    ): Boolean =
        if (syncedSettings.proofOfWork.updateCategory(category, enabled)) {
            saveAccountSettings()
            true
        } else {
            false
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

    fun changeDefaultRelayGroupsDiscoveryFollowList(name: FeedDefinition) {
        changeDefaultRelayGroupsDiscoveryFollowList(name.code)
    }

    fun changeDefaultRelayGroupsDiscoveryFollowList(name: TopFilter) {
        if (defaultRelayGroupsDiscoveryFollowList.value != name) {
            defaultRelayGroupsDiscoveryFollowList.tryEmit(name)
            saveAccountSettings()
        }
    }

    fun changeDefaultNappletsFollowList(name: FeedDefinition) {
        changeDefaultNappletsFollowList(name.code)
    }

    fun changeDefaultNappletsFollowList(name: TopFilter) {
        if (defaultNappletsFollowList.value != name) {
            defaultNappletsFollowList.tryEmit(name)
            saveAccountSettings()
        }
    }

    fun changeDefaultNsitesFollowList(name: FeedDefinition) {
        changeDefaultNsitesFollowList(name.code)
    }

    fun changeDefaultNsitesFollowList(name: TopFilter) {
        if (defaultNsitesFollowList.value != name) {
            defaultNsitesFollowList.tryEmit(name)
            saveAccountSettings()
        }
    }

    fun changeDefaultWorkoutsFollowList(name: FeedDefinition) {
        changeDefaultWorkoutsFollowList(name.code)
    }

    fun changeDefaultWorkoutsFollowList(name: TopFilter) {
        if (defaultWorkoutsFollowList.value != name) {
            defaultWorkoutsFollowList.tryEmit(name)
            saveAccountSettings()
        }
    }

    fun changeDefaultGitRepositoriesFollowList(name: FeedDefinition) {
        changeDefaultGitRepositoriesFollowList(name.code)
    }

    fun changeDefaultGitRepositoriesFollowList(name: TopFilter) {
        if (defaultGitRepositoriesFollowList.value != name) {
            defaultGitRepositoriesFollowList.tryEmit(name)
            saveAccountSettings()
        }
    }

    fun changeDefaultCalendarsFollowList(name: FeedDefinition) {
        changeDefaultCalendarsFollowList(name.code)
    }

    fun changeDefaultCalendarsFollowList(name: TopFilter) {
        if (defaultCalendarsFollowList.value != name) {
            defaultCalendarsFollowList.tryEmit(name)
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

    fun changeDefaultNestsFollowList(name: FeedDefinition) {
        changeDefaultNestsFollowList(name.code)
    }

    fun changeDefaultNestsFollowList(name: TopFilter) {
        if (defaultNestsFollowList.value != name) {
            defaultNestsFollowList.tryEmit(name)
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

    fun changeDefaultMusicTracksFollowList(name: FeedDefinition) {
        changeDefaultMusicTracksFollowList(name.code)
    }

    fun changeDefaultMusicTracksFollowList(name: TopFilter) {
        if (defaultMusicTracksFollowList.value != name) {
            defaultMusicTracksFollowList.tryEmit(name)
            saveAccountSettings()
        }
    }

    fun changeDefaultMusicPlaylistsFollowList(name: FeedDefinition) {
        changeDefaultMusicPlaylistsFollowList(name.code)
    }

    fun changeDefaultMusicPlaylistsFollowList(name: TopFilter) {
        if (defaultMusicPlaylistsFollowList.value != name) {
            defaultMusicPlaylistsFollowList.tryEmit(name)
            saveAccountSettings()
        }
    }

    fun changeDefaultPodcastEpisodesFollowList(name: FeedDefinition) {
        changeDefaultPodcastEpisodesFollowList(name.code)
    }

    fun changeDefaultPodcastEpisodesFollowList(name: TopFilter) {
        if (defaultPodcastEpisodesFollowList.value != name) {
            defaultPodcastEpisodesFollowList.tryEmit(name)
            saveAccountSettings()
        }
    }

    fun changeDefaultPodcastsFollowList(name: FeedDefinition) {
        changeDefaultPodcastsFollowList(name.code)
    }

    fun changeDefaultPodcastsFollowList(name: TopFilter) {
        if (defaultPodcastsFollowList.value != name) {
            defaultPodcastsFollowList.tryEmit(name)
            saveAccountSettings()
        }
    }

    fun changeDefaultSoftwareAppsFollowList(name: FeedDefinition) {
        changeDefaultSoftwareAppsFollowList(name.code)
    }

    fun changeDefaultSoftwareAppsFollowList(name: TopFilter) {
        if (defaultSoftwareAppsFollowList.value != name) {
            defaultSoftwareAppsFollowList.tryEmit(name)
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

    fun changeDefaultAppRecommendationsFollowList(name: FeedDefinition) {
        changeDefaultAppRecommendationsFollowList(name.code)
    }

    fun changeDefaultAppRecommendationsFollowList(name: TopFilter) {
        if (defaultAppRecommendationsFollowList.value != name) {
            defaultAppRecommendationsFollowList.tryEmit(name)
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

    fun updateCashuWallet(newWallet: CashuWalletEvent?) {
        if (newWallet == null) return
        // Replaceable: keep the latest by id (id changes on each re-sign).
        if (backupCashuWallet?.id != newWallet.id) {
            backupCashuWallet = newWallet
            saveAccountSettings()
        }
    }

    fun updateNutzapInfo(newNutzapInfo: NutzapInfoEvent?) {
        if (newNutzapInfo == null || newNutzapInfo.tags.isEmpty()) return
        // A mints-less kind:10019 is the "stop receiving nutzaps" tombstone
        // (an empty replacement carrying only an `alt` tag). Don't restore it
        // on next launch — backing it up would undo clearNutzapInfo() once the
        // empty event round-trips back through LocalCache.
        if (newNutzapInfo.mints().isEmpty()) {
            clearNutzapInfo()
            return
        }
        if (backupNutzapInfo?.id != newNutzapInfo.id) {
            backupNutzapInfo = newNutzapInfo
            saveAccountSettings()
        }
    }

    /**
     * Drop the cached kind:17375 so a relaunch doesn't restore a wallet the
     * user just deleted. Called when the wallet event is NIP-09 deleted —
     * without this the [backupCashuWallet] would be re-consumed into
     * LocalCache on next launch and resurrect the deleted wallet.
     */
    fun clearCashuWallet() {
        if (backupCashuWallet != null) {
            backupCashuWallet = null
            saveAccountSettings()
        }
    }

    /** Drop the cached kind:10019. Mirror of [clearCashuWallet] for the nutzap info. */
    fun clearNutzapInfo() {
        if (backupNutzapInfo != null) {
            backupNutzapInfo = null
            saveAccountSettings()
        }
    }

    /**
     * NUT-13 keyset counters live in [CashuPreferences], a dedicated
     * SharedPreferences file with synchronous (`commit = true`) writes.
     * AccountSettings goes through a 1-second debounce on its own save
     * path; the cashu counter cannot tolerate that window because the
     * mint persists signed (keyset, blind_message) pairs the moment it
     * sees them, so any local lag → "outputs already signed" on retry.
     * See [CashuPreferences] for the full rationale.
     */
    private val cashuPrefs: CashuPreferences by lazy {
        CashuPreferences.forAccount(keyPair.pubKey.toNpub())
    }

    /**
     * Reserve [count] consecutive NUT-13 counters for [keysetId],
     * returning the first one. Caller derives `(secret, r)` from
     * `(seed, keysetId, i)` for `i in [returned .. returned+count-1]`.
     * Persisted synchronously before returning — see [CashuPreferences].
     *
     * One-time migration: when this keyset has a non-zero value in the
     * legacy [cashuKeysetCounters] map (from a build that persisted
     * counters inside AccountSettings) and the dedicated store is
     * still at zero, the legacy value is copied over before we reserve
     * so an upgrade doesn't reset the counter.
     */
    fun reserveCashuCounters(
        keysetId: String,
        count: Int,
    ): Long {
        migrateLegacyCashuCounter(keysetId)
        return cashuPrefs.reserveCounters(keysetId, count)
    }

    /** Inspect the next counter for [keysetId] without consuming any. */
    fun peekCashuCounter(keysetId: String): Long {
        migrateLegacyCashuCounter(keysetId)
        return cashuPrefs.peekCounter(keysetId)
    }

    private fun migrateLegacyCashuCounter(keysetId: String) {
        val legacy = cashuKeysetCounters[keysetId] ?: return
        cashuPrefs.seedCounterIfMissing(keysetId, legacy)
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

    override fun relayGroupList() = backupRelayGroupList

    override fun updateRelayGroupListTo(newRelayGroupList: SimpleGroupListEvent?) {
        // Joined groups can live in the NIP-44 private items (encrypted content),
        // so an empty `tags` is NOT an empty list — guard only on null.
        if (newRelayGroupList == null) return

        // Events might be different objects, we have to compare their ids.
        if (backupRelayGroupList?.id != newRelayGroupList.id) {
            backupRelayGroupList = newRelayGroupList
            saveAccountSettings()
        }
    }

    override fun concordList() = backupConcordList

    override fun updateConcordListTo(newConcordList: ConcordCommunityListEvent?) {
        // The joined list lives entirely in NIP-44-encrypted content (secrets),
        // so an empty `tags` is NOT an empty list — guard only on null.
        if (newConcordList == null) return

        if (backupConcordList?.id != newConcordList.id) {
            backupConcordList = newConcordList
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
    // pinned chatrooms
    // ---

    fun toggleChatroomPin(room: ChatroomKey) {
        syncedSettings.chats.pinnedChatrooms.update {
            if (room in it) it - room else it + room
        }
        saveAccountSettings()
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

    fun updateReportWarningThreshold(threshold: Int): Boolean =
        if (syncedSettings.security.updateReportWarningThreshold(threshold)) {
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

    fun changeDefaultRelayAuthPolicy(policy: RelayAuthPolicy) {
        if (defaultRelayAuthPolicy.value != policy) {
            defaultRelayAuthPolicy.tryEmit(policy)
            saveAccountSettings()
        }
    }

    private fun changeToggle(
        flow: MutableStateFlow<Boolean>,
        enabled: Boolean,
    ) {
        if (flow.value != enabled) {
            flow.tryEmit(enabled)
            saveAccountSettings()
        }
    }

    fun changeRelayAuthTrustMyRelaysAndVenues(enabled: Boolean) = changeToggle(relayAuthTrustMyRelaysAndVenues, enabled)

    fun changeRelayAuthTrustReadFollows(enabled: Boolean) = changeToggle(relayAuthTrustReadFollows, enabled)

    fun changeRelayAuthTrustMessageFollows(enabled: Boolean) = changeToggle(relayAuthTrustMessageFollows, enabled)

    fun changeRelayAuthTrustMessageStrangers(enabled: Boolean) = changeToggle(relayAuthTrustMessageStrangers, enabled)
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
