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
package com.vitorpamplona.quartz.buzz.huddles

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The `content` JSON of a relay-synthesized huddle-liveness snapshot (`kind:48104`):
 * `{"ephemeral_channel_id": <session uuid>, "generation": <string>}`.
 *
 * [generation] is a **string** on the wire (the relay `to_string()`s it): the mesh lease
 * generation (a monotonic decimal integer) when the relay runs a mesh, otherwise an opaque
 * per-process epoch. Compare with [compareGenerations], never numerically on a `Long`.
 * Ground truth: `handle_huddle_liveness_req` in Buzz's `buzz-relay/src/handlers/req.rs`;
 * reader in `desktop/src/features/huddle/lib/huddlePresence.ts`.
 */
@Serializable
data class HuddleLivenessContent(
    @SerialName("ephemeral_channel_id") val ephemeralChannelId: String,
    val generation: String,
) {
    fun encodeToJson(): String = JSON.encodeToString(this)

    companion object {
        val JSON =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            }

        fun decodeFromJson(json: String): HuddleLivenessContent = JSON.decodeFromString(json)

        fun decodeFromJsonOrNull(json: String): HuddleLivenessContent? =
            try {
                decodeFromJson(json)
            } catch (_: Exception) {
                null
            }

        /**
         * Orders two liveness generations the way Buzz's desktop does
         * (`compareHuddleGenerations`): only when both are plain decimal integers (mesh
         * generations, which are monotonic) is there an order, returned as -1/0/1 for
         * [candidate] below/equal/above [current]. Anything else is an opaque epoch and yields
         * null — a differing opaque epoch means "take a fresh snapshot", not "newer".
         */
        fun compareGenerations(
            candidate: String,
            current: String,
        ): Int? {
            if (!candidate.isDecimal() || !current.isDecimal()) return null
            val a = candidate.trimStart('0')
            val b = current.trimStart('0')
            if (a.length != b.length) return if (a.length < b.length) -1 else 1
            val cmp = a.compareTo(b)
            return if (cmp < 0) {
                -1
            } else if (cmp > 0) {
                1
            } else {
                0
            }
        }

        private fun String.isDecimal() = isNotEmpty() && all { it in '0'..'9' }
    }
}
