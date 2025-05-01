/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.nip99Classifieds

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.containsAllTagNamesWithValues
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTags.dTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip23LongContent.tags.ImageTag
import com.vitorpamplona.quartz.nip23LongContent.tags.PublishedAtTag
import com.vitorpamplona.quartz.nip23LongContent.tags.SummaryTag
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.nip99Classifieds.tags.ConditionTag
import com.vitorpamplona.quartz.nip99Classifieds.tags.LocationTag
import com.vitorpamplona.quartz.nip99Classifieds.tags.PriceTag
import com.vitorpamplona.quartz.nip99Classifieds.tags.StatusTag
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.UUID

@Immutable
class ClassifiedsEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    fun image() = tags.firstNotNullOfOrNull(ImageTag::parse)

    fun condition() = tags.firstNotNullOfOrNull(ConditionTag::parse)

    fun conditionValid() = tags.firstNotNullOfOrNull(ConditionTag::parseCondition)

    fun images() = tags.mapNotNull(ImageTag::parse)

    fun status() = tags.firstNotNullOfOrNull(StatusTag::parse)

    fun summary() = tags.firstNotNullOfOrNull(SummaryTag::parse)

    fun price() = tags.firstNotNullOfOrNull(PriceTag::parse)

    fun location() = tags.firstNotNullOfOrNull(LocationTag::parse)

    fun publishedAt() = tags.firstNotNullOfOrNull(PublishedAtTag::parse)

    fun categories() = tags.hashtags()

    fun isWellFormed() = tags.containsAllTagNamesWithValues(REQUIRED_FIELDS)

    fun imageMetas(): List<ProductImageMeta> {
        val images = images()
        val imetas = imetas()

        val imetaSet = imetas.associate { it.url to it }

        return images.map {
            val imeta = imetaSet.get(it)
            if (imeta != null) {
                ProductImageMeta.parse(imeta)
            } else {
                ProductImageMeta(it)
            }
        }
    }

    companion object {
        const val KIND = 30402
        const val ALT_DESCRIPTION = "Classifieds listing"

        val REQUIRED_FIELDS = setOf(TitleTag.TAG_NAME, PriceTag.TAG_NAME, ImageTag.TAG_NAME)

        fun build(
            title: String,
            price: PriceTag,
            description: String,
            location: String? = null,
            condition: ConditionTag.CONDITION? = null,
            images: List<String>? = null,
            status: StatusTag.STATUS = StatusTag.STATUS.ACTIVE,
            dTag: String = UUID.randomUUID().toString(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ClassifiedsEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, description, createdAt) {
            dTag(dTag)
            title(title)
            price(price)
            status(status)

            condition?.let { condition(it) }
            location?.let { location(it) }
            images?.let { images(images) }

            alt(ALT_DESCRIPTION)
            initializer()
        }
    }
}
