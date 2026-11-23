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
package com.vitorpamplona.amethyst.commons.model.concord

import com.vitorpamplona.quartz.concord.cord03Channels.ChannelDelivery
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelAccess
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelAccess.AccessOpening
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelAccess.AccessVerdict
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner

/**
 * The access drain (CORD-03 §5): what this account does with one buffered key delivery. Quartz's
 * [ConcordChannelAccess.accessVerdict] decides it before any signer call (settle it for good, keep
 * it for a later fold, or open our blob) and [ConcordChannelAccess.openAccessFor] opens it; this
 * adds what only the app knows, the Community List write that adopts the key.
 */
object ConcordAccessDrain {
    /**
     * Runs one buffered `access` delivery through the drain and returns true when it is settled for
     * good (the caller retires its wrap), false to keep it for a later drain. [adopt] writes the
     * opened key to the Community List and returns false only when the write failed (a List not
     * loaded yet, a signer that could not sign it), so the key is collected again.
     */
    suspend fun drainOne(
        delivery: ChannelDelivery,
        channelIdHex: HexKey,
        signer: NostrSigner,
        heldEpoch: Long?,
        trusted: () -> Boolean,
        adopt: suspend (ByteArray) -> Boolean,
    ): Boolean =
        when (ConcordChannelAccess.accessVerdict(delivery, channelIdHex, signer.pubKey.lowercase(), heldEpoch, trusted)) {
            AccessVerdict.WAIT -> false
            AccessVerdict.NOT_FOR_ME, AccessVerdict.STALE -> true
            AccessVerdict.OPEN ->
                when (val opened = ConcordChannelAccess.openAccessFor(delivery, signer, channelIdHex)) {
                    AccessOpening.Refused -> true
                    AccessOpening.SignerUnavailable -> false
                    is AccessOpening.Key -> adopt(opened.key)
                }
        }
}
