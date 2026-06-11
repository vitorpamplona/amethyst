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
package com.vitorpamplona.quartz.experimental.clink.kotlinSerialization

import com.vitorpamplona.quartz.experimental.clink.common.GfyDelta
import com.vitorpamplona.quartz.experimental.clink.common.SatRange
import com.vitorpamplona.quartz.experimental.clink.debits.DebitFrequency
import com.vitorpamplona.quartz.experimental.clink.debits.DebitRequest
import com.vitorpamplona.quartz.experimental.clink.debits.DebitResponse
import com.vitorpamplona.quartz.experimental.clink.manage.ManageOffer
import com.vitorpamplona.quartz.experimental.clink.manage.ManageRequest
import com.vitorpamplona.quartz.experimental.clink.manage.ManageResponse
import com.vitorpamplona.quartz.experimental.clink.manage.OfferData
import com.vitorpamplona.quartz.experimental.clink.manage.OfferFields
import com.vitorpamplona.quartz.experimental.clink.offers.OfferReceipt
import com.vitorpamplona.quartz.experimental.clink.offers.OfferRequest
import com.vitorpamplona.quartz.experimental.clink.offers.OfferResponse
import com.vitorpamplona.quartz.nip47WalletConnect.kotlinSerialization.toAnyMap
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Hand-written kotlinx serializers for the CLINK decrypted payload DTOs
 * (`OfferRequest`/`OfferResponse`/`OfferReceipt`, `DebitRequest`/`DebitResponse`,
 * `ManageRequest`/`ManageResponse`). They mirror what Jackson does reflectively on
 * JVM/Android — including coercing a lone `details` object into a one-element list
 * (Jackson's `ACCEPT_SINGLE_VALUE_AS_ARRAY`) — so native targets parse the same wire shapes.
 */

private fun anyToJsonElement(value: Any?): JsonElement =
    when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Map<*, *> -> buildJsonObject { value.forEach { (k, v) -> put(k.toString(), anyToJsonElement(v)) } }
        is Iterable<*> -> buildJsonArray { value.forEach { add(anyToJsonElement(it)) } }
        else -> JsonPrimitive(value.toString())
    }

private fun JsonObject.stringOrNull(key: String): String? = get(key)?.let { if (it is JsonNull) null else it.jsonPrimitive.content }

private fun JsonObject.longOrNull(key: String): Long? = get(key)?.let { if (it is JsonNull) null else it.jsonPrimitive.longOrNull }

private fun JsonObject.intOrNull(key: String): Int? = get(key)?.let { if (it is JsonNull) null else it.jsonPrimitive.intOrNull }

private fun JsonObject.objectOrNull(key: String): JsonObject? = get(key) as? JsonObject

private fun JsonObject.stringListOrNull(key: String): List<String>? = (get(key) as? JsonArray)?.map { it.jsonPrimitive.content }

private fun serializeSatRange(range: SatRange): JsonObject =
    buildJsonObject {
        range.min?.let { put("min", it) }
        range.max?.let { put("max", it) }
    }

private fun parseSatRange(obj: JsonObject): SatRange =
    SatRange(
        min = obj.longOrNull("min"),
        max = obj.longOrNull("max"),
    )

private fun serializeGfyDelta(delta: GfyDelta): JsonObject =
    buildJsonObject {
        delta.max_delta_ms?.let { put("max_delta_ms", it) }
        delta.actual_delta_ms?.let { put("actual_delta_ms", it) }
    }

private fun parseGfyDelta(obj: JsonObject): GfyDelta =
    GfyDelta(
        max_delta_ms = obj.longOrNull("max_delta_ms"),
        actual_delta_ms = obj.longOrNull("actual_delta_ms"),
    )

object OfferRequestKSerializer : KSerializer<OfferRequest> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ClinkOfferRequest")

    override fun serialize(
        encoder: Encoder,
        value: OfferRequest,
    ) {
        val jsonObject =
            buildJsonObject {
                value.offer?.let { put("offer", it) }
                value.amount_sats?.let { put("amount_sats", it) }
                value.payer_data?.let { put("payer_data", anyToJsonElement(it)) }
                value.zap?.let { put("zap", it) }
                value.expires_in_seconds?.let { put("expires_in_seconds", it) }
                value.description?.let { put("description", it) }
            }
        (encoder as JsonEncoder).encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): OfferRequest {
        val obj = (decoder as JsonDecoder).decodeJsonElement().jsonObject
        return OfferRequest(
            offer = obj.stringOrNull("offer"),
            amount_sats = obj.longOrNull("amount_sats"),
            payer_data = obj.objectOrNull("payer_data")?.toAnyMap(),
            zap = obj.stringOrNull("zap"),
            expires_in_seconds = obj.longOrNull("expires_in_seconds"),
            description = obj.stringOrNull("description"),
        )
    }
}

object OfferResponseKSerializer : KSerializer<OfferResponse> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ClinkOfferResponse")

    override fun serialize(
        encoder: Encoder,
        value: OfferResponse,
    ) {
        val jsonObject =
            buildJsonObject {
                value.bolt11?.let { put("bolt11", it) }
                value.error?.let { put("error", it) }
                value.code?.let { put("code", it) }
                value.range?.let { put("range", serializeSatRange(it)) }
                value.latest?.let { put("latest", it) }
            }
        (encoder as JsonEncoder).encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): OfferResponse {
        val obj = (decoder as JsonDecoder).decodeJsonElement().jsonObject
        return OfferResponse(
            bolt11 = obj.stringOrNull("bolt11"),
            error = obj.stringOrNull("error"),
            code = obj.intOrNull("code"),
            range = obj.objectOrNull("range")?.let { parseSatRange(it) },
            latest = obj.stringOrNull("latest"),
        )
    }
}

object OfferReceiptKSerializer : KSerializer<OfferReceipt> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ClinkOfferReceipt")

    override fun serialize(
        encoder: Encoder,
        value: OfferReceipt,
    ) {
        val jsonObject =
            buildJsonObject {
                value.res?.let { put("res", it) }
                value.preimage?.let { put("preimage", it) }
            }
        (encoder as JsonEncoder).encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): OfferReceipt {
        val obj = (decoder as JsonDecoder).decodeJsonElement().jsonObject
        return OfferReceipt(
            res = obj.stringOrNull("res"),
            preimage = obj.stringOrNull("preimage"),
        )
    }
}

object DebitRequestKSerializer : KSerializer<DebitRequest> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ClinkDebitRequest")

    override fun serialize(
        encoder: Encoder,
        value: DebitRequest,
    ) {
        val jsonObject =
            buildJsonObject {
                value.pointer?.let { put("pointer", it) }
                value.amount_sats?.let { put("amount_sats", it) }
                value.bolt11?.let { put("bolt11", it) }
                value.description?.let { put("description", it) }
                value.k1?.let { put("k1", it) }
                value.frequency?.let { put("frequency", serializeDebitFrequency(it)) }
            }
        (encoder as JsonEncoder).encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): DebitRequest {
        val obj = (decoder as JsonDecoder).decodeJsonElement().jsonObject
        return DebitRequest(
            pointer = obj.stringOrNull("pointer"),
            amount_sats = obj.longOrNull("amount_sats"),
            bolt11 = obj.stringOrNull("bolt11"),
            description = obj.stringOrNull("description"),
            k1 = obj.stringOrNull("k1"),
            frequency = obj.objectOrNull("frequency")?.let { parseDebitFrequency(it) },
        )
    }

    private fun serializeDebitFrequency(frequency: DebitFrequency): JsonObject =
        buildJsonObject {
            frequency.number?.let { put("number", it) }
            frequency.unit?.let { put("unit", it) }
        }

    private fun parseDebitFrequency(obj: JsonObject): DebitFrequency =
        DebitFrequency(
            number = obj.intOrNull("number"),
            unit = obj.stringOrNull("unit"),
        )
}

object DebitResponseKSerializer : KSerializer<DebitResponse> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ClinkDebitResponse")

    override fun serialize(
        encoder: Encoder,
        value: DebitResponse,
    ) {
        val jsonObject =
            buildJsonObject {
                value.res?.let { put("res", it) }
                value.preimage?.let { put("preimage", it) }
                value.code?.let { put("code", it) }
                value.error?.let { put("error", it) }
                value.range?.let { put("range", serializeSatRange(it)) }
                value.retry_after?.let { put("retry_after", it) }
                value.delta?.let { put("delta", serializeGfyDelta(it)) }
            }
        (encoder as JsonEncoder).encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): DebitResponse {
        val obj = (decoder as JsonDecoder).decodeJsonElement().jsonObject
        return DebitResponse(
            res = obj.stringOrNull("res"),
            preimage = obj.stringOrNull("preimage"),
            code = obj.intOrNull("code"),
            error = obj.stringOrNull("error"),
            range = obj.objectOrNull("range")?.let { parseSatRange(it) },
            retry_after = obj.longOrNull("retry_after"),
            delta = obj.objectOrNull("delta")?.let { parseGfyDelta(it) },
        )
    }
}

object ManageRequestKSerializer : KSerializer<ManageRequest> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ClinkManageRequest")

    override fun serialize(
        encoder: Encoder,
        value: ManageRequest,
    ) {
        val jsonObject =
            buildJsonObject {
                value.resource?.let { put("resource", it) }
                value.action?.let { put("action", it) }
                value.pointer?.let { put("pointer", it) }
                value.offer?.let { put("offer", serializeManageOffer(it)) }
            }
        (encoder as JsonEncoder).encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): ManageRequest {
        val obj = (decoder as JsonDecoder).decodeJsonElement().jsonObject
        return ManageRequest(
            resource = obj.stringOrNull("resource"),
            action = obj.stringOrNull("action"),
            pointer = obj.stringOrNull("pointer"),
            offer = obj.objectOrNull("offer")?.let { parseManageOffer(it) },
        )
    }

    private fun serializeManageOffer(offer: ManageOffer): JsonObject =
        buildJsonObject {
            offer.id?.let { put("id", it) }
            offer.fields?.let { put("fields", serializeOfferFields(it)) }
        }

    private fun parseManageOffer(obj: JsonObject): ManageOffer =
        ManageOffer(
            id = obj.stringOrNull("id"),
            fields = obj.objectOrNull("fields")?.let { parseOfferFields(it) },
        )

    private fun serializeOfferFields(fields: OfferFields): JsonObject =
        buildJsonObject {
            fields.label?.let { put("label", it) }
            fields.price_sats?.let { put("price_sats", it) }
            fields.callback_url?.let { put("callback_url", it) }
            fields.payer_data?.let { names -> put("payer_data", buildJsonArray { names.forEach { add(it) } }) }
        }

    private fun parseOfferFields(obj: JsonObject): OfferFields =
        OfferFields(
            label = obj.stringOrNull("label"),
            price_sats = obj.longOrNull("price_sats"),
            callback_url = obj.stringOrNull("callback_url"),
            payer_data = obj.stringListOrNull("payer_data"),
        )
}

object ManageResponseKSerializer : KSerializer<ManageResponse> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ClinkManageResponse")

    override fun serialize(
        encoder: Encoder,
        value: ManageResponse,
    ) {
        val jsonObject =
            buildJsonObject {
                value.res?.let { put("res", it) }
                value.resource?.let { put("resource", it) }
                value.details?.let { details ->
                    put("details", buildJsonArray { details.forEach { add(serializeOfferData(it)) } })
                }
                value.code?.let { put("code", it) }
                value.error?.let { put("error", it) }
                value.field?.let { put("field", it) }
                value.range?.let { put("range", serializeSatRange(it)) }
                value.retry_after?.let { put("retry_after", it) }
                value.delta?.let { put("delta", serializeGfyDelta(it)) }
            }
        (encoder as JsonEncoder).encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): ManageResponse {
        val obj = (decoder as JsonDecoder).decodeJsonElement().jsonObject
        return ManageResponse(
            res = obj.stringOrNull("res"),
            resource = obj.stringOrNull("resource"),
            details = parseDetails(obj["details"]),
            code = obj.intOrNull("code"),
            error = obj.stringOrNull("error"),
            field = obj.stringOrNull("field"),
            range = obj.objectOrNull("range")?.let { parseSatRange(it) },
            retry_after = obj.longOrNull("retry_after"),
            delta = obj.objectOrNull("delta")?.let { parseGfyDelta(it) },
        )
    }

    /** The spec types `details` as `OfferData | OfferData[]`: coerce a lone object into a one-element list. */
    private fun parseDetails(element: JsonElement?): List<OfferData>? =
        when (element) {
            null, is JsonNull -> null
            is JsonArray -> element.mapNotNull { (it as? JsonObject)?.let { obj -> parseOfferData(obj) } }
            is JsonObject -> listOf(parseOfferData(element))
            else -> null
        }

    private fun serializeOfferData(data: OfferData): JsonObject =
        buildJsonObject {
            data.id?.let { put("id", it) }
            data.noffer?.let { put("noffer", it) }
            data.label?.let { put("label", it) }
            data.price_sats?.let { put("price_sats", it) }
            data.callback_url?.let { put("callback_url", it) }
            data.payer_data?.let { names -> put("payer_data", buildJsonArray { names.forEach { add(it) } }) }
        }

    private fun parseOfferData(obj: JsonObject): OfferData =
        OfferData(
            id = obj.stringOrNull("id"),
            noffer = obj.stringOrNull("noffer"),
            label = obj.stringOrNull("label"),
            price_sats = obj.longOrNull("price_sats"),
            callback_url = obj.stringOrNull("callback_url"),
            payer_data = obj.stringListOrNull("payer_data"),
        )
}
