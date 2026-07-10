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
package com.vitorpamplona.amethyst.commons.model.concord

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The account-scoped owner of the live Concord read-path. It keeps the
 * [ConcordSessionRegistry] in step with the joined-communities list and turns
 * each community's folds into a single observable tick the app layer watches to
 * (re)derive subscription filters and refresh its channel index.
 *
 * Wiring, per account:
 *  - Construct once with the account's `liveCommunities` flow, its pubkey, and a
 *    long-lived [scope]. It self-starts a collector that [ConcordSessionRegistry.sync]s
 *    on every list change and, for each **new** session, watches its
 *    [ConcordCommunitySession.state] so a fold bumps [revision].
 *  - Feed every inbound kind-1059 wrap through [ingest]; a Concord plane wrap is
 *    applied (control → re-fold, channel → re-project) and returns true, so the
 *    caller can stop treating it as a NIP-59 DM.
 *  - Read [subscribeAddresses] to build the `authors` set for the kind-1059
 *    subscription; re-read it whenever [revision] advances (a fold reveals new
 *    channel planes to watch).
 *
 * Everything platform-specific (the LocalCache channel index, the actual REQ
 * mounting) stays in the app layer, which reacts to [revision]; this class holds
 * no Android/UI dependency so it stays unit-testable.
 */
class ConcordSessionManager(
    private val communities: StateFlow<List<ConcordCommunityListEntry>>,
    private val myPubKey: HexKey,
    private val scope: CoroutineScope,
    private val onRumor: ConcordRumorSink = { _, _, _ -> },
) {
    val registry = ConcordSessionRegistry(onRumor)

    private val _revision = MutableStateFlow(0)

    /** Monotonic counter bumped whenever the joined set or any community's fold changes. */
    val revision: StateFlow<Int> = _revision

    private val lock = KmpLock()
    private val stateWatchers = HashMap<HexKey, Job>() // communityId -> state collector

    init {
        scope.launch {
            communities.collect { entries -> onCommunitiesChanged(entries) }
        }
    }

    private fun onCommunitiesChanged(entries: List<ConcordCommunityListEntry>) {
        val created = registry.sync(entries, myPubKey)
        val wantedIds = entries.mapTo(HashSet()) { it.id }

        lock.withLock {
            // Cancel watchers for communities we've left.
            val departed = stateWatchers.keys.filterNot { it in wantedIds }
            for (id in departed) stateWatchers.remove(id)?.cancel()

            // Watch each newly-created session so its folds bump the revision.
            for (id in created) {
                val session = registry.sessionFor(id) ?: continue
                stateWatchers[id] =
                    scope.launch {
                        session.state.collect { bumpRevision() }
                    }
            }
        }
        bumpRevision()
    }

    private fun bumpRevision() {
        _revision.value = _revision.value + 1
    }

    /** The `authors` set (control + known channel planes) for the kind-1059 subscription. */
    fun subscribeAddresses(): Set<HexKey> = registry.subscribeAddresses()

    /** Route an inbound stream wrap; true if it was a Concord plane wrap we applied. */
    fun ingest(wrap: Event): Boolean {
        val applied = registry.ingest(wrap)
        if (applied) bumpRevision()
        return applied
    }

    fun sessions() = registry.sessions()

    fun sessionFor(communityId: HexKey) = registry.sessionFor(communityId)

    fun destroy() {
        lock.withLock {
            stateWatchers.values.forEach { it.cancel() }
            stateWatchers.clear()
        }
        registry.clear()
    }
}
