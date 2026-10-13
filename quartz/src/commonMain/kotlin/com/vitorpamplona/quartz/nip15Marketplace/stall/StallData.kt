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
import com.vitorpamplona.quartz.nip15Marketplace.Nip15Json
import com.vitorpamplona.quartz.nip15Marketplace.objectList
import com.vitorpamplona.quartz.nip15Marketplace.textOrNull
import kotlinx.serialization.Serializable

/**
 * The JSON body of a NIP-15 stall (kind 30017). Every field but [id] may be missing on the
 * wire; [parse] reads each one leniently. [id] falls back to the event's `d` tag, which the spec
 * requires to equal it.
 */
@Immutable
@Serializable
data class StallData(
    val id: String,
    val name: String? = null,
    val description: String? = null,
    val currency: String? = null,
    val shipping: List<ShippingZone> = emptyList(),
) {
    companion object {
        /** The body of [content], or null when it is not a JSON object. Never throws. */
        fun parse(
            content: String,
            fallbackId: String = "",
        ): StallData? {
            val json = Nip15Json.parseObject(content) ?: return null
            return StallData(
                id = json.textOrNull("id") ?: fallbackId,
                name = json.textOrNull("name"),
                description = json.textOrNull("description"),
                currency = json.textOrNull("currency"),
                shipping = json.objectList("shipping").map(ShippingZone::from),
            )
        }
    }
}
