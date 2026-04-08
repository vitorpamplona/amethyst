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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.SavingTopBar
import com.vitorpamplona.amethyst.ui.note.UpdateZapAmountContent
import com.vitorpamplona.amethyst.ui.note.UpdateZapAmountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet.WalletViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.placeholderText

@Composable
fun NIP47SetupScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
    nip47: String?,
) {
    val postViewModel: UpdateZapAmountViewModel = viewModel()
    postViewModel.init(accountViewModel)

    val walletViewModel: WalletViewModel = viewModel()
    walletViewModel.init(accountViewModel)

    LaunchedEffect(accountViewModel, postViewModel) {
        postViewModel.load()
        walletViewModel.loadLnAddress()
    }

    NIP47SetupScreen(postViewModel, walletViewModel, accountViewModel, nav, nip47)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NIP47SetupScreen(
    postViewModel: UpdateZapAmountViewModel,
    walletViewModel: WalletViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
    nip47: String?,
) {
    Scaffold(
        topBar = {
            SavingTopBar(
                titleRes = R.string.wallet_connect,
                isActive = postViewModel::hasChanged,
                onCancel = {
                    postViewModel.cancel()
                    nav.popBack()
                },
                onPost = {
                    postViewModel.sendPost()
                    nav.popBack()
                },
            )
        },
    ) {
        Column(Modifier.padding(it)) {
            UpdateZapAmountContent(
                postViewModel,
                onClose = {
                    postViewModel.cancel()
                    nav.popBack()
                },
                nip47,
                accountViewModel,
            ) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                LightningAddressSetupSection(walletViewModel)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                PaymentTargetsSetupRow(nav)
            }
        }
    }
}

@Composable
private fun LightningAddressSetupSection(walletViewModel: WalletViewModel) {
    val lnAddress by walletViewModel.lnAddress.collectAsState()

    Column(
        modifier = Modifier.padding(horizontal = 0.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringRes(R.string.lightning_address),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = lnAddress,
                onValueChange = { walletViewModel.updateLnAddress(it) },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = "me@mylightningnode.com",
                        color = MaterialTheme.colorScheme.placeholderText,
                    )
                },
                singleLine = true,
            )
            TextButton(onClick = { walletViewModel.saveLnAddress() }) {
                Text(text = stringRes(R.string.save))
            }
        }
    }
}

@Composable
private fun PaymentTargetsSetupRow(nav: INav) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.AccountBalanceWallet,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringRes(R.string.payment_targets),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        TextButton(onClick = { nav.nav(Route.EditPaymentTargets) }) {
            Text(
                text = stringRes(R.string.manage),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
