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
package com.vitorpamplona.androiddevice.grpc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A call the server answered with a gRPC status other than OK. */
class GrpcException(
    val status: Int,
    message: String,
) : IOException("gRPC status $status: $message") {
    companion object {
        const val CANCELLED = 1
        const val UNKNOWN = 2
        const val UNAVAILABLE = 14
        const val UNAUTHENTICATED = 16
        const val PERMISSION_DENIED = 7
        const val UNIMPLEMENTED = 12
    }
}

/**
 * gRPC over plain HTTP/2 (`h2c`, prior knowledge), the way the emulator serves it on localhost:
 * a POST per call, each message framed as one flag byte (uncompressed) and a big-endian length,
 * and the outcome in the `grpc-status` trailer (or header, for a trailers-only answer). Only the
 * call shapes the emulator needs: unary, and server streaming.
 *
 * Never through a proxy: the endpoint is on this computer, and the app may send everything else
 * through Tor.
 */
class GrpcChannel(
    private val host: String,
    private val port: Int,
    /** `authorization: Bearer <token>`, when the emulator was started with `-grpc-use-token`. */
    private val token: String? = null,
    baseClient: OkHttpClient = sharedClient,
    private val callTimeoutMs: Long = 15_000,
    /** The whole `authorization` value for each call, instead of [token]: a signed JWT ([com.vitorpamplona.androiddevice.emulator.EmulatorJwtAuth]). */
    private val authorization: (() -> String)? = null,
) {
    private val client =
        baseClient
            .newBuilder()
            .protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
            .proxy(Proxy.NO_PROXY)
            .connectTimeout(5, TimeUnit.SECONDS)
            // A stream waits as long as the screen doesn't change; unary calls get their own timeout.
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

    private val unaryClient = client.newBuilder().callTimeout(callTimeoutMs, TimeUnit.MILLISECONDS).build()

    private fun request(
        method: String,
        message: ByteArray,
    ): Request =
        Request
            .Builder()
            .url("http://$host:$port/$method")
            .header("te", "trailers")
            .header("grpc-accept-encoding", "identity")
            .apply { (authorization?.invoke() ?: token?.let { "Bearer $it" })?.let { header("authorization", it) } }
            .post(frame(message).toRequestBody(GRPC))
            .build()

    /** Calls [method] (`package.Service/method`) with one message and returns its one answer. */
    suspend fun unary(
        method: String,
        message: ByteArray,
    ): ByteArray {
        val response = unaryClient.newCall(request(method, message)).await()
        // Reading the body blocks: never on the caller's thread, which may be the UI's.
        return withContext(Dispatchers.IO) {
            response.use {
                checkHeaders(it)
                val source = it.body.source()
                val answer = readMessage(source)
                // Draining the body is what makes the trailers readable.
                while (readMessage(source) != null) Unit
                checkTrailers(it)
                answer ?: throw GrpcException(GrpcException.UNKNOWN, "No message in the answer to $method")
            }
        }
    }

    /**
     * Calls [method] with one message and emits each message the server streams back, until it
     * ends the stream (normal completion) or fails ([GrpcException], [IOException]). Cancelling the
     * collector cancels the call.
     */
    fun serverStream(
        method: String,
        message: ByteArray,
    ): Flow<ByteArray> =
        channelFlow {
            val call = client.newCall(request(method, message))
            launch(Dispatchers.IO) {
                try {
                    call.execute().use { response ->
                        checkHeaders(response)
                        val source = response.body.source()
                        while (true) {
                            val next = readMessage(source) ?: break
                            send(next)
                        }
                        checkTrailers(response)
                    }
                    close()
                } catch (e: IOException) {
                    // A cancel from awaitClose surfaces here as an IOException: nobody is listening any more.
                    if (!call.isCanceled()) close(e) else close()
                }
            }
            awaitClose { call.cancel() }
        }.buffer(1)

    private fun checkHeaders(response: Response) {
        if (response.code != 200) throw GrpcException(GrpcException.UNAVAILABLE, "HTTP ${response.code} from the emulator")
        // A trailers-only answer (an error before any message) carries the status in its headers.
        response.header("grpc-status")?.toIntOrNull()?.let { status ->
            if (status != 0) throw GrpcException(status, response.header("grpc-message").orEmpty())
        }
    }

    private fun checkTrailers(response: Response) {
        val trailers =
            try {
                response.trailers()
            } catch (_: IllegalStateException) {
                return
            }
        val status = trailers["grpc-status"]?.toIntOrNull() ?: return
        if (status != 0) throw GrpcException(status, trailers["grpc-message"].orEmpty())
    }

    companion object {
        private val GRPC = "application/grpc".toMediaType()

        /**
         * Every channel derives from this one, so they share one connection pool and dispatcher:
         * an OkHttpClient each would start thread pools per emulator connection.
         */
        private val sharedClient: OkHttpClient by lazy { OkHttpClient() }

        /** One message as gRPC frames it on the wire: uncompressed flag, length, bytes. */
        fun frame(message: ByteArray): ByteArray {
            val out = ByteArray(5 + message.size)
            val n = message.size
            out[1] = (n ushr 24).toByte()
            out[2] = (n ushr 16).toByte()
            out[3] = (n ushr 8).toByte()
            out[4] = n.toByte()
            message.copyInto(out, 5)
            return out
        }

        /** The next framed message, or null at the end of the stream. */
        fun readMessage(source: BufferedSource): ByteArray? {
            if (source.exhausted()) return null
            val compressed = source.readByte().toInt()
            val length = source.readInt()
            if (compressed != 0) throw GrpcException(GrpcException.UNIMPLEMENTED, "Compressed gRPC messages aren't supported")
            if (length < 0) throw GrpcException(GrpcException.UNKNOWN, "Bad gRPC frame length $length")
            return source.readByteArray(length.toLong())
        }
    }
}

private suspend fun Call.await(): Response =
    suspendCancellableCoroutine { continuation ->
        enqueue(
            object : Callback {
                override fun onResponse(
                    call: Call,
                    response: Response,
                ) = continuation.resume(response)

                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) = continuation.resumeWithException(e)
            },
        )
        continuation.invokeOnCancellation { cancel() }
    }
