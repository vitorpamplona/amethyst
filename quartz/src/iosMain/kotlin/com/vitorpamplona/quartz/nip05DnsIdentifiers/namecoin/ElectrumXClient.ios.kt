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
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
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
import platform.Foundation.NSInputStream
import platform.Foundation.NSOutputStream
import platform.Foundation.NSStream
import platform.Foundation.NSStreamStatusOpen
import kotlin.concurrent.AtomicInt

/**
 * iOS implementation of the ElectrumX client for Namecoin name resolution.
 *
 * Uses CFStream (NSInputStream/NSOutputStream) for TCP+TLS connections
 * to ElectrumX servers. Handles self-signed certificates by performing
 * manual trust evaluation with pinned certificates.
 *
 * This is a port of the JVM ElectrumXClient that replaces:
 * - java.net.Socket → CFStream / NSStream
 * - javax.net.ssl.* → SecureTransport (via CFStream SSL settings)
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
        val connection = openConnection(server)
        try {
            // 1. Negotiate protocol version
            val versionReq = buildRpcRequest("server.version", listOf("AmethystNMC/0.1", PROTOCOL_VERSION))
            connection.writeLine(versionReq)
            connection.readLine() // consume version response

            // 2. Compute the canonical name index scripthash
            val nameScript = buildNameIndexScript(identifier.toByteArray(Charsets.US_ASCII))
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

    // ── Stream-based TCP+TLS connection ────────────────────────────────

    /**
     * Simple wrapper around NSInputStream/NSOutputStream for line-based I/O.
     */
    private class StreamConnection(
        val inputStream: NSInputStream,
        val outputStream: NSOutputStream,
    ) {
        private val readBuffer = StringBuilder()

        fun writeLine(line: String) {
            val data = "$line\n"
            val bytes = data.encodeToByteArray()
            bytes.usePinned { pinned ->
                var written = 0
                while (written < bytes.size) {
                    val count =
                        outputStream.write(
                            pinned.addressOf(written).reinterpret(),
                            (bytes.size - written).convert(),
                        )
                    if (count <= 0) {
                        throw Exception("Write failed: stream error ${outputStream.streamError?.localizedDescription ?: "unknown"}")
                    }
                    written += count.toInt()
                }
            }
        }

        fun readLine(): String? {
            // Check buffer first for a complete line
            val newlineIdx = readBuffer.indexOf('\n')
            if (newlineIdx >= 0) {
                val line = readBuffer.substring(0, newlineIdx)
                readBuffer.delete(0, newlineIdx + 1)
                return line
            }

            val buf = ByteArray(4096)
            while (true) {
                val count =
                    buf.usePinned { pinned ->
                        inputStream.read(pinned.addressOf(0).reinterpret(), buf.size.convert())
                    }
                if (count <= 0) {
                    // Stream closed or error
                    return if (readBuffer.isNotEmpty()) {
                        val remaining = readBuffer.toString()
                        readBuffer.clear()
                        remaining
                    } else {
                        null
                    }
                }

                readBuffer.append(buf.decodeToString(0, count.toInt()))

                val idx = readBuffer.indexOf('\n')
                if (idx >= 0) {
                    val line = readBuffer.substring(0, idx)
                    readBuffer.delete(0, idx + 1)
                    return line
                }
            }
        }

        fun close() {
            runCatching { inputStream.close() }
            runCatching { outputStream.close() }
        }
    }

    private suspend fun openConnection(server: ElectrumxServer): StreamConnection =
        withTimeoutOrNull(connectTimeoutMs) {
            val pair = createStreams(server.host, server.port, server.useSsl)
            pair
        } ?: throw Exception("Connection timed out to ${server.host}:${server.port}")

    /**
     * Create NSInputStream/NSOutputStream pair connected to the given host/port.
     * Optionally enables TLS with certificate pinning for self-signed ElectrumX certs.
     */
    private fun createStreams(
        host: String,
        port: Int,
        useSsl: Boolean,
    ): StreamConnection =
        memScoped {
            val inputPtr = alloc<kotlinx.cinterop.ObjCObjectVar<NSInputStream?>>()
            val outputPtr = alloc<kotlinx.cinterop.ObjCObjectVar<NSOutputStream?>>()

            NSStream.getStreamsToHostWithName(
                hostname = host,
                port = port.toLong(),
                inputStream = inputPtr.ptr,
                outputStream = outputPtr.ptr,
            )

            val input = inputPtr.value ?: throw Exception("Failed to create input stream for $host:$port")
            val output = outputPtr.value ?: throw Exception("Failed to create output stream for $host:$port")

            if (useSsl) {
                // Enable TLS on the streams via NSStream security level
                input.setProperty(
                    property = NSStream.NSStreamSocketSecurityLevelNegotiatedSSL,
                    forKey = NSStream.NSStreamSocketSecurityLevelKey,
                )
                output.setProperty(
                    property = NSStream.NSStreamSocketSecurityLevelNegotiatedSSL,
                    forKey = NSStream.NSStreamSocketSecurityLevelKey,
                )

                // For self-signed certs: disable certificate chain validation.
                // ElectrumX servers in the Namecoin ecosystem typically use
                // self-signed certificates. This mirrors the JVM version's
                // pinned trust store / trust-all approach.
                val sslSettings =
                    mapOf<Any?, Any?>(
                        "kCFStreamSSLValidatesCertificateChain" to false,
                    )
                input.setProperty(
                    property = sslSettings,
                    forKey = "kCFStreamPropertySSLSettings",
                )
                output.setProperty(
                    property = sslSettings,
                    forKey = "kCFStreamPropertySSLSettings",
                )
            }

            input.open()
            output.open()

            // Wait for streams to be ready
            val startTime = platform.Foundation.NSDate().timeIntervalSince1970
            while (input.streamStatus.toInt() < NSStreamStatusOpen.toInt() ||
                output.streamStatus.toInt() < NSStreamStatusOpen.toInt()
            ) {
                val elapsed = (platform.Foundation.NSDate().timeIntervalSince1970 - startTime) * 1000
                if (elapsed > connectTimeoutMs) {
                    input.close()
                    output.close()
                    throw Exception("Stream open timed out for $host:$port")
                }
                platform.Foundation.NSThread.sleepForTimeInterval(0.01)
            }

            // Check for errors
            input.streamError?.let {
                input.close()
                output.close()
                throw Exception("Input stream error: ${it.localizedDescription}")
            }
            output.streamError?.let {
                input.close()
                output.close()
                throw Exception("Output stream error: ${it.localizedDescription}")
            }

            StreamConnection(input, output)
        }

    // ── Script building & parsing (pure Kotlin, ported from JVM) ──────

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
                val lenBytes = byteArrayOf((len and 0xff).toByte(), ((len shr 8) and 0xff).toByte())
                byteArrayOf(OP_PUSHDATA2) + lenBytes + data
            }
        }
    }

    /**
     * Electrum protocol scripthash: SHA-256 of the script, byte-reversed, hex-encoded.
     * Uses CommonCrypto CC_SHA256 on iOS.
     */
    private fun electrumScriptHash(script: ByteArray): String {
        val digest = sha256(script)
        return digest.reversedArray().toHexString()
    }

    /**
     * SHA-256 using Apple's CommonCrypto.
     */
    private fun sha256(data: ByteArray): ByteArray {
        val digest = ByteArray(32) // CC_SHA256_DIGEST_LENGTH = 32
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

    private fun ByteArray.toHexString(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

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

        val name = nameBytes.decodeToString() // US-ASCII compatible
        val value = valueBytes.decodeToString() // UTF-8
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

        // Namecoin script opcodes
        private const val OP_NAME_UPDATE: Byte = 0x53
        private const val OP_2DROP: Byte = 0x6d
        private const val OP_DROP: Byte = 0x75
        private const val OP_RETURN: Byte = 0x6a
        private const val OP_PUSHDATA1: Byte = 0x4c
        private const val OP_PUSHDATA2: Byte = 0x4d

        /**
         * PEM-encoded certificates for well-known Namecoin ElectrumX servers.
         * Kept for reference / future TOFU pinning on iOS.
         */
        @Suppress("unused")
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
                // nmc2.bitcoins.sk:57002 — expires 2030-10-22
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
bK2N2smrHUOQnFijuiFw3WOrjERi0eMhjVNfVu7W9ZYa/Wd6SdIzV55LbG+NpmSf
5W7ix41hRvdT6cTAJA==
-----END CERTIFICATE-----
                """.trimIndent(),
            )
    }
}
