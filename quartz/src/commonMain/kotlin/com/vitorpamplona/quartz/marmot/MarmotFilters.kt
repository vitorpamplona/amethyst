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

import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageUtils
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent

/**
 * Relay subscription filter builders for Marmot protocol events.
 *
 * Provides pre-configured [Filter] instances for subscribing to the various
 * Marmot event types on Nostr relays.
 */
object MarmotFilters {
    /**
     * Filter for KeyPackages by author (kind:30443).
     * Used to discover a user's available KeyPackages for group invitations.
     *
     * {kinds: [30443], authors: [pubkey]}
     */
    fun keyPackagesByAuthor(pubkey: HexKey): Filter =
        Filter(
            kinds = listOf(KeyPackageEvent.KIND),
            authors = listOf(pubkey),
        )

    /**
     * Filter for KeyPackages by multiple authors.
     * Used when inviting multiple users to a group at once.
     *
     * {kinds: [30443], authors: [pubkey1, pubkey2, ...]}
     */
    fun keyPackagesByAuthors(pubkeys: List<HexKey>): Filter =
        Filter(
            kinds = listOf(KeyPackageEvent.KIND),
            authors = pubkeys,
        )

    /**
     * Filter for a specific KeyPackage by its ref (kind:30443, #i tag).
     * Used to look up a specific KeyPackage by its KeyPackageRef hash.
     *
     * {kinds: [30443], #i: [keyPackageRef]}
     */
    fun keyPackageByRef(keyPackageRef: HexKey): Filter =
        Filter(
            kinds = listOf(KeyPackageEvent.KIND),
            tags = mapOf("i" to listOf(keyPackageRef)),
        )

    /**
     * Filter for GroupEvents by group ID (kind:445, #h tag).
     * Used to subscribe to all messages and commits for a specific group.
     *
     * {kinds: [445], #h: [nostrGroupId]}
     */
    fun groupEventsByGroupId(nostrGroupId: HexKey): Filter =
        Filter(
            kinds = listOf(GroupEvent.KIND),
            tags = mapOf("h" to listOf(nostrGroupId)),
        )

    /**
     * Filter for GroupEvents by group ID with a time range.
     * Used to catch up on missed messages since a given timestamp.
     *
     * {kinds: [445], #h: [nostrGroupId], since: timestamp}
     */
    fun groupEventsByGroupIdSince(
        nostrGroupId: HexKey,
        since: Long,
    ): Filter =
        Filter(
            kinds = listOf(GroupEvent.KIND),
            tags = mapOf("h" to listOf(nostrGroupId)),
            since = since,
        )

    /**
     * Filter for NIP-59 gift wraps addressed to a user (kind:1059).
     * Welcome messages (kind:444) are delivered inside gift wraps.
     * This reuses the standard NIP-59 subscription pattern.
     *
     * {kinds: [1059], #p: [recipientPubKey]}
     */
    fun giftWrapsForUser(recipientPubKey: HexKey): Filter =
        Filter(
            kinds = listOf(GiftWrapEvent.KIND),
            tags = mapOf("p" to listOf(recipientPubKey)),
        )

    /**
     * Filter for NIP-59 gift wraps since a given timestamp.
     * Used to catch up on missed Welcome messages.
     *
     * {kinds: [1059], #p: [recipientPubKey], since: timestamp}
     */
    fun giftWrapsForUserSince(
        recipientPubKey: HexKey,
        since: Long,
    ): Filter =
        Filter(
            kinds = listOf(GiftWrapEvent.KIND),
            tags = mapOf("p" to listOf(recipientPubKey)),
            since = since,
        )

    /**
     * Filter for KeyPackages during migration (both kind:443 and kind:30443).
     * Used during the transition period from legacy to addressable KeyPackages.
     *
     * {kinds: [30443, 443], authors: [pubkey]}
     */
    fun keyPackagesMigration(pubkey: HexKey): Filter =
        Filter(
            kinds = KeyPackageUtils.migrationKinds(),
            authors = listOf(pubkey),
        )
}
