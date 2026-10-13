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
package com.vitorpamplona.quartz.nip15Marketplace.stall

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip15Marketplace.numberOrNull
import com.vitorpamplona.quartz.nip15Marketplace.textList
import com.vitorpamplona.quartz.nip15Marketplace.textOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A stall's shipping zone: a base [cost] (in the stall's currency) to ship to its [regions].
 * Some merchants write `countries` instead of `regions`; both are kept.
 */
@Immutable
@Serializable
data class ShippingZone(
    val id: String? = null,
    val name: String? = null,
    val cost: Double? = null,
    val regions: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
) {
    /** Where the zone ships, whichever of the two fields the merchant used. */
    fun destinations(): List<String> = regions.ifEmpty { countries }

    companion object {
        fun from(json: JsonObject) =
            ShippingZone(
                id = json.textOrNull("id"),
                name = json.textOrNull("name"),
                cost = json.numberOrNull("cost"),
                regions = json.textList("regions"),
                countries = json.textList("countries"),
            )
    }
}
