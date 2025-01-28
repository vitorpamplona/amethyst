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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.DefaultSearchRelayList
import com.vitorpamplona.amethyst.ui.components.SetDialogToEdgeToEdge
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.CloseButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.SaveButton
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.imageModifier
import com.vitorpamplona.quartz.nip01Core.relays.RelayStat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSearchRelayListDialog(
    onClose: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val postViewModel: SearchRelayListViewModel = viewModel()

    LaunchedEffect(Unit) { postViewModel.load(accountViewModel.account) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        SetDialogToEdgeToEdge()
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

                            Text(stringRes(R.string.search_relays_title))

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
                        Spacer(modifier = StdHorzSpacer)
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
                    Modifier.padding(
                        16.dp,
                        pad.calculateTopPadding(),
                        16.dp,
                        pad.calculateBottomPadding(),
                    ),
                verticalArrangement = Arrangement.SpaceAround,
            ) {
                Explanation(postViewModel)

                SearchRelayList(postViewModel, accountViewModel, onClose, nav)
            }
        }
    }
}

@Composable
private fun Explanation(postViewModel: SearchRelayListViewModel) {
    Card(modifier = MaterialTheme.colorScheme.imageModifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringRes(id = R.string.search_relays_not_found_editing),
            )

            Spacer(modifier = StdVertSpacer)

            Text(
                text = stringRes(id = R.string.search_relays_not_found_examples),
            )

            Spacer(modifier = StdVertSpacer)

            ResetSearchRelaysLonger(postViewModel)
        }
    }
}

@Composable
fun ResetSearchRelaysLonger(postViewModel: SearchRelayListViewModel) {
    OutlinedButton(
        onClick = {
            postViewModel.deleteAll()
            DefaultSearchRelayList.forEach { postViewModel.addRelay(BasicRelaySetupInfo(it, RelayStat())) }
            postViewModel.loadRelayDocuments()
        },
    ) {
        Text(stringRes(R.string.default_relays_longer))
    }
}
