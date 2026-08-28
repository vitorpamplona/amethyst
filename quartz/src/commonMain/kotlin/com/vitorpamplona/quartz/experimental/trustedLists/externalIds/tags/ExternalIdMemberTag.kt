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
package com.vitorpamplona.quartz.experimental.trustedLists.externalIds.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.trustedLists.tags.MemberTagFields
import com.vitorpamplona.quartz.experimental.trustedLists.tags.TrustedListMemberTag
import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip73ExternalIds.ExternalId
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

/**
 * An external-identifier member of a kind-30395 Trusted List:
 * `["i", <i-tag>, <url-hint>, <score>]`.
 *
 * Index 2 is NIP-73's URL hint rather than a relay hint, so these members
 * carry no relay information for the hint indexer. Index 3 is the same 0..100
 * score the rest of the family carries; see [MemberTagFields.SCORE_RANGE].
 */
@Immutable
data class ExternalIdMemberTag(
    val externalId: String,
    val hint: String? = null,
    override val score: Int? = null,
) : TrustedListMemberTag {
    override val memberValue: String get() = externalId

    fun toTagArray() = assemble(externalId, hint, score)

    companion object {
        const val TAG_NAME = "i"

        fun isTag(tag: Tag) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        fun isTagged(
            tag: Tag,
            externalId: String,
        ) = tag.has(1) && tag[0] == TAG_NAME && tag[1] == externalId

        fun parse(tag: Tag): ExternalIdMemberTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }

            return ExternalIdMemberTag(tag[1], MemberTagFields.hint(tag), MemberTagFields.score(tag))
        }

        fun parseId(tag: Tag): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return tag[1]
        }

        fun assemble(
            externalId: String,
            hint: String?,
            score: Int?,
        ) = arrayOfNotNull(TAG_NAME, externalId, hint, MemberTagFields.encodeScore(score))

        fun assemble(member: ExternalIdMemberTag) = assemble(member.externalId, member.hint, member.score)

        fun assemble(members: List<ExternalIdMemberTag>) = members.map { assemble(it) }

        fun assemble(
            externalId: ExternalId,
            score: Int? = null,
        ) = assemble(externalId.toScope(), externalId.hint(), score)
    }
}
