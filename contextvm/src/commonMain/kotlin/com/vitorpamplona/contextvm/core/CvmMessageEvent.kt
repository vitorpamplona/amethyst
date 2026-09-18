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
package com.vitorpamplona.contextvm.core

import com.vitorpamplona.contextvm.jsonrpc.JsonRpcCodec
import com.vitorpamplona.contextvm.jsonrpc.JsonRpcMessage
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
 * A ContextVM message: kind 25910, carrying a stringified MCP JSON-RPC message
 * in `content`.
 *
 * The ContextVM layering is deliberately thin — the MCP message is preserved
 * byte-for-byte and only addressing and correlation move into tags:
 *  - `p` names the peer this message is for
 *  - `e` references the request event a response answers
 *
 * `content` is a **JSON string**, not an embedded JSON object. The spec's
 * examples show it unstringified for readability, which is an easy trap; rule
 * `CVM-CORE-02` asserts the stringified form.
 *
 * This kind is ephemeral, so a response can only be received by a subscription
 * that was already live when the peer published it (`CVM-CORE-06`).
 */
class CvmMessageEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: TagArray,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The peer this message is addressed to, or null when untagged. */
    fun recipient(): HexKey? = tags.firstNotNullOfOrNull(PTag::parseKey)

    /** The request event id this message answers, or null when it is not a response. */
    fun inReplyTo(): HexKey? = tags.firstNotNullOfOrNull(ETag::parseId)

    /**
     * The JSON-RPC message in `content`.
     *
     * @throws com.vitorpamplona.contextvm.jsonrpc.JsonRpcFormatException when
     *   `content` is not a well-formed JSON-RPC 2.0 message.
     */
    fun message(): JsonRpcMessage = JsonRpcCodec.decode(content)

    /**
     * The discovery tags this message carries, per CEP-35.
     *
     * Routing tags are excluded; everything else is preserved, including tags we
     * do not understand, because CEP-35 makes forward compatibility the default.
     */
    fun discoveryTags(): List<Tag> = tags.filter { it.isNotEmpty() && !CvmTags.isRouting(it[0]) }

    companion object {
        const val KIND = CvmKinds.MESSAGE

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
        ) = eventTemplate<CvmMessageEvent>(KIND, JsonRpcCodec.encode(message), createdAt) {
            addAll(assembleTags(recipient, inReplyTo, extraTags))
        }

        suspend fun create(
            message: JsonRpcMessage,
            recipient: HexKey,
            signer: NostrSigner,
            inReplyTo: HexKey? = null,
            extraTags: List<Tag> = emptyList(),
            createdAt: Long = TimeUtils.now(),
        ): CvmMessageEvent =
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
