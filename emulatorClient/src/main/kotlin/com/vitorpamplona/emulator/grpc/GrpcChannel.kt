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
package com.vitorpamplona.emulator.grpc

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource
import java.io.IOException
import java.net.Proxy
import java.time.Duration
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * gRPC over OkHttp's cleartext HTTP/2 with prior knowledge (h2c): the emulator's gRPC server
 * listens in plain text on loopback, so there is no TLS or ALPN, and the connection starts with
 * the HTTP/2 preface. Each call is `POST /<service>/<method>` with `content-type:
 * application/grpc` and `te: trailers`; the answer is length-prefixed messages in the body and
 * `grpc-status` in the trailers (or in the headers when the server answers trailers-only).
 *
 * @param baseUrl `http://host:port`, without a trailing slash.
 * @param authorization the whole `authorization` header value (e.g. `Bearer <token>`), or null.
 */
internal class GrpcChannel(
    client: OkHttpClient,
    private val baseUrl: String,
    private val service: String,
    private val authorization: () -> String?,
) {
    private val unaryClient: OkHttpClient =
        client
            .newBuilder()
            .protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
            // Loopback: a system HTTP proxy must never see these calls (or the token).
            .proxy(Proxy.NO_PROXY)
            .build()

    // A screen stream is quiet for as long as the screen does not change.
    private val streamingClient: OkHttpClient =
        unaryClient
            .newBuilder()
            .readTimeout(Duration.ZERO)
            .callTimeout(Duration.ZERO)
            .build()

    private fun request(
        method: String,
        body: ByteArray,
    ): Request {
        val builder =
            Request
                .Builder()
                .url("$baseUrl/$service/$method")
                .header("te", "trailers")
                .header("grpc-accept-encoding", "identity")
                .post(body.toRequestBody(GRPC))
        authorization()?.let { builder.header("authorization", it) }
        return builder.build()
    }

    /** A unary call: one request message, one response message. */
    suspend fun unary(
        method: String,
        message: ByteArray,
    ): ByteArray = call(method, GrpcFraming.frame(message))

    /**
     * A client-streaming call whose request stream is known up front: every message is sent in
     * one request body, then the stream is half-closed. One response message comes back.
     */
    suspend fun clientStreaming(
        method: String,
        messages: List<ByteArray>,
    ): ByteArray = call(method, GrpcFraming.frameAll(messages))

    private suspend fun call(
        method: String,
        body: ByteArray,
    ): ByteArray =
        suspendCancellableCoroutine { cont ->
            val call = unaryClient.newCall(request(method, body))
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        cont.resumeWithException(e)
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        val result =
                            try {
                                response.use { readUnary(method, it) }
                            } catch (e: Exception) {
                                // Anything, not just IOException: an uncaught one would leave the
                                // caller suspended forever.
                                cont.resumeWithException(e)
                                return
                            }
                        cont.resume(result)
                    }
                },
            )
        }

    private fun readUnary(
        method: String,
        response: Response,
    ): ByteArray {
        val source = openResponse(method, response) ?: return EMPTY
        // Unary answers carry exactly one message; an OK answer with none decodes as all defaults.
        val message = GrpcFraming.readMessage(source) ?: EMPTY
        while (GrpcFraming.readMessage(source) != null) {
            // Not valid for a unary method; drain so the trailers can be read.
        }
        checkTrailers(method, response)
        return message
    }

    /**
     * A server-streaming call. Messages are handed over one at a time (no buffering here, so a
     * slow collector applies backpressure; add `conflate()` to keep only the newest). Cancelling
     * the collector cancels the HTTP/2 stream.
     */
    fun serverStreaming(
        method: String,
        message: ByteArray,
    ): Flow<ByteArray> =
        channelFlow {
            val call = streamingClient.newCall(request(method, GrpcFraming.frame(message)))
            launch(Dispatchers.IO) {
                try {
                    call.execute().use { response ->
                        val source = openResponse(method, response)
                        if (source != null) {
                            while (true) {
                                val next = GrpcFraming.readMessage(source) ?: break
                                send(next)
                            }
                            checkTrailers(method, response)
                        }
                    }
                } catch (e: IOException) {
                    // Our own cancel (the collector went away) is not a failure of the stream.
                    if (call.isCanceled()) throw CancellationException("gRPC $method cancelled", e)
                    throw e
                }
                channel.close()
            }
            awaitClose { call.cancel() }
        }.buffer(0)

    /**
     * Checks the HTTP status and a trailers-only answer. Returns the body to read messages from,
     * or null when the headers already ended the call with OK.
     */
    private fun openResponse(
        method: String,
        response: Response,
    ): BufferedSource? {
        val headerStatus = response.header("grpc-status")
        if (response.code != 200) {
            throw GrpcStatusException(
                status = headerStatus?.toIntOrNull() ?: GrpcStatusException.UNKNOWN,
                grpcMessage = GrpcFraming.decodeGrpcMessage(response.header("grpc-message")) ?: response.message,
                method = method,
                httpCode = response.code,
            )
        }
        if (headerStatus != null) {
            throwIfNotOk(method, headerStatus, response.header("grpc-message"))
            return null
        }
        val contentType = response.header("content-type")
        if (contentType == null || !contentType.startsWith("application/grpc")) {
            throw GrpcStatusException(GrpcStatusException.UNKNOWN, "unexpected content-type $contentType", method)
        }
        return response.body.source()
    }

    private fun checkTrailers(
        method: String,
        response: Response,
    ) {
        val trailers = response.trailers()
        val status =
            trailers["grpc-status"]
                ?: throw GrpcStatusException(GrpcStatusException.UNKNOWN, "stream ended without grpc-status", method)
        throwIfNotOk(method, status, trailers["grpc-message"])
    }

    private fun throwIfNotOk(
        method: String,
        status: String,
        message: String?,
    ) {
        val code = status.trim().toIntOrNull() ?: GrpcStatusException.UNKNOWN
        if (code != GrpcStatusException.OK) {
            throw GrpcStatusException(code, GrpcFraming.decodeGrpcMessage(message), method)
        }
    }

    companion object {
        private val GRPC = "application/grpc".toMediaType()
        private val EMPTY = ByteArray(0)
    }
}
