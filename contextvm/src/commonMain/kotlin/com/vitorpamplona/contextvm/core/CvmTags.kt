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
package com.vitorpamplona.contextvm.core

import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag

/**
 * ContextVM tag vocabulary.
 *
 * Addressing (`p`) and correlation (`e`) reuse the NIP-01 tags quartz already
 * models — [PTag] and [ETag] — because ContextVM assigns them their ordinary
 * NIP-01 meanings. Only the ContextVM-specific names are defined here.
 */
object CvmTags {
    /** Recipient public key. Same meaning as NIP-01; parse with [PTag]. */
    const val PUBKEY = PTag.TAG_NAME

    /** Correlates a response to its request event. Same meaning as NIP-01; parse with [ETag]. */
    const val EVENT_ID = ETag.TAG_NAME

    /** Relay URL hint (CEP-17 reuses the NIP-65 `r` tag). */
    const val RELAY = "r"

    // --- CEP-6 server identity metadata ---

    const val NAME = "name"
    const val ABOUT = "about"
    const val PICTURE = "picture"
    const val WEBSITE = "website"

    // --- Transport capability advertisement ---

    /** CEP-4: presence alone indicates the peer supports encrypted messages. */
    const val SUPPORT_ENCRYPTION = "support_encryption"

    /** CEP-19: presence indicates the peer accepts kind 21059 gift wraps. */
    const val SUPPORT_ENCRYPTION_EPHEMERAL = "support_encryption_ephemeral"

    /** CEP-22: presence indicates support for bounded oversized payload transfer. */
    const val SUPPORT_OVERSIZED_TRANSFER = "support_oversized_transfer"

    /** CEP-41: presence indicates support for open-ended streams. */
    const val SUPPORT_OPEN_STREAM = "support_open_stream"

    // --- CEP-8 pricing and payment ---

    /** `["cap", "<tool:name|prompt:name|resource:uri>", "<price|min-max>", "<unit>"]`. */
    const val CAPABILITY_PRICE = "cap"

    /** `["pmi", "<payment-method-identifier>"]`. */
    const val PAYMENT_METHOD = "pmi"

    /** `["payment_interaction", "transparent"|"explicit_gating"]`. */
    const val PAYMENT_INTERACTION = "payment_interaction"

    /** `["direct_payment", "<pmi>", "<payload>"]` — bearer-asset optimization. */
    const val DIRECT_PAYMENT = "direct_payment"

    /** `["change", "<pmi>", "<payload>"]` — overpayment remainder. */
    const val CHANGE = "change"

    // --- CEP-15 common tool schemas (NIP-73 external identity tags) ---

    /** `["i", "<schema-hash>", "<tool-name>"]`. */
    const val EXTERNAL_ID = "i"

    /** `["k", "io.contextvm/common-schema"]`. */
    const val EXTERNAL_KIND = "k"

    /** The NIP-73 kind value CEP-15 uses in its [EXTERNAL_KIND] tag. */
    const val COMMON_SCHEMA_NAMESPACE = "io.contextvm/common-schema"

    /**
     * Tags that carry routing rather than discovery information.
     *
     * CEP-35 requires unknown discovery tags to be preserved, but says routing
     * tags SHOULD be excluded from the learned discovery surface. Keeping the
     * exclusion set explicit (rather than an allow-list of known discovery tags)
     * is what makes forward compatibility work: a tag we have never heard of is
     * preserved by default instead of dropped.
     */
    val ROUTING = setOf(PUBKEY, EVENT_ID)

    fun isRouting(tagName: String) = tagName in ROUTING

    /** A valueless capability tag, e.g. `["support_encryption"]`. */
    fun flag(name: String): Tag = arrayOf(name)

    /**
     * True when [tags] contains the capability flag [name].
     *
     * A flag is signalled by presence, so a tag carrying extra elements still
     * counts — the spec defines meaning by the tag name, not by arity.
     */
    fun hasFlag(
        tags: Array<Tag>,
        name: String,
    ) = tags.any { it.isNotEmpty() && it[0] == name }
}
