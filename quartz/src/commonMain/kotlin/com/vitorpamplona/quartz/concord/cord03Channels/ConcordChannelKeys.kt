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
package com.vitorpamplona.quartz.concord.cord03Channels

import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.concord.crypto.ConcordLabels
import com.vitorpamplona.quartz.concord.crypto.GroupKey

/**
 * Channel Chat Plane key derivation (CORD-03).
 *
 * Both channel types use the same `group_key("concord/channel", secret,
 * channel_id, epoch)` derivation; only the secret and epoch differ:
 *  - **Public** channels derive from the shared `community_root` at the current
 *    root epoch — every member can derive them, so no key is distributed.
 *  - **Private** channels derive from their own random `channel_key` at the
 *    channel's own epoch — the key is delivered on role grant and rotated on
 *    revocation.
 *
 * The `channel_id` is folded into the derivation so every channel gets a distinct
 * address regardless of the secret source, and it stays constant across
 * visibility conversions and epoch rotations.
 */
object ConcordChannelKeys {
    /** Public channel: derived from the community root at the root epoch. */
    fun publicChannel(
        communityRoot: ByteArray,
        channelId: ByteArray,
        rootEpoch: Long,
    ): GroupKey = ConcordKeyDerivation.groupKey(ConcordLabels.CHANNEL, communityRoot, channelId, rootEpoch)

    /** Private channel: derived from its own channel key at the channel epoch. */
    fun privateChannel(
        channelKey: ByteArray,
        channelId: ByteArray,
        channelEpoch: Long,
    ): GroupKey = ConcordKeyDerivation.groupKey(ConcordLabels.CHANNEL, channelKey, channelId, channelEpoch)
}
