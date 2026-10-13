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
package com.vitorpamplona.quartz.nip15Marketplace.product

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip15Marketplace.Nip15Json
import com.vitorpamplona.quartz.nip15Marketplace.numberOrNull
import com.vitorpamplona.quartz.nip15Marketplace.objectList
import com.vitorpamplona.quartz.nip15Marketplace.pairList
import com.vitorpamplona.quartz.nip15Marketplace.textList
import com.vitorpamplona.quartz.nip15Marketplace.textOrNull
import com.vitorpamplona.quartz.nip15Marketplace.wholeNumberOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** A product's extra shipping [cost] for the stall's shipping zone [id]. */
@Immutable
@Serializable
data class ProductShippingSurcharge(
    val id: String? = null,
    val cost: Double? = null,
) {
    companion object {
        fun from(json: JsonObject) = ProductShippingSurcharge(json.textOrNull("id"), json.numberOrNull("cost"))
    }
}

/**
 * The JSON body of a NIP-15 product (kind 30018). Every field but [id] may be missing on the
 * wire; [parse] reads each one leniently. [id] falls back to the event's `d` tag, which the spec
 * requires to equal it.
 *
 * [quantity] null means unlimited availability (digital goods, services); 0 is sold out.
 * [specs] are `[key, value]` pairs.
 */
@Immutable
@Serializable
data class ProductData(
    val id: String,
    @SerialName("stall_id")
    val stallId: String? = null,
    val name: String? = null,
    val description: String? = null,
    val images: List<String> = emptyList(),
    val currency: String? = null,
    val price: Double? = null,
    val quantity: Long? = null,
    val specs: List<List<String>> = emptyList(),
    val shipping: List<ProductShippingSurcharge> = emptyList(),
) {
    fun isSoldOut() = quantity != null && quantity <= 0

    companion object {
        /** The body of [content], or null when it is not a JSON object. Never throws. */
        fun parse(
            content: String,
            fallbackId: String = "",
        ): ProductData? {
            val json = Nip15Json.parseObject(content) ?: return null
            return ProductData(
                id = json.textOrNull("id") ?: fallbackId,
                stallId = json.textOrNull("stall_id"),
                name = json.textOrNull("name"),
                description = json.textOrNull("description"),
                images = json.imageUrls(),
                currency = json.textOrNull("currency"),
                price = json.numberOrNull("price"),
                quantity = json.wholeNumberOrNull("quantity"),
                specs = json.pairList("specs"),
                shipping = json.objectList("shipping").map(ProductShippingSurcharge::from),
            )
        }
    }
}

/** `images`, or a lone `image` some merchants write instead; web URLs only. */
internal fun JsonObject.imageUrls(): List<String> =
    textList("images")
        .ifEmpty { listOfNotNull(textOrNull("image")) }
        .filter { it.startsWith("https://") || it.startsWith("http://") }
