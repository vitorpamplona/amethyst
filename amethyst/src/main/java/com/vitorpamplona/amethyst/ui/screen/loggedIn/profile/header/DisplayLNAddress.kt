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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.header

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.ui.actions.InformationDialog
import com.vitorpamplona.amethyst.ui.components.ClickableTextPrimary
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeToMessage
import com.vitorpamplona.amethyst.ui.note.ErrorMessageDialog
import com.vitorpamplona.amethyst.ui.note.LightningAddressIcon
import com.vitorpamplona.amethyst.ui.note.creators.invoice.InvoiceRequestCard
import com.vitorpamplona.amethyst.ui.note.payViaIntent
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.Size16Modifier
import com.vitorpamplona.quartz.nip47WalletConnect.PayInvoiceErrorResponse
import com.vitorpamplona.quartz.nip47WalletConnect.PayInvoiceSuccessResponse

@Composable
fun DisplayLNAddress(
    lud16: String?,
    user: User,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val context = LocalContext.current
    var zapExpanded by remember { mutableStateOf(false) }

    var showErrorMessageDialog by remember { mutableStateOf<String?>(null) }

    if (showErrorMessageDialog != null) {
        ErrorMessageDialog(
            title = stringRes(id = R.string.error_dialog_zap_error),
            textContent = showErrorMessageDialog ?: "",
            onClickStartMessage = {
                nav.nav {
                    routeToMessage(user, showErrorMessageDialog, accountViewModel = accountViewModel)
                }
            },
            onDismiss = { showErrorMessageDialog = null },
        )
    }

    var showInfoMessageDialog by remember { mutableStateOf<String?>(null) }
    if (showInfoMessageDialog != null) {
        InformationDialog(
            title = stringRes(context, R.string.payment_successful),
            textContent = showInfoMessageDialog ?: "",
        ) {
            showInfoMessageDialog = null
        }
    }

    if (!lud16.isNullOrEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LightningAddressIcon(modifier = Size16Modifier, tint = BitcoinOrange)

            ClickableTextPrimary(
                text = lud16,
                onClick = { zapExpanded = !zapExpanded },
                modifier =
                    Modifier
                        .padding(top = 1.dp, bottom = 1.dp, start = 5.dp)
                        .weight(1f),
            )
        }

        if (zapExpanded) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 5.dp),
            ) {
                InvoiceRequestCard(
                    lud16,
                    user,
                    accountViewModel,
                    onSuccess = {
                        zapExpanded = false
                        // pay directly
                        if (accountViewModel.account.nip47SignerState.hasWalletConnectSetup()) {
                            accountViewModel.sendZapPaymentRequestFor(it, null) { response ->
                                if (response is PayInvoiceSuccessResponse) {
                                    showInfoMessageDialog = stringRes(context, R.string.payment_successful)
                                } else if (response is PayInvoiceErrorResponse) {
                                    showErrorMessageDialog =
                                        response.error?.message
                                            ?: response.error?.code?.toString()
                                            ?: stringRes(context, R.string.error_parsing_error_message)
                                }
                            }
                        } else {
                            payViaIntent(it, context, { zapExpanded = false }, { showErrorMessageDialog = it })
                        }
                    },
                    onError = { title, message -> accountViewModel.toastManager.toast(title, message) },
                )
            }
        }
    }
}
