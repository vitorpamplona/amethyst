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
package com.vitorpamplona.quartz.experimental.decentralizedLists.taggings

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.AddressableListItemEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.ParentListTag
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.serialization.Serializable

/**
 * The context a tag-element was created for, recorded as a pubkey-free `z` value alongside
 * its concept membership. The author's intent only: a reader must not require a hint for a tag
 * to work in a context, nor read its absence as "not applicable".
 */
enum class TagApplicabilityHint(
    val code: String,
) {
    PUBKEY("tag-for-nostr-pubkey"),
    EVENT("tag-for-nostr-event"),
    ;

    companion object {
        fun fromCode(code: String) = entries.firstOrNull { it.code == code }
    }
}

@Immutable
@Serializable
data class TagInfo(
    val slug: String,
    val name: String? = null,
    val description: String? = null,
)

/** The `content` of a tag-element: `{"tag": {"slug", "name", "description"}}`. */
@Immutable
@Serializable
data class TagElementContent(
    val tag: TagInfo,
) {
    fun toContent() = JsonMapper.toJson(this)

    companion object {
        fun parse(content: String): TagElementContent? {
            if (content.isBlank()) return null
            return runCatching { JsonMapper.fromJson<TagElementContent>(content) }.getOrNull()
        }
    }
}

/**
 * Tags & Taggings: a *tag* ("Podcaster is a tag") is a kind 39999 item joining the deployment's
 * `tag` concept, with `d` = its slug. It is addressed at `39999:<author>:<slug>`; tags by
 * different authors with the same slug are different tags.
 */
object TagElement {
    /**
     * @param tagConcept the deployment's `tag` concept address (`39998:<assistant>:tag`).
     *   Deployment-specific: never hardcode it.
     */
    fun build(
        tagConcept: String,
        slug: String,
        name: String,
        description: String? = null,
        hints: Set<TagApplicabilityHint> = emptySet(),
        createdAt: Long = TimeUtils.now(),
    ) = AddressableListItemEvent.build(
        parent = ParentListTag.classify(tagConcept),
        dTag = slug,
        createdAt = createdAt,
        content = TagElementContent(TagInfo(slug, name, description)).toContent(),
    ) {
        hints.forEach { applicabilityHint(it) }
    }

    fun content(event: AddressableListItemEvent) = TagElementContent.parse(event.content)
}
