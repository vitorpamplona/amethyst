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
package com.vitorpamplona.quartz.nip99Classifieds.shipping

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.fastAny
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHashTag
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip99Classifieds.shipping.tags.CodeListTag
import com.vitorpamplona.quartz.nip99Classifieds.shipping.tags.DurationTag
import com.vitorpamplona.quartz.nip99Classifieds.shipping.tags.ShippingTextTag
import com.vitorpamplona.quartz.nip99Classifieds.tags.LocationTag
import com.vitorpamplona.quartz.nip99Classifieds.tags.PriceTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * An Open Markets shipping option (kind 30406): a `title`, a base `price`, the `country` codes it
 * serves and a `service` (standard, express, overnight, pickup), with optional `carrier`,
 * `region`, `duration`, pickup `location`/`g`, and weight, size and per-unit pricing constraints.
 * Products and collections point at it with `shipping_option`.
 *
 * Shopstr stores buyer escrow records on the same number; [isShippingOption] tells them apart.
 */
@Immutable
class ShippingOptionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: TagArray,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    SearchableEvent {
    // A pickup point is found by where it is, a courier by its name.
    override fun indexableContent() = listOfNotNull(title(), carrier(), content, location()).joinToString("\n")

    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(title())) return
        if (!visitor.visit(carrier())) return
        if (!visitor.visit(content)) return
        visitor.visit(location())
    }

    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    /** The base cost; the currency is in [PriceTag.currency]. */
    fun price() = tags.firstNotNullOfOrNull(PriceTag::parse)

    fun countries(): List<String> = tags.firstNotNullOfOrNull { CodeListTag.parse(it, CodeListTag.COUNTRY) } ?: emptyList()

    fun regions(): List<String> = tags.firstNotNullOfOrNull { CodeListTag.parse(it, CodeListTag.REGION) } ?: emptyList()

    fun service() = tags.firstNotNullOfOrNull { ShippingTextTag.parse(it, ShippingTextTag.SERVICE) }

    fun isPickup() = service() == ShippingTextTag.PICKUP

    fun carrier() = tags.firstNotNullOfOrNull { ShippingTextTag.parse(it, ShippingTextTag.CARRIER) }

    fun duration() = tags.firstNotNullOfOrNull(DurationTag::parse)

    fun location() = tags.firstNotNullOfOrNull(LocationTag::parse)

    fun geohash() = tags.firstNotNullOfOrNull(GeoHashTag::parse)

    /** Whether this option serves [countryCode] (ISO 3166-1 alpha-2), compared case-insensitively. */
    fun shipsTo(countryCode: String) = countries().any { it.equals(countryCode, ignoreCase = true) }

    companion object {
        const val KIND = 30406

        /**
         * A shipping option carries a `title`, a `service` or a `country`. Shopstr's escrow records
         * on this kind carry none of them, and parse as [UnrecognizedKind30406Event].
         */
        fun isShippingOption(tags: TagArray): Boolean =
            tags.fastAny {
                it.size > 1 &&
                    it[1].isNotBlank() &&
                    (it[0] == TitleTag.TAG_NAME || it[0] == ShippingTextTag.SERVICE || it[0] == CodeListTag.COUNTRY)
            }

        fun build(
            title: String,
            price: PriceTag,
            countries: List<String>,
            service: String,
            description: String = "",
            dTag: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ShippingOptionEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, description, createdAt) {
            dTag(dTag)
            add(TitleTag.assemble(title))
            add(price.toTagArray())
            add(CodeListTag.assemble(CodeListTag.COUNTRY, countries))
            add(ShippingTextTag.assemble(ShippingTextTag.SERVICE, service))
            initializer()
        }
    }
}
