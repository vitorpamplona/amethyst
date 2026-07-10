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

import com.vitorpamplona.quartz.concord.events.ConcordKinds
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler

/**
 * Chat Plane message binding (CORD-03).
 *
 * Every Chat Plane rumor — a message, reply, reaction, edit, or delete — commits
 * to the channel and epoch it belongs to via `["channel", <id>]` and
 * `["epoch", <n>]` tags inside the author-signed rumor. Recipients enforce this
 * binding ([isBoundTo]) so an event lifted from one channel/epoch can't be
 * replayed into another.
 */
object ChannelChat {
    const val TAG_CHANNEL = "channel"
    const val TAG_EPOCH = "epoch"

    /** Builds the channel/epoch binding tags shared by every Chat Plane rumor. */
    fun bindingTags(
        channelId: HexKey,
        epoch: Long,
    ): Array<Array<String>> = arrayOf(arrayOf(TAG_CHANNEL, channelId), arrayOf(TAG_EPOCH, epoch.toString()))

    /**
     * Builds an unsigned kind-9 chat message rumor bound to [channelId]/[epoch].
     * Wrap it for the channel plane with
     * [com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope] (encrypted
     * seal) to publish.
     */
    fun message(
        authorPubKey: HexKey,
        channelId: HexKey,
        epoch: Long,
        text: String,
        createdAt: Long,
        extraTags: Array<Array<String>> = emptyArray(),
    ): Event =
        RumorAssembler.assembleRumor(
            pubKey = authorPubKey,
            createdAt = createdAt,
            kind = ConcordKinds.MESSAGE,
            tags = bindingTags(channelId, epoch) + extraTags,
            content = text,
        )

    /**
     * Builds an unsigned kind-9 reply rumor bound to [channelId]/[epoch], quoting
     * [parentId] (a `q` tag, NIP-C7 style) and crediting its author with a `p` tag.
     * Reuses [message], so it is a normal channel message that also threads.
     */
    fun reply(
        authorPubKey: HexKey,
        channelId: HexKey,
        epoch: Long,
        text: String,
        parentId: HexKey,
        parentAuthor: HexKey,
        createdAt: Long,
    ): Event =
        message(
            authorPubKey = authorPubKey,
            channelId = channelId,
            epoch = epoch,
            text = text,
            createdAt = createdAt,
            extraTags = arrayOf(arrayOf("q", parentId), arrayOf("p", parentAuthor)),
        )

    /**
     * Builds an unsigned kind-7 reaction rumor bound to [channelId]/[epoch] against
     * the target message ([targetId]/[targetAuthor]/[targetKind]). [content] is the
     * reaction (e.g. `"+"`, `"🤙"`). On the receiving side this decrypts to a normal
     * kind-7 that wires to its target Note by the `e` tag through the shared cache.
     */
    fun reaction(
        authorPubKey: HexKey,
        channelId: HexKey,
        epoch: Long,
        targetId: HexKey,
        targetAuthor: HexKey,
        targetKind: Int,
        content: String,
        createdAt: Long,
    ): Event =
        RumorAssembler.assembleRumor(
            pubKey = authorPubKey,
            createdAt = createdAt,
            kind = ConcordKinds.REACTION,
            tags =
                bindingTags(channelId, epoch) +
                    arrayOf(
                        arrayOf("e", targetId),
                        arrayOf("p", targetAuthor),
                        arrayOf("k", targetKind.toString()),
                    ),
            content = content,
        )

    /** The channel id a Chat Plane [rumor] is bound to, or null if unbound. */
    fun channelOf(rumor: Event): HexKey? = rumor.tags.firstTagValue(TAG_CHANNEL)

    /** The epoch a Chat Plane [rumor] is bound to, or null if unbound/malformed. */
    fun epochOf(rumor: Event): Long? = rumor.tags.firstTagValue(TAG_EPOCH)?.toLongOrNull()

    /**
     * True when [rumor] is bound to exactly [channelId] and [epoch]. Recipients
     * must reject any Chat Plane event whose binding does not match the plane it
     * arrived on.
     */
    fun isBoundTo(
        rumor: Event,
        channelId: HexKey,
        epoch: Long,
    ): Boolean = channelOf(rumor) == channelId && epochOf(rumor) == epoch
}
