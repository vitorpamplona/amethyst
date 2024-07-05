/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.encoders

import com.vitorpamplona.quartz.events.Event
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

class Nip01Serializer {
    interface Writer {
        fun append(
            value: ByteArray,
            offset: Int,
            length: Int,
        )

        fun append(
            value: String,
            offset: Int,
            length: Int,
        )

        fun append(value: ByteArray)

        fun append(value: Byte)

        fun dump()
    }

    class BufferedDigestWriter(
        val digest: MessageDigest,
    ) : Writer {
        val utf8Encoder =
            Charsets.UTF_8
                .newEncoder()
                .onMalformedInput(CodingErrorAction.IGNORE)
                .onUnmappableCharacter(CodingErrorAction.IGNORE)

        companion object {
            const val BUFFER_SIZE = 128
        }

        private val innerBuffer = ByteArray(BUFFER_SIZE)
        private val byteBuffer = ByteBuffer.wrap(innerBuffer)

        override fun append(value: Byte) {
            if (byteBuffer.position() == byteBuffer.capacity()) {
                dump()
            }

            byteBuffer.put(value)
        }

        override fun append(value: ByteArray) {
            if (value.size >= BUFFER_SIZE) {
                dump()
                // don't cache if the cache is smaller than the value
                digest.update(value)
            } else {
                if (value.size > byteBuffer.remaining()) {
                    dump()
                }

                value.copyInto(innerBuffer, byteBuffer.position(), 0, value.size)
                byteBuffer.position(byteBuffer.position() + value.size)
            }
        }

        override fun append(
            value: ByteArray,
            offset: Int,
            length: Int,
        ) {
            if (value.size >= BUFFER_SIZE) {
                dump()
                // don't cache if the cache is smaller than the value
                digest.update(value, offset, length)
            } else {
                if (length > byteBuffer.remaining()) {
                    dump()
                }

                value.copyInto(innerBuffer, byteBuffer.position(), offset, offset + length)
                byteBuffer.position(byteBuffer.position() + value.size)
            }
        }

        override fun append(
            value: String,
            offset: Int,
            length: Int,
        ) {
            val toEncode = CharBuffer.wrap(value, offset, offset + length)
            while (toEncode.hasRemaining()) {
                val result = utf8Encoder.encode(toEncode, byteBuffer, false)
                if (result.isOverflow) {
                    dump()
                }
            }
        }

        override fun dump() {
            if (byteBuffer.position() > 0) {
                digest.update(innerBuffer, 0, byteBuffer.position())
                byteBuffer.clear()
            }
        }
    }

    class StringWriter : Writer {
        private val stringBuilder: StringBuilder = StringBuilder()

        override fun append(
            value: ByteArray,
            offset: Int,
            length: Int,
        ) {
            stringBuilder.append(value.decodeToString(offset, offset + length))
        }

        override fun append(value: ByteArray) {
            stringBuilder.append(value.decodeToString())
        }

        override fun append(
            value: String,
            offset: Int,
            length: Int,
        ) {
            stringBuilder.append(value, offset, offset + length)
        }

        override fun append(value: Byte) {
            stringBuilder.append(value.toInt().toChar())
        }

        override fun toString(): String = stringBuilder.toString()

        override fun dump() {
        }
    }

    companion object {
        private const val DOUBLE_QUOTE_ASCII = 0x22
        private const val BACKLASH_ASCII = 0x5C
        private const val TAB_ASCII = 0x09
        private const val BACKSPACE_ASCII = 0x08
        private const val NEWLINE_ASCII = 0x0A
        private const val RETURN_ASCII = 0x0D
        private const val FORM_FEED_ASCII = 0x0C

        private val ESCAPED_DOUBLE_QUOTE = "\\\"".toByteArray()
        private val ESCAPED_DOUBLE_BACKLASH = "\\\\".toByteArray()
        private val ESCAPED_TAB = "\\t".toByteArray()
        private val ESCAPED_BACKSPACE = "\\b".toByteArray()
        private val ESCAPED_NEW_LINE = "\\n".toByteArray()
        private val ESCAPED_RETURN = "\\r".toByteArray()
        private val ESCAPED_FORM_FEED = "\\f".toByteArray()

        val ARRAY_ZERO_COMMA_QUOTE = "[0,\"".toByteArray()
        val COMMA = ",".toByteArray()
        val QUOTE = "\"".toByteArray()
        val QUOTE_COMMA = "\",".toByteArray()
        val QUOTE_COMMA_QUOTE = "\",\"".toByteArray()
        val COMMA_OPEN_ARRAY = ",[".toByteArray()
        val COMMA_OPEN_ARRAY_QUOTE = ",[\"".toByteArray()
        val OPEN_ARRAY = "[".toByteArray()
        val OPEN_ARRAY_QUOTE = "[\"".toByteArray()
        val QUOTE_CLOSE_ARRAY = "\"]".toByteArray()
        val CLOSE_ARRAY_COMMA_QUOTE = "],\"".toByteArray()

        private val MAPPER = Array<ByteArray?>(255) { null }

        init {
            for (i in 0 until 0x1F) {
                MAPPER[i] = String.format("\\u%04x", i.toByte()).toByteArray()
            }

            MAPPER[DOUBLE_QUOTE_ASCII] = ESCAPED_DOUBLE_QUOTE
            MAPPER[BACKLASH_ASCII] = ESCAPED_DOUBLE_BACKLASH
            MAPPER[TAB_ASCII] = ESCAPED_TAB
            MAPPER[BACKSPACE_ASCII] = ESCAPED_BACKSPACE
            MAPPER[NEWLINE_ASCII] = ESCAPED_NEW_LINE
            MAPPER[RETURN_ASCII] = ESCAPED_RETURN
            MAPPER[FORM_FEED_ASCII] = ESCAPED_FORM_FEED
        }
    }

    fun escapeStringInto(
        value: String,
        writer: Writer,
    ) {
        var lastNormalSequenceStarts = 0
        var lastNormalSequenceLength = 0

        for (i in value.indices) {
            if (value[i].code >= 255) {
                lastNormalSequenceLength++
            } else {
                val escaped = MAPPER[value[i].code]
                if (escaped != null) {
                    if (lastNormalSequenceLength > 0) {
                        writer.append(value, lastNormalSequenceStarts, lastNormalSequenceLength)
                    }
                    lastNormalSequenceStarts = i + 1
                    lastNormalSequenceLength = 0
                    writer.append(escaped)
                } else {
                    lastNormalSequenceLength++
                }
            }
        }

        if (lastNormalSequenceLength > 0) {
            if (lastNormalSequenceLength == value.length) {
                writer.append(value, 0, value.length)
            } else {
                writer.append(value, lastNormalSequenceStarts, lastNormalSequenceLength)
            }
        }
    }

    fun serializeEventInto(
        event: Event,
        writer: Writer,
    ) {
        writer.append(ARRAY_ZERO_COMMA_QUOTE)
        writer.append(event.pubKey.toByteArray())
        writer.append(QUOTE_COMMA)
        writer.append(event.createdAt.toString().toByteArray())
        writer.append(COMMA)
        writer.append(event.kind.toString().toByteArray())
        writer.append(COMMA_OPEN_ARRAY)

        for (index in event.tags.indices) {
            val tag = event.tags[index]
            if (index > 0) {
                writer.append(COMMA_OPEN_ARRAY_QUOTE)
            } else {
                writer.append(OPEN_ARRAY_QUOTE)
            }
            for (sIndex in tag.indices) {
                if (sIndex > 0) {
                    writer.append(QUOTE_COMMA_QUOTE)
                }
                escapeStringInto(tag[sIndex], writer)
            }
            writer.append(QUOTE_CLOSE_ARRAY)
        }
        writer.append(CLOSE_ARRAY_COMMA_QUOTE)

        escapeStringInto(event.content, writer)
        writer.append(QUOTE_CLOSE_ARRAY)

        writer.dump()
    }
}
