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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync

/**
 * Thrown when the mint returns a non-2xx response or an HTTP error.
 *
 * The mint's `detail` field is preserved when present so the UI can surface
 * something more useful than "HTTP 500".
 */
class MintHttpException(
    val httpStatus: Int,
    val detail: String?,
    val code: Int?,
    message: String,
) : RuntimeException(message)

/**
 * Thrown when the mint responded with HTTP 2xx but the protocol-level
 * outcome was a failure (e.g. melt completed without reaching the PAID
 * state). Distinct from [MintHttpException] so callers can tell
 * "network/HTTP problem" apart from "mint said no to the request".
 */
class MintProtocolException(
    message: String,
) : RuntimeException(message)

/**
 * OkHttp-backed Cashu v1 mint client implementing NUT-00..06 endpoints.
 *
 * Each instance is bound to a single mint URL (e.g. `https://mint.example.com`).
 * Trailing slashes are stripped on construction.
 */
class MintHttpClient(
    mintUrl: String,
    private val okHttpClient: (String) -> OkHttpClient,
) {
    private val baseUrl: String = mintUrl.trimEnd('/')

    // Mint /v1/info changes infrequently (mint name, icon, supported
    // NUTs, motd). Cache it for [INFO_CACHE_TTL_MS] so the Verify
    // button + future NUT-17 feature-detection + any UI surface that
    // shows mint metadata doesn't refetch on every interaction.
    // Volatile pair so the read on the hot path doesn't need a lock —
    // a torn read just causes one extra HTTP call.
    @Volatile private var cachedInfo: Pair<MintInfoDto, Long>? = null

    /**
     * Fetch mint info, caching the last successful response for
     * [INFO_CACHE_TTL_MS]. Pass [force]=true to bypass and refetch
     * (e.g. user-initiated refresh, after a known mint upgrade).
     * Failures don't poison the cache — a stale-but-good entry beats
     * a transient network error.
     */
    suspend fun info(force: Boolean = false): MintInfoDto {
        if (!force) {
            cachedInfo?.let { (info, at) ->
                if (System.currentTimeMillis() - at < INFO_CACHE_TTL_MS) {
                    return info
                }
            }
        }
        val fresh = get<MintInfoDto>("/v1/info")
        cachedInfo = fresh to System.currentTimeMillis()
        return fresh
    }

    suspend fun activeKeysets(): KeysResponseDto = get("/v1/keys")

    suspend fun keysetById(keysetId: String): KeysResponseDto = get("/v1/keys/$keysetId")

    suspend fun keysets(): KeysetListResponseDto = get("/v1/keysets")

    suspend fun mintQuoteBolt11(request: MintQuoteBolt11RequestDto): MintQuoteBolt11ResponseDto = post("/v1/mint/quote/bolt11", request, MintQuoteBolt11RequestDto.serializer())

    suspend fun mintQuoteBolt11Status(quote: String): MintQuoteBolt11ResponseDto = get("/v1/mint/quote/bolt11/$quote")

    suspend fun mintBolt11(request: MintBolt11RequestDto): MintBolt11ResponseDto = post("/v1/mint/bolt11", request, MintBolt11RequestDto.serializer())

    suspend fun swap(request: SwapRequestDto): SwapResponseDto = post("/v1/swap", request, SwapRequestDto.serializer())

    suspend fun meltQuoteBolt11(request: MeltQuoteBolt11RequestDto): MeltQuoteBolt11ResponseDto = post("/v1/melt/quote/bolt11", request, MeltQuoteBolt11RequestDto.serializer())

    suspend fun meltQuoteBolt11Status(quote: String): MeltQuoteBolt11ResponseDto = get("/v1/melt/quote/bolt11/$quote")

    suspend fun meltBolt11(request: MeltBolt11RequestDto): MeltBolt11ResponseDto = post("/v1/melt/bolt11", request, MeltBolt11RequestDto.serializer())

    suspend fun checkState(request: CheckStateRequestDto): CheckStateResponseDto = post("/v1/checkstate", request, CheckStateRequestDto.serializer())

    /**
     * NUT-09 restore. Encodes the request through the generated
     * serializer (small payload — fine for the JIT), but decodes the
     * response via [Json.parseToJsonElement] (tree API) instead of the
     * generated `RestoreResponseDto.serializer().deserialize`.
     *
     * Why bypass the generated path: on Android 15+ ART crashes the
     * JIT optimizer (SIGSEGV at offset 0x48 in Jit thread pool) when
     * it reaches tier-1 compilation of the kotlinx.serialization
     * generated decode body for `BlindSignatureDto` after ~3-4 batches
     * of ~300-400 elements each. The tree API uses a different,
     * smaller, simpler code path that avoids the offending pattern —
     * the parser is hand-walked here, no generated serializers
     * involved in the hot loop.
     */
    suspend fun restore(request: RestoreRequestDto): RestoreResponseDto = postWithManualResponseDecode("/v1/restore", request, RestoreRequestDto.serializer(), ::parseRestoreResponse)

    private suspend inline fun <reified R> get(path: String): R =
        withContext(Dispatchers.IO) {
            val url = baseUrl + path
            val client = okHttpClient(url)
            val req =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .build()
            client.newCall(req).executeAsync().use { resp ->
                val body = resp.body.string()
                if (!resp.isSuccessful) throw decodeError(resp.code, body)
                json.decodeFromString<R>(body)
            }
        }

    private suspend inline fun <T, reified R> post(
        path: String,
        body: T,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): R =
        withContext(Dispatchers.IO) {
            val url = baseUrl + path
            val client = okHttpClient(url)
            val bodyJson = json.encodeToString(serializer, body)
            val req =
                Request
                    .Builder()
                    .url(url)
                    .post(bodyJson.toRequestBody(jsonMediaType))
                    .build()
            client.newCall(req).executeAsync().use { resp ->
                val text = resp.body.string()
                if (!resp.isSuccessful) throw decodeError(resp.code, text)
                json.decodeFromString<R>(text)
            }
        }

    /**
     * POST + decode-via-callback variant. The encode side still goes
     * through the generated serializer (request payloads are small),
     * but the response body is handed to the caller as a JSON string
     * so the caller can pick a decode path that avoids
     * kotlinx.serialization's generated deserializers — see [restore]
     * for the rationale.
     */
    private suspend fun <T> postWithManualResponseDecode(
        path: String,
        body: T,
        bodySerializer: kotlinx.serialization.KSerializer<T>,
        decode: (String) -> RestoreResponseDto,
    ): RestoreResponseDto =
        withContext(Dispatchers.IO) {
            val url = baseUrl + path
            val client = okHttpClient(url)
            val bodyJson = json.encodeToString(bodySerializer, body)
            val req =
                Request
                    .Builder()
                    .url(url)
                    .post(bodyJson.toRequestBody(jsonMediaType))
                    .build()
            client.newCall(req).executeAsync().use { resp ->
                val text = resp.body.string()
                if (!resp.isSuccessful) throw decodeError(resp.code, text)
                decode(text)
            }
        }

    /**
     * Hand-rolled [RestoreResponseDto] decoder.
     *
     * Uses [Json.parseToJsonElement] (the JSON-tree API) plus manual
     * field extraction instead of the generated
     * `RestoreResponseDto.serializer().deserialize(...)`. The tree API
     * is a single Json parser routine — much smaller bytecode than
     * the per-class generated deserializers, and crucially NOT the
     * code path that ART JIT crashes on at tier-1 compilation on
     * Android 15+.
     *
     * Defensive: missing / wrong-shape fields fall back to safe
     * defaults (empty lists, null dleq) so a misbehaving mint can't
     * trip the decoder.
     */
    private fun parseRestoreResponse(text: String): RestoreResponseDto {
        val root =
            runCatching { json.parseToJsonElement(text).jsonObject }
                .getOrNull()
                ?: return RestoreResponseDto(outputs = emptyList(), signatures = emptyList())

        val outputsArr = root["outputs"]?.jsonArray
        val signaturesArr = root["signatures"]?.jsonArray

        val outputs =
            outputsArr
                ?.map { el ->
                    val o = el.jsonObject
                    BlindedMessageDto(
                        amount = o.getValue("amount").jsonPrimitive.long,
                        id =
                            o
                                .getValue("id")
                                .jsonPrimitive.contentOrNull
                                .orEmpty(),
                        bTick =
                            o
                                .getValue("B_")
                                .jsonPrimitive.contentOrNull
                                .orEmpty(),
                    )
                }.orEmpty()

        val signatures =
            signaturesArr
                ?.map { el ->
                    val o = el.jsonObject
                    val dleq =
                        (o["dleq"] as? JsonObject)?.let { d ->
                            DleqProofDto(
                                e =
                                    d
                                        .getValue("e")
                                        .jsonPrimitive.contentOrNull
                                        .orEmpty(),
                                s =
                                    d
                                        .getValue("s")
                                        .jsonPrimitive.contentOrNull
                                        .orEmpty(),
                                r = (d["r"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull,
                            )
                        }
                    BlindSignatureDto(
                        amount = o.getValue("amount").jsonPrimitive.long,
                        id =
                            o
                                .getValue("id")
                                .jsonPrimitive.contentOrNull
                                .orEmpty(),
                        cTick =
                            o
                                .getValue("C_")
                                .jsonPrimitive.contentOrNull
                                .orEmpty(),
                        dleq = dleq,
                    )
                }.orEmpty()

        return RestoreResponseDto(outputs = outputs, signatures = signatures)
    }

    private fun decodeError(
        status: Int,
        body: String,
    ): MintHttpException {
        val parsed =
            runCatching { json.decodeFromString<MintErrorDto>(body) }
                .getOrNull()
        val detail = parsed?.detail
        val code = parsed?.code
        val message = detail ?: body.ifBlank { "HTTP $status" }
        return MintHttpException(status, detail, code, message)
    }

    companion object {
        internal val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
                explicitNulls = false
            }

        private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

        /**
         * Mint info TTL. /v1/info typically changes on the order of
         * weeks (mint name, supported NUTs, motd updates). 30 minutes
         * is a compromise between "stale enough to matter" and "fresh
         * enough that a user-visible mint upgrade reaches the UI
         * without an app restart". Callers that need certainty (e.g.
         * after the user explicitly retries) pass force=true.
         */
        private const val INFO_CACHE_TTL_MS: Long = 30L * 60L * 1000L
    }
}
