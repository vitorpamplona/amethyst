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
package com.vitorpamplona.quartz.experimental.ballots

import androidx.compose.runtime.Immutable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The JSON `content` of a [BallotEvent]. Three shapes are seen in the wild, tried in order:
 *
 * - `responses`: `[{question_id, value}]`, one entry per question; a value may be a string, a
 *   number or a boolean.
 * - `ballot`: an object mapping each question to its choice.
 * - `vote_choice`: a single choice with no question, read as [BallotEvent.VOTE_QUESTION].
 *
 * Values that are objects, arrays or null are skipped rather than stringified.
 */
@Immutable
class BallotContent(
    val answers: List<BallotAnswer>,
    val proofHash: String?,
) {
    companion object {
        /** Parses [json], or returns null when it is not a JSON object. Never throws. */
        fun parse(json: String): BallotContent? {
            if (!looksLikeJsonObject(json)) return null

            val obj =
                try {
                    Json.parseToJsonElement(json) as? JsonObject
                } catch (_: Exception) {
                    null
                } ?: return null

            return BallotContent(
                answers = answers(obj),
                proofHash = (obj["proof_hash"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.ifBlank { null },
            )
        }

        private fun looksLikeJsonObject(text: String): Boolean {
            for (c in text) {
                if (!c.isWhitespace()) return c == '{'
            }
            return false
        }

        private fun answers(obj: JsonObject): List<BallotAnswer> {
            val responses = obj["responses"] as? JsonArray
            if (responses != null) {
                return responses.mapNotNull { response ->
                    val entry = response as? JsonObject ?: return@mapNotNull null
                    val question = scalar(entry["question_id"]) ?: scalar(entry["question"]) ?: return@mapNotNull null
                    val answer = scalar(entry["value"]) ?: return@mapNotNull null
                    BallotAnswer(question, answer)
                }
            }

            val ballot = obj["ballot"] as? JsonObject
            if (ballot != null) {
                return ballot.mapNotNull { (question, choice) ->
                    scalar(choice)?.let { BallotAnswer(question, it) }
                }
            }

            return scalar(obj["vote_choice"])?.let { listOf(BallotAnswer(BallotEvent.VOTE_QUESTION, it)) } ?: emptyList()
        }

        /** A string, number or boolean as text; null for anything else, and for blank strings. */
        private fun scalar(element: JsonElement?): String? =
            if (element is JsonPrimitive && element !is JsonNull) {
                element.content.ifBlank { null }
            } else {
                null
            }
    }
}
