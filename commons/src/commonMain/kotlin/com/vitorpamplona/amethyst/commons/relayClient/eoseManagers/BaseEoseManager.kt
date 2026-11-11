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

import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.amethyst.commons.relayClient.AccountScopedQuery
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.amethyst.commons.service.BundledUpdate
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.SubscriptionController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

abstract class BaseEoseManager<T>(
    val client: INostrClient,
    val allKeys: () -> Set<T>,
    val sampleTime: Long = 500,
) : IEoseManager {
    private val orchestrator = SubscriptionController(client)

    abstract fun updateSubscriptions(keys: Set<T>)

    fun getSubscription(subId: String) = orchestrator.getSub(subId)

    /**
     * Opens a subscription whose EOSEs and live events also reach the cursors of the holders it
     * serves at that moment (see [serve]), whatever key its [listener] captured.
     */
    fun requestNewSubscription(listener: SubscriptionListener): Subscription {
        val subId = newSubId()
        return orchestrator.requestNewSubscription(subId, CursorRoutingListener(listener, cursorSource) { cursorsServedBy(subId) })
    }

    fun dismissSubscription(subId: String) {
        serve(subId, emptyList())
        orchestrator.dismissSubscription(subId)
    }

    /** Names this manager's entries in the holders' cursors (see [CursorSource]). */
    protected val cursorSource = CursorSource(this::class.simpleName ?: "EoseManager")

    private val servedLock = KmpLock()
    private val served = mutableMapOf<String, List<RelayCursors>>()

    private fun cursorsServedBy(subId: String): List<RelayCursors> = servedLock.withLock { served[subId].orEmpty() }

    /** Records whose cursors [subId]'s answers go to: the holders its current keys name. */
    protected fun serve(
        subId: String,
        cursors: List<RelayCursors>,
    ) {
        val before =
            servedLock.withLock {
                (if (cursors.isEmpty()) served.remove(subId) else served.put(subId, cursors)).orEmpty()
            }
        // Each holder knows who serves it, so clearing it (a widened filter, a prune) re-plans them.
        before.filter { old -> cursors.none { it === old } }.forEach { it.removeServer(this) }
        cursors.filter { new -> before.none { it === new } }.forEach { it.addServer(this) }
    }

    /**
     * The holders of the notes [key]'s subscription loads, whose cursors set its windows and receive
     * its EOSEs. By default what a [CursorHoldingQuery] key names; a manager whose keys serve other
     * managers too (one key type for every account loader) names them here instead.
     */
    protected open fun cursorHoldersOf(key: T): List<RelayCursorHolder> = (key as? CursorHoldingQuery)?.cursorHolders.orEmpty()

    /** The cursors of every holder [keys] name, once each; empty when none of them name any. */
    fun cursorsNamedBy(keys: Collection<T>): List<RelayCursors> = keys.flatMap { cursorHoldersOf(it) }.map { it.relayCursors }.distinct()

    /**
     * The filters for a subscription serving [keys]. When the keys name holders of notes, their
     * cursors set the windows ([CursorPlanner.window]) and [ownSince] is not read: the manager's own
     * map stays only for keys that name none.
     */
    protected inline fun filtersFor(
        keys: Collection<T>,
        ownSince: () -> SincePerRelayMap?,
        build: (SincePerRelayMap?) -> List<RelayBasedFilter>?,
    ): Pair<List<RelayBasedFilter>?, List<RelayCursors>> {
        val cursors = cursorsNamedBy(keys)
        if (cursors.isEmpty()) return build(ownSince()) to cursors
        return build(null)?.let { CursorPlanner.window(it, cursors, cursorSource) } to cursors
    }

    // Refreshes observers in batches.
    private val bundler = BundledUpdate(sampleTime, Dispatchers.IO)

    override fun invalidateFilters(ignoreIfDoing: Boolean) {
        bundler.invalidate(ignoreIfDoing, ::forceInvalidate)
    }

    fun forceInvalidate() {
        updateSubscriptions(allKeys())
        orchestrator.updateRelays()
    }

    /**
     * The Settings › Messages toggles this manager's filters read. While a subscription is open, a flip
     * of any of them (once applied — see [com.vitorpamplona.amethyst.commons.model.chats.ChatFeedToggles])
     * calls [onChatFeedsToggled]. Empty for managers that don't depend on one.
     */
    protected open val watchedChatFeeds: Set<ChatFeedType> = emptySet()

    /** A watched toggle flipped: rebuilds the filters. Override to reset state that depends on it first. */
    protected open fun onChatFeedsToggled() = invalidateFilters()

    private val chatFeedWatchers by lazy { ChatFeedWatchers(watchedChatFeeds) { onChatFeedsToggled() } }

    /**
     * For the base classes: call with each subscription's key before building its filters, so a toggle
     * flip can't fall between the build and the watcher. A no-op for keys not scoped to an account.
     */
    protected fun watchChatFeeds(
        subKey: Any,
        key: T,
    ) {
        (key as? AccountScopedQuery)?.let { chatFeedWatchers.ensure(subKey, it.account) }
    }

    /** For the base classes: call when the subscription for [subKey] ends. */
    protected fun unwatchChatFeeds(subKey: Any) = chatFeedWatchers.stop(subKey)

    override fun destroy() {
        bundler.cancel()
    }
}
