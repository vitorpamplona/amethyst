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

import com.vitorpamplona.quartz.concord.cord03Channels.tags.ChannelTag
import com.vitorpamplona.quartz.concord.cord03Channels.tags.EpochTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent

/**
 * Chat Plane message binding (CORD-03).
 *
 * A Concord chat rumor **is** a standard Nostr event — a kind-9 [ChatEvent]
 * message/reply or a kind-7 [ReactionEvent] — that additionally commits to the
 * channel and epoch it belongs to via `["channel", <id>]` + `["epoch", <n>]` tags
 * (see [channel]/[epoch] and [ChannelTag]/[EpochTag]). This object reuses the
 * standard event builders and only adds the binding, so the same event classes
 * that render everywhere else in the app render Concord messages too. Recipients
 * enforce the binding ([TagArray.isConcordBoundTo]) so an event lifted from one
 * channel/epoch can't be replayed into another.
 */
object ChannelChat {
    /**
     * Builds an unsigned kind-9 [ChatEvent] rumor bound to [channelId]/[epoch].
     * Wrap it for the channel plane with
     * [com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope] to publish.
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
            authorPubKey,
            ChatEvent.build(text, createdAt) {
                channelBinding(channelId, epoch)
                extraTags.forEach { addUnique(it) }
            },
        )

    /**
     * Builds an unsigned kind-1111 **thread reply** ([CommentEvent], NIP-22) to
     * [parent], bound to [channelId]/[epoch].
     *
     * A thread reply is a NIP-22 comment — NOT a kind-9 message with a `q` tag
     * (which NIP-C7 reserves for *inline quotes* that clients deliberately keep out
     * of threads). [CommentEvent.replyBuilder] emits the uppercase `K`/`E`/`P`
     * pointers at the immutable thread root and the lowercase `k`/`e`/`p` pointers
     * at the immediate [parent] (inheriting the root when [parent] is itself a
     * comment, so the root is stable at any depth). We add the same
     * `["channel", …]` + `["epoch", …]` binding every Chat Plane rumor carries, so
     * the reply is verifiable against the plane it arrives on. This is exactly the
     * shape Soapbox Armada builds and groups into a message's thread.
     */
    fun reply(
        authorPubKey: HexKey,
        channelId: HexKey,
        epoch: Long,
        text: String,
        parent: Event,
        createdAt: Long,
    ): Event =
        RumorAssembler.assembleRumor(
            authorPubKey,
            CommentEvent.replyBuilder(text, EventHintBundle(parent), createdAt) {
                channelBinding(channelId, epoch)
            },
        )

    /**
     * Builds an unsigned kind-7 [ReactionEvent] rumor bound to [channelId]/[epoch]
     * against the target message ([targetId]/[targetAuthor]/[targetKind]). [content]
     * is the reaction (e.g. `"+"`, `"🤙"`). Kept to the minimal `e`/`p`/`k` tag form
     * (no relay hints) so it stays wire-identical across clients. On the receiving
     * side it decrypts to a normal kind-7 that wires to its target Note by the `e`
     * tag through the shared cache.
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
        RumorAssembler.assembleRumor<ReactionEvent>(
            pubKey = authorPubKey,
            createdAt = createdAt,
            kind = ReactionEvent.KIND,
            tags =
                arrayOf(
                    ChannelTag.assemble(channelId),
                    EpochTag.assemble(epoch),
                    arrayOf("e", targetId),
                    arrayOf("p", targetAuthor),
                    arrayOf("k", targetKind.toString()),
                ),
            content = content,
        )

    /** The channel id a Chat Plane [rumor] is bound to, or null if unbound. */
    fun channelOf(rumor: Event): HexKey? = rumor.tags.concordChannel()

    /** The epoch a Chat Plane [rumor] is bound to, or null if unbound/malformed. */
    fun epochOf(rumor: Event): Long? = rumor.tags.concordEpoch()

    /**
     * True when [rumor] is bound to exactly [channelId] and [epoch]. Recipients must
     * reject any Chat Plane event whose binding does not match the plane it arrived on.
     */
    fun isBoundTo(
        rumor: Event,
        channelId: HexKey,
        epoch: Long,
    ): Boolean = rumor.tags.isConcordBoundTo(channelId, epoch)
}
