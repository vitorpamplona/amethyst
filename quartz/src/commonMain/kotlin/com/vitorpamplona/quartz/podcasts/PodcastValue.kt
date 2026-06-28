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
}

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
