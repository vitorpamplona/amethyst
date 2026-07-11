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
package com.vitorpamplona.quartz.nip01Core.metadata

import com.vitorpamplona.quartz.utils.Log
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Tolerant serializer for kind-0 string fields.
 *
 * Some clients publish structurally wrong values for NIP-01/NIP-24 profile
 * fields — seen in the wild: `"nip05":{}`. With the default serializer that
 * type mismatch throws, and because [MetadataEvent.contactMetaData] turns any
 * parse exception into `null`, one malformed field would discard the **entire**
 * profile (name, picture, about…).
 *
 * This serializer accepts any JSON primitive (matching the pre-existing lenient
 * behavior, where a bare number or boolean decodes into a string field) and
 * treats objects, arrays, and JSON null as absent rather than failing.
 *
 * Same rationale as [BirthdayTolerantSerializer].
 */
object TolerantStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.vitorpamplona.quartz.nip01Core.metadata.TolerantString", PrimitiveKind.STRING).nullable

    override fun deserialize(decoder: Decoder): String? {
        require(decoder is JsonDecoder) { "This serializer can only be used with Json format" }

        val element = decoder.decodeJsonElement()
        return when {
            element is JsonNull -> null
            element is JsonPrimitive -> element.content
            else -> {
                // Log the JSON kind only, not the raw (untrusted, network-sourced) value.
                Log.w("TolerantStringSerializer") { "Ignoring non-primitive string field (${element::class.simpleName})" }
                null
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(
        encoder: Encoder,
        value: String?,
    ) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeString(value)
        }
    }
}

/**
 * Tolerant serializer for the kind-0 `bot` flag: accepts `true`/`false` and their
 * quoted string forms; anything else (objects, arrays, other strings, JSON null)
 * is treated as absent rather than failing the whole profile parse.
 */
object TolerantBooleanSerializer : KSerializer<Boolean?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.vitorpamplona.quartz.nip01Core.metadata.TolerantBoolean", PrimitiveKind.BOOLEAN).nullable

    override fun deserialize(decoder: Decoder): Boolean? {
        require(decoder is JsonDecoder) { "This serializer can only be used with Json format" }

        val element = decoder.decodeJsonElement()
        if (element is JsonPrimitive && element !is JsonNull) {
            val parsed = element.booleanOrNull
            if (parsed != null) return parsed
        }
        if (element !is JsonNull) {
            Log.w("TolerantBooleanSerializer") { "Ignoring non-boolean bot field (${element::class.simpleName})" }
        }
        return null
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(
        encoder: Encoder,
        value: Boolean?,
    ) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeBoolean(value)
        }
    }
}
