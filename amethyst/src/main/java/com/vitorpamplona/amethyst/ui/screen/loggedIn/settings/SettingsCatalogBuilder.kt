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
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route

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
    val account =
        SettingsCategory(
            titleRes = R.string.account_settings,
            entries =
                listOf(
                    SettingsEntry(
                        titleRes = R.string.relay_setup,
                        icon = SettingsIcon.Painter(R.drawable.relays, 4),
                        keywordsRes = R.string.relay_setup_search_keywords,
                    ) { nav.nav(Route.EditRelays) },
                    SettingsEntry(R.string.event_sync_title, SettingsIcon.Symbol(MaterialSymbols.Sync)) { nav.nav(Route.EventSync) },
                    SettingsEntry(R.string.route_import_follows, SettingsIcon.Symbol(MaterialSymbols.GroupAdd)) { nav.nav(Route.ImportFollowsSelectUser) },
                    SettingsEntry(R.string.media_servers, SettingsIcon.Symbol(MaterialSymbols.CloudUpload)) { nav.nav(Route.EditMediaServers) },
                    SettingsEntry(R.string.nests_servers_title, SettingsIcon.Symbol(MaterialSymbols.CloudUpload)) { nav.nav(Route.EditNestsServers) },
                    SettingsEntry(R.string.profile_badges_title, SettingsIcon.Symbol(MaterialSymbols.MilitaryTech)) { nav.nav(Route.ProfileBadges) },
                    SettingsEntry(R.string.favorite_dvms_title, SettingsIcon.Symbol(MaterialSymbols.AutoAwesome)) { nav.nav(Route.EditFavoriteAlgoFeeds) },
                    SettingsEntry(R.string.reactions, SettingsIcon.Symbol(MaterialSymbols.FavoriteBorder)) { nav.nav(Route.UpdateReactionType) },
                    SettingsEntry(R.string.video_player_settings, SettingsIcon.Symbol(MaterialSymbols.VideoSettings)) { nav.nav(Route.VideoPlayerSettings) },
                    SettingsEntry(
                        titleRes = R.string.zaps,
                        icon = SettingsIcon.Symbol(MaterialSymbols.Bolt),
                        keywordsRes = R.string.zaps_search_keywords,
                    ) { nav.nav(Route.UpdateZapAmount()) },
                    SettingsEntry(R.string.payment_targets, SettingsIcon.Symbol(MaterialSymbols.Payment)) { nav.nav(Route.EditPaymentTargets) },
                    SettingsEntry(
                        titleRes = R.string.security_filters,
                        icon = SettingsIcon.Symbol(MaterialSymbols.Security),
                        keywordsRes = R.string.security_filters_search_keywords,
                    ) { nav.nav(Route.SecurityFilters) },
                    SettingsEntry(R.string.call_settings, SettingsIcon.Symbol(MaterialSymbols.Phone)) { nav.nav(Route.CallSettings) },
                    SettingsEntry(R.string.translations, SettingsIcon.Symbol(MaterialSymbols.Translate)) { nav.nav(Route.UserSettings) },
                ),
        )

    val app =
        SettingsCategory(
            titleRes = R.string.app_settings,
            entries =
                listOf(
                    SettingsEntry(
                        titleRes = R.string.privacy_options,
                        icon = SettingsIcon.Painter(R.drawable.ic_tor, 1),
                        keywordsRes = R.string.privacy_options_search_keywords,
                    ) { nav.nav(Route.PrivacyOptions) },
                    SettingsEntry(R.string.ots_explorer_settings, SettingsIcon.Symbol(MaterialSymbols.Search)) { nav.nav(Route.OtsSettings) },
                    SettingsEntry(R.string.namecoin_settings, SettingsIcon.Symbol(MaterialSymbols.Security)) { nav.nav(Route.NamecoinSettings) },
                    SettingsEntry(
                        titleRes = R.string.ui_preferences,
                        icon = SettingsIcon.Symbol(MaterialSymbols.Settings),
                        keywordsRes = R.string.ui_preferences_search_keywords,
                    ) { nav.nav(Route.Settings) },
                    SettingsEntry(
                        titleRes = R.string.notification_settings,
                        icon = SettingsIcon.Symbol(MaterialSymbols.Notifications),
                        keywordsRes = R.string.notification_settings_search_keywords,
                    ) { nav.nav(Route.NotificationSettings) },
                    SettingsEntry(R.string.calendar_reminder_settings_title, SettingsIcon.Symbol(MaterialSymbols.CalendarMonth)) { nav.nav(Route.CalendarReminderSettings) },
                    SettingsEntry(R.string.compose_settings, SettingsIcon.Symbol(MaterialSymbols.Edit)) { nav.nav(Route.ComposeSettings) },
                    SettingsEntry(R.string.reactions_settings, SettingsIcon.Symbol(MaterialSymbols.ThumbUp)) { nav.nav(Route.ReactionsSettings) },
                    SettingsEntry(R.string.bottom_bar_settings, SettingsIcon.Symbol(MaterialSymbols.Dashboard)) { nav.nav(Route.BottomBarSettings) },
                    SettingsEntry(R.string.home_tabs_settings, SettingsIcon.Symbol(MaterialSymbols.Home)) { nav.nav(Route.HomeTabsSettings) },
                    SettingsEntry(R.string.profile_ui_settings, SettingsIcon.Symbol(MaterialSymbols.AccountCircle)) { nav.nav(Route.ProfileUiSettings) },
                ),
        )

    val danger =
        SettingsCategory(
            titleRes = R.string.danger_zone,
            isDanger = true,
            entries =
                buildList {
                    if (hasPrivateKey) {
                        add(SettingsEntry(R.string.backup_keys, SettingsIcon.Symbol(MaterialSymbols.Key), isDanger = true) { nav.nav(Route.AccountBackup) })
                        add(SettingsEntry(R.string.request_to_vanish, SettingsIcon.Symbol(MaterialSymbols.DeleteForever), isDanger = true) { nav.nav(Route.RequestToVanish) })
                    }
                    add(SettingsEntry(R.string.vanish_history, SettingsIcon.Symbol(MaterialSymbols.History), isDanger = true) { nav.nav(Route.VanishEvents) })
                    add(SettingsEntry(R.string.reset_marmot_state, SettingsIcon.Symbol(MaterialSymbols.DeleteSweep), isDanger = true, onClick = onResetMarmot))
                },
        )

    return listOfNotNull(account, app, legalSettingsCategory(uriHandler), danger)
}
