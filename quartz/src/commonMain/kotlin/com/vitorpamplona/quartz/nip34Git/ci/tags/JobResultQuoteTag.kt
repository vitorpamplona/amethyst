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
package com.vitorpamplona.quartz.nip34Git.ci.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.ensure

/**
 * A Workflow Result's (or Progress') quote of one Job Result. [publisher] is the compute
 * provider that signed it, which may differ from the coordinator vouching for it.
 */
@Immutable
data class CiJobResultQuote(
    val eventId: HexKey,
    val relay: NormalizedRelayUrl?,
    val publisher: HexKey?,
    val jobId: String,
)

/**
 * Nostr CI Job Result `q` tag: `["q", "<job-result-id>", "<relay-url>", "<publisher-pubkey>", "<job-id>"]`
 * — a NIP-18 quote whose marker is the job id as declared in the workflow file.
 *
 * The provenance markers (`manual-trigger`, `service-request`) are not job ids and are left to
 * [ProvenanceQuoteTag]. A quote without a marker is not one this NIP defines and does not parse
 * here (ngit ignores it rather than reinterpreting it as a job).
 */
class JobResultQuoteTag {
    companion object {
        const val TAG_NAME = "q"

        fun isTag(tag: Array<String>) = parse(tag) != null

        fun parse(tag: Array<String>): CiJobResultQuote? {
            ensure(tag.has(4)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64 && Hex.isHex(tag[1])) { return null }
            ensure(tag[4].isNotEmpty()) { return null }
            ensure(CiProvenanceKind.parse(tag[4]) == null) { return null }
            val publisher = tag[3].takeIf { it.length == 64 && Hex.isHex(it) }
            return CiJobResultQuote(tag[1], RelayUrlNormalizer.normalizeHintOrNull(tag[2]), publisher, tag[4])
        }

        /** [parse]'s publisher, with the same checks but no relay normalisation or allocation: for graph edges. */
        fun parsePublisher(tag: Array<String>): HexKey? {
            ensure(tag.has(4)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64 && Hex.isHex(tag[1])) { return null }
            ensure(tag[4].isNotEmpty()) { return null }
            ensure(CiProvenanceKind.parse(tag[4]) == null) { return null }
            return tag[3].takeIf { it.length == 64 && Hex.isHex(it) }
        }

        fun parseAsHint(tag: Array<String>): EventIdHint? {
            val quote = parse(tag) ?: return null
            val relay = quote.relay ?: return null
            return EventIdHint(quote.eventId, relay)
        }

        fun assemble(
            eventId: HexKey,
            relay: NormalizedRelayUrl?,
            publisher: HexKey?,
            jobId: String,
        ) = arrayOf(TAG_NAME, eventId, relay?.url ?: "", publisher ?: "", jobId)

        fun assemble(quote: CiJobResultQuote) = assemble(quote.eventId, quote.relay, quote.publisher, quote.jobId)
    }
}
