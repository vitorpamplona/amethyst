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
import java.io.ByteArrayInputStream
import java.net.Socket
import java.net.URI
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Namecoin Core JSON-RPC client.
 *
 * Implements [NamecoinNameBackend] by issuing a `name_show` JSON-RPC call
 * against a user-configured Namecoin Core node — typically a StartOS or
 * umbrel install reached over its Tor hidden service or LAN URL.
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

    /**
     * User-supplied PEM certificates the user has explicitly trusted
     * via the Settings "Test RPC" TOFU prompt. Shared with
     * [ElectrumXClient] at the SharedPreferences layer but kept in a
     * private list here so each client owns its own [SSLSocketFactory].
     */
    private val dynamicCerts = mutableListOf<String>()

    /** Lazy-cached SSLSocketFactory for pinned certs. Thread-safe via volatile + DCL. */
    @Volatile
    private var pinnedFactory: SSLSocketFactory? = null

    fun setConfig(cfg: NamecoinCoreRpcConfig) {
        config = cfg
    }

    fun currentConfig(): NamecoinCoreRpcConfig = config

    /**
     * Append a PEM-encoded certificate to the dynamic trust store.
     * Typically called after the user confirms a cert fingerprint via
     * the Settings "Test RPC" flow. Invalidates the cached factory so
     * the next connection picks it up.
     */
    fun addPinnedCert(pem: String) {
        synchronized(this) {
            if (pem !in dynamicCerts) dynamicCerts.add(pem)
            pinnedFactory = null
        }
    }

    /**
     * Replace all dynamic certs (e.g. loaded from preferences on startup).
     */
    fun setDynamicCerts(pems: List<String>) {
        synchronized(this) {
            dynamicCerts.clear()
            dynamicCerts.addAll(pems)
            pinnedFactory = null
        }
    }

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
        // Capture the leaf cert + fingerprint as a best-effort side channel
        // before issuing the RPC. This lets the Settings UI show the
        // server's certificate even when the subsequent RPC call fails
        // (e.g. wrong username) and — crucially — even when the RPC
        // call would fail TLS verification (so the user can choose to
        // pin it via TOFU). Only meaningful for https URLs.
        val captured: CapturedLeafCert? =
            withContext(Dispatchers.IO) { captureLeafCert(cfg) }
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
                serverCertPem = captured?.pem,
                certFingerprint = captured?.fingerprint,
            )
        } catch (e: NamecoinLookupException.ServersUnreachable) {
            val cause = e.cause
            val tlsFailure =
                cause is SSLHandshakeException ||
                    (cause is javax.net.ssl.SSLException && cause.message?.contains("trust", ignoreCase = true) == true)
            RpcProbeResult(
                success = false,
                elapsedMs = System.currentTimeMillis() - started,
                error = cause?.message ?: e.message ?: "Unreachable",
                serverCertPem = captured?.pem,
                certFingerprint = captured?.fingerprint,
                tlsHandshakeFailed = tlsFailure,
            )
        } catch (e: Exception) {
            RpcProbeResult(
                success = false,
                elapsedMs = System.currentTimeMillis() - started,
                error = e.message ?: e::class.simpleName ?: "Error",
                serverCertPem = captured?.pem,
                certFingerprint = captured?.fingerprint,
                tlsHandshakeFailed = e is SSLHandshakeException,
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

            val builder =
                httpClientForUrl(cfg.url)
                    .newBuilder()
                    .followRedirects(false)
                    .connectTimeout(cfg.timeoutMs, TimeUnit.MILLISECONDS)
                    .readTimeout(cfg.timeoutMs, TimeUnit.MILLISECONDS)
                    .callTimeout(cfg.timeoutMs, TimeUnit.MILLISECONDS)
            if (cfg.usePinnedTrustStore && cfg.url.startsWith("https://")) {
                // Route this call through the pinned trust store + a permissive
                // hostname verifier. We've already vouched for the cert by
                // pinning it, and Namecoin Core LAN/onion deployments
                // commonly present certs whose SAN doesn't match the URL
                // the user typed (e.g. IP-in-SAN vs hostname).
                val tm = pinnedTrustManager()
                builder.sslSocketFactory(cachedPinnedSslFactory(), tm)
                builder.hostnameVerifier { _, _ -> true }
            }
            val client = builder.build()

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

    // ── TLS pinning helpers ────────────────────────────────────────────────

    private data class CapturedLeafCert(
        val pem: String,
        val fingerprint: String,
    )

    /**
     * Open a short-lived TLS connection to [cfg]'s host:port and return
     * the server's leaf certificate as PEM + SHA-256 fingerprint.
     *
     * Used by [probe] so the Settings UI can show the certificate the
     * server presented — even if the subsequent RPC call rejects it.
     * Done via a separate socket (no HTTP, no Authorization header) so:
     *   1. We never send credentials over an untrusted connection.
     *   2. The capture works regardless of whether the OkHttp request
     *      below would have failed TLS verification.
     *
     * Returns null for non-https URLs, or when the TCP/TLS connection
     * itself fails (server down, port closed, etc.).
     */
    private fun captureLeafCert(cfg: NamecoinCoreRpcConfig): CapturedLeafCert? {
        if (!cfg.url.startsWith("https://")) return null
        val uri =
            try {
                URI(cfg.url)
            } catch (_: Exception) {
                return null
            }
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443

        // Use a trust-all SSLContext just for the capture handshake.
        // We're not making any trust decision here — we're inspecting.
        // The actual call below applies the user's pinning policy.
        val trustAll =
            arrayOf<javax.net.ssl.TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(
                        chain: Array<X509Certificate>,
                        authType: String,
                    ) {}

                    override fun checkServerTrusted(
                        chain: Array<X509Certificate>,
                        authType: String,
                    ) {}

                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                },
            )
        val ctx =
            try {
                SSLContext.getInstance("TLSv1.2")
            } catch (_: Exception) {
                SSLContext.getInstance("TLS")
            }
        ctx.init(null, trustAll, SecureRandom())
        val factory = ctx.socketFactory

        return try {
            // Bound the TCP connect with the configured timeout. The
            // capture is best-effort; never let it stall the probe.
            val raw = Socket()
            raw.connect(
                java.net.InetSocketAddress(host, port),
                cfg.timeoutMs.coerceAtMost(10_000L).toInt(),
            )
            raw.soTimeout = cfg.timeoutMs.coerceAtMost(10_000L).toInt()
            val ssl = factory.createSocket(raw, host, port, true) as SSLSocket
            ssl.useClientMode = true
            try {
                ssl.startHandshake()
                val peerCerts = ssl.session.peerCertificates
                if (peerCerts.isEmpty() || peerCerts[0] !is X509Certificate) return null
                val x509 = peerCerts[0] as X509Certificate
                val encoded =
                    Base64.getMimeEncoder(76, "\n".toByteArray()).encodeToString(x509.encoded)
                val pem = "-----BEGIN CERTIFICATE-----\n$encoded\n-----END CERTIFICATE-----\n"
                val digest = MessageDigest.getInstance("SHA-256").digest(x509.encoded)
                val fp = digest.joinToString(":") { "%02X".format(it) }
                CapturedLeafCert(pem = pem, fingerprint = fp)
            } finally {
                runCatching { ssl.close() }
                runCatching { raw.close() }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun cachedPinnedSslFactory(): SSLSocketFactory {
        pinnedFactory?.let { return it }
        synchronized(this) {
            pinnedFactory?.let { return it }
            return buildPinnedSslFactory().also { pinnedFactory = it }
        }
    }

    /**
     * Build an [SSLSocketFactory] that trusts user-pinned certificates
     * plus the system CA store. Same shape as the ElectrumX pinned
     * factory, minus the hardcoded list (Namecoin Core RPC endpoints
     * are 100% user-configured — there are no public defaults to ship).
     */
    private fun buildPinnedSslFactory(): SSLSocketFactory {
        val ks =
            try {
                KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
            } catch (_: Exception) {
                KeyStore.getInstance("PKCS12").apply { load(null, null) }
            }

        val cf = CertificateFactory.getInstance("X.509")
        val pems = synchronized(this) { dynamicCerts.toList() }
        for ((index, pem) in pems.withIndex()) {
            try {
                val cert = cf.generateCertificate(ByteArrayInputStream(pem.toByteArray(Charsets.US_ASCII)))
                ks.setCertificateEntry("namecoin_rpc_$index", cert)
            } catch (_: Exception) {
                // Skip malformed certs — the rest may still work.
            }
        }

        val systemTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        systemTmf.init(null as KeyStore?)
        val systemTm = systemTmf.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
        if (systemTm != null) {
            for ((index, issuer) in systemTm.acceptedIssuers.withIndex()) {
                try {
                    ks.setCertificateEntry("system_$index", issuer)
                } catch (_: Exception) {
                    // Some OEMs reject re-inserts; skip.
                }
            }
        }

        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(ks)
        val sslContext =
            try {
                SSLContext.getInstance("TLSv1.2")
            } catch (_: Exception) {
                SSLContext.getInstance("TLS")
            }
        sslContext.init(null, tmf.trustManagers, SecureRandom())
        return sslContext.socketFactory
    }

    /** Return an [X509TrustManager] backed by the same pinned keystore. */
    private fun pinnedTrustManager(): X509TrustManager {
        val ks =
            try {
                KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
            } catch (_: Exception) {
                KeyStore.getInstance("PKCS12").apply { load(null, null) }
            }
        val cf = CertificateFactory.getInstance("X.509")
        val pems = synchronized(this) { dynamicCerts.toList() }
        for ((index, pem) in pems.withIndex()) {
            try {
                val cert = cf.generateCertificate(ByteArrayInputStream(pem.toByteArray(Charsets.US_ASCII)))
                ks.setCertificateEntry("namecoin_rpc_$index", cert)
            } catch (_: Exception) {
                // skip
            }
        }
        val systemTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        systemTmf.init(null as KeyStore?)
        val systemTm = systemTmf.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
        if (systemTm != null) {
            for ((index, issuer) in systemTm.acceptedIssuers.withIndex()) {
                try {
                    ks.setCertificateEntry("system_$index", issuer)
                } catch (_: Exception) {
                    // skip
                }
            }
        }
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(ks)
        return tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
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
    /**
     * PEM-encoded server leaf certificate captured during the probe.
     * Only populated when the URL scheme is https and the TLS handshake
     * succeeded (even if the subsequent HTTP request failed). Used by
     * the Settings UI to prompt for a TOFU pin.
     */
    val serverCertPem: String? = null,
    /**
     * SHA-256 fingerprint of the captured leaf certificate, formatted
     * as colon-separated uppercase hex bytes (matches
     * [ServerTestResult.certFingerprint] for ElectrumX).
     */
    val certFingerprint: String? = null,
    /**
     * True when the probe failed specifically because the TLS handshake
     * was rejected (self-signed cert, untrusted CA, hostname mismatch).
     * Used by the Settings UI to suggest a TOFU pin as the fix.
     */
    val tlsHandshakeFailed: Boolean = false,
)
