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
package com.vitorpamplona.quartz.marmot.mls.group

import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter
import com.vitorpamplona.quartz.marmot.mls.messages.GroupContext
import com.vitorpamplona.quartz.marmot.mls.schedule.EpochSecrets
import com.vitorpamplona.quartz.marmot.mls.schedule.SenderRatchetState

/**
 * Serializable snapshot of an MLS group's complete state.
 *
 * This captures everything needed to restore an [MlsGroup] after an app restart:
 * - Group context (group ID, epoch, tree hash, transcript hash, extensions)
 * - Ratchet tree (all member leaf nodes and parent nodes)
 * - Key schedule secrets (epoch secrets for decryption)
 * - Local member state (leaf index, signing key, encryption key)
 * - Transcript hash for next epoch
 *
 * The state is serialized using TLS encoding (compact binary, same codec
 * used throughout the MLS stack) for efficient storage.
 *
 * Security: This blob contains secret key material (signing key, encryption key,
 * epoch secrets). It MUST be stored in encrypted local storage.
 *
 * [senderRatchetStates] carries each sender's live SecretTree ratchet position
 * (RFC 9420 §9). Preserving it is what stops the restored local member from
 * re-emitting an already-used generation within the same epoch — see
 * [com.vitorpamplona.quartz.marmot.mls.schedule.SecretTree.exportSenderStates].
 * It is optional (empty for STATE_VERSION 1 blobs) so older persisted state
 * still decodes.
 */
data class MlsGroupState(
    val groupContext: GroupContext,
    val treeBytes: ByteArray,
    val myLeafIndex: Int,
    val epochSecrets: EpochSecrets,
    val initSecret: ByteArray,
    val signingPrivateKey: ByteArray,
    val encryptionPrivateKey: ByteArray,
    val interimTranscriptHash: ByteArray,
    val encryptionSecret: ByteArray,
    val senderRatchetStates: Map<Int, SenderRatchetState> = emptyMap(),
) {
    fun encodeTls(): ByteArray {
        val writer = TlsWriter()

        // Version tag for future-proofing
        writer.putUint16(STATE_VERSION)

        // GroupContext
        groupContext.encodeTls(writer)

        // Ratchet tree (already TLS-encoded)
        writer.putOpaqueVarInt(treeBytes)

        // Local member state
        writer.putUint32(myLeafIndex.toLong())

        // Epoch secrets (all 12 fields)
        writer.putOpaqueVarInt(epochSecrets.joinerSecret)
        writer.putOpaqueVarInt(epochSecrets.welcomeSecret)
        writer.putOpaqueVarInt(epochSecrets.epochSecret)
        writer.putOpaqueVarInt(epochSecrets.senderDataSecret)
        writer.putOpaqueVarInt(epochSecrets.encryptionSecret)
        writer.putOpaqueVarInt(epochSecrets.exporterSecret)
        writer.putOpaqueVarInt(epochSecrets.epochAuthenticator)
        writer.putOpaqueVarInt(epochSecrets.externalSecret)
        writer.putOpaqueVarInt(epochSecrets.confirmationKey)
        writer.putOpaqueVarInt(epochSecrets.membershipKey)
        writer.putOpaqueVarInt(epochSecrets.resumptionPsk)
        writer.putOpaqueVarInt(epochSecrets.initSecret)

        // Init secret for next epoch
        writer.putOpaqueVarInt(initSecret)

        // Private keys
        writer.putOpaqueVarInt(signingPrivateKey)
        writer.putOpaqueVarInt(encryptionPrivateKey)

        // Transcript state
        writer.putOpaqueVarInt(interimTranscriptHash)

        // Encryption secret for SecretTree reconstruction
        writer.putOpaqueVarInt(encryptionSecret)

        // Per-sender SecretTree ratchet positions (STATE_VERSION 2+).
        // Preserving the local sender's generation counter is what prevents
        // AEAD key+nonce reuse (and strict-receiver rejection) after a restore.
        writer.putUint32(senderRatchetStates.size.toLong())
        for ((leafIndex, ratchet) in senderRatchetStates) {
            writer.putUint32(leafIndex.toLong())
            writer.putOpaqueVarInt(ratchet.handshakeSecret)
            writer.putUint32(ratchet.handshakeGeneration.toLong())
            writer.putOpaqueVarInt(ratchet.applicationSecret)
            writer.putUint32(ratchet.applicationGeneration.toLong())
        }

        return writer.toByteArray()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MlsGroupState) return false
        return groupContext == other.groupContext && myLeafIndex == other.myLeafIndex
    }

    override fun hashCode(): Int {
        var result = groupContext.hashCode()
        result = 31 * result + myLeafIndex
        return result
    }

    companion object {
        /**
         * v1: original layout (no SecretTree ratchet positions).
         * v2: appends [senderRatchetStates] so restores don't reset the
         *     ratchet to generation 0. v1 blobs still decode (empty map).
         */
        private const val STATE_VERSION = 2

        fun decodeTls(data: ByteArray): MlsGroupState {
            val reader = TlsReader(data)

            val version = reader.readUint16()
            require(version in 1..STATE_VERSION) { "Unsupported state version: $version" }

            val groupContext = GroupContext.decodeTls(reader)
            val treeBytes = reader.readOpaqueVarInt()
            val myLeafIndex = reader.readUint32().toInt()

            val epochSecrets =
                EpochSecrets(
                    joinerSecret = reader.readOpaqueVarInt(),
                    welcomeSecret = reader.readOpaqueVarInt(),
                    epochSecret = reader.readOpaqueVarInt(),
                    senderDataSecret = reader.readOpaqueVarInt(),
                    encryptionSecret = reader.readOpaqueVarInt(),
                    exporterSecret = reader.readOpaqueVarInt(),
                    epochAuthenticator = reader.readOpaqueVarInt(),
                    externalSecret = reader.readOpaqueVarInt(),
                    confirmationKey = reader.readOpaqueVarInt(),
                    membershipKey = reader.readOpaqueVarInt(),
                    resumptionPsk = reader.readOpaqueVarInt(),
                    initSecret = reader.readOpaqueVarInt(),
                )

            val initSecret = reader.readOpaqueVarInt()
            val signingPrivateKey = reader.readOpaqueVarInt()
            val encryptionPrivateKey = reader.readOpaqueVarInt()
            val interimTranscriptHash = reader.readOpaqueVarInt()
            val encryptionSecret = reader.readOpaqueVarInt()

            // v2+: per-sender SecretTree ratchet positions. Absent (or an
            // empty count) for v1 blobs, which restore at generation 0.
            val senderRatchetStates =
                if (version >= 2 && reader.hasRemaining) {
                    val count = reader.readUint32().toInt()
                    buildMap {
                        repeat(count) {
                            val leafIndex = reader.readUint32().toInt()
                            val handshakeSecret = reader.readOpaqueVarInt()
                            val handshakeGeneration = reader.readUint32().toInt()
                            val applicationSecret = reader.readOpaqueVarInt()
                            val applicationGeneration = reader.readUint32().toInt()
                            put(
                                leafIndex,
                                SenderRatchetState(
                                    handshakeSecret = handshakeSecret,
                                    handshakeGeneration = handshakeGeneration,
                                    applicationSecret = applicationSecret,
                                    applicationGeneration = applicationGeneration,
                                ),
                            )
                        }
                    }
                } else {
                    emptyMap()
                }

            return MlsGroupState(
                groupContext = groupContext,
                treeBytes = treeBytes,
                myLeafIndex = myLeafIndex,
                epochSecrets = epochSecrets,
                initSecret = initSecret,
                signingPrivateKey = signingPrivateKey,
                encryptionPrivateKey = encryptionPrivateKey,
                interimTranscriptHash = interimTranscriptHash,
                encryptionSecret = encryptionSecret,
                senderRatchetStates = senderRatchetStates,
            )
        }
    }
}

/**
 * A retained epoch's decryption secrets for processing late-arriving messages.
 *
 * When an epoch transition occurs, the previous epoch's secrets are kept
 * in a bounded window so messages that arrive out of order can still be
 * decrypted. Only the minimum secrets needed for decryption are retained.
 */
data class RetainedEpochSecrets(
    val epoch: Long,
    val senderDataSecret: ByteArray,
    val encryptionSecret: ByteArray,
    val leafCount: Int,
    val exporterSecret: ByteArray = ByteArray(0),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RetainedEpochSecrets) return false
        return epoch == other.epoch
    }

    override fun hashCode(): Int = epoch.hashCode()

    fun encodeTls(writer: TlsWriter) {
        writer.putUint64(epoch)
        writer.putOpaqueVarInt(senderDataSecret)
        writer.putOpaqueVarInt(encryptionSecret)
        writer.putUint32(leafCount.toLong())
        writer.putOpaqueVarInt(exporterSecret)
    }

    companion object {
        fun decodeTls(reader: TlsReader): RetainedEpochSecrets {
            val epoch = reader.readUint64()
            val senderDataSecret = reader.readOpaqueVarInt()
            val encryptionSecret = reader.readOpaqueVarInt()
            val leafCount = reader.readUint32().toInt()
            // exporterSecret was added later; tolerate its absence in older serialized data
            val exporterSecret =
                if (reader.hasRemaining) {
                    reader.readOpaqueVarInt()
                } else {
                    ByteArray(0)
                }
            return RetainedEpochSecrets(
                epoch = epoch,
                senderDataSecret = senderDataSecret,
                encryptionSecret = encryptionSecret,
                leafCount = leafCount,
                exporterSecret = exporterSecret,
            )
        }
    }
}
