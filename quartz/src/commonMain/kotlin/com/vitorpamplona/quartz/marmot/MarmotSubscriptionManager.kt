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
package com.vitorpamplona.quartz.marmot

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Subscription state for a single Marmot group.
 *
 * Tracks the `since` timestamp for pagination so that reconnections
 * only fetch events newer than the last seen event.
 */
data class GroupSubscriptionState(
    val nostrGroupId: HexKey,
    var since: Long? = null,
    var active: Boolean = true,
)

/**
 * Coordinates relay subscriptions for Marmot protocol events.
 *
 * Manages three categories of subscriptions:
 * 1. **GroupEvent (kind:445)** — per-group, filtered by `h` tag
 * 2. **GiftWrap (kind:1059)** — per-user, for receiving Welcome messages
 * 3. **KeyPackage (kind:30443)** — on-demand, for fetching member KeyPackages
 *
 * The platform layer (amethyst/desktopApp) wires these filters into the
 * relay client via [buildFilters] or by polling [activeGroupFilters].
 *
 * This class is protocol-only and does NOT depend on Android/UI. It
 * produces [Filter] instances that the platform relay client consumes.
 */
class MarmotSubscriptionManager(
    private val userPubKey: HexKey,
) {
    private val mutex = Mutex()
    private val groupSubscriptions = mutableMapOf<HexKey, GroupSubscriptionState>()
    private var giftWrapSince: Long? = null

    /**
     * Subscribe to GroupEvents for a group.
     * Call this when joining a group or restoring from storage.
     *
     * @param nostrGroupId hex-encoded Nostr group ID
     * @param since optional timestamp to resume from (e.g., last seen event)
     */
    suspend fun subscribeGroup(
        nostrGroupId: HexKey,
        since: Long? = null,
    ) = mutex.withLock {
        groupSubscriptions[nostrGroupId] =
            GroupSubscriptionState(
                nostrGroupId = nostrGroupId,
                since = since,
                active = true,
            )
    }

    /**
     * Unsubscribe from a group's events.
     * Call this when leaving a group.
     */
    suspend fun unsubscribeGroup(nostrGroupId: HexKey) =
        mutex.withLock {
            groupSubscriptions.remove(nostrGroupId)
        }

    /**
     * Update the `since` timestamp for a group after processing events.
     * This ensures reconnections only fetch newer events.
     */
    suspend fun updateGroupSince(
        nostrGroupId: HexKey,
        since: Long,
    ) = mutex.withLock {
        groupSubscriptions[nostrGroupId]?.since = since
    }

    /**
     * Update the `since` timestamp for gift wrap subscriptions.
     */
    suspend fun updateGiftWrapSince(since: Long) =
        mutex.withLock {
            giftWrapSince = since
        }

    /**
     * Returns all active group IDs being tracked.
     */
    fun activeGroupIds(): Set<HexKey> = groupSubscriptions.filter { it.value.active }.keys

    /**
     * Check if a group is currently subscribed.
     */
    fun isSubscribed(nostrGroupId: HexKey): Boolean = groupSubscriptions[nostrGroupId]?.active == true

    /**
     * Build filters for all active group subscriptions.
     *
     * Returns one [Filter] per active group (kind:445 filtered by `h` tag),
     * using the tracked `since` timestamp for pagination.
     */
    fun activeGroupFilters(): List<Filter> =
        groupSubscriptions.values
            .filter { it.active }
            .map { state ->
                if (state.since != null) {
                    MarmotFilters.groupEventsByGroupIdSince(state.nostrGroupId, state.since!!)
                } else {
                    MarmotFilters.groupEventsByGroupId(state.nostrGroupId)
                }
            }

    /**
     * Build the gift wrap filter for receiving Welcome messages.
     *
     * Returns a single filter for kind:1059 addressed to the user's pubkey,
     * using the tracked `since` timestamp for pagination.
     */
    fun giftWrapFilter(): Filter =
        if (giftWrapSince != null) {
            MarmotFilters.giftWrapsForUserSince(userPubKey, giftWrapSince!!)
        } else {
            MarmotFilters.giftWrapsForUser(userPubKey)
        }

    /**
     * Build a filter for the current user's own KeyPackages (kind:30443).
     * Used to discover previously published KeyPackages on relay connect/reconnect,
     * so the app can track whether a KeyPackage has already been published.
     */
    fun ownKeyPackageFilter(): Filter = MarmotFilters.keyPackagesByAuthor(userPubKey)

    /**
     * Build a KeyPackage filter for a specific user.
     * Used on-demand when inviting a user to a group.
     */
    fun keyPackageFilter(pubkey: HexKey): Filter = MarmotFilters.keyPackagesByAuthor(pubkey)

    /**
     * Build KeyPackage filters for multiple users.
     * Used when inviting multiple users at once.
     */
    fun keyPackageFilterForMultiple(pubkeys: List<HexKey>): Filter = MarmotFilters.keyPackagesByAuthors(pubkeys)

    /**
     * Build all filters needed for the current subscription state.
     *
     * Returns the combined list of:
     * - One filter per active group (kind:445)
     * - One gift wrap filter (kind:1059)
     *
     * The platform layer should send these filters to the relay client
     * whenever subscriptions change or on reconnection.
     */
    fun buildFilters(): List<Filter> {
        val filters = mutableListOf<Filter>()
        filters.addAll(activeGroupFilters())
        filters.add(giftWrapFilter())
        filters.add(ownKeyPackageFilter())
        return filters
    }

    /**
     * Synchronize subscriptions with the group manager's active groups.
     *
     * Adds subscriptions for new groups and removes subscriptions
     * for groups we're no longer members of.
     *
     * @param activeGroupIds the set of group IDs from [MlsGroupManager.activeGroupIds]
     */
    suspend fun syncWithGroupManager(activeGroupIds: Set<HexKey>) =
        mutex.withLock {
            // Add new groups
            for (groupId in activeGroupIds) {
                if (!groupSubscriptions.containsKey(groupId)) {
                    groupSubscriptions[groupId] =
                        GroupSubscriptionState(
                            nostrGroupId = groupId,
                            active = true,
                        )
                }
            }

            // Remove stale groups
            val staleGroups = groupSubscriptions.keys - activeGroupIds
            for (groupId in staleGroups) {
                groupSubscriptions.remove(groupId)
            }
        }

    /**
     * Clear all subscription state.
     */
    suspend fun clear() =
        mutex.withLock {
            groupSubscriptions.clear()
            giftWrapSince = null
        }
}
