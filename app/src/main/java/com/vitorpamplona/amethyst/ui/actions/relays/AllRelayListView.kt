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
import com.vitorpamplona.amethyst.service.relays.Constants.defaultRelays
import com.vitorpamplona.amethyst.ui.actions.CloseButton
import com.vitorpamplona.amethyst.ui.actions.SaveButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllRelayListView(
    onClose: () -> Unit,
    relayToAdd: String = "",
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val postViewModel: Kind3RelayListViewModel = viewModel()
    val dmViewModel: DMRelayListViewModel = viewModel()
    val feedState by postViewModel.relays.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { postViewModel.load(accountViewModel.account) }

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
                            Spacer(modifier = StdHorzSpacer)

                            Button(
                                onClick = {
                                    postViewModel.deleteAll()
                                    defaultRelays.forEach { postViewModel.addRelay(it) }
                                    postViewModel.loadRelayDocuments()
                                },
                            ) {
                                Text(stringResource(R.string.default_relays))
                            }

                            SaveButton(
                                onPost = {
                                    postViewModel.create()
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
                                postViewModel.clear()
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
                    Modifier.fillMaxSize().padding(
                        16.dp,
                        pad.calculateTopPadding(),
                        16.dp,
                        pad.calculateBottomPadding(),
                    ),
                verticalArrangement = Arrangement.SpaceAround,
            ) {
                Kind3RelayListView(feedState, postViewModel, accountViewModel, onClose, nav, relayToAdd)
            }
        }
    }
}
