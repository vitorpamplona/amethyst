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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.rememberHeightDecreaser
import com.vitorpamplona.amethyst.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.ui.note.UpdateZapAmountContent
import com.vitorpamplona.amethyst.ui.note.UpdateZapAmountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.SaveButton
import com.vitorpamplona.amethyst.ui.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NIP47SetupScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
    nip47: String?,
) {
    val postViewModel: UpdateZapAmountViewModel =
        viewModel(
            key = "UpdateZapAmountViewModel",
            factory = UpdateZapAmountViewModel.Factory(accountViewModel.account.settings),
        )

    Scaffold(
        topBar = {
            TopAppBar(
                scrollBehavior = rememberHeightDecreaser(),
                title = { Text(stringRes(id = R.string.wallet_connect)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            postViewModel.cancel()
                            nav.popBack()
                        },
                        modifier = Modifier,
                    ) {
                        ArrowBackIcon()
                    }
                },
                actions = {
                    SaveButton(
                        onPost = {
                            postViewModel.sendPost()
                            nav.popBack()
                        },
                        isActive = postViewModel.hasChanged(),
                    )
                },
            )
        },
    ) {
        Column(Modifier.padding(it)) {
            UpdateZapAmountContent(postViewModel, onClose = {
                postViewModel.cancel()
                nav.popBack()
            }, nip47, accountViewModel)
        }
    }
}
