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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.dm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.DefaultDMRelayList
import com.vitorpamplona.amethyst.ui.components.SetDialogToEdgeToEdge
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.SavingTopBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.relaySetupInfoBuilder
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.imageModifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDMRelayListDialog(
    onClose: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val postViewModel: DMRelayListViewModel = viewModel()

    postViewModel.init(accountViewModel.account)

    LaunchedEffect(accountViewModel.account) {
        postViewModel.load()
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        SetDialogToEdgeToEdge()
        Scaffold(
            topBar = {
                SavingTopBar(
                    titleRes = R.string.dm_relays_title,
                    onCancel = {
                        postViewModel.clear()
                        onClose()
                    },
                    onPost = {
                        postViewModel.create()
                        onClose()
                    },
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

                DMRelayList(postViewModel, accountViewModel, onClose, nav)
            }
        }
    }
}

@Composable
private fun Explanation(postViewModel: DMRelayListViewModel) {
    Card(modifier = MaterialTheme.colorScheme.imageModifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringRes(id = R.string.dm_relays_not_found_editing),
            )

            Spacer(modifier = StdVertSpacer)

            Text(
                text = stringRes(id = R.string.dm_relays_not_found_examples2),
            )

            Spacer(modifier = StdVertSpacer)

            ResetDMRelaysLonger(postViewModel)
        }
    }
}

@Composable
fun ResetDMRelaysLonger(postViewModel: DMRelayListViewModel) {
    OutlinedButton(
        onClick = {
            postViewModel.deleteAll()
            DefaultDMRelayList.forEach {
                postViewModel.addRelay(relaySetupInfoBuilder(it))
            }
            postViewModel.loadRelayDocuments()
        },
    ) {
        Text(stringRes(R.string.default_relays_longer))
    }
}
