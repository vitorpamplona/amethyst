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
package com.vitorpamplona.amethyst.commons.relayClient.eoseManagers

import com.vitorpamplona.amethyst.commons.service.BundledUpdate
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.SubscriptionController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

abstract class BaseEoseManager<T>(
    val client: INostrClient,
    val allKeys: () -> Set<T>,
    val sampleTime: Long = 500,
) : IEoseManager {
    private val orchestrator = SubscriptionController(client)

    /**
     * A short, human-readable explanation of what this manager's subscriptions are
     * doing (e.g. "Your DMs", "Notifications", "Home feed"). Surfaced by the always-on
     * notification so the user can see what each open relay connection is for.
     *
     * Override with a friendly, CONSTANT label (do not reference constructor fields that
     * may not be initialized yet: [SingleSubEoseManager] creates its subscription in a
     * property initializer, so this getter can run before the leaf class finishes
     * constructing). When null, a readable name derived from the class name is used, so
     * every subscription is labeled even without an override.
     */
    open val subscriptionReason: String? get() = null

    abstract fun updateSubscriptions(keys: Set<T>)

    fun getSubscription(subId: String) = orchestrator.getSub(subId)

    fun requestNewSubscription(listener: SubscriptionListener) = orchestrator.requestNewSubscription(newSubId(), listener, resolveReason())

    fun requestNewSubscription(
        reason: String,
        listener: SubscriptionListener,
    ) = orchestrator.requestNewSubscription(newSubId(), listener, reason)

    private fun resolveReason(): String = subscriptionReason ?: humanizeClassName()

    /**
     * Fallback label for managers that don't override [subscriptionReason]: strips the
     * infrastructure suffix from the class name and splits camelCase into words, e.g.
     * `HomeOutboxEventsEoseManager` -> "Home Outbox Events". Not pretty for every class,
     * but always non-blank and good enough to tell subscriptions apart in the list.
     */
    private fun humanizeClassName(): String {
        val raw = this::class.simpleName ?: return "Subscription"
        val stripped =
            raw
                .removeSuffix("SubAssembler")
                .removeSuffix("SubAssembly")
                .removeSuffix("EoseManager")
                .removeSuffix("FilterAssembler")
                .removeSuffix("Assembler")
                .removeSuffix("Manager")
                .ifEmpty { raw }
        return stripped.replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), " ").trim()
    }

    fun dismissSubscription(subId: String) = orchestrator.dismissSubscription(subId)

    // Refreshes observers in batches.
    private val bundler = BundledUpdate(sampleTime, Dispatchers.IO)

    override fun invalidateFilters(ignoreIfDoing: Boolean) {
        bundler.invalidate(ignoreIfDoing, ::forceInvalidate)
    }

    fun forceInvalidate() {
        updateSubscriptions(allKeys())
        orchestrator.updateRelays()
    }

    override fun destroy() {
        bundler.cancel()
    }
}
