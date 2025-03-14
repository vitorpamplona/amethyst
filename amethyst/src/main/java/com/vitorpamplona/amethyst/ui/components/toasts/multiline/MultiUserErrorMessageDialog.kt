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
package com.vitorpamplona.amethyst.ui.components.toasts.multiline

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.navigation.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size16dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@Composable
@Preview
fun MultiUserErrorMessageContentPreview() {
    val accountViewModel = mockAccountViewModel()
    val nav = EmptyNav

    var user1: User? = null
    var user2: User? = null
    var user3: User? = null

    runBlocking {
        withContext(Dispatchers.IO) {
            user1 = LocalCache.getOrCreateUser("aaabccaabbccaabbccabdd")
            user2 = LocalCache.getOrCreateUser("bbbccabbbccabbbccaabdd")
            user3 = LocalCache.getOrCreateUser("ccaadaccaadaccaadaabdd")
        }
    }

    val model = MultiErrorToastMsg(R.string.error_dialog_zap_error)
    model.add("Could not fetch invoice from https://minibits.cash/.well-known/lnurlp/victorieeman: There are too many unpaid invoices for this name.", user1)
    model.add("No Wallets found to pay a lightning invoice. Please install a Lightning wallet to use zaps.", user2)
    model.add("Could not fetch invoice", user3)

    ThemeComparisonColumn {
        MultiUserErrorMessageDialog(
            model = model,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
fun MultiUserErrorMessageDialog(
    model: MultiErrorToastMsg,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    AlertDialog(
        onDismissRequest = accountViewModel.toastManager::clearToasts,
        title = { Text(stringRes(model.titleResId)) },
        text = {
            ErrorList(model, accountViewModel, nav)
        },
        confirmButton = {
            Button(
                onClick = accountViewModel.toastManager::clearToasts,
                contentPadding = PaddingValues(horizontal = Size16dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Done,
                    contentDescription = null,
                )
                Spacer(StdHorzSpacer)
                Text(stringRes(R.string.error_dialog_button_ok))
            }
        },
    )
}
