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
package com.vitorpamplona.amethyst.service.crashreports

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeToMessage
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size16dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DisplayCrashMessages(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val stackTrace = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(accountViewModel) {
        withContext(Dispatchers.IO) {
            stackTrace.value = Amethyst.instance.crashReportCache.loadAndDelete()
        }
    }

    stackTrace.value?.let { stack ->
        AlertDialog(
            onDismissRequest = { stackTrace.value = null },
            title = { Text(stringResource(R.string.crashreport_found)) },
            text = {
                SelectionContainer {
                    Text(stringResource(R.string.would_you_like_to_send_the_recent_crash_report_to_amethyst_in_a_dm_no_personal_information_will_be_shared))
                }
            },
            dismissButton = {
                TextButton(onClick = { stackTrace.value = null }) {
                    Text(stringRes(R.string.cancel))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        nav.nav {
                            routeToMessage(
                                user = LocalCache.getOrCreateUser("aa9047325603dacd4f8142093567973566de3b1e20a89557b728c3be4c6a844b"),
                                draftMessage = stack,
                                accountViewModel = accountViewModel,
                                expiresDays = 30,
                            )
                        }
                        stackTrace.value = null
                    },
                    contentPadding = PaddingValues(horizontal = Size16dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Done,
                            contentDescription = stringRes(R.string.crashreport_found_send),
                        )
                        Spacer(StdHorzSpacer)
                        Text(stringRes(R.string.crashreport_found_send))
                    }
                }
            },
        )
    }
}
