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
package com.vitorpamplona.amethyst.commons.viewmodels

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.logTime
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * An account's feeds ([AccountFeedContentStates]), outliving the `AccountViewModel` that shows them.
 *
 * Feeds keep the notes they show, and their relay cursors vouch for those notes. When the feeds died
 * with the view model, an account switch threw both away and built them again on the way back:
 * every feed from the cache, every REQ from scratch. Now view models lease the account's feeds:
 *
 * - **Active** while some view model holds a lease: event bundles reach the feeds.
 * - **Warm** for [warmFor] after the last lease ends: the feeds keep their notes and cursors and stop
 *   taking events, which are set aside instead. A lease taken now hands the feeds what was set aside
 *   (or, past [MAX_MISSED] of them, refreshes the built feeds from the cache), and their cursors ask
 *   the relays for exactly what came since.
 * - **Cold** after that, on memory pressure while warm, or when the account's scope ends (a
 *   logout): the feeds are released with their cursors, and the next lease builds new ones.
 *
 * The feeds run on a child of the account's scope, so nothing here outlives the account.
 */
class AccountFeeds private constructor(
    val account: Account,
    memoryPressure: Flow<Unit>,
    private val warmFor: Duration,
) {
    private val job = SupervisorJob(account.scope.coroutineContext[Job])
    private val scope = CoroutineScope(account.scope.coroutineContext + job)

    val states = AccountFeedContentStates(account, scope, memoryPressure)

    private val lock = KmpLock()
    private var leases = 0
    private var everLeased = false
    private var updates: Job? = null
    private var coldTimer: Job? = null
    private var cold = false

    // What arrives while warm, for the next lease: cheaper than a rebuild, which walks the cache.
    private class Missed {
        val added = LinkedHashSet<Note>()
        val deleted = LinkedHashSet<Note>()
        var overflowed = false

        fun onAdded(notes: Set<Note>) = take { added.addAll(notes) }

        // A note deleted after it arrived is not handed to the feeds at all.
        fun onDeleted(notes: Set<Note>) =
            take {
                added.removeAll(notes)
                deleted.addAll(notes)
            }

        private inline fun take(block: () -> Unit) {
            if (overflowed) return
            block()
            // Past this, a rebuild is cheaper than holding them all, and the set is let go.
            if (added.size + deleted.size > MAX_MISSED) {
                overflowed = true
                added.clear()
                deleted.clear()
            }
        }
    }

    private var missed: Missed? = null
    private var setAside: Job? = null
    private var initialized = false

    init {
        // Under memory pressure a warm set is the first thing to give back.
        scope.launch(Dispatchers.IO) { memoryPressure.collect { if (lock.withLock { leases == 0 }) goCold() } }
    }

    /** One view model's hold on these feeds; [release] it when the view model is cleared. */
    inner class Lease internal constructor() {
        private var released = false

        val states: AccountFeedContentStates get() = this@AccountFeeds.states

        fun release() {
            if (released) return
            released = true
            this@AccountFeeds.release()
        }
    }

    /** Null when these feeds went cold in the meantime: the registry builds new ones. */
    private fun acquire(): Lease? {
        val warm =
            lock.withLock {
                if (cold) return null
                coldTimer?.cancel()
                coldTimer = null
                leases++
                (leases == 1).also { first -> if (first) startUpdates(replay = if (everLeased) takeMissed() else null) }.also { everLeased = true }
            }
        if (warm) Log.d(TAG) { "${account.userProfile().pubkeyHex.take(8)}: active" }
        return Lease()
    }

    private fun release() {
        lock.withLock {
            leases--
            if (leases > 0) return
            updates?.cancel()
            updates = null
            setAsideWhileWarm()
            coldTimer =
                scope.launch {
                    delay(warmFor)
                    goCold()
                }
        }
        Log.d(TAG) { "${account.userProfile().pubkeyHex.take(8)}: warm for $warmFor" }
    }

    // Under [lock]. Collects what the feeds miss while warm, until the next lease takes it.
    private fun setAsideWhileWarm() {
        val into = Missed().also { missed = it }
        setAside =
            scope.launch(Dispatchers.IO) {
                // Once a lease took the set, whatever is still in flight goes to the feeds instead.
                launch {
                    LocalCache.live.deletedEventBundles
                        .coalesced()
                        .collect { notes -> lock.withLock { if (missed === into) into.onDeleted(notes) } }
                }
                LocalCache.live.newEventBundles
                    .coalesced()
                    .collect { notes -> lock.withLock { if (missed === into) into.onAdded(notes) } }
            }
    }

    // Under [lock].
    private fun takeMissed(): Missed? {
        setAside?.cancel()
        setAside = null
        return missed.also { missed = null }
    }

    // Under [lock]. [replay] is what a warm set missed; null on the first lease.
    private fun startUpdates(replay: Missed?) {
        updates =
            scope.launch(Dispatchers.IO) {
                if (!initialized) {
                    states.init()
                    initialized = true
                }
                if (replay != null) {
                    // Never collected (a lease right after a release): nothing was missed but the race.
                    if (replay.overflowed) {
                        states.refreshBuiltFeeds()
                    } else {
                        if (replay.deleted.isNotEmpty()) states.deleteNotes(replay.deleted)
                        if (replay.added.isNotEmpty()) states.updateFeedsWith(replay.added)
                    }
                }
                // Coalesced, not dropped: the shared flow drops a slow collector's oldest bundles, and
                // the feeds would never learn of those notes (Home no longer rebuilds on every resume
                // to cover for it). Bundles that arrive while the feeds are busy merge into one.
                launch {
                    LocalCache.live.deletedEventBundles.coalesced().collect { notes ->
                        logTime("AccountFeeds deletedEventBundle Update with ${notes.size} notes") { states.deleteNotes(notes) }
                    }
                }
                LocalCache.live.newEventBundles.coalesced().collect { notes ->
                    logTime("AccountFeeds newEventBundle Update with ${notes.size} new notes") { states.updateFeedsWith(notes) }
                }
            }
    }

    private fun goCold() {
        lock.withLock {
            if (leases > 0 || cold) return
            cold = true
        }
        Log.d(TAG) { "${account.userProfile().pubkeyHex.take(8)}: cold, feeds released" }
        job.cancel()
    }

    private var destroyed = false

    // Once, whichever ends these feeds first: going cold, or the account's scope (a logout).
    private fun onEnded() {
        lock.withLock {
            cold = true
            if (destroyed) return
            destroyed = true
        }
        forget(this)
        states.destroy()
    }

    companion object {
        private const val TAG = "AccountFeeds"

        /** How long an account's feeds stay warm after the last view model lets go of them. */
        val WARM_FOR = 15.minutes

        /** How many missed notes a warm set keeps for its next lease before it settles for a rebuild. */
        const val MAX_MISSED = 5_000

        private val registryLock = KmpLock()

        // By identity: a new Account object for the same key (a log out and back in) gets new feeds.
        private val registry = mutableMapOf<Account, AccountFeeds>()

        private fun forget(feeds: AccountFeeds) = registryLock.withLock { if (registry[feeds.account] === feeds) registry.remove(feeds.account) }

        /** A lease on [account]'s feeds, warm ones when there are, else new ones. */
        fun lease(
            account: Account,
            memoryPressure: Flow<Unit>,
            warmFor: Duration = WARM_FOR,
        ): Lease {
            while (true) {
                // An account whose scope already ended (a logout racing a screen): its feeds' job is
                // born complete and would go cold before the registry kept them, so every retry here
                // built another set. It gets feeds of its own that take no events and are never shared.
                if (account.scope.coroutineContext[Job]?.isActive == false) {
                    return AccountFeeds(account, memoryPressure, warmFor).acquire() ?: error("unreachable: not registered, never cold")
                }
                val feeds =
                    registryLock.withLock {
                        registry.getOrPut(account) {
                            AccountFeeds(account, memoryPressure, warmFor).also { created ->
                                // Going cold cancels it; a logout ends the account's scope, and it with it.
                                created.job.invokeOnCompletion { created.onEnded() }
                            }
                        }
                    }
                feeds.acquire()?.let { return it }
                forget(feeds)
            }
        }
    }
}
