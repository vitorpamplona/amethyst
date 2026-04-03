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
import com.vitorpamplona.quartz.nip01Core.core.HexKey

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

    companion object {
        const val CURRENT_VERSION = 2

        /** MLS extension type identifier for marmot_group_data */
        const val EXTENSION_ID: UShort = 0xF2EEu
    }
}
