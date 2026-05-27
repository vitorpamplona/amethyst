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
package com.vitorpamplona.quartz.nip60Cashu.mintApi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Cashu v1 mint HTTP DTOs covering NUT-00 through NUT-06.
 *
 *   GET  /v1/info                       → [MintInfoDto]
 *   GET  /v1/keys                       → [KeysResponseDto]
 *   GET  /v1/keys/{keyset_id}           → [KeysResponseDto]
 *   GET  /v1/keysets                    → [KeysetListResponseDto]
 *
 *   POST /v1/mint/quote/bolt11          → [MintQuoteBolt11ResponseDto]
 *   GET  /v1/mint/quote/bolt11/{quote}  → [MintQuoteBolt11ResponseDto]
 *   POST /v1/mint/bolt11                → [MintBolt11ResponseDto]
 *
 *   POST /v1/swap                       → [SwapResponseDto]
 *
 *   POST /v1/melt/quote/bolt11          → [MeltQuoteBolt11ResponseDto]
 *   GET  /v1/melt/quote/bolt11/{quote}  → [MeltQuoteBolt11ResponseDto]
 *   POST /v1/melt/bolt11                → [MeltBolt11ResponseDto]
 *
 *   POST /v1/checkstate                 → [CheckStateResponseDto]
 *
 * State enums (`PAID`, `UNPAID`, `ISSUED`, `PENDING`, `SPENT`, `UNSPENT`) are
 * kept as plain strings — the protocol is stable but error-tolerance is cheap.
 */

@Serializable
data class MintInfoDto(
    val name: String? = null,
    val pubkey: String? = null,
    val version: String? = null,
    val description: String? = null,
    @SerialName("description_long") val descriptionLong: String? = null,
    val contact: List<MintContactDto>? = null,
    val motd: String? = null,
    @SerialName("icon_url") val iconUrl: String? = null,
)

@Serializable
data class MintContactDto(
    val method: String? = null,
    val info: String? = null,
)

@Serializable
data class KeysetSummaryDto(
    val id: String,
    val unit: String,
    val active: Boolean = true,
    @SerialName("input_fee_ppk") val inputFeePpk: Long? = null,
)

@Serializable
data class KeysetListResponseDto(
    val keysets: List<KeysetSummaryDto>,
)

@Serializable
data class KeysetDto(
    val id: String,
    val unit: String,
    /** amount (as decimal string) → mint pubkey for that amount (33-byte compressed hex). */
    val keys: Map<String, String>,
    /**
     * NUT-02 per-input fee, in parts-per-thousand of one input proof. The
     * mint charges `ceil(numInputs * inputFeePpk / 1000)` extra atoms on
     * every swap/melt — the wallet must reserve this from inputs or the
     * mint rejects with "amount-mismatch". Older mints don't include this
     * field; treat absent as zero fee.
     */
    @SerialName("input_fee_ppk") val inputFeePpk: Long? = null,
)

@Serializable
data class KeysResponseDto(
    val keysets: List<KeysetDto>,
)

@Serializable
data class BlindedMessageDto(
    val amount: Long,
    val id: String,
    @SerialName("B_") val bTick: String,
)

@Serializable
data class BlindSignatureDto(
    val amount: Long,
    val id: String,
    @SerialName("C_") val cTick: String,
)

@Serializable
data class ProofDto(
    val amount: Long,
    val id: String,
    val secret: String,
    @SerialName("C") val c: String,
    /**
     * NUT-11 witness for spending a P2PK-locked proof. Encoded as a JSON
     * string: `{"signatures":["<hex_sig>", …]}`. Mints ignore this field on
     * non-locked proofs.
     */
    val witness: String? = null,
)

@Serializable
data class MintQuoteBolt11RequestDto(
    val unit: String,
    val amount: Long,
    val description: String? = null,
)

@Serializable
data class MintQuoteBolt11ResponseDto(
    val quote: String,
    val request: String,
    val state: String,
    val expiry: Long? = null,
    val paid: Boolean? = null,
)

@Serializable
data class MintBolt11RequestDto(
    val quote: String,
    val outputs: List<BlindedMessageDto>,
)

@Serializable
data class MintBolt11ResponseDto(
    val signatures: List<BlindSignatureDto>,
)

@Serializable
data class SwapRequestDto(
    val inputs: List<ProofDto>,
    val outputs: List<BlindedMessageDto>,
)

@Serializable
data class SwapResponseDto(
    val signatures: List<BlindSignatureDto>,
)

@Serializable
data class MeltQuoteBolt11RequestDto(
    val unit: String,
    val request: String,
)

@Serializable
data class MeltQuoteBolt11ResponseDto(
    val quote: String,
    val amount: Long,
    @SerialName("fee_reserve") val feeReserve: Long,
    val state: String,
    val expiry: Long? = null,
    val paid: Boolean? = null,
)

@Serializable
data class MeltBolt11RequestDto(
    val quote: String,
    val inputs: List<ProofDto>,
    val outputs: List<BlindedMessageDto>? = null,
)

@Serializable
data class MeltBolt11ResponseDto(
    val quote: String? = null,
    val state: String? = null,
    val paid: Boolean? = null,
    val payment_preimage: String? = null,
    val change: List<BlindSignatureDto>? = null,
)

@Serializable
data class CheckStateRequestDto(
    @SerialName("Ys") val ys: List<String>,
)

@Serializable
data class CheckStateRowDto(
    @SerialName("Y") val y: String,
    val state: String,
    val witness: String? = null,
)

@Serializable
data class CheckStateResponseDto(
    val states: List<CheckStateRowDto>,
)

@Serializable
data class MintErrorDto(
    val detail: String? = null,
    val code: Int? = null,
)
