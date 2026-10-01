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
package com.vitorpamplona.quartz.experimental.predictionMarkets

import androidx.compose.runtime.Immutable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * The market fields BAO Markets writes as JSON — in the `data` tag of its current shape, or as the
 * whole `content` of its BAO Fund and first shapes. Every field is optional: the shapes disagree
 * on which ones they carry, and nothing here is trusted to be well-formed.
 */
@Immutable
class PredictionMarketDetails(
    val title: String?,
    val description: String?,
    val outcomes: List<String>,
    val category: String?,
    val status: String?,
    val reason: String?,
    val resolution: String?,
    val endDate: Long?,
    val resolutionSource: String?,
    val minBetSats: Long?,
    val maxBetSats: Long?,
    val feePercent: Double?,
) {
    companion object {
        /** Parses [json] into details, or null when it is not a JSON object. Never throws. */
        fun parse(json: String?): PredictionMarketDetails? {
            if (json == null || !looksLikeJsonObject(json)) return null

            val obj =
                try {
                    Json.parseToJsonElement(json) as? JsonObject
                } catch (_: Exception) {
                    null
                } ?: return null

            return PredictionMarketDetails(
                title = obj.text("title") ?: obj.text("question"),
                description = obj.text("description"),
                outcomes = obj.outcomes(),
                category = obj.text("category"),
                status = obj.text("status"),
                reason = obj.text("reason"),
                resolution = obj.text("resolution"),
                endDate = obj.long("endDate") ?: obj.long("end"),
                resolutionSource = obj.text("resolutionSource"),
                minBetSats = obj.long("minBetSats"),
                maxBetSats = obj.long("maxBetSats"),
                feePercent = obj.double("feePercent"),
            )
        }

        /** Skips the parser for the social-post `content` of the current shape: JSON objects start with `{`. */
        private fun looksLikeJsonObject(text: String): Boolean {
            for (c in text) {
                if (!c.isWhitespace()) return c == '{'
            }
            return false
        }

        private fun JsonObject.primitive(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

        /** A string field, or null when absent, blank, null or not a string. */
        private fun JsonObject.text(key: String): String? = primitive(key)?.takeIf { it.isString }?.content?.ifBlank { null }

        private fun JsonObject.long(key: String): Long? = primitive(key)?.longOrNull

        private fun JsonObject.double(key: String): Double? = primitive(key)?.doubleOrNull

        /**
         * `outcomes` as display strings: a plain string per outcome (current shape), or an object
         * per outcome whose `label` (else `id`, `name`) names it (BAO Fund and first shapes).
         */
        private fun JsonObject.outcomes(): List<String> {
            val array = this["outcomes"] as? JsonArray ?: return emptyList()
            val result = ArrayList<String>(array.size)
            for (element in array) {
                val name = outcomeName(element)
                if (name != null && name !in result) result.add(name)
            }
            return result
        }

        private fun outcomeName(element: JsonElement): String? =
            when (element) {
                is JsonPrimitive -> element.takeIf { it.isString }?.content?.ifBlank { null }
                is JsonObject -> element.text("label") ?: element.text("id") ?: element.text("name")
                else -> null
            }
    }
}
