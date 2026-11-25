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

import okio.Buffer
import okio.BufferedSource
import java.io.IOException
import java.net.URLDecoder

/**
 * gRPC's length-prefixed message framing
 * (https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md): a one-byte compressed flag,
 * a four-byte big-endian length, then that many bytes of protobuf. This client never asks for
 * compression, so a frame with the flag set is an error.
 */
internal object GrpcFraming {
    /** Refuse frames bigger than this: a 4K RGBA frame is ~33 MB, so 64 MB leaves headroom. */
    const val MAX_MESSAGE_BYTES = 64 * 1024 * 1024

    fun frame(message: ByteArray): ByteArray {
        val out = Buffer()
        writeFrame(out, message)
        return out.readByteArray()
    }

    fun frameAll(messages: List<ByteArray>): ByteArray {
        val out = Buffer()
        messages.forEach { writeFrame(out, it) }
        return out.readByteArray()
    }

    private fun writeFrame(
        out: Buffer,
        message: ByteArray,
    ) {
        out.writeByte(0)
        out.writeInt(message.size)
        out.write(message)
    }

    /**
     * Reads the next message, blocking until it has all arrived (however the bytes were split
     * across HTTP/2 DATA frames). Returns null at a clean end of stream, between messages.
     */
    fun readMessage(source: BufferedSource): ByteArray? {
        if (source.exhausted()) return null
        val compressed = source.readByte().toInt()
        if (!source.request(4)) throw IOException("gRPC stream ended inside a message header")
        val length = source.readInt()
        if (compressed != 0) throw IOException("compressed gRPC message (flag $compressed) but none was negotiated")
        if (length < 0 || length > MAX_MESSAGE_BYTES) throw IOException("gRPC message of $length bytes")
        if (!source.request(length.toLong())) throw IOException("gRPC stream ended inside a $length-byte message")
        return source.readByteArray(length.toLong())
    }

    /** `grpc-message` is percent-encoded UTF-8; `+` is literal there, unlike form encoding. */
    fun decodeGrpcMessage(raw: String?): String? {
        if (raw == null) return null
        return try {
            URLDecoder.decode(raw.replace("+", "%2B"), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            raw
        }
    }
}
