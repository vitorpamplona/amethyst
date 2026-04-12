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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common

import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.BasicRelaySetupInfo
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.BasicRelaySetupInfoState
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

abstract class BasicRelaySetupInfoModel : BasicRelaySetupInfoState() {
    lateinit var accountViewModel: AccountViewModel
    lateinit var account: Account

    fun init(accountViewModel: AccountViewModel) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
    }

    override fun launchSigner(block: suspend () -> Unit) {
        accountViewModel.launchSigner {
            block()
        }
    }

    override suspend fun loadRelayInfo(
        relay: NormalizedRelayUrl,
        onPaid: (Boolean) -> Unit,
    ) {
        Amethyst.instance.nip11Cache.loadRelayInfo(
            relay = relay,
            onInfo = {
                onPaid(it.limitation?.payment_required ?: false)
            },
            onError = { url, errorCode, exceptionMessage -> },
        )
    }

    override fun buildRelaySetupInfo(normalized: NormalizedRelayUrl): BasicRelaySetupInfo =
        relaySetupInfoBuilder(
            normalized = normalized,
            forcesTor =
                Amethyst.instance.torEvaluatorFlow.flow.value
                    .useTor(normalized),
        )

    override fun getClient(): INostrClient = account.client
}
