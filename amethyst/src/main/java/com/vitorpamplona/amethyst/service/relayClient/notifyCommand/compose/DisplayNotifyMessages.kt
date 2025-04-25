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
package com.vitorpamplona.amethyst.service.relayClient.notifyCommand.compose

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.relayClient.notifyCommand.model.NotifyRequestsCache
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip65RelayList.RelayUrlFormatter

@Composable
fun DisplayNotifyMessages(
    accountViewModel: AccountViewModel,
    nav: INav,
) = DisplayNotifyMessages(Amethyst.instance.notifyCoordinator.requests, accountViewModel, nav)

@Composable
fun DisplayNotifyMessages(
    requests: NotifyRequestsCache,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val openDialogMsg = requests.transientPaymentRequests.collectAsStateWithLifecycle(emptySet())

    openDialogMsg.value.firstOrNull()?.let { request ->
        NotifyRequestDialog(
            title =
                stringRes(
                    id = R.string.payment_required_title,
                    RelayUrlFormatter.displayUrl(request.relayUrl),
                ),
            textContent = request.description,
            accountViewModel = accountViewModel,
            nav = nav,
            onDismiss = { requests.dismissPaymentRequest(request) },
        )
    }
}
