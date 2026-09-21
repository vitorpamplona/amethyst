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
package com.vitorpamplona.quartz.nipCCGeocaching.foundLog.tags

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent

/**
 * The `a` tag of a found log (kind 7516): the geocache listing the log is about.
 *
 * An ordinary NIP-01 addressable reference — unlike the composite `a` on a kind 7517 — so the
 * parsing delegates to [ATag]. The named wrapper exists so the package reads in its own
 * vocabulary and so the assemble overload can take a [GeocacheListingEvent] directly.
 */
class GeocacheTag {
    companion object {
        const val TAG_NAME = ATag.TAG_NAME

        fun isTag(tag: Array<String>) = ATag.isTagged(tag)

        fun parse(tag: Array<String>) = ATag.parse(tag)

        fun parseAddress(tag: Array<String>) = ATag.parseAddress(tag)

        fun parseAddressId(tag: Array<String>) = ATag.parseAddressId(tag)

        fun parseAsHint(tag: Array<String>) = ATag.parseAsHint(tag)

        fun assemble(
            cache: Address,
            relayHint: NormalizedRelayUrl?,
        ) = ATag.assemble(cache, relayHint)

        fun assemble(cache: EventHintBundle<GeocacheListingEvent>) = assemble(cache.event.address(), cache.relay)
    }
}
