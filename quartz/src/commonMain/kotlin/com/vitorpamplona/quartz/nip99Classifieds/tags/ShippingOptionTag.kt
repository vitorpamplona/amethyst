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
package com.vitorpamplona.quartz.nip99Classifieds.tags

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

/**
 * Open Markets `["shipping_option", <30406|30405>:<pubkey>:<d>, <extra-cost>]` on a product or collection.
 *
 * The address is a kind-30406 shipping option or a kind-30405 collection whose options apply.
 * [extraCost] is added to that option's own price, in the product's currency.
 *
 * Shopstr writes the same tag name as `[name, cost, currency]`, with no address; [parse] refuses
 * those so a caller never reads a cost as an address.
 */
@Stable
data class ShippingOptionTag(
    val address: Address,
    val extraCost: String?,
) {
    fun toTagArray() = assemble(address, extraCost)

    companion object {
        const val TAG_NAME = "shipping_option"

        fun isTag(tag: Array<String>) = parse(tag) != null

        fun parse(tag: Array<String>): ShippingOptionTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            val address = Address.parse(tag[1]) ?: return null
            ensure(address.kind == SHIPPING_OPTION_KIND || address.kind == COLLECTION_KIND) { return null }
            return ShippingOptionTag(address, tag.getOrNull(2)?.ifEmpty { null })
        }

        fun assemble(
            address: Address,
            extraCost: String?,
        ) = arrayOfNotNull(TAG_NAME, address.toValue(), extraCost)

        // Literals rather than the classes' KINDs: those classes import this tag.
        private const val SHIPPING_OPTION_KIND = 30406
        private const val COLLECTION_KIND = 30405
    }
}
