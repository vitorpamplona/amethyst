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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.components.ClickableTextPrimary
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.routeToMessage
import com.vitorpamplona.amethyst.ui.note.ErrorMessageDialog
import com.vitorpamplona.amethyst.ui.note.MoneroIcon
import com.vitorpamplona.amethyst.ui.note.creators.tipping.TippingRequestCard
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.MoneroOrange
import com.vitorpamplona.amethyst.ui.theme.Size16Modifier

@Composable
fun DisplayMoneroTipping(
    address: String?,
    userHex: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var tipExpanded by remember { mutableStateOf(false) }
    var showErrorMessageDialog by remember { mutableStateOf<String?>(null) }
    if (showErrorMessageDialog != null) {
        ErrorMessageDialog(
            title = stringRes(id = R.string.error_dialog_tip_error),
            textContent = showErrorMessageDialog ?: "",
            onClickStartMessage = {
                nav.nav {
                    routeToMessage(userHex, showErrorMessageDialog, accountViewModel = accountViewModel)
                }
            },
            onDismiss = { showErrorMessageDialog = null },
        )
    }

    if (!address.isNullOrEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MoneroIcon(modifier = Size16Modifier, tint = MoneroOrange)

            ClickableTextPrimary(
                text = address,
                onClick = { tipExpanded = !tipExpanded },
                modifier =
                    Modifier
                        .padding(top = 1.dp, bottom = 1.dp, start = 5.dp)
                        .weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (tipExpanded) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 5.dp),
            ) {
                TippingRequestCard(
                    address,
                    onSuccess = {
                        tipExpanded = false
                    },
                    onError = { title, message -> accountViewModel.toastManager.toast(title, message) },
                )
            }
        }
    }
}
