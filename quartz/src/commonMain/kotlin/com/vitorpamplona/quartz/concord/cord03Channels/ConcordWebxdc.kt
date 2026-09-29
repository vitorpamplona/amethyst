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
import com.vitorpamplona.quartz.concord.cord03Channels.tags.MsTag
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordJson
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A WebXDC realtime peer signal: [topic] is the app's gossip topic (52 base32 characters), and
 * [addr] the advertised, opaque node address — null for a departure (`"left"`).
 */
class WebxdcPeerSignal(
    val topic: String,
    val addr: String?,
) {
    val isAdvert: Boolean get() = addr != null
}

/**
 * WebXDC on the Chat Plane (kind 3310, CORD-02 Appendix B; examples §2.6). The CORDs register the
 * kind — a Chat rumor with the usual `channel`/`epoch`/`ms` binding — but do not pin its payload: the
 * content is app-level and opaque to the protocol. A 3310 is **never a chat message**: it is not a
 * feed row, not a preview, not unread; a client with no WebXDC host just holds it for one.
 *
 * What rides it in practice is the reference client's (Armada, interoperating with Vector), which
 * this object builds and parses so a future host has the plumbing:
 *  - an app **state update** for one app session: `["i", <session>]` and `["alt", "Webxdc update"]`
 *    (plus optional `info`, `document`, `summary`), the content being the JSON payload;
 *  - a realtime **peer signal**, untagged beyond the binding: content
 *    `{"op":"ad","topic":<52-char base32>,"addr":<node address>}` to advertise a gossip endpoint, or
 *    `{"op":"left","topic":…}` to withdraw it.
 * Amethyst has no WebXDC runtime; see the conformance review (F10) for what full support would take.
 */
object ConcordWebxdc {
    const val KIND = 3310

    const val TAG_SESSION = "i"
    const val TAG_ALT = "alt"
    const val ALT_UPDATE = "Webxdc update"
    const val TAG_INFO = "info"
    const val TAG_DOCUMENT = "document"
    const val TAG_SUMMARY = "summary"

    /** A topic id is 32 bytes, so its RFC 4648 base32 form (no padding) is always this long. */
    const val TOPIC_ID_CHARS = 52

    /** The longest advertised node address honored (Vector's cap); anything longer is not one. */
    const val MAX_NODE_ADDR_CHARS = 2048

    private const val OP_AD = "ad"
    private const val OP_LEFT = "left"

    /** True when [rumor] is a WebXDC signal. */
    fun isWebxdc(rumor: Event): Boolean = rumor.kind == KIND

    /** An app state update for app session [session] on [channelId]/[epoch]; [payload] is the app's JSON. */
    fun stateUpdate(
        authorPubKey: HexKey,
        channelId: HexKey,
        epoch: Long,
        session: String,
        payload: String,
        createdAt: Long,
        info: String? = null,
        document: String? = null,
        summary: String? = null,
        ms: Int = MsTag.remainderFor(createdAt),
    ): Event {
        val tags = binding(channelId, epoch, ms)
        tags.add(arrayOf(TAG_SESSION, session))
        tags.add(arrayOf(TAG_ALT, ALT_UPDATE))
        if (info != null) tags.add(arrayOf(TAG_INFO, info))
        if (document != null) tags.add(arrayOf(TAG_DOCUMENT, document))
        if (summary != null) tags.add(arrayOf(TAG_SUMMARY, summary))
        return RumorAssembler.assembleRumor(authorPubKey, createdAt, KIND, tags.toTypedArray(), payload)
    }

    /**
     * A realtime peer signal on [channelId]/[epoch]: an advert of [nodeAddr] for [topic], or, when
     * [nodeAddr] is null, the departure from it.
     */
    fun peerSignal(
        authorPubKey: HexKey,
        channelId: HexKey,
        epoch: Long,
        topic: String,
        nodeAddr: String?,
        createdAt: Long,
        ms: Int = MsTag.remainderFor(createdAt),
    ): Event = RumorAssembler.assembleRumor(authorPubKey, createdAt, KIND, binding(channelId, epoch, ms).toTypedArray(), peerSignalContent(topic, nodeAddr))

    /** The peer-signal body, key order `op`, `topic`, `addr` as the reference client writes it. */
    fun peerSignalContent(
        topic: String,
        nodeAddr: String?,
    ): String =
        buildJsonObject {
            put("op", if (nodeAddr == null) OP_LEFT else OP_AD)
            put("topic", topic)
            if (nodeAddr != null) put("addr", nodeAddr)
        }.toString()

    /** The app session a state update belongs to, or null (a peer signal carries none). */
    fun sessionOf(rumor: Event): String? = if (rumor.kind == KIND) rumor.tags.firstTagValue(TAG_SESSION) else null

    /** Exactly [TOPIC_ID_CHARS] uppercase RFC 4648 base32 characters (Vector's receive-side check). */
    fun isTopicId(value: String?): Boolean = value != null && value.length == TOPIC_ID_CHARS && value.all { it in 'A'..'Z' || it in '2'..'7' }

    /**
     * Parses an untrusted peer-signal body: null unless it is `{"op":"ad","topic","addr"}` with a
     * valid topic and a non-empty address of at most [MAX_NODE_ADDR_CHARS], or `{"op":"left","topic"}`.
     */
    fun parsePeerSignal(content: String): WebxdcPeerSignal? {
        val obj = runCatching { ConcordJson.instance.parseToJsonElement(content) }.getOrNull() as? JsonObject ?: return null
        val topic = (obj["topic"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        if (!isTopicId(topic)) return null
        val op = (obj["op"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        return when (op) {
            OP_LEFT -> WebxdcPeerSignal(topic, null)
            OP_AD -> {
                val addr = (obj["addr"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
                if (addr.isEmpty() || addr.length > MAX_NODE_ADDR_CHARS) return null
                WebxdcPeerSignal(topic, addr)
            }
            else -> null
        }
    }

    /** The binding every Chat rumor commits, in the examples' order: channel, epoch, ms. */
    private fun binding(
        channelId: HexKey,
        epoch: Long,
        ms: Int,
    ): ArrayList<Array<String>> = arrayListOf(ChannelTag.assemble(channelId), EpochTag.assemble(epoch), MsTag.assemble(ms))
}
