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
package com.vitorpamplona.quartz.experimental.decentralizedLists.assistant.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.ensure

/**
 * Tapestry Assistant Designation, the blanket entry on a user's kind 10040:
 * `["39998:dlist-header", <assistant pubkey>, <relay>]` — "this pubkey authors my concept /
 * list headers on my behalf".
 *
 * Signed by the user, so it is a delegation the user granted; revoked by republishing the Map
 * without it. The relay may be the empty string when the writer had none configured, and the
 * designation stands without it.
 */
@Immutable
data class AssistantDesignation(
    val assistant: HexKey,
    val relay: NormalizedRelayUrl? = null,
) {
    fun toTagArray() = AssistantDesignationTag.assemble(assistant, relay)
}

class AssistantDesignationTag {
    companion object {
        const val RESERVED_D_TAG = "dlist-header"
        const val KEY = "39998:$RESERVED_D_TAG"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == KEY && isPubKey(tag[1])

        fun parse(tag: Array<String>): AssistantDesignation? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == KEY) { return null }
            ensure(isPubKey(tag[1])) { return null }
            return AssistantDesignation(tag[1], tag.getOrNull(2)?.let { RelayUrlNormalizer.normalizeOrNull(it) })
        }

        // Always three elements: an empty relay keeps the shape the spec fixes.
        fun assemble(
            assistant: HexKey,
            relay: NormalizedRelayUrl?,
        ) = arrayOf(KEY, assistant, relay?.url ?: "")

        internal fun isPubKey(value: String) = value.length == 64 && Hex.isHex64(value)
    }
}
