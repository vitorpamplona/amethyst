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

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.net.SocketFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Lightweight, query-only ElectrumX client for Namecoin name resolution.
 *
 * Connects over TCP/TLS to a Namecoin ElectrumX server and resolves
 * Namecoin names to their current values using the standard Electrum
 * protocol (scripthash-based lookups). Works with both the Namecoin
 * ElectrumX fork and stock ElectrumX pointed at a Namecoin node, as
 * long as the server has a name index.
 *
 * Resolution strategy:
 * 1. Build a canonical name index script for the identifier
 * 2. Compute the Electrum-style scripthash (reversed SHA-256)
 * 3. Query `blockchain.scripthash.get_history` to find the latest tx
 * 4. Fetch the raw transaction and parse the name value from the script
 *
 * Usage:
 * ```
 * val client = ElectrumxClient()
 * val result = client.nameShow("d/example", server)
 * ```
 */
class ElectrumXClient(
    private val connectTimeoutMs: Long = 10_000L,
    private val readTimeoutMs: Long = 15_000L,
    private val socketFactory: () -> SocketFactory = { SocketFactory.getDefault() },
) : IElectrumXClient {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    private val requestId = AtomicInteger(0)
    private val serverMutexes = ConcurrentHashMap<String, Mutex>()

    /**
     * Perform a name_show lookup against the given ElectrumX server.
     *
     * Uses the scripthash-based approach: computes the name's canonical
     * index script hash, queries transaction history, and parses the
     * name value from the latest transaction's output script.
     *
     * @param identifier Full Namecoin name, e.g. "d/example" or "id/alice"
     * @param server     ElectrumX server to query
     * @return [NameShowResult] on success, null if the name does not exist
     *         or the server is unreachable
     */
    suspend fun nameShow(
        identifier: String,
        server: ElectrumxServer = DEFAULT_ELECTRUMX_SERVERS.first(),
    ): NameShowResult? =
        withContext(Dispatchers.IO) {
            val mutex = serverMutexes.getOrPut("${server.host}:${server.port}") { Mutex() }
            mutex.withLock {
                try {
                    connectAndQuery(identifier, server)
                } catch (e: NamecoinLookupException) {
                    // Propagate name-not-found and expired — these are definitive answers.
                    throw e
                } catch (e: Exception) {
                    // Log but don't crash — callers handle null gracefully.
                    e.printStackTrace()
                    null
                }
            }
        }

    /**
     * Try each server in order until one succeeds.
     *
     * @param identifier Full Namecoin name, e.g. "d/example"
     * @param servers    Ordered server list to try; defaults to [DEFAULT_ELECTRUMX_SERVERS]
     * @throws NamecoinLookupException.NameNotFound if the name definitively doesn't exist
     * @throws NamecoinLookupException.NameExpired if the name has expired
     * @throws NamecoinLookupException.ServersUnreachable if all servers failed with connection errors
     */
    override suspend fun nameShowWithFallback(
        identifier: String,
        servers: List<ElectrumxServer>,
    ): NameShowResult? {
        var lastError: Exception? = null
        for (server in servers) {
            try {
                val result = nameShow(identifier, server)
                if (result != null) return result
            } catch (e: NamecoinLookupException.NameNotFound) {
                throw e // Definitive answer from blockchain — no point trying other servers
            } catch (e: NamecoinLookupException.NameExpired) {
                throw e // Definitive answer
            } catch (e: Exception) {
                lastError = e // Server error — try next server
            }
        }
        throw NamecoinLookupException.ServersUnreachable(lastError)
    }

    // ── internals ──────────────────────────────────────────────────────

    private fun connectAndQuery(
        identifier: String,
        server: ElectrumxServer,
    ): NameShowResult? {
        val socket = createSocket(server)
        socket.soTimeout = readTimeoutMs.toInt()
        val writer = PrintWriter(socket.getOutputStream(), true)
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

        try {
            // 1. Negotiate protocol version
            val versionReq = buildRpcRequest("server.version", listOf("AmethystNMC/0.1", PROTOCOL_VERSION))
            writer.println(versionReq)
            reader.readLine() // consume version response

            // 2. Compute the canonical name index scripthash
            val nameScript = buildNameIndexScript(identifier.toByteArray(Charsets.US_ASCII))
            val scriptHash = electrumScriptHash(nameScript)

            // 3. Get transaction history for this name
            val historyReq = buildRpcRequest("blockchain.scripthash.get_history", listOf(scriptHash))
            writer.println(historyReq)
            val historyResponse = reader.readLine() ?: return null
            val historyEntries = parseHistoryResponse(historyResponse) ?: return null
            if (historyEntries.isEmpty()) throw NamecoinLookupException.NameNotFound(identifier)

            // 4. Get the latest transaction (last entry = most recent update)
            val latestEntry = historyEntries.last()
            val txHash = latestEntry.first
            val height = latestEntry.second

            val txReq = buildRpcRequest("blockchain.transaction.get", listOf(txHash, true))
            writer.println(txReq)
            val txResponse = reader.readLine() ?: return null

            // 5. Get current block height to check name expiry
            val headersReq = buildRpcRequest("blockchain.headers.subscribe", emptyList<String>())
            writer.println(headersReq)
            val headersResponse = reader.readLine()
            val currentHeight = parseBlockHeight(headersResponse)

            // 6. Check if the name has expired
            if (currentHeight != null && height > 0) {
                val blocksSinceUpdate = currentHeight - height
                if (blocksSinceUpdate >= NAME_EXPIRE_DEPTH) {
                    throw NamecoinLookupException.NameExpired(identifier)
                }
            }

            // 7. Parse the name value from the transaction
            val result = parseNameFromTransaction(identifier, txHash, height, txResponse)
            // Populate expiresIn if we know the current height
            return if (result != null && currentHeight != null && height > 0) {
                result.copy(expiresIn = NAME_EXPIRE_DEPTH - (currentHeight - height))
            } else {
                result
            }
        } finally {
            runCatching { writer.close() }
            runCatching { reader.close() }
            runCatching { socket.close() }
        }
    }

    /**
     * Build the canonical script used by ElectrumX to index Namecoin names.
     *
     * Format: OP_NAME_UPDATE <push(name)> <push(empty)> OP_2DROP OP_DROP OP_RETURN
     *
     * This matches the `build_name_index_script` method in the Namecoin
     * ElectrumX fork (electrumx/lib/coins.py).
     */
    private fun buildNameIndexScript(nameBytes: ByteArray): ByteArray {
        val result = mutableListOf<Byte>()
        result.add(OP_NAME_UPDATE)
        result.addAll(pushData(nameBytes).toList())
        result.addAll(pushData(byteArrayOf()).toList()) // empty value
        result.add(OP_2DROP)
        result.add(OP_DROP)
        result.add(OP_RETURN)
        return result.toByteArray()
    }

    /**
     * Bitcoin-style push data encoding.
     */
    private fun pushData(data: ByteArray): ByteArray {
        val len = data.size
        return when {
            len < 0x4c -> {
                byteArrayOf(len.toByte()) + data
            }

            len <= 0xff -> {
                byteArrayOf(OP_PUSHDATA1, len.toByte()) + data
            }

            else -> {
                val lenBytes = byteArrayOf((len and 0xff).toByte(), ((len shr 8) and 0xff).toByte())
                byteArrayOf(OP_PUSHDATA2) + lenBytes + data
            }
        }
    }

    /**
     * Electrum protocol scripthash: SHA-256 of the script, byte-reversed, hex-encoded.
     */
    private fun electrumScriptHash(script: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(script)
        return digest.reversedArray().toHexKey()
    }

    /**
     * Parse the block height from a `blockchain.headers.subscribe` response.
     *
     * Response format: {"result": {"height": 814300, "hex": "..."}, ...}
     */
    private fun parseBlockHeight(raw: String?): Int? {
        if (raw == null) return null
        return try {
            val envelope = json.parseToJsonElement(raw).jsonObject
            val result = envelope["result"]?.jsonObject ?: return null
            result["height"]?.jsonPrimitive?.int
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parse the history response into a list of (txHash, height) pairs.
     */
    private fun parseHistoryResponse(raw: String): List<Pair<String, Int>>? {
        val envelope = json.parseToJsonElement(raw).jsonObject
        val error = envelope["error"]
        if (error != null && error !is JsonNull) return null

        val result = envelope["result"]?.jsonArray ?: return null
        return result.mapNotNull { entry ->
            val obj = entry.jsonObject
            val txHash = obj["tx_hash"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val height = obj["height"]?.jsonPrimitive?.int ?: return@mapNotNull null
            txHash to height
        }
    }

    /**
     * Parse a Namecoin name and value from a verbose transaction response.
     *
     * Scans each output for a NAME_UPDATE script (starts with OP_3 = 0x53),
     * then extracts the name and value from the script's push data.
     */
    private fun parseNameFromTransaction(
        identifier: String,
        txHash: String,
        height: Int,
        raw: String,
    ): NameShowResult? {
        val envelope = json.parseToJsonElement(raw).jsonObject
        val error = envelope["error"]
        if (error != null && error !is JsonNull) return null

        val result = envelope["result"]?.jsonObject ?: return null
        val vouts = result["vout"]?.jsonArray ?: return null

        for (vout in vouts) {
            val scriptHex =
                vout.jsonObject["scriptPubKey"]
                    ?.jsonObject
                    ?.get("hex")
                    ?.jsonPrimitive
                    ?.content
                    ?: continue

            // NAME_UPDATE scripts start with OP_3 (0x53)
            if (!scriptHex.startsWith("53")) continue

            val scriptBytes = hexToBytes(scriptHex)
            val parsed = parseNameScript(scriptBytes) ?: continue

            // Verify this is the name we're looking for
            if (parsed.first == identifier) {
                return NameShowResult(
                    name = parsed.first,
                    value = parsed.second,
                    txid = txHash,
                    height = height,
                )
            }
        }

        return null
    }

    /**
     * Parse a NAME_UPDATE script to extract the name and value.
     *
     * Script format: OP_NAME_UPDATE <push(name)> <push(value)> OP_2DROP OP_DROP <address_script>
     *
     * @return Pair of (name, value) as strings, or null if parsing fails
     */
    private fun parseNameScript(script: ByteArray): Pair<String, String>? {
        if (script.isEmpty() || script[0] != OP_NAME_UPDATE) return null

        var pos = 1

        // Read name
        val (nameBytes, newPos1) = readPushData(script, pos) ?: return null
        pos = newPos1

        // Read value
        val (valueBytes, _) = readPushData(script, pos) ?: return null

        val name = String(nameBytes, Charsets.US_ASCII)
        val value = String(valueBytes, Charsets.UTF_8)
        return name to value
    }

    /**
     * Read a push-data encoded byte sequence from the script at the given position.
     *
     * @return Pair of (data, nextPosition), or null if the script is malformed
     */
    private fun readPushData(
        script: ByteArray,
        pos: Int,
    ): Pair<ByteArray, Int>? {
        if (pos >= script.size) return null

        val opcode = script[pos].toInt() and 0xff
        return when {
            opcode == 0 -> {
                // OP_0 / push empty
                byteArrayOf() to (pos + 1)
            }

            opcode < 0x4c -> {
                // Direct push: opcode is the length
                val end = pos + 1 + opcode
                if (end > script.size) return null
                script.copyOfRange(pos + 1, end) to end
            }

            opcode == 0x4c -> {
                // OP_PUSHDATA1: next byte is length
                if (pos + 2 > script.size) return null
                val len = script[pos + 1].toInt() and 0xff
                val end = pos + 2 + len
                if (end > script.size) return null
                script.copyOfRange(pos + 2, end) to end
            }

            opcode == 0x4d -> {
                // OP_PUSHDATA2: next 2 bytes are length (little-endian)
                if (pos + 3 > script.size) return null
                val len =
                    (script[pos + 1].toInt() and 0xff) or
                        ((script[pos + 2].toInt() and 0xff) shl 8)
                val end = pos + 3 + len
                if (end > script.size) return null
                script.copyOfRange(pos + 3, end) to end
            }

            else -> {
                null
            }
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] =
                (
                    (Character.digit(hex[i], 16) shl 4) +
                        Character.digit(hex[i + 1], 16)
                ).toByte()
        }
        return data
    }

    private fun createSocket(server: ElectrumxServer): Socket {
        // Create the base socket through the injected factory, which
        // may route through a SOCKS proxy (e.g. Tor) if configured.
        val baseSocket =
            socketFactory().createSocket().apply {
                connect(InetSocketAddress(server.host, server.port), connectTimeoutMs.toInt())
            }

        if (!server.useSsl) return baseSocket

        // Upgrade to TLS over the already-connected (possibly proxied) socket.
        // When the server uses a self-signed certificate (trustAllCerts flag),
        // we use a pinned trust store that contains the known ElectrumX server
        // certs. This is required because Samsung One UI 7 (Android 16) silently
        // rejects connections that use a no-op "trust-all" X509TrustManager.
        val sslFactory =
            if (server.trustAllCerts) {
                cachedPinnedSslFactory()
            } else {
                SSLSocketFactory.getDefault() as SSLSocketFactory
            }
        val sslSocket = sslFactory.createSocket(baseSocket, server.host, server.port, true)

        // Enforce TLSv1.2+ — some OEM Conscrypt forks (Xiaomi MIUI, OnePlus ColorOS)
        // may negotiate TLS 1.0/1.1 by default for raw socket upgrades.
        if (sslSocket is javax.net.ssl.SSLSocket) {
            val supported = sslSocket.supportedProtocols
            val modern = supported.filter { it == "TLSv1.2" || it == "TLSv1.3" }
            if (modern.isNotEmpty()) {
                sslSocket.enabledProtocols = modern.toTypedArray()
            }
        }

        return sslSocket
    }

    /** Lazy-cached SSLSocketFactory for pinned certs. Thread-safe via volatile + DCL. */
    @Volatile
    private var pinnedFactory: SSLSocketFactory? = null

    private fun cachedPinnedSslFactory(): SSLSocketFactory {
        pinnedFactory?.let { return it }
        synchronized(this) {
            pinnedFactory?.let { return it }
            return buildPinnedSslFactory().also { pinnedFactory = it }
        }
    }

    /**
     * Build an SSLSocketFactory that trusts the pinned ElectrumX server
     * certificates plus the system CA store.
     *
     * Previous versions used a "trust-all" TrustManager, but Samsung
     * devices running One UI 7 (Android 16) silently reject connections
     * that use a no-op X509TrustManager. Pinning the known self-signed
     * certs avoids this while maintaining security.
     *
     * Also handles OEM-specific quirks:
     * - Xiaomi MIUI/HyperOS: KeyStore.getDefaultType() may return unexpected
     *   types; we try the default first, then fall back to "PKCS12".
     * - OnePlus ColorOS: some versions require explicit TLSv1.2 protocol.
     * - All OEMs: SSLContext("TLSv1.2") is preferred over ("TLS") which may
     *   resolve to TLS 1.0 on older Conscrypt forks.
     */
    private fun buildPinnedSslFactory(): SSLSocketFactory {
        val ks =
            try {
                KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
            } catch (_: Exception) {
                // Fallback for Xiaomi devices where getDefaultType() returns an unsupported type
                KeyStore.getInstance("PKCS12").apply { load(null, null) }
            }

        val cf = CertificateFactory.getInstance("X.509")

        // Load each pinned certificate into the keystore
        for ((index, pem) in PINNED_ELECTRUMX_CERTS.withIndex()) {
            try {
                val cert = cf.generateCertificate(ByteArrayInputStream(pem.toByteArray(Charsets.US_ASCII)))
                ks.setCertificateEntry("electrumx_$index", cert)
            } catch (_: Exception) {
                // Skip malformed certs — the remaining ones may still work
            }
        }

        // Also load system CA certificates so that servers with real certs work too
        val systemTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        systemTmf.init(null as KeyStore?) // null = system default
        val systemTm = systemTmf.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
        if (systemTm != null) {
            for ((index, issuer) in systemTm.acceptedIssuers.withIndex()) {
                try {
                    ks.setCertificateEntry("system_$index", issuer)
                } catch (_: Exception) {
                    // Some OEMs return certs that can't be re-inserted; skip
                }
            }
        }

        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(ks)

        // Prefer TLSv1.2 explicitly — SSLContext.getInstance("TLS") can resolve
        // to TLS 1.0 on some OEM Conscrypt forks (Xiaomi, OnePlus).
        val sslContext =
            try {
                SSLContext.getInstance("TLSv1.2")
            } catch (_: Exception) {
                SSLContext.getInstance("TLS")
            }
        sslContext.init(null, tmf.trustManagers, SecureRandom())
        return sslContext.socketFactory
    }

    companion object {
        private const val PROTOCOL_VERSION = "1.4"

        /**
         * Namecoin names expire this many blocks after their last update.
         * From chainparams.cpp: consensus.nNameExpirationDepth = 36000
         * (~250 days at ~10 min/block).
         */
        const val NAME_EXPIRE_DEPTH = 36_000

        // Namecoin script opcodes
        private const val OP_NAME_UPDATE: Byte = 0x53 // OP_3 repurposed by Namecoin
        private const val OP_2DROP: Byte = 0x6d
        private const val OP_DROP: Byte = 0x75
        private const val OP_RETURN: Byte = 0x6a
        private const val OP_PUSHDATA1: Byte = 0x4c
        private const val OP_PUSHDATA2: Byte = 0x4d

        /**
         * PEM-encoded certificates for the well-known Namecoin ElectrumX servers.
         *
         * These are self-signed certificates that cannot be verified by the
         * system CA store. We pin them explicitly so that connections succeed
         * on devices with strict TLS enforcement (e.g. Samsung One UI 7).
         *
         * To update: `echo | openssl s_client -connect HOST:PORT 2>/dev/null | openssl x509 -outform PEM`
         */
        private val PINNED_ELECTRUMX_CERTS =
            listOf(
                // electrumx.testls.space:50002 — expires 2027-05-04
                """
-----BEGIN CERTIFICATE-----
MIIDwzCCAqsCFGGKT5mjh7oN98aNyjOCiqafL8VyMA0GCSqGSIb3DQEBCwUAMIGd
MQswCQYDVQQGEwJVUzEQMA4GA1UECAwHQ2hpY2FnbzEQMA4GA1UEBwwHQ2hpY2Fn
bzESMBAGA1UECgwJSW50ZXJuZXRzMQ8wDQYDVQQLDAZJbnRlcncxHjAcBgNVBAMM
FWVsZWN0cnVtLnRlc3Rscy5zcGFjZTElMCMGCSqGSIb3DQEJARYWbWpfZ2lsbF84
OUBob3RtYWlsLmNvbTAeFw0yMjA1MDUwNjIzNDFaFw0yNzA1MDQwNjIzNDFaMIGd
MQswCQYDVQQGEwJVUzEQMA4GA1UECAwHQ2hpY2FnbzEQMA4GA1UEBwwHQ2hpY2Fn
bzESMBAGA1UECgwJSW50ZXJuZXRzMQ8wDQYDVQQLDAZJbnRlcncxHjAcBgNVBAMM
FWVsZWN0cnVtLnRlc3Rscy5zcGFjZTElMCMGCSqGSIb3DQEJARYWbWpfZ2lsbF84
OUBob3RtYWlsLmNvbTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAO4H
+PKCdiiz3jNOA77aAmS2YaU7eOQ8ZGliEVr/PlLcgF5gmthb2DI6iK4KhC1ad34G
1n9IhkXPhkVJ94i8wB3uoTBlA7mI5h59m01yhzSkJAoYoU/i6DM9ipbakqWFCTEp
P+yE216NTU5MbYwThZdRSAIIABe9RyIliMSidyrwHvKBLfnJPFScghW6rhBWN7PG
PA8k0MFGzf+HXbpnV/jAvz08ZC34qiBIjkJrTgh49JweyoZKdppyJcH4UbkslJ2t
YUJR3oURBvrPj+D7TwLVRbX36ul7r4+dP3IjgmljsSAHDK4N/PfWrCBdlj9Pc1Cp
yX+ZDh8X2NrL4ukHoVMCAwEAATANBgkqhkiG9w0BAQsFAAOCAQEAeVj6VZNmY/Vb
nhzrC7xBSHqVWQ1wkLOClLsdvgKP8cFFJuUoCMQU5bPMi7nWnkfvvsIKH4Eibk5K
fqiA9jVsY0FHvQ8gP3KMk1LVuUf/sTcRe5itp3guBOSk/zXZUD5tUz/oRk3k+rdc
MsInqhomjNy/dqYmD6Wm4DNPjZh6fWy+AVQKVNOI2t4koaVdpoi8Uv8h4gFGPbdI
sVmtoGiIGkKNIWum+6mnF6PfynNrLk+ztH4TrdacVNeoJUPYEAxOuesWXFy3H4r+
HKBqA4xAzyjgKLPqoWnjSu7gxj1GIjBhnDxkM6wUOnDq8A0EqxR+A17OcXW9sZ2O
2ZIVwmtnyA==
-----END CERTIFICATE-----
                """.trimIndent(),
                // nmc2.bitcoins.sk:57002 / 46.229.238.187:57002 — expires 2030-10-22
                """
-----BEGIN CERTIFICATE-----
MIID+TCCAuGgAwIBAgIUdmJGukmfPvqmAYpTfuGcjRoYHJ8wDQYJKoZIhvcNAQEL
BQAwgYsxCzAJBgNVBAYTAlNLMREwDwYDVQQIDAhTbG92YWtpYTETMBEGA1UEBwwK
QnJhdGlzbGF2YTEUMBIGA1UECgwLYml0Y29pbnMuc2sxGTAXBgNVBAMMEG5tYzIu
Yml0Y29pbnMuc2sxIzAhBgkqhkiG9w0BCQEWFGRlYWZib3lAY2ljb2xpbmEub3Jn
MB4XDTIwMTAyNDE5MjQzOVoXDTMwMTAyMjE5MjQzOVowgYsxCzAJBgNVBAYTAlNL
MREwDwYDVQQIDAhTbG92YWtpYTETMBEGA1UEBwwKQnJhdGlzbGF2YTEUMBIGA1UE
CgwLYml0Y29pbnMuc2sxGTAXBgNVBAMMEG5tYzIuYml0Y29pbnMuc2sxIzAhBgkq
hkiG9w0BCQEWFGRlYWZib3lAY2ljb2xpbmEub3JnMIIBIjANBgkqhkiG9w0BAQEF
AAOCAQ8AMIIBCgKCAQEAzBUkZNDfaz7kc28l5tDKohJjekWmz1ynzfGx3ZLsqOZE
c+kNfcMaWU+zT/j0mV6pX6KSH7G9pPAku+8PRdKRq+d63wiJDEjGSaFztQWKW6L1
vTxgCK5gu+Eir3BkTagJObsrLKS+T6qH610/3+btGgoR3lunB5TzCgB/9oQanjDW
zjg2CwmxgR5Iw1Eqfenx7zkSK33FSXSF2SvbUs1Atj2oPU4DLivyrx0RaUmaPemn
cmcpnax+py4pQeB6dJWU1INhzXt3hTJRyoqsSGY3vCECIKIBIkh8GsYjAX4z+Y9y
6pJx0da2b88qPWdsoxaIMvrQiuWknDrSJwAyw2Yd8QIDAQABo1MwUTAdBgNVHQ4E
FgQUT2J83B2/9jxGGdFeWrxMohTzHNwwHwYDVR0jBBgwFoAUT2J83B2/9jxGGdFe
WrxMohTzHNwwDwYDVR0TAQH/BAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAsbxX
wN8tZaXOybImMZCQS7zfxmKl2IAcqu+R01KPfnIfrFqXPsGDDl3rYLkwh1O4/hYQ
NKNW9KTxoJxuBmAkm7EXQQh1XUUzajdEDqDBVRyvR0Z2MdMYnMSAiiMXMl2wUZnc
QXYftBo0HbtfsaJjImQdDjmlmRPSzE/RW6iUe+1cesKBC7e8nVf69Yu/fxO4m083
VWwAstlWJfk1GyU7jzVc8svealg/oIiDoOMe6CFSLx1BDv2FeHSpRdqd3fn+AC73
bK2N2smrHUOQnFijuiFw3WOrjERi0eMhjVNfVu9W9ZYa/Wd6SdIzV55LbG+NpmSf
5W7ix41hRvdT6cTAJA==
-----END CERTIFICATE-----
                """.trimIndent(),
            )
    }

    private fun buildRpcRequest(
        method: String,
        params: List<Any>,
    ): String {
        val id = requestId.incrementAndGet()
        val obj =
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", id)
                put("method", method)
                put(
                    "params",
                    json.encodeToJsonElement(
                        ListSerializer(
                            JsonElement
                                .serializer(),
                        ),
                        params.map {
                            when (it) {
                                is Boolean -> JsonPrimitive(it)
                                is Number -> JsonPrimitive(it)
                                else -> JsonPrimitive(it.toString())
                            }
                        },
                    ),
                )
            }
        return json.encodeToString(JsonObject.serializer(), obj)
    }
}
