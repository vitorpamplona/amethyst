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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.HomeFeedType
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.home_tabs_settings
import com.vitorpamplona.amethyst.model.UiSettingsFlow
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonRow

@Preview
@Composable
fun HomeTabsSettingsScreenPreview() {
    ThemeComparisonRow {
        HomeTabsSettingsScreen(
            mockAccountViewModel(),
            EmptyNav(),
        )
    }
}

@Composable
fun HomeTabsSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = Res.string.home_tabs_settings), nav)
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            HomeTabsSection(accountViewModel.settings.uiSettingsFlow)
            HomeContentTypesSection(accountViewModel)
        }
    }
}

@Composable
private fun HomeTabsSection(ui: UiSettingsFlow) {
    val showNewThreads by ui.showHomeNewThreadsTab.collectAsStateWithLifecycle()
    val showConversations by ui.showHomeConversationsTab.collectAsStateWithLifecycle()
    val showEverything by ui.showHomeEverythingTab.collectAsStateWithLifecycle()

    val activeCount = listOf(showNewThreads, showConversations, showEverything).count { it }

    SettingsSection(R.string.settings_section_home_tabs) {
        SettingsSwitchTile(
            icon = MaterialSymbols.Forum,
            title = R.string.new_threads,
            checked = showNewThreads,
            // Don't allow disabling the last remaining tab.
            enabled = !(showNewThreads && activeCount == 1),
            onCheckedChange = { ui.showHomeNewThreadsTab.tryEmit(it) },
        )
        SettingsDivider()
        SettingsSwitchTile(
            icon = MaterialSymbols.Chat,
            title = R.string.conversations,
            checked = showConversations,
            enabled = !(showConversations && activeCount == 1),
            onCheckedChange = { ui.showHomeConversationsTab.tryEmit(it) },
        )
        SettingsDivider()
        SettingsSwitchTile(
            icon = MaterialSymbols.Public,
            title = R.string.home_tab_everything,
            checked = showEverything,
            enabled = !(showEverything && activeCount == 1),
            onCheckedChange = { ui.showHomeEverythingTab.tryEmit(it) },
        )
    }
}

/** One toggleable Home content group, mapping a [HomeFeedType] to its display title + icon. */
private data class HomeFeedTypeUi(
    val type: HomeFeedType,
    val titleRes: Int,
    val icon: MaterialSymbol,
)

// Ordered by how common each group is on a typical home feed (everyday posts first, niche last).
private val HOME_FEED_TYPES =
    listOf(
        HomeFeedTypeUi(HomeFeedType.TEXT_NOTES, R.string.home_content_type_text_notes, MaterialSymbols.EditNote),
        HomeFeedTypeUi(HomeFeedType.REPOSTS, R.string.home_content_type_reposts, MaterialSymbols.Forward),
        HomeFeedTypeUi(HomeFeedType.COMMENTS, R.string.home_content_type_comments, MaterialSymbols.Chat),
        HomeFeedTypeUi(HomeFeedType.PICTURES, R.string.home_content_type_pictures, MaterialSymbols.Image),
        HomeFeedTypeUi(HomeFeedType.VIDEOS, R.string.home_content_type_videos, MaterialSymbols.Videocam),
        HomeFeedTypeUi(HomeFeedType.SHORTS, R.string.home_content_type_shorts, MaterialSymbols.SmartDisplay),
        HomeFeedTypeUi(HomeFeedType.ARTICLES, R.string.home_content_type_articles, MaterialSymbols.AutoMirrored.Article),
        HomeFeedTypeUi(HomeFeedType.WIKI, R.string.home_content_type_wiki, MaterialSymbols.MenuBook),
        HomeFeedTypeUi(HomeFeedType.HIGHLIGHTS, R.string.home_content_type_highlights, MaterialSymbols.FormatQuote),
        HomeFeedTypeUi(HomeFeedType.POLLS, R.string.home_content_type_polls, MaterialSymbols.Poll),
        HomeFeedTypeUi(HomeFeedType.CLASSIFIEDS, R.string.home_content_type_classifieds, MaterialSymbols.Storefront),
        HomeFeedTypeUi(HomeFeedType.TORRENTS, R.string.home_content_type_torrents, MaterialSymbols.Download),
        HomeFeedTypeUi(HomeFeedType.VOICE, R.string.home_content_type_voice, MaterialSymbols.Mic),
        HomeFeedTypeUi(HomeFeedType.LIVE_ACTIVITIES, R.string.home_content_type_live_activities, MaterialSymbols.Sensors),
        HomeFeedTypeUi(HomeFeedType.EPHEMERAL_CHAT, R.string.home_content_type_ephemeral_chat, MaterialSymbols.Forum),
        HomeFeedTypeUi(HomeFeedType.INTERACTIVE_STORIES, R.string.home_content_type_interactive_stories, MaterialSymbols.AutoAwesome),
        HomeFeedTypeUi(HomeFeedType.CHESS, R.string.home_content_type_chess, MaterialSymbols.ChessKnight),
        HomeFeedTypeUi(HomeFeedType.BIRDS, R.string.home_content_type_birds, MaterialSymbols.TravelExplore),
        HomeFeedTypeUi(HomeFeedType.ATTESTATIONS, R.string.home_content_type_attestations, MaterialSymbols.Shield),
        HomeFeedTypeUi(HomeFeedType.NIPS, R.string.home_content_type_nips, MaterialSymbols.Code),
        HomeFeedTypeUi(HomeFeedType.MUSIC, R.string.home_content_type_music, MaterialSymbols.MusicNote),
        HomeFeedTypeUi(HomeFeedType.PODCASTS, R.string.home_content_type_podcasts, MaterialSymbols.Podcasts),
        HomeFeedTypeUi(HomeFeedType.FUNDRAISERS, R.string.home_content_type_fundraisers, MaterialSymbols.Paid),
    )

/**
 * Per-content-type load toggles for the Home feed. Turning one off both drops its event kinds from
 * the always-on home relay filters AND hides them from the New Threads / Conversations / Everything
 * tabs. Everything is on by default.
 */
@Composable
private fun HomeContentTypesSection(accountViewModel: AccountViewModel) {
    val enabled by accountViewModel.account.settings.enabledHomeFeedTypes
        .collectAsStateWithLifecycle()

    SettingsSection(R.string.settings_section_home_content_types) {
        HOME_FEED_TYPES.forEachIndexed { index, item ->
            if (index > 0) SettingsDivider()
            SettingsSwitchTile(
                icon = item.icon,
                title = item.titleRes,
                checked = item.type in enabled,
                onCheckedChange = { accountViewModel.account.settings.setHomeFeedTypeEnabled(item.type, it) },
            )
        }
    }
}
