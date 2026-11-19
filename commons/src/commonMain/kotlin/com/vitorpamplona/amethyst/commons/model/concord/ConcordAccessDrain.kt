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
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey

/**
 * What the access drain (CORD-03 §5) does with one buffered key delivery, decided before any
 * signer call: settle it for good, keep it for a later fold, or open this account's blob.
 */
object ConcordAccessDrain {
    enum class Verdict {
        /** Not an `access` delivery of this Channel, or it holds no blob for this account: retire it. */
        NOT_FOR_ME,

        /** It hands an epoch this account already holds or has passed: retire it. */
        STALE,

        /** The fold does not trust it yet (the deliverer's or our own Grant may still fold), or it jumps too far ahead of the held epoch: keep it. */
        WAIT,

        /** Open this account's blob; whatever comes of it, the delivery is settled. */
        OPEN,
    }

    fun verdict(
        delivery: ChannelDelivery,
        channelIdHex: HexKey,
        me: HexKey,
        heldEpoch: Long?,
        trusted: () -> Boolean,
    ): Verdict {
        if (delivery.form != ConcordChannelAccess.FORM_ACCESS || !delivery.scopeIdHex.equals(channelIdHex, ignoreCase = true)) return Verdict.NOT_FOR_ME
        if (!holdsBlobFor(delivery, me, channelIdHex)) return Verdict.NOT_FOR_ME
        if (heldEpoch != null && delivery.newEpoch <= heldEpoch) return Verdict.STALE
        if (heldEpoch != null && delivery.newEpoch > heldEpoch + ConcordChannelAccess.MAX_EPOCH_JUMP) return Verdict.WAIT
        return if (trusted()) Verdict.OPEN else Verdict.WAIT
    }

    /** Whether [delivery] carries a blob at [me]'s locator (CORD-06 §1): no signer call, no decryption. */
    fun holdsBlobFor(
        delivery: ChannelDelivery,
        me: HexKey,
        scopeIdHex: HexKey,
    ): Boolean {
        val locator =
            runCatching {
                ConcordKeyDerivation.recipientLocator(delivery.deliverer.hexToByteArray(), me.hexToByteArray(), scopeIdHex.hexToByteArray(), delivery.newEpoch).toHexKey()
            }.getOrNull() ?: return false
        return delivery.blobs.any { it.locator == locator }
    }
}
