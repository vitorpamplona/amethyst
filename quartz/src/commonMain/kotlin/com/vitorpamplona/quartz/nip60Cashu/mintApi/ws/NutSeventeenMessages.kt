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
package com.vitorpamplona.quartz.nip60Cashu.mintApi.ws

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * NUT-17 subscription kinds — what mint events the wallet wants pushed.
 * Values match the on-the-wire string verbatim; don't rename.
 */
object NutSeventeenKinds {
    const val BOLT11_MINT_QUOTE = "bolt11_mint_quote"
    const val BOLT11_MELT_QUOTE = "bolt11_melt_quote"
    const val BOLT12_MINT_QUOTE = "bolt12_mint_quote"
    const val BOLT12_MELT_QUOTE = "bolt12_melt_quote"
    const val PROOF_STATE = "proof_state"
}

/**
 * NUT-17 JSON-RPC 2.0 framing. The mint speaks a tiny subset of JSON-RPC
 * over WebSocket:
 *
 *  - **Request** (wallet→mint): subscribe / unsubscribe, with [id] for
 *    correlation. Mint replies with [WsResponse].
 *  - **Notification** (mint→wallet): unsolicited state push using
 *    method="subscribe" and a [subId] reference back to the original
 *    request. No `id` field — distinguishing notifications from
 *    responses is what the discriminator helper below is for.
 */
@Serializable
data class WsRequest(
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    val jsonrpc: String = "2.0",
    val method: String,
    val params: WsRequestParams,
    val id: Long,
)

/**
 * Either a subscribe (kind + filters + subId) or unsubscribe (subId only).
 * The mint discriminates on the [method] field of the parent [WsRequest];
 * we keep both shapes on one DTO so a single serializer round-trips both.
 */
@Serializable
data class WsRequestParams(
    val kind: String? = null,
    val filters: List<String>? = null,
    val subId: String,
)

/**
 * Response to a wallet-initiated request. The mint echoes the `id` from
 * the request; `result` shape depends on the method (status field on
 * subscribe acknowledgements, etc.).
 */
@Serializable
data class WsResponse(
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    val jsonrpc: String = "2.0",
    val result: JsonElement? = null,
    val error: WsError? = null,
    val id: Long,
)

@Serializable
data class WsError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null,
)

/**
 * Mint-pushed state update. `method` is always the literal string
 * "subscribe" per NUT-17; [params] carries the original subId plus the
 * payload, whose shape depends on the subscription kind (mint-quote
 * response, melt-quote response, or check-state response).
 */
@Serializable
data class WsNotification(
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    val jsonrpc: String = "2.0",
    val method: String,
    val params: WsNotificationParams,
)

@Serializable
data class WsNotificationParams(
    val subId: String,
    /**
     * Payload is one of:
     *   - kind=bolt11_mint_quote → MintQuoteBolt11ResponseDto
     *   - kind=bolt11_melt_quote → MeltQuoteBolt11ResponseDto
     *   - kind=proof_state       → ProofStateNotificationDto (NUT-07 shape)
     *
     * Kept as `JsonElement` so we don't have to deserialize-then-discriminate;
     * the caller knows the kind from the subId it created and decodes
     * directly.
     */
    val payload: JsonElement,
)

/**
 * NUT-07 proof state notification payload — one row from the
 * /v1/checkstate response, but pushed individually per-proof.
 */
@Serializable
data class ProofStateNotificationDto(
    @SerialName("Y") val y: String,
    val state: String,
    val witness: String? = null,
)
