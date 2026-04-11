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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.rememberRelayDragState
import com.vitorpamplona.amethyst.model.DefaultDMRelayList
import com.vitorpamplona.amethyst.model.DefaultIndexerRelayList
import com.vitorpamplona.amethyst.model.DefaultSearchRelayList
import com.vitorpamplona.amethyst.ui.components.M3ActionDialog
import com.vitorpamplona.amethyst.ui.components.M3ActionRow
import com.vitorpamplona.amethyst.ui.components.M3ActionSection
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.SavingTopBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.blocked.BlockedRelayListViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.blocked.renderBlockedItems
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.broadcast.BroadcastRelayListViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.broadcast.renderBroadcastItems
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.RelayExporter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.RelayListCollection
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.RelayZipExporter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.relaySetupInfoBuilder
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.connected.ConnectedRelayListViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.connected.renderConnectedItems
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.dm.DMRelayListViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.dm.renderDMItems
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.feeds.RelayFeedsListViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.feeds.renderRelayFeedsItems
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
import com.vitorpamplona.amethyst.ui.theme.SettingsCategoryFirstWithHorzBorderModifier
import com.vitorpamplona.amethyst.ui.theme.SettingsCategorySpacingWithHorzBorderModifier
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
    val relayFeedsViewModel: RelayFeedsListViewModel = viewModel()

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
    relayFeedsViewModel.init(accountViewModel)

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
        relayFeedsViewModel.load()
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
        relayFeedsViewModel,
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
    relayFeedsViewModel: RelayFeedsListViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
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
    val relayFeedsFeedState by relayFeedsViewModel.relays.collectAsStateWithLifecycle()

    val outboxCounts by nip65ViewModel.homeCountResults.collectAsStateWithLifecycle()
    val inboxCounts by nip65ViewModel.notifCountResults.collectAsStateWithLifecycle()
    val dmCounts by dmViewModel.countResults.collectAsStateWithLifecycle()
    val privateHomeCounts by privateOutboxViewModel.countResults.collectAsStateWithLifecycle()
    val proxyCounts by proxyViewModel.countResults.collectAsStateWithLifecycle()
    val indexerCounts by indexerViewModel.countResults.collectAsStateWithLifecycle()
    val searchCounts by searchViewModel.countResults.collectAsStateWithLifecycle()

    val homeDragState =
        rememberRelayDragState(
            onMove = { from, to -> nip65ViewModel.moveHomeRelay(from, to) },
            itemCount = { homeFeedState.size },
        )
    val notifDragState =
        rememberRelayDragState(
            onMove = { from, to -> nip65ViewModel.moveNotifRelay(from, to) },
            itemCount = { notifFeedState.size },
        )
    val dmDragState =
        rememberRelayDragState(
            onMove = { from, to -> dmViewModel.moveRelay(from, to) },
            itemCount = { dmFeedState.size },
        )
    val privateOutboxDragState =
        rememberRelayDragState(
            onMove = { from, to -> privateOutboxViewModel.moveRelay(from, to) },
            itemCount = { privateOutboxFeedState.size },
        )
    val proxyDragState =
        rememberRelayDragState(
            onMove = { from, to -> proxyViewModel.moveRelay(from, to) },
            itemCount = { proxyRelays.size },
        )
    val broadcastDragState =
        rememberRelayDragState(
            onMove = { from, to -> broadcastViewModel.moveRelay(from, to) },
            itemCount = { broadcastRelays.size },
        )
    val indexerDragState =
        rememberRelayDragState(
            onMove = { from, to -> indexerViewModel.moveRelay(from, to) },
            itemCount = { indexerRelays.size },
        )
    val searchDragState =
        rememberRelayDragState(
            onMove = { from, to -> searchViewModel.moveRelay(from, to) },
            itemCount = { searchFeedState.size },
        )
    val localDragState =
        rememberRelayDragState(
            onMove = { from, to -> localViewModel.moveRelay(from, to) },
            itemCount = { localFeedState.size },
        )
    val trustedDragState =
        rememberRelayDragState(
            onMove = { from, to -> trustedViewModel.moveRelay(from, to) },
            itemCount = { trustedFeedState.size },
        )
    val feedsDragState =
        rememberRelayDragState(
            onMove = { from, to -> relayFeedsViewModel.moveRelay(from, to) },
            itemCount = { relayFeedsFeedState.size },
        )
    val blockedDragState =
        rememberRelayDragState(
            onMove = { from, to -> blockedViewModel.moveRelay(from, to) },
            itemCount = { blockedFeedState.size },
        )

    Scaffold(
        topBar = {
            SavingTopBar(
                titleRes = R.string.relay_settings,
                additionalActions = {
                    ExportDropdownMenu {
                        RelayListCollection(
                            homeRelays = homeFeedState,
                            notifRelays = notifFeedState,
                            dmRelays = dmFeedState,
                            privateOutboxRelays = privateOutboxFeedState,
                            proxyRelays = proxyRelays,
                            broadcastRelays = broadcastRelays,
                            indexerRelays = indexerRelays,
                            searchRelays = searchFeedState,
                            localRelays = localFeedState,
                            trustedRelays = trustedFeedState,
                            favoriteRelays = relayFeedsFeedState,
                            blockedRelays = blockedFeedState,
                        )
                    }
                },
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
                    relayFeedsViewModel.clear()
                    nav.popBack()
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
                    relayFeedsViewModel.create()
                    nav.popBack()
                },
            )
        },
    ) { pad ->
        val anyDragging =
            homeDragState.isDragging || notifDragState.isDragging ||
                dmDragState.isDragging || privateOutboxDragState.isDragging ||
                proxyDragState.isDragging || broadcastDragState.isDragging ||
                indexerDragState.isDragging || searchDragState.isDragging ||
                localDragState.isDragging || trustedDragState.isDragging ||
                feedsDragState.isDragging || blockedDragState.isDragging

        LazyColumn(
            contentPadding = FeedPadding,
            userScrollEnabled = !anyDragging,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = pad.calculateTopPadding(),
                        bottom = pad.calculateBottomPadding(),
                    ).consumeWindowInsets(pad)
                    .imePadding(),
        ) {
            item {
                SettingsCategory(
                    R.string.public_home_section,
                    R.string.public_home_section_explainer,
                    SettingsCategoryFirstWithHorzBorderModifier,
                )
            }
            renderNip65HomeItems(homeFeedState, nip65ViewModel, accountViewModel, nav, outboxCounts, homeDragState)

            item {
                SettingsCategory(
                    R.string.public_notif_section,
                    R.string.public_notif_section_explainer,
                    SettingsCategorySpacingWithHorzBorderModifier,
                )
            }
            renderNip65NotifItems(notifFeedState, nip65ViewModel, accountViewModel, nav, inboxCounts, notifDragState)

            item {
                SettingsCategoryWithButton(
                    R.string.private_inbox_section,
                    R.string.private_inbox_section_explainer,
                    SettingsCategorySpacingWithHorzBorderModifier,
                    action = {
                        ResetDMRelays(dmViewModel)
                    },
                )
            }
            renderDMItems(dmFeedState, dmViewModel, accountViewModel, nav, dmCounts, dmDragState)

            item {
                SettingsCategory(
                    R.string.private_outbox_section,
                    R.string.private_outbox_section_explainer,
                    SettingsCategorySpacingWithHorzBorderModifier,
                )
            }
            renderPrivateOutboxItems(privateOutboxFeedState, privateOutboxViewModel, accountViewModel, nav, privateHomeCounts, privateOutboxDragState)

            item {
                SettingsCategory(
                    R.string.proxy_section,
                    R.string.proxy_section_explainer,
                    SettingsCategorySpacingWithHorzBorderModifier,
                )
            }
            renderProxyItems(proxyRelays, proxyViewModel, accountViewModel, nav, proxyCounts, proxyDragState)

            item {
                SettingsCategory(
                    R.string.broadcast_section,
                    R.string.broadcast_section_explainer,
                    SettingsCategorySpacingWithHorzBorderModifier,
                )
            }
            renderBroadcastItems(broadcastRelays, broadcastViewModel, accountViewModel, nav, broadcastDragState)

            item {
                SettingsCategoryWithButton(
                    R.string.indexer_section,
                    R.string.indexer_section_explainer,
                    SettingsCategorySpacingWithHorzBorderModifier,
                ) {
                    ResetIndexerRelays(indexerViewModel)
                }
            }
            renderIndexerItems(indexerRelays, indexerViewModel, accountViewModel, nav, indexerCounts, indexerDragState)

            item {
                SettingsCategoryWithButton(
                    R.string.search_section,
                    R.string.search_section_explainer,
                    SettingsCategorySpacingWithHorzBorderModifier,
                ) {
                    ResetSearchRelays(searchViewModel)
                }
            }
            renderSearchItems(searchFeedState, searchViewModel, accountViewModel, nav, searchCounts, searchDragState)

            item {
                SettingsCategory(
                    R.string.local_section,
                    R.string.local_section_explainer,
                    SettingsCategorySpacingWithHorzBorderModifier,
                )
            }
            renderLocalItems(localFeedState, localViewModel, accountViewModel, nav, localDragState)

            item {
                SettingsCategory(
                    R.string.trusted_section,
                    R.string.trusted_section_explainer,
                    SettingsCategorySpacingWithHorzBorderModifier,
                )
            }
            renderTrustedItems(trustedFeedState, trustedViewModel, accountViewModel, nav, trustedDragState)

            item {
                SettingsCategory(
                    R.string.favorite_section,
                    R.string.favorite_section_explainer,
                    SettingsCategorySpacingWithHorzBorderModifier,
                )
            }
            renderRelayFeedsItems(relayFeedsFeedState, relayFeedsViewModel, accountViewModel, nav, feedsDragState)

            item {
                SettingsCategory(
                    R.string.blocked_section,
                    R.string.blocked_section_explainer,
                    SettingsCategorySpacingWithHorzBorderModifier,
                )
            }
            renderBlockedItems(blockedFeedState, blockedViewModel, accountViewModel, nav, blockedDragState)

            item {
                SettingsCategory(
                    R.string.connected_section,
                    R.string.connected_section_description,
                    SettingsCategorySpacingWithHorzBorderModifier,
                )
            }
            renderConnectedItems(connectedRelays, connectedViewModel, accountViewModel, nav)
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

@Composable
fun ExportDropdownMenu(collection: () -> RelayListCollection) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = Icons.Default.Share,
            contentDescription = stringRes(R.string.export_relay_settings),
        )
    }

    if (expanded) {
        M3ActionDialog(
            title = stringRes(R.string.export_actions_dialog_title),
            onDismiss = { expanded = false },
        ) {
            M3ActionSection {
                M3ActionRow(
                    icon = Icons.Outlined.Description,
                    text = stringRes(R.string.export_as_text),
                ) {
                    expanded = false
                    RelayExporter(context).export(collection())
                }
                M3ActionRow(
                    icon = Icons.Outlined.FolderZip,
                    text = stringRes(R.string.export_as_zip),
                ) {
                    expanded = false
                    RelayZipExporter(context).export(collection())
                }
            }
        }
    }
}
