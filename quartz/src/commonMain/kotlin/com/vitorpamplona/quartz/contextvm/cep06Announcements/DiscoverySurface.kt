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

import com.vitorpamplona.quartz.contextvm.core.CvmTags
import com.vitorpamplona.quartz.nip01Core.core.Tag

/**
 * What a peer told us about itself.
 *
 * CEP-35 makes this a *session* concept rather than an announcement one: the
 * same tag vocabulary arrives either on a public announcement (CEP-6) or on the
 * first direct message of a session, and both are treated as equivalent.
 */
data class DiscoverySurface(
    val name: String? = null,
    val about: String? = null,
    val picture: String? = null,
    val website: String? = null,
    val supportsEncryption: Boolean = false,
    val supportsEphemeralEncryption: Boolean = false,
    val supportsOversizedTransfer: Boolean = false,
    val supportsOpenStream: Boolean = false,
    /**
     * Everything else, routing excluded.
     *
     * CEP-35 requires unknown discovery tags to be preserved rather than
     * discarded, so custom protocols can build on the same exchange. Keeping
     * them reachable is what makes that work.
     */
    val unknownTags: List<Tag> = emptyList(),
) {
    /**
     * Whether the peer said anything about itself at all.
     *
     * The distinction this exists for: CEP-35 reads an absent flag as "not
     * supported", which is right for a peer that sent a discovery surface and
     * left a flag out of it. It is wrong for a peer that sent no discovery tags
     * whatsoever — that peer has made no claim, and treating its silence as a
     * denial would have us conclude it cannot do anything.
     *
     * This is not hypothetical. A live cordn coordinator's kind-25910 responses
     * carry only the routing tags `p` and `e` (observed on the public relays,
     * 2026-09-23), so reading them as a full surface would say "supports
     * nothing" about a server that in fact accepts every wrap we send.
     */
    val declaresNothing: Boolean
        get() =
            name == null &&
                about == null &&
                picture == null &&
                website == null &&
                !supportsEncryption &&
                !supportsEphemeralEncryption &&
                !supportsOversizedTransfer &&
                !supportsOpenStream &&
                unknownTags.isEmpty()

    /** Raw access for a caller that understands a tag this version does not. */
    fun rawTag(name: String): Tag? = unknownTags.firstOrNull { it.isNotEmpty() && it[0] == name }

    companion object {
        private val KNOWN =
            setOf(
                CvmTags.NAME,
                CvmTags.ABOUT,
                CvmTags.PICTURE,
                CvmTags.WEBSITE,
                CvmTags.SUPPORT_ENCRYPTION,
                CvmTags.SUPPORT_ENCRYPTION_EPHEMERAL,
                CvmTags.SUPPORT_OVERSIZED_TRANSFER,
                CvmTags.SUPPORT_OPEN_STREAM,
            )

        fun parse(tags: Array<Tag>): DiscoverySurface {
            fun value(name: String) = tags.firstOrNull { it.size >= 2 && it[0] == name }?.get(1)

            return DiscoverySurface(
                name = value(CvmTags.NAME),
                about = value(CvmTags.ABOUT),
                picture = value(CvmTags.PICTURE),
                website = value(CvmTags.WEBSITE),
                supportsEncryption = CvmTags.hasFlag(tags, CvmTags.SUPPORT_ENCRYPTION),
                supportsEphemeralEncryption = CvmTags.hasFlag(tags, CvmTags.SUPPORT_ENCRYPTION_EPHEMERAL),
                supportsOversizedTransfer = CvmTags.hasFlag(tags, CvmTags.SUPPORT_OVERSIZED_TRANSFER),
                supportsOpenStream = CvmTags.hasFlag(tags, CvmTags.SUPPORT_OPEN_STREAM),
                unknownTags =
                    tags.filter {
                        it.isNotEmpty() && it[0] !in KNOWN && !CvmTags.isRouting(it[0])
                    },
            )
        }
    }
}
