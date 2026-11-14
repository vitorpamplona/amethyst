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
package com.vitorpamplona.amethyst.commons.settings.registry

import com.vitorpamplona.amethyst.commons.model.AccentColorType
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.BooleanType
import com.vitorpamplona.amethyst.commons.model.ChatLayoutType
import com.vitorpamplona.amethyst.commons.model.ConnectivityType
import com.vitorpamplona.amethyst.commons.model.FeatureSetType
import com.vitorpamplona.amethyst.commons.model.FontFamilyType
import com.vitorpamplona.amethyst.commons.model.FontSizeType
import com.vitorpamplona.amethyst.commons.model.NavigationLayoutType
import com.vitorpamplona.amethyst.commons.model.NoteLayoutType
import com.vitorpamplona.amethyst.commons.model.ProfileGalleryType
import com.vitorpamplona.amethyst.commons.model.SpellCheckType
import com.vitorpamplona.amethyst.commons.model.ThemeType
import com.vitorpamplona.amethyst.commons.model.UiSettingsFlow
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Amethyst's settings, for the settings assistant. App settings write the same flows the
 * settings screens write (the app saves them); account settings go through [AccountSettings]'
 * own setters, which save and sync.
 *
 * Left out on purpose, so no assistant can read or change them: keys, the signer and its
 * secrets, wallet connections, payment targets, relay lists and anything else that moves
 * money, identity or where the user's events go.
 */
object AmethystSettings {
    fun registry(
        ui: UiSettingsFlow,
        account: AccountSettings?,
    ): SettingsRegistry = SettingsRegistry(appSettings(ui) + (account?.let(::accountSettings) ?: emptyList()))

    private val CONNECTIVITY =
        mapOf(
            ConnectivityType.ALWAYS to "always",
            ConnectivityType.WIFI_ONLY to "only on Wi-Fi or other unmetered connections",
            ConnectivityType.NEVER to "never",
        )

    private fun appSettings(ui: UiSettingsFlow): List<SettingDefinition> =
        listOf(
            choice(
                "app.theme",
                "Theme",
                "Light or dark colors for the whole app, or follow the system.",
                ui.theme,
                mapOf(ThemeType.SYSTEM to "follow the system", ThemeType.LIGHT to "always light", ThemeType.DARK to "always dark"),
                Route.Settings,
                listOf("dark mode", "light mode", "night", "appearance", "colors"),
            ),
            choice(
                "app.accent_color",
                "Accent color",
                "The color of buttons, links and highlights.",
                ui.accentColor,
                AccentColorType.entries.associateWith { it.name.lowercase() },
                Route.AccentColorSettings,
                listOf("color", "appearance"),
            ),
            choice(
                "app.font_size",
                "Text size",
                "Scales the text size across the app.",
                ui.fontSize,
                mapOf(FontSizeType.SMALL to "smaller", FontSizeType.NORMAL to "default", FontSizeType.LARGE to "larger", FontSizeType.HUGE to "largest"),
                Route.Settings,
                listOf("font", "bigger", "smaller", "accessibility", "read"),
            ),
            choice(
                "app.font_family",
                "Typeface",
                "The font used throughout the app.",
                ui.fontFamily,
                FontFamilyType.entries.associateWith { it.name.lowercase().replace('_', ' ') },
                Route.Settings,
                listOf("font"),
            ),
            choice(
                "app.feature_set",
                "Interface style",
                "How much the interface shows: everything, a simpler feed, or a lighter one for slow devices.",
                ui.featureSet,
                mapOf(
                    FeatureSetType.COMPLETE to "full: every action and detail",
                    FeatureSetType.SIMPLIFIED to "simple: fewer buttons and details on each post",
                    FeatureSetType.PERFORMANCE to "fast: the lightest, for older or slower devices",
                ),
                Route.Settings,
                listOf("simple", "complete", "performance", "fast", "clutter"),
            ),
            choice(
                "app.note_layout",
                "Post layout",
                "Indent posts past the author's picture, put the picture above so posts use the full width, or also run images and videos to the screen edges.",
                ui.noteLayout,
                mapOf(
                    NoteLayoutType.AUTHOR_COLUMN to "text indented beside the author's picture",
                    NoteLayoutType.FULL_WIDTH to "picture above, posts use the full width",
                    NoteLayoutType.EDGE_TO_EDGE to "full width, with images and videos to the screen edges",
                ),
                Route.Settings,
                listOf("feed", "posts", "width", "layout"),
            ),
            choice(
                "app.chat_layout",
                "Chat layout",
                "How chat rooms draw messages.",
                ui.chatLayout,
                mapOf(
                    ChatLayoutType.BUBBLES to "messenger bubbles",
                    ChatLayoutType.COMPACT to "compact one-line messages, like IRC",
                    ChatLayoutType.OPEN to "open full-width replies, only your own messages in bubbles",
                ),
                Route.Settings,
                listOf("messages", "bubbles", "irc"),
            ),
            choice(
                "app.navigation_layout",
                "Navigation layout",
                "How screens open on wide windows: one stack, deck columns beside it, each in its own tab, or each in its own window. Tabs and windows have no back button: they are closed instead.",
                ui.navigationLayout,
                mapOf(
                    NavigationLayoutType.STACK to "one stack, with a back button",
                    NavigationLayoutType.DECK to "deck columns beside the main screen on wide windows",
                    NavigationLayoutType.TABS to "each screen in its own tab",
                    NavigationLayoutType.WINDOWS to "each screen in its own window",
                ),
                Route.Settings,
                listOf("deck", "tabs", "windows", "columns"),
            ),
            choice(
                "app.profile_gallery",
                "Profile gallery style",
                "How the media gallery on profiles looks.",
                ui.gallerySet,
                mapOf(ProfileGalleryType.CLASSIC to "classic", ProfileGalleryType.MODERN to "modern"),
                Route.ProfileUiSettings,
                listOf("gallery", "profile", "media"),
            ),
            choice(
                "app.load_images",
                "Load images automatically",
                "When images and GIFs in posts load without a tap.",
                ui.automaticallyShowImages,
                CONNECTIVITY,
                Route.Settings,
                listOf("images", "pictures", "data", "gif", "mobile data"),
            ),
            choice(
                "app.start_playback",
                "Start videos automatically",
                "When videos start playing without a tap.",
                ui.automaticallyStartPlayback,
                CONNECTIVITY,
                Route.Settings,
                listOf("video", "autoplay", "data", "mobile data"),
            ),
            choice(
                "app.url_previews",
                "Link previews",
                "When links in posts show a preview card with the page's title and image.",
                ui.automaticallyShowUrlPreview,
                CONNECTIVITY,
                Route.Settings,
                listOf("links", "url", "preview", "data"),
            ),
            choice(
                "app.profile_pictures",
                "Profile pictures",
                "When people's profile pictures load.",
                ui.automaticallyShowProfilePictures,
                CONNECTIVITY,
                Route.Settings,
                listOf("avatars", "pictures", "data"),
            ),
            choice(
                "app.translation_models",
                "Download translation models",
                "When the on-device translator may download a language model (about 30 MB each) to translate posts.",
                ui.automaticallyDownloadTranslationModels,
                CONNECTIVITY,
                Route.Settings,
                listOf("translate", "translation", "language", "data"),
            ),
            onOffType(
                "app.play_videos",
                "Load videos",
                "Whether videos and GIFs load in posts.",
                ui.automaticallyPlayVideos,
                Route.Settings,
                listOf("video", "gif"),
            ),
            onOffType(
                "app.hide_nav_bars",
                "Hide bars when scrolling",
                "Hides the top and bottom bars while scrolling a feed, to show more of it.",
                ui.automaticallyHideNavigationBars,
                Route.Settings,
                listOf("navigation", "bars", "scroll", "fullscreen"),
            ),
            onOffType(
                "app.drafts",
                "Save drafts",
                "Saves what is being written as a draft, so it isn't lost.",
                ui.automaticallyCreateDrafts,
                Route.ComposeSettings,
                listOf("drafts", "compose", "writing"),
            ),
            onOffType(
                "app.ai_writing",
                "Propose text improvements",
                "Uses an on-device AI model to propose corrections and tone changes while writing.",
                ui.automaticallyProposeAiImprovements,
                Route.ComposeSettings,
                listOf("ai", "grammar", "writing", "compose"),
            ),
            onOffType(
                "app.tracked_broadcasts",
                "Tracked broadcasts",
                "Shows live progress and each relay's status while a post is being sent.",
                ui.useTrackedBroadcasts,
                Route.ComposeSettings,
                listOf("broadcast", "sending", "relays", "progress"),
            ),
            choice(
                "app.spell_check",
                "Spell checking",
                "Underlines misspelled words in posts and messages.",
                ui.spellCheck,
                mapOf(
                    SpellCheckType.AUTOMATIC to "the system's spell checker when it can, the built-in English dictionary otherwise",
                    SpellCheckType.BUILT_IN to "the built-in English dictionary",
                    SpellCheckType.OFF to "off",
                ),
                Route.ComposeSettings,
                listOf("spelling", "typos", "dictionary"),
            ),
            text(
                "app.signature",
                "Signature",
                "Text added at the end of new posts, replies, quotes and articles. Empty turns it off.",
                ui.composeSignature,
                Route.ComposeSettings,
                listOf("signature", "footer", "compose"),
                maxLength = 280,
            ),
            onOff(
                "app.home_new_threads_tab",
                "New Threads tab",
                "Shows the New Threads tab on the home screen: new posts from people the user follows.",
                ui.showHomeNewThreadsTab,
                Route.HomeTabsSettings,
                listOf("home", "tabs", "feed"),
            ),
            onOff(
                "app.home_conversations_tab",
                "Conversations tab",
                "Shows the Conversations tab on the home screen: replies and threads.",
                ui.showHomeConversationsTab,
                Route.HomeTabsSettings,
                listOf("home", "tabs", "replies", "feed"),
            ),
            onOff(
                "app.home_everything_tab",
                "Everything tab",
                "Shows the Everything tab on the home screen: posts and replies together.",
                ui.showHomeEverythingTab,
                Route.HomeTabsSettings,
                listOf("home", "tabs", "feed", "all"),
            ),
            onOff(
                "app.profile_badges",
                "Badges on profiles",
                "Shows badges people have been awarded on their profiles.",
                ui.showProfileBadges,
                Route.ProfileUiSettings,
                listOf("badges", "profile"),
            ),
            onOff(
                "app.profile_app_recommendations",
                "App recommendations on profiles",
                "Shows the apps a person recommends on their profile.",
                ui.showProfileAppRecommendations,
                Route.ProfileUiSettings,
                listOf("apps", "profile"),
            ),
            onOff(
                "app.profile_zaps_feed",
                "Zaps tab on profiles",
                "Shows the zaps a person received on their profile.",
                ui.showProfileZapReceivedFeed,
                Route.ProfileUiSettings,
                listOf("zaps", "profile", "tips"),
            ),
            onOff(
                "app.profile_followers_feed",
                "Followers tab on profiles",
                "Shows who follows a person on their profile.",
                ui.showProfileFollowersFeed,
                Route.ProfileUiSettings,
                listOf("followers", "profile"),
            ),
            onOff(
                "app.onchain_wallet",
                "Bitcoin on-chain wallet",
                "Shows the on-chain Bitcoin wallet: its card on the wallet screen, the on-chain chip on profiles and the on-chain option when sending.",
                ui.showOnchainWallet,
                Route.ProfileUiSettings,
                listOf("bitcoin", "onchain", "wallet", "taproot"),
            ),
            onOff(
                "app.pay_to_zap_chip",
                "Pay-to chip when zapping",
                "Offers a pay-to hand-off when the sender and recipient share a payment method. Those can be bank or Venmo handles with legal names.",
                ui.showPayToZapChip,
                Route.ProfileUiSettings,
                listOf("zap", "payment", "pay"),
                sensitivity = SettingSensitivity.CAREFUL,
            ),
            onOff(
                "app.hide_wallet_balances",
                "Hide wallet balances",
                "Hides balances on the wallet screens, for using the app in public.",
                ui.hideWalletBalances,
                null,
                listOf("wallet", "balance", "privacy"),
            ),
        )

    private fun accountSettings(account: AccountSettings): List<SettingDefinition> =
        listOf(
            accountOnOff(
                "account.notifications_messages",
                "Messages in notifications",
                "Includes direct and group messages on the Notifications tab. Off keeps messages only in the Messages tab.",
                { account.showMessagesInNotifications.value },
                { if (it != account.showMessagesInNotifications.value) account.toggleShowMessagesInNotifications() },
                Route.NotificationSettings,
                listOf("notifications", "messages", "dm"),
            ),
            accountOnOff(
                "account.notifications_split",
                "Split notifications by follows",
                "Shows two notification tabs, Following (people the user follows) and Everyone. The unread dot then glows only for people the user follows.",
                { account.splitNotificationsEnabled.value },
                { if (it != account.splitNotificationsEnabled.value) account.toggleSplitNotificationsEnabled() },
                Route.NotificationSettings,
                listOf("notifications", "follows", "spam", "noise"),
            ),
            accountOnOff(
                "account.calls",
                "Calls",
                "Lets people call the user over audio and video. Off rejects calls.",
                { account.callsEnabled.value },
                account::changeCallsEnabled,
                Route.CallSettings,
                listOf("calls", "video", "audio", "phone"),
                sensitivity = SettingSensitivity.CAREFUL,
            ),
            accountOnOff(
                "account.video_captions",
                "Video captions",
                "Shows captions on videos that have them.",
                { account.syncedSettings.videoPlayer.captionsEnabled.value },
                { account.changeCaptionsEnabled(it) },
                Route.VideoPlayerSettings,
                listOf("captions", "subtitles", "video", "accessibility"),
            ),
            accountOnOff(
                "account.hide_community_rule_breaks",
                "Hide posts that break community rules",
                "Drops posts from a community's feed when the community publishes structured rules and the post breaks them. No effect on communities without such rules.",
                { account.hideCommunityRulesViolations.value },
                account::changeHideCommunityRulesViolations,
                null,
                listOf("communities", "moderation", "rules"),
            ),
            accountOnOff(
                "account.upload_strip_location",
                "Remove location from uploads",
                "Removes GPS location and similar metadata from photos and videos before they are uploaded.",
                { account.stripLocationOnUpload },
                account::changeStripLocationOnUpload,
                Route.EditMediaServers,
                listOf("privacy", "location", "gps", "exif", "metadata", "upload"),
                sensitivity = SettingSensitivity.CAREFUL,
            ),
            accountOnOff(
                "account.upload_optimize",
                "Optimize media before upload",
                "Compresses photos and videos before uploading them, to save data and space.",
                { account.optimizeMediaOnUpload.value },
                account::changeOptimizeMediaOnUpload,
                Route.EditMediaServers,
                listOf("upload", "compress", "quality", "data"),
            ),
            accountOnOff(
                "account.upload_mirror",
                "Mirror uploads to all servers",
                "Copies every upload to all of the user's media servers, so files stay up if one server goes down.",
                { account.mirrorUploadsToAllServers.value },
                account::changeMirrorUploadsToAllServers,
                Route.EditMediaServers,
                listOf("upload", "blossom", "media servers", "backup"),
            ),
            accountOnOff(
                "account.local_media_cache",
                "Local media cache",
                "Keeps a local copy of media the app loads, so it loads faster the next time.",
                { account.useLocalBlossomCache.value },
                account::changeUseLocalBlossomCache,
                Route.EditMediaServers,
                listOf("cache", "media", "storage", "blossom"),
            ),
            accountOnOff(
                "account.relay_login_my_relays",
                "Log in to my own relays without asking",
                "When one of the user's own relays, or a relay hosting a group they are in, asks who they are, log in without asking. Logging in tells the relay which account this is.",
                { account.relayAuthTrustMyRelaysAndVenues.value },
                account::changeRelayAuthTrustMyRelaysAndVenues,
                Route.RelayAuthSettings,
                listOf("relays", "auth", "login", "privacy"),
                sensitivity = SettingSensitivity.CAREFUL,
            ),
            accountOnOff(
                "account.relay_login_read_follows",
                "Log in to read people I follow",
                "When a relay asks who the user is before serving posts from people they follow, log in without asking.",
                { account.relayAuthTrustReadFollows.value },
                account::changeRelayAuthTrustReadFollows,
                Route.RelayAuthSettings,
                listOf("relays", "auth", "login", "privacy"),
                sensitivity = SettingSensitivity.CAREFUL,
            ),
            accountOnOff(
                "account.relay_login_message_follows",
                "Log in to message people I follow",
                "When a relay asks who the user is before accepting a message to someone they follow, log in without asking.",
                { account.relayAuthTrustMessageFollows.value },
                account::changeRelayAuthTrustMessageFollows,
                Route.RelayAuthSettings,
                listOf("relays", "auth", "login", "privacy", "messages"),
                sensitivity = SettingSensitivity.CAREFUL,
            ),
            accountOnOff(
                "account.relay_login_message_strangers",
                "Log in to message people I don't follow",
                "When a relay asks who the user is before accepting a message to someone they don't follow, log in without asking.",
                { account.relayAuthTrustMessageStrangers.value },
                account::changeRelayAuthTrustMessageStrangers,
                Route.RelayAuthSettings,
                listOf("relays", "auth", "login", "privacy", "messages", "strangers"),
                sensitivity = SettingSensitivity.CAREFUL,
            ),
        )

    private fun <E : Enum<E>> choice(
        id: String,
        title: String,
        description: String,
        flow: MutableStateFlow<E>,
        meanings: Map<E, String>,
        route: Route?,
        keywords: List<String>,
    ): SettingDefinition {
        val values = meanings.keys.toList()
        return SettingDefinition(
            id = id,
            title = title,
            description = description,
            scope = SettingScope.APP,
            type = SettingType.Choice(values.map { SettingOption(it.name, meanings.getValue(it)) }),
            route = route,
            keywords = keywords,
            read = { flow.value.name },
            write = { name -> values.firstOrNull { it.name == name }?.let { flow.value = it } },
        )
    }

    private fun onOff(
        id: String,
        title: String,
        description: String,
        flow: MutableStateFlow<Boolean>,
        route: Route?,
        keywords: List<String>,
        sensitivity: SettingSensitivity = SettingSensitivity.NORMAL,
    ) = SettingDefinition(id, title, description, SettingScope.APP, SettingType.OnOff, route, sensitivity, keywords, { flow.value.toString() }, { flow.value = it == "true" })

    /** A [BooleanType] flow, shown as on/off. */
    private fun onOffType(
        id: String,
        title: String,
        description: String,
        flow: MutableStateFlow<BooleanType>,
        route: Route?,
        keywords: List<String>,
    ) = SettingDefinition(
        id,
        title,
        description,
        SettingScope.APP,
        SettingType.OnOff,
        route,
        SettingSensitivity.NORMAL,
        keywords,
        { (flow.value == BooleanType.ALWAYS).toString() },
        { flow.value = if (it == "true") BooleanType.ALWAYS else BooleanType.NEVER },
    )

    private fun text(
        id: String,
        title: String,
        description: String,
        flow: MutableStateFlow<String>,
        route: Route?,
        keywords: List<String>,
        maxLength: Int,
    ) = SettingDefinition(id, title, description, SettingScope.APP, SettingType.Text(maxLength), route, SettingSensitivity.NORMAL, keywords, { flow.value }, { flow.value = it })

    private fun accountOnOff(
        id: String,
        title: String,
        description: String,
        read: () -> Boolean,
        write: (Boolean) -> Unit,
        route: Route?,
        keywords: List<String>,
        sensitivity: SettingSensitivity = SettingSensitivity.NORMAL,
    ) = SettingDefinition(id, title, description, SettingScope.ACCOUNT, SettingType.OnOff, route, sensitivity, keywords, { read().toString() }, { write(it == "true") })
}
