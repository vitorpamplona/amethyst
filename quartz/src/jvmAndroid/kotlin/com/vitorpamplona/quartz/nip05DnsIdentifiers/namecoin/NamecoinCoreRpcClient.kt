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
package com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync
import java.util.concurrent.TimeUnit

/**
 * Namecoin Core JSON-RPC client.
 *
 * Implements [NamecoinNameBackend] by issuing a `name_show` JSON-RPC call
 * against a user-configured Namecoin Core node — typically a StartOS
 * install reached over its Tor hidden service or LAN URL.
 *
 * The transport is delegated to an [OkHttpClient] supplied by the caller
 * (Amethyst plumbs it through `roleBasedHttpClientBuilder.okHttpClientForNip05(url)`
 * which already honours the user's Tor settings and pinned-trust-store
 * choices). This means onion endpoints "just work" without us having to
 * teach this class anything about SOCKS proxies.
 *
 * Wire format (Bitcoin-Core convention, accepted by Namecoin Core):
 *
 * ```
 * POST /  HTTP/1.1
 * Authorization: Basic <base64(user:pass)>
 * Content-Type: text/plain
 *
 * {"jsonrpc":"1.0","id":"amethyst","method":"name_show","params":["d/example"]}
 * ```
 *
 * Response:
 *
 * ```
 * { "result": { "name": "d/example", "value": "{...}",
 *               "txid": "...", "height": 12345, "expires_in": 36000,
 *               "expired": false, ... },
 *   "error": null, "id": "amethyst" }
 * ```
 *
 * On `result == null`, the `error` object is inspected: error code -4
 * with a "name not found"/"name never existed" message becomes
 * [NamecoinLookupException.NameNotFound]. Any HTTP-level failure or
 * transport exception becomes [NamecoinLookupException.ServersUnreachable].
 */
class NamecoinCoreRpcClient(
    private val httpClientForUrl: (String) -> OkHttpClient,
) : NamecoinNameBackend {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    @Volatile
    private var config: NamecoinCoreRpcConfig = NamecoinCoreRpcConfig()

    fun setConfig(cfg: NamecoinCoreRpcConfig) {
        config = cfg
    }

    fun currentConfig(): NamecoinCoreRpcConfig = config

    /**
     * Run `name_show <identifier>` against the configured Namecoin Core node.
     *
     * Returns the [NameShowResult] on success, throws
     * [NamecoinLookupException.NameNotFound] / [NamecoinLookupException.NameExpired]
     * for authoritative negatives, and
     * [NamecoinLookupException.ServersUnreachable] for transport / config
     * problems (so the [CompositeNamecoinBackend] can cascade to fallbacks).
     */
    override suspend fun nameShow(identifier: String): NameShowResult? = callNameShow(config, identifier)

    /**
     * Public testable form — used by the Settings "Test RPC connection"
     * button which probes an ad-hoc [NamecoinCoreRpcConfig] without
     * mutating the persisted client config.
     */
    suspend fun probe(cfg: NamecoinCoreRpcConfig): RpcProbeResult {
        val started = System.currentTimeMillis()
        return try {
            // Use a cheap, side-effect-free call: getblockchaininfo. It works on every
            // bitcoin-derived node, doesn't require -namehistoric, and answers
            // quickly even on slow links.
            val element =
                callRpc(cfg, method = "getblockchaininfo", params = emptyList())
            val obj = element as? JsonObject
            val chain = (obj?.get("chain") as? JsonPrimitive)?.contentOrNull
            val blocks = (obj?.get("blocks") as? JsonPrimitive)?.intOrNull
            val verification =
                (obj?.get("verificationprogress") as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
            val initialDownload =
                (obj?.get("initialblockdownload") as? JsonPrimitive)?.let {
                    try {
                        it.boolean
                    } catch (_: Exception) {
                        null
                    }
                }
            RpcProbeResult(
                success = true,
                elapsedMs = System.currentTimeMillis() - started,
                chain = chain,
                blocks = blocks,
                verificationProgress = verification,
                initialBlockDownload = initialDownload,
            )
        } catch (e: NamecoinLookupException.ServersUnreachable) {
            RpcProbeResult(
                success = false,
                elapsedMs = System.currentTimeMillis() - started,
                error = e.cause?.message ?: e.message ?: "Unreachable",
            )
        } catch (e: Exception) {
            RpcProbeResult(
                success = false,
                elapsedMs = System.currentTimeMillis() - started,
                error = e.message ?: e::class.simpleName ?: "Error",
            )
        }
    }

    private suspend fun callNameShow(
        cfg: NamecoinCoreRpcConfig,
        identifier: String,
    ): NameShowResult? {
        val element =
            try {
                callRpc(cfg, method = "name_show", params = listOf(JsonPrimitive(identifier)))
            } catch (e: NamecoinLookupException) {
                throw e
            }

        val obj = element as? JsonObject ?: return null
        val name = (obj["name"] as? JsonPrimitive)?.contentOrNull ?: identifier
        val value = (obj["value"] as? JsonPrimitive)?.contentOrNull ?: return null
        val txid = (obj["txid"] as? JsonPrimitive)?.contentOrNull
        val height = (obj["height"] as? JsonPrimitive)?.intOrNull
        val expiresIn = (obj["expires_in"] as? JsonPrimitive)?.intOrNull
        val expired =
            (obj["expired"] as? JsonPrimitive)?.let {
                try {
                    it.boolean
                } catch (_: Exception) {
                    false
                }
            } ?: false

        if (expired) throw NamecoinLookupException.NameExpired(name)

        return NameShowResult(
            name = name,
            value = value,
            txid = txid,
            height = height,
            expiresIn = expiresIn,
        )
    }

    /**
     * Low-level call: returns the `result` JSON element on success, or
     * throws a typed [NamecoinLookupException] on JSON-RPC / transport
     * errors. Only callers that need raw access to the result should use
     * this directly.
     */
    private suspend fun callRpc(
        cfg: NamecoinCoreRpcConfig,
        method: String,
        params: List<kotlinx.serialization.json.JsonElement>,
    ): kotlinx.serialization.json.JsonElement =
        withContext(Dispatchers.IO) {
            if (!cfg.isUsable) {
                throw NamecoinLookupException.ServersUnreachable(
                    lastError = IllegalArgumentException("Namecoin Core RPC URL not configured"),
                )
            }

            val body =
                buildString {
                    append("{\"jsonrpc\":\"1.0\",\"id\":\"amethyst\",\"method\":\"")
                    append(method)
                    append("\",\"params\":[")
                    params.forEachIndexed { i, p ->
                        if (i > 0) append(',')
                        append(p.toString())
                    }
                    append("]}")
                }.toRequestBody("text/plain".toMediaType())

            val requestBuilder =
                Request
                    .Builder()
                    .url(cfg.url)
                    .post(body)
                    .header("Content-Type", "text/plain")

            if (cfg.username.isNotEmpty() || cfg.password.isNotEmpty()) {
                requestBuilder.header(
                    "Authorization",
                    Credentials.basic(cfg.username, cfg.password),
                )
            }

            val client =
                httpClientForUrl(cfg.url)
                    .newBuilder()
                    .followRedirects(false)
                    .connectTimeout(cfg.timeoutMs, TimeUnit.MILLISECONDS)
                    .readTimeout(cfg.timeoutMs, TimeUnit.MILLISECONDS)
                    .callTimeout(cfg.timeoutMs, TimeUnit.MILLISECONDS)
                    .build()

            val request = requestBuilder.build()

            val responseBody: String =
                try {
                    client.newCall(request).executeAsync().use { resp ->
                        val raw = resp.body.string()
                        if (!resp.isSuccessful && raw.isBlank()) {
                            // No JSON body to inspect — surface as transport failure.
                            throw NamecoinLookupException.ServersUnreachable(
                                lastError = IllegalStateException("HTTP ${resp.code} ${resp.message}"),
                            )
                        }
                        raw
                    }
                } catch (e: NamecoinLookupException) {
                    throw e
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw NamecoinLookupException.ServersUnreachable(lastError = e)
                }

            val parsed =
                try {
                    json.parseToJsonElement(responseBody).jsonObject
                } catch (e: Exception) {
                    throw NamecoinLookupException.ServersUnreachable(
                        lastError = IllegalStateException("Non-JSON response: ${e.message}"),
                    )
                }

            // JSON-RPC error path: classify by code/message.
            val errorEl = parsed["error"]
            if (errorEl != null && errorEl !is kotlinx.serialization.json.JsonNull) {
                val errObj = errorEl as? JsonObject
                val code = (errObj?.get("code") as? JsonPrimitive)?.intOrNull
                val msg = (errObj?.get("message") as? JsonPrimitive)?.contentOrNull.orEmpty()
                // -4 covers both "name not found" and "name never existed".
                // Some Namecoin Core builds also use -5.
                if (code == -4 || code == -5 ||
                    msg.contains("name not found", ignoreCase = true) ||
                    msg.contains("name never existed", ignoreCase = true)
                ) {
                    // Use the requested name from params for the exception payload.
                    val askedName = (params.firstOrNull() as? JsonPrimitive)?.contentOrNull.orEmpty()
                    throw NamecoinLookupException.NameNotFound(askedName)
                }
                throw NamecoinLookupException.ServersUnreachable(
                    lastError = IllegalStateException("RPC error $code: $msg"),
                )
            }

            parsed["result"]
                ?: throw NamecoinLookupException.ServersUnreachable(
                    lastError = IllegalStateException("RPC response missing result"),
                )
        }
}

/** Outcome of a `Test RPC` probe in Settings. */
data class RpcProbeResult(
    val success: Boolean,
    val elapsedMs: Long,
    val chain: String? = null,
    val blocks: Int? = null,
    val verificationProgress: Double? = null,
    val initialBlockDownload: Boolean? = null,
    val error: String? = null,
)
