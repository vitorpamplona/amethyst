/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.actions.relays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.actions.CloseButton
import com.vitorpamplona.amethyst.ui.actions.SaveButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.MinHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.RowColSpacing
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.ammolite.relays.Constants
import com.vitorpamplona.ammolite.relays.RelayStat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllRelayListView(
    onClose: () -> Unit,
    relayToAdd: String = "",
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val kind3ViewModel: Kind3RelayListViewModel = viewModel()
    val kind3FeedState by kind3ViewModel.relays.collectAsStateWithLifecycle()
    val kind3Proposals by kind3ViewModel.proposedRelays.collectAsStateWithLifecycle()

    val dmViewModel: DMRelayListViewModel = viewModel()
    val dmFeedState by dmViewModel.relays.collectAsStateWithLifecycle()

    val nip65ViewModel: Nip65RelayListViewModel = viewModel()
    val homeFeedState by nip65ViewModel.homeRelays.collectAsStateWithLifecycle()
    val notifFeedState by nip65ViewModel.notificationRelays.collectAsStateWithLifecycle()

    val privateOutboxViewModel: PrivateOutboxRelayListViewModel = viewModel()
    val privateOutboxFeedState by privateOutboxViewModel.relays.collectAsStateWithLifecycle()

    val searchViewModel: SearchRelayListViewModel = viewModel()
    val searchFeedState by searchViewModel.relays.collectAsStateWithLifecycle()

    val localViewModel: LocalRelayListViewModel = viewModel()
    val localFeedState by localViewModel.relays.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        kind3ViewModel.load(accountViewModel.account)
        dmViewModel.load(accountViewModel.account)
        nip65ViewModel.load(accountViewModel.account)
        searchViewModel.load(accountViewModel.account)
        localViewModel.load(accountViewModel.account)
        privateOutboxViewModel.load(accountViewModel.account)
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Spacer(modifier = MinHorzSpacer)

                            Text(
                                text = stringRes(R.string.relay_settings),
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleLarge,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                            )

                            SaveButton(
                                onPost = {
                                    kind3ViewModel.create()
                                    dmViewModel.create()
                                    nip65ViewModel.create()
                                    searchViewModel.create()
                                    localViewModel.create()
                                    privateOutboxViewModel.create()
                                    onClose()
                                },
                                true,
                            )
                        }
                    },
                    navigationIcon = {
                        Row {
                            Spacer(modifier = StdHorzSpacer)
                            CloseButton(
                                onPress = {
                                    kind3ViewModel.clear()
                                    dmViewModel.clear()
                                    nip65ViewModel.clear()
                                    searchViewModel.clear()
                                    localViewModel.clear()
                                    privateOutboxViewModel.clear()
                                    onClose()
                                },
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                )
            },
        ) { pad ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            16.dp,
                            pad.calculateTopPadding(),
                            16.dp,
                            pad.calculateBottomPadding(),
                        ),
                verticalArrangement = Arrangement.SpaceAround,
            ) {
                LazyColumn(contentPadding = FeedPadding) {
                    item {
                        SettingsCategory(
                            stringRes(R.string.public_home_section),
                            stringRes(R.string.public_home_section_explainer),
                            Modifier.padding(bottom = 8.dp),
                        )
                    }
                    renderNip65HomeItems(homeFeedState, nip65ViewModel, accountViewModel, onClose, nav)

                    item {
                        SettingsCategory(
                            stringRes(R.string.public_notif_section),
                            stringRes(R.string.public_notif_section_explainer),
                        )
                    }
                    renderNip65NotifItems(notifFeedState, nip65ViewModel, accountViewModel, onClose, nav)

                    item {
                        SettingsCategory(
                            stringRes(R.string.private_inbox_section),
                            stringRes(R.string.private_inbox_section_explainer),
                        )
                    }
                    renderDMItems(dmFeedState, dmViewModel, accountViewModel, onClose, nav)

                    item {
                        SettingsCategory(
                            stringRes(R.string.private_outbox_section),
                            stringRes(R.string.private_outbox_section_explainer),
                        )
                    }
                    renderPrivateOutboxItems(privateOutboxFeedState, privateOutboxViewModel, accountViewModel, onClose, nav)

                    item {
                        SettingsCategoryWithButton(
                            stringRes(R.string.search_section),
                            stringRes(R.string.search_section_explainer),
                            action = {
                                ResetSearchRelays(searchViewModel)
                            },
                        )
                    }
                    renderSearchItems(searchFeedState, searchViewModel, accountViewModel, onClose, nav)

                    item {
                        SettingsCategory(
                            stringRes(R.string.local_section),
                            stringRes(R.string.local_section_explainer),
                        )
                    }
                    renderLocalItems(localFeedState, localViewModel, accountViewModel, onClose, nav)

                    item {
                        SettingsCategoryWithButton(
                            stringRes(R.string.kind_3_section),
                            stringRes(R.string.kind_3_section_description),
                            action = {
                                ResetKind3Relays(kind3ViewModel)
                            },
                        )
                    }
                    renderKind3Items(kind3FeedState, kind3ViewModel, accountViewModel, onClose, nav, relayToAdd)

                    if (kind3Proposals.isNotEmpty()) {
                        item {
                            SettingsCategory(
                                stringRes(R.string.kind_3_recommended_section),
                                stringRes(R.string.kind_3_recommended_section_description),
                            )
                        }
                        renderKind3ProposalItems(kind3Proposals, kind3ViewModel, accountViewModel, onClose, nav)
                    }
                }
            }
        }
    }
}

@Composable
fun ResetKind3Relays(postViewModel: Kind3RelayListViewModel) {
    OutlinedButton(
        onClick = {
            postViewModel.deleteAll()
            postViewModel.addAll(Constants.defaultRelays)
            postViewModel.loadRelayDocuments()
        },
    ) {
        Text(stringRes(R.string.default_relays))
    }
}

@Composable
fun ResetSearchRelays(postViewModel: SearchRelayListViewModel) {
    OutlinedButton(
        onClick = {
            postViewModel.deleteAll()
            Constants.defaultSearchRelaySet.forEach { postViewModel.addRelay(BasicRelaySetupInfo(it, RelayStat())) }
            postViewModel.loadRelayDocuments()
        },
    ) {
        Text(stringRes(R.string.default_relays))
    }
}

@Composable
fun SettingsCategory(
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
) {
    Column(modifier) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
        )
        if (description != null) {
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.grayText,
            )
        }
    }
}

@Composable
fun SettingsCategoryWithButton(
    title: String,
    description: String? = null,
    action: @Composable () -> Unit,
    modifier: Modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
) {
    Row(modifier, horizontalArrangement = RowColSpacing) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.grayText,
                )
            }
        }

        action()
    }
}
