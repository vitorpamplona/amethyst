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
package com.vitorpamplona.quartz.marmot.mls.messages

import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.codec.TlsSerializable
import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter
import com.vitorpamplona.quartz.marmot.mls.crypto.HpkeCiphertext
import com.vitorpamplona.quartz.marmot.mls.tree.Extension

/**
 * MLS Welcome message (RFC 9420 Section 12.4.3.1).
 *
 * Sent to new members to bootstrap them into the group. Contains:
 * - Ciphersuite identifier
 * - Per-recipient encrypted group secrets (one per added member)
 * - Encrypted GroupInfo (shared, encrypted with welcome_secret)
 *
 * ```
 * struct {
 *     CipherSuite cipher_suite;
 *     EncryptedGroupSecrets secrets<V>;
 *     opaque encrypted_group_info<1..2^32-1>;
 * } Welcome;
 * ```
 */
data class Welcome(
    val cipherSuite: Int,
    val secrets: List<EncryptedGroupSecrets>,
    val encryptedGroupInfo: ByteArray,
) : TlsSerializable {
    override fun encodeTls(writer: TlsWriter) {
        writer.putUint16(cipherSuite)
        writer.putVector4(secrets)
        writer.putOpaque4(encryptedGroupInfo)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Welcome) return false
        return cipherSuite == other.cipherSuite && encryptedGroupInfo.contentEquals(other.encryptedGroupInfo)
    }

    override fun hashCode(): Int = encryptedGroupInfo.contentHashCode()

    companion object {
        fun decodeTls(reader: TlsReader): Welcome =
            Welcome(
                cipherSuite = reader.readUint16(),
                secrets = reader.readVector4 { EncryptedGroupSecrets.decodeTls(it) },
                encryptedGroupInfo = reader.readOpaque4(),
            )
    }
}

/**
 * Per-recipient encrypted group secrets in a Welcome message.
 *
 * ```
 * struct {
 *     opaque key_package_ref<V>;
 *     HPKECiphertext encrypted_group_secrets;
 * } EncryptedGroupSecrets;
 * ```
 */
data class EncryptedGroupSecrets(
    val newMember: ByteArray,
    val encryptedGroupSecrets: HpkeCiphertext,
) : TlsSerializable {
    override fun encodeTls(writer: TlsWriter) {
        writer.putOpaque1(newMember)
        encryptedGroupSecrets.encodeTls(writer)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedGroupSecrets) return false
        return newMember.contentEquals(other.newMember)
    }

    override fun hashCode(): Int = newMember.contentHashCode()

    companion object {
        fun decodeTls(reader: TlsReader): EncryptedGroupSecrets =
            EncryptedGroupSecrets(
                newMember = reader.readOpaque1(),
                encryptedGroupSecrets = HpkeCiphertext.decodeTls(reader),
            )
    }
}

/**
 * MLS GroupInfo (RFC 9420 Section 12.4.3).
 *
 * Describes the group state for new members joining via Welcome.
 *
 * ```
 * struct {
 *     GroupContext group_context;
 *     Extension extensions<V>;
 *     opaque confirmation_tag<V>;
 *     uint32 signer;
 *     opaque signature<V>;
 * } GroupInfo;
 * ```
 */
data class GroupInfo(
    val groupContext: GroupContext,
    val extensions: List<Extension>,
    val confirmationTag: ByteArray,
    val signer: Int,
    val signature: ByteArray,
) : TlsSerializable {
    override fun encodeTls(writer: TlsWriter) {
        groupContext.encodeTls(writer)
        writer.putVector4(extensions)
        writer.putOpaque1(confirmationTag)
        writer.putUint32(signer.toLong())
        writer.putOpaque2(signature)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GroupInfo) return false
        return groupContext == other.groupContext && signer == other.signer
    }

    override fun hashCode(): Int = groupContext.hashCode()

    companion object {
        fun decodeTls(reader: TlsReader): GroupInfo =
            GroupInfo(
                groupContext = GroupContext.decodeTls(reader),
                extensions = reader.readVector4 { Extension.decodeTls(it) },
                confirmationTag = reader.readOpaque1(),
                signer = reader.readUint32().toInt(),
                signature = reader.readOpaque2(),
            )
    }
}

/**
 * MLS GroupContext (RFC 9420 Section 8.1).
 *
 * Identifies a specific group state. Included in key derivations and signatures.
 *
 * ```
 * struct {
 *     ProtocolVersion version = mls10;
 *     CipherSuite cipher_suite;
 *     opaque group_id<V>;
 *     uint64 epoch;
 *     opaque tree_hash<V>;
 *     opaque confirmed_transcript_hash<V>;
 *     Extension extensions<V>;
 * } GroupContext;
 * ```
 */
data class GroupContext(
    val version: Int = 1,
    val cipherSuite: Int = 1,
    val groupId: ByteArray,
    val epoch: Long,
    val treeHash: ByteArray,
    val confirmedTranscriptHash: ByteArray,
    val extensions: List<Extension> = emptyList(),
) : TlsSerializable {
    override fun encodeTls(writer: TlsWriter) {
        writer.putUint16(version)
        writer.putUint16(cipherSuite)
        writer.putOpaque1(groupId)
        writer.putUint64(epoch)
        writer.putOpaque1(treeHash)
        writer.putOpaque1(confirmedTranscriptHash)
        writer.putVector4(extensions)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GroupContext) return false
        return groupId.contentEquals(other.groupId) && epoch == other.epoch
    }

    override fun hashCode(): Int {
        var result = groupId.contentHashCode()
        result = 31 * result + epoch.hashCode()
        return result
    }

    companion object {
        fun decodeTls(reader: TlsReader): GroupContext =
            GroupContext(
                version = reader.readUint16(),
                cipherSuite = reader.readUint16(),
                groupId = reader.readOpaque1(),
                epoch = reader.readUint64(),
                treeHash = reader.readOpaque1(),
                confirmedTranscriptHash = reader.readOpaque1(),
                extensions = reader.readVector4 { Extension.decodeTls(it) },
            )
    }
}

/**
 * GroupSecrets: the secrets encrypted per-recipient in Welcome messages.
 *
 * ```
 * struct {
 *     opaque joiner_secret<V>;
 *     optional<PathSecret> path_secret;
 *     PreSharedKeyID psks<V>;
 * } GroupSecrets;
 * ```
 */
data class GroupSecrets(
    val joinerSecret: ByteArray,
    val pathSecret: ByteArray?,
    val psks: List<ByteArray> = emptyList(),
) : TlsSerializable {
    override fun encodeTls(writer: TlsWriter) {
        writer.putOpaque1(joinerSecret)
        if (pathSecret != null) {
            writer.putUint8(1)
            writer.putOpaque1(pathSecret)
        } else {
            writer.putUint8(0)
        }
        val pskWriter = TlsWriter()
        for (psk in psks) {
            pskWriter.putOpaque2(psk)
        }
        writer.putOpaque4(pskWriter.toByteArray())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GroupSecrets) return false
        return joinerSecret.contentEquals(other.joinerSecret)
    }

    override fun hashCode(): Int = joinerSecret.contentHashCode()

    companion object {
        fun decodeTls(reader: TlsReader): GroupSecrets {
            val joinerSecret = reader.readOpaque1()
            val pathSecret = reader.readOptional { it.readOpaque1() }
            val pskBytes = reader.readOpaque4()
            val pskReader = TlsReader(pskBytes)
            val psks = mutableListOf<ByteArray>()
            while (pskReader.hasRemaining) {
                psks.add(pskReader.readOpaque2())
            }
            return GroupSecrets(joinerSecret, pathSecret, psks)
        }
    }
}
