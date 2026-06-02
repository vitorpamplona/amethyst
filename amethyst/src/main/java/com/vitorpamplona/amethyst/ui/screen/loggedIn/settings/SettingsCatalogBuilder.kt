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
                    SettingsEntry(
                        titleRes = R.string.event_sync_title,
                        icon = SettingsIcon.Symbol(MaterialSymbols.Sync),
                        keywordsRes = R.string.event_sync_search_keywords,
                    ) { nav.nav(Route.EventSync) },
                    SettingsEntry(
                        titleRes = R.string.route_import_follows,
                        icon = SettingsIcon.Symbol(MaterialSymbols.GroupAdd),
                        keywordsRes = R.string.import_follows_search_keywords,
                    ) { nav.nav(Route.ImportFollowsSelectUser) },
                    SettingsEntry(
                        titleRes = R.string.media_servers,
                        icon = SettingsIcon.Symbol(MaterialSymbols.CloudUpload),
                        keywordsRes = R.string.media_servers_search_keywords,
                    ) { nav.nav(Route.EditMediaServers) },
                    SettingsEntry(
                        titleRes = R.string.nests_servers_title,
                        icon = SettingsIcon.Symbol(MaterialSymbols.CloudUpload),
                        keywordsRes = R.string.nests_servers_search_keywords,
                    ) { nav.nav(Route.EditNestsServers) },
                    SettingsEntry(
                        titleRes = R.string.profile_badges_title,
                        icon = SettingsIcon.Symbol(MaterialSymbols.MilitaryTech),
                        keywordsRes = R.string.profile_badges_search_keywords,
                    ) { nav.nav(Route.ProfileBadges) },
                    SettingsEntry(
                        titleRes = R.string.favorite_dvms_title,
                        icon = SettingsIcon.Symbol(MaterialSymbols.AutoAwesome),
                        keywordsRes = R.string.favorite_dvms_search_keywords,
                    ) { nav.nav(Route.EditFavoriteAlgoFeeds) },
                    SettingsEntry(
                        titleRes = R.string.reactions,
                        icon = SettingsIcon.Symbol(MaterialSymbols.FavoriteBorder),
                        keywordsRes = R.string.reactions_search_keywords,
                    ) { nav.nav(Route.UpdateReactionType) },
                    SettingsEntry(
                        titleRes = R.string.video_player_settings,
                        icon = SettingsIcon.Symbol(MaterialSymbols.VideoSettings),
                        keywordsRes = R.string.video_player_search_keywords,
                    ) { nav.nav(Route.VideoPlayerSettings) },
                    SettingsEntry(
                        titleRes = R.string.zaps,
                        icon = SettingsIcon.Symbol(MaterialSymbols.Bolt),
                        keywordsRes = R.string.zaps_search_keywords,
                    ) { nav.nav(Route.UpdateZapAmount()) },
                    SettingsEntry(
                        titleRes = R.string.payment_targets,
                        icon = SettingsIcon.Symbol(MaterialSymbols.Payment),
                        keywordsRes = R.string.payment_targets_search_keywords,
                    ) { nav.nav(Route.EditPaymentTargets) },
                    SettingsEntry(
                        titleRes = R.string.security_filters,
                        icon = SettingsIcon.Symbol(MaterialSymbols.Security),
                        keywordsRes = R.string.security_filters_search_keywords,
                    ) { nav.nav(Route.SecurityFilters) },
                    SettingsEntry(
                        titleRes = R.string.call_settings,
                        icon = SettingsIcon.Symbol(MaterialSymbols.Phone),
                        keywordsRes = R.string.call_settings_search_keywords,
                    ) { nav.nav(Route.CallSettings) },
                    SettingsEntry(
                        titleRes = R.string.translations,
                        icon = SettingsIcon.Symbol(MaterialSymbols.Translate),
                        keywordsRes = R.string.translations_search_keywords,
                    ) { nav.nav(Route.UserSettings) },
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
                    SettingsEntry(
                        titleRes = R.string.ots_explorer_settings,
                        icon = SettingsIcon.Symbol(MaterialSymbols.Search),
                        keywordsRes = R.string.ots_explorer_search_keywords,
                    ) { nav.nav(Route.OtsSettings) },
                    SettingsEntry(
                        titleRes = R.string.namecoin_settings,
                        icon = SettingsIcon.Symbol(MaterialSymbols.Security),
                        keywordsRes = R.string.namecoin_search_keywords,
                    ) { nav.nav(Route.NamecoinSettings) },
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
                    SettingsEntry(
                        titleRes = R.string.calendar_reminder_settings_title,
                        icon = SettingsIcon.Symbol(MaterialSymbols.CalendarMonth),
                        keywordsRes = R.string.calendar_reminder_search_keywords,
                    ) { nav.nav(Route.CalendarReminderSettings) },
                    SettingsEntry(
                        titleRes = R.string.compose_settings,
                        icon = SettingsIcon.Symbol(MaterialSymbols.Edit),
                        keywordsRes = R.string.compose_search_keywords,
                    ) { nav.nav(Route.ComposeSettings) },
                    SettingsEntry(
                        titleRes = R.string.reactions_settings,
                        icon = SettingsIcon.Symbol(MaterialSymbols.ThumbUp),
                        keywordsRes = R.string.reactions_settings_search_keywords,
                    ) { nav.nav(Route.ReactionsSettings) },
                    SettingsEntry(
                        titleRes = R.string.bottom_bar_settings,
                        icon = SettingsIcon.Symbol(MaterialSymbols.Dashboard),
                        keywordsRes = R.string.bottom_bar_search_keywords,
                    ) { nav.nav(Route.BottomBarSettings) },
                    SettingsEntry(
                        titleRes = R.string.home_tabs_settings,
                        icon = SettingsIcon.Symbol(MaterialSymbols.Home),
                        keywordsRes = R.string.home_tabs_search_keywords,
                    ) { nav.nav(Route.HomeTabsSettings) },
                    SettingsEntry(
                        titleRes = R.string.profile_ui_settings,
                        icon = SettingsIcon.Symbol(MaterialSymbols.AccountCircle),
                        keywordsRes = R.string.profile_ui_search_keywords,
                    ) { nav.nav(Route.ProfileUiSettings) },
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
