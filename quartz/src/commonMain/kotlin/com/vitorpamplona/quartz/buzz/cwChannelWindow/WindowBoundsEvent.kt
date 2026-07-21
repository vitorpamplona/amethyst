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
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz NIP-CW window-bounds overlay (`kind:39006`), **signed by the relay** and
 * synthesized at query time (never stored). Exactly one is appended per window response and
 * it is the only authority on exhaustion. Addressable with `d` = `<channel_id>:<cursor|head>`
 * and `h`-tags the channel; `content` is a [WindowBoundsContent] JSON blob. Clients never
 * publish this kind; [build] exists for fixtures/tests. Ground truth:
 * `buzz-relay/src/api/bridge.rs` (window bounds).
 */
@Immutable
class WindowBoundsEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The window key — the `d` tag, formatted `<channel_id>:<cursor-or-head>`. */
    fun windowKey() = dTag()

    /** The channel id — the `h` tag. */
    fun channelId() = tags.firstNotNullOfOrNull(GroupIdTag::parse)

    /** Parses the JSON bounds [content]. Throws on malformed content; use [boundsOrNull]. */
    fun bounds(): WindowBoundsContent = WindowBoundsContent.decodeFromJson(content)

    fun boundsOrNull(): WindowBoundsContent? =
        try {
            bounds()
        } catch (_: Exception) {
            null
        }

    companion object {
        const val KIND = 39006

        /** The `d`-tag value for a window: `<channelId>:<cursor>`, where `cursor` is `head` for the first page. */
        fun windowKey(
            channelId: String,
            cursor: String = "head",
        ) = "$channelId:$cursor"

        fun build(
            channelId: String,
            bounds: WindowBoundsContent,
            cursor: String = "head",
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<WindowBoundsEvent>.() -> Unit = {},
        ) = eventTemplate<WindowBoundsEvent>(KIND, bounds.encodeToJson(), createdAt) {
            dTag(windowKey(channelId, cursor))
            addUnique(GroupIdTag.assemble(channelId))
            initializer()
        }
    }
}
