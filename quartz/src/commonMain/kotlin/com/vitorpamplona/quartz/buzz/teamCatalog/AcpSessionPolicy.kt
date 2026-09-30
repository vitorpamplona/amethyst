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
package com.vitorpamplona.quartz.buzz.teamCatalog

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

/**
 * Whether one ACP conversation is shared by a whole channel ([CHANNEL], the default) or kept
 * per thread ([THREAD]). Wire values are `"channel"` / `"thread"`; like upstream's lenient
 * `Deserialize`, anything other than the string `"thread"` (including `null`, a number or an
 * unknown string) reads as [CHANNEL]. Ground truth: `AcpSessionPolicy` in Buzz's
 * `desktop/src-tauri/src/managed_agents/session_policy.rs`.
 */
@Serializable(with = AcpSessionPolicySerializer::class)
enum class AcpSessionPolicy(
    val code: String,
) {
    CHANNEL("channel"),
    THREAD("thread"),
}

object AcpSessionPolicySerializer : KSerializer<AcpSessionPolicy> {
    override val descriptor = PrimitiveSerialDescriptor("buzz.AcpSessionPolicy", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: AcpSessionPolicy,
    ) = encoder.encodeString(value.code)

    override fun deserialize(decoder: Decoder): AcpSessionPolicy {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement()
        val value =
            if (element == null) {
                decoder.decodeString()
            } else {
                (element as? JsonPrimitive)?.takeIf { it.isString }?.content
            }
        return if (value == AcpSessionPolicy.THREAD.code) AcpSessionPolicy.THREAD else AcpSessionPolicy.CHANNEL
    }
}
