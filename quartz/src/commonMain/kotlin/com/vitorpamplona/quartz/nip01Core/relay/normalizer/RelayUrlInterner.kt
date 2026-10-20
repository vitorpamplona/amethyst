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
package com.vitorpamplona.quartz.nip01Core.relay.normalizer

/**
 * Keeps one [NormalizedRelayUrl] instance per relay url, so every spelling of a relay, parsed any
 * number of times, resolves to the same object and the same url string.
 *
 * Backed by weak references: an entry lives exactly as long as something outside the interner
 * holds its relay, with no count limit, so a list of tens of thousands of relays dedupes as well
 * as a short one and is released with it. [RelayUrlNormalizer] interns every url it parses; its
 * own parse memo is a speed cache only, and evicting from it can no longer make a duplicate.
 *
 * Platforms without weak references fall back to a passthrough: no canonicalisation, no leaks.
 */
expect class RelayUrlInterner() {
    /**
     * The canonical instance for [relay]'s url, storing [relay] as canonical if no live one exists.
     * Internal: [RelayUrlNormalizer] answers straight from this map, so only urls it has validated
     * may enter it. A public intern would let any caller plant an unvalidated url.
     */
    internal fun intern(relay: NormalizedRelayUrl): NormalizedRelayUrl

    /** The live canonical instance whose url is exactly [url], else null. */
    fun get(url: String): NormalizedRelayUrl?

    /** Number of map entries, including dead weak references not swept yet. */
    fun size(): Int

    /** Drops every entry. For tests. */
    internal fun clear()

    companion object {
        /** Process-wide shared instance, the one [RelayUrlNormalizer] uses. */
        val Default: RelayUrlInterner
    }
}
