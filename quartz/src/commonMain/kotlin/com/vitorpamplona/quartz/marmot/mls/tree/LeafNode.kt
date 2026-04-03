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
package com.vitorpamplona.quartz.marmot.mls.tree

import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.codec.TlsSerializable
import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter

/**
 * MLS Credential (RFC 9420 Section 5.3).
 * For Marmot, we use BasicCredential with the Nostr public key.
 */
sealed class Credential : TlsSerializable {
    data class Basic(
        val identity: ByteArray,
    ) : Credential() {
        override fun encodeTls(writer: TlsWriter) {
            writer.putUint16(CREDENTIAL_TYPE_BASIC)
            writer.putOpaqueVarInt(identity)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Basic) return false
            return identity.contentEquals(other.identity)
        }

        override fun hashCode(): Int = identity.contentHashCode()
    }

    companion object {
        const val CREDENTIAL_TYPE_BASIC = 1
        const val CREDENTIAL_TYPE_X509 = 2

        fun decodeTls(reader: TlsReader): Credential {
            val type = reader.readUint16()
            return when (type) {
                CREDENTIAL_TYPE_BASIC -> Basic(reader.readOpaqueVarInt())
                else -> throw IllegalArgumentException("Unknown credential type: $type")
            }
        }
    }
}

/**
 * MLS Capabilities (RFC 9420 Section 7.2).
 * Advertises supported versions, ciphersuites, extensions, proposals, and credentials.
 */
data class Capabilities(
    val versions: List<Int> = listOf(1), // MLS protocol version 1
    val ciphersuites: List<Int> = listOf(1), // 0x0001
    val extensions: List<Int> = emptyList(),
    val proposals: List<Int> = emptyList(),
    val credentials: List<Int> = listOf(Credential.CREDENTIAL_TYPE_BASIC),
) : TlsSerializable {
    override fun encodeTls(writer: TlsWriter) {
        // versions<V><2..255>
        val versionsWriter = TlsWriter()
        for (v in versions) versionsWriter.putUint16(v)
        writer.putOpaqueVarInt(versionsWriter.toByteArray())

        // ciphersuites<V><2..255>
        val csWriter = TlsWriter()
        for (cs in ciphersuites) csWriter.putUint16(cs)
        writer.putOpaqueVarInt(csWriter.toByteArray())

        // extensions<V><2..255>
        val extWriter = TlsWriter()
        for (e in extensions) extWriter.putUint16(e)
        writer.putOpaqueVarInt(extWriter.toByteArray())

        // proposals<V><2..255>
        val propWriter = TlsWriter()
        for (p in proposals) propWriter.putUint16(p)
        writer.putOpaqueVarInt(propWriter.toByteArray())

        // credentials<V><2..255>
        val credWriter = TlsWriter()
        for (c in credentials) credWriter.putUint16(c)
        writer.putOpaqueVarInt(credWriter.toByteArray())
    }

    companion object {
        fun decodeTls(reader: TlsReader): Capabilities {
            fun readUint16List(data: ByteArray): List<Int> {
                val r = TlsReader(data)
                val list = mutableListOf<Int>()
                while (r.hasRemaining) list.add(r.readUint16())
                return list
            }

            return Capabilities(
                versions = readUint16List(reader.readOpaqueVarInt()),
                ciphersuites = readUint16List(reader.readOpaqueVarInt()),
                extensions = readUint16List(reader.readOpaqueVarInt()),
                proposals = readUint16List(reader.readOpaqueVarInt()),
                credentials = readUint16List(reader.readOpaqueVarInt()),
            )
        }
    }
}

/**
 * MLS Extension (RFC 9420 Section 7.2).
 * Generic extension container for extensibility.
 */
data class Extension(
    val extensionType: Int,
    val extensionData: ByteArray,
) : TlsSerializable {
    override fun encodeTls(writer: TlsWriter) {
        writer.putUint16(extensionType)
        writer.putOpaqueVarInt(extensionData)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Extension) return false
        return extensionType == other.extensionType && extensionData.contentEquals(other.extensionData)
    }

    override fun hashCode(): Int {
        var result = extensionType
        result = 31 * result + extensionData.contentHashCode()
        return result
    }

    companion object {
        fun decodeTls(reader: TlsReader): Extension =
            Extension(
                extensionType = reader.readUint16(),
                extensionData = reader.readOpaqueVarInt(),
            )
    }
}

/**
 * Lifetime extension for LeafNode validity period.
 */
data class Lifetime(
    val notBefore: Long,
    val notAfter: Long,
) : TlsSerializable {
    override fun encodeTls(writer: TlsWriter) {
        writer.putUint64(notBefore)
        writer.putUint64(notAfter)
    }

    companion object {
        fun decodeTls(reader: TlsReader): Lifetime =
            Lifetime(
                notBefore = reader.readUint64(),
                notAfter = reader.readUint64(),
            )
    }
}

/**
 * MLS LeafNode (RFC 9420 Section 7.2).
 *
 * Represents a group member in the ratchet tree. Contains:
 * - HPKE encryption key (for TreeKEM path secret distribution)
 * - Ed25519 signature key (for authenticating messages)
 * - Credential (identity binding)
 * - Capabilities and extensions
 * - Signature over the LeafNodeTBS
 *
 * ```
 * struct {
 *     HPKEPublicKey encryption_key;
 *     SignaturePublicKey signature_key;
 *     Credential credential;
 *     Capabilities capabilities;
 *     LeafNodeSource leaf_node_source;
 *     // select (leaf_node_source) {
 *     //   case key_package: Lifetime lifetime;
 *     //   case update: <empty>;
 *     //   case commit: <empty>;
 *     // }
 *     Extension extensions<V>;
 *     opaque signature<V>;
 * } LeafNode;
 * ```
 */
data class LeafNode(
    val encryptionKey: ByteArray,
    val signatureKey: ByteArray,
    val credential: Credential,
    val capabilities: Capabilities,
    val leafNodeSource: LeafNodeSource,
    val lifetime: Lifetime?,
    val parentHash: ByteArray? = null,
    val extensions: List<Extension>,
    val signature: ByteArray,
) : TlsSerializable {
    override fun encodeTls(writer: TlsWriter) {
        writer.putOpaqueVarInt(encryptionKey)
        writer.putOpaqueVarInt(signatureKey)
        writer.putStruct(credential)
        writer.putStruct(capabilities)
        writer.putUint8(leafNodeSource.value)

        when (leafNodeSource) {
            LeafNodeSource.KEY_PACKAGE -> {
                lifetime?.encodeTls(writer) ?: Lifetime(0, 0).encodeTls(writer)
            }

            LeafNodeSource.UPDATE -> {}

            // empty
            LeafNodeSource.COMMIT -> {
                writer.putOpaqueVarInt(parentHash ?: ByteArray(0))
            }
        }

        writer.putVectorVarInt(extensions)
        writer.putOpaqueVarInt(signature)
    }

    /**
     * Encode LeafNodeTBS (To-Be-Signed) for signature verification.
     * Includes all fields except the signature itself, plus context-dependent
     * group_id and leaf_index for update/commit sources.
     */
    fun encodeTbs(
        groupId: ByteArray? = null,
        leafIndex: Int? = null,
    ): ByteArray {
        val writer = TlsWriter()
        writer.putOpaqueVarInt(encryptionKey)
        writer.putOpaqueVarInt(signatureKey)
        writer.putStruct(credential)
        writer.putStruct(capabilities)
        writer.putUint8(leafNodeSource.value)

        when (leafNodeSource) {
            LeafNodeSource.KEY_PACKAGE -> {
                lifetime?.encodeTls(writer) ?: Lifetime(0, 0).encodeTls(writer)
            }

            LeafNodeSource.UPDATE -> {}

            LeafNodeSource.COMMIT -> {
                writer.putOpaqueVarInt(parentHash ?: ByteArray(0))
            }
        }

        writer.putVectorVarInt(extensions)

        // Context for update/commit
        if (leafNodeSource != LeafNodeSource.KEY_PACKAGE) {
            requireNotNull(groupId) { "group_id required for update/commit LeafNode" }
            requireNotNull(leafIndex) { "leaf_index required for update/commit LeafNode" }
            writer.putOpaqueVarInt(groupId)
            writer.putUint32(leafIndex.toLong())
        }

        return writer.toByteArray()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LeafNode) return false
        return encryptionKey.contentEquals(other.encryptionKey) &&
            signatureKey.contentEquals(other.signatureKey) &&
            credential == other.credential &&
            capabilities == other.capabilities &&
            leafNodeSource == other.leafNodeSource &&
            lifetime == other.lifetime &&
            extensions == other.extensions &&
            signature.contentEquals(other.signature)
    }

    override fun hashCode(): Int {
        var result = encryptionKey.contentHashCode()
        result = 31 * result + signatureKey.contentHashCode()
        result = 31 * result + credential.hashCode()
        return result
    }

    companion object {
        fun decodeTls(reader: TlsReader): LeafNode {
            val encryptionKey = reader.readOpaqueVarInt()
            val signatureKey = reader.readOpaqueVarInt()
            val credential = Credential.decodeTls(reader)
            val capabilities = Capabilities.decodeTls(reader)
            val source = LeafNodeSource.fromValue(reader.readUint8())

            var lifetime: Lifetime? = null
            var parentHash: ByteArray? = null

            when (source) {
                LeafNodeSource.KEY_PACKAGE -> {
                    lifetime = Lifetime.decodeTls(reader)
                }

                LeafNodeSource.UPDATE -> {}

                // empty
                LeafNodeSource.COMMIT -> {
                    parentHash = reader.readOpaqueVarInt()
                }
            }

            val extensions = reader.readVectorVarInt { Extension.decodeTls(it) }
            val signature = reader.readOpaqueVarInt()

            return LeafNode(
                encryptionKey = encryptionKey,
                signatureKey = signatureKey,
                credential = credential,
                capabilities = capabilities,
                leafNodeSource = source,
                lifetime = lifetime,
                parentHash = parentHash,
                extensions = extensions,
                signature = signature,
            )
        }
    }
}

enum class LeafNodeSource(
    val value: Int,
) {
    KEY_PACKAGE(1),
    UPDATE(2),
    COMMIT(3),
    ;

    companion object {
        fun fromValue(value: Int): LeafNodeSource =
            entries.find { it.value == value }
                ?: throw IllegalArgumentException("Unknown LeafNodeSource: $value")
    }
}
