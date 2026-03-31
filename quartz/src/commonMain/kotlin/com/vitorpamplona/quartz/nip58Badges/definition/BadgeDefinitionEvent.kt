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
package com.vitorpamplona.quartz.nip58Badges.definition

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip58Badges.definition.tags.ImageTag
import com.vitorpamplona.quartz.nip58Badges.definition.tags.ThumbTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class BadgeDefinitionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun name() = tags.badgeName()

    fun image() = tags.badgeImageUrl()

    fun imageWithDimensions() = tags.badgeImage()

    fun description() = tags.badgeDescription()

    fun thumb() = tags.badgeThumbUrl()

    fun thumbs() = tags.badgeThumbs()

    companion object {
        const val KIND = 30009
        const val ALT_DESCRIPTION = "Badge definition"

        fun build(
            badgeId: String,
            name: String? = null,
            image: ImageTag? = null,
            description: String? = null,
            thumbs: List<ThumbTag> = emptyList(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<BadgeDefinitionEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            dTag(badgeId)
            alt(ALT_DESCRIPTION)
            name?.let { name(it) }
            image?.let { image(it.url, it.dimensions) }
            description?.let { description(it) }
            if (thumbs.isNotEmpty()) {
                thumbs(thumbs)
            }
            initializer()
        }

        fun build(
            badgeId: String,
            name: String? = null,
            imageUrl: String? = null,
            imageDimensions: DimensionTag? = null,
            description: String? = null,
            thumbs: List<ThumbTag> = emptyList(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<BadgeDefinitionEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            dTag(badgeId)
            alt(ALT_DESCRIPTION)
            name?.let { name(it) }
            imageUrl?.let { image(it, imageDimensions) }
            description?.let { description(it) }
            if (thumbs.isNotEmpty()) {
                thumbs(thumbs)
            }
            initializer()
        }
    }
}
