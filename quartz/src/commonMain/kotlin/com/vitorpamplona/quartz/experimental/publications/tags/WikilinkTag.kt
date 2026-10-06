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
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.arrayOfNotNull
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
                relay = parseRelay(tag),
                eventId = eventIdSlot(tag)?.let { tag[it] },
            )
        }

        /** The linked author (slot 2), when it is a valid pubkey. */
        fun parseKey(tag: Tag): HexKey? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[2].isValid()) { return null }
            return tag[2]
        }

        /** The linked event (slot 4, or slot 3 when the empty relay slot was dropped), when it is a valid id. */
        fun parseEventId(tag: Tag): HexKey? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            return eventIdSlot(tag)?.let { tag[it] }
        }

        /**
         * Where the event id sits. NKBIP-01 puts it in slot 4, behind a relay slot that
         * [assemble] keeps as `""` when empty — but a writer that drops the empty slot publishes
         * `["wikilink", t, <pubkey>, <id>]`. A relay URL can never be 64 hex chars, so a valid
         * id in slot 3 is that shape: read it as the event id rather than letting a relay
         * parser turn it into `wss://<id>/`. (`["wikilink", t, <id>]`, with the pubkey dropped
         * too, is indistinguishable from a pubkey and stays read as one.)
         */
        private fun eventIdSlot(tag: Tag): Int? =
            when {
                tag.has(4) && tag[4].isValid() -> 4
                tag.has(3) && tag[3].isValid() -> 3
                else -> null
            }

        /** Slot 3, only when it holds a real `ws(s)://` relay URL (never an id or a label). */
        private fun parseRelay(tag: Tag): NormalizedRelayUrl? = RelayUrlNormalizer.normalizeHintOrNull(tag.getOrNull(3))

        /** The linked author with the relay in slot 3; null without both. */
        fun parseKeyAsHint(tag: Tag): PubKeyHint? {
            ensure(tag.has(3)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[2].isValid()) { return null }
            val relay = parseRelay(tag) ?: return null
            return PubKeyHint(tag[2], relay)
        }

        /** The linked event with the relay in slot 3; null without both. */
        fun parseEventAsHint(tag: Tag): EventIdHint? {
            ensure(tag.has(4)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[4].isValid()) { return null }
            val relay = parseRelay(tag) ?: return null
            return EventIdHint(tag[4], relay)
        }

        /**
         * Keeps NKBIP-01's positional slots: [arrayOfNotNull] writes a missing pubkey or relay
         * before a present value as `""`, so the event id always lands in slot 4, and only
         * trailing nulls are trimmed.
         */
        fun assemble(
            target: String,
            pubKey: HexKey? = null,
            relay: NormalizedRelayUrl? = null,
            eventId: HexKey? = null,
        ) = arrayOfNotNull(TAG_NAME, target, pubKey, relay?.url, eventId)
    }
}
