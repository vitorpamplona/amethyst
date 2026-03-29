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
package com.vitorpamplona.quartz.nip60Cashu.token

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class CashuProofJson(
    val id: String,
    val amount: Long,
    val secret: String,
    @SerialName("C") val c: String,
)

@Serializable
internal data class TokenContentJson(
    val mint: String,
    val proofs: List<CashuProofJson>,
    val unit: String = "sat",
    val del: List<String> = emptyList(),
)

object TokenContentParser {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    fun fromJson(jsonString: String): TokenContent? =
        try {
            val parsed = json.decodeFromString<TokenContentJson>(jsonString)
            TokenContent(
                mint = parsed.mint,
                proofs =
                    parsed.proofs.map { proof ->
                        CashuProof(
                            id = proof.id,
                            amount = proof.amount,
                            secret = proof.secret,
                            c = proof.c,
                        )
                    },
                unit = parsed.unit,
                del = parsed.del,
            )
        } catch (_: Exception) {
            null
        }

    fun toJson(tokenContent: TokenContent): String =
        json.encodeToString(
            TokenContentJson.serializer(),
            TokenContentJson(
                mint = tokenContent.mint,
                proofs =
                    tokenContent.proofs.map { proof ->
                        CashuProofJson(
                            id = proof.id,
                            amount = proof.amount,
                            secret = proof.secret,
                            c = proof.c,
                        )
                    },
                unit = tokenContent.unit,
                del = tokenContent.del,
            ),
        )
}
