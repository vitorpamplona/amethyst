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
import com.vitorpamplona.quartz.marmot.mls.tree.Extension
import com.vitorpamplona.quartz.marmot.mls.tree.LeafNode

/**
 * MLS Proposal types (RFC 9420 Section 12).
 *
 * Proposals represent requested changes to group state. They must be
 * collected into a Commit to take effect.
 *
 * Marmot uses: Add, Remove, Update, SelfRemove (custom), GroupContextExtensions.
 */
enum class ProposalType(
    val value: Int,
) {
    ADD(1),
    UPDATE(2),
    REMOVE(3),
    PSK(4),
    REINIT(5),
    EXTERNAL_INIT(6),
    GROUP_CONTEXT_EXTENSIONS(7),

    // Marmot custom proposal types (private-use range 0xF000-0xFFFF)
    SELF_REMOVE(0xF001),
    ;

    companion object {
        fun fromValue(value: Int): ProposalType =
            entries.find { it.value == value }
                ?: throw IllegalArgumentException("Unknown ProposalType: $value")
    }
}

/**
 * MLS Proposal (RFC 9420 Section 12).
 */
sealed class Proposal : TlsSerializable {
    abstract val proposalType: ProposalType

    /**
     * Add proposal: includes a KeyPackage for the member to be added.
     */
    data class Add(
        val keyPackage: MlsKeyPackage,
    ) : Proposal() {
        override val proposalType = ProposalType.ADD

        override fun encodeTls(writer: TlsWriter) {
            writer.putUint16(proposalType.value)
            keyPackage.encodeTls(writer)
        }
    }

    /**
     * Update proposal: member updates their own LeafNode (new encryption key, etc.)
     */
    data class Update(
        val leafNode: LeafNode,
    ) : Proposal() {
        override val proposalType = ProposalType.UPDATE

        override fun encodeTls(writer: TlsWriter) {
            writer.putUint16(proposalType.value)
            leafNode.encodeTls(writer)
        }
    }

    /**
     * Remove proposal: remove a member by their leaf index.
     */
    data class Remove(
        val removedLeafIndex: Int,
    ) : Proposal() {
        override val proposalType = ProposalType.REMOVE

        override fun encodeTls(writer: TlsWriter) {
            writer.putUint16(proposalType.value)
            writer.putUint32(removedLeafIndex.toLong())
        }
    }

    /**
     * SelfRemove proposal (Marmot extension): member removes themselves.
     * Sent as a PublicMessage since the sender needs to authenticate it.
     */
    class SelfRemove : Proposal() {
        override val proposalType = ProposalType.SELF_REMOVE

        override fun encodeTls(writer: TlsWriter) {
            writer.putUint16(proposalType.value)
        }
    }

    /**
     * GroupContextExtensions proposal: update the group's extensions
     * (e.g., change group name/description via MarmotGroupData extension).
     */
    data class GroupContextExtensions(
        val extensions: List<Extension>,
    ) : Proposal() {
        override val proposalType = ProposalType.GROUP_CONTEXT_EXTENSIONS

        override fun encodeTls(writer: TlsWriter) {
            writer.putUint16(proposalType.value)
            writer.putVectorVarInt(extensions)
        }
    }

    /**
     * PSK proposal: include a pre-shared key in the epoch.
     */
    data class Psk(
        val pskType: Int,
        val pskId: ByteArray,
        val pskNonce: ByteArray,
    ) : Proposal() {
        override val proposalType = ProposalType.PSK

        override fun encodeTls(writer: TlsWriter) {
            writer.putUint16(proposalType.value)
            writer.putUint8(pskType)
            writer.putOpaqueVarInt(pskId)
            writer.putOpaqueVarInt(pskNonce)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Psk) return false
            return pskId.contentEquals(other.pskId)
        }

        override fun hashCode(): Int = pskId.contentHashCode()
    }

    /**
     * ReInit proposal: reinitialize the group with new parameters (RFC 9420 Section 12.1.5).
     */
    data class ReInit(
        val groupId: ByteArray,
        val version: Int,
        val cipherSuite: Int,
        val extensions: List<Extension>,
    ) : Proposal() {
        override val proposalType = ProposalType.REINIT

        override fun encodeTls(writer: TlsWriter) {
            writer.putUint16(proposalType.value)
            writer.putOpaqueVarInt(groupId)
            writer.putUint16(version)
            writer.putUint16(cipherSuite)
            writer.putVectorVarInt(extensions)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ReInit) return false
            return groupId.contentEquals(other.groupId) && version == other.version && cipherSuite == other.cipherSuite
        }

        override fun hashCode(): Int = groupId.contentHashCode()
    }

    /**
     * ExternalInit proposal: allows an external party to join via external commit (RFC 9420 Section 12.1.6).
     */
    data class ExternalInit(
        val kemOutput: ByteArray,
    ) : Proposal() {
        override val proposalType = ProposalType.EXTERNAL_INIT

        override fun encodeTls(writer: TlsWriter) {
            writer.putUint16(proposalType.value)
            writer.putOpaqueVarInt(kemOutput)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ExternalInit) return false
            return kemOutput.contentEquals(other.kemOutput)
        }

        override fun hashCode(): Int = kemOutput.contentHashCode()
    }

    companion object {
        fun decodeTls(reader: TlsReader): Proposal {
            val type = ProposalType.fromValue(reader.readUint16())
            return when (type) {
                ProposalType.ADD -> {
                    Add(MlsKeyPackage.decodeTls(reader))
                }

                ProposalType.UPDATE -> {
                    Update(LeafNode.decodeTls(reader))
                }

                ProposalType.REMOVE -> {
                    Remove(reader.readUint32().toInt())
                }

                ProposalType.SELF_REMOVE -> {
                    SelfRemove()
                }

                ProposalType.GROUP_CONTEXT_EXTENSIONS -> {
                    GroupContextExtensions(reader.readVectorVarInt { Extension.decodeTls(it) })
                }

                ProposalType.PSK -> {
                    Psk(reader.readUint8(), reader.readOpaqueVarInt(), reader.readOpaqueVarInt())
                }

                ProposalType.REINIT -> {
                    ReInit(
                        groupId = reader.readOpaqueVarInt(),
                        version = reader.readUint16(),
                        cipherSuite = reader.readUint16(),
                        extensions = reader.readVectorVarInt { Extension.decodeTls(it) },
                    )
                }

                ProposalType.EXTERNAL_INIT -> {
                    ExternalInit(reader.readOpaqueVarInt())
                }
            }
        }
    }
}

/**
 * ProposalOrRef: a Proposal can be included inline or by reference (hash).
 */
sealed class ProposalOrRef : TlsSerializable {
    data class Inline(
        val proposal: Proposal,
    ) : ProposalOrRef() {
        override fun encodeTls(writer: TlsWriter) {
            writer.putUint8(1) // proposal
            proposal.encodeTls(writer)
        }
    }

    data class Reference(
        val proposalRef: ByteArray,
    ) : ProposalOrRef() {
        override fun encodeTls(writer: TlsWriter) {
            writer.putUint8(2) // reference
            writer.putOpaqueVarInt(proposalRef)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Reference) return false
            return proposalRef.contentEquals(other.proposalRef)
        }

        override fun hashCode(): Int = proposalRef.contentHashCode()
    }

    companion object {
        fun decodeTls(reader: TlsReader): ProposalOrRef {
            val type = reader.readUint8()
            return when (type) {
                1 -> Inline(Proposal.decodeTls(reader))
                2 -> Reference(reader.readOpaqueVarInt())
                else -> throw IllegalArgumentException("Unknown ProposalOrRef type: $type")
            }
        }
    }
}
