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
package com.vitorpamplona.quartz.buzz.cwChannelWindow

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.DTag
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException

/**
 * A Buzz NIP-CW thread-bounds overlay (`kind:39007`), **signed by the relay** and synthesized
 * per thread-mode query (never stored; the relay rejects it at ingest). Exactly one is
 * appended to every served thread window — including empty and exhausted pages — and it is
 * the only authority on the thread window's exhaustion. Deliberately distinct from the
 * channel-mode [WindowBoundsEvent] (`39006`), whose channel/cursor key cannot tell concurrent
 * roots apart.
 *
 * Tags are exactly `["d","tw:1:<sha256 hex>"]` (the request binding, see
 * [ThreadWindowRequest.binding]), `["h",<channel uuid>]` and `["e",<root id>]`; `content` is a
 * [ThreadWindowBoundsContent]. Before trusting it a client MUST check the relay signer and
 * signature (the caller's job), the exact tags and binding ([matches]), and the body
 * ([ThreadWindowBoundsContent.isConsistent]). Clients never publish this kind; [build] exists
 * for fixtures/tests. Ground truth: `buzz-relay/src/api/bridge/thread_window.rs`,
 * `buzz-core/src/thread_window.rs`, `docs/nips/NIP-CW.md` §Thread Bounds.
 */
@Immutable
class ThreadWindowBoundsEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider {
    override fun eventHints(): List<EventIdHint> = tags.mapNotNull(ETag::parseAsHint)

    override fun linkedEventIds(): List<HexKey> = listOfNotNull(rootId())

    /** The request binding — the `d` tag, `tw:1:<sha256 hex>`. */
    fun binding() = dTag()

    /** The channel id — the `h` tag. */
    fun channelId() = tags.firstNotNullOfOrNull(GroupIdTag::parse)

    /** The thread root id — the `e` tag. */
    fun rootId() = tags.firstNotNullOfOrNull(ETag::parseId)

    /** Parses the JSON bounds [content]. Throws on malformed content; use [boundsOrNull]. */
    fun bounds(): ThreadWindowBoundsContent = ThreadWindowBoundsContent.decodeFromJson(content)

    fun boundsOrNull(): ThreadWindowBoundsContent? =
        try {
            bounds()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }

    /**
     * True when the tags are exactly one two-element `d`, `h` and `e` (and nothing else), the
     * `d` is a `tw:1:` binding, the `h` a lowercase hyphenated UUID, the `e` a lowercase
     * 64-hex id, and the body is consistent. Says nothing about which request it answers —
     * use [matches] for that.
     */
    fun isWellFormed(): Boolean {
        if (tags.size != 3) return false
        var d: String? = null
        var h: String? = null
        var e: String? = null
        for (tag in tags) {
            if (tag.size != 2) return false
            when (tag[0]) {
                DTag.TAG_NAME -> if (d == null) d = tag[1] else return false
                GroupIdTag.TAG_NAME -> if (h == null) h = tag[1] else return false
                ETag.TAG_NAME -> if (e == null) e = tag[1] else return false
                else -> return false
            }
        }
        if (d == null || h == null || e == null) return false
        if (!d.startsWith(ThreadWindowRequest.BINDING_PREFIX) || !isLowercaseHex(d.substring(ThreadWindowRequest.BINDING_PREFIX.length), 64)) return false
        if (!CANONICAL_UUID.matches(h) || !isLowercaseHex(e, 64)) return false
        return boundsOrNull()?.isConsistent() == true
    }

    /**
     * True when this well-formed overlay answers [request], as issued by [readerPubKey] to the
     * relay at [host]: the binding, channel and root all match.
     */
    fun matches(
        request: ThreadWindowRequest,
        host: String,
        readerPubKey: HexKey,
    ): Boolean =
        isWellFormed() &&
            binding() == request.binding(host, readerPubKey) &&
            channelId() == request.channelId.lowercase() &&
            rootId() == request.rootId.lowercase()

    companion object {
        const val KIND = 39007

        private val CANONICAL_UUID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

        private fun isLowercaseHex(
            value: String,
            length: Int,
        ) = value.length == length && value.all { it in '0'..'9' || it in 'a'..'f' }

        /** Builds the overlay the relay would sign for [request] (fixtures/tests only). */
        fun build(
            request: ThreadWindowRequest,
            host: String,
            readerPubKey: HexKey,
            bounds: ThreadWindowBoundsContent,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ThreadWindowBoundsEvent>.() -> Unit = {},
        ) = eventTemplate<ThreadWindowBoundsEvent>(KIND, bounds.encodeToJson(), createdAt) {
            dTag(request.binding(host, readerPubKey))
            addUnique(GroupIdTag.assemble(request.channelId.lowercase()))
            addUnique(ETag.assemble(request.rootId.lowercase(), null, null))
            initializer()
        }
    }
}
