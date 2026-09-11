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

import com.vitorpamplona.quartz.marmot.mls.crypto.MlsCryptoProvider
import com.vitorpamplona.quartz.nip44Encryption.crypto.ChaCha20Poly1305

/**
 * The context every agent-text-stream key derivation is bound to.
 *
 * ```
 * len("v1") || "v1" || len(group_id) || group_id || len(stream_id) || stream_id
 *   || mls_epoch (uint64) || len(sender_id) || sender_id
 *   || len(start_event_id) || start_event_id
 * ```
 *
 * The whole tuple goes into the HKDF info, which is what separates two streams
 * that share one group exporter secret: change the stream, the epoch, the
 * sender or the anchoring kind-1200 event and the record key changes with it.
 */
class AgentTextStreamKeyContextV1(
    val groupId: ByteArray,
    val streamId: ByteArray,
    val mlsEpoch: Long,
    val senderId: ByteArray,
    val startEventId: ByteArray,
) {
    fun encode(): ByteArray {
        val out = ArrayList<Byte>()
        out.addLengthPrefixed(VERSION)
        out.addLengthPrefixed(groupId)
        out.addLengthPrefixed(streamId)
        for (i in 7 downTo 0) out.add((mlsEpoch shr (8 * i)).toByte())
        out.addLengthPrefixed(senderId)
        out.addLengthPrefixed(startEventId)
        return out.toByteArray()
    }

    companion object {
        val VERSION = "v1".encodeToByteArray()
    }
}

/**
 * Per-stream record AEAD.
 *
 * The stream secret is the group's `MLS-Exporter("marmot",
 * "agent-text-stream-quic", 32)`, so every member of the epoch can derive it;
 * per-stream and per-record separation comes entirely from the key context and
 * the sequence number:
 *
 * - key   = `HKDF-Expand(secret, len("record key") || "record key" || context, 32)`
 * - nonce = `HKDF-Expand(secret, len("record nonce") || "record nonce" || context, 12)`
 *           XOR the 96-bit big-endian sequence number
 * - aad   = `version || SHA-256(group_id) || len(stream_id) || stream_id ||
 *            mls_epoch || len(sender_id) || sender_id || seq || record_type || flags`
 *
 * HKDF-**Expand** with no Extract: the exporter secret is already a uniformly
 * random PRK, so extracting again would only discard entropy.
 */
class AgentTextStreamCrypto(
    val streamSecret: ByteArray,
    val context: AgentTextStreamKeyContextV1,
) {
    init {
        require(streamSecret.size == SECRET_LENGTH) { "agent text stream secret must be $SECRET_LENGTH bytes" }
        require(context.streamId.size == AgentTextStreamRecordV1.PROFILE_STREAM_ID_LEN) {
            "agent text stream id must be ${AgentTextStreamRecordV1.PROFILE_STREAM_ID_LEN} bytes"
        }
        require(context.startEventId.size == AgentTextStreamRecordV1.START_EVENT_ID_LEN) {
            "agent text stream start event id must be ${AgentTextStreamRecordV1.START_EVENT_ID_LEN} bytes"
        }
    }

    fun recordKey(): ByteArray = derive(KEY_LABEL, KEY_LENGTH)

    /** `nonce_base` XOR uint96_be(seq); `seq = 0` yields `nonce_base` itself. */
    fun recordNonce(seq: Long): ByteArray {
        val nonce = derive(NONCE_LABEL, NONCE_LENGTH)
        // uint96_be(seq): the top 4 of the 12 bytes stay zero for any uint64.
        for (i in 0 until 8) {
            nonce[NONCE_LENGTH - 1 - i] = (nonce[NONCE_LENGTH - 1 - i].toInt() xor (seq shr (8 * i)).toInt()).toByte()
        }
        return nonce
    }

    fun recordAad(record: AgentTextStreamRecordV1): ByteArray {
        val out = ArrayList<Byte>()
        out.add(AgentTextStreamRecordV1.VERSION.toByte())
        for (b in MlsCryptoProvider.hash(context.groupId)) out.add(b)
        out.addLengthPrefixed(record.streamId)
        for (i in 7 downTo 0) out.add((context.mlsEpoch shr (8 * i)).toByte())
        out.addLengthPrefixed(context.senderId)
        for (i in 7 downTo 0) out.add((record.seq shr (8 * i)).toByte())
        out.add(record.recordType.toByte())
        out.add(record.flags.toByte())
        return out.toByteArray()
    }

    /** Encrypt [record]'s plaintext frame in place, returning the wire record. */
    fun seal(
        record: AgentTextStreamRecordV1,
        maxPlaintextFrameLen: Long = AgentTextStreamQuicPolicyV1.MAX_PLAINTEXT_FRAME_LEN,
    ): AgentTextStreamRecordV1 {
        requireSameStream(record)
        require(record.frame.size <= maxPlaintextFrameLen) {
            "agent text stream plaintext frame is larger than the group's limit"
        }
        return record.copyWithFrame(
            ChaCha20Poly1305.encrypt(record.frame, recordAad(record), recordNonce(record.seq), recordKey()),
        )
    }

    /** Decrypt a wire record, returning it with its plaintext frame. */
    fun open(record: AgentTextStreamRecordV1): AgentTextStreamRecordV1 {
        requireSameStream(record)
        require(record.frame.size >= AgentTextStreamRecordV1.AEAD_TAG_LEN) {
            "encrypted agent text stream frame is shorter than the AEAD tag"
        }
        return record.copyWithFrame(
            ChaCha20Poly1305.decrypt(record.frame, recordAad(record), recordNonce(record.seq), recordKey()),
        )
    }

    fun openOrNull(record: AgentTextStreamRecordV1): AgentTextStreamRecordV1? =
        try {
            open(record)
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IllegalStateException) {
            null
        }

    private fun requireSameStream(record: AgentTextStreamRecordV1) {
        require(record.streamId.contentEquals(context.streamId)) {
            "agent text stream record belongs to a different stream than its key context"
        }
    }

    private fun derive(
        label: ByteArray,
        length: Int,
    ): ByteArray {
        val info = ArrayList<Byte>()
        info.addLengthPrefixed(label)
        for (b in context.encode()) info.add(b)
        return MlsCryptoProvider.hkdfExpand(streamSecret, info.toByteArray(), length)
    }

    companion object {
        const val SECRET_LENGTH = 32
        const val KEY_LENGTH = 32
        const val NONCE_LENGTH = 12

        /** `MLS-Exporter("marmot", "agent-text-stream-quic", 32)`. */
        const val EXPORTER_LABEL = "marmot"
        val EXPORTER_CONTEXT = "agent-text-stream-quic".encodeToByteArray()

        private val KEY_LABEL = "record key".encodeToByteArray()
        private val NONCE_LABEL = "record nonce".encodeToByteArray()
    }
}
