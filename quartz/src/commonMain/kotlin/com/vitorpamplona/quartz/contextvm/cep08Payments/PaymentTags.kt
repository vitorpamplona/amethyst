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
package com.vitorpamplona.quartz.contextvm.cep08Payments

import com.vitorpamplona.quartz.contextvm.core.CvmTags
import com.vitorpamplona.quartz.nip01Core.core.Tag

/**
 * How payment is surfaced for a session (CEP-8).
 *
 * The distinction is not cosmetic: under [TRANSPARENT] payment is handled by
 * transport middleware and the application may never see it, while under
 * [EXPLICIT_GATING] payment becomes the invocation's own error result so an
 * agent or user can decide. A client that needs the decision visible must not
 * silently accept the other mode — see [PaymentSession].
 */
enum class PaymentInteraction(
    val wire: String,
) {
    TRANSPARENT("transparent"),
    EXPLICIT_GATING("explicit_gating"),
    ;

    companion object {
        /** The compatibility baseline when no tag is present. */
        val DEFAULT = TRANSPARENT

        fun fromWire(value: String?) = entries.firstOrNull { it.wire == value }
    }
}

/**
 * A Payment Method Identifier.
 *
 * Follows the W3C format (`[a-z0-9-]+`). A PMI is not just a discovery label: it
 * is the type tag for the opaque `pay_req` string, so a handler that does not
 * recognise the PMI cannot interpret the payment request at all.
 */
data class Pmi(
    val value: String,
) {
    init {
        require(PATTERN.matches(value)) { "PMI must match [a-z0-9-]+ but was '$value'" }
    }

    /**
     * True for bearer-asset methods that allow settlement directly on the
     * request via a `direct_payment` tag (CEP-21's `-direct` suffix convention).
     */
    val supportsDirectPayment get() = value.endsWith(DIRECT_SUFFIX)

    override fun toString() = value

    companion object {
        private val PATTERN = Regex("[a-z0-9-]+")

        const val DIRECT_SUFFIX = "-direct"

        /** The one PMI CEP-21 currently recommends: `pay_req` is a BOLT11 invoice. */
        val LIGHTNING_BOLT11 = Pmi("bitcoin-lightning-bolt11")

        fun parseOrNull(value: String) = if (PATTERN.matches(value)) Pmi(value) else null
    }
}

/** A capability's advertised reference price. */
sealed interface Price {
    data class Fixed(
        val amount: Long,
    ) : Price

    /**
     * An inclusive range. The server may request any amount within it, so this
     * is a discovery hint rather than a commitment.
     */
    data class Range(
        val min: Long,
        val max: Long,
    ) : Price

    fun includes(amount: Long) =
        when (this) {
            is Fixed -> amount == this.amount
            is Range -> amount in min..max
        }
}

/** What a [CapTag] prices. */
enum class CapabilityKind(
    val prefix: String,
) {
    TOOL("tool:"),
    PROMPT("prompt:"),
    RESOURCE("resource:"),
    ;

    companion object {
        fun of(identifier: String) = entries.firstOrNull { identifier.startsWith(it.prefix) }
    }
}

/** `["cap", "<tool:name>", "<price>", "<unit>"]` — a reference price for discovery and UX. */
data class CapTag(
    val kind: CapabilityKind,
    val name: String,
    val price: Price,
    val unit: String,
) {
    fun toTag(): Tag = arrayOf(CvmTags.CAPABILITY_PRICE, kind.prefix + name, priceWire(), unit)

    private fun priceWire() =
        when (price) {
            is Price.Fixed -> price.amount.toString()
            is Price.Range -> "${price.min}-${price.max}"
        }

    companion object {
        fun parse(tag: Tag): CapTag? {
            if (tag.size < 4 || tag[0] != CvmTags.CAPABILITY_PRICE) return null
            val kind = CapabilityKind.of(tag[1]) ?: return null
            val price = parsePrice(tag[2]) ?: return null
            return CapTag(kind, tag[1].removePrefix(kind.prefix), price, tag[3])
        }

        private fun parsePrice(raw: String): Price? {
            val separator = raw.indexOf('-')
            if (separator <= 0) return raw.toLongOrNull()?.let { Price.Fixed(it) }

            val min = raw.substring(0, separator).toLongOrNull() ?: return null
            val max = raw.substring(separator + 1).toLongOrNull() ?: return null
            return if (min <= max) Price.Range(min, max) else null
        }
    }
}

/** Assembling and reading the CEP-8 tag family. */
object PaymentTags {
    fun pmi(pmi: Pmi): Tag = arrayOf(CvmTags.PAYMENT_METHOD, pmi.value)

    fun paymentInteraction(mode: PaymentInteraction): Tag = arrayOf(CvmTags.PAYMENT_INTERACTION, mode.wire)

    fun directPayment(
        pmi: Pmi,
        payload: String,
    ): Tag = arrayOf(CvmTags.DIRECT_PAYMENT, pmi.value, payload)

    fun change(
        pmi: Pmi,
        payload: String,
    ): Tag = arrayOf(CvmTags.CHANGE, pmi.value, payload)

    fun parsePmis(tags: Array<Tag>): List<Pmi> =
        tags
            .filter { it.size >= 2 && it[0] == CvmTags.PAYMENT_METHOD }
            .mapNotNull { Pmi.parseOrNull(it[1]) }

    fun parseCaps(tags: Array<Tag>): List<CapTag> = tags.mapNotNull(CapTag::parse)

    /**
     * The interaction mode a peer is asking for, or null when the tag is absent.
     *
     * Absence is meaningful: on a session's first direct message it means
     * [PaymentInteraction.TRANSPARENT], while on a later message it means the
     * current effective mode is inherited unchanged.
     */
    fun parsePaymentInteraction(tags: Array<Tag>): PaymentInteraction? =
        tags
            .firstOrNull { it.size >= 2 && it[0] == CvmTags.PAYMENT_INTERACTION }
            ?.let { PaymentInteraction.fromWire(it[1]) }

    /**
     * Direct-payment offers in request order.
     *
     * A client SHOULD send at most one, but a server evaluating several takes
     * the first whose PMI it supports, so order is preserved here.
     */
    fun parseDirectPayments(tags: Array<Tag>): List<Pair<Pmi, String>> =
        tags
            .filter { it.size >= 3 && it[0] == CvmTags.DIRECT_PAYMENT }
            .mapNotNull { tag -> Pmi.parseOrNull(tag[1])?.let { it to tag[2] } }

    fun parseChange(tags: Array<Tag>): Pair<Pmi, String>? =
        tags
            .firstOrNull { it.size >= 3 && it[0] == CvmTags.CHANGE }
            ?.let { tag -> Pmi.parseOrNull(tag[1])?.let { it to tag[2] } }
}
