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

import androidx.annotation.StringRes
import androidx.compose.ui.platform.UriHandler
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
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
    // Most rows are a symbol icon + a keyword blob that navigates to a route. This local
    // helper collapses that shape to one line per row and makes a mismatched keyword/route
    // obvious. Painter-icon rows and the danger rows below are spelled out explicitly.
    fun symEntry(
        @StringRes titleRes: Int,
        symbol: MaterialSymbol,
        @StringRes keywordsRes: Int,
        route: Route,
    ) = SettingsEntry(
        titleRes = titleRes,
        icon = SettingsIcon.Symbol(symbol),
        keywordsRes = keywordsRes,
    ) { nav.nav(route) }

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
                    symEntry(R.string.event_sync_title, MaterialSymbols.Sync, R.string.event_sync_search_keywords, Route.EventSync),
                    symEntry(R.string.route_import_follows, MaterialSymbols.GroupAdd, R.string.import_follows_search_keywords, Route.ImportFollowsSelectUser),
                    symEntry(R.string.media_servers, MaterialSymbols.CloudUpload, R.string.media_servers_search_keywords, Route.EditMediaServers),
                    symEntry(R.string.nests_servers_title, MaterialSymbols.CloudUpload, R.string.nests_servers_search_keywords, Route.EditNestsServers),
                    symEntry(R.string.reactions, MaterialSymbols.FavoriteBorder, R.string.reactions_search_keywords, Route.UpdateReactionType),
                    symEntry(R.string.zaps, MaterialSymbols.Bolt, R.string.zaps_search_keywords, Route.UpdateZapAmount()),
                    symEntry(R.string.video_player_settings, MaterialSymbols.VideoSettings, R.string.video_player_search_keywords, Route.VideoPlayerSettings),
                    symEntry(R.string.audio_visualizer_settings, MaterialSymbols.MusicNote, R.string.audio_visualizer_search_keywords, Route.AudioVisualizerSettings),
                    symEntry(R.string.favorite_dvms_title, MaterialSymbols.AutoAwesome, R.string.favorite_dvms_search_keywords, Route.EditFavoriteAlgoFeeds),
                    symEntry(R.string.profile_badges_title, MaterialSymbols.MilitaryTech, R.string.profile_badges_search_keywords, Route.ProfileBadges),
                    symEntry(R.string.payment_targets, MaterialSymbols.Payment, R.string.payment_targets_search_keywords, Route.EditPaymentTargets),
                    symEntry(R.string.security_filters, MaterialSymbols.Security, R.string.security_filters_search_keywords, Route.SecurityFilters),
                    symEntry(R.string.call_settings, MaterialSymbols.Phone, R.string.call_settings_search_keywords, Route.CallSettings),
                    symEntry(R.string.translations, MaterialSymbols.Translate, R.string.translations_search_keywords, Route.UserSettings),
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
                    symEntry(R.string.ui_preferences, MaterialSymbols.Settings, R.string.ui_preferences_search_keywords, Route.Settings),
                    symEntry(R.string.compose_settings, MaterialSymbols.Edit, R.string.compose_search_keywords, Route.ComposeSettings),
                    symEntry(R.string.notification_settings, MaterialSymbols.Notifications, R.string.notification_settings_search_keywords, Route.NotificationSettings),
                    symEntry(R.string.home_tabs_settings, MaterialSymbols.Home, R.string.home_tabs_search_keywords, Route.HomeTabsSettings),
                    symEntry(R.string.reactions_settings, MaterialSymbols.ThumbUp, R.string.reactions_settings_search_keywords, Route.ReactionsSettings),
                    symEntry(R.string.bottom_bar_settings, MaterialSymbols.Dashboard, R.string.bottom_bar_search_keywords, Route.BottomBarSettings),
                    symEntry(R.string.profile_ui_settings, MaterialSymbols.AccountCircle, R.string.profile_ui_search_keywords, Route.ProfileUiSettings),
                    symEntry(R.string.calendar_reminder_settings_title, MaterialSymbols.CalendarMonth, R.string.calendar_reminder_search_keywords, Route.CalendarReminderSettings),
                    symEntry(R.string.ots_explorer_settings, MaterialSymbols.Search, R.string.ots_explorer_search_keywords, Route.OtsSettings),
                    symEntry(R.string.namecoin_settings, MaterialSymbols.Security, R.string.namecoin_search_keywords, Route.NamecoinSettings),
                ),
        )

    val danger =
        SettingsCategory(
            titleRes = R.string.danger_zone,
            isDanger = true,
            entries =
                buildList {
                    if (hasPrivateKey) {
                        add(
                            SettingsEntry(
                                titleRes = R.string.backup_keys,
                                icon = SettingsIcon.Symbol(MaterialSymbols.Key),
                                keywordsRes = R.string.backup_keys_search_keywords,
                                isDanger = true,
                            ) { nav.nav(Route.AccountBackup) },
                        )
                        add(
                            SettingsEntry(
                                titleRes = R.string.request_to_vanish,
                                icon = SettingsIcon.Symbol(MaterialSymbols.DeleteForever),
                                keywordsRes = R.string.request_to_vanish_search_keywords,
                                isDanger = true,
                            ) { nav.nav(Route.RequestToVanish) },
                        )
                    }
                    add(
                        SettingsEntry(
                            titleRes = R.string.vanish_history,
                            icon = SettingsIcon.Symbol(MaterialSymbols.History),
                            keywordsRes = R.string.vanish_history_search_keywords,
                            isDanger = true,
                        ) { nav.nav(Route.VanishEvents) },
                    )
                    add(
                        SettingsEntry(
                            titleRes = R.string.reset_marmot_state,
                            icon = SettingsIcon.Symbol(MaterialSymbols.DeleteSweep),
                            keywordsRes = R.string.reset_marmot_search_keywords,
                            isDanger = true,
                            onClick = onResetMarmot,
                        ),
                    )
                },
        )

    return listOfNotNull(account, app, legalSettingsCategory(uriHandler), danger)
}
