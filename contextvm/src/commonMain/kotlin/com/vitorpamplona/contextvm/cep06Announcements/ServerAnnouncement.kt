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
package com.vitorpamplona.contextvm.cep06Announcements

import com.vitorpamplona.contextvm.core.CvmKinds
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind

/**
 * A CEP-6 announcement.
 *
 * `content` is the stringified result of the matching MCP call — the initialize
 * result for a server announcement, a list result for the others — so it is left
 * as text for the caller to decode with the same codec it uses on the wire.
 */
data class ServerAnnouncement(
    val kind: Kind,
    val pubKey: HexKey,
    val createdAt: Long,
    val content: String,
    val discovery: DiscoverySurface,
) {
    companion object {
        fun parseOrNull(event: Event): ServerAnnouncement? {
            if (event.kind !in CvmKinds.ANNOUNCEMENTS) return null
            return ServerAnnouncement(
                kind = event.kind,
                pubKey = event.pubKey,
                createdAt = event.createdAt,
                content = event.content,
                discovery = DiscoverySurface.parse(event.tags),
            )
        }

        /**
         * Keeps the newest announcement per `(kind, pubkey)`.
         *
         * These kinds are replaceable, so an older event arriving late from a
         * lagging relay must not overwrite a newer one already held.
         */
        fun latestPerKind(events: List<Event>): Map<Kind, ServerAnnouncement> =
            events
                .mapNotNull { parseOrNull(it) }
                .groupBy { it.kind }
                .mapValues { (_, list) -> list.maxBy { it.createdAt } }
    }
}
