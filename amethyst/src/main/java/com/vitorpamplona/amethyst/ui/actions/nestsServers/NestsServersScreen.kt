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
package com.vitorpamplona.amethyst.ui.actions.nestsServers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.actions.mediaServers.MediaServerEditField
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.SavingTopBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.SettingsCategory
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.SettingsCategoryWithButton
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DoubleVertPadding
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.SettingsCategoryFirstModifier
import com.vitorpamplona.amethyst.ui.theme.SettingsCategorySpacingModifier
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.grayText

/**
 * Settings screen for the user's preferred audio-room (NIP-53 / nests)
 * MoQ host servers — kind 10112 [com.vitorpamplona.quartz.nip53LiveActivities.nestsServers.NestsServersEvent].
 *
 * Mirrors [com.vitorpamplona.amethyst.ui.actions.mediaServers.AllMediaServersScreen]
 * — top bar with Cancel / Save, list of saved servers with delete
 * buttons, edit-field row to add a new one, and a "Use defaults" row
 * for one-tap inclusion of `nostrnests.com`.
 *
 * Reachable from `Settings → Audio-room servers`.
 */
@Composable
fun NestsServersScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: NestsServersViewModel = viewModel()
    viewModel.init(accountViewModel)

    LaunchedEffect(accountViewModel) { viewModel.load() }

    NestsServersScaffold(viewModel) { nav.popBack() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NestsServersScaffold(
    viewModel: NestsServersViewModel,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            SavingTopBar(
                titleRes = R.string.nests_servers_title,
                onCancel = {
                    viewModel.refresh()
                    onClose()
                },
                onPost = {
                    viewModel.save()
                    onClose()
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = 16.dp,
                        top = padding.calculateTopPadding(),
                        end = 16.dp,
                        bottom = padding.calculateBottomPadding(),
                    ).consumeWindowInsets(padding)
                    .imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp, alignment = Alignment.Top),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringRes(id = R.string.nests_servers_explainer),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.grayText,
            )
            NestsServersBody(viewModel)
        }
    }
}

@Composable
private fun NestsServersBody(viewModel: NestsServersViewModel) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()

    LazyColumn(
        verticalArrangement = Arrangement.SpaceAround,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = FeedPadding,
    ) {
        item {
            SettingsCategory(
                R.string.nests_servers_my_section,
                R.string.nests_servers_my_explainer,
                SettingsCategoryFirstModifier,
            )
        }

        if (servers.isEmpty()) {
            item {
                Text(
                    text = stringRes(id = R.string.nests_servers_empty),
                    modifier = DoubleVertPadding,
                )
            }
        } else {
            itemsIndexed(servers, key = { _, s -> "saved-${s.baseUrl}" }) { _, entry ->
                NestsServerEntry(
                    server = entry,
                    isAmethystDefault = false,
                    onAction = { viewModel.removeServer(entry.baseUrl) },
                )
            }
        }

        item {
            Spacer(modifier = StdVertSpacer)
            MediaServerEditField(label = R.string.nests_servers_add_field) { url ->
                viewModel.addServer(url)
            }
        }

        item {
            SettingsCategoryWithButton(
                title = R.string.nests_servers_recommended_section,
                description = R.string.nests_servers_recommended_explainer,
                modifier = SettingsCategorySpacingModifier,
            ) {
                OutlinedButton(
                    onClick = { viewModel.addServerList(DEFAULT_NESTS_SERVERS.map { it.baseUrl }) },
                ) {
                    Text(text = stringRes(id = R.string.nests_servers_use_defaults))
                }
            }
        }

        itemsIndexed(DEFAULT_NESTS_SERVERS, key = { _, s -> "proposed-${s.baseUrl}" }) { _, entry ->
            NestsServerEntry(
                server = entry,
                isAmethystDefault = true,
                onAction = { viewModel.addServer(entry.baseUrl) },
            )
        }
    }
}

@Composable
private fun NestsServerEntry(
    server: NestsServer,
    isAmethystDefault: Boolean,
    onAction: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = server.name.replaceFirstChar(Char::titlecase),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = StdVertSpacer)
            Text(
                text = server.baseUrl,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.grayText,
            )
        }
        Row(horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onAction) {
                Icon(
                    symbol = if (isAmethystDefault) MaterialSymbols.Add else MaterialSymbols.Delete,
                    contentDescription =
                        if (isAmethystDefault) {
                            stringRes(id = R.string.nests_servers_add_recommended)
                        } else {
                            stringRes(id = R.string.nests_servers_remove)
                        },
                )
            }
        }
    }
}

/**
 * Built-in suggestion list shown under "Recommended servers". Today
 * just the public `nostrnests.com` deployment; add new entries here
 * as community-run moq-rs / moq-auth instances come online.
 */
val DEFAULT_NESTS_SERVERS: List<NestsServer> =
    listOf(
        NestsServer(name = "nostrnests.com", baseUrl = "https://moq.nostrnests.com"),
    )
