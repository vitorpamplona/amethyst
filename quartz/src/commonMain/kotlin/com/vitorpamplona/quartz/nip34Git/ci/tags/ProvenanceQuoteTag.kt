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

/** Which request a run's frozen provenance quote names. */
enum class CiProvenanceKind(
    val marker: String,
) {
    /** A one-shot kind 9840 Manual Trigger the run replayed. */
    MANUAL_TRIGGER("manual-trigger"),

    /** The standing kind 9843 Service Request selected at final runner handoff. */
    SERVICE_REQUEST("service-request"),
    ;

    companion object {
        fun parse(marker: String): CiProvenanceKind? =
            when (marker) {
                MANUAL_TRIGGER.marker -> MANUAL_TRIGGER
                SERVICE_REQUEST.marker -> SERVICE_REQUEST
                else -> null
            }
    }
}

/**
 * A run's request-provenance quote. It only *types* the reference: a coordinator-authored `q` is
 * not evidence that a maintainer asked for the run. A trust layer must fetch the quoted event and
 * check its kind, signature, author and repository before relying on it.
 */
@Immutable
data class CiProvenanceQuote(
    val kind: CiProvenanceKind,
    val eventId: HexKey,
    val relay: NormalizedRelayUrl?,
    val requester: HexKey,
)

/**
 * Nostr CI request-provenance `q` tag (NIP-18 quote with a marker):
 * `["q", "<request-id>", "<relay-url>", "<requester-pubkey>", "manual-trigger" | "service-request"]`.
 *
 * The requester pubkey is required — both quoted kinds are regular events, so it is how the
 * request is found again — and a quote without it is rejected, as ngit does.
 */
class ProvenanceQuoteTag {
    companion object {
        const val TAG_NAME = "q"

        fun isTag(tag: Array<String>) = parse(tag) != null

        fun parse(tag: Array<String>): CiProvenanceQuote? {
            ensure(tag.has(4)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64 && Hex.isHex(tag[1])) { return null }
            ensure(tag[3].length == 64 && Hex.isHex(tag[3])) { return null }
            val kind = CiProvenanceKind.parse(tag[4]) ?: return null
            return CiProvenanceQuote(kind, tag[1], RelayUrlNormalizer.normalizeOrNull(tag[2]), tag[3])
        }

        fun parseAsHint(tag: Array<String>): EventIdHint? {
            val quote = parse(tag) ?: return null
            val relay = quote.relay ?: return null
            return EventIdHint(quote.eventId, relay)
        }

        fun assemble(
            kind: CiProvenanceKind,
            eventId: HexKey,
            relay: NormalizedRelayUrl?,
            requester: HexKey,
        ) = arrayOf(TAG_NAME, eventId, relay?.url ?: "", requester, kind.marker)

        fun assemble(quote: CiProvenanceQuote) = assemble(quote.kind, quote.eventId, quote.relay, quote.requester)
    }
}
