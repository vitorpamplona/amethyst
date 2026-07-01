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
package com.vitorpamplona.quartz.podcasts

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Podcasting-2.0 value-for-value (V4V) block — how a podcast splits incoming sats across its
 * participants. Carried as a JSON object: nested under `value` in the show's `kind:30078` metadata,
 * and serialized into the episode's `["value", "<json>"]` tag (where an episode override also sets
 * [enabled]).
 *
 * This is the parsed data model; performing the actual Lightning splits (keysend to `node`
 * recipients, LNURL-pay to `lnaddress` recipients, weighted by [PodcastValueRecipient.split]) is a
 * separate wallet/NWC concern and is not done here.
 */
@Immutable
@Serializable
class PodcastValue(
    /** Episode-level override switch; absent/null on show-level value blocks. */
    val enabled: Boolean? = null,
    /** Suggested amount (per the spec, typically per-minute streaming), in [currency] units. */
    val amount: Long? = null,
    /** Currency of [amount], e.g. "sat" or "USD". */
    val currency: String? = null,
    val recipients: List<PodcastValueRecipient> = emptyList(),
) {
    /** Sum of recipient splits, used to turn each [PodcastValueRecipient.split] into a share. */
    fun totalSplit(): Int = recipients.sumOf { it.split }

    /**
     * Splits [totalMilliSats] across the recipients per the Podcasting-2.0 value rules and returns
     * the non-zero shares (recipient + amount in millisats), preserving recipient order.
     *
     * - A recipient with [PodcastValueRecipient.fee] = true takes its [PodcastValueRecipient.split]
     *   as a **percentage of the total**, off the top (e.g. an app/host fee).
     * - The remainder is divided among the non-fee recipients **by relative weight**
     *   ([PodcastValueRecipient.split] / sum of non-fee splits).
     *
     * Recipients without a payable [PodcastValueRecipient.address] or with a non-positive split are
     * ignored. Integer division floors each share, so a few millisats may go unallocated (dust) —
     * acceptable for value-for-value streaming.
     */
    fun computeShares(totalMilliSats: Long): List<PodcastValueShare> {
        if (totalMilliSats <= 0) return emptyList()

        val active = recipients.filter { it.split > 0 && !it.address.isNullOrBlank() }
        if (active.isEmpty()) return emptyList()

        var feeTotalMillis = 0L
        val feeAmounts = HashMap<PodcastValueRecipient, Long>()
        for (recipient in active) {
            if (recipient.fee == true) {
                val millis = totalMilliSats * recipient.split / 100
                if (millis > 0) {
                    feeAmounts[recipient] = millis
                    feeTotalMillis += millis
                }
            }
        }

        val remainder = (totalMilliSats - feeTotalMillis).coerceAtLeast(0)
        val sharedWeight = active.filter { it.fee != true }.sumOf { it.split }

        val shares = ArrayList<PodcastValueShare>(active.size)
        for (recipient in active) {
            val millis =
                if (recipient.fee == true) {
                    feeAmounts[recipient] ?: 0L
                } else if (sharedWeight > 0 && remainder > 0) {
                    remainder * recipient.split / sharedWeight
                } else {
                    0L
                }
            if (millis > 0) shares.add(PodcastValueShare(recipient, millis))
        }
        return shares
    }

    companion object {
        /**
         * TLV record type for the Podcasting-2.0 keysend metadata blob (the "boostagram"), a JSON
         * object carrying podcast/episode/app/value context. Registered value, used by the whole
         * Podcasting-2.0 ecosystem. See <https://github.com/satoshisstream/satoshis.stream>.
         */
        const val PODCAST_TLV_RECORD: Long = 7629169L

        /** Recipient [PodcastValueRecipient.type] for a keysend to a raw Lightning node pubkey. */
        const val TYPE_NODE = "node"

        /** Recipient [PodcastValueRecipient.type] for an LNURL-pay to a lightning address. */
        const val TYPE_LNADDRESS = "lnaddress"
    }
}

/** One recipient's resolved share of a [PodcastValue] split, in millisats. */
@Immutable
class PodcastValueShare(
    val recipient: PodcastValueRecipient,
    val amountMilliSats: Long,
)

/** One destination in a [PodcastValue] split. */
@Immutable
@Serializable
class PodcastValueRecipient(
    val name: String? = null,
    /** "node" (keysend to a node pubkey) or "lnaddress" (LNURL-pay). */
    val type: String? = null,
    /** The node pubkey or lightning address, per [type]. */
    val address: String? = null,
    /** Relative weight of this recipient's share (not necessarily a percentage). */
    val split: Int = 0,
    val customKey: String? = null,
    val customValue: String? = null,
    /** When true, this recipient takes its share off the top as a fee before the rest is split. */
    val fee: Boolean? = null,
)
