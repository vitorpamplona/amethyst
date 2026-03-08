/**
 * ElectrumxClient.kt
 *
 * Lightweight ElectrumX protocol client for querying Namecoin name records.
 * Implements only the subset of the Electrum protocol needed for name_show
 * lookups — no wallet functionality.
 *
 * SPDX-License-Identifier: MIT
 */
package com.vitorpamplona.quartz.nip05.namecoin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLSocketFactory

@Serializable
data class NameShowResult(
    val name: String,
    val value: String,
    val txid: String? = null,
    val height: Int? = null,
    val expiresIn: Int? = null,
)

data class ElectrumxServer(
    val host: String,
    val port: Int,
    val useSsl: Boolean = true,
)

class ElectrumxClient(
    private val connectTimeoutMs: Long = 10_000L,
    private val readTimeoutMs: Long = 15_000L,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val requestId = AtomicInteger(0)
    private val mutex = Mutex()

    companion object {
        val DEFAULT_SERVERS = listOf(
            ElectrumxServer("ulrichard.ch", 50006, useSsl = true),
            ElectrumxServer("nmc2.lelux.fi", 50006, useSsl = true),
        )
        private const val PROTOCOL_VERSION = "1.4.3"
    }

    suspend fun nameShow(
        identifier: String,
        server: ElectrumxServer = DEFAULT_SERVERS.first(),
    ): NameShowResult? = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                connectAndQuery(identifier, server)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun nameShowWithFallback(identifier: String): NameShowResult? {
        for (server in DEFAULT_SERVERS) {
            val result = nameShow(identifier, server)
            if (result != null) return result
        }
        return null
    }

    private fun connectAndQuery(identifier: String, server: ElectrumxServer): NameShowResult? {
        val socket = createSocket(server)
        socket.soTimeout = readTimeoutMs.toInt()
        val writer = PrintWriter(socket.getOutputStream(), true)
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        try {
            val versionReq = buildRpcRequest("server.version", listOf("AmethystNMC/0.1", PROTOCOL_VERSION))
            writer.println(versionReq)
            reader.readLine()

            val nameReq = buildRpcRequest("blockchain.name.get_value_proof", listOf(identifier))
            writer.println(nameReq)
            val response = reader.readLine() ?: return null
            return parseNameShowResponse(identifier, response)
        } finally {
            runCatching { writer.close() }
            runCatching { reader.close() }
            runCatching { socket.close() }
        }
    }

    private fun createSocket(server: ElectrumxServer): Socket {
        return if (server.useSsl) {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            factory.createSocket().apply {
                connect(InetSocketAddress(server.host, server.port), connectTimeoutMs.toInt())
            }
        } else {
            Socket().apply {
                connect(InetSocketAddress(server.host, server.port), connectTimeoutMs.toInt())
            }
        }
    }

    private fun buildRpcRequest(method: String, params: List<Any>): String {
        val id = requestId.incrementAndGet()
        val obj = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", json.encodeToJsonElement(
                kotlinx.serialization.builtins.ListSerializer(
                    kotlinx.serialization.json.JsonElement.serializer()
                ),
                params.map { JsonPrimitive(it.toString()) }
            ))
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    private fun parseNameShowResponse(identifier: String, raw: String): NameShowResult? {
        val envelope = json.parseToJsonElement(raw).jsonObject
        val error = envelope["error"]
        if (error != null && error !is JsonNull) return null
        val result = envelope["result"] ?: return null
        return when {
            result is JsonObject && result.containsKey("value") -> {
                NameShowResult(
                    name = result["name"]?.jsonPrimitive?.content ?: identifier,
                    value = result["value"]?.jsonPrimitive?.content ?: return null,
                    txid = result["txid"]?.jsonPrimitive?.content,
                    height = result["height"]?.jsonPrimitive?.content?.toIntOrNull(),
                    expiresIn = result["expires_in"]?.jsonPrimitive?.content?.toIntOrNull(),
                )
            }
            result is JsonPrimitive -> NameShowResult(name = identifier, value = result.content)
            else -> null
        }
    }
}
