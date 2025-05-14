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
package com.vitorpamplona.amethyst

import android.util.Log
import androidx.compose.runtime.Stable
import coil3.annotation.DelicateCoilApi
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.ammolite.relays.NostrClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Stable
class ServiceManager(
    val client: NostrClient,
    val scope: CoroutineScope,
) {
    // to not open amber in a loop trying to use auth relays and registering for notifications
    private var isStarted: Boolean = false

    private var account: Account? = null

    private var collectorJob: Job? = null

    private fun start(account: Account) {
        this.account = account
        start()
    }

    @OptIn(DelicateCoilApi::class)
    private fun start() {
        Log.d("ServiceManager", "-- May Start (hasStarted: $isStarted) for account $account")
        if (isStarted && account != null) {
            Log.d("ServiceManager", "---- Restarting innactive relay Services with Tor: ${account?.settings?.torSettings?.torType?.value}")
            client.reconnect()
            return
        }
        Log.d("ServiceManager", "---- Starting Relay Services with Tor: ${account?.settings?.torSettings?.torType?.value}")

        val myAccount = account

        if (myAccount != null) {
            val relaySet = myAccount.connectToRelaysWithProxy.value
            client.reconnect(relaySet)

            collectorJob?.cancel()
            collectorJob = null
            collectorJob =
                scope.launch {
                    myAccount.connectToRelaysWithProxy.collectLatest {
                        delay(500)
                        if (isStarted) {
                            client.reconnect(it, onlyIfChanged = true)
                        }
                    }
                }

            isStarted = true
        }
    }

    private fun pause() {
        Log.d("ServiceManager", "-- Pausing Relay Services")

        collectorJob?.cancel()
        collectorJob = null

        client.reconnect(null)
        isStarted = false
    }

    fun cleanObservers() {
        LocalCache.cleanObservers()
    }

    // This method keeps the pause/start in a Syncronized block to
    // avoid concurrent pauses and starts.
    @Synchronized
    fun forceRestart(
        account: Account? = null,
        start: Boolean = true,
        pause: Boolean = true,
    ) {
        Log.d("ServiceManager", "-- Force Restart (start:$start) (pause:$pause) for $account")
        if (pause) {
            pause()
        }

        if (start) {
            if (account != null) {
                start(account)
            } else {
                start()
            }
        }
    }

    fun setAccountAndRestart(account: Account) {
        forceRestart(account, true, true)
    }

    fun forceRestart() {
        forceRestart(null, true, true)
    }

    fun justStartIfItHasAccount() {
        if (account != null) {
            forceRestart(null, true, false)
        }
    }

    fun pauseForGood() {
        forceRestart(null, false, true)
    }

    fun pauseAndLogOff() {
        account = null
        forceRestart(null, false, true)
    }
}
