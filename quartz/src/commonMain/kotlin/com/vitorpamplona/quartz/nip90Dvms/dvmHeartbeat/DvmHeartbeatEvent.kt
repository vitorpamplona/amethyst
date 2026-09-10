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
package com.vitorpamplona.quartz.nip90Dvms.dvmHeartbeat

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * DVM heartbeat (kind 11998, experimental — no NIP yet): a beat a DVM publishes every 300s to
 * prove it is alive. The operator contract is plain-text content with `d` (the DVM's NIP-89
 * DTAG), `status` (free text) and `expiration` (createdAt + 300, NIP-40) tags.
 *
 * The kind sits in the replaceable range (10000–19999), so relays keep only the latest beat
 * per author. The `d` tag participates in the client-side address so each announced DVM has
 * its own cache slot: `Address(11998, dvmPubKey, dTag)` mirrors the announcement's
 * `Address(31990, dvmPubKey, dTag)`.
 */
@Stable
@Immutable
class DvmHeartbeatEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun status(): String? = tags.firstOrNull { it.size > 1 && it[0] == STATUS_TAG }?.get(1)

    fun expiration(): Long? = tags.expiration()

    /** True while this beat still proves liveness at [now]. */
    fun isFreshAt(now: Long = TimeUtils.now()): Boolean = createdAt >= now - MAX_AGE_SECONDS

    companion object {
        const val KIND = 11998
        const val STATUS_TAG = "status"
        const val CONTENT = "Alive and kicking"

        /**
         * A beat older than this no longer proves liveness. Beats arrive every 300s, so this
         * window deliberately tolerates several missed deliveries (relay reconnects, REQ churn)
         * before a DVM is dropped — hysteresis against transient delivery gaps, at the cost of a
         * dead DVM lingering this long before disappearing.
         */
        const val MAX_AGE_SECONDS = 900

        fun build(
            dTag: String,
            status: String,
            expiration: Long,
            createdAt: Long = TimeUtils.now(),
        ): EventTemplate<DvmHeartbeatEvent> =
            eventTemplate(KIND, CONTENT, createdAt) {
                add(arrayOf("d", dTag))
                add(arrayOf(STATUS_TAG, status))
                add(arrayOf("expiration", expiration.toString()))
            }
    }
}
