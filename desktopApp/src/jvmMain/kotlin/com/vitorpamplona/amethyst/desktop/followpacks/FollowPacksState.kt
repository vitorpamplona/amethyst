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
package com.vitorpamplona.amethyst.desktop.followpacks

import com.vitorpamplona.amethyst.commons.model.nip02FollowList.Kind3FollowListState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.RelayConnectionManager
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Single state holder for everything follow-pack-related on Desktop.
 *
 * Sources:
 *  - Featured pack: random pick from cached 39089 events (refreshed by Shuffle).
 *  - Followed-author packs: 39089 events authored by accounts the user follows.
 *  - All packs: every 39089 in the cache (used by Discover gallery / Browse-all).
 *
 * Subscribes to a multi-relay set for global pack discovery.
 * (relay.following.space doesn't actually exist — using popular indexer relays.)
 */
class FollowPacksState(
    private val cache: DesktopLocalCache,
    private val relayManager: RelayConnectionManager,
    private val kind3FollowList: Kind3FollowListState,
    private val scope: CoroutineScope,
) {
    private val followedAuthors: StateFlow<Set<HexKey>> =
        kind3FollowList.flow
            .map { it.authors }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = kind3FollowList.flow.value.authors,
            )

    companion object {
        private const val SUB_ID = "discover-follow-packs"

        // Multi-relay set for global pack discovery.
        // following.space (mentioned in plan) does NOT have its own relay —
        // it uses these popular relays. Verified empirically.
        val DISCOVERY_RELAYS: Set<NormalizedRelayUrl> =
            listOfNotNull(
                RelayUrlNormalizer.normalizeOrNull("wss://relay.nostr.band"),
                RelayUrlNormalizer.normalizeOrNull("wss://relay.primal.net"),
                RelayUrlNormalizer.normalizeOrNull("wss://nos.lol"),
            ).toSet()
    }

    /** All 39089 events currently in the cache. Recomputes on cache change. */
    val allPacks: StateFlow<List<FollowListEvent>> =
        cache.followPackVersion
            .map { cache.snapshotFollowPacks().sortedByDescending { it.createdAt } }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = cache.snapshotFollowPacks().sortedByDescending { it.createdAt },
            )

    /** Packs authored by accounts the user follows. Top-3 for sidebar. */
    val followedAuthorsPacks: StateFlow<List<FollowListEvent>> =
        combine(allPacks, followedAuthors) { packs, followed ->
            packs.filter { it.pubKey in followed }
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    /**
     * Sidebar top-3: own packs + packs by people user follows.
     * Falls back to most-recent-from-relays when user has no follow-derived packs.
     */
    val sidebarTopPacks: StateFlow<List<FollowListEvent>> =
        followedAuthorsPacks
            .map { followedPacks ->
                if (followedPacks.isNotEmpty()) {
                    followedPacks.take(3)
                } else {
                    // empty-state fallback: most recent from cache (any author)
                    allPacks.value.take(3)
                }
            }.stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )

    // ---- Featured pack with shuffle ----

    private val shuffleTickInternal = MutableStateFlow(0L)

    /** Increments on every shuffle() call. Used by gallery to re-randomize order. */
    val shuffleTick: StateFlow<Long> = shuffleTickInternal.asStateFlow()

    private val recentlyShown = ArrayDeque<String>(5)

    private val featuredPackInternal = MutableStateFlow<FollowListEvent?>(null)
    val featuredPack: StateFlow<FollowListEvent?> = featuredPackInternal.asStateFlow()

    init {
        // Recompute featured on cache or shuffle change.
        scope.launch(Dispatchers.Default) {
            combine(allPacks, shuffleTickInternal) { packs, _ -> packs }.collect { packs ->
                featuredPackInternal.value = pickRandom(packs)
            }
        }

        // Subscribe to discovery relays for global pack feed.
        subscribeToDiscovery()
    }

    fun shuffle() {
        shuffleTickInternal.value++
    }

    private fun pickRandom(packs: List<FollowListEvent>): FollowListEvent? {
        if (packs.isEmpty()) return null
        val candidates = packs.filter { it.address().toValue() !in recentlyShown }
        val pool = if (candidates.isEmpty()) packs else candidates
        val pick = pool.random()
        rememberShown(pick.address().toValue())
        return pick
    }

    private fun rememberShown(aTag: String) {
        if (recentlyShown.size >= 5) recentlyShown.removeFirst()
        recentlyShown.addLast(aTag)
    }

    private fun subscribeToDiscovery() {
        val listener =
            object : SubscriptionListener {
                override fun onEvent(
                    event: com.vitorpamplona.quartz.nip01Core.core.Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    cache.consume(event, relay)
                }
            }

        // For each discovery relay, ensure it's added then subscribe.
        scope.launch {
            // small grace period for relay manager to settle
            delay(500)
            DISCOVERY_RELAYS.forEach { relay ->
                relayManager.subscribeOnRelay(
                    relay = relay,
                    subId = "$SUB_ID-${relay.url.hashCode()}",
                    filters = listOf(Filter(kinds = listOf(FollowListEvent.KIND), limit = 200)),
                    onEvent = { event, r -> cache.consume(event, r) },
                )
                // Suppress unused variable warning
                @Suppress("UNUSED_VARIABLE")
                val unused = listener
            }
        }
    }

    /** Returns hashtags present on this pack, lowercase + deduped. */
    fun hashtagsFor(pack: FollowListEvent): List<String> = pack.hashtags().map { it.lowercase() }.distinct()
}
