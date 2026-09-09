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
package com.vitorpamplona.quartz.marmot.appComponents.agentTextStream

/**
 * One `AgentTextStreamRecordV1` frame, the unit that travels over the QUIC
 * stream.
 *
 * ```
 * version         uint8 (= 1)
 * stream_id       opaque<V>
 * seq             uint64
 * record_type     uint8
 * flags           uint8
 * frame           opaque<V>
 * ```
 *
 * [frame] holds plaintext before [AgentTextStreamCrypto.seal] and ciphertext
 * after it; the wire always carries the sealed form. `seq` is authenticated
 * through the AEAD nonce and the AAD, so a reordered or replayed record fails
 * to open rather than being detected afterwards.
 */
class AgentTextStreamRecordV1(
    val streamId: ByteArray,
    val seq: Long,
    val recordType: Int,
    val flags: Int = 0,
    val frame: ByteArray,
) {
    init {
        require(streamId.isNotEmpty()) { "agent text stream id cannot be empty" }
        require(streamId.size <= MAX_STREAM_ID_LEN) { "agent text stream id is too long: ${streamId.size}" }
        require(seq >= 0) { "agent text stream record seq cannot be negative" }
        require(recordType in 0..0xff) { "agent text stream record type is not a uint8" }
        require(flags in 0..0xff) { "agent text stream record flags are not a uint8" }
        require(frame.size <= MAX_CIPHERTEXT_LEN) { "agent text stream frame is too large: ${frame.size}" }
    }

    fun copyWithFrame(newFrame: ByteArray) =
        AgentTextStreamRecordV1(
            streamId = streamId,
            seq = seq,
            recordType = recordType,
            flags = flags,
            frame = newFrame,
        )

    fun encode(): ByteArray {
        val out = ArrayList<Byte>(encodedLength())
        out.add(VERSION.toByte())
        out.addLengthPrefixed(streamId)
        for (i in 7 downTo 0) out.add((seq shr (8 * i)).toByte())
        out.add(recordType.toByte())
        out.add(flags.toByte())
        out.addLengthPrefixed(frame)
        return out.toByteArray()
    }

    fun encodedLength(): Int =
        1 +
            QuicVarInt.encodedLength(streamId.size.toLong()) + streamId.size +
            8 + 1 + 1 +
            QuicVarInt.encodedLength(frame.size.toLong()) + frame.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AgentTextStreamRecordV1) return false
        return seq == other.seq &&
            recordType == other.recordType &&
            flags == other.flags &&
            streamId.contentEquals(other.streamId) &&
            frame.contentEquals(other.frame)
    }

    override fun hashCode(): Int {
        var result = streamId.contentHashCode()
        result = 31 * result + seq.hashCode()
        result = 31 * result + recordType
        result = 31 * result + flags
        result = 31 * result + frame.contentHashCode()
        return result
    }

    companion object {
        const val VERSION = 1

        const val TYPE_TEXT_DELTA = 0x01
        const val TYPE_PROGRESS_DELTA = 0x02
        const val TYPE_STATUS = 0x03
        const val TYPE_CHECKPOINT = 0x04
        const val TYPE_ABORT = 0x05
        const val TYPE_FINAL_NOTICE = 0x06

        const val MAX_STREAM_ID_LEN = 64

        /** The profile pins the stream id to 32 bytes; the framing allows less. */
        const val PROFILE_STREAM_ID_LEN = 32
        const val START_EVENT_ID_LEN = 32

        /** Wire bound of the record's `ciphertext<0..2^16-1>` field. */
        const val MAX_CIPHERTEXT_LEN = 0xffff

        const val AEAD_TAG_LEN = 16

        fun textDelta(
            streamId: ByteArray,
            seq: Long,
            frame: ByteArray,
        ) = AgentTextStreamRecordV1(streamId, seq, TYPE_TEXT_DELTA, 0, frame)

        /**
         * Decode one record. Trailing bytes are an error: a record is framed
         * by the QUIC stream, so anything after the frame field means the
         * sender and receiver disagree about where this record ends.
         *
         * An UNKNOWN [recordType] is deliberately not an error. A newer
         * advisory record type must not tear down an otherwise valid preview
         * stream, so receivers ignore semantics they do not understand.
         */
        fun decode(bytes: ByteArray): AgentTextStreamRecordV1 {
            require(bytes.isNotEmpty()) { "agent text stream record is truncated while reading version" }
            require(bytes[0].toInt() and 0xff == VERSION) {
                "unsupported agent text stream record version: ${bytes[0].toInt() and 0xff}"
            }
            var at = 1

            val streamIdLen = QuicVarInt.decode(bytes, at)
            at += streamIdLen.length
            require(at + streamIdLen.value <= bytes.size) { "agent text stream record is truncated while reading stream_id" }
            val streamId = bytes.copyOfRange(at, at + streamIdLen.value.toInt())
            at += streamIdLen.value.toInt()

            require(at + 8 <= bytes.size) { "agent text stream record is truncated while reading seq" }
            var seq = 0L
            for (i in 0 until 8) seq = (seq shl 8) or (bytes[at + i].toLong() and 0xff)
            at += 8

            require(at + 2 <= bytes.size) { "agent text stream record is truncated while reading record_type" }
            val recordType = bytes[at].toInt() and 0xff
            val flags = bytes[at + 1].toInt() and 0xff
            at += 2

            val frameLen = QuicVarInt.decode(bytes, at)
            at += frameLen.length
            require(at + frameLen.value <= bytes.size) { "agent text stream record is truncated while reading frame" }
            val frame = bytes.copyOfRange(at, at + frameLen.value.toInt())
            at += frameLen.value.toInt()

            require(at == bytes.size) { "agent text stream record contains trailing bytes: ${bytes.size - at}" }
            return AgentTextStreamRecordV1(streamId, seq, recordType, flags, frame)
        }
    }
}
