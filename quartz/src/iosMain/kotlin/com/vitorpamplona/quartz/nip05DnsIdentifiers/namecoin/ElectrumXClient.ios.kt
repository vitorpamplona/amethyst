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
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
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
import platform.Foundation.NSLog
import platform.Network.NW_CONNECTION_DEFAULT_MESSAGE_CONTEXT
import platform.Network.NW_PARAMETERS_DISABLE_PROTOCOL
import platform.Network.nw_connection_cancel
import platform.Network.nw_connection_create
import platform.Network.nw_connection_receive
import platform.Network.nw_connection_send
import platform.Network.nw_connection_set_queue
import platform.Network.nw_connection_set_state_changed_handler
import platform.Network.nw_connection_start
import platform.Network.nw_endpoint_create_host
import platform.Network.nw_parameters_create_secure_tcp
import platform.Network.nw_tls_copy_sec_protocol_options
import platform.Security.sec_protocol_options_set_verify_block
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.NSObject
import platform.darwin.dispatch_data_apply
import platform.darwin.dispatch_data_create
import platform.darwin.dispatch_data_get_size
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait

/**
 * iOS ElectrumX client using Apple Network.framework for TLS.
 *
 * Uses nw_connection with a custom TLS verify block that accepts all
 * certificates (ElectrumX servers typically use self-signed certs).
 * All async Network.framework calls are made synchronous via dispatch
 * semaphores — this is safe because the caller runs on Dispatchers.IO.
 */
@OptIn(ExperimentalForeignApi::class)
class ElectrumXClient : IElectrumXClient {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private var requestId = 0
    private val serverMutexesMutex = Mutex()
    private val serverMutexes = mutableMapOf<String, Mutex>()

    private var connection: NSObject? = null
    private val queue = dispatch_queue_create("electrumx.nw", null)

    /** Buffer for partial reads (data received but no newline yet). */
    private val readBuffer = StringBuilder()

    // ── public API ─────────────────────────────────────────────────────

    suspend fun nameShow(
        identifier: String,
        server: ElectrumxServer = DEFAULT_ELECTRUMX_SERVERS.first(),
    ): NameShowResult? =
        withContext(Dispatchers.IO) {
            val key = "${server.host}:${server.port}"
            val mutex =
                serverMutexesMutex.withLock {
                    serverMutexes.getOrPut(key) { Mutex() }
                }
            mutex.withLock {
                try {
                    connectAndQuery(identifier, server)
                } catch (e: NamecoinLookupException) {
                    throw e
                } catch (e: Exception) {
                    NSLog("ElectrumXClient: nameShow failed: %@", e.message ?: "unknown")
                    null
                }
            }
        }

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

    // ── Network.framework transport ────────────────────────────────────

    private fun connect(
        host: String,
        port: Int,
    ) {
        readBuffer.clear()

        // Configure TLS to accept all certificates (self-signed ElectrumX servers)
        val configureTls: (NSObject?) -> Unit = { protocolOptions ->
            val secOptions = nw_tls_copy_sec_protocol_options(protocolOptions)
            sec_protocol_options_set_verify_block(
                secOptions,
                { _, _, completion -> completion?.invoke(true) },
                queue,
            )
        }

        val params =
            nw_parameters_create_secure_tcp(
                configureTls,
                NW_PARAMETERS_DISABLE_PROTOCOL,
            )
        val endpoint = nw_endpoint_create_host(host, port.toString())
        val conn = nw_connection_create(endpoint, params)

        val semaphore = dispatch_semaphore_create(0)
        var connectError: String? = null

        nw_connection_set_state_changed_handler(conn) { state, error ->
            @Suppress("ktlint:standard:no-multi-spaces")
            when (state) {
                NW_CONNECTION_STATE_READY -> {
                    NSLog("ElectrumXClient: connected to %@:%@", host, port.toString())
                    dispatch_semaphore_signal(semaphore)
                }

                NW_CONNECTION_STATE_FAILED -> {
                    val msg = "Connection failed to $host:$port (error: $error)"
                    connectError = msg
                    NSLog("ElectrumXClient: %@", msg)
                    dispatch_semaphore_signal(semaphore)
                }

                NW_CONNECTION_STATE_CANCELLED -> {
                    NSLog("ElectrumXClient: connection cancelled")
                }

                else -> {}
            }
        }

        nw_connection_set_queue(conn, queue)
        nw_connection_start(conn)

        dispatch_semaphore_wait(semaphore, DISPATCH_TIME_FOREVER)

        if (connectError != null) {
            nw_connection_cancel(conn)
            throw Exception(connectError)
        }

        connection = conn
    }

    private fun writeLine(line: String) {
        val conn = connection ?: throw Exception("Not connected")
        val bytes = (line + "\n").encodeToByteArray()
        val semaphore = dispatch_semaphore_create(0)
        var sendError: String? = null

        bytes.usePinned { pinned ->
            val dispatchData =
                dispatch_data_create(
                    pinned.addressOf(0),
                    bytes.size.toULong(),
                    queue,
                    null,
                )

            nw_connection_send(
                conn,
                dispatchData,
                NW_CONNECTION_DEFAULT_MESSAGE_CONTEXT,
                false,
            ) { error ->
                if (error != null) {
                    sendError = "Send error: $error"
                }
                dispatch_semaphore_signal(semaphore)
            }
        }

        dispatch_semaphore_wait(semaphore, DISPATCH_TIME_FOREVER)
        if (sendError != null) throw Exception(sendError)
    }

    private fun readLine(): String? {
        // Check buffer first for a complete line
        val buffered = readBuffer.toString()
        val newlineIdx = buffered.indexOf('\n')
        if (newlineIdx >= 0) {
            val line = buffered.substring(0, newlineIdx)
            readBuffer.clear()
            if (newlineIdx + 1 < buffered.length) {
                readBuffer.append(buffered.substring(newlineIdx + 1))
            }
            return line
        }

        val conn = connection ?: return null

        // Read from network until we get a newline
        while (true) {
            val semaphore = dispatch_semaphore_create(0)
            var receivedBytes: ByteArray? = null
            var receiveComplete = false
            var receiveError: String? = null

            nw_connection_receive(conn, 1u, 65536u) { content, _, isComplete, error ->
                if (error != null) {
                    receiveError = "Receive error: $error"
                } else if (content != null) {
                    val size = dispatch_data_get_size(content).toInt()
                    if (size > 0) {
                        val result = ByteArray(size)
                        var offset = 0
                        dispatch_data_apply(content) {
                            _,
                            _,
                            ptr: CPointer<out CPointed>?,
                            regionSize: ULong,
                            ->
                            if (ptr != null) {
                                val bytePtr: CPointer<ByteVar> = ptr.reinterpret()
                                for (i in 0 until regionSize.toInt()) {
                                    result[offset + i] = bytePtr[i]
                                }
                                offset += regionSize.toInt()
                            }
                            true
                        }
                        receivedBytes = result
                    }
                }
                receiveComplete = isComplete
                dispatch_semaphore_signal(semaphore)
            }

            dispatch_semaphore_wait(semaphore, DISPATCH_TIME_FOREVER)

            if (receiveError != null) throw Exception(receiveError)

            val bytes = receivedBytes
            if (bytes != null) {
                readBuffer.append(bytes.decodeToString())
            }

            // Check for complete line
            val current = readBuffer.toString()
            val idx = current.indexOf('\n')
            if (idx >= 0) {
                val line = current.substring(0, idx)
                readBuffer.clear()
                if (idx + 1 < current.length) {
                    readBuffer.append(current.substring(idx + 1))
                }
                return line
            }

            // If connection is complete and no newline, return what we have or null
            if (receiveComplete) {
                if (readBuffer.isNotEmpty()) {
                    val remaining = readBuffer.toString()
                    readBuffer.clear()
                    return remaining
                }
                return null
            }
        }
    }

    private fun close() {
        val conn = connection
        if (conn != null) {
            nw_connection_cancel(conn)
        }
        connection = null
        readBuffer.clear()
    }

    // ── internals (business logic) ─────────────────────────────────────

    private fun connectAndQuery(
        identifier: String,
        server: ElectrumxServer,
    ): NameShowResult? {
        connect(server.host, server.port)
        try {
            // 1. Negotiate protocol version
            val versionReq =
                buildRpcRequest("server.version", listOf("AmethystNMC/0.1", PROTOCOL_VERSION))
            writeLine(versionReq)
            readLine() // consume version response

            // 2. Compute the canonical name index scripthash
            val nameScript = buildNameIndexScript(identifier.encodeToByteArray())
            val scriptHash = electrumScriptHash(nameScript)

            // 3. Get transaction history for this name
            val historyReq =
                buildRpcRequest("blockchain.scripthash.get_history", listOf(scriptHash))
            writeLine(historyReq)
            val historyResponse = readLine() ?: return null
            val historyEntries = parseHistoryResponse(historyResponse) ?: return null
            if (historyEntries.isEmpty()) throw NamecoinLookupException.NameNotFound(identifier)

            // 4. Get the latest transaction (last entry = most recent update)
            val latestEntry = historyEntries.last()
            val txHash = latestEntry.first
            val height = latestEntry.second

            val txReq = buildRpcRequest("blockchain.transaction.get", listOf(txHash, true))
            writeLine(txReq)
            val txResponse = readLine() ?: return null

            // 5. Get current block height to check name expiry
            val headersReq =
                buildRpcRequest("blockchain.headers.subscribe", emptyList<String>())
            writeLine(headersReq)
            val headersResponse = readLine()
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
            close()
        }
    }

    /**
     * Build the canonical script used by ElectrumX to index Namecoin names.
     *
     * Format: OP_NAME_UPDATE <push(name)> <push(empty)> OP_2DROP OP_DROP OP_RETURN
     */
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
                val lenBytes =
                    byteArrayOf((len and 0xff).toByte(), ((len shr 8) and 0xff).toByte())
                byteArrayOf(OP_PUSHDATA2) + lenBytes + data
            }
        }
    }

    private fun electrumScriptHash(script: ByteArray): String {
        val digest = sha256(script)
        return digest.reversedArray().toHexKey()
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

        val name = nameBytes.decodeToString()
        val value = valueBytes.decodeToString()
        return name to value
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
                (
                    (hexDigit(hex[i]) shl 4) +
                        hexDigit(hex[i + 1])
                ).toByte()
        }
        return data
    }

    private fun hexDigit(c: Char): Int =
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
        val id = ++requestId
        val obj =
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", id)
                put("method", method)
                put(
                    "params",
                    json.encodeToJsonElement(
                        ListSerializer(JsonElement.serializer()),
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

        // nw_connection_state constants
        private const val NW_CONNECTION_STATE_READY: UInt = 3u
        private const val NW_CONNECTION_STATE_FAILED: UInt = 5u
        private const val NW_CONNECTION_STATE_CANCELLED: UInt = 6u
    }
}
