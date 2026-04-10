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

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
import platform.posix.AF_INET
import platform.posix.IPPROTO_TCP
import platform.posix.SOCK_STREAM
import platform.posix.close
import platform.posix.connect
import platform.posix.errno
import platform.posix.gethostbyname
import platform.posix.memcpy
import platform.posix.recv
import platform.posix.send
import platform.posix.sockaddr_in
import platform.posix.socket
import kotlin.concurrent.AtomicInt

/**
 * iOS implementation of the ElectrumX client for Namecoin name resolution.
 *
 * Uses POSIX sockets for TCP connections. TLS is not yet implemented
 * on the iOS side — connections default to plaintext for now.
 * This matches the core ElectrumX protocol (JSON-RPC over TCP).
 *
 * For production use with TLS-only servers, a future version should
 * integrate Apple's SecureTransport or Network.framework.
 *
 * Port of the JVM ElectrumXClient replacing:
 * - java.net.Socket → POSIX socket()
 * - java.security.MessageDigest → CommonCrypto CC_SHA256
 */
@OptIn(ExperimentalForeignApi::class)
class ElectrumXClient(
    private val connectTimeoutMs: Long = 10_000L,
    private val readTimeoutMs: Long = 15_000L,
) : IElectrumXClient {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    private val requestId = AtomicInt(0)
    private val serverMutexes = mutableMapOf<String, Mutex>()
    private val mutexLock = Mutex()

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
                throw e
            } catch (e: NamecoinLookupException.NameExpired) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw NamecoinLookupException.ServersUnreachable(lastError)
    }

    private suspend fun nameShow(
        identifier: String,
        server: ElectrumxServer = DEFAULT_ELECTRUMX_SERVERS.first(),
    ): NameShowResult? =
        withContext(Dispatchers.IO) {
            val key = "${server.host}:${server.port}"
            val mutex =
                mutexLock.withLock {
                    serverMutexes.getOrPut(key) { Mutex() }
                }
            mutex.withLock {
                try {
                    connectAndQuery(identifier, server)
                } catch (e: NamecoinLookupException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }

    // ── Connection & Query ─────────────────────────────────────────────

    private suspend fun connectAndQuery(
        identifier: String,
        server: ElectrumxServer,
    ): NameShowResult? {
        val connection =
            withTimeoutOrNull(connectTimeoutMs) {
                PosixSocketConnection.connect(server.host, server.port, connectTimeoutMs)
            } ?: throw Exception("Connection timed out to ${server.host}:${server.port}")

        try {
            // 1. Negotiate protocol version
            val versionReq = buildRpcRequest("server.version", listOf("AmethystNMC/0.1", PROTOCOL_VERSION))
            connection.writeLine(versionReq)
            connection.readLine() // consume version response

            // 2. Compute the canonical name index scripthash
            val nameScript = buildNameIndexScript(identifier.encodeToByteArray())
            val scriptHash = electrumScriptHash(nameScript)

            // 3. Get transaction history for this name
            val historyReq = buildRpcRequest("blockchain.scripthash.get_history", listOf(scriptHash))
            connection.writeLine(historyReq)
            val historyResponse = connection.readLine() ?: return null
            val historyEntries = parseHistoryResponse(historyResponse) ?: return null
            if (historyEntries.isEmpty()) throw NamecoinLookupException.NameNotFound(identifier)

            // 4. Get the latest transaction
            val latestEntry = historyEntries.last()
            val txHash = latestEntry.first
            val height = latestEntry.second

            val txReq = buildRpcRequest("blockchain.transaction.get", listOf(txHash, true))
            connection.writeLine(txReq)
            val txResponse = connection.readLine() ?: return null

            // 5. Get current block height to check name expiry
            val headersReq = buildRpcRequest("blockchain.headers.subscribe", emptyList<String>())
            connection.writeLine(headersReq)
            val headersResponse = connection.readLine()
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
            return if (result != null && currentHeight != null && height > 0) {
                result.copy(expiresIn = NAME_EXPIRE_DEPTH - (currentHeight - height))
            } else {
                result
            }
        } finally {
            connection.close()
        }
    }

    // ── POSIX socket connection ────────────────────────────────────────

    /**
     * Line-based TCP connection using POSIX sockets.
     *
     * Note: TLS is not yet implemented on iOS. The default ElectrumX
     * servers require TLS — this will need SecureTransport integration
     * for production use with TLS-only servers.
     */
    private class PosixSocketConnection(
        private val fd: Int,
    ) {
        private val readBuffer = StringBuilder()

        fun writeLine(line: String) {
            val data = "$line\n".encodeToByteArray()
            data.usePinned { pinned ->
                var sent = 0
                while (sent < data.size) {
                    val n = send(fd, pinned.addressOf(sent), (data.size - sent).convert(), 0)
                    if (n <= 0) throw Exception("Socket write failed (errno=$errno)")
                    sent += n.toInt()
                }
            }
        }

        fun readLine(): String? {
            val newlineIdx = readBuffer.indexOf('\n')
            if (newlineIdx >= 0) {
                val line = readBuffer.substring(0, newlineIdx)
                readBuffer.deleteRange(0, newlineIdx + 1)
                return line
            }

            val buf = ByteArray(4096)
            while (true) {
                val n =
                    buf.usePinned { pinned ->
                        recv(fd, pinned.addressOf(0), buf.size.convert(), 0)
                    }
                if (n <= 0) {
                    return if (readBuffer.isNotEmpty()) {
                        val remaining = readBuffer.toString()
                        readBuffer.clear()
                        remaining
                    } else {
                        null
                    }
                }

                readBuffer.append(buf.decodeToString(0, n.toInt()))

                val idx = readBuffer.indexOf('\n')
                if (idx >= 0) {
                    val line = readBuffer.substring(0, idx)
                    readBuffer.deleteRange(0, idx + 1)
                    return line
                }
            }
        }

        fun close() {
            close(fd)
        }

        companion object {
            fun connect(
                host: String,
                port: Int,
                timeoutMs: Long,
            ): PosixSocketConnection =
                memScoped {
                    val fd = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP)
                    if (fd < 0) throw Exception("socket() failed (errno=$errno)")

                    try {
                        // Resolve hostname
                        val hostent =
                            gethostbyname(host)
                                ?: throw Exception("DNS resolution failed for $host")

                        val addr = alloc<sockaddr_in>()
                        addr.sin_family = AF_INET.convert()
                        // htons: convert port to network byte order (big-endian)
                        val p = port.toUShort()
                        addr.sin_port = ((p.toInt() shr 8) or ((p.toInt() and 0xff) shl 8)).toUShort()

                        // Copy the resolved address
                        val hostentVal = hostent.pointed
                        val addrList =
                            hostentVal.h_addr_list
                                ?: throw Exception("No addresses found for $host")
                        val firstAddr =
                            addrList[0]
                                ?: throw Exception("No addresses found for $host")
                        memcpy(addr.sin_addr.ptr, firstAddr, sizeOf<platform.posix.in_addr>().convert())

                        // Connect (blocking for simplicity)
                        val result = connect(fd, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert())
                        if (result < 0) {
                            throw Exception("connect() failed to $host:$port (errno=$errno)")
                        }

                        PosixSocketConnection(fd)
                    } catch (e: Exception) {
                        close(fd)
                        throw e
                    }
                }
        }
    }

    // ── Script building & parsing (pure Kotlin, ported from JVM) ──────

    private fun buildNameIndexScript(nameBytes: ByteArray): ByteArray {
        val result = mutableListOf<Byte>()
        result.add(OP_NAME_UPDATE)
        result.addAll(pushData(nameBytes).toList())
        result.addAll(pushData(byteArrayOf()).toList())
        result.add(OP_2DROP)
        result.add(OP_DROP)
        result.add(OP_RETURN)
        return result.toByteArray()
    }

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

    private fun electrumScriptHash(script: ByteArray): String {
        val digest = sha256(script)
        return digest.reversedArray().toHexString()
    }

    private fun sha256(data: ByteArray): ByteArray {
        val digest = ByteArray(32)
        data.usePinned { pinnedData ->
            digest.usePinned { pinnedDigest ->
                platform.CoreCrypto.CC_SHA256(
                    pinnedData.addressOf(0),
                    data.size.convert(),
                    pinnedDigest.addressOf(0).reinterpret(),
                )
            }
        }
        return digest
    }

    private fun ByteArray.toHexString(): String {
        val hexChars = "0123456789abcdef"
        val sb = StringBuilder(size * 2)
        for (byte in this) {
            val v = byte.toInt() and 0xff
            sb.append(hexChars[v shr 4])
            sb.append(hexChars[v and 0x0f])
        }
        return sb.toString()
    }

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

            if (!scriptHex.startsWith("53")) continue

            val scriptBytes = hexToBytes(scriptHex)
            val parsed = parseNameScript(scriptBytes) ?: continue

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

    private fun parseNameScript(script: ByteArray): Pair<String, String>? {
        if (script.isEmpty() || script[0] != OP_NAME_UPDATE) return null
        var pos = 1
        val (nameBytes, newPos1) = readPushData(script, pos) ?: return null
        pos = newPos1
        val (valueBytes, _) = readPushData(script, pos) ?: return null
        return nameBytes.decodeToString() to valueBytes.decodeToString()
    }

    private fun readPushData(
        script: ByteArray,
        pos: Int,
    ): Pair<ByteArray, Int>? {
        if (pos >= script.size) return null
        val opcode = script[pos].toInt() and 0xff
        return when {
            opcode == 0 -> {
                byteArrayOf() to (pos + 1)
            }

            opcode < 0x4c -> {
                val end = pos + 1 + opcode
                if (end > script.size) return null
                script.copyOfRange(pos + 1, end) to end
            }

            opcode == 0x4c -> {
                if (pos + 2 > script.size) return null
                val len = script[pos + 1].toInt() and 0xff
                val end = pos + 2 + len
                if (end > script.size) return null
                script.copyOfRange(pos + 2, end) to end
            }

            opcode == 0x4d -> {
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
                ((charToHexDigit(hex[i]) shl 4) + charToHexDigit(hex[i + 1])).toByte()
        }
        return data
    }

    private fun charToHexDigit(c: Char): Int =
        when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> 0
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
                        kotlinx.serialization.builtins.ListSerializer(JsonElement.serializer()),
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

    companion object {
        private const val PROTOCOL_VERSION = "1.4"
        const val NAME_EXPIRE_DEPTH = 36_000

        private const val OP_NAME_UPDATE: Byte = 0x53
        private const val OP_2DROP: Byte = 0x6d
        private const val OP_DROP: Byte = 0x75
        private const val OP_RETURN: Byte = 0x6a
        private const val OP_PUSHDATA1: Byte = 0x4c
        private const val OP_PUSHDATA2: Byte = 0x4d
    }
}
