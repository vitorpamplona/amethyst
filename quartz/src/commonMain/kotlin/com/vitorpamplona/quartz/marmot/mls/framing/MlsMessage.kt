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
package com.vitorpamplona.quartz.marmot.mls.framing

import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.codec.TlsSerializable
import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter
import com.vitorpamplona.quartz.marmot.mls.messages.Commit
import com.vitorpamplona.quartz.marmot.mls.messages.Proposal

/**
 * MLS MLSMessage (RFC 9420 Section 6).
 *
 * Top-level wire format container for all MLS messages:
 * - PublicMessage (proposals, some commits)
 * - PrivateMessage (encrypted application data and commits)
 * - Welcome (for new members)
 * - GroupInfo (in Welcome)
 * - KeyPackage (published separately)
 *
 * ```
 * struct {
 *     ProtocolVersion version = mls10;
 *     WireFormat wire_format;
 *     select (MLSMessage.wire_format) {
 *         case mls_public_message:  PublicMessage;
 *         case mls_private_message: PrivateMessage;
 *         case mls_welcome:         Welcome;
 *         case mls_group_info:      GroupInfo;
 *         case mls_key_package:     KeyPackage;
 *     };
 * } MLSMessage;
 * ```
 */
data class MlsMessage(
    val version: Int = MLS_VERSION_10,
    val wireFormat: WireFormat,
    val payload: ByteArray,
) : TlsSerializable {
    override fun encodeTls(writer: TlsWriter) {
        writer.putUint16(version)
        writer.putUint16(wireFormat.value)
        writer.putBytes(payload) // Payload is already TLS-encoded
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MlsMessage) return false
        return version == other.version && wireFormat == other.wireFormat && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + wireFormat.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }

    companion object {
        const val MLS_VERSION_10 = 1

        fun decodeTls(reader: TlsReader): MlsMessage {
            val version = reader.readUint16()
            require(version == MLS_VERSION_10) { "Unsupported MLS version: $version" }
            val wireFormat = WireFormat.fromValue(reader.readUint16())

            // The remaining bytes are the payload
            val payload = reader.readBytes(reader.remaining)
            return MlsMessage(version, wireFormat, payload)
        }

        fun fromPublicMessage(publicMessage: PublicMessage): MlsMessage {
            val payload = publicMessage.toTlsBytes()
            return MlsMessage(MLS_VERSION_10, WireFormat.PUBLIC_MESSAGE, payload)
        }

        fun fromPrivateMessage(privateMessage: PrivateMessage): MlsMessage {
            val payload = privateMessage.toTlsBytes()
            return MlsMessage(MLS_VERSION_10, WireFormat.PRIVATE_MESSAGE, payload)
        }
    }
}

/**
 * MLS FramedContentTBS (RFC 9420 Section 6.1) — the data that gets signed.
 *
 * This includes the wire_format, content fields, and group context.
 */
data class FramedContentTbs(
    val version: Int = MlsMessage.MLS_VERSION_10,
    val wireFormat: WireFormat,
    val groupId: ByteArray,
    val epoch: Long,
    val sender: Sender,
    val authenticatedData: ByteArray,
    val contentType: ContentType,
    val content: ByteArray,
    val groupContext: ByteArray? = null,
) : TlsSerializable {
    override fun encodeTls(writer: TlsWriter) {
        writer.putUint16(version)
        writer.putUint16(wireFormat.value)
        writer.putOpaqueVarInt(groupId)
        writer.putUint64(epoch)
        encodeSender(writer, sender)
        writer.putOpaqueVarInt(authenticatedData)
        writer.putUint8(contentType.value)
        writer.putBytes(content)

        // GroupContext is included for member senders
        if (sender.senderType == SenderType.MEMBER && groupContext != null) {
            writer.putBytes(groupContext)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FramedContentTbs) return false
        return groupId.contentEquals(other.groupId) && epoch == other.epoch
    }

    override fun hashCode(): Int = groupId.contentHashCode()
}

/**
 * MLS PublicMessage (RFC 9420 Section 6.2).
 *
 * Cleartext message format used for Proposals and some Commits.
 * Contains the content, membership MAC, and signature.
 */
data class PublicMessage(
    val groupId: ByteArray,
    val epoch: Long,
    val sender: Sender,
    val authenticatedData: ByteArray,
    val contentType: ContentType,
    val content: ByteArray,
    val signature: ByteArray,
    val confirmationTag: ByteArray? = null,
    val membershipTag: ByteArray? = null,
) : TlsSerializable {
    override fun encodeTls(writer: TlsWriter) {
        writer.putOpaqueVarInt(groupId)
        writer.putUint64(epoch)
        encodeSender(writer, sender)
        writer.putOpaqueVarInt(authenticatedData)
        writer.putUint8(contentType.value)

        // RFC 9420 §6 FramedContent.content is a type-dependent body:
        //   case application: opaque application_data<V>
        //   case proposal:    Proposal proposal (struct, no outer length prefix)
        //   case commit:      Commit commit       (struct, no outer length prefix)
        // `content` holds the already-serialized body for proposal/commit, and
        // the raw application bytes for application.
        when (contentType) {
            ContentType.APPLICATION -> writer.putOpaqueVarInt(content)
            ContentType.PROPOSAL, ContentType.COMMIT -> writer.putBytes(content)
        }

        // FramedContentAuthData
        writer.putOpaqueVarInt(signature)
        if (contentType == ContentType.COMMIT) {
            writer.putOpaqueVarInt(confirmationTag ?: ByteArray(0))
        }

        // membership_tag (only for member senders)
        if (sender.senderType == SenderType.MEMBER) {
            writer.putOpaqueVarInt(membershipTag ?: ByteArray(0))
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PublicMessage) return false
        return groupId.contentEquals(other.groupId) && epoch == other.epoch
    }

    override fun hashCode(): Int = groupId.contentHashCode()

    companion object {
        fun decodeTls(reader: TlsReader): PublicMessage {
            val groupId = reader.readOpaqueVarInt()
            val epoch = reader.readUint64()
            val sender = decodeSender(reader)
            val authenticatedData = reader.readOpaqueVarInt()
            val contentType = ContentType.fromValue(reader.readUint8())

            // RFC 9420 §6 FramedContent.content body varies by content_type.
            // For PROPOSAL/COMMIT we decode the inner struct to advance the reader
            // and then re-serialize back to bytes so the invariant
            // "content holds the serialized body" holds for all variants.
            val content: ByteArray =
                when (contentType) {
                    ContentType.APPLICATION -> {
                        reader.readOpaqueVarInt()
                    }

                    ContentType.PROPOSAL -> {
                        val proposal = Proposal.decodeTls(reader)
                        val w = TlsWriter()
                        proposal.encodeTls(w)
                        w.toByteArray()
                    }

                    ContentType.COMMIT -> {
                        val commit = Commit.decodeTls(reader)
                        val w = TlsWriter()
                        commit.encodeTls(w)
                        w.toByteArray()
                    }
                }
            val signature = reader.readOpaqueVarInt()

            val confirmationTag =
                if (contentType == ContentType.COMMIT) {
                    reader.readOpaqueVarInt()
                } else {
                    null
                }

            val membershipTag =
                if (sender.senderType == SenderType.MEMBER && reader.hasRemaining) {
                    reader.readOpaqueVarInt()
                } else {
                    null
                }

            return PublicMessage(
                groupId = groupId,
                epoch = epoch,
                sender = sender,
                authenticatedData = authenticatedData,
                contentType = contentType,
                content = content,
                signature = signature,
                confirmationTag = confirmationTag,
                membershipTag = membershipTag,
            )
        }
    }
}

/**
 * MLS PrivateMessage (RFC 9420 Section 6.3).
 *
 * Encrypted message format used for application data and some commits.
 */
data class PrivateMessage(
    val groupId: ByteArray,
    val epoch: Long,
    val contentType: ContentType,
    val authenticatedData: ByteArray,
    val encryptedSenderData: ByteArray,
    val ciphertext: ByteArray,
) : TlsSerializable {
    override fun encodeTls(writer: TlsWriter) {
        writer.putOpaqueVarInt(groupId)
        writer.putUint64(epoch)
        writer.putUint8(contentType.value)
        writer.putOpaqueVarInt(authenticatedData)
        writer.putOpaqueVarInt(encryptedSenderData)
        writer.putOpaqueVarInt(ciphertext)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PrivateMessage) return false
        return groupId.contentEquals(other.groupId) && epoch == other.epoch
    }

    override fun hashCode(): Int = groupId.contentHashCode()

    companion object {
        fun decodeTls(reader: TlsReader): PrivateMessage =
            PrivateMessage(
                groupId = reader.readOpaqueVarInt(),
                epoch = reader.readUint64(),
                contentType = ContentType.fromValue(reader.readUint8()),
                authenticatedData = reader.readOpaqueVarInt(),
                encryptedSenderData = reader.readOpaqueVarInt(),
                ciphertext = reader.readOpaqueVarInt(),
            )
    }
}

// --- Sender encoding/decoding helpers ---

internal fun encodeSender(
    writer: TlsWriter,
    sender: Sender,
) {
    writer.putUint8(sender.senderType.value)
    when (sender.senderType) {
        SenderType.MEMBER -> {
            writer.putUint32(sender.leafIndex.toLong())
        }

        SenderType.EXTERNAL -> {
            writer.putUint32(sender.leafIndex.toLong())
        }

        SenderType.NEW_MEMBER_PROPOSAL, SenderType.NEW_MEMBER_COMMIT -> {} // empty
    }
}

internal fun decodeSender(reader: TlsReader): Sender {
    val senderType = SenderType.fromValue(reader.readUint8())
    val leafIndex =
        when (senderType) {
            SenderType.MEMBER, SenderType.EXTERNAL -> reader.readUint32().toInt()
            else -> 0
        }
    return Sender(senderType, leafIndex)
}
