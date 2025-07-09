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
package com.vitorpamplona.amethyst.service.relayClient.eoseManagers

import android.util.Log
import com.vitorpamplona.amethyst.isDebug
import com.vitorpamplona.ammolite.relays.BundledUpdate
import com.vitorpamplona.ammolite.relays.datasources.SubscriptionController
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.Dispatchers

abstract class BaseEoseManager<T>(
    val client: NostrClient,
    val allKeys: () -> Set<T>,
) {
    protected val logTag: String = this.javaClass.simpleName

    private val orchestrator = SubscriptionController(client)

    abstract fun updateSubscriptions(keys: Set<T>)

    fun printStats() = orchestrator.printStats(logTag)

    fun newSubscriptionId() = if (isDebug) logTag + newSubId() else newSubId()

    fun getSubscription(subId: String) = orchestrator.getSub(subId)

    fun requestNewSubscription(onEOSE: ((Long, NormalizedRelayUrl) -> Unit)? = null) = orchestrator.requestNewSubscription(newSubscriptionId(), onEOSE)

    fun dismissSubscription(subId: String) = orchestrator.dismissSubscription(subId)

    // Refreshes observers in batches.
    private val bundler = BundledUpdate(300, Dispatchers.Default)

    fun invalidateFilters() {
        bundler.invalidate {
            forceInvalidate()
        }
    }

    fun forceInvalidate() {
        updateSubscriptions(allKeys())

        orchestrator.updateRelays()
    }

    fun destroy() {
        bundler.cancel()
        orchestrator.destroy()
        if (isDebug) {
            Log.d(logTag, "Destroy, Unsubscribe")
        }
    }
}
