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
package com.vitorpamplona.quartz.nip15Marketplace.auction

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastFirstNotNullOfOrNull
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
import com.vitorpamplona.quartz.nip01Core.core.firstTagValueFor
import com.vitorpamplona.quartz.nip15Marketplace.Nip15Json
import com.vitorpamplona.quartz.nip15Marketplace.numberOrNull
import com.vitorpamplona.quartz.nip15Marketplace.objectList
import com.vitorpamplona.quartz.nip15Marketplace.pairList
import com.vitorpamplona.quartz.nip15Marketplace.product.ProductShippingSurcharge
import com.vitorpamplona.quartz.nip15Marketplace.product.imageUrls
import com.vitorpamplona.quartz.nip15Marketplace.textOrNull
import com.vitorpamplona.quartz.nip15Marketplace.wholeNumberOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A NIP-15 auction (kind 30020): a product sold to the highest bid, from [startDate] for
 * [duration] seconds (plus whatever the merchant's bid confirmations extend it by).
 *
 * The spec puts everything in a JSON body, and gives an auction no currency of its own (it is
 * the stall's). Relays also hold a tag form that auction apps publish instead of, or next to,
 * the body: `title`, `summary`, `image`, `starting_bid` / `start_price`, `currency`,
 * `start_date` / `start_time`, `duration` / `end_time`, with the description as plain content.
 * [read] takes the body first and fills each missing field from those tags.
 */
@Immutable
@Serializable
data class AuctionData(
    val id: String,
    @SerialName("stall_id")
    val stallId: String? = null,
    val name: String? = null,
    val description: String? = null,
    val images: List<String> = emptyList(),
    val currency: String? = null,
    @SerialName("starting_bid")
    val startingBid: Double? = null,
    @SerialName("start_date")
    val startDate: Long? = null,
    val duration: Long? = null,
    val specs: List<List<String>> = emptyList(),
    val shipping: List<ProductShippingSurcharge> = emptyList(),
) {
    /** When the auction closes before any extension: `start_date + duration`; null if either is unknown. */
    fun endsAt(extensionSeconds: Long = 0): Long? {
        val start = startDate ?: return null
        val length = duration ?: return null
        return start + length + extensionSeconds
    }

    companion object {
        /**
         * The auction in [content] and [tags], or null when neither the body (a JSON object) nor
         * the tags (a `title` or `name`) describe one. Never throws.
         */
        fun read(
            content: String,
            tags: TagArray,
            fallbackId: String = "",
        ): AuctionData? {
            val json = Nip15Json.parseObject(content)
            val tagName = tags.tagText("title", "name")
            if (json == null && tagName == null) return null

            val tagStart = tags.tagNumber("start_date", "start_time")?.toLong()
            val start = json?.wholeNumberOrNull("start_date") ?: tagStart
            val tagEnd = tags.tagNumber("end_time")?.toLong()
            val duration =
                json?.wholeNumberOrNull("duration")
                    ?: tags.tagNumber("duration")?.toLong()
                    ?: if (start != null && tagEnd != null && tagEnd > start) tagEnd - start else null

            return AuctionData(
                id = json?.textOrNull("id") ?: fallbackId,
                stallId = json?.textOrNull("stall_id") ?: tags.tagText("stall_id"),
                name = json?.textOrNull("name") ?: json?.textOrNull("title") ?: tagName,
                description =
                    json?.textOrNull("description")
                        ?: tags.tagText("summary")
                        ?: if (json == null) content.trim().ifEmpty { null } else null,
                images = json?.imageUrls()?.ifEmpty { null } ?: tags.imageTags(),
                currency = json?.textOrNull("currency") ?: tags.tagText("currency"),
                startingBid = json?.numberOrNull("starting_bid") ?: tags.tagNumber("starting_bid", "start_price"),
                startDate = start,
                duration = duration,
                specs = json?.pairList("specs") ?: emptyList(),
                shipping = json?.objectList("shipping")?.map(ProductShippingSurcharge::from) ?: emptyList(),
            )
        }

        private fun TagArray.tagText(vararg names: String) = firstTagValueFor(*names)?.trim()?.ifEmpty { null }

        private fun TagArray.tagNumber(vararg names: String): Double? =
            fastFirstNotNullOfOrNull { tag ->
                if (tag.size > 1 && tag[0] in names) tag[1].trim().toDoubleOrNull()?.takeIf { it.isFinite() } else null
            }

        private fun TagArray.imageTags(): List<String> {
            val urls = ArrayList<String>(2)
            fastForEach { tag ->
                if (tag.size > 1 && tag[0] == "image") {
                    val url = tag[1].trim()
                    if (url.startsWith("https://") || url.startsWith("http://")) urls.add(url)
                }
            }
            return urls
        }
    }
}
