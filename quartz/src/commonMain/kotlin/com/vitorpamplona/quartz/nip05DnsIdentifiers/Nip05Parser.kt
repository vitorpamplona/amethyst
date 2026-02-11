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
package com.vitorpamplona.quartz.nip05DnsIdentifiers

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.text
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class KeyInfoSet(
    val names: Map<String, String>,
    val relays: Map<String, List<String>>,
)

class Nip05Parser {
    fun toJson(keyInfo: KeyInfoSet) = Json.encodeToString(keyInfo)

    fun parse(json: String) = Json.decodeFromString<KeyInfoSet>(json)

    fun parseHexKey(
        nip05: Nip05Id,
        json: String,
    ): HexKey? =
        Json
            .parseToJsonElement(json)
            .jsonObject["names"]
            ?.jsonObject[nip05.name]
            ?.jsonPrimitive
            ?.content

    fun parseHexKeyAndRelays(
        nip05: Nip05Id,
        json: String,
    ): Nip05KeyInfo? {
        val rootElement = Json.parseToJsonElement(json)

        val hexKey =
            rootElement.jsonObject["names"]
                ?.jsonObject[nip05.name]
                ?.jsonPrimitive
                ?.content

        return if (!hexKey.isNullOrEmpty()) {
            val relays =
                rootElement.jsonObject["relays"]
                    ?.jsonObject[hexKey]
                    ?.jsonArray
                    ?.map {
                        it.jsonPrimitive.text
                    } ?: emptyList()

            Nip05KeyInfo(hexKey, relays)
        } else {
            null
        }
    }
}
