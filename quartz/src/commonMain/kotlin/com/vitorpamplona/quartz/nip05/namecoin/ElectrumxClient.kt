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
    /** If true, accept any certificate (self-signed, expired, etc.) */
    val trustAllCerts: Boolean = false,
)

class ElectrumxClient(
    private val connectTimeoutMs: Long = 10_000L,
    private val readTimeoutMs: Long = 15_000L,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    private val requestId = AtomicInteger(0)
    private val mutex = Mutex()

    companion object {
        val DEFAULT_SERVERS =
            listOf(
                ElectrumxServer("electrumx.testls.space", 50002, useSsl = true, trustAllCerts = true),
                ElectrumxServer("nmc2.bitcoins.sk", 57002, useSsl = true, trustAllCerts = true),
                ElectrumxServer("46.229.238.187", 57002, useSsl = true, trustAllCerts = true),
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
                ElectrumxServer("nmc2.bitcoins.sk", 57002, useSsl = true, trustAllCerts = true),
            )

        private const val PROTOCOL_VERSION = "1.4"
    }

    /** Outcome of a name lookup against a single server. */
    sealed class LookupOutcome {
        data class Found(
            val result: NameShowResult,
        ) : LookupOutcome()

        /** Server was reachable but the name does not exist. */
        data class NameNotFound(
            val name: String,
        ) : LookupOutcome()

        /** Could not connect / communicate with this server. */
        data class ServerError(
            val server: ElectrumxServer,
            val message: String,
        ) : LookupOutcome()
    }

    suspend fun nameShow(
        identifier: String,
        server: ElectrumxServer = DEFAULT_SERVERS.first(),
    ): LookupOutcome =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    connectAndQuery(identifier, server)
                } catch (e: Exception) {
                    LookupOutcome.ServerError(server, e.message ?: e.javaClass.simpleName)
                }
            }
        }

    suspend fun nameShowWithFallback(identifier: String): LookupOutcome {
        val serverErrors = mutableListOf<LookupOutcome.ServerError>()
        for (server in DEFAULT_SERVERS) {
            when (val outcome = nameShow(identifier, server)) {
                is LookupOutcome.Found -> return outcome
                is LookupOutcome.NameNotFound -> return outcome
                is LookupOutcome.ServerError -> serverErrors.add(outcome)
            }
        }
        // All servers failed — return the first error as representative
        return serverErrors.firstOrNull()
            ?: LookupOutcome.ServerError(DEFAULT_SERVERS.first(), "No servers configured")
    }

    private fun connectAndQuery(
        identifier: String,
        server: ElectrumxServer,
    ): LookupOutcome {
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
            val response =
                reader.readLine()
                    ?: return LookupOutcome.ServerError(server, "Empty response from server")
            return parseNameShowResponse(identifier, response, server)
        } finally {
            runCatching { writer.close() }
            runCatching { reader.close() }
            runCatching { socket.close() }
        }
    }

    private fun createSocket(server: ElectrumxServer): Socket =
        if (server.useSsl) {
            val factory =
                if (server.trustAllCerts) {
                    trustAllSslSocketFactory()
                } else {
                    SSLSocketFactory.getDefault() as SSLSocketFactory
                }
            factory.createSocket().apply {
                connect(InetSocketAddress(server.host, server.port), connectTimeoutMs.toInt())
            }
        } else {
            Socket().apply {
                connect(InetSocketAddress(server.host, server.port), connectTimeoutMs.toInt())
            }
        }

    private fun trustAllSslSocketFactory(): SSLSocketFactory {
        val trustAllCerts =
            arrayOf<javax.net.ssl.TrustManager>(
                object : javax.net.ssl.X509TrustManager {
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
        val sc =
            javax.net.ssl.SSLContext
                .getInstance("TLS")
        sc.init(null, trustAllCerts, java.security.SecureRandom())
        return sc.socketFactory
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
                        params.map { JsonPrimitive(it.toString()) },
                    ),
                )
            }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    private fun parseNameShowResponse(
        identifier: String,
        raw: String,
        server: ElectrumxServer,
    ): LookupOutcome {
        val envelope =
            try {
                json.parseToJsonElement(raw).jsonObject
            } catch (e: Exception) {
                return LookupOutcome.ServerError(server, "Malformed JSON response")
            }
        val error = envelope["error"]
        if (error != null && error !is JsonNull) {
            val errorMsg =
                when (error) {
                    is JsonPrimitive -> error.content
                    is JsonObject -> error["message"]?.jsonPrimitive?.content ?: error.toString()
                    else -> error.toString()
                }
            // ElectrumX returns an error when the name doesn't exist
            return LookupOutcome.NameNotFound(identifier)
        }
        val result = envelope["result"] ?: return LookupOutcome.NameNotFound(identifier)
        val nameShowResult =
            when {
                result is JsonObject && result.containsKey("value") -> {
                    val value = result["value"]?.jsonPrimitive?.content ?: return LookupOutcome.NameNotFound(identifier)
                    NameShowResult(
                        name = result["name"]?.jsonPrimitive?.content ?: identifier,
                        value = value,
                        txid = result["txid"]?.jsonPrimitive?.content,
                        height = result["height"]?.jsonPrimitive?.content?.toIntOrNull(),
                        expiresIn = result["expires_in"]?.jsonPrimitive?.content?.toIntOrNull(),
                    )
                }

                result is JsonPrimitive -> {
                    NameShowResult(name = identifier, value = result.content)
                }

                else -> {
                    return LookupOutcome.NameNotFound(identifier)
                }
            }
        return LookupOutcome.Found(nameShowResult)
    }
}
