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
package com.vitorpamplona.amethyst.service.relayClient.speedLogger

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.utils.LargeCache
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class KindGroup(
    var count: AtomicInteger = AtomicInteger(0),
    var memory: AtomicInteger = AtomicInteger(0),
    val subs: LargeCache<String, AtomicInteger> = LargeCache<String, AtomicInteger>(),
    val relays: LargeCache<NormalizedRelayUrl, AtomicInteger> = LargeCache<NormalizedRelayUrl, AtomicInteger>(),
) {
    companion object {
        const val MB: Int = 1024
    }

    fun increment(
        mem: Int,
        subId: String,
        relayUrl: NormalizedRelayUrl,
    ) {
        count.incrementAndGet()
        memory.addAndGet(mem)

        val subStats = subs.get(subId)
        if (subStats != null) {
            subStats.incrementAndGet()
        } else {
            subs.put(subId, AtomicInteger(1))
        }

        val relayStats = relays.get(relayUrl)
        if (relayStats != null) {
            relayStats.incrementAndGet()
        } else {
            relays.put(relayUrl, AtomicInteger(1))
        }
    }

    fun reset() {
        count.set(0)
        memory.set(0)
        subs.forEach { key, value -> value.set(0) }
        relays.forEach { key, value -> value.set(0) }
    }

    fun printSubs() = subs.joinToString(", ") { key, value -> if (value.get() > 0) "$key ($value)" else "" }

    fun printRelays() = relays.joinToString(", ") { key, value -> if (value.get() > 0) "${key.displayUrl()} ($value)" else "" }

    override fun toString() = "(${count.get()} - ${memory.get().div(MB)}kb); ${printSubs()}; ${printRelays()}"
}
