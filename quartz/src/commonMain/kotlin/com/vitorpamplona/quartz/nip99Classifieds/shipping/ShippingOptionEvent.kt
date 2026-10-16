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
import com.vitorpamplona.quartz.nip99Classifieds.shipping.tags.CountryTag
import com.vitorpamplona.quartz.nip99Classifieds.shipping.tags.ServiceTag
import com.vitorpamplona.quartz.nip99Classifieds.shipping.tags.ShippingService
import com.vitorpamplona.quartz.nip99Classifieds.tags.LocationTag
import com.vitorpamplona.quartz.nip99Classifieds.tags.PriceTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * An Open Markets shipping option (kind 30406): a `title`, a base `price`, the `country` codes it
 * serves and a `service`, with optional `carrier`, `region`, `duration`, pickup `location`/`g`, and
 * weight, size and per-unit pricing constraints. Products and collections point at it with
 * `shipping_option`.
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

    fun countries() = tags.countries()

    fun regions() = tags.regions()

    /** The raw service; [serviceType] is it as a known [ShippingService]. */
    fun service() = tags.service()

    fun serviceType() = tags.serviceType()

    fun isPickup() = serviceType() == ShippingService.PICKUP

    fun carrier() = tags.carrier()

    fun duration() = tags.duration()

    fun location() = tags.firstNotNullOfOrNull(LocationTag::parse)

    fun geohash() = tags.firstNotNullOfOrNull(GeoHashTag::parse)

    /**
     * Whether this option serves [countryCode] (ISO 3166-1 alpha-2, compared case-insensitively).
     * An option that names no country restricts none: the spec requires `country`, but worldwide
     * and digital-delivery options in the wild leave it out.
     */
    fun shipsTo(countryCode: String): Boolean {
        val countries = countries()
        return countries.isEmpty() || countries.any { it.equals(countryCode, ignoreCase = true) }
    }

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
                    (it[0] == TitleTag.TAG_NAME || it[0] == ServiceTag.TAG_NAME || it[0] == CountryTag.TAG_NAME)
            }

        fun build(
            title: String,
            price: PriceTag,
            countries: List<String>,
            service: ShippingService,
            description: String = "",
            dTag: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ShippingOptionEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, description, createdAt) {
            dTag(dTag)
            title(title)
            price(price)
            countries(countries)
            service(service)
            initializer()
        }
    }
}
