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
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.ensure

/**
 * Tapestry Assistant Designation, a per-list entry on a user's kind 10040:
 * `["<kind>:<d-tag>", <assistant pubkey>, <relay>]` — "my assistant curates this list for me,
 * with the header at `<kind>:<assistant>:<d-tag>`".
 *
 * The d-tag is everything after the **first** colon and may itself contain colons. The word
 * `dlist-header` is reserved for [AssistantDesignationTag] and is never a per-list d-tag.
 */
@Immutable
data class DListCuration(
    val kind: Int,
    val dTag: String,
    val assistant: HexKey,
    val relay: NormalizedRelayUrl? = null,
) {
    /** The curated header: authorization is read from the Map, composition from this header. */
    fun headerAddress() = Address(kind, assistant, dTag)

    fun toTagArray() = DListCurationTag.assemble(this)
}

class DListCurationTag {
    companion object {
        val KINDS = setOf(39998, 39999)

        fun key(
            kind: Int,
            dTag: String,
        ) = "$kind:$dTag"

        fun isTag(tag: Array<String>) = parse(tag) != null

        fun parse(tag: Array<String>): DListCuration? {
            ensure(tag.has(1)) { return null }
            ensure(AssistantDesignationTag.isPubKey(tag[1])) { return null }

            val divider = tag[0].indexOf(':')
            ensure(divider > 0) { return null }

            val kind = tag[0].substring(0, divider).toIntOrNull() ?: return null
            ensure(kind in KINDS) { return null }

            val dTag = tag[0].substring(divider + 1)
            ensure(dTag.isNotEmpty()) { return null }
            ensure(dTag != AssistantDesignationTag.RESERVED_D_TAG) { return null }

            return DListCuration(kind, dTag, tag[1], tag.getOrNull(2)?.let { RelayUrlNormalizer.normalizeOrNull(it) })
        }

        fun assemble(entry: DListCuration) = arrayOf(key(entry.kind, entry.dTag), entry.assistant, entry.relay?.url ?: "")
    }
}
