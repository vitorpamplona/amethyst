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
package com.vitorpamplona.quartz.nip84Highlights.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.graph.props.RoleProps
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.people.PubKeyReferenceTag
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

/**
 * NIP-84's attribution `p`: `["p", <pubkey>, <relay>, <role>]`. The role says what the person is
 * to the highlighted content ([AUTHOR_ROLE], `editor`, …); in a quote highlight the
 * [MENTION_ROLE] marks someone the comment cites instead.
 */
@Immutable
data class AttributionTag(
    override val pubKey: HexKey,
    override val relayHint: NormalizedRelayUrl? = null,
    val role: String? = null,
) : PubKeyReferenceTag {
    fun isMention() = role == MENTION_ROLE

    /** The person's role on the link to them, when the tag names one. */
    fun linkProps() = RoleProps(listOfNotNull(role))

    fun toTagArray() = assemble(pubKey, relayHint, role)

    companion object {
        const val TAG_NAME = "p"

        const val AUTHOR_ROLE = "author"
        const val MENTION_ROLE = "mention"

        fun parse(tag: Array<String>): AttributionTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }

            val hint = tag.getOrNull(2)?.let { RelayUrlNormalizer.normalizeOrNull(it) }

            return AttributionTag(tag[1], hint, tag.getOrNull(3)?.ifBlank { null })
        }

        /** A role keeps its relay slot, blank without a hint, so it stays in slot 3. */
        fun assemble(
            pubKey: HexKey,
            relayHint: NormalizedRelayUrl?,
            role: String?,
        ) = if (role != null) {
            arrayOf(TAG_NAME, pubKey, relayHint?.url ?: "", role)
        } else {
            arrayOfNotNull(TAG_NAME, pubKey, relayHint?.url)
        }
    }
}
