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
package com.vitorpamplona.quic.interop.runner

import com.vitorpamplona.quic.connection.QuicConnection
import com.vitorpamplona.quic.connection.QuicConnectionDriver
import kotlinx.coroutines.flow.toList

/**
 * HQ-interop (HTTP/0.9 over QUIC) GET client. quic-interop-runner convention
 * for the non-`http3` testcases (handshake / chacha20 / transfer / loss
 * variants): the client opens a bidi stream, sends `GET /path\r\n` (raw
 * ASCII, no framing), FINs the send side, and reads the response body
 * verbatim until the server FINs. There is no status code, no headers, no
 * QPACK, no control stream — the body bytes ARE the response.
 *
 * Per the runner's testcase validator, an empty body means failure, a
 * non-empty body that matches what the server staged at $WWW/<path> means
 * success. We surface non-empty as status=200, empty as status=0, so the
 * caller's `if (resp.status != 200) anyFailed = true` check keeps working.
 */
class HqInteropGetClient(
    private val conn: QuicConnection,
    @Suppress("UNUSED_PARAMETER") private val driver: QuicConnectionDriver,
) : GetClient {
    override suspend fun prepareRequest(
        @Suppress("UNUSED_PARAMETER") authority: String,
        path: String,
    ): RequestHandle {
        val stream = conn.openBidiStream()
        val request = "GET $path\r\n".encodeToByteArray()
        stream.send.enqueue(request)
        stream.send.finish()
        return HqRequestHandle(stream)
    }

    override suspend fun prepareRequests(
        @Suppress("UNUSED_PARAMETER") authority: String,
        paths: List<String>,
    ): List<RequestHandle> {
        // Pre-format outside the lock — see Http3GetClient. Then
        // openBidiStreamsBatch atomically opens all N streams under
        // streamsLock so the writer's next drain coalesces them.
        val encoded = paths.map { "GET $it\r\n".encodeToByteArray() }
        return conn.openBidiStreamsBatch(encoded) { stream, request ->
            stream.send.enqueue(request)
            stream.send.finish()
            HqRequestHandle(stream)
        }
    }

    override suspend fun awaitResponse(handle: RequestHandle): GetResponse {
        val stream = (handle as HqRequestHandle).stream
        val chunks = stream.incoming.toList()
        val total = chunks.sumOf { it.size }
        val body = ByteArray(total)
        var off = 0
        for (c in chunks) {
            c.copyInto(body, off)
            off += c.size
        }
        return GetResponse(status = if (body.isEmpty()) 0 else 200, body = body)
    }
}

private class HqRequestHandle(
    val stream: com.vitorpamplona.quic.stream.QuicStream,
) : RequestHandle
