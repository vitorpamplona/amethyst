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
package com.vitorpamplona.quartz.nip15Marketplace.marketplace

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip15Marketplace.Nip15Json
import com.vitorpamplona.quartz.nip15Marketplace.objectOrNull
import com.vitorpamplona.quartz.nip15Marketplace.textList
import com.vitorpamplona.quartz.nip15Marketplace.textOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** How a marketplace (kind 30019) asks to be drawn: logo, banner, theme name, dark mode. */
@Immutable
@Serializable
data class MarketplaceUi(
    val picture: String? = null,
    val banner: String? = null,
    val theme: String? = null,
    val darkMode: Boolean? = null,
) {
    companion object {
        fun from(json: JsonObject) =
            MarketplaceUi(
                picture = json.textOrNull("picture"),
                banner = json.textOrNull("banner"),
                theme = json.textOrNull("theme"),
                darkMode = (json["darkMode"] as? JsonPrimitive)?.booleanOrNull,
            )
    }
}

/**
 * The JSON body of a NIP-15 marketplace (kind 30019): a curated set of [merchants] with a name
 * and a look. Every field is optional on the wire; [parse] reads each one leniently.
 */
@Immutable
@Serializable
data class MarketplaceData(
    val name: String? = null,
    val about: String? = null,
    val ui: MarketplaceUi? = null,
    val merchants: List<String> = emptyList(),
) {
    companion object {
        /** The body of [content], or null when it is not a JSON object. Never throws. */
        fun parse(content: String): MarketplaceData? {
            val json = Nip15Json.parseObject(content) ?: return null
            return MarketplaceData(
                name = json.textOrNull("name"),
                about = json.textOrNull("about"),
                ui = json.objectOrNull("ui")?.let(MarketplaceUi::from),
                merchants = json.textList("merchants"),
            )
        }
    }
}
