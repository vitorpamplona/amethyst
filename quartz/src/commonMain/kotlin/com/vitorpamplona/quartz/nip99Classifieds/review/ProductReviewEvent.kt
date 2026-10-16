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
package com.vitorpamplona.quartz.nip99Classifieds.review

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nip99Classifieds.review.tags.ReviewRatingTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * An Open Markets product review (kind 31555). The `d` names the product as
 * `a:30402:<merchant-pubkey>:<product-d>`, so a reviewer holds one review per product and a newer
 * one replaces the older. A `["rating", <0..1>, "thumb"]` is required; more `rating` tags score
 * named criteria. The content is the review text.
 *
 * Shopstr also publishes this kind in its own shape (`d=<reviewer>:<product>`, 0–5 scores, no
 * thumb); those have no [product] and no [score] here.
 */
@Immutable
class ProductReviewEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: TagArray,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressHintProvider,
    PubKeyHintProvider,
    SearchableEvent {
    override fun indexableContent() = content

    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        visitor.visit(content)
    }

    // The product is named only inside `d`, which carries no relay hint.
    override fun addressHints(): List<AddressHint> = emptyList()

    // linked*() run on every relay copy; the parse of `d` they share is done once.
    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    private var productCache: Address? = null

    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    private var productParsed = false

    override fun linkedAddressIds(): List<String> = listOfNotNull(product()?.let { dTag().substring(PRODUCT_PREFIX.length) })

    override fun pubKeyHints(): List<PubKeyHint> = emptyList()

    override fun linkedPubKeys(): List<HexKey> = listOfNotNull(merchant())

    /** The reviewed kind-30402 product, read from `d`; null when `d` names anything else. */
    fun product(): Address? {
        if (productParsed) return productCache
        val d = dTag()
        val parsed =
            if (d.startsWith(PRODUCT_PREFIX)) {
                Address.parse(d.substring(PRODUCT_PREFIX.length))?.takeIf { it.kind == ClassifiedsEvent.KIND }
            } else {
                null
            }
        productCache = parsed
        productParsed = true
        return parsed
    }

    fun merchant(): HexKey? = product()?.pubKeyHex

    fun ratings(): List<ReviewRatingTag> = tags.ratings()

    /** The required primary score, 0 (negative) to 1 (positive). */
    fun thumb(): Double? = tags.thumb()

    /**
     * The spec's total: thumb × 0.5 + 0.5 × the mean of the category scores. With no categories
     * it is the thumb alone (Shopstr scores a thumb-only review as thumb × 0.5, which the spec's
     * formula does not say). Null without a thumb, which a valid review always carries.
     */
    fun score(): Double? {
        val all = ratings()
        val thumb = all.firstOrNull { it.isThumb() }?.score ?: return null
        val categories = all.filterNot { it.isThumb() }
        if (categories.isEmpty()) return thumb
        return thumb * 0.5 + 0.5 * categories.sumOf { it.score } / categories.size
    }

    companion object {
        const val KIND = 31555

        const val PRODUCT_PREFIX = "a:"

        fun dTagFor(product: Address) = PRODUCT_PREFIX + product.toValue()

        fun build(
            product: Address,
            thumb: Double,
            review: String = "",
            categories: Map<String, Double> = emptyMap(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ProductReviewEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, review, createdAt) {
            dTag(dTagFor(product))
            thumb(thumb)
            ratings(categories)
            initializer()
        }
    }
}
