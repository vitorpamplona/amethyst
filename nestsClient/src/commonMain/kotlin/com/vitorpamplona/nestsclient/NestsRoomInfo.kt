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
package com.vitorpamplona.nestsclient

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Information returned by a nests audio-room backend when a client
 * authenticates against `<service>/api/v1/nests/<roomId>`.
 *
 * Field names mirror the nests reference server payload:
 * ```
 * { "endpoint": "...", "token": "...", "codec": "opus", "sample_rate": 48000 }
 * ```
 * All fields except [endpoint] are optional so the client can negotiate with
 * alternate nests implementations that omit codec/token metadata.
 */
@Serializable
data class NestsRoomInfo(
    val endpoint: String,
    val token: String? = null,
    val codec: String? = null,
    @SerialName("sample_rate") val sampleRate: Int? = null,
    val transport: String? = null,
    val extra: Map<String, JsonElement> = emptyMap(),
) {
    companion object {
        private val json =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }

        fun parse(body: String): NestsRoomInfo = json.decodeFromString(serializer(), body)
    }
}

/**
 * Build the URL a client should call to resolve [NestsRoomInfo] for a specific
 * room. [serviceBase] comes from the `service` tag of the NIP-53 kind 30312
 * event; [roomId] is the event's `d` tag.
 *
 * Example: `https://nostrnests.com/api/v1/nests` + `abc-123` →
 * `https://nostrnests.com/api/v1/nests/abc-123`.
 */
fun nestsRoomInfoUrl(
    serviceBase: String,
    roomId: String,
): String {
    val trimmed = serviceBase.trimEnd('/')
    val encoded = roomId.trim()
    return "$trimmed/$encoded"
}
