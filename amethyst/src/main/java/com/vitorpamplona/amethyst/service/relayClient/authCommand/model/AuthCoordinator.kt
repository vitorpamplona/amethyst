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
package com.vitorpamplona.amethyst.service.relayClient.authCommand.model

import android.util.Log
import com.vitorpamplona.amethyst.isDebug
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.datasources.RelayAuthenticator

class ScreenAuthAccount(
    val account: Account,
)

class AuthCoordinator(
    client: NostrClient,
) {
    private val authWithAccounts = ListWithUniqueSetCache<ScreenAuthAccount, Account> { it.account }

    val receiver =
        RelayAuthenticator(client) { challenge, relay ->
            authWithAccounts.distinct().forEach {
                it.sendAuthEvent(relay, challenge)
            }
        }

    fun destroy() {
        receiver.destroy()
    }

    // This is called by main. Keep it really fast.
    fun subscribe(account: ScreenAuthAccount?) {
        if (account == null) return

        if (isDebug) {
            Log.d(this::class.simpleName, "Watch $account")
        }

        authWithAccounts.add(account)
    }

    // This is called by main. Keep it really fast.
    fun unsubscribe(account: ScreenAuthAccount?) {
        if (account == null) return

        if (isDebug) {
            Log.d(this::class.simpleName, "Unwatch $account")
        }

        authWithAccounts.remove(account)
    }
}
