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
package com.vitorpamplona.amethyst.service.relayClient.dupLogger

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.cache.LargeCache
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Tracks every time a single (relay, eventId) pair has been downloaded and which
 * subscriptions delivered it. The first download is expected; every download after
 * that is wasted bandwidth — the same relay sent us the same event again.
 *
 * Two kinds of waste are distinguished by looking at the subscriptions involved:
 *  - [Category.REDELIVERY]: the SAME subscription id received the event more than
 *    once from the relay. Usually a re-REQ that didn't carry a tight enough `since`,
 *    or an EOSE/limit handling problem in that assembler.
 *  - [Category.OVERLAP]: TWO OR MORE subscription ids each pulled the event from the
 *    same relay. The filters of those assemblers overlap on this relay and could be
 *    merged or narrowed.
 *
 * Records are accumulated for the whole debug session, so this is debug-only.
 */
@OptIn(ExperimentalAtomicApi::class)
class DownloadRecord(
    val relayUrl: NormalizedRelayUrl,
    val eventId: HexKey,
    val kind: Int,
    val author: HexKey,
) {
    enum class Category {
        REDELIVERY,
        OVERLAP,
    }

    // total number of times this event arrived from this relay (1 = no waste yet)
    private val downloads = AtomicInteger(0)

    // total bytes of all the duplicate (2nd+) deliveries
    private val wastedBytes = AtomicInteger(0)

    // how many times each subscription id delivered this event from this relay
    private val perSubId = LargeCache<String, AtomicInteger>()

    /**
     * Registers one delivery of this event from [relayUrl] under [subId].
     *
     * @return null if this is the first (expected) download, or a [Duplicate]
     *  describing the waste when this is the 2nd+ download.
     */
    fun register(
        subId: String,
        memory: Int,
    ): Duplicate? {
        val count = downloads.incrementAndGet()

        val subStat = perSubId.get(subId)
        if (subStat != null) {
            subStat.incrementAndGet()
        } else {
            perSubId.put(subId, AtomicInteger(1))
        }

        if (count == 1) return null

        val wasted = wastedBytes.addAndGet(memory)

        val subIds = subscriptionIds()
        val category = if (subIds.size > 1) Category.OVERLAP else Category.REDELIVERY

        return Duplicate(
            relayUrl = relayUrl,
            eventId = eventId,
            kind = kind,
            author = author,
            downloads = count,
            wastedBytesTotal = wasted,
            lastBytes = memory,
            subscriptions = subIds,
            offendingSubId = subId,
            category = category,
        )
    }

    private fun subscriptionIds(): List<String> = perSubId.keys().sorted()

    /** Snapshot describing one wasted (duplicated) download. */
    data class Duplicate(
        val relayUrl: NormalizedRelayUrl,
        val eventId: HexKey,
        val kind: Int,
        val author: HexKey,
        val downloads: Int,
        val wastedBytesTotal: Int,
        val lastBytes: Int,
        val subscriptions: List<String>,
        val offendingSubId: String,
        val category: Category,
    ) {
        /**
         * A stable key for the offending filter interaction. For overlaps it is the
         * sorted set of subscription ids ("subA x subB"); for redeliveries it is the
         * single re-requesting subscription id ("subA (re)").
         */
        fun interactionKey(): String =
            when (category) {
                Category.OVERLAP -> subscriptions.joinToString(" x ")
                Category.REDELIVERY -> "$offendingSubId (re)"
            }
    }
}
