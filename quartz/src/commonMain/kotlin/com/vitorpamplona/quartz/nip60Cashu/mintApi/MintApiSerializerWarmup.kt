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
package com.vitorpamplona.quartz.nip60Cashu.mintApi

import kotlinx.serialization.json.Json

/**
 * Pre-warms kotlinx.serialization for the hot mint-API DTOs.
 *
 * # Why
 *
 * NUT-09 restore returns 300–400 [BlindSignatureDto]s + [BlindedMessageDto]s
 * per HTTP round-trip, each with nested [DleqProofDto]s. Decoding that
 * response allocates ~1.5k data-class instances and ~5k Strings in
 * tight succession through kotlinx.serialization's generated
 * `serializer()` deserialize methods.
 *
 * On Android 15+ the ART JIT optimizer crashes (SIGSEGV at 0x48 in
 * Jit thread pool) when it tries to escape-analyze those generated
 * decode bodies. We've already addressed the in-Kotlin BDHKE
 * allocation density via [com.vitorpamplona.quartz.nip60Cashu.bdhke.BdhkeScratchpad],
 * but the serializers are also-ran hot methods we don't own.
 *
 * Warming them at init forces the JIT compile to happen under low
 * pressure (background coroutine, no user waiting) instead of mid-
 * restore where the synchronous compile pause is visible AND the
 * crash blast-radius is "wallet unusable".
 *
 * # How
 *
 * Decode a synthetic [RestoreResponseDto] with [WARMUP_ELEMENT_COUNT]
 * entries. This exercises the same code paths the real response will
 * — the same generated `BlindSignatureDto.serializer().deserialize(...)`,
 * the same nested `DleqProofDto` decode, the same List<> accumulator.
 * If ART's optimizer is going to crash on this shape, it crashes now
 * (visible at app start) instead of mid-recovery.
 */
object MintApiSerializerWarmup {
    private const val WARMUP_ELEMENT_COUNT = 32

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            explicitNulls = false
        }

    fun warmup() {
        val payload = buildSyntheticRestorePayload(WARMUP_ELEMENT_COUNT)
        // Decode + re-encode. The encode path is also hot (every mint
        // request body serializes a List<BlindedMessageDto>), so warm
        // both directions.
        val decoded = json.decodeFromString(RestoreResponseDto.serializer(), payload)
        json.encodeToString(RestoreResponseDto.serializer(), decoded)

        // Also warm the SwapResponseDto / MintBolt11ResponseDto decoders
        // since they go through the same BlindSignatureDto inner code.
        val swap = buildSyntheticSwapPayload(WARMUP_ELEMENT_COUNT)
        json.decodeFromString(SwapResponseDto.serializer(), swap)
    }

    private fun buildSyntheticRestorePayload(count: Int): String {
        val outputs =
            (0 until count).joinToString(",") {
                """{"amount":1,"id":"00aabbccdd001122","B_":"02${"a".repeat(64)}"}"""
            }
        val sigs =
            (0 until count).joinToString(",") {
                """{"amount":1,"id":"00aabbccdd001122","C_":"02${"b".repeat(64)}","dleq":{"e":"${"c".repeat(64)}","s":"${"d".repeat(64)}"}}"""
            }
        return """{"outputs":[$outputs],"signatures":[$sigs]}"""
    }

    private fun buildSyntheticSwapPayload(count: Int): String {
        val sigs =
            (0 until count).joinToString(",") {
                """{"amount":1,"id":"00aabbccdd001122","C_":"02${"b".repeat(64)}","dleq":{"e":"${"c".repeat(64)}","s":"${"d".repeat(64)}"}}"""
            }
        return """{"signatures":[$sigs]}"""
    }
}
