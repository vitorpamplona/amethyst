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
package com.vitorpamplona.amethyst.service.relayClient.authCommand.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.service.relayClient.authCommand.model.AuthCoordinator
import com.vitorpamplona.amethyst.service.relayClient.authCommand.model.RelayAuthPermissionLedger
import com.vitorpamplona.amethyst.service.relayClient.authCommand.model.ScreenAuthAccount
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun RelayAuthSubscription(accountViewModel: AccountViewModel) = RelayAuthSubscription(accountViewModel, Amethyst.instance.authCoordinator)

@Composable
fun RelayAuthSubscription(
    accountViewModel: AccountViewModel,
    dataSource: AuthCoordinator,
) {
    val account = accountViewModel.account

    val state =
        remember(accountViewModel) {
            ScreenAuthAccount(account)
        }

    val ledger =
        remember(accountViewModel) {
            RelayAuthPermissionLedger(
                store = Amethyst.instance.relayAuthPermissionStore,
                globalPolicy = { account.settings.defaultRelayAuthPolicy.value },
                isInMyRelayList = { relayUrl ->
                    account.settings.localRelayServers.value.any {
                        it.trimEnd('/') == relayUrl.trimEnd('/')
                    }
                },
            )
        }

    DisposableEffect(state, ledger) {
        dataSource.subscribe(state)
        dataSource.subscribeLedger(ledger)
        onDispose {
            dataSource.unsubscribe(state)
            dataSource.unsubscribeLedger(ledger)
        }
    }
}
