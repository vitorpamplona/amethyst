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
package com.vitorpamplona.quartz.marmot.mip05PushNotifications

import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * The record key of a push token: `(member_id_hex, leaf_index, platform,
 * server_pubkey_hex)` (`features/push-notifications.md`, "Record key and
 * ordering primitive").
 *
 * At most one active record exists per key per group. `leaf_index` is in the
 * key deliberately: one Marmot account can hold several MLS leaves, and
 * collapsing them would let one device's list entry or removal overwrite a
 * sibling device's live token. `token_fingerprint` is NOT in the key — it is
 * replaceable data on the record, and gating a delete on it would weaken
 * tombstones.
 */
data class PushRecordKey(
    val memberIdHex: HexKey,
    val leafIndex: Int,
    val platform: PushPlatform,
    val serverPubKeyHex: HexKey,
)

/**
 * The ordering primitive for a record key: `(owner_ts, record digest)`.
 *
 * The `owner_ts` half is an owner-supplied latest-wins clock; the digest half
 * makes two distinct records stamped in the same millisecond converge on the
 * same winner everywhere. Both halves come out of fields the owner proof binds,
 * so the primitive inherits `owner_sig`'s trust rather than the carrying
 * event's sender.
 *
 * A client MUST NOT substitute the carrying event's `created_at`, arrival
 * order, outer event ids, relay metadata or local receive time for this. Using
 * `owner_ts` is exactly what makes a relayed kind `448` safe: the relaying
 * member cannot advance or rewind a record it cannot re-sign.
 */
data class PushRecordStamp(
    val ownerTsMillis: Long,
    val digestHex: HexKey,
) : Comparable<PushRecordStamp> {
    override fun compareTo(other: PushRecordStamp): Int {
        val byTime = ownerTsMillis.compareTo(other.ownerTsMillis)
        if (byTime != 0) return byTime
        return digestHex.compareTo(other.digestHex)
    }
}
