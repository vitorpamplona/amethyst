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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.settings

import androidx.compose.ui.platform.UriHandler
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.account_settings
import com.vitorpamplona.amethyst.commons.resources.active_subs_search_keywords
import com.vitorpamplona.amethyst.commons.resources.active_subs_title
import com.vitorpamplona.amethyst.commons.resources.app_settings
import com.vitorpamplona.amethyst.commons.resources.audio_visualizer_search_keywords
import com.vitorpamplona.amethyst.commons.resources.audio_visualizer_settings
import com.vitorpamplona.amethyst.commons.resources.backup_keys
import com.vitorpamplona.amethyst.commons.resources.backup_keys_search_keywords
import com.vitorpamplona.amethyst.commons.resources.bolt12_offers
import com.vitorpamplona.amethyst.commons.resources.bolt12_offers_search_keywords
import com.vitorpamplona.amethyst.commons.resources.bottom_bar_search_keywords
import com.vitorpamplona.amethyst.commons.resources.bottom_bar_settings
import com.vitorpamplona.amethyst.commons.resources.calendar_reminder_search_keywords
import com.vitorpamplona.amethyst.commons.resources.calendar_reminder_settings_title
import com.vitorpamplona.amethyst.commons.resources.call_settings
import com.vitorpamplona.amethyst.commons.resources.call_settings_search_keywords
import com.vitorpamplona.amethyst.commons.resources.compose_search_keywords
import com.vitorpamplona.amethyst.commons.resources.compose_settings
import com.vitorpamplona.amethyst.commons.resources.danger_zone
import com.vitorpamplona.amethyst.commons.resources.drawer_search_keywords
import com.vitorpamplona.amethyst.commons.resources.drawer_settings
import com.vitorpamplona.amethyst.commons.resources.event_sync_search_keywords
import com.vitorpamplona.amethyst.commons.resources.event_sync_title
import com.vitorpamplona.amethyst.commons.resources.favorite_dvms_search_keywords
import com.vitorpamplona.amethyst.commons.resources.favorite_dvms_title
import com.vitorpamplona.amethyst.commons.resources.home_tabs_search_keywords
import com.vitorpamplona.amethyst.commons.resources.import_follows_search_keywords
import com.vitorpamplona.amethyst.commons.resources.media_servers
import com.vitorpamplona.amethyst.commons.resources.media_servers_search_keywords
import com.vitorpamplona.amethyst.commons.resources.messages_settings
import com.vitorpamplona.amethyst.commons.resources.messages_settings_search_keywords
import com.vitorpamplona.amethyst.commons.resources.namecoin_search_keywords
import com.vitorpamplona.amethyst.commons.resources.namecoin_settings
import com.vitorpamplona.amethyst.commons.resources.napplet_connected_apps_search_keywords
import com.vitorpamplona.amethyst.commons.resources.napplet_permissions_title
import com.vitorpamplona.amethyst.commons.resources.nests_servers_search_keywords
import com.vitorpamplona.amethyst.commons.resources.nests_servers_title
import com.vitorpamplona.amethyst.commons.resources.notification_settings
import com.vitorpamplona.amethyst.commons.resources.notification_settings_search_keywords
import com.vitorpamplona.amethyst.commons.resources.ots_explorer_search_keywords
import com.vitorpamplona.amethyst.commons.resources.ots_explorer_settings
import com.vitorpamplona.amethyst.commons.resources.payment_targets
import com.vitorpamplona.amethyst.commons.resources.payment_targets_search_keywords
import com.vitorpamplona.amethyst.commons.resources.privacy_options
import com.vitorpamplona.amethyst.commons.resources.privacy_options_search_keywords
import com.vitorpamplona.amethyst.commons.resources.profile_badges_search_keywords
import com.vitorpamplona.amethyst.commons.resources.profile_badges_title
import com.vitorpamplona.amethyst.commons.resources.profile_ui_search_keywords
import com.vitorpamplona.amethyst.commons.resources.profile_ui_settings
import com.vitorpamplona.amethyst.commons.resources.reactions
import com.vitorpamplona.amethyst.commons.resources.reactions_search_keywords
import com.vitorpamplona.amethyst.commons.resources.reactions_settings
import com.vitorpamplona.amethyst.commons.resources.reactions_settings_search_keywords
import com.vitorpamplona.amethyst.commons.resources.relay_auth_search_keywords
import com.vitorpamplona.amethyst.commons.resources.relay_auth_settings_title
import com.vitorpamplona.amethyst.commons.resources.relay_setup
import com.vitorpamplona.amethyst.commons.resources.relay_setup_search_keywords
import com.vitorpamplona.amethyst.commons.resources.request_to_vanish
import com.vitorpamplona.amethyst.commons.resources.request_to_vanish_search_keywords
import com.vitorpamplona.amethyst.commons.resources.reset_marmot_search_keywords
import com.vitorpamplona.amethyst.commons.resources.reset_marmot_state
import com.vitorpamplona.amethyst.commons.resources.resource_usage_search_keywords
import com.vitorpamplona.amethyst.commons.resources.resource_usage_title
import com.vitorpamplona.amethyst.commons.resources.route_home
import com.vitorpamplona.amethyst.commons.resources.route_import_follows
import com.vitorpamplona.amethyst.commons.resources.security_filters
import com.vitorpamplona.amethyst.commons.resources.security_filters_search_keywords
import com.vitorpamplona.amethyst.commons.resources.translations
import com.vitorpamplona.amethyst.commons.resources.translations_search_keywords
import com.vitorpamplona.amethyst.commons.resources.ui_preferences
import com.vitorpamplona.amethyst.commons.resources.ui_preferences_search_keywords
import com.vitorpamplona.amethyst.commons.resources.vanish_history
import com.vitorpamplona.amethyst.commons.resources.vanish_history_search_keywords
import com.vitorpamplona.amethyst.commons.resources.video_player_search_keywords
import com.vitorpamplona.amethyst.commons.resources.video_player_settings
import com.vitorpamplona.amethyst.commons.resources.zaps
import com.vitorpamplona.amethyst.commons.resources.zaps_search_keywords
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.routes.Route
import org.jetbrains.compose.resources.StringResource

/**
 * Assembles the full settings catalog. Not composable: actions close over [nav],
 * [uriHandler], and [onResetMarmot]; conditional rows are included via [hasPrivateKey].
 * The blank-query render of this catalog must match the legacy hardcoded screen.
 */
fun buildSettingsCatalog(
    nav: INav,
    uriHandler: UriHandler,
    hasPrivateKey: Boolean,
    onResetMarmot: () -> Unit,
): List<SettingsCategory> {
    // Most rows are a symbol icon + a keyword blob that navigates to a route. This local
    // helper collapses that shape to one line per row and makes a mismatched keyword/route
    // obvious. Painter-icon rows and the danger rows below are spelled out explicitly.
    fun symEntry(
        titleRes: StringResource,
        symbol: MaterialSymbol,
        keywordsRes: StringResource,
        route: Route,
    ) = SettingsEntry(
        titleRes = titleRes,
        icon = SettingsIcon.Symbol(symbol),
        keywordsRes = keywordsRes,
    ) { nav.nav(route) }

    val account =
        SettingsCategory(
            titleRes = Res.string.account_settings,
            entries =
                listOf(
                    SettingsEntry(
                        titleRes = Res.string.relay_setup,
                        icon = SettingsIcon.Painter(R.drawable.relays, 4),
                        keywordsRes = Res.string.relay_setup_search_keywords,
                    ) { nav.nav(Route.EditRelays) },
                    symEntry(Res.string.event_sync_title, MaterialSymbols.Sync, Res.string.event_sync_search_keywords, Route.EventSync),
                    symEntry(Res.string.route_import_follows, MaterialSymbols.GroupAdd, Res.string.import_follows_search_keywords, Route.ImportFollowsSelectUser),
                    symEntry(Res.string.media_servers, MaterialSymbols.CloudUpload, Res.string.media_servers_search_keywords, Route.EditMediaServers),
                    symEntry(Res.string.nests_servers_title, MaterialSymbols.CloudUpload, Res.string.nests_servers_search_keywords, Route.EditNestsServers),
                    symEntry(Res.string.zaps, MaterialSymbols.Bolt, Res.string.zaps_search_keywords, Route.UpdateZapAmount()),
                    symEntry(Res.string.reactions, MaterialSymbols.FavoriteBorder, Res.string.reactions_search_keywords, Route.UpdateReactionType),
                    symEntry(Res.string.reactions_settings, MaterialSymbols.ThumbUp, Res.string.reactions_settings_search_keywords, Route.ReactionsSettings),
                    symEntry(Res.string.messages_settings, MaterialSymbols.Mail, Res.string.messages_settings_search_keywords, Route.MessagesSettings),
                    symEntry(Res.string.bottom_bar_settings, MaterialSymbols.Dashboard, Res.string.bottom_bar_search_keywords, Route.BottomBarSettings),
                    symEntry(Res.string.drawer_settings, MaterialSymbols.AutoMirrored.ViewList, Res.string.drawer_search_keywords, Route.DrawerSettings),
                    symEntry(Res.string.video_player_settings, MaterialSymbols.VideoSettings, Res.string.video_player_search_keywords, Route.VideoPlayerSettings),
                    symEntry(Res.string.audio_visualizer_settings, MaterialSymbols.MusicNote, Res.string.audio_visualizer_search_keywords, Route.AudioVisualizerSettings),
                    symEntry(Res.string.favorite_dvms_title, MaterialSymbols.AutoAwesome, Res.string.favorite_dvms_search_keywords, Route.EditFavoriteAlgoFeeds),
                    symEntry(Res.string.profile_badges_title, MaterialSymbols.MilitaryTech, Res.string.profile_badges_search_keywords, Route.ProfileBadges),
                    symEntry(Res.string.payment_targets, MaterialSymbols.Payment, Res.string.payment_targets_search_keywords, Route.EditPaymentTargets),
                    symEntry(Res.string.bolt12_offers, MaterialSymbols.Payment, Res.string.bolt12_offers_search_keywords, Route.EditBolt12Offers),
                    symEntry(Res.string.security_filters, MaterialSymbols.Security, Res.string.security_filters_search_keywords, Route.SecurityFilters),
                    symEntry(Res.string.translations, MaterialSymbols.Translate, Res.string.translations_search_keywords, Route.UserSettings),
                    symEntry(Res.string.napplet_permissions_title, MaterialSymbols.Apps, Res.string.napplet_connected_apps_search_keywords, Route.ConnectedApps),
                    symEntry(Res.string.relay_auth_settings_title, MaterialSymbols.Lock, Res.string.relay_auth_search_keywords, Route.RelayAuthSettings),
                    symEntry(Res.string.call_settings, MaterialSymbols.Phone, Res.string.call_settings_search_keywords, Route.CallSettings),
                ),
        )

    val app =
        SettingsCategory(
            titleRes = Res.string.app_settings,
            entries =
                listOf(
                    SettingsEntry(
                        titleRes = Res.string.privacy_options,
                        icon = SettingsIcon.Painter(R.drawable.ic_tor, 1),
                        keywordsRes = Res.string.privacy_options_search_keywords,
                    ) { nav.nav(Route.PrivacyOptions) },
                    symEntry(Res.string.ui_preferences, MaterialSymbols.Settings, Res.string.ui_preferences_search_keywords, Route.Settings),
                    symEntry(Res.string.route_home, MaterialSymbols.Home, Res.string.home_tabs_search_keywords, Route.HomeTabsSettings),
                    symEntry(Res.string.notification_settings, MaterialSymbols.Notifications, Res.string.notification_settings_search_keywords, Route.NotificationSettings),
                    symEntry(Res.string.compose_settings, MaterialSymbols.Edit, Res.string.compose_search_keywords, Route.ComposeSettings),
                    symEntry(Res.string.profile_ui_settings, MaterialSymbols.AccountCircle, Res.string.profile_ui_search_keywords, Route.ProfileUiSettings),
                    symEntry(Res.string.calendar_reminder_settings_title, MaterialSymbols.CalendarMonth, Res.string.calendar_reminder_search_keywords, Route.CalendarReminderSettings),
                    symEntry(Res.string.ots_explorer_settings, MaterialSymbols.Search, Res.string.ots_explorer_search_keywords, Route.OtsSettings),
                    symEntry(Res.string.namecoin_settings, MaterialSymbols.Security, Res.string.namecoin_search_keywords, Route.NamecoinSettings),
                    symEntry(Res.string.active_subs_title, MaterialSymbols.CloudSync, Res.string.active_subs_search_keywords, Route.ActiveSubscriptions),
                    symEntry(Res.string.resource_usage_title, MaterialSymbols.Bolt, Res.string.resource_usage_search_keywords, Route.ResourceUsage),
                ),
        )

    val danger =
        SettingsCategory(
            titleRes = Res.string.danger_zone,
            isDanger = true,
            entries =
                buildList {
                    if (hasPrivateKey) {
                        add(
                            SettingsEntry(
                                titleRes = Res.string.backup_keys,
                                icon = SettingsIcon.Symbol(MaterialSymbols.Key),
                                keywordsRes = Res.string.backup_keys_search_keywords,
                                isDanger = true,
                            ) { nav.nav(Route.AccountBackup) },
                        )
                        add(
                            SettingsEntry(
                                titleRes = Res.string.request_to_vanish,
                                icon = SettingsIcon.Symbol(MaterialSymbols.DeleteForever),
                                keywordsRes = Res.string.request_to_vanish_search_keywords,
                                isDanger = true,
                            ) { nav.nav(Route.RequestToVanish) },
                        )
                    }
                    add(
                        SettingsEntry(
                            titleRes = Res.string.vanish_history,
                            icon = SettingsIcon.Symbol(MaterialSymbols.History),
                            keywordsRes = Res.string.vanish_history_search_keywords,
                            isDanger = true,
                        ) { nav.nav(Route.VanishEvents) },
                    )
                    add(
                        SettingsEntry(
                            titleRes = Res.string.reset_marmot_state,
                            icon = SettingsIcon.Symbol(MaterialSymbols.DeleteSweep),
                            keywordsRes = Res.string.reset_marmot_search_keywords,
                            isDanger = true,
                            onClick = onResetMarmot,
                        ),
                    )
                },
        )

    return listOfNotNull(account, app, legalSettingsCategory(uriHandler), danger)
}
