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

import android.content.res.Resources
import androidx.core.os.ConfigurationCompat
import com.vitorpamplona.amethyst.commons.service.pow.PoWCategory
import com.vitorpamplona.amethyst.ui.navigation.bottombars.BottomBarEntry
import com.vitorpamplona.amethyst.ui.navigation.bottombars.DefaultBottomBarEntries
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import kotlinx.serialization.Serializable
import java.util.Locale

val DefaultReactions =
    listOf(
        "\uD83D\uDE80",
        "\uD83E\uDEC2",
        "\uD83D\uDC40",
        "\uD83D\uDE02",
        "\uD83C\uDF89",
        "\uD83E\uDD14",
        "\uD83D\uDE31",
    )

val DefaultZapAmounts = listOf(21L, 50L, 100L)
val DefaultOnchainZapAmounts = listOf(5_000L)
val DefaultReportWarningThreshold = 5

/**
 * Product floor for the on-chain rail — stricter than the protocol-level
 * dust threshold (OnchainZapBuilder.DUST_THRESHOLD_SATS). The unified zap
 * picker offers the on-chain logo only for amounts at or above this, and the
 * on-chain send dialog draws its presets from the single zap-amount list
 * filtered by it. Single source of truth for everywhere that gates on-chain
 * by amount.
 */
const val MIN_ONCHAIN_ZAP_SATS = 1_000L

/**
 * Default on-chain quick-pick amount, used when the user's unified zap-amount
 * list contains nothing at or above [MIN_ONCHAIN_ZAP_SATS]. Matches the legacy
 * dedicated on-chain default so the send dialog always offers one preset.
 */
const val DEFAULT_ONCHAIN_ZAP_SATS = 5_000L

@Serializable
enum class ReactionRowAction {
    Reply,
    Boost,
    Like,
    Zap,
    Share,
    Pay,
}

@Serializable
data class ReactionRowItem(
    val action: ReactionRowAction,
    val enabled: Boolean = true,
    val showCounter: Boolean = true,
)

val DefaultReactionRowItems =
    listOf(
        ReactionRowItem(ReactionRowAction.Reply),
        ReactionRowItem(ReactionRowAction.Boost),
        ReactionRowItem(ReactionRowAction.Like),
        ReactionRowItem(ReactionRowAction.Zap),
        ReactionRowItem(ReactionRowAction.Pay, enabled = false, showCounter = false),
        ReactionRowItem(ReactionRowAction.Share, showCounter = false),
    )

// Existing accounts have a reaction-row list serialized before some actions
// existed (e.g. Pay was added later). Append any default items the saved list
// is missing so new actions surface without forcing a settings reset — the
// user's existing order/toggles for actions they already have are preserved.
fun mergeWithDefaultReactionRowItems(saved: List<ReactionRowItem>): List<ReactionRowItem> {
    val knownActions = saved.mapTo(mutableSetOf()) { it.action }
    val missing = DefaultReactionRowItems.filter { it.action !in knownActions }
    return if (missing.isEmpty()) saved else saved + missing
}

@Serializable
enum class VideoPlayerAction {
    Fullscreen,
    Mute,
    Quality,
    Share,
    Download,
    PictureInPicture,
    Cast,
}

@Serializable
enum class VideoButtonLocation {
    TopBar,
    OverflowMenu,
}

@Serializable
data class VideoPlayerButtonItem(
    val action: VideoPlayerAction,
    val location: VideoButtonLocation = VideoButtonLocation.OverflowMenu,
)

val DefaultVideoPlayerButtonItems =
    listOf(
        VideoPlayerButtonItem(VideoPlayerAction.Fullscreen, VideoButtonLocation.TopBar),
        VideoPlayerButtonItem(VideoPlayerAction.Mute, VideoButtonLocation.TopBar),
        VideoPlayerButtonItem(VideoPlayerAction.Quality, VideoButtonLocation.TopBar),
        VideoPlayerButtonItem(VideoPlayerAction.Cast, VideoButtonLocation.TopBar),
        VideoPlayerButtonItem(VideoPlayerAction.Share, VideoButtonLocation.OverflowMenu),
        VideoPlayerButtonItem(VideoPlayerAction.Download, VideoButtonLocation.OverflowMenu),
        VideoPlayerButtonItem(VideoPlayerAction.PictureInPicture, VideoButtonLocation.OverflowMenu),
    )

// Existing accounts have a button-items list serialized before some actions
// existed (e.g. Cast was added later). Append any default items the saved list
// is missing so new actions surface without forcing a settings reset — the
// user's existing order/locations for actions they already have are preserved.
fun mergeWithDefaultVideoPlayerButtons(saved: List<VideoPlayerButtonItem>): List<VideoPlayerButtonItem> {
    val knownActions = saved.mapTo(mutableSetOf()) { it.action }
    val missing = DefaultVideoPlayerButtonItems.filter { it.action !in knownActions }
    return if (missing.isEmpty()) saved else saved + missing
}

fun getLanguagesSpokenByUser(): Set<String> {
    val languageList = ConfigurationCompat.getLocales(Resources.getSystem().getConfiguration())
    val codedList = mutableSetOf<String>()
    for (i in 0 until languageList.size()) {
        languageList.get(i)?.let { codedList.add(it.language) }
    }
    return codedList
}

@Serializable
class AccountSyncedSettingsInternal(
    val reactions: AccountReactionPreferencesInternal = AccountReactionPreferencesInternal(),
    val zaps: AccountZapPreferencesInternal = AccountZapPreferencesInternal(),
    val languages: AccountLanguagePreferencesInternal = AccountLanguagePreferencesInternal(),
    val security: AccountSecurityPreferencesInternal = AccountSecurityPreferencesInternal(),
    val videoPlayer: AccountVideoPlayerPreferencesInternal = AccountVideoPlayerPreferencesInternal(),
    val media: AccountMediaPreferencesInternal = AccountMediaPreferencesInternal(),
    val chats: AccountChatPreferencesInternal = AccountChatPreferencesInternal(),
    val proofOfWork: AccountPoWPreferencesInternal = AccountPoWPreferencesInternal(),
    val navigation: AccountNavigationPreferencesInternal = AccountNavigationPreferencesInternal(),
    /**
     * Settings that used to live only on the device. Nullable as a whole so a
     * blob written before this section existed is read as "absent", not as a
     * request to reset every one of them. See [AccountAppPreferencesInternal].
     */
    val app: AccountAppPreferencesInternal? = null,
)

/**
 * Preferences that describe the user rather than the device, carried in the
 * NIP-78 blob so they follow the user to a new phone without the old one being
 * involved. Everything here was device-only until
 * `amethyst/plans/2026-08-21-account-migration-new-phone.md`.
 *
 * EVERY FIELD IS NULLABLE, and null means "the key was absent" — not a value.
 * A client older than a given field rewrites the blob without it, and because
 * `AppSpecificState` replays the cached event on every app start, reading
 * absent as "the default" would wipe that setting on every launch. The same
 * hazard [AccountChatPreferencesInternal.mutedPublicChats] documents; the
 * decision itself lives in [mergePortableSettings], where it is testable.
 */
@Serializable
class AccountAppPreferencesInternal(
    /** Per-feed top filter, keyed by the preference-file id (`defaultHomeFollowList`, …). */
    var feedFilters: Map<String, TopFilter>? = null,
    /** DISABLED chat feed codes — absence means all-on, matching the local file. */
    var disabledChatFeeds: List<String>? = null,
    /** DISABLED home feed codes, for the same reason as [disabledChatFeeds]. */
    var disabledHomeFeedTypes: List<String>? = null,
    // The three below are enum *names*, not the enums. An id written by a newer
    // client would fail the enum decoder and take the whole blob down with it,
    // so unknown names are dropped on read instead — the approach
    // AccountNavigationPreferencesInternal.hiddenDrawerItems already takes.
    var relayGroupViewMode: String? = null,
    var concordViewMode: String? = null,
    var relayAuthPolicy: String? = null,
    var relayAuthTrustMyRelaysAndVenues: Boolean? = null,
    var relayAuthTrustReadFollows: Boolean? = null,
    var relayAuthTrustMessageFollows: Boolean? = null,
    var relayAuthTrustMessageStrangers: Boolean? = null,
    var hideDeleteRequestDialog: Boolean? = null,
    var hideBlockAlertDialog: Boolean? = null,
    var hideNip17WarningDialog: Boolean? = null,
    var hasDonatedInVersion: List<String>? = null,
    var callsEnabled: Boolean? = null,
    var splitNotificationsEnabled: Boolean? = null,
    var showMessagesInNotifications: Boolean? = null,
)

@Serializable
class AccountNavigationPreferencesInternal(
    // The ordered list of tabs pinned to the bottom navigation bar (built-ins,
    // favorite apps, and individual joined chats/groups). Defaulted so blobs
    // written before this field existed decode to the app's current defaults.
    var bottomBarItems: List<BottomBarEntry> = DefaultBottomBarEntries,
    // The drawer (side menu) rows the user switched off, as NavBarItem *names*.
    // Empty by default, which is what makes a newly shipped destination visible
    // to everyone without a migration — see DrawerItemVisibility.
    //
    // Stored as strings rather than the enum on purpose: an id written by a
    // newer client would fail the enum decoder and take the whole synced-settings
    // blob down with it, so unknown names are dropped on read instead (the same
    // approach AccountPoWPreferencesInternal.enabledCategories takes).
    var hiddenDrawerItems: List<String> = emptyList(),
)

@Serializable
class AccountVideoPlayerPreferencesInternal(
    var buttonItems: List<VideoPlayerButtonItem> = DefaultVideoPlayerButtonItems,
)

@Serializable
class AccountReactionPreferencesInternal(
    var reactionChoices: List<String> = DefaultReactions,
    var reactionRowItems: List<ReactionRowItem> = DefaultReactionRowItems,
)

@Serializable
class AccountZapPreferencesInternal(
    var zapAmountChoices: List<Long> = DefaultZapAmounts,
    // Legacy field: the on-chain rail no longer has its own editable preset
    // list — amounts live in [zapAmountChoices] and on-chain just filters by
    // [MIN_ONCHAIN_ZAP_SATS]. Kept (de)serialized so older clients still sync
    // and so the on-chain-eligible subset round-trips back for them; on load it
    // is unioned into [zapAmountChoices]. See AccountSyncedSettings.
    var onchainZapAmountChoices: List<Long> = DefaultOnchainZapAmounts,
    val defaultZapType: LnZapEvent.ZapType = LnZapEvent.ZapType.PUBLIC,
)

@Serializable
class AccountLanguagePreferencesInternal(
    var dontTranslateFrom: Set<String> = getLanguagesSpokenByUser(),
    var languagePreferences: Map<String, String> = mapOf(),
    var translateTo: String = Locale.getDefault().language,
)

@Serializable
class AccountSecurityPreferencesInternal(
    val showSensitiveContent: Boolean? = null,
    var warnAboutPostsWithReports: Boolean = true,
    val reportWarningThreshold: Int = DefaultReportWarningThreshold,
    var filterSpamFromStrangers: Boolean = true,
    val maxHashtagLimit: Int = 8,
    var sendKind0EventsToLocalRelay: Boolean = false,
    var addClientTag: Boolean = true,
)

@Serializable
class AccountMediaPreferencesInternal(
    // Stored as VisualizerStyle.name; defaults to CLASSIC (the app's classic audio animation).
    var audioVisualizer: String = "CLASSIC",
)

@Serializable
class AccountPoWPreferencesInternal(
    // NIP-13 target difficulty in leading zero bits; 0 = don't mine anything.
    val difficulty: Int = 0,
    // PoWCategory ids the user wants mined when difficulty > 0.
    val enabledCategories: List<String> = PoWCategory.DEFAULT_ENABLED.map { it.id },
)

@Serializable
class AccountChatPreferencesInternal(
    // Rooms pinned to the top of the chat list. Each room is its member
    // pubkeys (hex) sorted ascending, so the serialized form is deterministic
    // regardless of set iteration order.
    var pinnedRooms: List<List<String>> = emptyList(),
    // NIP-28 channel ids (hex) whose notifications are silenced, sorted ascending
    // for the same determinism reason as pinnedRooms.
    //
    // NULLABLE ON PURPOSE. The default has to tell two cases apart:
    //   null = key absent — an older client rewrote the blob and dropped it, so
    //          the local mute set must be left alone.
    //   []   = an explicit "unmute everything" from a client that knows the field.
    // A non-null default would collapse them and let an old client silently erase
    // the user's mutes on every launch. See updateAppSpecificData.
    var mutedPublicChats: List<String>? = null,
)
