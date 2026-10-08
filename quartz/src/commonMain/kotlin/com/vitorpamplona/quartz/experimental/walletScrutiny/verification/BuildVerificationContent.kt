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
package com.vitorpamplona.quartz.experimental.walletScrutiny.verification

import androidx.compose.runtime.Immutable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The JSON `content` of a [BuildVerificationEvent]: `{"description": "<what was reproduced>",
 * "content": "<the report, markdown>"}`. WalletScrutiny caps the description at 120 chars and the
 * report at 60,000.
 */
@Immutable
class BuildVerificationContent(
    val description: String?,
    val report: String?,
) {
    fun toJson(): String =
        buildJsonObject {
            description?.let { put(DESCRIPTION, it) }
            report?.let { put(REPORT, it) }
        }.toString()

    companion object {
        private const val DESCRIPTION = "description"
        private const val REPORT = "content"

        /** Parses [json], or returns null when it is not a JSON object. Never throws. */
        fun parse(json: String): BuildVerificationContent? {
            if (!looksLikeJsonObject(json)) return null

            val obj =
                try {
                    Json.parseToJsonElement(json) as? JsonObject
                } catch (_: Exception) {
                    null
                } ?: return null

            return BuildVerificationContent(text(obj, DESCRIPTION), text(obj, REPORT))
        }

        private fun text(
            obj: JsonObject,
            key: String,
        ): String? = (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.ifBlank { null }

        private fun looksLikeJsonObject(text: String): Boolean {
            for (c in text) {
                if (!c.isWhitespace()) return c == '{'
            }
            return false
        }
    }
}
