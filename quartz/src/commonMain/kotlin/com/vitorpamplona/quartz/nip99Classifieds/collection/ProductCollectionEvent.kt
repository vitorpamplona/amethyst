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
package com.vitorpamplona.quartz.nip99Classifieds.collection

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.fastAny
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHashTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip23LongContent.tags.ImageTag
import com.vitorpamplona.quartz.nip23LongContent.tags.SummaryTag
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip99Classifieds.shippingOptions
import com.vitorpamplona.quartz.nip99Classifieds.tags.LocationTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * An Open Markets product collection (kind 30405): a NIP-51-style list of kind-30402 products
 * (`a` tags) with a `title`, and optional `summary`, `image`, `location`, `g` and
 * `shipping_option` that its products may reference. Nothing cascades: a product inherits a
 * collection's shipping or location only by pointing at it.
 *
 * Shopstr publishes encrypted carts on the same number; [isProductCollection] tells them apart.
 */
@Immutable
class ProductCollectionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: TagArray,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressHintProvider,
    SearchableEvent {
    override fun indexableContent() = (listOfNotNull(title(), summary(), content, location()) + categories()).joinToString("\n")

    // The read path: the same fields indexableContent() joins, without building the string.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(title())) return
        if (!visitor.visit(summary())) return
        if (!visitor.visit(content)) return
        if (!visitor.visit(location())) return
        for (tag in tags) {
            val category = HashtagTag.parse(tag) ?: continue
            if (!visitor.visit(category)) return
        }
    }

    override fun addressHints(): List<AddressHint> = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds(): List<String> = tags.mapNotNull(ATag::parseAddressId)

    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    fun summary() = tags.firstNotNullOfOrNull(SummaryTag::parse)

    fun image() = tags.firstNotNullOfOrNull(ImageTag::parse)

    fun location() = tags.firstNotNullOfOrNull(LocationTag::parse)

    fun geohash() = tags.firstNotNullOfOrNull(GeoHashTag::parse)

    fun categories() = tags.hashtags()

    /** The kind-30402 products in the collection, in tag order. */
    fun products(): List<Address> = tags.products()

    fun shippingOptions() = tags.shippingOptions()

    companion object {
        const val KIND = 30405

        /**
         * A collection names itself with `title` or lists a product with an `a`. Shopstr's carts on
         * this kind carry no tags at all, their `d` and items sealed in the encrypted content, and
         * parse as [UnrecognizedKind30405Event].
         */
        fun isProductCollection(tags: TagArray): Boolean =
            tags.fastAny {
                it.size > 1 &&
                    it[1].isNotBlank() &&
                    (it[0] == TitleTag.TAG_NAME || (it[0] == ATag.TAG_NAME && it[1].startsWith(PRODUCT_ADDRESS)))
            }

        private const val PRODUCT_ADDRESS = "30402:"

        fun build(
            title: String,
            products: List<Address>,
            description: String = "",
            dTag: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ProductCollectionEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, description, createdAt) {
            dTag(dTag)
            title(title)
            products(products)
            initializer()
        }
    }
}
