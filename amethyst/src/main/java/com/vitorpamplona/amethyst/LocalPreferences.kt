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
import com.vitorpamplona.amethyst.commons.model.HomeFeedType
import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.amethyst.commons.model.clink.ClinkDebitWalletEntry
import com.vitorpamplona.amethyst.commons.model.concord.ConcordViewMode
import com.vitorpamplona.amethyst.commons.model.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupViewMode
import com.vitorpamplona.amethyst.commons.model.nip47WalletConnect.NwcWalletEntry
import com.vitorpamplona.amethyst.commons.model.nip47WalletConnect.NwcWalletEntryNorm
import com.vitorpamplona.amethyst.commons.model.preferences.AccountPreferenceStores
import com.vitorpamplona.amethyst.commons.model.preferences.CopyOnceMigration
import com.vitorpamplona.amethyst.commons.model.preferences.DialogDismissal
import com.vitorpamplona.amethyst.commons.model.preferences.DialogDismissalStore
import com.vitorpamplona.amethyst.commons.model.preferences.FeedVisibility
import com.vitorpamplona.amethyst.commons.model.preferences.FeedVisibilityStore
import com.vitorpamplona.amethyst.commons.model.preferences.FollowListSlot
import com.vitorpamplona.amethyst.commons.model.preferences.LatestEventCacheStore
import com.vitorpamplona.amethyst.commons.model.preferences.LatestEventSlot
import com.vitorpamplona.amethyst.commons.model.preferences.NotificationPrefs
import com.vitorpamplona.amethyst.commons.model.preferences.NotificationPrefsStore
import com.vitorpamplona.amethyst.commons.model.preferences.RelayAuth
import com.vitorpamplona.amethyst.commons.model.preferences.RelayAuthStore
import com.vitorpamplona.amethyst.commons.model.preferences.TopNavFollowListStore
import com.vitorpamplona.amethyst.commons.model.preferences.UploadSettings
import com.vitorpamplona.amethyst.commons.model.preferences.UploadSettingsStore
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPolicy
import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.UiSettings
import com.vitorpamplona.amethyst.model.backups.BackupConflictStorage
import com.vitorpamplona.amethyst.model.nip60Cashu.CashuPreferences
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEvent
import com.vitorpamplona.quartz.experimental.ephemChat.list.EphemeralChatListEvent
import com.vitorpamplona.quartz.experimental.nipA3.PaymentTargetsEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
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
import com.vitorpamplona.quartz.nip51Lists.favoriteAlgoFeedsList.FavoriteAlgoFeedsListEvent
import com.vitorpamplona.quartz.nip51Lists.geohashList.GeohashListEvent
import com.vitorpamplona.quartz.nip51Lists.hashtagList.HashtagListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BlockedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.IndexerRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.RelayFeedsListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.TrustedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.SimpleGroupListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip72ModCommunities.follow.CommunityListEvent
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.offer.Bolt12OfferListEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okio.Path.Companion.toOkioPath
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

    // Global (non-account) master switch for the always-on notification service.
    // When off, the service is suppressed for every account regardless of each
    // account's own participation flag. Persisted so it survives restarts/crashes.
    const val NOTIFICATION_SERVICE_ENABLED = "notification_service_enabled"
    const val SAVED_ACCOUNTS = "all_saved_accounts"
    const val NOSTR_PRIVKEY = "nostr_privkey"
    const val NOSTR_PUBKEY = "nostr_pubkey"
    const val LOCAL_RELAY_SERVERS = "localRelayServers"
    const val DEFAULT_FILE_SERVER = "defaultFileServer"
    const val STRIP_LOCATION_ON_UPLOAD = "stripLocationOnUpload"
    const val USE_LOCAL_BLOSSOM_CACHE = "useLocalBlossomCache"
    const val LOCAL_BLOSSOM_CACHE_PROFILE_PICTURES_ONLY = "localBlossomCacheProfilePicturesOnly"
    const val MIRROR_UPLOADS_TO_ALL_SERVERS = "mirrorUploadsToAllServers"
    const val OPTIMIZE_MEDIA_ON_UPLOAD = "optimizeMediaOnUpload"
    const val HIDE_COMMUNITY_RULES_VIOLATIONS = "hideCommunityRulesViolations"
    const val NIP46_SIGNER_ENABLED = "nip46SignerEnabled"
    const val NIP46_BUNKER_SECRET = "nip46BunkerSecret"
    const val NIP46_TRANSPORT_KEY = "nip46TransportKey"
    const val NIP46_SEEN_IDS = "nip46SeenRequestIds"
    const val DEFAULT_HOME_FOLLOW_LIST = "defaultHomeFollowList"
    const val DEFAULT_STORIES_FOLLOW_LIST = "defaultStoriesFollowList"
    const val DEFAULT_NOTIFICATION_FOLLOW_LIST = "defaultNotificationFollowList"
    const val DEFAULT_DISCOVERY_FOLLOW_LIST = "defaultDiscoveryFollowList"
    const val DEFAULT_POLLS_FOLLOW_LIST = "defaultPollsFollowList"
    const val DEFAULT_PICTURES_FOLLOW_LIST = "defaultPicturesFollowList"
    const val DEFAULT_RELAY_GROUPS_DISCOVERY_FOLLOW_LIST = "defaultRelayGroupsDiscoveryFollowList"
    const val DEFAULT_NAPPLETS_FOLLOW_LIST = "defaultNappletsFollowList"
    const val DEFAULT_NSITES_FOLLOW_LIST = "defaultNsitesFollowList"
    const val DEFAULT_WORKOUTS_FOLLOW_LIST = "defaultWorkoutsFollowList"
    const val DEFAULT_GIT_REPOSITORIES_FOLLOW_LIST = "defaultGitRepositoriesFollowList"
    const val DEFAULT_HIGHLIGHTS_FOLLOW_LIST = "defaultHighlightsFollowList"
    const val DEFAULT_CALENDARS_FOLLOW_LIST = "defaultCalendarsFollowList"
    const val DEFAULT_PRODUCTS_FOLLOW_LIST = "defaultProductsFollowList"
    const val DEFAULT_GEOCACHES_FOLLOW_LIST = "defaultGeocachesFollowList"
    const val DEFAULT_SHORTS_FOLLOW_LIST = "defaultShortsFollowList"
    const val DEFAULT_PUBLIC_CHATS_FOLLOW_LIST = "defaultPublicChatsFollowList"
    const val DEFAULT_LIVE_STREAMS_FOLLOW_LIST = "defaultLiveStreamsFollowList"
    const val DEFAULT_NESTS_FOLLOW_LIST = "defaultNestsFollowList"
    const val DEFAULT_LONGS_FOLLOW_LIST = "defaultLongsFollowList"
    const val DEFAULT_ARTICLES_FOLLOW_LIST = "defaultArticlesFollowList"
    const val DEFAULT_MUSIC_TRACKS_FOLLOW_LIST = "defaultMusicTracksFollowList"
    const val DEFAULT_MUSIC_PLAYLISTS_FOLLOW_LIST = "defaultMusicPlaylistsFollowList"
    const val DEFAULT_PODCAST_EPISODES_FOLLOW_LIST = "defaultPodcastEpisodesFollowList"
    const val DEFAULT_PODCASTS_FOLLOW_LIST = "defaultPodcastsFollowList"
    const val DEFAULT_SOFTWARE_APPS_FOLLOW_LIST = "defaultSoftwareAppsFollowList"
    const val DEFAULT_BADGES_FOLLOW_LIST = "defaultBadgesFollowList"
    const val DEFAULT_BROWSE_EMOJI_SETS_FOLLOW_LIST = "defaultBrowseEmojiSetsFollowList"
    const val DEFAULT_COMMUNITIES_FOLLOW_LIST = "defaultCommunitiesFollowList"
    const val DEFAULT_FOLLOW_PACKS_FOLLOW_LIST = "defaultFollowPacksFollowList"
    const val DEFAULT_APP_RECOMMENDATIONS_FOLLOW_LIST = "defaultAppRecommendationsFollowList"
    const val ZAP_PAYMENT_REQUEST_SERVER = "zapPaymentServer" // legacy, kept for migration
    const val NWC_WALLETS = "nwcWallets"
    const val DEFAULT_NWC_WALLET_ID = "defaultNwcWalletId" // legacy, migrated into DEFAULT_PAYMENT_SOURCE_ID
    const val CLINK_DEBIT_WALLETS = "clinkDebitWallets"
    const val DEFAULT_PAYMENT_SOURCE_ID = "defaultPaymentSourceId"
    const val OPEN_BACKUP_CONFLICTS = "openBackupConflicts"
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
    const val LATEST_RELAY_GROUP_LIST = "latestRelayGroupList"
    const val LATEST_CONCORD_LIST = "latestConcordList"
    const val LATEST_TRUST_PROVIDER_LIST = "latestTrustProviderList"
    const val LATEST_KEY_PACKAGE_RELAY_LIST = "latestKeyPackageRelayList"
    const val LATEST_FAVORITE_ALGO_FEEDS_LIST = "latestFavoriteAlgoFeedsList"
    const val CALLS_ENABLED = "calls_enabled"
    const val HIDE_DELETE_REQUEST_DIALOG = "hide_delete_request_dialog"
    const val HIDE_BLOCK_ALERT_DIALOG = "hide_block_alert_dialog"
    const val HIDE_NIP_17_WARNING_DIALOG = "hide_nip24_warning_dialog" // delete later
    const val ALWAYS_ON_NOTIFICATION_SERVICE = "always_on_notification_service"
    const val DEFAULT_RELAY_AUTH_POLICY = "default_relay_auth_policy"
    const val RELAY_GROUP_VIEW_MODE = "relay_group_view_mode"
    const val CONCORD_VIEW_MODE = "concord_view_mode"

    // Stores the DISABLED chat feed types (comma-joined codes) so absence = all-on and any newly
    // added type defaults enabled for accounts that customized before it existed.
    const val DISABLED_CHAT_FEEDS = "disabled_chat_feeds"

    // Same convention as DISABLED_CHAT_FEEDS but for the Home feed's event-kind groups: stores the
    // DISABLED codes so absence = all-on and any newly added group defaults enabled.
    const val DISABLED_HOME_FEED_TYPES = "disabled_home_feed_types"
    const val RELAY_AUTH_TRUST_MY_RELAYS = "relay_auth_trust_my_relays_and_venues"
    const val RELAY_AUTH_TRUST_READ_FOLLOWS = "relay_auth_trust_read_follows"
    const val RELAY_AUTH_TRUST_MESSAGE_FOLLOWS = "relay_auth_trust_message_follows"
    const val RELAY_AUTH_TRUST_MESSAGE_STRANGERS = "relay_auth_trust_message_strangers"
    const val SPLIT_NOTIFICATIONS_ENABLED = "split_notifications_enabled"
    const val SHOW_MESSAGES_IN_NOTIFICATIONS = "show_messages_in_notifications"

    // One-shot stamp: set once an account has gone through the notifications
    // Global -> Selected (Curated) migration (or was created after it shipped).
    const val NOTIF_GLOBAL_TO_CURATED_MIGRATED = "notif_global_to_curated_migrated"
    const val TOR_SETTINGS = "tor_settings"
    const val USE_PROXY = "use_proxy"
    const val PROXY_PORT = "proxy_port"
    const val LAST_READ_PER_ROUTE = "last_read_route_per_route"
    const val LOGIN_WITH_EXTERNAL_SIGNER = "login_with_external_signer"
    const val SIGNER_PACKAGE_NAME = "signer_package_name"
    const val HAS_DONATED_IN_VERSION = "has_donated_in_version"
    const val DISMISSED_POLL_NOTE_IDS = "dismissed_poll_note_ids"
    const val DISMISSED_CHANNEL_INVITES = "dismissed_channel_invites"
    const val MUTED_PUBLIC_CHATS = "muted_public_chats"
    const val VIEWED_POLL_RESULT_NOTE_IDS = "viewed_poll_result_note_ids"
    const val PENDING_ATTESTATIONS = "pending_attestations"

    // Per-account one-shot flag: false only for freshly-GENERATED accounts that
    // haven't yet backed up their secret key. Absent (defaults to true) for every
    // account logged in via an existing nsec/bunker/external signer — those already
    // hold their key elsewhere and must not be nudged.
    const val HAS_BACKED_UP_KEYS = "has_backed_up_keys"

    const val ALL_ACCOUNT_INFO = "all_saved_accounts_info"
    const val SHARED_SETTINGS = "shared_settings"
    const val LATEST_PAYMENT_TARGETS = "latestPaymentTargets"
    const val LATEST_BOLT12_OFFERS = "latestBolt12Offers"
    const val LATEST_CASHU_WALLET = "latestCashuWallet"
    const val LATEST_NUTZAP_INFO = "latestNutzapInfo"
}

object LocalPreferences {
    private const val COMMA = ","

    private var currentAccount: String? = null
    private val savedAccounts: MutableStateFlow<List<AccountInfo>?> = MutableStateFlow(null)

    // Guards the one-time lazy population of [savedAccounts]. Without it, concurrent callers
    // of savedAccounts() (e.g. the account-load path, the always-on notification service, and
    // the orphan-dir sweep, all launched at startup) would each see a null value, run the IO
    // read in parallel, and the migration branch could double-write ALL_ACCOUNT_INFO.
    private val savedAccountsMutex = Mutex()
    private val cachedAccounts: MutableMap<String, AccountSettings?> = mutableMapOf()

    /**
     * The per-account DataStore, and the top-nav filter selections inside it.
     *
     * Each account's store carries a [CopyOnceMigration] that lifts the filters
     * out of that account's legacy encrypted SharedPreferences the first time
     * the store is read. The copy leaves the legacy keys in place, so a build
     * that reads the old location still works — see [CopyOnceMigration].
     */
    private val accountStores: AccountPreferenceStores by lazy {
        AccountPreferenceStores(
            rootFilesDir = {
                Amethyst.instance.appContext.filesDir
                    .toOkioPath()
            },
            migrations = { npub ->
                listOf(followListMigration(npub), latestEventMigration(npub)) +
                    listOf(uploadSettingsMigration(npub), dialogDismissalMigration(npub), relayAuthMigration(npub), feedVisibilityMigration(npub), notificationPrefsMigration(npub))
            },
        )
    }

    private fun followListStore(npub: String) = TopNavFollowListStore(accountStores.getDataStore(npub))

    private fun latestEventStore(npub: String) = LatestEventCacheStore(accountStores.getDataStore(npub))

    private fun uploadSettingsStore(npub: String) = UploadSettingsStore(accountStores.getDataStore(npub))

    private fun dialogDismissalStore(npub: String) = DialogDismissalStore(accountStores.getDataStore(npub))

    private fun relayAuthStore(npub: String) = RelayAuthStore(accountStores.getDataStore(npub))

    private fun feedVisibilityStore(npub: String) = FeedVisibilityStore(accountStores.getDataStore(npub))

    private fun notificationPrefsStore(npub: String) = NotificationPrefsStore(accountStores.getDataStore(npub))

    private fun uploadSettingsMigration(npub: String) =
        CopyOnceMigration("migrated.uploadSettings") { out ->
            withContext(Dispatchers.IO) {
                val legacy = encryptedPreferences(npub)
                if (legacy.contains(PrefKeys.STRIP_LOCATION_ON_UPLOAD)) out[UploadSettingsStore.stripLocationOnUpload] = legacy.getBoolean(PrefKeys.STRIP_LOCATION_ON_UPLOAD, false)
                if (legacy.contains(PrefKeys.OPTIMIZE_MEDIA_ON_UPLOAD)) out[UploadSettingsStore.optimizeMediaOnUpload] = legacy.getBoolean(PrefKeys.OPTIMIZE_MEDIA_ON_UPLOAD, false)
                if (legacy.contains(PrefKeys.MIRROR_UPLOADS_TO_ALL_SERVERS)) out[UploadSettingsStore.mirrorUploadsToAllServers] = legacy.getBoolean(PrefKeys.MIRROR_UPLOADS_TO_ALL_SERVERS, false)
                if (legacy.contains(PrefKeys.USE_LOCAL_BLOSSOM_CACHE)) out[UploadSettingsStore.useLocalBlossomCache] = legacy.getBoolean(PrefKeys.USE_LOCAL_BLOSSOM_CACHE, false)
                if (legacy.contains(PrefKeys.LOCAL_BLOSSOM_CACHE_PROFILE_PICTURES_ONLY)) out[UploadSettingsStore.localBlossomCacheProfilePicturesOnly] = legacy.getBoolean(PrefKeys.LOCAL_BLOSSOM_CACHE_PROFILE_PICTURES_ONLY, false)
                legacy.getString(PrefKeys.DEFAULT_FILE_SERVER, null)?.let { out[UploadSettingsStore.defaultFileServerJson] = it }
            }
        }

    private fun dialogDismissalMigration(npub: String) =
        CopyOnceMigration("migrated.dialogDismissal") { out ->
            withContext(Dispatchers.IO) {
                val legacy = encryptedPreferences(npub)
                if (legacy.contains(PrefKeys.HIDE_DELETE_REQUEST_DIALOG)) out[DialogDismissalStore.hideDeleteRequestDialog] = legacy.getBoolean(PrefKeys.HIDE_DELETE_REQUEST_DIALOG, false)
                if (legacy.contains(PrefKeys.HIDE_BLOCK_ALERT_DIALOG)) out[DialogDismissalStore.hideBlockAlertDialog] = legacy.getBoolean(PrefKeys.HIDE_BLOCK_ALERT_DIALOG, false)
                if (legacy.contains(PrefKeys.HIDE_NIP_17_WARNING_DIALOG)) out[DialogDismissalStore.hideNip17WarningDialog] = legacy.getBoolean(PrefKeys.HIDE_NIP_17_WARNING_DIALOG, false)
                if (legacy.contains(PrefKeys.HIDE_COMMUNITY_RULES_VIOLATIONS)) out[DialogDismissalStore.hideCommunityRulesViolations] = legacy.getBoolean(PrefKeys.HIDE_COMMUNITY_RULES_VIOLATIONS, false)
                legacy.getStringSet(PrefKeys.DISMISSED_POLL_NOTE_IDS, null)?.let { out[DialogDismissalStore.dismissedPollNoteIds] = it }
                legacy.getStringSet(PrefKeys.DISMISSED_CHANNEL_INVITES, null)?.let { out[DialogDismissalStore.dismissedChannelInvites] = it }
                legacy.getStringSet(PrefKeys.MUTED_PUBLIC_CHATS, null)?.let { out[DialogDismissalStore.mutedPublicChats] = it }
                legacy.getStringSet(PrefKeys.HAS_DONATED_IN_VERSION, null)?.let { out[DialogDismissalStore.hasDonatedInVersion] = it }
                legacy.getString(PrefKeys.VIEWED_POLL_RESULT_NOTE_IDS, null)?.let { out[DialogDismissalStore.viewedPollResultNoteIdsJson] = it }
            }
        }

    private fun relayAuthMigration(npub: String) =
        CopyOnceMigration("migrated.relayAuth") { out ->
            withContext(Dispatchers.IO) {
                val legacy = encryptedPreferences(npub)
                legacy.getString(PrefKeys.DEFAULT_RELAY_AUTH_POLICY, null)?.let { out[RelayAuthStore.policyName] = it }
                if (legacy.contains(PrefKeys.RELAY_AUTH_TRUST_MY_RELAYS)) out[RelayAuthStore.trustMyRelays] = legacy.getBoolean(PrefKeys.RELAY_AUTH_TRUST_MY_RELAYS, false)
                if (legacy.contains(PrefKeys.RELAY_AUTH_TRUST_READ_FOLLOWS)) out[RelayAuthStore.trustReadFollows] = legacy.getBoolean(PrefKeys.RELAY_AUTH_TRUST_READ_FOLLOWS, false)
                if (legacy.contains(PrefKeys.RELAY_AUTH_TRUST_MESSAGE_FOLLOWS)) out[RelayAuthStore.trustMessageFollows] = legacy.getBoolean(PrefKeys.RELAY_AUTH_TRUST_MESSAGE_FOLLOWS, false)
                if (legacy.contains(PrefKeys.RELAY_AUTH_TRUST_MESSAGE_STRANGERS)) out[RelayAuthStore.trustMessageStrangers] = legacy.getBoolean(PrefKeys.RELAY_AUTH_TRUST_MESSAGE_STRANGERS, false)
            }
        }

    private fun feedVisibilityMigration(npub: String) =
        CopyOnceMigration("migrated.feedVisibility") { out ->
            withContext(Dispatchers.IO) {
                val legacy = encryptedPreferences(npub)
                legacy.getString(PrefKeys.DISABLED_CHAT_FEEDS, null)?.let { out[FeedVisibilityStore.disabledChatFeeds] = it }
                legacy.getString(PrefKeys.DISABLED_HOME_FEED_TYPES, null)?.let { out[FeedVisibilityStore.disabledHomeFeedTypes] = it }
                legacy.getString(PrefKeys.RELAY_GROUP_VIEW_MODE, null)?.let { out[FeedVisibilityStore.relayGroupViewMode] = it }
                legacy.getString(PrefKeys.CONCORD_VIEW_MODE, null)?.let { out[FeedVisibilityStore.concordViewMode] = it }
                if (legacy.contains(PrefKeys.CALLS_ENABLED)) out[FeedVisibilityStore.callsEnabled] = legacy.getBoolean(PrefKeys.CALLS_ENABLED, false)
            }
        }

    private fun notificationPrefsMigration(npub: String) =
        CopyOnceMigration("migrated.notificationPrefs") { out ->
            withContext(Dispatchers.IO) {
                val legacy = encryptedPreferences(npub)
                if (legacy.contains(PrefKeys.ALWAYS_ON_NOTIFICATION_SERVICE)) out[NotificationPrefsStore.alwaysOnService] = legacy.getBoolean(PrefKeys.ALWAYS_ON_NOTIFICATION_SERVICE, false)
                if (legacy.contains(PrefKeys.SHOW_MESSAGES_IN_NOTIFICATIONS)) out[NotificationPrefsStore.showMessagesInNotifications] = legacy.getBoolean(PrefKeys.SHOW_MESSAGES_IN_NOTIFICATIONS, false)
                if (legacy.contains(PrefKeys.SPLIT_NOTIFICATIONS_ENABLED)) out[NotificationPrefsStore.splitNotificationsEnabled] = legacy.getBoolean(PrefKeys.SPLIT_NOTIFICATIONS_ENABLED, false)
            }
        }

    /**
     * Everything the account's DataStore holds, read in one hop.
     *
     * Loaded as a group rather than store by store because
     * [innerLoadCurrentAccountFromEncryptedStorage] is already near the JVM's
     * 64KB method limit: every suspend call inside it adds a state to the
     * generated coroutine state machine, and seven separate loads pushed it
     * over. One call, one state.
     */
    private class AccountStoreData(
        val followLists: Map<FollowListSlot, TopFilter>,
        val latestEvents: Map<LatestEventSlot, String>,
        val uploadSettings: UploadSettings,
        val dialogDismissal: DialogDismissal,
        val relayAuth: RelayAuth,
        val feedVisibility: FeedVisibility,
        val notificationPrefs: NotificationPrefs,
    )

    private suspend fun loadAccountStores(npub: String) =
        AccountStoreData(
            followLists = followListStore(npub).load(),
            latestEvents = latestEventStore(npub).load(),
            uploadSettings = uploadSettingsStore(npub).load(),
            dialogDismissal = dialogDismissalStore(npub).load(),
            relayAuth = relayAuthStore(npub).load(),
            feedVisibility = feedVisibilityStore(npub).load(),
            notificationPrefs = notificationPrefsStore(npub).load(),
        )

    private fun followListMigration(npub: String) =
        CopyOnceMigration("migrated.followLists") { out ->
            withContext(Dispatchers.IO) {
                val legacy = encryptedPreferences(npub)
                FollowListSlot.entries.forEach { slot ->
                    legacy.getString(slot.prefKey, null)?.let { out[slot.key] = it }
                }
            }
        }

    private fun latestEventMigration(npub: String) =
        CopyOnceMigration("migrated.latestEvents") { out ->
            withContext(Dispatchers.IO) {
                val legacy = encryptedPreferences(npub)
                LatestEventSlot.entries.forEach { slot ->
                    legacy.getString(slot.prefKey, null)?.let { out[slot.key] = it }
                }
            }
        }

    // Global master switch for the always-on notification service ("Background
    // notification service"). Default ON: existing users keep current behavior, and
    // per-account participation decides who actually stays active.
    //
    // Stored in PLAIN (non-encrypted) SharedPreferences on purpose. It is a non-sensitive
    // global boolean, and — unlike encryptedPreferences(), which asserts non-main — plain
    // prefs can be read synchronously on ANY thread. The restart-layer gate
    // (NotificationRelayService.isEnabled) is synchronous and runs in fresh processes (boot
    // receiver, WorkManager), so it MUST read the persisted value without a suspend hop;
    // otherwise a saved OFF would be missed on cold boot and the service would resurrect.
    // The flow is lazily seeded from disk once (synchronous, main-safe) and is thereafter
    // the source of truth, so there is no async hydrate that could clobber a user toggle.
    private fun globalSettingsPrefs(): SharedPreferences = Amethyst.instance.appContext.getSharedPreferences("amethyst_global_settings", Context.MODE_PRIVATE)

    /**
     * Loads the global-settings prefs file into SharedPreferences' in-memory cache, off the main
     * thread, so the first synchronous read below hits memory rather than disk.
     *
     * The read itself is deliberately synchronous — see [setNotificationServiceEnabled]: an async
     * hydrate reintroduces a window where a late disk read clobbers a user's toggle. So this warms
     * the cache instead of deferring the read. Best-effort: if a main-thread reader wins the race it
     * simply pays the disk hit once, exactly as before.
     */
    fun warmGlobalSettings() {
        globalSettingsPrefs().getBoolean(PrefKeys.NOTIFICATION_SERVICE_ENABLED, true)
    }

    private val notificationServiceEnabled: MutableStateFlow<Boolean> by lazy {
        MutableStateFlow(globalSettingsPrefs().getBoolean(PrefKeys.NOTIFICATION_SERVICE_ENABLED, true))
    }

    fun notificationServiceEnabledFlow(): StateFlow<Boolean> = notificationServiceEnabled

    fun isNotificationServiceEnabled(): Boolean = notificationServiceEnabled.value

    fun setNotificationServiceEnabled(enabled: Boolean) {
        // In-memory update is the source of truth (main-safe); plain-prefs edit{} persists
        // asynchronously via apply(), also main-safe. No suspend/hydrate hop, so no window
        // where a late disk read can clobber this write.
        notificationServiceEnabled.value = enabled
        globalSettingsPrefs().edit { putBoolean(PrefKeys.NOTIFICATION_SERVICE_ENABLED, enabled) }
    }

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
        // Fast path: already populated, no lock needed.
        savedAccounts.value?.let { return it }

        return savedAccountsMutex.withLock {
            // Re-check under the lock: another coroutine may have populated it while we waited.
            savedAccounts.value ?: loadSavedAccountsFromStorage().also { savedAccounts.emit(it) }
        }
    }

    private suspend fun loadSavedAccountsFromStorage(): List<AccountInfo> =
        withContext(Dispatchers.IO) {
            with(encryptedPreferences()) {
                val newSystemOfAccounts =
                    getString(PrefKeys.ALL_ACCOUNT_INFO, "[]")?.let {
                        JsonMapper.fromJson<List<AccountInfo>>(it)
                    }

                if (!newSystemOfAccounts.isNullOrEmpty()) {
                    // How many accounts are in play is the first thing you need when reading any
                    // boot log: nearly every per-account subsystem below multiplies by this number.
                    Log.i("LocalPreferences") { "Found ${newSystemOfAccounts.size} saved account(s)" }
                    newSystemOfAccounts
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

                    edit {
                        putString(PrefKeys.ALL_ACCOUNT_INFO, JsonMapper.toJson(migrated))
                    }

                    migrated
                }
            }
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
            // Drop the in-memory copy as well; otherwise re-adding the same account later
            // would resurrect the deleted settings from this cache.
            mutex.withLock { cachedAccounts.remove(accountInfo.npub) }
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

    /**
     * Make [accountSettings] the current account, persisting + caching it. Returns the settings that
     * actually became current — normally [accountSettings] itself, but see the downgrade guard below.
     */
    suspend fun setDefaultAccount(accountSettings: AccountSettings): AccountSettings {
        val npub = accountSettings.keyPair.pubKey.toNpub()

        // Downgrade guard: adding a read-only npub for a pubkey we already hold a SIGNING account for
        // must not clobber that account. Accounts dedup by npub, so saving fresh read-only settings
        // here would overwrite the signing account's per-npub file — wiping its cached follow/relay/
        // mute lists and flipping hasPrivKey off, which silently disables its push notifications. A
        // signing account already does everything the read-only one would, so keep it and just make
        // it current instead of degrading it.
        if (!accountSettings.isWriteable()) {
            val existing = loadAccountConfigFromEncryptedStorage(npub)
            if (existing != null && existing.isWriteable()) {
                setCurrentAccount(existing)
                return existing
            }
        }

        // Save the per-npub file before emitting onto the savedAccounts flow.
        // Otherwise a collector (e.g. AlwaysOnNotificationServiceManager) can race in
        // and call loadAccountConfigFromEncryptedStorage(npub) before NOSTR_PUBKEY is
        // written, get null back, and poison `cachedAccounts[npub] = null` for the
        // rest of the session — making every later switch to this account land on
        // LoggedOff instead of LoggedIn.
        saveToEncryptedStorage(accountSettings)
        mutex.withLock { cachedAccounts.put(npub, accountSettings) }
        setCurrentAccount(accountSettings)
        return accountSettings
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

                    putBoolean(PrefKeys.NIP46_SIGNER_ENABLED, settings.nip46SignerEnabled.value)
                    putString(PrefKeys.NIP46_BUNKER_SECRET, settings.nip46BunkerSecret.value)
                    putString(PrefKeys.NIP46_TRANSPORT_KEY, settings.nip46TransportKey.value)
                    putStringSet(PrefKeys.NIP46_SEEN_IDS, settings.nip46SeenRequestIds.value)

                    // top-nav filters now live in the account's DataStore; written below,
                    // outside this edit block, because that write is suspend.

                    val walletEntries = settings.nwcWallets.value.mapNotNull { it.denormalize() }
                    if (walletEntries.isNotEmpty()) {
                        putString(PrefKeys.NWC_WALLETS, JsonMapper.toJson(walletEntries))
                    } else {
                        remove(PrefKeys.NWC_WALLETS)
                    }

                    val debitEntries = settings.clinkDebitWallets.value.map { it.denormalize() }
                    if (debitEntries.isNotEmpty()) {
                        putString(PrefKeys.CLINK_DEBIT_WALLETS, JsonMapper.toJson(debitEntries))
                    } else {
                        remove(PrefKeys.CLINK_DEBIT_WALLETS)
                    }

                    settings.defaultPaymentSourceId.value?.let {
                        putString(PrefKeys.DEFAULT_PAYMENT_SOURCE_ID, it)
                    } ?: remove(PrefKeys.DEFAULT_PAYMENT_SOURCE_ID)
                    // Legacy NWC-only default key is superseded by DEFAULT_PAYMENT_SOURCE_ID.
                    remove(PrefKeys.DEFAULT_NWC_WALLET_ID)

                    // Remove legacy key after migration
                    remove(PrefKeys.ZAP_PAYMENT_REQUEST_SERVER)

                    // The undecided conflicts themselves, not just the backups they hold back.
                    // Without these the card vanishes on the next launch and the user never
                    // answers the question the backup is still waiting on.
                    val openConflicts = settings.openBackupConflicts()
                    if (openConflicts.isEmpty()) {
                        remove(PrefKeys.OPEN_BACKUP_CONFLICTS)
                    } else {
                        putString(PrefKeys.OPEN_BACKUP_CONFLICTS, BackupConflictStorage.encode(openConflicts))
                    }

                    if (settings.localRelayServers.value.isNotEmpty()) {
                        putStringSet(PrefKeys.LOCAL_RELAY_SERVERS, settings.localRelayServers.value)
                    } else {
                        remove(PrefKeys.LOCAL_RELAY_SERVERS)
                    }

                    // Any account that reaches a save has its notification filter in its
                    // post-split meaning, so stamp it as migrated. This keeps the one-shot
                    // Global -> Selected rewrite from ever touching it again and preserves a
                    // deliberate raw-Global choice (including on brand-new accounts).
                    putBoolean(PrefKeys.NOTIF_GLOBAL_TO_CURATED_MIGRATED, true)

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

                    putString(
                        PrefKeys.PENDING_ATTESTATIONS,
                        JsonMapper.toJson(settings.pendingAttestations.value),
                    )
                }
            }
            uploadSettingsStore(settings.keyPair.pubKey.toNpub()).save(
                UploadSettings(
                    stripLocationOnUpload = settings.stripLocationOnUpload,
                    optimizeMediaOnUpload = settings.optimizeMediaOnUpload.value,
                    mirrorUploadsToAllServers = settings.mirrorUploadsToAllServers.value,
                    useLocalBlossomCache = settings.useLocalBlossomCache.value,
                    localBlossomCacheProfilePicturesOnly = settings.localBlossomCacheProfilePicturesOnly.value,
                    defaultFileServerJson = JsonMapper.toJson(settings.defaultFileServer),
                ),
            )
            dialogDismissalStore(settings.keyPair.pubKey.toNpub()).save(
                DialogDismissal(
                    hideDeleteRequestDialog = settings.hideDeleteRequestDialog,
                    hideBlockAlertDialog = settings.hideBlockAlertDialog,
                    hideNip17WarningDialog = settings.hideNIP17WarningDialog,
                    hideCommunityRulesViolations = settings.hideCommunityRulesViolations.value,
                    dismissedPollNoteIds = settings.dismissedPollNoteIds.value,
                    dismissedChannelInvites = settings.dismissedChannelInvites.value,
                    mutedPublicChats = settings.mutedPublicChats.value,
                    hasDonatedInVersion = settings.hasDonatedInVersion.value,
                    viewedPollResultNoteIdsJson = JsonMapper.toJson(settings.viewedPollResultNoteIds.value),
                ),
            )
            relayAuthStore(settings.keyPair.pubKey.toNpub()).save(
                RelayAuth(
                    policyName = settings.defaultRelayAuthPolicy.value.name,
                    trustMyRelays = settings.relayAuthTrustMyRelaysAndVenues.value,
                    trustReadFollows = settings.relayAuthTrustReadFollows.value,
                    trustMessageFollows = settings.relayAuthTrustMessageFollows.value,
                    trustMessageStrangers = settings.relayAuthTrustMessageStrangers.value,
                ),
            )
            feedVisibilityStore(settings.keyPair.pubKey.toNpub()).save(
                FeedVisibility(
                    disabledChatFeeds = ChatFeedType.encode(ChatFeedType.ALL - settings.enabledChatFeeds.value),
                    disabledHomeFeedTypes = HomeFeedType.encode(HomeFeedType.ALL - settings.enabledHomeFeedTypes.value),
                    relayGroupViewMode = settings.relayGroupViewMode.value.name,
                    concordViewMode = settings.concordViewMode.value.name,
                    callsEnabled = settings.callsEnabled.value,
                ),
            )
            notificationPrefsStore(settings.keyPair.pubKey.toNpub()).save(
                NotificationPrefs(
                    alwaysOnService = settings.alwaysOnNotificationService.value,
                    showMessagesInNotifications = settings.showMessagesInNotifications.value,
                    splitNotificationsEnabled = settings.splitNotificationsEnabled.value,
                ),
            )
            latestEventStore(settings.keyPair.pubKey.toNpub()).saveAll(
                mapOf(
                    LatestEventSlot.CONTACT_LIST to settings.backupContactList?.let { OptimizedJsonMapper.toJson(it) },
                    LatestEventSlot.USER_METADATA to settings.backupUserMetadata?.let { OptimizedJsonMapper.toJson(it) },
                    LatestEventSlot.DM_RELAY_LIST to settings.backupDMRelayList?.let { OptimizedJsonMapper.toJson(it) },
                    LatestEventSlot.NIP65_RELAY_LIST to settings.backupNIP65RelayList?.let { OptimizedJsonMapper.toJson(it) },
                    LatestEventSlot.SEARCH_RELAY_LIST to settings.backupSearchRelayList?.let { OptimizedJsonMapper.toJson(it) },
                    LatestEventSlot.INDEX_RELAY_LIST to settings.backupIndexRelayList?.let { OptimizedJsonMapper.toJson(it) },
                    LatestEventSlot.RELAY_FEEDS_LIST to settings.backupRelayFeedsList?.let { OptimizedJsonMapper.toJson(it) },
                    LatestEventSlot.BLOCKED_RELAY_LIST to settings.backupBlockedRelayList?.let { OptimizedJsonMapper.toJson(it) },
                    LatestEventSlot.TRUSTED_RELAY_LIST to settings.backupTrustedRelayList?.let { OptimizedJsonMapper.toJson(it) },
                    LatestEventSlot.MUTE_LIST to settings.backupMuteList?.let { OptimizedJsonMapper.toJson(it) },
                    LatestEventSlot.PRIVATE_HOME_RELAY_LIST to settings.backupPrivateHomeRelayList?.let { OptimizedJsonMapper.toJson(it) },
                    LatestEventSlot.APP_SPECIFIC_DATA to settings.backupAppSpecificData?.let { OptimizedJsonMapper.toJson(it) },
                    LatestEventSlot.CHANNEL_LIST to settings.backupChannelList?.let { OptimizedJsonMapper.toJson(it) },
                    LatestEventSlot.COMMUNITY_LIST to settings.backupCommunityList?.let { OptimizedJsonMapper.toJson(it) },
                    LatestEventSlot.HASHTAG_LIST to settings.backupHashtagList?.let { OptimizedJsonMapper.toJson(it) },
                    LatestEventSlot.GEOHASH_LIST to settings.backupGeohashList?.let { OptimizedJsonMapper.toJson(it) },
                    LatestEventSlot.EPHEMERAL_LIST to settings.backupEphemeralChatList?.let { OptimizedJsonMapper.toJson(it) },
                    LatestEventSlot.RELAY_GROUP_LIST to settings.backupRelayGroupList?.let { OptimizedJsonMapper.toJson(it) },
                    LatestEventSlot.CONCORD_LIST to settings.backupConcordList?.let { OptimizedJsonMapper.toJson(it) },
                    LatestEventSlot.TRUST_PROVIDER_LIST to settings.backupTrustProviderList?.let { OptimizedJsonMapper.toJson(it) },
                    LatestEventSlot.KEY_PACKAGE_RELAY_LIST to settings.backupKeyPackageRelayList?.let { OptimizedJsonMapper.toJson(it) },
                    LatestEventSlot.FAVORITE_ALGO_FEEDS_LIST to settings.backupFavoriteAlgoFeedsList?.let { OptimizedJsonMapper.toJson(it) },
                    LatestEventSlot.PAYMENT_TARGETS to settings.backupNipA3PaymentTargets?.let { OptimizedJsonMapper.toJson(it) },
                    LatestEventSlot.BOLT12_OFFERS to settings.backupBolt12Offers?.let { OptimizedJsonMapper.toJson(it) },
                    LatestEventSlot.CASHU_WALLET to settings.backupCashuWallet?.let { OptimizedJsonMapper.toJson(it) },
                    LatestEventSlot.NUTZAP_INFO to settings.backupNutzapInfo?.let { OptimizedJsonMapper.toJson(it) },
                ),
            )
            followListStore(settings.keyPair.pubKey.toNpub()).saveAll(
                mapOf(
                    FollowListSlot.HOME to settings.defaultHomeFollowList.value,
                    FollowListSlot.STORIES to settings.defaultStoriesFollowList.value,
                    FollowListSlot.NOTIFICATION to settings.defaultNotificationFollowList.value,
                    FollowListSlot.DISCOVERY to settings.defaultDiscoveryFollowList.value,
                    FollowListSlot.POLLS to settings.defaultPollsFollowList.value,
                    FollowListSlot.PICTURES to settings.defaultPicturesFollowList.value,
                    FollowListSlot.RELAY_GROUPS_DISCOVERY to settings.defaultRelayGroupsDiscoveryFollowList.value,
                    FollowListSlot.NAPPLETS to settings.defaultNappletsFollowList.value,
                    FollowListSlot.NSITES to settings.defaultNsitesFollowList.value,
                    FollowListSlot.WORKOUTS to settings.defaultWorkoutsFollowList.value,
                    FollowListSlot.GIT_REPOSITORIES to settings.defaultGitRepositoriesFollowList.value,
                    FollowListSlot.HIGHLIGHTS to settings.defaultHighlightsFollowList.value,
                    FollowListSlot.CALENDARS to settings.defaultCalendarsFollowList.value,
                    FollowListSlot.PRODUCTS to settings.defaultProductsFollowList.value,
                    FollowListSlot.GEOCACHES to settings.defaultGeocachesFollowList.value,
                    FollowListSlot.SHORTS to settings.defaultShortsFollowList.value,
                    FollowListSlot.PUBLIC_CHATS to settings.defaultPublicChatsFollowList.value,
                    FollowListSlot.LIVE_STREAMS to settings.defaultLiveStreamsFollowList.value,
                    FollowListSlot.NESTS to settings.defaultNestsFollowList.value,
                    FollowListSlot.LONGS to settings.defaultLongsFollowList.value,
                    FollowListSlot.ARTICLES to settings.defaultArticlesFollowList.value,
                    FollowListSlot.MUSIC_TRACKS to settings.defaultMusicTracksFollowList.value,
                    FollowListSlot.MUSIC_PLAYLISTS to settings.defaultMusicPlaylistsFollowList.value,
                    FollowListSlot.PODCAST_EPISODES to settings.defaultPodcastEpisodesFollowList.value,
                    FollowListSlot.PODCASTS to settings.defaultPodcastsFollowList.value,
                    FollowListSlot.SOFTWARE_APPS to settings.defaultSoftwareAppsFollowList.value,
                    FollowListSlot.BADGES to settings.defaultBadgesFollowList.value,
                    FollowListSlot.BROWSE_EMOJI_SETS to settings.defaultBrowseEmojiSetsFollowList.value,
                    FollowListSlot.COMMUNITIES to settings.defaultCommunitiesFollowList.value,
                    FollowListSlot.FOLLOW_PACKS to settings.defaultFollowPacksFollowList.value,
                    FollowListSlot.APP_RECOMMENDATIONS to settings.defaultAppRecommendationsFollowList.value,
                ),
            )
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

    // Reactive, per-account cache of the "has backed up keys" flag so the home-screen
    // nudge updates the instant the user backs up or dismisses it, without a full
    // account reload. Keyed by npub. Seeded lazily from encrypted storage.
    private val hasBackedUpKeysFlows: MutableMap<String, MutableStateFlow<Boolean>> = mutableMapOf()
    private val hasBackedUpKeysMutex = Mutex()

    private suspend fun hasBackedUpKeysFlow(npub: String): MutableStateFlow<Boolean> =
        hasBackedUpKeysMutex.withLock {
            hasBackedUpKeysFlows.getOrPut(npub) {
                val stored =
                    withContext(Dispatchers.IO) {
                        encryptedPreferences(npub).getBoolean(PrefKeys.HAS_BACKED_UP_KEYS, true)
                    }
                MutableStateFlow(stored)
            }
        }

    /** Reactive flag: true (default) unless a freshly-generated account still needs to back up its key. */
    suspend fun hasBackedUpKeys(npub: String): MutableStateFlow<Boolean> = hasBackedUpKeysFlow(npub)

    suspend fun setHasBackedUpKeys(
        value: Boolean,
        npub: String,
    ) {
        withContext(Dispatchers.IO) {
            encryptedPreferences(npub).edit { putBoolean(PrefKeys.HAS_BACKED_UP_KEYS, value) }
        }
        hasBackedUpKeysFlow(npub).value = value
    }

    val mutex = Mutex()

    suspend fun loadAccountConfigFromEncryptedStorage(npub: String): AccountSettings? {
        // if already loaded, return right away
        cachedAccounts[npub]?.let { return it }

        return withContext(Dispatchers.IO) {
            mutex.withLock {
                cachedAccounts[npub]?.let { return@withContext it }

                val accountSettings = innerLoadCurrentAccountFromEncryptedStorage(npub)

                // Only cache successful loads. Caching null would leave the account
                // permanently unreachable for the rest of the session if a reader
                // raced in before the per-npub file finished being written.
                if (accountSettings != null) {
                    cachedAccounts.put(npub, accountSettings)
                }

                return@withContext accountSettings
            }
        }
    }

    private suspend fun innerLoadCurrentAccountFromEncryptedStorage(npub: String?): AccountSettings? {
        Log.d("LocalPreferences") { "Load account from file $npub" }
        val startedAtMs = TimeUtils.nowMillis()
        val result =
            withContext(Dispatchers.IO) {
                return@withContext with(encryptedPreferences(npub)) {
                    Log.d("LocalPreferences") { "Load account from file $npub - opened file" }
                    val privKey = getString(PrefKeys.NOSTR_PRIVKEY, null)
                    val pubKey = getString(PrefKeys.NOSTR_PUBKEY, null) ?: return@with null
                    val externalSignerPackageName = getString(PrefKeys.SIGNER_PACKAGE_NAME, null) ?: if (getBoolean(PrefKeys.LOGIN_WITH_EXTERNAL_SIGNER, false)) "com.greenart7c3.nostrsigner" else null

                    val keyPair = KeyPair(privKey = privKey?.hexToByteArray(), pubKey = pubKey.hexToByteArray())

                    val stores = loadAccountStores(keyPair.pubKey.toNpub())

                    Log.d("LocalPreferences") { "Load account from file $npub - keys ready" }

                    val stripLocationOnUpload = stores.uploadSettings.stripLocationOnUpload
                    val useLocalBlossomCache = stores.uploadSettings.useLocalBlossomCache
                    val localBlossomCacheProfilePicturesOnly = stores.uploadSettings.localBlossomCacheProfilePicturesOnly
                    val mirrorUploadsToAllServers = stores.uploadSettings.mirrorUploadsToAllServers
                    val optimizeMediaOnUpload = stores.uploadSettings.optimizeMediaOnUpload
                    val hideCommunityRulesViolations = stores.dialogDismissal.hideCommunityRulesViolations
                    val nip46SignerEnabled = getBoolean(PrefKeys.NIP46_SIGNER_ENABLED, false)
                    val nip46BunkerSecret = getString(PrefKeys.NIP46_BUNKER_SECRET, "") ?: ""
                    val nip46TransportKey = getString(PrefKeys.NIP46_TRANSPORT_KEY, "") ?: ""
                    val nip46SeenRequestIds = getStringSet(PrefKeys.NIP46_SEEN_IDS, null) ?: setOf()
                    val hideDeleteRequestDialog = stores.dialogDismissal.hideDeleteRequestDialog
                    val hideBlockAlertDialog = stores.dialogDismissal.hideBlockAlertDialog
                    val hideNIP17WarningDialog = stores.dialogDismissal.hideNip17WarningDialog
                    val callsEnabled = stores.feedVisibility.callsEnabled
                    val alwaysOnNotificationService = stores.notificationPrefs.alwaysOnService
                    // Read as a group via a helper: this load lambda sits right at the JVM's
                    // per-method bytecode limit (see the note above the awaits below), so keeping
                    // these heavy string/enum decodes out of it preserves headroom.
                    val inboxPrefs = readInboxPrefs(stores.relayAuth, stores.feedVisibility)
                    val splitNotificationsEnabled = stores.notificationPrefs.splitNotificationsEnabled
                    val showMessagesInNotifications = stores.notificationPrefs.showMessagesInNotifications
                    val hasDonatedInVersion = stores.dialogDismissal.hasDonatedInVersion
                    val dismissedPollNoteIds = stores.dialogDismissal.dismissedPollNoteIds
                    val dismissedChannelInvites = stores.dialogDismissal.dismissedChannelInvites
                    val mutedPublicChats = stores.dialogDismissal.mutedPublicChats
                    val viewedPollResultNoteIdsStr = stores.dialogDismissal.viewedPollResultNoteIdsJson
                    val localRelayServers = getStringSet(PrefKeys.LOCAL_RELAY_SERVERS, null) ?: setOf()

                    val followListPrefs = toFollowListPrefs(stores.followLists)

                    val zapPaymentRequestServerStr = getString(PrefKeys.ZAP_PAYMENT_REQUEST_SERVER, null)
                    val nwcWalletsStr = getString(PrefKeys.NWC_WALLETS, null)
                    val defaultNwcWalletIdStr = getString(PrefKeys.DEFAULT_NWC_WALLET_ID, null)
                    val clinkDebitWalletsStr = getString(PrefKeys.CLINK_DEBIT_WALLETS, null)
                    val defaultPaymentSourceIdStr = getString(PrefKeys.DEFAULT_PAYMENT_SOURCE_ID, null)
                    val defaultFileServerStr = stores.uploadSettings.defaultFileServerJson

                    val pendingAttestationsStr = getString(PrefKeys.PENDING_ATTESTATIONS, null)
                    val openBackupConflictsStr = getString(PrefKeys.OPEN_BACKUP_CONFLICTS, null)
                    val latestUserMetadataStr = stores.latestEvents[LatestEventSlot.USER_METADATA]
                    val latestContactListStr = stores.latestEvents[LatestEventSlot.CONTACT_LIST]
                    val latestDmRelayListStr = stores.latestEvents[LatestEventSlot.DM_RELAY_LIST]
                    val latestNip65RelayListStr = stores.latestEvents[LatestEventSlot.NIP65_RELAY_LIST]
                    val latestSearchRelayListStr = stores.latestEvents[LatestEventSlot.SEARCH_RELAY_LIST]
                    val latestIndexRelayListStr = stores.latestEvents[LatestEventSlot.INDEX_RELAY_LIST]
                    val latestRelayFeedsListStr = stores.latestEvents[LatestEventSlot.RELAY_FEEDS_LIST]
                    val latestBlockedRelayListStr = stores.latestEvents[LatestEventSlot.BLOCKED_RELAY_LIST]
                    val latestTrustedRelayListStr = stores.latestEvents[LatestEventSlot.TRUSTED_RELAY_LIST]
                    val latestMuteListStr = stores.latestEvents[LatestEventSlot.MUTE_LIST]
                    val latestPrivateHomeRelayListStr = stores.latestEvents[LatestEventSlot.PRIVATE_HOME_RELAY_LIST]
                    val latestAppSpecificDataStr = stores.latestEvents[LatestEventSlot.APP_SPECIFIC_DATA]
                    val latestChannelListStr = stores.latestEvents[LatestEventSlot.CHANNEL_LIST]
                    val latestCommunityListStr = stores.latestEvents[LatestEventSlot.COMMUNITY_LIST]
                    val latestHashtagListStr = stores.latestEvents[LatestEventSlot.HASHTAG_LIST]
                    val latestGeohashListStr = stores.latestEvents[LatestEventSlot.GEOHASH_LIST]
                    val latestEphemeralListStr = stores.latestEvents[LatestEventSlot.EPHEMERAL_LIST]
                    val latestRelayGroupListStr = stores.latestEvents[LatestEventSlot.RELAY_GROUP_LIST]
                    val latestConcordListStr = stores.latestEvents[LatestEventSlot.CONCORD_LIST]
                    val latestTrustProviderListStr = stores.latestEvents[LatestEventSlot.TRUST_PROVIDER_LIST]
                    val latestKeyPackageRelayListStr = stores.latestEvents[LatestEventSlot.KEY_PACKAGE_RELAY_LIST]
                    val latestFavoriteAlgoFeedsListStr = stores.latestEvents[LatestEventSlot.FAVORITE_ALGO_FEEDS_LIST]
                    val latestPaymentTargetsStr = stores.latestEvents[LatestEventSlot.PAYMENT_TARGETS]
                    val latestBolt12OffersStr = stores.latestEvents[LatestEventSlot.BOLT12_OFFERS]
                    val latestCashuWalletStr = stores.latestEvents[LatestEventSlot.CASHU_WALLET]
                    val latestNutzapInfoStr = stores.latestEvents[LatestEventSlot.NUTZAP_INFO]
                    val lastReadPerRouteStr = getString(PrefKeys.LAST_READ_PER_ROUTE, null)

                    Log.d("LocalPreferences") { "Load account from file $npub - before parsing events" }

                    val nwcWalletsLoaded =
                        async {
                            val nwcWalletEntries = parseOrNull<List<NwcWalletEntry>>(nwcWalletsStr)
                            if (nwcWalletEntries != null && nwcWalletEntries.isNotEmpty()) {
                                val wallets = nwcWalletEntries.mapNotNull { it.normalize() }
                                val defaultId = defaultNwcWalletIdStr ?: wallets.firstOrNull()?.id
                                Pair(wallets, defaultId)
                            } else {
                                val legacyUri = parseOrNull<Nip47WalletConnect.Nip47URI>(zapPaymentRequestServerStr)
                                val legacyNorm = legacyUri?.normalize()
                                if (legacyNorm != null) {
                                    val migrated =
                                        NwcWalletEntryNorm(
                                            id =
                                                java.util.UUID
                                                    .randomUUID()
                                                    .toString(),
                                            name = "Wallet",
                                            uri = legacyNorm,
                                        )
                                    Pair(listOf(migrated), migrated.id)
                                } else {
                                    Pair(emptyList(), null)
                                }
                            }
                        }
                    val clinkDebitsLoaded =
                        async {
                            parseOrNull<List<ClinkDebitWalletEntry>>(clinkDebitWalletsStr)?.mapNotNull { it.normalize() } ?: emptyList()
                        }
                    val defaultFileServer = async { parseOrNull<ServerName>(defaultFileServerStr) ?: DEFAULT_MEDIA_SERVERS[0] }

                    val viewedPollResultNoteIds = async { parseOrNull<Map<String, Long>>(viewedPollResultNoteIdsStr) ?: mapOf() }
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
                    val latestRelayGroupList = async { parseEventOrNull<SimpleGroupListEvent>(latestRelayGroupListStr) }
                    val latestConcordList = async { parseEventOrNull<ConcordCommunityListEvent>(latestConcordListStr) }
                    val latestTrustProviderList = async { parseEventOrNull<TrustProviderListEvent>(latestTrustProviderListStr) }
                    val latestKeyPackageRelayList = async { parseEventOrNull<KeyPackageRelayListEvent>(latestKeyPackageRelayListStr) }
                    val latestFavoriteAlgoFeedsList = async { parseEventOrNull<FavoriteAlgoFeedsListEvent>(latestFavoriteAlgoFeedsListStr) }
                    val latestPaymentTargets = async { parseEventOrNull<PaymentTargetsEvent>(latestPaymentTargetsStr) }
                    val latestBolt12Offers = async { parseEventOrNull<Bolt12OfferListEvent>(latestBolt12OffersStr) }
                    val latestCashuWallet =
                        async {
                            parseEventOrNull<com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletEvent>(latestCashuWalletStr)
                        }
                    val latestNutzapInfo =
                        async {
                            parseEventOrNull<com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent>(latestNutzapInfoStr)
                        }

                    val lastReadPerRoute =
                        async {
                            parseOrNull<Map<String, Long>>(lastReadPerRouteStr)?.mapValues {
                                MutableStateFlow(it.value)
                            } ?: mapOf()
                        }

                    Log.d("LocalPreferences") { "Load account from file $npub - asyncs created" }

                    // Resolve every parallel parse into a local before constructing AccountSettings.
                    // Awaiting inside the 70-argument constructor expression below would place ~27
                    // suspension points in the middle of a single huge operand stack, forcing the
                    // coroutine state machine to spill/restore every partially-evaluated argument at
                    // each point. That bloats the generated method past the compiler's per-method
                    // instruction limit ("Method exceeds compiler instruction limit"). Awaiting into
                    // vals first keeps each suspension point at a statement boundary (near-empty
                    // operand stack) and leaves the constructor as straight-line, suspension-free code.
                    val nwcWalletsResolved = nwcWalletsLoaded.await()
                    val clinkDebitsResolved = clinkDebitsLoaded.await()
                    val defaultFileServerResolved = defaultFileServer.await()
                    val viewedPollResultNoteIdsResolved = viewedPollResultNoteIds.await()
                    val pendingAttestationsResolved = pendingAttestations.await()
                    val lastReadPerRouteResolved = lastReadPerRoute.await()
                    val latestUserMetadataResolved = latestUserMetadata.await()
                    val latestContactListResolved = latestContactList.await()
                    val latestDmRelayListResolved = latestDmRelayList.await()
                    val latestNip65RelayListResolved = latestNip65RelayList.await()
                    val latestSearchRelayListResolved = latestSearchRelayList.await()
                    val latestIndexRelayListResolved = latestIndexRelayList.await()
                    val latestRelayFeedsListResolved = latestRelayFeedsList.await()
                    val latestBlockedRelayListResolved = latestBlockedRelayList.await()
                    val latestTrustedRelayListResolved = latestTrustedRelayList.await()
                    val latestMuteListResolved = latestMuteList.await()
                    val latestPrivateHomeRelayListResolved = latestPrivateHomeRelayList.await()
                    val latestAppSpecificDataResolved = latestAppSpecificData.await()
                    val latestChannelListResolved = latestChannelList.await()
                    val latestCommunityListResolved = latestCommunityList.await()
                    val latestHashtagListResolved = latestHashtagList.await()
                    val latestGeohashListResolved = latestGeohashList.await()
                    val latestEphemeralListResolved = latestEphemeralList.await()
                    val latestRelayGroupListResolved = latestRelayGroupList.await()
                    val latestConcordListResolved = latestConcordList.await()
                    val latestTrustProviderListResolved = latestTrustProviderList.await()
                    val latestKeyPackageRelayListResolved = latestKeyPackageRelayList.await()
                    val latestFavoriteAlgoFeedsListResolved = latestFavoriteAlgoFeedsList.await()
                    val latestPaymentTargetsResolved = latestPaymentTargets.await()
                    val latestBolt12OffersResolved = latestBolt12Offers.await()
                    val latestCashuWalletResolved = latestCashuWallet.await()
                    val latestNutzapInfoResolved = latestNutzapInfo.await()

                    Log.d("LocalPreferences") { "Load account from file $npub - asyncs resolved" }

                    return@with AccountSettings(
                        keyPair = keyPair,
                        transientAccount = false,
                        cashuCounters = CashuPreferences.forAccount(keyPair.pubKey.toNpub()),
                        externalSignerPackageName = externalSignerPackageName,
                        localRelayServers = MutableStateFlow(localRelayServers),
                        defaultFileServer = defaultFileServerResolved,
                        stripLocationOnUpload = stripLocationOnUpload,
                        useLocalBlossomCache = MutableStateFlow(useLocalBlossomCache),
                        localBlossomCacheProfilePicturesOnly = MutableStateFlow(localBlossomCacheProfilePicturesOnly),
                        mirrorUploadsToAllServers = MutableStateFlow(mirrorUploadsToAllServers),
                        optimizeMediaOnUpload = MutableStateFlow(optimizeMediaOnUpload),
                        hideCommunityRulesViolations = MutableStateFlow(hideCommunityRulesViolations),
                        nip46SignerEnabled = MutableStateFlow(nip46SignerEnabled),
                        nip46BunkerSecret = MutableStateFlow(nip46BunkerSecret),
                        nip46TransportKey = MutableStateFlow(nip46TransportKey),
                        nip46SeenRequestIds = MutableStateFlow(nip46SeenRequestIds),
                        defaultHomeFollowList = MutableStateFlow(followListPrefs.home),
                        defaultStoriesFollowList = MutableStateFlow(followListPrefs.stories),
                        defaultNotificationFollowList = MutableStateFlow(followListPrefs.notification),
                        defaultDiscoveryFollowList = MutableStateFlow(followListPrefs.discovery),
                        defaultPollsFollowList = MutableStateFlow(followListPrefs.polls),
                        defaultPicturesFollowList = MutableStateFlow(followListPrefs.pictures),
                        defaultRelayGroupsDiscoveryFollowList = MutableStateFlow(followListPrefs.relayGroupsDiscovery),
                        defaultNappletsFollowList = MutableStateFlow(followListPrefs.napplets),
                        defaultNsitesFollowList = MutableStateFlow(followListPrefs.nsites),
                        defaultWorkoutsFollowList = MutableStateFlow(followListPrefs.workouts),
                        defaultGitRepositoriesFollowList = MutableStateFlow(followListPrefs.gitRepositories),
                        defaultHighlightsFollowList = MutableStateFlow(followListPrefs.highlights),
                        defaultCalendarsFollowList = MutableStateFlow(followListPrefs.calendars),
                        defaultProductsFollowList = MutableStateFlow(followListPrefs.products),
                        defaultGeocachesFollowList = MutableStateFlow(followListPrefs.geocaches),
                        defaultShortsFollowList = MutableStateFlow(followListPrefs.shorts),
                        defaultPublicChatsFollowList = MutableStateFlow(followListPrefs.publicChats),
                        defaultLiveStreamsFollowList = MutableStateFlow(followListPrefs.liveStreams),
                        defaultNestsFollowList = MutableStateFlow(followListPrefs.nests),
                        defaultLongsFollowList = MutableStateFlow(followListPrefs.longs),
                        defaultMusicTracksFollowList = MutableStateFlow(followListPrefs.musicTracks),
                        defaultMusicPlaylistsFollowList = MutableStateFlow(followListPrefs.musicPlaylists),
                        defaultPodcastEpisodesFollowList = MutableStateFlow(followListPrefs.podcastEpisodes),
                        defaultPodcastsFollowList = MutableStateFlow(followListPrefs.podcasts),
                        defaultArticlesFollowList = MutableStateFlow(followListPrefs.articles),
                        defaultSoftwareAppsFollowList = MutableStateFlow(followListPrefs.softwareApps),
                        defaultBadgesFollowList = MutableStateFlow(followListPrefs.badges),
                        defaultBrowseEmojiSetsFollowList = MutableStateFlow(followListPrefs.browseEmojiSets),
                        defaultCommunitiesFollowList = MutableStateFlow(followListPrefs.communities),
                        defaultFollowPacksFollowList = MutableStateFlow(followListPrefs.followPacks),
                        defaultAppRecommendationsFollowList = MutableStateFlow(followListPrefs.appRecommendations),
                        nwcWallets = MutableStateFlow(nwcWalletsResolved.first),
                        clinkDebitWallets = MutableStateFlow(clinkDebitsResolved),
                        // Prefer the new unified default; migrate from the legacy NWC default;
                        // else fall back to the first configured source (NWC before debits).
                        defaultPaymentSourceId =
                            MutableStateFlow(
                                defaultPaymentSourceIdStr
                                    ?: nwcWalletsResolved.second
                                    ?: clinkDebitsResolved.firstOrNull()?.id,
                            ),
                        hideDeleteRequestDialog = hideDeleteRequestDialog,
                        hideBlockAlertDialog = hideBlockAlertDialog,
                        hideNIP17WarningDialog = hideNIP17WarningDialog,
                        alwaysOnNotificationService = MutableStateFlow(alwaysOnNotificationService),
                        defaultRelayAuthPolicy = MutableStateFlow(inboxPrefs.defaultRelayAuthPolicy),
                        relayGroupViewMode = MutableStateFlow(inboxPrefs.relayGroupViewMode),
                        concordViewMode = MutableStateFlow(inboxPrefs.concordViewMode),
                        enabledChatFeeds = MutableStateFlow(inboxPrefs.enabledChatFeeds),
                        enabledHomeFeedTypes = MutableStateFlow(inboxPrefs.enabledHomeFeedTypes),
                        relayAuthTrustMyRelaysAndVenues = MutableStateFlow(inboxPrefs.relayAuthTrustMyRelays),
                        relayAuthTrustReadFollows = MutableStateFlow(inboxPrefs.relayAuthTrustReadFollows),
                        relayAuthTrustMessageFollows = MutableStateFlow(inboxPrefs.relayAuthTrustMessageFollows),
                        relayAuthTrustMessageStrangers = MutableStateFlow(inboxPrefs.relayAuthTrustMessageStrangers),
                        splitNotificationsEnabled = MutableStateFlow(splitNotificationsEnabled),
                        showMessagesInNotifications = MutableStateFlow(showMessagesInNotifications),
                        backupUserMetadata = latestUserMetadataResolved,
                        backupContactList = latestContactListResolved,
                        backupNIP65RelayList = latestNip65RelayListResolved,
                        backupDMRelayList = latestDmRelayListResolved,
                        backupSearchRelayList = latestSearchRelayListResolved,
                        backupIndexRelayList = latestIndexRelayListResolved,
                        backupRelayFeedsList = latestRelayFeedsListResolved,
                        backupBlockedRelayList = latestBlockedRelayListResolved,
                        backupTrustedRelayList = latestTrustedRelayListResolved,
                        backupPrivateHomeRelayList = latestPrivateHomeRelayListResolved,
                        backupMuteList = latestMuteListResolved,
                        backupAppSpecificData = latestAppSpecificDataResolved,
                        backupChannelList = latestChannelListResolved,
                        backupCommunityList = latestCommunityListResolved,
                        backupHashtagList = latestHashtagListResolved,
                        backupGeohashList = latestGeohashListResolved,
                        backupEphemeralChatList = latestEphemeralListResolved,
                        backupRelayGroupList = latestRelayGroupListResolved,
                        backupConcordList = latestConcordListResolved,
                        backupTrustProviderList = latestTrustProviderListResolved,
                        backupKeyPackageRelayList = latestKeyPackageRelayListResolved,
                        backupFavoriteAlgoFeedsList = latestFavoriteAlgoFeedsListResolved,
                        lastReadPerRoute = MutableStateFlow(lastReadPerRouteResolved),
                        hasDonatedInVersion = MutableStateFlow(hasDonatedInVersion),
                        dismissedPollNoteIds = MutableStateFlow(dismissedPollNoteIds),
                        dismissedChannelInvites = MutableStateFlow(dismissedChannelInvites),
                        mutedPublicChats = MutableStateFlow(mutedPublicChats),
                        viewedPollResultNoteIds = MutableStateFlow(viewedPollResultNoteIdsResolved),
                        pendingAttestations = MutableStateFlow(pendingAttestationsResolved),
                        backupNipA3PaymentTargets = latestPaymentTargetsResolved,
                        backupBolt12Offers = latestBolt12OffersResolved,
                        backupCashuWallet = latestCashuWalletResolved,
                        backupNutzapInfo = latestNutzapInfoResolved,
                        callsEnabled = MutableStateFlow(callsEnabled),
                    ).also { it.restoreBackupConflicts(BackupConflictStorage.decode(openBackupConflictsStr)) }
                }
            }
        // Milestone with its cost attached. Decrypting and parsing one account's settings is one of
        // the most expensive things a cold start does (it resolves a fan of backup events), it runs
        // once per account, and "which account was slow" is the first question when a boot drags.
        // The six intermediate steps above stay at DEBUG.
        Log.i("LocalPreferences") { "Loaded account $npub in ${TimeUtils.nowMillis() - startedAtMs}ms" }
        return result
    }

    private fun parseTopFilterOrDefault(
        value: String?,
        default: TopFilter,
    ): TopFilter {
        if (value.isNullOrEmpty() || value == "null") return default
        return try {
            JsonMapper.fromJson<TopFilter>(value)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Log.w("LocalPreferences", "Error Decoding TopFilter from Preferences", e)
            default
        }
    }

    private data class FollowListPrefs(
        val home: TopFilter,
        val stories: TopFilter,
        val notification: TopFilter,
        val discovery: TopFilter,
        val polls: TopFilter,
        val pictures: TopFilter,
        val relayGroupsDiscovery: TopFilter,
        val napplets: TopFilter,
        val nsites: TopFilter,
        val workouts: TopFilter,
        val gitRepositories: TopFilter,
        val highlights: TopFilter,
        val calendars: TopFilter,
        val products: TopFilter,
        val geocaches: TopFilter,
        val shorts: TopFilter,
        val publicChats: TopFilter,
        val liveStreams: TopFilter,
        val nests: TopFilter,
        val longs: TopFilter,
        val articles: TopFilter,
        val musicTracks: TopFilter,
        val musicPlaylists: TopFilter,
        val podcastEpisodes: TopFilter,
        val podcasts: TopFilter,
        val softwareApps: TopFilter,
        val badges: TopFilter,
        val browseEmojiSets: TopFilter,
        val communities: TopFilter,
        val followPacks: TopFilter,
        val appRecommendations: TopFilter,
    )

    /**
     * One-shot migration of the notifications filter.
     *
     * The notifications "Global" mode was split into a raw [TopFilter.Global]
     * (every event that p-tags the user) and a curated [TopFilter.Selected].
     * Existing users who had selected the old, curated "Global" keep a value
     * that now deserializes to the much-more-permissive raw Global. Move them to
     * [TopFilter.Selected] exactly once, then stamp the account so a later,
     * deliberate raw-Global choice is never reverted. Accounts created after the
     * split are stamped at save time, so they are never touched here.
     */
    private fun SharedPreferences.migrateNotificationFilter(current: TopFilter): TopFilter {
        if (getBoolean(PrefKeys.NOTIF_GLOBAL_TO_CURATED_MIGRATED, false)) return current

        val migrated = if (current is TopFilter.Global) TopFilter.Selected else current
        edit {
            if (migrated !== current) {
                putString(PrefKeys.DEFAULT_NOTIFICATION_FOLLOW_LIST, JsonMapper.toJson(migrated))
            }
            putBoolean(PrefKeys.NOTIF_GLOBAL_TO_CURATED_MIGRATED, true)
        }
        return migrated
    }

    /**
     * Maps the store's slot table onto the named fields [AccountSettings]
     * still expects. `getValue` is intentional: [TopNavFollowListStore.load]
     * returns every slot, so a missing one is a bug in this mapping rather
     * than a user with no saved filter, and should fail loudly.
     */
    private fun SharedPreferences.toFollowListPrefs(filters: Map<FollowListSlot, TopFilter>): FollowListPrefs =
        FollowListPrefs(
            home = filters.getValue(FollowListSlot.HOME),
            stories = filters.getValue(FollowListSlot.STORIES),
            notification = migrateNotificationFilter(filters.getValue(FollowListSlot.NOTIFICATION)),
            discovery = filters.getValue(FollowListSlot.DISCOVERY),
            polls = filters.getValue(FollowListSlot.POLLS),
            pictures = filters.getValue(FollowListSlot.PICTURES),
            relayGroupsDiscovery = filters.getValue(FollowListSlot.RELAY_GROUPS_DISCOVERY),
            napplets = filters.getValue(FollowListSlot.NAPPLETS),
            nsites = filters.getValue(FollowListSlot.NSITES),
            workouts = filters.getValue(FollowListSlot.WORKOUTS),
            gitRepositories = filters.getValue(FollowListSlot.GIT_REPOSITORIES),
            highlights = filters.getValue(FollowListSlot.HIGHLIGHTS),
            calendars = filters.getValue(FollowListSlot.CALENDARS),
            products = filters.getValue(FollowListSlot.PRODUCTS),
            geocaches = filters.getValue(FollowListSlot.GEOCACHES),
            shorts = filters.getValue(FollowListSlot.SHORTS),
            publicChats = filters.getValue(FollowListSlot.PUBLIC_CHATS),
            liveStreams = filters.getValue(FollowListSlot.LIVE_STREAMS),
            nests = filters.getValue(FollowListSlot.NESTS),
            longs = filters.getValue(FollowListSlot.LONGS),
            articles = filters.getValue(FollowListSlot.ARTICLES),
            musicTracks = filters.getValue(FollowListSlot.MUSIC_TRACKS),
            musicPlaylists = filters.getValue(FollowListSlot.MUSIC_PLAYLISTS),
            podcastEpisodes = filters.getValue(FollowListSlot.PODCAST_EPISODES),
            podcasts = filters.getValue(FollowListSlot.PODCASTS),
            softwareApps = filters.getValue(FollowListSlot.SOFTWARE_APPS),
            badges = filters.getValue(FollowListSlot.BADGES),
            browseEmojiSets = filters.getValue(FollowListSlot.BROWSE_EMOJI_SETS),
            communities = filters.getValue(FollowListSlot.COMMUNITIES),
            followPacks = filters.getValue(FollowListSlot.FOLLOW_PACKS),
            appRecommendations = filters.getValue(FollowListSlot.APP_RECOMMENDATIONS),
        )

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
            Log.w("LocalPreferences", "Error Decoding ${T::class.simpleName} from Preferences", e)
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
            Log.w("LocalPreferences", "Error Decoding ${T::class.simpleName} from Preferences", e)
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

/**
 * The inbox / relay-auth / feed-type preferences, read as one group. Extracted out of
 * [LocalPreferences]' account-load lambda (which is right at the JVM's per-method bytecode limit)
 * so these enum/set decodes don't count against that method's budget.
 */
private class InboxPrefs(
    val defaultRelayAuthPolicy: RelayAuthPolicy,
    val relayGroupViewMode: RelayGroupViewMode,
    val concordViewMode: ConcordViewMode,
    val enabledChatFeeds: Set<ChatFeedType>,
    val enabledHomeFeedTypes: Set<HomeFeedType>,
    val relayAuthTrustMyRelays: Boolean,
    val relayAuthTrustReadFollows: Boolean,
    val relayAuthTrustMessageFollows: Boolean,
    val relayAuthTrustMessageStrangers: Boolean,
)

private fun readInboxPrefs(
    relayAuth: RelayAuth,
    feedVisibility: FeedVisibility,
) = InboxPrefs(
    // Missing key = an account saved before this setting existed. Those keep CUSTOM; only
    // brand-new logins get the ALWAYS default from AccountSettings' constructor.
    defaultRelayAuthPolicy =
        relayAuth.policyName
            ?.let { runCatching { RelayAuthPolicy.valueOf(it) }.getOrNull() }
            ?: RelayAuthPolicy.CUSTOM,
    relayGroupViewMode = RelayGroupViewMode.fromName(feedVisibility.relayGroupViewMode),
    concordViewMode = ConcordViewMode.fromName(feedVisibility.concordViewMode),
    enabledChatFeeds = ChatFeedType.ALL - ChatFeedType.decode(feedVisibility.disabledChatFeeds),
    enabledHomeFeedTypes = HomeFeedType.ALL - HomeFeedType.decode(feedVisibility.disabledHomeFeedTypes),
    relayAuthTrustMyRelays = relayAuth.trustMyRelays,
    relayAuthTrustReadFollows = relayAuth.trustReadFollows,
    relayAuthTrustMessageFollows = relayAuth.trustMessageFollows,
    relayAuthTrustMessageStrangers = relayAuth.trustMessageStrangers,
)
