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
package com.vitorpamplona.quartz.nip65RelayList

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayInfo
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayType

/**
 * One dimension of a NIP-65 kind:10002 entry's read/write marker.
 *
 * [AdvertisedRelayType] is the *combined* marker stored on the tag
 * (`READ`, `WRITE`, or `BOTH`); a facet is a single flag of it, which is
 * what UIs edit ("use this relay as an outbox" toggles [WRITE]).
 */
enum class AdvertisedRelayFacet {
    READ,
    WRITE,
}

/** Read/write flags for one relay, split out from [AdvertisedRelayType]. */
private data class RW(
    val read: Boolean,
    val write: Boolean,
)

private fun AdvertisedRelayType.rw() = RW(isRead(), isWrite())

private fun RW.toTypeOrNull(): AdvertisedRelayType? =
    when {
        read && write -> AdvertisedRelayType.BOTH
        read -> AdvertisedRelayType.READ
        write -> AdvertisedRelayType.WRITE
        else -> null
    }

/**
 * Toggle one [facet] on/off for [url] within a kind:10002 entry list,
 * applying the NIP-65 merge rules:
 *
 *  - turning a facet **on** merges into `BOTH` when the other facet is
 *    already set (adding a write marker to a read-only relay promotes it
 *    to `BOTH`); adding to an absent relay creates a single-facet entry;
 *  - turning a facet **off** on a `BOTH` relay demotes it to the other
 *    facet; turning the **last** facet off drops the relay entirely.
 *
 * Entry order is preserved; the receiver is not modified.
 */
fun List<AdvertisedRelayInfo>.applyFacet(
    url: NormalizedRelayUrl,
    facet: AdvertisedRelayFacet,
    present: Boolean,
): List<AdvertisedRelayInfo> {
    val urls = LinkedHashMap<String, NormalizedRelayUrl>()
    val flags = LinkedHashMap<String, RW>()
    for (i in this) {
        urls[i.relayUrl.url] = i.relayUrl
        flags[i.relayUrl.url] = i.type.rw()
    }
    val cur = flags[url.url] ?: RW(read = false, write = false)
    val next = if (facet == AdvertisedRelayFacet.WRITE) cur.copy(write = present) else cur.copy(read = present)
    if (next.read || next.write) {
        urls[url.url] = url
        flags[url.url] = next
    } else {
        urls.remove(url.url)
        flags.remove(url.url)
    }
    return flags.entries.map { AdvertisedRelayInfo(urls[it.key]!!, it.value.toTypeOrNull()!!) }
}

/** Adds [facet] to [url], promoting an existing entry to `BOTH` when it already carries the other facet. */
fun List<AdvertisedRelayInfo>.addFacet(
    url: NormalizedRelayUrl,
    facet: AdvertisedRelayFacet,
): List<AdvertisedRelayInfo> = applyFacet(url, facet, present = true)

/** Removes [facet] from [url], demoting a `BOTH` entry to the other facet or dropping the relay when it was the last one. */
fun List<AdvertisedRelayInfo>.removeFacet(
    url: NormalizedRelayUrl,
    facet: AdvertisedRelayFacet,
): List<AdvertisedRelayInfo> = applyFacet(url, facet, present = false)

/**
 * Makes exactly [targets] carry [facet], demoting (or removing, per the
 * [applyFacet] merge rules) any relay that currently carries it but
 * shouldn't. Relays only carrying the other facet are left untouched.
 */
fun List<AdvertisedRelayInfo>.setFacet(
    targets: List<NormalizedRelayUrl>,
    facet: AdvertisedRelayFacet,
): List<AdvertisedRelayInfo> {
    val keep = targets.map { it.url }.toSet()
    var result = this
    for (i in this) {
        val has = if (facet == AdvertisedRelayFacet.WRITE) i.type.isWrite() else i.type.isRead()
        if (has && i.relayUrl.url !in keep) result = result.applyFacet(i.relayUrl, facet, present = false)
    }
    for (u in targets) result = result.applyFacet(u, facet, present = true)
    return result
}
