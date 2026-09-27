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
package com.vitorpamplona.quartz.contextvm.cep06Announcements

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.contextvm.core.CvmKinds
import com.vitorpamplona.quartz.nip01Core.core.BaseReplaceableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * A server's CEP-6 announcement of itself: kind [CvmKinds.SERVER_ANNOUNCEMENT].
 *
 * A typed class so the announcement can live in the event cache like anything
 * else. Without one the kind is unlisted, and an unlisted kind is rejected as
 * unsupported on the way in -- which is why a coordinator's announced name had
 * to be fetched and parsed by hand everywhere it was wanted, with the
 * newest-wins rule, the author check and the signature check all re-derived per
 * caller. Being replaceable (10000..19999), the cache now keeps the newest per
 * (kind, pubkey) and verifies before trusting it.
 *
 * [discovery] is the surface the server advertises -- its name, its blurb, and
 * what it says it supports. Every field of it is the server's own claim;
 * [pubKey] is the only thing here that is not.
 */
@Immutable
class CvmServerAnnouncementEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseReplaceableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    /**
     * What the server says about itself, parsed from the tags.
     *
     * Not cached on the instance: the cache keeps one event per coordinator and
     * the screens read a name off it, so the parse is rare and cheap next to
     * holding a second copy of every surface in memory.
     */
    fun discovery(): DiscoverySurface = DiscoverySurface.parse(tags)

    /** The server's own name for itself, or null when it publishes none. */
    fun serverName(): String? = discovery().name?.takeIf { it.isNotBlank() }

    companion object {
        const val KIND = CvmKinds.SERVER_ANNOUNCEMENT
    }
}

/**
 * A server's CEP-6 `tools/list` announcement: kind [CvmKinds.TOOLS_LIST].
 *
 * Stored for the same reason as [CvmServerAnnouncementEvent], and needed
 * alongside it because what makes a ContextVM server a *cordn coordinator* is
 * the eleven tools it advertises here, not anything it says about itself.
 */
@Immutable
class CvmToolsListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseReplaceableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The advertised tools, or null when the content does not parse as a list. */
    fun tools(): AnnouncedTools? = AnnouncedTools.parseOrNull(content)

    companion object {
        const val KIND = CvmKinds.TOOLS_LIST
    }
}
