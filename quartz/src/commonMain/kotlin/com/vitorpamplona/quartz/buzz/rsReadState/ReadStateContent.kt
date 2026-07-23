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
package com.vitorpamplona.quartz.buzz.rsReadState

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The decrypted NIP-RS (Buzz cross-device read-state, `docs/nips/NIP-RS.md`) blob: a per-client
 * map of context id -> read-frontier unix timestamp (seconds).
 *
 * Field names are exact wire names ([clientId] is `client_id`). Per NIP-RS a client MUST ignore
 * blobs with an unknown [v]; [contexts] values are "all messages in this context at or before
 * this time are read." This is the plaintext that gets NIP-44 self-encrypted into an
 * [com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent] `content` — see [ReadState].
 */
@Serializable
data class ReadStateContent(
    val v: Int = CURRENT_VERSION,
    @SerialName("client_id") val clientId: String,
    val contexts: Map<String, Long> = emptyMap(),
) {
    fun encodeToJson(): String = JSON.encodeToString(this)

    /** NIP-RS validity: the schema version is the one this client understands and `client_id` is 1–64 chars. */
    fun isSupported(): Boolean = v == CURRENT_VERSION && clientId.length in 1..64

    companion object {
        const val CURRENT_VERSION = 1

        val JSON =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            }

        fun decodeFromJson(json: String): ReadStateContent = JSON.decodeFromString(json)
    }
}
