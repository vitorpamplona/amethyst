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
package com.vitorpamplona.nestsclient.moq

/**
 * Encode/decode MoQ control-stream messages per draft-ietf-moq-transport.
 *
 * Wire format for every control message:
 *
 *   message_type (varint) | message_length (varint) | payload bytes
 *
 * The payload layout is per-message. Payload encoders here produce only the
 * payload; [encode] wraps it with type+length for on-the-wire use.
 */
object MoqCodec {
    /**
     * Encode a full control-stream frame (type + length + payload).
     */
    fun encode(message: MoqMessage): ByteArray {
        val payload = encodePayload(message)
        val frame = MoqWriter(payload.size + 8)
        frame.writeVarint(message.type.code)
        frame.writeVarint(payload.size.toLong())
        frame.writeBytes(payload)
        return frame.toByteArray()
    }

    /**
     * Decode one control-stream message from [src] starting at [offset].
     *
     * Returns the message plus the number of bytes consumed, or null if [src]
     * doesn't yet contain a whole frame (caller should buffer more data from
     * the control stream and retry).
     */
    fun decode(
        src: ByteArray,
        offset: Int = 0,
    ): DecodeResult? {
        val typeDec = Varint.decode(src, offset) ?: return null
        val lenDec = Varint.decode(src, offset + typeDec.bytesConsumed) ?: return null
        val payloadStart = offset + typeDec.bytesConsumed + lenDec.bytesConsumed
        val payloadEnd = payloadStart + lenDec.value.toInt()
        if (payloadEnd > src.size) return null

        val type =
            MoqMessageType.fromCode(typeDec.value)
                ?: throw MoqCodecException("unknown MoQ message type: 0x${typeDec.value.toString(16)}")

        val reader = MoqReader(src, payloadStart, payloadEnd)
        val message =
            when (type) {
                MoqMessageType.ClientSetup -> decodeClientSetup(reader)
                MoqMessageType.ServerSetup -> decodeServerSetup(reader)
            }
        if (reader.hasMore()) {
            throw MoqCodecException(
                "trailing bytes in ${type.name} payload (consumed=${payloadEnd - payloadStart - reader.remaining}, total=${payloadEnd - payloadStart})",
            )
        }
        return DecodeResult(message, payloadEnd - offset)
    }

    private fun encodePayload(message: MoqMessage): ByteArray =
        when (message) {
            is ClientSetup -> encodeClientSetup(message)
            is ServerSetup -> encodeServerSetup(message)
        }

    private fun encodeClientSetup(message: ClientSetup): ByteArray {
        val w = MoqWriter()
        w.writeVarint(message.supportedVersions.size.toLong())
        for (v in message.supportedVersions) w.writeVarint(v)
        encodeParameters(w, message.parameters)
        return w.toByteArray()
    }

    private fun decodeClientSetup(r: MoqReader): ClientSetup {
        val nVersions = r.readVarint().toInt()
        if (nVersions < 0) throw MoqCodecException("negative version count: $nVersions")
        val versions = ArrayList<Long>(nVersions)
        repeat(nVersions) { versions.add(r.readVarint()) }
        val params = decodeParameters(r)
        return ClientSetup(versions, params)
    }

    private fun encodeServerSetup(message: ServerSetup): ByteArray {
        val w = MoqWriter()
        w.writeVarint(message.selectedVersion)
        encodeParameters(w, message.parameters)
        return w.toByteArray()
    }

    private fun decodeServerSetup(r: MoqReader): ServerSetup {
        val version = r.readVarint()
        val params = decodeParameters(r)
        return ServerSetup(version, params)
    }

    private fun encodeParameters(
        w: MoqWriter,
        params: List<SetupParameter>,
    ) {
        w.writeVarint(params.size.toLong())
        for (p in params) {
            w.writeVarint(p.key)
            w.writeLengthPrefixedBytes(p.value)
        }
    }

    private fun decodeParameters(r: MoqReader): List<SetupParameter> {
        val n = r.readVarint().toInt()
        if (n < 0) throw MoqCodecException("negative parameter count: $n")
        val out = ArrayList<SetupParameter>(n)
        repeat(n) {
            val key = r.readVarint()
            val value = r.readLengthPrefixedBytes()
            out.add(SetupParameter(key, value))
        }
        return out
    }

    data class DecodeResult(
        val message: MoqMessage,
        val bytesConsumed: Int,
    )
}
