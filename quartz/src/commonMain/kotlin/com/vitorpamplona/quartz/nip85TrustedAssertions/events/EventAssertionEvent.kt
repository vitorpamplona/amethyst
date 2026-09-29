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
package com.vitorpamplona.quartz.nip85TrustedAssertions.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkProvider
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.links
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.DTag
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.tags.CommentCountTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.tags.QuoteCountTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.tags.ReactionCountTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.tags.RepostCountTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.tags.ZapAmountTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.tags.ZapCountTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.tags.contentSubjectProps
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.tags.RankTag
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class EventAssertionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    LinkProvider {
    fun aboutEvent() = tags.dTag()

    fun rank() = tags.firstNotNullOfOrNull(RankTag::parse)

    fun commentCount() = tags.firstNotNullOfOrNull(CommentCountTag::parse)

    fun quoteCount() = tags.firstNotNullOfOrNull(QuoteCountTag::parse)

    fun repostCount() = tags.firstNotNullOfOrNull(RepostCountTag::parse)

    fun reactionCount() = tags.firstNotNullOfOrNull(ReactionCountTag::parse)

    fun zapCount() = tags.firstNotNullOfOrNull(ZapCountTag::parse)

    fun zapAmount() = tags.firstNotNullOfOrNull(ZapAmountTag::parse)

    /**
     * NIP-85: the `d` is the SUBJECT, the event this assertion scores (not the assertion's own
     * identity), with the scores as props. An `e` equal to the `d` is only its relay hint.
     */
    override fun links(): List<Link<*>> = links { event(Relation.SUBJECT, aboutEvent(), DTag.TAG_NAME, tags.contentSubjectProps()) }

    companion object {
        const val KIND = 30383

        fun build(
            targetEventId: HexKey,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<EventAssertionEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            dTag(targetEventId)
            initializer()
        }
    }
}
