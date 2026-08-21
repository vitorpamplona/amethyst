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

import com.vitorpamplona.amethyst.commons.model.concord.ConcordViewMode
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupViewMode
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPolicy
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * The slice of [AccountSettings] that lives on the device today but describes a
 * *preference*, not a device — so it should follow the user to a new phone.
 *
 * These settings ride the NIP-78 `AmethystSettings` blob alongside
 * [AccountSyncedSettings]. They stay declared on [AccountSettings] rather than
 * moving into the synced object because they are also read on cold start,
 * before (or without) the blob having been decrypted, and because that keeps
 * every existing read site untouched.
 *
 * Deliberately NOT here: anything that describes the hardware or the install
 * rather than the person — the always-on notification service opt-in, Tor
 * settings, the local relay address, wallet connections. Those travel in the
 * encrypted transfer file instead. See
 * `amethyst/plans/2026-08-21-account-migration-new-phone.md`.
 */
data class PortableAccountSettings(
    /**
     * Per-feed top filter, keyed by the same stable id the preference file uses
     * (`defaultHomeFollowList`, …). A map rather than 30 named fields so that
     * adding a feed doesn't need a new field on the wire, and so a client that
     * doesn't know a feed round-trips its entry instead of dropping it.
     */
    val feedFilters: Map<String, TopFilter>,
    val mutedPublicChats: Set<HexKey>,
    /**
     * Stores the DISABLED codes, matching the preference file's convention:
     * absence means all-on, so a feed type added later defaults to enabled for
     * accounts that customized before it existed.
     */
    val disabledChatFeeds: Set<String>,
    /** Disabled (not enabled) codes, for the same reason as [disabledChatFeeds]. */
    val disabledHomeFeedTypes: Set<String>,
    val relayGroupViewMode: RelayGroupViewMode,
    val concordViewMode: ConcordViewMode,
    val relayAuthPolicy: RelayAuthPolicy,
    val relayAuthTrustMyRelaysAndVenues: Boolean,
    val relayAuthTrustReadFollows: Boolean,
    val relayAuthTrustMessageFollows: Boolean,
    val relayAuthTrustMessageStrangers: Boolean,
    val hideDeleteRequestDialog: Boolean,
    val hideBlockAlertDialog: Boolean,
    val hideNip17WarningDialog: Boolean,
    val hasDonatedInVersion: Set<String>,
    val callsEnabled: Boolean,
    val splitNotificationsEnabled: Boolean,
    val showMessagesInNotifications: Boolean,
)

/** Serializes for the NIP-78 blob. Every field is written; absence only ever means "older client". */
fun PortableAccountSettings.toInternal() =
    AccountAppPreferencesInternal(
        feedFilters = feedFilters,
        // sorted so the serialized form is deterministic and identical settings
        // don't produce a differing event on every save
        disabledChatFeeds = disabledChatFeeds.sorted(),
        disabledHomeFeedTypes = disabledHomeFeedTypes.sorted(),
        relayGroupViewMode = relayGroupViewMode.name,
        concordViewMode = concordViewMode.name,
        relayAuthPolicy = relayAuthPolicy.name,
        relayAuthTrustMyRelaysAndVenues = relayAuthTrustMyRelaysAndVenues,
        relayAuthTrustReadFollows = relayAuthTrustReadFollows,
        relayAuthTrustMessageFollows = relayAuthTrustMessageFollows,
        relayAuthTrustMessageStrangers = relayAuthTrustMessageStrangers,
        hideDeleteRequestDialog = hideDeleteRequestDialog,
        hideBlockAlertDialog = hideBlockAlertDialog,
        hideNip17WarningDialog = hideNip17WarningDialog,
        hasDonatedInVersion = hasDonatedInVersion.sorted(),
        callsEnabled = callsEnabled,
        splitNotificationsEnabled = splitNotificationsEnabled,
        showMessagesInNotifications = showMessagesInNotifications,
    )

/**
 * The inbound-sync decision for the portable settings, kept out of
 * [AccountSettings] so it can be unit-tested — that class builds a default
 * `AccountSyncedSettingsInternal`, whose language preferences call
 * `Resources.getSystem()`, so it cannot be constructed in a JVM test. Same
 * reason [mergeMutedPublicChats] lives on its own.
 *
 * Every field of [remote] is nullable and `null` means *the key was absent*,
 * which happens whenever a client older than that field rewrites the blob. The
 * local value has to survive that: `AppSpecificState` replays the cached backup
 * event on every app start, so treating absent as "reset to default" would undo
 * the user's settings on every single launch.
 */
fun mergePortableSettings(
    local: PortableAccountSettings,
    remote: AccountAppPreferencesInternal?,
): PortableAccountSettings {
    if (remote == null) return local

    return local.copy(
        // Merged per key, not replaced: a client that predates a feed writes the
        // blob without that feed's entry, and the entry must not be lost.
        feedFilters = remote.feedFilters?.let { local.feedFilters + it } ?: local.feedFilters,
        disabledChatFeeds = remote.disabledChatFeeds?.toSet() ?: local.disabledChatFeeds,
        disabledHomeFeedTypes = remote.disabledHomeFeedTypes?.toSet() ?: local.disabledHomeFeedTypes,
        // Matched by name rather than through the enums' own fromName(), which
        // falls back to the DEFAULT constant. A value this build doesn't know is
        // one a newer client wrote, and keeping the local setting is closer to
        // the user's intent than resetting them to the default.
        relayGroupViewMode = RelayGroupViewMode.entries.firstOrNull { it.name == remote.relayGroupViewMode } ?: local.relayGroupViewMode,
        concordViewMode = ConcordViewMode.entries.firstOrNull { it.name == remote.concordViewMode } ?: local.concordViewMode,
        relayAuthPolicy = RelayAuthPolicy.entries.firstOrNull { it.name == remote.relayAuthPolicy } ?: local.relayAuthPolicy,
        relayAuthTrustMyRelaysAndVenues = remote.relayAuthTrustMyRelaysAndVenues ?: local.relayAuthTrustMyRelaysAndVenues,
        relayAuthTrustReadFollows = remote.relayAuthTrustReadFollows ?: local.relayAuthTrustReadFollows,
        relayAuthTrustMessageFollows = remote.relayAuthTrustMessageFollows ?: local.relayAuthTrustMessageFollows,
        relayAuthTrustMessageStrangers = remote.relayAuthTrustMessageStrangers ?: local.relayAuthTrustMessageStrangers,
        hideDeleteRequestDialog = remote.hideDeleteRequestDialog ?: local.hideDeleteRequestDialog,
        hideBlockAlertDialog = remote.hideBlockAlertDialog ?: local.hideBlockAlertDialog,
        hideNip17WarningDialog = remote.hideNip17WarningDialog ?: local.hideNip17WarningDialog,
        // Unioned, not replaced: a donation made on either phone is a fact about
        // the user, and forgetting one puts the donation nag back in front of a
        // supporter.
        hasDonatedInVersion = remote.hasDonatedInVersion?.let { local.hasDonatedInVersion + it } ?: local.hasDonatedInVersion,
        callsEnabled = remote.callsEnabled ?: local.callsEnabled,
        splitNotificationsEnabled = remote.splitNotificationsEnabled ?: local.splitNotificationsEnabled,
        showMessagesInNotifications = remote.showMessagesInNotifications ?: local.showMessagesInNotifications,
    )
}

/**
 * Two filter selections are the same when their [TopFilter.code]s match.
 *
 * The addressable subclasses (`PeopleList`, `Community`, …) are plain classes
 * with no `equals`, so `==` compares identity and would report a change on
 * every decode of the blob — republishing the settings event in a loop.
 */
fun sameFilters(
    a: Map<String, TopFilter>,
    b: Map<String, TopFilter>,
): Boolean {
    if (a.size != b.size) return false
    return a.all { (key, filter) -> b[key]?.code == filter.code }
}

/**
 * The per-feed filter flows, keyed by the id used both in the preference file
 * and in the synced blob. One table so the wire format, the local file and the
 * transfer bundle can never disagree about which feeds exist.
 */
fun AccountSettings.feedFilterFlows() =
    mapOf(
        "defaultHomeFollowList" to defaultHomeFollowList,
        "defaultStoriesFollowList" to defaultStoriesFollowList,
        "defaultNotificationFollowList" to defaultNotificationFollowList,
        "defaultDiscoveryFollowList" to defaultDiscoveryFollowList,
        "defaultPollsFollowList" to defaultPollsFollowList,
        "defaultPicturesFollowList" to defaultPicturesFollowList,
        "defaultNappletsFollowList" to defaultNappletsFollowList,
        "defaultNsitesFollowList" to defaultNsitesFollowList,
        "defaultWorkoutsFollowList" to defaultWorkoutsFollowList,
        "defaultGitRepositoriesFollowList" to defaultGitRepositoriesFollowList,
        "defaultHighlightsFollowList" to defaultHighlightsFollowList,
        "defaultCalendarsFollowList" to defaultCalendarsFollowList,
        "defaultProductsFollowList" to defaultProductsFollowList,
        "defaultShortsFollowList" to defaultShortsFollowList,
        "defaultPublicChatsFollowList" to defaultPublicChatsFollowList,
        "defaultLiveStreamsFollowList" to defaultLiveStreamsFollowList,
        "defaultNestsFollowList" to defaultNestsFollowList,
        "defaultLongsFollowList" to defaultLongsFollowList,
        "defaultArticlesFollowList" to defaultArticlesFollowList,
        "defaultMusicTracksFollowList" to defaultMusicTracksFollowList,
        "defaultMusicPlaylistsFollowList" to defaultMusicPlaylistsFollowList,
        "defaultPodcastEpisodesFollowList" to defaultPodcastEpisodesFollowList,
        "defaultPodcastsFollowList" to defaultPodcastsFollowList,
        "defaultSoftwareAppsFollowList" to defaultSoftwareAppsFollowList,
        "defaultBadgesFollowList" to defaultBadgesFollowList,
        "defaultBrowseEmojiSetsFollowList" to defaultBrowseEmojiSetsFollowList,
        "defaultCommunitiesFollowList" to defaultCommunitiesFollowList,
        "defaultFollowPacksFollowList" to defaultFollowPacksFollowList,
        "defaultAppRecommendationsFollowList" to defaultAppRecommendationsFollowList,
        "defaultRelayGroupsDiscoveryFollowList" to defaultRelayGroupsDiscoveryFollowList,
    )
