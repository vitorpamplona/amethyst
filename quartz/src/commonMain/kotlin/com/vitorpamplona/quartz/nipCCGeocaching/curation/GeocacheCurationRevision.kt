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
package com.vitorpamplona.quartz.nipCCGeocaching.curation

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.update
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Adding a cache to a hunt that already exists.
 *
 * A curation list is replaceable, so "add to hunt" is a new revision at the same address rather
 * than a new list. Built on [update] for the same reason the listing's revisions are: a real
 * 37517 carries tags this library models nothing for, and rebuilding from parsed fields would
 * drop them.
 *
 * The cache lands at the end. NIP-CC makes the `a` order meaningful — a hunt is a route someone
 * designed — and appending is the only placement that needs no opinion about where in that route
 * the new stop belongs. Reordering is the composer's job.
 */
object GeocacheCurationRevision {
    fun withCache(
        hunt: GeocacheCurationListEvent,
        cache: Address,
        relayHint: NormalizedRelayUrl? = null,
        createdAt: Long = TimeUtils.now(),
    ) = hunt.update(createdAt) { geocache(cache, relayHint) }

    /** Whether [cache] is already on this hunt, so callers can avoid a no-op republish. */
    fun contains(
        hunt: GeocacheCurationListEvent,
        cache: Address,
    ) = hunt.geocaches().any { it == cache }
}
