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
package com.vitorpamplona.amethyst

import android.util.Log
import androidx.compose.runtime.Stable
import coil3.annotation.DelicateCoilApi
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.NostrAccountDataSource
import com.vitorpamplona.amethyst.service.NostrChannelDataSource
import com.vitorpamplona.amethyst.service.NostrChatroomDataSource
import com.vitorpamplona.amethyst.service.NostrChatroomListDataSource
import com.vitorpamplona.amethyst.service.NostrCommunityDataSource
import com.vitorpamplona.amethyst.service.NostrDiscoveryDataSource
import com.vitorpamplona.amethyst.service.NostrGeohashDataSource
import com.vitorpamplona.amethyst.service.NostrHashtagDataSource
import com.vitorpamplona.amethyst.service.NostrHomeDataSource
import com.vitorpamplona.amethyst.service.NostrSearchEventOrUserDataSource
import com.vitorpamplona.amethyst.service.NostrSingleChannelDataSource
import com.vitorpamplona.amethyst.service.NostrSingleEventDataSource
import com.vitorpamplona.amethyst.service.NostrSingleUserDataSource
import com.vitorpamplona.amethyst.service.NostrThreadDataSource
import com.vitorpamplona.amethyst.service.NostrUserProfileDataSource
import com.vitorpamplona.amethyst.service.NostrVideoDataSource
import com.vitorpamplona.amethyst.service.eventCache.MemoryTrimmingService
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@Stable
class ServiceManager(
    val client: NostrClient,
    val scope: CoroutineScope,
) {
    // to not open amber in a loop trying to use auth relays and registering for notifications
    private var isStarted: Boolean = false

    private var account: Account? = null

    private var collectorJob: Job? = null

    private val trimmingService = MemoryTrimmingService()

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

        Amethyst.instance.setImageLoader(myAccount?.shouldUseTorForImageDownload())

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

            // start services
            NostrAccountDataSource.account = myAccount
            NostrHomeDataSource.account = myAccount
            NostrChatroomListDataSource.account = myAccount
            NostrVideoDataSource.account = myAccount
            NostrDiscoveryDataSource.account = myAccount

            NostrAccountDataSource.otherAccounts =
                runBlocking {
                    LocalPreferences.allSavedAccounts().mapNotNull {
                        try {
                            it.npub.bechToBytes().toHexKey()
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            null
                        }
                    }
                }

            // Notification Elements
            NostrHomeDataSource.start()
            NostrAccountDataSource.start()
            GlobalScope.launch(Dispatchers.IO) {
                delay(3000)
                NostrChatroomListDataSource.start()
                NostrDiscoveryDataSource.start()
                NostrVideoDataSource.start()
            }

            // More Info Data Sources
            NostrSingleEventDataSource.start()
            NostrSingleChannelDataSource.start()
            NostrSingleUserDataSource.start()
            isStarted = true
        }
    }

    private fun pause() {
        Log.d("ServiceManager", "-- Pausing Relay Services")

        collectorJob?.cancel()
        collectorJob = null

        NostrAccountDataSource.stopSync()
        NostrHomeDataSource.stopSync()
        NostrChannelDataSource.stopSync()
        NostrChatroomDataSource.stopSync()
        NostrChatroomListDataSource.stopSync()
        NostrDiscoveryDataSource.stopSync()

        NostrCommunityDataSource.stopSync()
        NostrHashtagDataSource.stopSync()
        NostrGeohashDataSource.stopSync()
        NostrSearchEventOrUserDataSource.stopSync()
        NostrSingleChannelDataSource.stopSync()
        NostrSingleEventDataSource.stopSync()
        NostrSingleUserDataSource.stopSync()
        NostrThreadDataSource.stopSync()
        NostrUserProfileDataSource.stopSync()
        NostrVideoDataSource.stopSync()

        client.reconnect(null)
        isStarted = false
    }

    fun cleanObservers() {
        LocalCache.cleanObservers()
    }

    suspend fun trimMemory() {
        trimmingService.run(account)
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
