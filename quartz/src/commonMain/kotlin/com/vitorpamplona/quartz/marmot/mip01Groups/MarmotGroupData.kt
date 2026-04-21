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
 * Wire format (TLS presentation language, v3):
 * ```
 * struct {
 *     uint16 version;                       // Current: 3 (v0 reserved/invalid)
 *     opaque nostr_group_id[32];            // Nostr routing ID (distinct from MLS group ID)
 *     opaque name<0..2^16-1>;
 *     opaque description<0..2^16-1>;
 *     opaque admin_pubkeys<0..2^16-1>;      // Concatenated raw 32-byte x-only pubkeys
 *     RelayUrl relays<0..2^16-1>;
 *     opaque image_hash<0..32>;
 *     opaque image_key<0..32>;              // HKDF seed for encryption key derivation
 *     opaque image_nonce<0..12>;
 *     opaque image_upload_key<0..32>;       // HKDF seed for upload keypair derivation
 *     opaque disappearing_message_secs<0..8>; // v3+: 0 bytes = persist forever,
 *                                             // 8 bytes big-endian uint64 = expiration secs
 *                                             // (value 0 is rejected)
 * } NostrGroupData;
 * ```
 */
@Immutable
data class MarmotGroupData(
    /** Extension format version. Current: 3 (v0 reserved/invalid). */
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
    /**
     * Disappearing-message duration in seconds (v3+).
     * `null` means messages persist forever. A positive value auto-applies a
     * NIP-40 `expiration` tag to kind:445 events at `created_at + secs`.
     * Per MIP-01, a value of `0` MUST be rejected.
     */
    val disappearingMessageSecs: ULong? = null,
) {
    init {
        require(version > 0) { "MarmotGroupData version 0 is reserved/invalid" }
        require(disappearingMessageSecs == null || disappearingMessageSecs > 0UL) {
            "disappearing_message_secs must be > 0 when set (MIP-01)"
        }
        require(adminPubkeys.size == adminPubkeys.toSet().size) {
            "MarmotGroupData.admin_pubkeys MUST NOT contain duplicates (MIP-01)"
        }
    }

    /** Whether the given pubkey is an admin of this group */
    fun isAdmin(pubKey: HexKey): Boolean = adminPubkeys.contains(pubKey)

    /** Whether this group has an encrypted image set */
    fun hasImage(): Boolean = imageHash != null && imageKey != null && imageNonce != null

    /**
     * Return a copy with [newRelays] unioned into [relays], de-duplicated and order-preserving.
     *
     * Every metadata-updating commit produced by the group creator or an admin SHOULD fold
     * its author's own outbox into [relays] so new invitees learn a single canonical relay
     * set for kind:445 from the Welcome, even when the inviter's outbox has drifted since
     * the last commit. Both the UI and the CLI need this same merge rule — hence living
     * on the data class.
     */
    fun withMergedRelays(newRelays: Collection<String>): MarmotGroupData {
        if (newRelays.isEmpty()) return this
        val merged = (relays + newRelays).distinct()
        return if (merged == relays) this else copy(relays = merged)
    }

    /**
     * Encode this MarmotGroupData to TLS wire format bytes.
     * Mirrors the [decodeTls] format.
     *
     * Per MIP-01, all variable-length vectors use QUIC-style variable-length integer
     * (VarInt) length prefixes, as implemented by the Rust `tls_codec` crate (v0.4+):
     * - lengths 0..63      → 1 byte  (high bits 00)
     * - lengths 64..16383  → 2 bytes (high bits 01)
     * - lengths 16384+     → 4 bytes (high bits 10)
     */
    fun encodeTls(): ByteArray {
        val writer = TlsWriter()
        writer.putUint16(version)
        writer.putBytes(nostrGroupId.hexToByteArray())
        writer.putOpaqueVarInt(name.encodeToByteArray())
        writer.putOpaqueVarInt(description.encodeToByteArray())

        // admin_pubkeys: Vec<[u8;32]> — outer VarInt covers total bytes, each 32-byte
        // key is fixed-size with no inner length prefix.
        val adminBytes = ByteArray(adminPubkeys.size * 32)
        adminPubkeys.forEachIndexed { index, key ->
            key.hexToByteArray().copyInto(adminBytes, index * 32)
        }
        writer.putOpaqueVarInt(adminBytes)

        // relays: Vec<Vec<u8>> — outer VarInt covers total bytes, each inner relay
        // string is VarInt-length-prefixed UTF-8.
        val relayWriter = TlsWriter()
        for (relay in relays) {
            relayWriter.putOpaqueVarInt(relay.encodeToByteArray())
        }
        writer.putOpaqueVarInt(relayWriter.toByteArray())

        // Optional image fields — empty Vec<u8> encodes as a single zero byte (VarInt(0)).
        writer.putOpaqueVarInt(imageHash?.hexToByteArray() ?: ByteArray(0))
        writer.putOpaqueVarInt(imageKey ?: ByteArray(0))
        writer.putOpaqueVarInt(imageNonce ?: ByteArray(0))
        writer.putOpaqueVarInt(imageUploadKey ?: ByteArray(0))

        // v3+: disappearing_message_secs (0 bytes = none, 8 bytes big-endian uint64 = secs).
        // Only emitted for version ≥ 3; v1/v2 have no such field, so omitting it keeps
        // the wire format byte-for-byte compatible with older implementations (MDK v2).
        if (version >= 3) {
            val disappearingBytes =
                disappearingMessageSecs?.let { secs ->
                    val out = ByteArray(8)
                    var v = secs.toLong()
                    for (i in 7 downTo 0) {
                        out[i] = (v and 0xFF).toByte()
                        v = v ushr 8
                    }
                    out
                } ?: ByteArray(0)
            writer.putOpaqueVarInt(disappearingBytes)
        }

        return writer.toByteArray()
    }

    /**
     * Convert this MarmotGroupData to an MLS Extension for use in GroupContextExtensions proposals.
     */
    fun toExtension(): Extension = Extension(EXTENSION_ID_INT, encodeTls())

    companion object {
        const val CURRENT_VERSION = 3

        /** Versions this implementation understands. v0 is reserved/invalid per MIP-01. */
        val SUPPORTED_VERSIONS: Set<Int> = setOf(1, 2, 3)

        /** MLS extension type identifier for marmot_group_data */
        const val EXTENSION_ID: UShort = 0xF2EEu
        const val EXTENSION_ID_INT: Int = 0xF2EE

        /**
         * Build a freshly-minted [MarmotGroupData] for a group with no prior metadata —
         * i.e. right after `MlsGroupManager.createGroup`. Creator becomes the sole admin
         * and their outbox relays are stamped as the group's relay set.
         *
         * Both the Android UI (`AccountViewModel.updateMarmotGroupMetadata`) and the
         * headless CLI need this same initial shape; keeping the factory here avoids
         * subtle drift between them.
         */
        fun bootstrap(
            nostrGroupId: HexKey,
            creatorPubKey: HexKey,
            outboxRelays: Collection<String>,
            name: String = "",
            description: String = "",
        ): MarmotGroupData =
            MarmotGroupData(
                nostrGroupId = nostrGroupId,
                name = name,
                description = description,
                adminPubkeys = listOf(creatorPubKey),
                relays = outboxRelays.distinct(),
            )

        /**
         * Find and decode the MarmotGroupData extension from a list of MLS extensions.
         * Returns null if no extension with type 0xF2EE is present or if decoding fails.
         */
        fun fromExtensions(extensions: List<Extension>): MarmotGroupData? {
            val ext = extensions.find { it.extensionType == EXTENSION_ID_INT } ?: return null
            return decodeTls(ext.extensionData)
        }

        /**
         * Decode MarmotGroupData from TLS wire format bytes.
         *
         * Per MIP-01, all variable-length vectors use QUIC-style VarInt length prefixes
         * (`tls_codec` v0.4+). The TLS comment syntax below uses `<V>` to denote VarInt.
         *
         * Wire format (v3):
         * ```
         * uint16 version                     // rejected if 0 or unsupported
         * opaque nostr_group_id[32]
         * opaque name<V>
         * opaque description<V>
         * opaque admin_pubkeys<V>            // concatenated 32-byte keys
         * RelayUrl relays<V>                 // VarInt-length-prefixed UTF-8 strings
         * opaque image_hash<V>
         * opaque image_key<V>
         * opaque image_nonce<V>
         * opaque image_upload_key<V>
         * opaque disappearing_message_secs<V> // v3+: 0 bytes or 8-byte uint64 (reject 0)
         * ```
         *
         * Unknown trailing bytes from future versions are silently ignored for
         * forward compatibility (MIP-01).
         */
        fun decodeTls(data: ByteArray): MarmotGroupData? =
            try {
                val reader = TlsReader(data)
                val version = reader.readUint16()
                require(version in SUPPORTED_VERSIONS) { "Unsupported MarmotGroupData version: $version" }

                val nostrGroupIdBytes = reader.readBytes(32)
                val nostrGroupId = nostrGroupIdBytes.toHexKey()

                val nameBytes = reader.readOpaqueVarInt()
                val name = nameBytes.decodeToString()

                val descriptionBytes = reader.readOpaqueVarInt()
                val description = descriptionBytes.decodeToString()

                // Admin pubkeys: concatenated 32-byte keys within a VarInt-prefixed block
                val adminBlock = reader.readOpaqueVarInt()
                val adminPubkeys = mutableListOf<HexKey>()
                var i = 0
                while (i + 32 <= adminBlock.size) {
                    adminPubkeys.add(adminBlock.copyOfRange(i, i + 32).toHexKey())
                    i += 32
                }

                // Relays: VarInt-prefixed block of VarInt-prefixed UTF-8 strings
                val relaysBlock = reader.readOpaqueVarInt()
                val relays = mutableListOf<String>()
                val relayReader = TlsReader(relaysBlock)
                while (relayReader.hasRemaining) {
                    val relayBytes = relayReader.readOpaqueVarInt()
                    relays.add(relayBytes.decodeToString())
                }

                // Optional fields — read if remaining
                val imageHash = if (reader.hasRemaining) reader.readOpaqueVarInt().takeIf { it.isNotEmpty() }?.toHexKey() else null
                val imageKey = if (reader.hasRemaining) reader.readOpaqueVarInt().takeIf { it.isNotEmpty() } else null
                val imageNonce = if (reader.hasRemaining) reader.readOpaqueVarInt().takeIf { it.isNotEmpty() } else null
                val imageUploadKey = if (reader.hasRemaining) reader.readOpaqueVarInt().takeIf { it.isNotEmpty() } else null

                // v3+: disappearing_message_secs
                val disappearingBytes = if (reader.hasRemaining) reader.readOpaqueVarInt() else ByteArray(0)
                val disappearingMessageSecs: ULong? =
                    when (disappearingBytes.size) {
                        0 -> {
                            null
                        }

                        8 -> {
                            var v = 0UL
                            for (b in disappearingBytes) {
                                v = (v shl 8) or (b.toInt() and 0xFF).toULong()
                            }
                            require(v > 0UL) { "disappearing_message_secs value 0 is rejected (MIP-01)" }
                            v
                        }

                        else -> {
                            throw IllegalArgumentException(
                                "disappearing_message_secs must be 0 or 8 bytes, got ${disappearingBytes.size}",
                            )
                        }
                    }

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
                    disappearingMessageSecs = disappearingMessageSecs,
                )
            } catch (_: Exception) {
                null
            }
    }
}
