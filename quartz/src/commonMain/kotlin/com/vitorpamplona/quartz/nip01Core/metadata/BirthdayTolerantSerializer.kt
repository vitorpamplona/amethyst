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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject

/**
 * Tolerant serializer for the kind-0 `birthday` field.
 *
 * NIP-24 defines `birthday` as an object `{ "year", "month", "day" }` (each field
 * optional). Some clients (e.g. Ditto / divine.video) instead write a string such
 * as `"10-24"`, which is not spec-compliant. With the default serializer that type
 * mismatch throws, and because [MetadataEvent.contactMetaData] turns any parse
 * exception into `null`, a single malformed `birthday` would discard the **entire**
 * profile (name, picture, about…).
 *
 * This serializer parses the spec object form and treats anything else as absent
 * (`null`) rather than failing, so one non-conformant field can no longer break
 * profile rendering. The string form is intentionally not "recovered": the spec
 * has no string format, and a bare `"10-24"` is ambiguous (MM-DD vs DD-MM).
 *
 * Modelled on [com.vitorpamplona.quartz.nip11RelayInfo.FlexibleIntListSerializer].
 */
object BirthdayTolerantSerializer : KSerializer<Birthday?> {
    private val delegate = Birthday.serializer()

    // Nullable serializer ⇒ nullable descriptor, so the framework's metadata stays
    // honest even on code paths that consult it (e.g. coerceInputValues).
    override val descriptor: SerialDescriptor = delegate.descriptor.nullable

    override fun deserialize(decoder: Decoder): Birthday? {
        require(decoder is JsonDecoder) { "This serializer can only be used with Json format" }

        val element = decoder.decodeJsonElement()
        if (element !is JsonObject) {
            // Non-spec birthday (e.g. Ditto's "10-24" string). Ignore it rather than
            // failing the whole profile parse. Log the JSON kind only, not the raw
            // (untrusted, network-sourced) value.
            Log.w("BirthdayTolerantSerializer") { "Ignoring non-object birthday (${element::class.simpleName})" }
            return null
        }

        return try {
            decoder.json.decodeFromJsonElement(delegate, element)
        } catch (e: Exception) {
            Log.w("BirthdayTolerantSerializer") { "Ignoring malformed birthday object: ${e.message}" }
            null
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: Birthday?,
    ) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            delegate.serialize(encoder, value)
        }
    }
}
