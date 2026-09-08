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
package com.vitorpamplona.quartz.experimental.publications.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.core.isValid
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.ensure

/**
 * NKBIP-01 `wikilink` tag: resolves one `[[double bracket]]` reference in a section's body.
 *
 * `["wikilink", "<target>", "<pubkey>", "<relay>", "<event id>"]` — everything after the target
 * is optional and often absent, so a tag may name a target with no way to reach it. That is the
 * normal case, not an error: the reader falls back to the target as plain text.
 */
@Immutable
data class WikilinkTag(
    val target: String,
    val pubKey: HexKey?,
    val relay: NormalizedRelayUrl?,
    val eventId: HexKey?,
) {
    companion object {
        const val TAG_NAME = "wikilink"

        fun parse(tag: Tag): WikilinkTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }

            return WikilinkTag(
                target = tag[1],
                // Positional slots, so a malformed entry must be dropped rather than shift the
                // ones after it — an event id read as a pubkey would address the wrong thing.
                pubKey = tag.getOrNull(2)?.takeIf { it.isValid() },
                relay = tag.getOrNull(3)?.takeIf { it.isNotEmpty() }?.let { RelayUrlNormalizer.normalizeOrNull(it) },
                eventId = tag.getOrNull(4)?.takeIf { it.isValid() },
            )
        }

        fun assemble(
            target: String,
            pubKey: HexKey? = null,
            relay: NormalizedRelayUrl? = null,
            eventId: HexKey? = null,
        ) = com.vitorpamplona.quartz.utils
            .arrayOfNotNull(TAG_NAME, target, pubKey, relay?.url, eventId)
    }
}
