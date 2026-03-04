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
package com.vitorpamplona.quartz.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.zlib.Z_DEFAULT_COMPRESSION
import platform.zlib.Z_DEFAULT_STRATEGY
import platform.zlib.Z_DEFLATED
import platform.zlib.Z_FINISH
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.deflate
import platform.zlib.deflateBound
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2
import platform.zlib.z_stream

@OptIn(ExperimentalForeignApi::class)
actual object GZip {
    /**
     * Compresses [content] into a gzip byte array using zlib's deflateInit2 with
     * windowBits=31 (MAX_WBITS + 16), which requests the gzip wrapper format.
     * deflateBound gives an upper-bound on output size so a single deflate call
     * with Z_FINISH is always sufficient.
     */
    actual fun compress(content: String): ByteArray {
        val input = content.encodeToByteArray()

        return memScoped {
            val stream = alloc<z_stream>()

            // windowBits = 15 + 16 = 31 → gzip format
            deflateInit2(
                stream.ptr,
                Z_DEFAULT_COMPRESSION,
                Z_DEFLATED,
                31,
                8,
                Z_DEFAULT_STRATEGY,
            ).let { check(it == Z_OK) { "deflateInit2 failed: $it" } }

            val maxSize = deflateBound(stream.ptr, input.size.toULong()).toInt()
            val output = ByteArray(maxSize)

            val written =
                input.usePinned { pinIn ->
                    output.usePinned { pinOut ->
                        stream.next_in = pinIn.addressOf(0).reinterpret()
                        stream.avail_in = input.size.toUInt()
                        stream.next_out = pinOut.addressOf(0).reinterpret()
                        stream.avail_out = maxSize.toUInt()

                        deflate(stream.ptr, Z_FINISH)
                            .let { check(it == Z_STREAM_END) { "deflate failed: $it" } }

                        maxSize - stream.avail_out.toInt()
                    }
                }

            deflateEnd(stream.ptr)
            output.copyOf(written)
        }
    }

    /**
     * Decompresses a gzip [content] byte array back to a UTF-8 string using
     * zlib's inflateInit2 with windowBits=47 (MAX_WBITS + 32), which enables
     * automatic detection of both gzip and zlib formats.
     * Output is collected in fixed-size chunks to handle arbitrary output size.
     */
    actual fun decompress(content: ByteArray): String {
        val chunks = ArrayList<ByteArray>()
        val chunkSize = maxOf(content.size * 4, 4096)

        memScoped {
            val stream = alloc<z_stream>()

            // windowBits = 15 + 32 = 47 → auto-detect gzip or zlib
            inflateInit2(stream.ptr, 47)
                .let { check(it == Z_OK) { "inflateInit2 failed: $it" } }

            content.usePinned { pinIn ->
                stream.next_in = pinIn.addressOf(0).reinterpret()
                stream.avail_in = content.size.toUInt()

                var status: Int = Z_OK
                do {
                    val chunk = ByteArray(chunkSize)
                    chunk.usePinned { pinOut ->
                        stream.next_out = pinOut.addressOf(0).reinterpret()
                        stream.avail_out = chunkSize.toUInt()
                        status = inflate(stream.ptr, Z_NO_FLUSH)
                        val produced = chunkSize - stream.avail_out.toInt()
                        if (produced > 0) chunks.add(chunk.copyOf(produced))
                    }
                } while (status == Z_OK)

                check(status == Z_STREAM_END) { "inflate failed: $status" }
            }

            inflateEnd(stream.ptr)
        }

        val totalSize = chunks.sumOf { it.size }
        val result = ByteArray(totalSize)
        var pos = 0
        chunks.forEach { chunk ->
            chunk.copyInto(result, pos)
            pos += chunk.size
        }
        return result.decodeToString()
    }
}
