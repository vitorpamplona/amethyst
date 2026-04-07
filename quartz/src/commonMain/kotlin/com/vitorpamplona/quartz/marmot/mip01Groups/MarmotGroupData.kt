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
package com.vitorpamplona.quartz.marmot.mip01Groups

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter
import com.vitorpamplona.quartz.marmot.mls.tree.Extension
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey

/**
 * Marmot Group Data Extension (MIP-01) — extension ID 0xF2EE.
 *
 * This data model represents the NostrGroupData structure carried inside
 * the MLS GroupContext.extensions. It links MLS group encryption with
 * Nostr's decentralized identity system.
 *
 * The actual TLS serialization/deserialization is handled by the MLS engine.
 * This class provides a Kotlin-friendly representation for the application layer.
 *
 * Wire format (TLS presentation language):
 * ```
 * struct {
 *     uint16 version;                    // Current: 2
 *     opaque nostr_group_id[32];         // Nostr routing ID (distinct from MLS group ID)
 *     opaque name<0..2^16-1>;
 *     opaque description<0..2^16-1>;
 *     opaque admin_pubkeys<0..2^16-1>;   // Concatenated raw 32-byte x-only pubkeys
 *     RelayUrl relays<0..2^16-1>;
 *     opaque image_hash<0..32>;
 *     opaque image_key<0..32>;           // HKDF seed for encryption key derivation
 *     opaque image_nonce<0..12>;
 *     opaque image_upload_key<0..32>;    // HKDF seed for upload keypair derivation
 * } NostrGroupData;
 * ```
 */
@Immutable
data class MarmotGroupData(
    /** Extension format version. Current: 2 */
    val version: Int = CURRENT_VERSION,
    /**
     * 32-byte Nostr routing ID (hex-encoded).
     * Distinct from the private MLS group ID. Used in "h" tags of kind:445 events.
     * MUST be cryptographically random when initially generated.
     */
    val nostrGroupId: HexKey,
    /** UTF-8 encoded group name. Empty string for unnamed groups. */
    val name: String = "",
    /** UTF-8 encoded group description. Empty string if not set. */
    val description: String = "",
    /**
     * Admin public keys (hex-encoded, 32-byte x-only secp256k1 pubkeys).
     * At least one admin key MUST be present for most group operations.
     * MUST NOT contain duplicates.
     */
    val adminPubkeys: List<HexKey> = emptyList(),
    /** Relay URLs for group message distribution. SHOULD contain at least one. */
    val relays: List<String> = emptyList(),
    /** SHA-256 hash of the encrypted group image (hex). Empty if no image. */
    val imageHash: HexKey? = null,
    /** HKDF seed for deriving the image encryption key. Empty if no image. */
    val imageKey: ByteArray? = null,
    /** ChaCha20-Poly1305 nonce for image encryption. Empty if no image. */
    val imageNonce: ByteArray? = null,
    /** HKDF seed for deriving the Blossom upload keypair. Empty if no image. */
    val imageUploadKey: ByteArray? = null,
) {
    /** Whether the given pubkey is an admin of this group */
    fun isAdmin(pubKey: HexKey): Boolean = adminPubkeys.contains(pubKey)

    /** Whether this group has an encrypted image set */
    fun hasImage(): Boolean = imageHash != null && imageKey != null && imageNonce != null

    /**
     * Encode this MarmotGroupData to TLS wire format bytes.
     * Mirrors the [decodeTls] format.
     */
    fun encodeTls(): ByteArray {
        val writer = TlsWriter()
        writer.putUint16(version)
        writer.putBytes(nostrGroupId.hexToByteArray())
        writer.putOpaque2(name.encodeToByteArray())
        writer.putOpaque2(description.encodeToByteArray())

        // Admin pubkeys: concatenated 32-byte keys within a length-prefixed block
        val adminBytes = ByteArray(adminPubkeys.size * 32)
        adminPubkeys.forEachIndexed { index, key ->
            key.hexToByteArray().copyInto(adminBytes, index * 32)
        }
        writer.putOpaque2(adminBytes)

        // Relays: length-prefixed block of length-prefixed UTF-8 strings
        val relayWriter = TlsWriter()
        for (relay in relays) {
            relayWriter.putOpaque2(relay.encodeToByteArray())
        }
        writer.putOpaque2(relayWriter.toByteArray())

        // Optional image fields
        writer.putOpaque2(imageHash?.hexToByteArray() ?: ByteArray(0))
        writer.putOpaque2(imageKey ?: ByteArray(0))
        writer.putOpaque2(imageNonce ?: ByteArray(0))
        writer.putOpaque2(imageUploadKey ?: ByteArray(0))

        return writer.toByteArray()
    }

    /**
     * Convert this MarmotGroupData to an MLS Extension for use in GroupContextExtensions proposals.
     */
    fun toExtension(): Extension = Extension(EXTENSION_ID_INT, encodeTls())

    companion object {
        const val CURRENT_VERSION = 2

        /** MLS extension type identifier for marmot_group_data */
        const val EXTENSION_ID: UShort = 0xF2EEu
        const val EXTENSION_ID_INT: Int = 0xF2EE

        /**
         * Find and decode the MarmotGroupData extension from a list of MLS extensions.
         * Returns null if no extension with type 0xF2EE is present.
         */
        fun fromExtensions(extensions: List<Extension>): MarmotGroupData? {
            val ext = extensions.find { it.extensionType == EXTENSION_ID_INT } ?: return null
            return decodeTls(ext.extensionData)
        }

        /**
         * Decode MarmotGroupData from TLS wire format bytes.
         *
         * Wire format:
         * ```
         * uint16 version
         * opaque nostr_group_id[32]
         * opaque name<0..2^16-1>
         * opaque description<0..2^16-1>
         * opaque admin_pubkeys<0..2^16-1>   // concatenated 32-byte keys
         * RelayUrl relays<0..2^16-1>        // length-prefixed UTF-8 strings
         * opaque image_hash<0..32>
         * opaque image_key<0..32>
         * opaque image_nonce<0..12>
         * opaque image_upload_key<0..32>
         * ```
         */
        fun decodeTls(data: ByteArray): MarmotGroupData? =
            try {
                val reader = TlsReader(data)
                val version = reader.readUint16()

                val nostrGroupIdBytes = reader.readBytes(32)
                val nostrGroupId = nostrGroupIdBytes.toHexKey()

                val nameBytes = reader.readOpaque2()
                val name = nameBytes.decodeToString()

                val descriptionBytes = reader.readOpaque2()
                val description = descriptionBytes.decodeToString()

                // Admin pubkeys: concatenated 32-byte keys within a length-prefixed block
                val adminBlock = reader.readOpaque2()
                val adminPubkeys = mutableListOf<HexKey>()
                var i = 0
                while (i + 32 <= adminBlock.size) {
                    adminPubkeys.add(adminBlock.copyOfRange(i, i + 32).toHexKey())
                    i += 32
                }

                // Relays: length-prefixed block of length-prefixed UTF-8 strings
                val relaysBlock = reader.readOpaque2()
                val relays = mutableListOf<String>()
                val relayReader = TlsReader(relaysBlock)
                while (relayReader.hasRemaining) {
                    val relayBytes = relayReader.readOpaque2()
                    relays.add(relayBytes.decodeToString())
                }

                // Optional fields — read if remaining
                val imageHash = if (reader.hasRemaining) reader.readOpaque2().takeIf { it.isNotEmpty() }?.toHexKey() else null
                val imageKey = if (reader.hasRemaining) reader.readOpaque2().takeIf { it.isNotEmpty() } else null
                val imageNonce = if (reader.hasRemaining) reader.readOpaque2().takeIf { it.isNotEmpty() } else null
                val imageUploadKey = if (reader.hasRemaining) reader.readOpaque2().takeIf { it.isNotEmpty() } else null

                MarmotGroupData(
                    version = version,
                    nostrGroupId = nostrGroupId,
                    name = name,
                    description = description,
                    adminPubkeys = adminPubkeys,
                    relays = relays,
                    imageHash = imageHash,
                    imageKey = imageKey,
                    imageNonce = imageNonce,
                    imageUploadKey = imageUploadKey,
                )
            } catch (e: Exception) {
                null
            }
    }
}
