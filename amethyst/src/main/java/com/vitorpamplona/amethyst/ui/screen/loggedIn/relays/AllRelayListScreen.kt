/**
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.DefaultDMRelayList
import com.vitorpamplona.amethyst.model.DefaultIndexerRelayList
import com.vitorpamplona.amethyst.model.DefaultSearchRelayList
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.SavingTopBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.blocked.BlockedRelayListViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.blocked.renderBlockedItems
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.broadcast.BroadcastRelayListViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.broadcast.renderBroadcastItems
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.relaySetupInfoBuilder
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.connected.ConnectedRelayListViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.connected.renderConnectedItems
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.dm.DMRelayListViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.dm.renderDMItems
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.indexer.IndexerRelayListViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.indexer.renderIndexerItems
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.local.LocalRelayListViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.local.renderLocalItems
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.nip37.PrivateOutboxRelayListViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.nip37.renderPrivateOutboxItems
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.nip65.Nip65RelayListViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.nip65.renderNip65HomeItems
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.nip65.renderNip65NotifItems
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.proxy.ProxyRelayListViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.proxy.renderProxyItems
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.search.SearchRelayListViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.search.renderSearchItems
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.trusted.TrustedRelayListViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.trusted.renderTrustedItems
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.RowColSpacing
import com.vitorpamplona.amethyst.ui.theme.SettingsCategoryFirstModifier
import com.vitorpamplona.amethyst.ui.theme.SettingsCategorySpacingModifier
import com.vitorpamplona.amethyst.ui.theme.grayText

@Composable
fun AllRelayListScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val dmViewModel: DMRelayListViewModel = viewModel()
    val nip65ViewModel: Nip65RelayListViewModel = viewModel()
    val privateOutboxViewModel: PrivateOutboxRelayListViewModel = viewModel()
    val searchViewModel: SearchRelayListViewModel = viewModel()
    val blockedViewModel: BlockedRelayListViewModel = viewModel()
    val trustedViewModel: TrustedRelayListViewModel = viewModel()
    val localViewModel: LocalRelayListViewModel = viewModel()
    val connectedViewModel: ConnectedRelayListViewModel = viewModel()
    val broadcastViewModel: BroadcastRelayListViewModel = viewModel()
    val indexerViewModel: IndexerRelayListViewModel = viewModel()
    val proxyViewModel: ProxyRelayListViewModel = viewModel()

    dmViewModel.init(accountViewModel)
    nip65ViewModel.init(accountViewModel)
    searchViewModel.init(accountViewModel)
    localViewModel.init(accountViewModel)
    privateOutboxViewModel.init(accountViewModel)
    connectedViewModel.init(accountViewModel)
    blockedViewModel.init(accountViewModel)
    trustedViewModel.init(accountViewModel)
    broadcastViewModel.init(accountViewModel)
    indexerViewModel.init(accountViewModel)
    proxyViewModel.init(accountViewModel)

    LaunchedEffect(accountViewModel) {
        dmViewModel.load()
        nip65ViewModel.load()
        searchViewModel.load()
        localViewModel.load()
        privateOutboxViewModel.load()
        connectedViewModel.load()
        blockedViewModel.load()
        trustedViewModel.load()
        broadcastViewModel.load()
        indexerViewModel.load()
        proxyViewModel.load()
    }

    MappedAllRelayListView(
        dmViewModel,
        nip65ViewModel,
        searchViewModel,
        localViewModel,
        privateOutboxViewModel,
        connectedViewModel,
        blockedViewModel,
        trustedViewModel,
        broadcastViewModel,
        indexerViewModel,
        proxyViewModel,
        accountViewModel,
        nav,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MappedAllRelayListView(
    dmViewModel: DMRelayListViewModel,
    nip65ViewModel: Nip65RelayListViewModel,
    searchViewModel: SearchRelayListViewModel,
    localViewModel: LocalRelayListViewModel,
    privateOutboxViewModel: PrivateOutboxRelayListViewModel,
    connectedViewModel: ConnectedRelayListViewModel,
    blockedViewModel: BlockedRelayListViewModel,
    trustedViewModel: TrustedRelayListViewModel,
    broadcastViewModel: BroadcastRelayListViewModel,
    indexerViewModel: IndexerRelayListViewModel,
    proxyViewModel: ProxyRelayListViewModel,
    accountViewModel: AccountViewModel,
    newNav: INav,
) {
    val dmFeedState by dmViewModel.relays.collectAsStateWithLifecycle()
    val homeFeedState by nip65ViewModel.homeRelays.collectAsStateWithLifecycle()
    val notifFeedState by nip65ViewModel.notificationRelays.collectAsStateWithLifecycle()
    val privateOutboxFeedState by privateOutboxViewModel.relays.collectAsStateWithLifecycle()
    val searchFeedState by searchViewModel.relays.collectAsStateWithLifecycle()
    val blockedFeedState by blockedViewModel.relays.collectAsStateWithLifecycle()
    val trustedFeedState by trustedViewModel.relays.collectAsStateWithLifecycle()
    val localFeedState by localViewModel.relays.collectAsStateWithLifecycle()
    val connectedRelays by connectedViewModel.relays.collectAsStateWithLifecycle()
    val broadcastRelays by broadcastViewModel.relays.collectAsStateWithLifecycle()
    val indexerRelays by indexerViewModel.relays.collectAsStateWithLifecycle()
    val proxyRelays by proxyViewModel.relays.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            SavingTopBar(
                titleRes = R.string.relay_settings,
                onCancel = {
                    dmViewModel.clear()
                    nip65ViewModel.clear()
                    searchViewModel.clear()
                    localViewModel.clear()
                    privateOutboxViewModel.clear()
                    trustedViewModel.clear()
                    blockedViewModel.clear()
                    broadcastViewModel.clear()
                    indexerViewModel.clear()
                    proxyViewModel.clear()
                    newNav.popBack()
                },
                onPost = {
                    dmViewModel.create()
                    nip65ViewModel.create()
                    searchViewModel.create()
                    localViewModel.create()
                    privateOutboxViewModel.create()
                    trustedViewModel.create()
                    blockedViewModel.create()
                    broadcastViewModel.create()
                    indexerViewModel.create()
                    proxyViewModel.create()
                    newNav.popBack()
                },
            )
        },
    ) { pad ->
        LazyColumn(
            contentPadding = FeedPadding,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = 10.dp,
                        end = 10.dp,
                        top = pad.calculateTopPadding(),
                        bottom = pad.calculateBottomPadding(),
                    ).consumeWindowInsets(pad)
                    .imePadding(),
        ) {
            item {
                SettingsCategory(
                    R.string.public_home_section,
                    R.string.public_home_section_explainer,
                    SettingsCategoryFirstModifier,
                )
            }
            renderNip65HomeItems(homeFeedState, nip65ViewModel, accountViewModel, newNav)

            item {
                SettingsCategory(
                    R.string.public_notif_section,
                    R.string.public_notif_section_explainer,
                    SettingsCategorySpacingModifier,
                )
            }
            renderNip65NotifItems(notifFeedState, nip65ViewModel, accountViewModel, newNav)

            item {
                SettingsCategoryWithButton(
                    R.string.private_inbox_section,
                    R.string.private_inbox_section_explainer,
                    SettingsCategorySpacingModifier,
                    action = {
                        ResetDMRelays(dmViewModel)
                    },
                )
            }
            renderDMItems(dmFeedState, dmViewModel, accountViewModel, newNav)

            item {
                SettingsCategory(
                    R.string.private_outbox_section,
                    R.string.private_outbox_section_explainer,
                    SettingsCategorySpacingModifier,
                )
            }
            renderPrivateOutboxItems(privateOutboxFeedState, privateOutboxViewModel, accountViewModel, newNav)

            item {
                SettingsCategory(
                    R.string.proxy_section,
                    R.string.proxy_section_explainer,
                    SettingsCategorySpacingModifier,
                )
            }
            renderProxyItems(proxyRelays, proxyViewModel, accountViewModel, newNav)

            item {
                SettingsCategory(
                    R.string.broadcast_section,
                    R.string.broadcast_section_explainer,
                    SettingsCategorySpacingModifier,
                )
            }
            renderBroadcastItems(broadcastRelays, broadcastViewModel, accountViewModel, newNav)

            item {
                SettingsCategoryWithButton(
                    R.string.indexer_section,
                    R.string.indexer_section_explainer,
                    SettingsCategorySpacingModifier,
                ) {
                    ResetIndexerRelays(indexerViewModel)
                }
            }
            renderIndexerItems(indexerRelays, indexerViewModel, accountViewModel, newNav)

            item {
                SettingsCategoryWithButton(
                    R.string.search_section,
                    R.string.search_section_explainer,
                    SettingsCategorySpacingModifier,
                ) {
                    ResetSearchRelays(searchViewModel)
                }
            }
            renderSearchItems(searchFeedState, searchViewModel, accountViewModel, newNav)

            item {
                SettingsCategory(
                    R.string.local_section,
                    R.string.local_section_explainer,
                    SettingsCategorySpacingModifier,
                )
            }
            renderLocalItems(localFeedState, localViewModel, accountViewModel, newNav)

            item {
                SettingsCategory(
                    R.string.trusted_section,
                    R.string.trusted_section_explainer,
                    SettingsCategorySpacingModifier,
                )
            }
            renderTrustedItems(trustedFeedState, trustedViewModel, accountViewModel, newNav)

            item {
                SettingsCategory(
                    R.string.blocked_section,
                    R.string.blocked_section_explainer,
                    SettingsCategorySpacingModifier,
                )
            }
            renderBlockedItems(blockedFeedState, blockedViewModel, accountViewModel, newNav)

            item {
                SettingsCategory(
                    R.string.connected_section,
                    R.string.connected_section_description,
                    SettingsCategorySpacingModifier,
                )
            }
            renderConnectedItems(connectedRelays, connectedViewModel, accountViewModel, newNav)
        }
    }
}

@Composable
fun ResetSearchRelays(postViewModel: SearchRelayListViewModel) {
    OutlinedButton(
        onClick = {
            postViewModel.deleteAll()
            DefaultSearchRelayList.forEach {
                postViewModel.addRelay(
                    relaySetupInfoBuilder(it),
                )
            }
            postViewModel.loadRelayDocuments()
        },
    ) {
        Text(stringRes(R.string.default_relays))
    }
}

@Composable
fun ResetIndexerRelays(postViewModel: IndexerRelayListViewModel) {
    OutlinedButton(
        onClick = {
            postViewModel.deleteAll()
            DefaultIndexerRelayList.forEach {
                postViewModel.addRelay(
                    relaySetupInfoBuilder(it),
                )
            }
            postViewModel.loadRelayDocuments()
        },
    ) {
        Text(stringRes(R.string.default_relays))
    }
}

@Composable
fun ResetDMRelays(postViewModel: DMRelayListViewModel) {
    OutlinedButton(
        onClick = {
            postViewModel.deleteAll()
            DefaultDMRelayList.forEach {
                postViewModel.addRelay(relaySetupInfoBuilder(it))
            }
            postViewModel.loadRelayDocuments()
        },
    ) {
        Text(stringRes(R.string.default_relays))
    }
}

@Composable
fun SettingsCategory(
    title: Int,
    description: Int? = null,
    modifier: Modifier,
) {
    Column(modifier) {
        Text(
            text = stringRes(title),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
        )
        if (description != null) {
            Text(
                text = stringRes(description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.grayText,
            )
        }
    }
}

@Composable
fun SettingsCategoryWithButton(
    title: Int,
    description: Int? = null,
    modifier: Modifier,
    action: @Composable () -> Unit,
) {
    Row(modifier, horizontalArrangement = RowColSpacing) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringRes(title),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
            )
            if (description != null) {
                Text(
                    text = stringRes(description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.grayText,
                )
            }
        }

        action()
    }
}
