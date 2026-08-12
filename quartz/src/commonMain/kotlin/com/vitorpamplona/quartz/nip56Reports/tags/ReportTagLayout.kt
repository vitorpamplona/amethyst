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
package com.vitorpamplona.quartz.nip56Reports.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip56Reports.ReportType

/**
 * Positional layout shared by the NIP-56 pointer tags (`p`, `e`, `a`).
 *
 * NIP-56 predates the convention that slot 2 of a pointer tag is a relay
 * hint: it put the report type there instead. Both layouts are in the wild,
 * so every reader has to disambiguate:
 *
 * ```
 * ["p", "<pubkey>", "nudity"]                    // legacy: type at 2
 * ["p", "<pubkey>", "wss://relay/", "nudity"]    // modern: hint at 2, type at 3
 * ["p", "<pubkey>", "wss://relay/"]              // modern, no per-tag type
 * ["p", "<pubkey>", "", "nudity"]                // empty hint slot
 * ```
 *
 * Disambiguation is by *shape*, not by tag length: a slot that parses as a
 * relay URL is a hint, never a type. No report-type code is a relay URL, so
 * the two spaces cannot collide.
 */
internal object ReportTagLayout {
    /** The relay hint at slot 2, or null under the legacy layout. */
    fun relayHint(tag: Array<String>): NormalizedRelayUrl? {
        if (tag.has(2) && tag[2].length > 7 && RelayUrlNormalizer.isRelayUrl(tag[2])) {
            return RelayUrlNormalizer.normalizeOrNull(tag[2])
        }
        return null
    }

    /**
     * The report type this tag carries, falling back to [default] (the
     * event-level type) when the tag does not name one of its own.
     *
     * Only a slot that is neither blank nor a relay URL is offered to
     * [ReportType.parseOrNull] — which despite its name never returns null
     * and maps anything unrecognized to [ReportType.OTHER]. Feeding it a
     * relay URL would turn every hint-carrying tag into an `OTHER` report
     * and mask the event-level default.
     */
    fun reportType(
        tag: Array<String>,
        default: ReportType?,
    ): ReportType? {
        val slot = if (tag.has(2) && (tag[2].isBlank() || relayHint(tag) != null)) 3 else 2

        if (!tag.has(slot) || tag[slot].isBlank()) return default

        return ReportType.parseOrNull(tag[slot], tag) ?: default
    }
}
