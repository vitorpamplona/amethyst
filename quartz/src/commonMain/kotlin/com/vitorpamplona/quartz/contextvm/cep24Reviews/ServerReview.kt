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
package com.vitorpamplona.quartz.contextvm.cep24Reviews

import com.vitorpamplona.quartz.contextvm.core.CvmKinds
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Tag

/** CEP-24 server reviews: NIP-22 comments anchored to a kind-11316 announcement. */
object ServerReview {
    /** The addressable coordinate a review targets. */
    fun coordinate(serverPubKey: HexKey) = "${CvmKinds.SERVER_ANNOUNCEMENT}:$serverPubKey:"

    /**
     * Tags for a top-level review.
     *
     * NIP-22 uses uppercase tags for the root and lowercase for the immediate
     * parent; for a top-level comment both are the announcement, hence the
     * apparent duplication.
     */
    fun topLevelTags(
        serverPubKey: HexKey,
        relayHint: String? = null,
        announcementEventId: HexKey? = null,
    ): List<Tag> {
        val coordinate = coordinate(serverPubKey)
        val kind = CvmKinds.SERVER_ANNOUNCEMENT.toString()
        return buildList {
            add(tagOf("A", coordinate, relayHint))
            add(arrayOf("K", kind))
            add(tagOf("P", serverPubKey, relayHint))
            add(tagOf("a", coordinate, relayHint))
            announcementEventId?.let { add(arrayOf("e", it, relayHint ?: "", serverPubKey)) }
            add(arrayOf("k", kind))
            add(tagOf("p", serverPubKey, relayHint))
        }
    }

    /**
     * Tags for a reply to an existing review.
     *
     * The uppercase root stays on the announcement while the lowercase parent
     * moves to the comment being answered — that asymmetry is the whole point of
     * NIP-22's dual tagging and is easy to get wrong.
     */
    fun replyTags(
        serverPubKey: HexKey,
        parentCommentId: HexKey,
        parentAuthor: HexKey,
        relayHint: String? = null,
    ): List<Tag> =
        buildList {
            add(tagOf("A", coordinate(serverPubKey), relayHint))
            add(arrayOf("K", CvmKinds.SERVER_ANNOUNCEMENT.toString()))
            add(tagOf("P", serverPubKey, relayHint))
            add(arrayOf("e", parentCommentId, relayHint ?: "", parentAuthor))
            add(arrayOf("k", CvmKinds.REVIEW.toString()))
            add(tagOf("p", parentAuthor, relayHint))
        }

    private fun tagOf(
        name: String,
        value: String,
        relayHint: String?,
    ): Tag = if (relayHint != null) arrayOf(name, value, relayHint) else arrayOf(name, value)
}
