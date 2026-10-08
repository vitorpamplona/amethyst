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
package com.vitorpamplona.quartz.nip34Git.ci.tags

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.ensure

/**
 * Nostr CI `e` tag on a Repository Secret Update (29846):
 * `["e", "<coordinator-advertisement-id>", "<relay-hint>", "secrets-key"]`.
 *
 * It names the exact Coordinator Advertisement (19843) whose `secrets-key` the update was
 * encrypted to. The `secrets-key` marker is required; an `e` without it is not this reference.
 * The relay hint locates the Advertisement — it is not where the update is submitted.
 */
class SecretsKeyAdvertisementTag {
    companion object {
        const val TAG_NAME = "e"
        const val MARKER = "secrets-key"

        fun isTag(tag: Array<String>) = parseId(tag) != null

        fun parseId(tag: Array<String>): HexKey? {
            ensure(tag.has(3)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64 && Hex.isHex(tag[1])) { return null }
            ensure(tag[3] == MARKER) { return null }
            return tag[1]
        }

        fun parseAsHint(tag: Array<String>): EventIdHint? {
            val id = parseId(tag) ?: return null
            val relay = RelayUrlNormalizer.normalizeHintOrNull(tag[2]) ?: return null
            return EventIdHint(id, relay)
        }

        fun assemble(
            advertisementId: HexKey,
            relay: NormalizedRelayUrl?,
        ) = arrayOf(TAG_NAME, advertisementId, relay?.url ?: "", MARKER)
    }
}
