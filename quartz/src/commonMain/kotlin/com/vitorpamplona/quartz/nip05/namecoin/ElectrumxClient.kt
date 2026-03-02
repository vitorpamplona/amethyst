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
package com.vitorpamplona.quartz.nip05.namecoin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import javax.net.SocketFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Result of an ElectrumX name_show query.
 *
 * Maps to the JSON fields returned by Namecoin Core / Electrum-NMC:
 *   { "name": "d/example", "value": "{...}", "txid": "abc...", "height": 12345, ... }
 */
@Serializable
data class NameShowResult(
    val name: String,
    val value: String,
    val txid: String? = null,
    val height: Int? = null,
    val expiresIn: Int? = null,
)

/**
 * Represents a single ElectrumX server endpoint.
 */
data class ElectrumxServer(
    val host: String,
    val port: Int,
    val useSsl: Boolean = true,
    /** If true, accept any certificate (self-signed, expired, etc.) */
    val trustAllCerts: Boolean = false,
)

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
class ElectrumxClient(
    private val connectTimeoutMs: Long = 10_000L,
    private val readTimeoutMs: Long = 15_000L,
    private val socketFactory: () -> SocketFactory = { SocketFactory.getDefault() },
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    private val requestId = AtomicInteger(0)
    private val mutex = Mutex()

    companion object {
        /** Well-known public Namecoin ElectrumX servers (clearnet). */
        val DEFAULT_SERVERS =
            listOf(
                ElectrumxServer("electrumx.testls.space", 50002, useSsl = true, trustAllCerts = true),
                ElectrumxServer("ulrichard.ch", 50006, useSsl = true),
                ElectrumxServer("nmc2.lelux.fi", 50006, useSsl = true),
            )

        /** Tor-preferred server list: onion primary, clearnet fallback. */
        val TOR_SERVERS =
            listOf(
                ElectrumxServer(
                    "i665jpwsq46zlsdbnj4axgzd3s56uzey5uhotsnxzsknzbn36jaddsid.onion",
                    50002,
                    useSsl = true,
                    trustAllCerts = true,
                ),
                ElectrumxServer("electrumx.testls.space", 50002, useSsl = true, trustAllCerts = true),
            )

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
    }

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
        server: ElectrumxServer = DEFAULT_SERVERS.first(),
    ): NameShowResult? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    connectAndQuery(identifier, server)
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
     * @param servers    Ordered server list to try; defaults to [DEFAULT_SERVERS]
     */
    suspend fun nameShowWithFallback(
        identifier: String,
        servers: List<ElectrumxServer> = DEFAULT_SERVERS,
    ): NameShowResult? {
        for (server in servers) {
            val result = nameShow(identifier, server)
            if (result != null) return result
        }
        return null
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
            if (historyEntries.isEmpty()) return null

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
                    return null // Name has expired
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
        return digest.reversedArray().joinToString("") { "%02x".format(it) }
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
        if (error != null && error !is kotlinx.serialization.json.JsonNull) return null

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
        if (error != null && error !is kotlinx.serialization.json.JsonNull) return null

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
        val sslFactory =
            if (server.trustAllCerts) {
                trustAllSslFactory()
            } else {
                SSLSocketFactory.getDefault() as SSLSocketFactory
            }
        return sslFactory.createSocket(baseSocket, server.host, server.port, true)
    }

    /**
     * Create an SSLSocketFactory that accepts any certificate.
     * Used for servers with self-signed certificates.
     */
    private fun trustAllSslFactory(): SSLSocketFactory {
        val trustAllCerts =
            arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(
                        chain: Array<java.security.cert.X509Certificate>,
                        authType: String,
                    ) {}

                    override fun checkServerTrusted(
                        chain: Array<java.security.cert.X509Certificate>,
                        authType: String,
                    ) {}

                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                },
            )
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        return sslContext.socketFactory
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
                        kotlinx.serialization.builtins.ListSerializer(
                            kotlinx.serialization.json.JsonElement
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
