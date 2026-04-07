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
import com.vitorpamplona.quartz.marmot.mls.crypto.MlsCryptoProvider
import com.vitorpamplona.quartz.marmot.mls.tree.Extension
import com.vitorpamplona.quartz.marmot.mls.tree.LeafNode

/**
 * MLS KeyPackage (RFC 9420 Section 10).
 *
 * Published by users who want to be added to groups. Contains:
 * - Protocol version and ciphersuite
 * - HPKE init_key (one-time use for Welcome message encryption)
 * - LeafNode (credential, encryption key, capabilities)
 * - Extensions
 * - Signature over KeyPackageTBS
 *
 * ```
 * struct {
 *     ProtocolVersion version;
 *     CipherSuite cipher_suite;
 *     HPKEPublicKey init_key;
 *     LeafNode leaf_node;
 *     Extension extensions<V>;
 *     opaque signature<V>;
 * } KeyPackage;
 * ```
 */
data class MlsKeyPackage(
    val version: Int = 1,
    val cipherSuite: Int = 1,
    val initKey: ByteArray,
    val leafNode: LeafNode,
    val extensions: List<Extension> = emptyList(),
    val signature: ByteArray,
) : TlsSerializable {
    override fun encodeTls(writer: TlsWriter) {
        writer.putUint16(version)
        writer.putUint16(cipherSuite)
        writer.putOpaqueVarInt(initKey)
        leafNode.encodeTls(writer)
        writer.putVectorVarInt(extensions)
        writer.putOpaqueVarInt(signature)
    }

    /**
     * Compute the KeyPackage reference (RFC 9420 Section 5.2).
     * Used to identify which KeyPackage was consumed.
     */
    fun reference(): ByteArray {
        val encoded = toTlsBytes()
        return MlsCryptoProvider.refHash("MLS 1.0 KeyPackage Reference", encoded)
    }

    /**
     * Encode the TBS (to-be-signed) portion for signature verification.
     */
    fun encodeTbs(): ByteArray {
        val writer = TlsWriter()
        writer.putUint16(version)
        writer.putUint16(cipherSuite)
        writer.putOpaqueVarInt(initKey)
        leafNode.encodeTls(writer)
        writer.putVectorVarInt(extensions)
        return writer.toByteArray()
    }

    /**
     * Verify the KeyPackage signature (RFC 9420 Section 10).
     * The signature is over KeyPackageTBS using the LeafNode's signature key.
     */
    fun verifySignature(): Boolean {
        val tbs = encodeTbs()
        return MlsCryptoProvider.verifyWithLabel(
            leafNode.signatureKey,
            "KeyPackageTBS",
            tbs,
            signature,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MlsKeyPackage) return false
        return version == other.version &&
            cipherSuite == other.cipherSuite &&
            initKey.contentEquals(other.initKey) &&
            leafNode == other.leafNode
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + cipherSuite
        result = 31 * result + initKey.contentHashCode()
        return result
    }

    companion object {
        fun decodeTls(reader: TlsReader): MlsKeyPackage {
            val version = reader.readUint16()
            require(version == 1) { "Unsupported MLS version: $version" }
            val cipherSuite = reader.readUint16()
            require(cipherSuite == 1) { "Unsupported ciphersuite: $cipherSuite" }
            return MlsKeyPackage(
                version = version,
                cipherSuite = cipherSuite,
                initKey = reader.readOpaqueVarInt(),
                leafNode = LeafNode.decodeTls(reader),
                extensions = reader.readVectorVarInt { Extension.decodeTls(it) },
                signature = reader.readOpaqueVarInt(),
            )
        }
    }
}

/**
 * A KeyPackage bundled with its private keys (for the owner).
 * Never serialized to wire — kept locally for processing Welcome messages.
 */
data class KeyPackageBundle(
    val keyPackage: MlsKeyPackage,
    val initPrivateKey: ByteArray,
    val encryptionPrivateKey: ByteArray,
    val signaturePrivateKey: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyPackageBundle) return false
        return keyPackage == other.keyPackage
    }

    override fun hashCode(): Int = keyPackage.hashCode()
}
