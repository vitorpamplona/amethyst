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
package com.vitorpamplona.quartz.nip11RelayInfo

import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation.RelayInformationRetentionData
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * A NIP-11 `retention` entry. Its `kinds` mixes plain kinds and `[from, to]` ranges —
 * the spec's own example is `[0, 1, [5, 7], [40, 49]]` — which a generated `List<Int>`
 * serializer cannot read, and one failed field fails the whole relay document. Plain kinds
 * land in [RelayInformationRetentionData.kinds] and ranges in
 * [RelayInformationRetentionData.kindRanges]; an entry that is neither is skipped, as
 * [FlexibleIntListSerializer] does for `supported_nips`.
 */
object RetentionDataSerializer : KSerializer<RelayInformationRetentionData> {
    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun deserialize(decoder: Decoder): RelayInformationRetentionData {
        require(decoder is JsonDecoder) { "This serializer can only be used with Json format" }

        val obj = decoder.decodeJsonElement() as? JsonObject ?: return RelayInformationRetentionData()

        val array = obj["kinds"] as? JsonArray
        val kinds = ArrayList<Int>()
        val ranges = ArrayList<IntRange>()

        array?.forEach { element ->
            when (element) {
                is JsonPrimitive -> {
                    element.intOrNull?.let { kinds.add(it) }
                }

                is JsonArray -> {
                    val from = element.getOrNull(0).asIntOrNull()
                    val to = element.getOrNull(1).asIntOrNull()
                    if (element.size == 2 && from != null && to != null) ranges.add(from..to)
                }

                else -> {}
            }
        }

        return RelayInformationRetentionData(
            // An absent `kinds` stays null; a present one keeps its plain kinds even when empty.
            kinds = if (array != null) kinds else null,
            time = obj["time"].asIntOrNull(),
            count = obj["count"].asIntOrNull(),
            kindRanges = ranges.ifEmpty { null },
        )
    }

    override fun serialize(
        encoder: Encoder,
        value: RelayInformationRetentionData,
    ) {
        require(encoder is JsonEncoder) { "This serializer can only be used with Json format" }

        val fields = LinkedHashMap<String, JsonElement>()
        if (value.kinds != null || value.kindRanges != null) {
            val kinds = ArrayList<JsonElement>()
            value.kinds?.forEach { kinds.add(JsonPrimitive(it)) }
            value.kindRanges?.forEach { kinds.add(JsonArray(listOf(JsonPrimitive(it.first), JsonPrimitive(it.last)))) }
            fields["kinds"] = JsonArray(kinds)
        }
        value.time?.let { fields["time"] = JsonPrimitive(it) }
        value.count?.let { fields["count"] = JsonPrimitive(it) }

        encoder.encodeJsonElement(JsonObject(fields))
    }

    private fun JsonElement?.asIntOrNull(): Int? = (this as? JsonPrimitive)?.intOrNull
}
