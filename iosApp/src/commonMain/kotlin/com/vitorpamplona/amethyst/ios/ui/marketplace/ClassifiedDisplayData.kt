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
package com.vitorpamplona.amethyst.ios.ui.marketplace

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.ios.cache.IosLocalCache
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArrayOrNull
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent

/**
 * Display data for a classified listing (NIP-99, kind 30402).
 */
data class ClassifiedDisplayData(
    val id: String,
    val addressId: String,
    val pubKeyHex: String,
    val pubKeyDisplay: String,
    val profilePictureUrl: String?,
    val title: String,
    val description: String,
    val summary: String?,
    val price: String?,
    val currency: String?,
    val condition: String?,
    val location: String?,
    val imageUrl: String?,
    val imageUrls: List<String>,
    val status: String?,
    val categories: List<String>,
    val createdAt: Long,
)

/**
 * Format a price with optional currency for display.
 */
fun ClassifiedDisplayData.formattedPrice(): String? {
    if (price == null) return null
    return if (currency != null) {
        "$price $currency"
    } else {
        price
    }
}

/**
 * Extension to convert a Note containing a ClassifiedsEvent to display data.
 */
fun Note.toClassifiedDisplayData(cache: IosLocalCache? = null): ClassifiedDisplayData? {
    val event = this.event as? ClassifiedsEvent ?: return null
    val user = cache?.getUserIfExists(event.pubKey)

    val displayName =
        user?.toBestDisplayName()
            ?: try {
                event.pubKey.hexToByteArrayOrNull()?.toNpub() ?: event.pubKey.take(16) + "..."
            } catch (e: Exception) {
                event.pubKey.take(16) + "..."
            }

    val priceTag = event.price()

    return ClassifiedDisplayData(
        id = event.id,
        addressId = event.addressTag(),
        pubKeyHex = event.pubKey,
        pubKeyDisplay = displayName,
        profilePictureUrl = user?.profilePicture(),
        title = event.title() ?: "Untitled Listing",
        description = event.content,
        summary = event.summary(),
        price = priceTag?.amount,
        currency = priceTag?.currency,
        condition = event.condition(),
        location = event.location(),
        imageUrl = event.image(),
        imageUrls = event.images(),
        status = event.status(),
        categories = event.categories(),
        createdAt = event.createdAt,
    )
}
