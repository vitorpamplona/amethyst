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
package com.vitorpamplona.quartz.nip85TrustedAssertions.externalIds

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkProvider
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.links
import com.vitorpamplona.quartz.nip01Core.links.valueTags
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.tags.CommentCountTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.tags.ReactionCountTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.tags.contentAssertionScores
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.RankTag
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class ExternalIdAssertionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    LinkProvider {
    fun aboutExternalId() = tags.dTag()

    fun rank() = tags.firstNotNullOfOrNull(RankTag::parse)

    fun commentCount() = tags.firstNotNullOfOrNull(CommentCountTag::parse)

    fun reactionCount() = tags.firstNotNullOfOrNull(ReactionCountTag::parse)

    /**
     * NIP-85: the `d` is the SUBJECT, the NIP-73 identifier this assertion scores (the same node a
     * NIP-73 `i` names), with the scores as props; the `k` tags are its NIP-73 kinds.
     */
    override fun links(): List<Link> =
        links {
            tag(Relation.SUBJECT, "i", aboutExternalId(), "d", tags.contentAssertionScores())
            valueTags(Relation.TAG, tags, "k")
        }

    companion object {
        const val KIND = 30385

        fun build(
            targetIdentifier: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ExternalIdAssertionEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            dTag(targetIdentifier)
            initializer()
        }
    }
}
