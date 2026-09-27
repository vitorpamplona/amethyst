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
package com.vitorpamplona.quartz.contextvm.core

import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcCodec
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcMessage
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A typed view over a ContextVM message event: kind 25910, carrying a
 * stringified MCP JSON-RPC message in `content`.
 *
 * This wraps an [Event] rather than extending it. Quartz mints event subclasses
 * through its own kind-to-class factory, which knows nothing about 25910, so an
 * event signed or parsed anywhere is always a plain [Event] — a subclass would
 * only ever exist where we constructed one by hand, and claiming a signer could
 * return one is simply false.
 *
 * The ContextVM layering is deliberately thin: the MCP message is preserved
 * byte-for-byte and only addressing and correlation move into tags, `p` for the
 * peer and `e` for the request a response answers.
 *
 * `content` is a **JSON string**, not an embedded JSON object. The spec's
 * examples show it unstringified for readability, which is an easy trap; rule
 * `CVM-CORE-02` asserts the stringified form.
 *
 * This kind is ephemeral, so a response can only be received by a subscription
 * that was already live when the peer published it (`CVM-CORE-06`).
 */
class CvmMessageEvent(
    val event: Event,
) {
    val id: HexKey get() = event.id
    val pubKey: HexKey get() = event.pubKey
    val createdAt: Long get() = event.createdAt
    val tags: TagArray get() = event.tags
    val content: String get() = event.content

    /** The peer this message is addressed to, or null when untagged. */
    fun recipient(): HexKey? = event.tags.firstNotNullOfOrNull(PTag::parseKey)

    /** The request event id this message answers, or null when it is not a response. */
    fun inReplyTo(): HexKey? = event.tags.firstNotNullOfOrNull(ETag::parseId)

    /**
     * The JSON-RPC message in `content`.
     *
     * @throws com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcFormatException when
     *   `content` is not a well-formed JSON-RPC 2.0 message.
     */
    fun message(): JsonRpcMessage = JsonRpcCodec.decode(event.content)

    /**
     * The discovery tags this message carries, per CEP-35.
     *
     * Routing tags are excluded; everything else is preserved, including tags we
     * do not understand, because CEP-35 makes forward compatibility the default.
     */
    fun discoveryTags(): List<Tag> = event.tags.filter { it.isNotEmpty() && !CvmTags.isRouting(it[0]) }

    companion object {
        const val KIND = CvmKinds.MESSAGE

        /** Wraps [event] when it is a ContextVM message, or returns null. */
        fun fromOrNull(event: Event) = if (event.kind == KIND) CvmMessageEvent(event) else null

        /**
         * Template for a message addressed to [recipient], optionally answering
         * [inReplyTo].
         *
         * [extraTags] carries this side's CEP-35 discovery tags on the first
         * direct message of a session; omit them afterwards.
         */
        fun build(
            message: JsonRpcMessage,
            recipient: HexKey,
            inReplyTo: HexKey? = null,
            extraTags: List<Tag> = emptyList(),
            createdAt: Long = TimeUtils.now(),
        ) = eventTemplate<Event>(KIND, JsonRpcCodec.encode(message), createdAt) {
            addAll(assembleTags(recipient, inReplyTo, extraTags))
        }

        /**
         * Signs a message for [recipient].
         *
         * Returns a plain [Event] because that is what the signer produces; wrap
         * it with [fromOrNull] when a typed view is wanted.
         */
        suspend fun create(
            message: JsonRpcMessage,
            recipient: HexKey,
            signer: NostrSigner,
            inReplyTo: HexKey? = null,
            extraTags: List<Tag> = emptyList(),
            createdAt: Long = TimeUtils.now(),
        ): Event =
            signer.sign(
                createdAt,
                KIND,
                assembleTags(recipient, inReplyTo, extraTags),
                JsonRpcCodec.encode(message),
            )

        private fun assembleTags(
            recipient: HexKey,
            inReplyTo: HexKey?,
            extraTags: List<Tag>,
        ): TagArray =
            buildList {
                add(PTag.assemble(recipient, relayHint = null))
                inReplyTo?.let { add(ETag.assemble(it, relay = null, author = null)) }
                addAll(extraTags)
            }.toTypedArray()
    }
}
