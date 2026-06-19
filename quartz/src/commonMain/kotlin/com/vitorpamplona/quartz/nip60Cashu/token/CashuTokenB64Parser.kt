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

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.DualCase
import com.vitorpamplona.quartz.utils.startsWith
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Parses NUT-00 out-of-band cashu token strings into [CashuToken]s — the
 * inverse of [V4Encoder].
 *
 *  - `cashuA` (v3): Base64-encoded JSON (standard or url-safe alphabet).
 *  - `cashuB` (v4): Base64URL-encoded CBOR (shares the [V4Token] wire models).
 *
 * Returns null on any malformed input — callers decide how to surface the
 * failure (the Amethyst UI wraps it in a `GenericLoadable.Error`). A single
 * token string can carry proofs from multiple mints, hence the list result
 * (one [CashuToken] per mint/keyset group).
 */
object CashuTokenB64Parser {
    /**
     * Both "cashuA" and "cashuB" prefixes are 6 chars. The prefix is stripped
     * with `drop()` rather than `removePrefix()` so it stays consistent with
     * the case-insensitive dispatch in [parse].
     */
    private const val PREFIX_LENGTH = 6

    /** Precomputed dual-case prefixes so dispatch avoids per-call case folding. */
    val cashuAPrefix = DualCase("cashuA")
    val cashuBPrefix = DualCase("cashuB")

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    fun parse(token: String): List<CashuToken>? =
        when {
            token.startsWith(cashuAPrefix) -> parseCashuA(token)
            token.startsWith(cashuBPrefix) -> parseCashuB(token)
            else -> null
        }

    @OptIn(ExperimentalEncodingApi::class)
    fun parseCashuA(token: String): List<CashuToken>? =
        try {
            val decoded = decodeStandardOrUrlSafe(token.drop(PREFIX_LENGTH)).decodeToString()
            val parsed = json.decodeFromString(V3TokenJson.serializer(), decoded)
            parsed.token?.map { entry ->
                buildToken(
                    token = token,
                    mint = entry.mint,
                    proofs = entry.proofs.map { CashuProof(id = it.id, amount = it.amount, secret = it.secret, c = it.c) },
                    unit = parsed.unit,
                    memo = parsed.memo,
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }

    @OptIn(ExperimentalEncodingApi::class, ExperimentalSerializationApi::class)
    fun parseCashuB(token: String): List<CashuToken>? =
        try {
            val bytes =
                Base64.UrlSafe
                    .withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)
                    .decode(token.drop(PREFIX_LENGTH))
            val parsed = cashuV4Cbor.decodeFromByteArray<V4Token>(bytes)
            parsed.t?.map { group ->
                val keysetId = group.i.toHexKey()
                buildToken(
                    token = token,
                    mint = parsed.m,
                    proofs = group.p.map { CashuProof(id = keysetId, amount = it.a.toLong(), secret = it.s, c = it.c.toHexKey()) },
                    unit = parsed.u,
                    memo = parsed.d,
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }

    /** Assembles a [CashuToken], deriving the total and normalising a blank memo to null. */
    private fun buildToken(
        token: String,
        mint: String,
        proofs: List<CashuProof>,
        unit: String?,
        memo: String?,
    ) = CashuToken(
        token = token,
        mint = mint,
        totalAmount = proofs.sumOf { it.amount },
        proofs = proofs,
        unit = unit,
        memo = memo?.takeIf { it.isNotBlank() },
    )

    /**
     * NUT-00 v3 specifies base64-urlsafe, but historical encoders (and older
     * Amethyst builds) emitted standard base64. Standard and url-safe only
     * differ in two characters, so an all-alphanumeric payload decodes the
     * same under either; try standard first and fall back to url-safe so both
     * encodings round-trip.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeStandardOrUrlSafe(payload: String): ByteArray =
        try {
            Base64.Default.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL).decode(payload)
        } catch (_: IllegalArgumentException) {
            Base64.UrlSafe.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL).decode(payload)
        }
}

/** NUT-00 v3 (`cashuA`) JSON envelope. Proofs reuse [CashuProofJson] (`C` → c). */
@Serializable
internal data class V3TokenJson(
    val token: List<V3TokenEntryJson>? = null,
    val unit: String? = null,
    val memo: String? = null,
)

@Serializable
internal data class V3TokenEntryJson(
    val mint: String,
    val proofs: List<CashuProofJson>,
)
