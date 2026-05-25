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

    suspend fun info(): MintInfoDto = get("/v1/info")

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
    }
}
