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
package com.vitorpamplona.quartz.experimental.trustedLists

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.trustedLists.tags.TrustedListMemberTag
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import kotlinx.serialization.Serializable

/**
 * The optional JSON echo of the membership carried in `content`, with the
 * values the publisher computed for each member.
 *
 * [partial] mirrors the `truncated` tag: it is only present (with [total])
 * when the list is not exhaustive.
 */
@Immutable
@Serializable
data class TrustedListContent(
    val members: List<TrustedListContentMember> = emptyList(),
    val partial: Boolean? = null,
    val total: Int? = null,
) {
    fun toContent() = assemble(this)

    companion object {
        fun parse(content: String): TrustedListContent? {
            if (content.isBlank()) return null
            return runCatching { JsonMapper.fromJson<TrustedListContent>(content) }.getOrNull()
        }

        fun assemble(content: TrustedListContent) = JsonMapper.toJson(content)
    }
}

/**
 * One member of the content echo. The member value is carried under the key
 * that matches the list's member type -- `pubkey` on 30392, `id` on 30393,
 * `address` on 30394 and `i` on 30395 -- so exactly one of them is set.
 * Use [memberValue] to read it without branching on the kind.
 */
@Immutable
@Serializable
data class TrustedListContentMember(
    val pubkey: String? = null,
    val id: String? = null,
    val address: String? = null,
    val i: String? = null,
    val endorsements: Int? = null,
    val disputes: Int? = null,
    val score: Int? = null,
) {
    /**
     * Matches [TrustedListMemberTag.memberValue] on the tag side. Computed, so
     * it never round-trips into the JSON as a key of its own.
     */
    val memberValue: String? get() = pubkey ?: id ?: address ?: i
}
