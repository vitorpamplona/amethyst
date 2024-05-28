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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.relays.Constants
import com.vitorpamplona.amethyst.ui.actions.CloseButton
import com.vitorpamplona.amethyst.ui.actions.SaveButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.grayText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllRelayListView(
    onClose: () -> Unit,
    relayToAdd: String = "",
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val kind3ViewModel: Kind3RelayListViewModel = viewModel()
    val dmViewModel: DMRelayListViewModel = viewModel()
    val kind3FeedState by kind3ViewModel.relays.collectAsStateWithLifecycle()
    val dmFeedState by dmViewModel.relays.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        kind3ViewModel.load(accountViewModel.account)
        dmViewModel.load(accountViewModel.account)
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
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SaveButton(
                                onPost = {
                                    kind3ViewModel.create()
                                    onClose()
                                },
                                true,
                            )
                        }
                    },
                    navigationIcon = {
                        Spacer(modifier = DoubleHorzSpacer)
                        CloseButton(
                            onPress = {
                                kind3ViewModel.clear()
                                onClose()
                            },
                        )
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
                            stringResource(R.string.private_inbox_section),
                            stringResource(R.string.private_inbox_section_explainer),
                            Modifier.padding(bottom = 8.dp),
                        )
                    }
                    renderDMItems(dmFeedState, dmViewModel, accountViewModel, onClose, nav)

                    item {
                        SettingsCategoryWithButton(
                            stringResource(R.string.kind_3_section),
                            stringResource(R.string.kind_3_section_description),
                            action = {
                                ResetKind3Relays(kind3ViewModel)
                            },
                        )
                    }
                    renderKind3Items(kind3FeedState, kind3ViewModel, accountViewModel, onClose, nav, relayToAdd)
                }
            }
        }
    }
}

@Composable
fun ResetKind3Relays(postViewModel: Kind3RelayListViewModel) {
    Button(
        onClick = {
            postViewModel.deleteAll()
            Constants.defaultRelays.forEach { postViewModel.addRelay(it) }
            postViewModel.loadRelayDocuments()
        },
    ) {
        Text(stringResource(R.string.default_relays))
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
    Row(modifier) {
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
