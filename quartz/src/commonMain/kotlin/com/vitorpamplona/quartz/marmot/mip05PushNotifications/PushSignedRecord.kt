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
package com.vitorpamplona.quartz.marmot.mip05PushNotifications

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.sha256.sha256

/**
 * The canonical `SignedRecord` bytes of a push token record or removal
 * (`features/push-notifications.md`, "Canonical record bytes").
 *
 * ```text
 * SignedRecord = domain_tag
 *             || group_id_len[2]            big-endian u16
 *             || group_id[group_id_len]
 *             || member_id[32]
 *             || leaf_index[4]              big-endian u32
 *             || platform_byte[1]
 *             || server_pubkey[32]
 *             || token_fingerprint[12]
 *             || owner_ts[8]                big-endian u64, milliseconds
 *             || relay_hint_len[2]          big-endian u16, 0 when absent or for a removal
 *             || relay_hint[relay_hint_len]
 *             || encrypted_token[1084]      removals omit this field
 * ```
 *
 * ## Why this is hand-rolled rather than a TLS vector or a QUIC varint
 *
 * The spec says so, in as many words: "Signers and verifiers MUST NOT
 * substitute QUIC varints, TLS vectors, or a serialization-library default."
 * The rest of Marmot's binary profile uses QUIC varints, so the temptation to
 * reach for [TlsWriter] here is real and wrong — a varint `group_id_len` would
 * be one byte where this is two, every subsequent field would shift, and the
 * digest would differ from every other implementation's while still looking
 * perfectly well-formed locally.
 *
 * ## What it is for
 *
 * ONLY the digest. `SHA-256(SignedRecord)` is the deterministic tie-breaker in
 * the `(owner_ts, digest)` ordering primitive; it is **not** the signature
 * preimage in a current group, where [PushOwnerProof]'s unpublished kind `451`
 * event is. It IS the preimage for the raw legacy proof form, which we verify
 * in legacy groups and never produce.
 */
object PushSignedRecord {
    /** `sha256:` plus 24 hex characters — the first 12 bytes of the token hash. */
    const val FINGERPRINT_PREFIX = "sha256:"
    const val FINGERPRINT_BYTES = 12
    const val FINGERPRINT_HEX_LENGTH = FINGERPRINT_BYTES * 2

    /** `EncryptedToken` is a fixed 1084 bytes; the record embeds it verbatim. */
    const val ENCRYPTED_TOKEN_BYTES = 1084

    /**
     * The fingerprint that names a token without revealing it:
     * `sha256:` + the first 24 hex characters of `SHA-256(platform_byte || device_token)`.
     */
    fun fingerprintOf(
        platform: PushPlatform,
        deviceToken: ByteArray,
    ): String {
        val preimage = ByteArray(1 + deviceToken.size)
        preimage[0] = platform.byte
        deviceToken.copyInto(preimage, 1)
        return FINGERPRINT_PREFIX + sha256(preimage).copyOfRange(0, FINGERPRINT_BYTES).toHexKey()
    }

    /** The 12 raw bytes a `sha256:`-prefixed fingerprint encodes, or null when malformed. */
    fun fingerprintBytes(fingerprint: String): ByteArray? {
        if (!fingerprint.startsWith(FINGERPRINT_PREFIX)) return null
        val hex = fingerprint.substring(FINGERPRINT_PREFIX.length)
        if (hex.length != FINGERPRINT_HEX_LENGTH) return null
        if (!hex.all { it in '0'..'9' || it in 'a'..'f' }) return null
        return hex.hexToByteArray()
    }

    /**
     * A relay hint as it is signed over: trimmed, and absent when the result is
     * empty. Both halves matter — a signer that kept the untrimmed string and a
     * verifier that trimmed it would compute different digests from identical
     * JSON, and the record would simply never verify anywhere.
     */
    fun normalizeRelayHint(relayHint: String?): String = relayHint?.trim().orEmpty()

    /**
     * Build the canonical bytes.
     *
     * @param encryptedToken the 1084-byte token for a record entry, or null for a removal.
     */
    fun encode(
        record: PushRecordKind,
        groupIdHex: HexKey,
        memberIdHex: HexKey,
        leafIndex: Int,
        platform: PushPlatform,
        serverPubKeyHex: HexKey,
        tokenFingerprint: String,
        ownerTsMillis: Long,
        relayHint: String = "",
        encryptedToken: ByteArray? = null,
    ): ByteArray {
        val domain = record.domainTag.encodeToByteArray()
        val groupId = groupIdHex.hexToByteArray()
        val memberId = memberIdHex.hexToByteArray()
        val serverPubKey = serverPubKeyHex.hexToByteArray()
        val fingerprint = requireNotNull(fingerprintBytes(tokenFingerprint)) { "malformed token fingerprint" }

        require(memberId.size == 32) { "member id must be 32 bytes" }
        require(serverPubKey.size == 32) { "server pubkey must be 32 bytes" }
        require(groupId.size <= 0xFFFF) { "group id too long" }

        // A removal signs a zero-length hint and omits the token entirely, so
        // the two record shapes can never collide on the same bytes.
        val hintBytes =
            if (record == PushRecordKind.REMOVAL) {
                ByteArray(0)
            } else {
                normalizeRelayHint(relayHint).encodeToByteArray()
            }
        require(hintBytes.size <= 0xFFFF) { "relay hint too long" }

        val token =
            if (record == PushRecordKind.REMOVAL) {
                null
            } else {
                requireNotNull(encryptedToken) { "a token record must carry its encrypted token" }
                    .also { require(it.size == ENCRYPTED_TOKEN_BYTES) { "EncryptedToken must be $ENCRYPTED_TOKEN_BYTES bytes" } }
            }

        val size =
            domain.size + 2 + groupId.size + 32 + 4 + 1 + 32 + FINGERPRINT_BYTES + 8 + 2 +
                hintBytes.size + (token?.size ?: 0)
        val out = ByteArray(size)
        var at = 0

        fun put(bytes: ByteArray) {
            bytes.copyInto(out, at)
            at += bytes.size
        }

        fun putU16(value: Int) {
            out[at++] = (value ushr 8 and 0xFF).toByte()
            out[at++] = (value and 0xFF).toByte()
        }

        put(domain)
        putU16(groupId.size)
        put(groupId)
        put(memberId)
        // leaf_index is u32 big-endian; an Int is the same 4 bytes for every
        // index MLS can actually reach.
        out[at++] = (leafIndex ushr 24 and 0xFF).toByte()
        out[at++] = (leafIndex ushr 16 and 0xFF).toByte()
        out[at++] = (leafIndex ushr 8 and 0xFF).toByte()
        out[at++] = (leafIndex and 0xFF).toByte()
        out[at++] = platform.byte
        put(serverPubKey)
        put(fingerprint)
        for (shift in 56 downTo 0 step 8) {
            out[at++] = (ownerTsMillis ushr shift and 0xFF).toByte()
        }
        putU16(hintBytes.size)
        put(hintBytes)
        token?.let { put(it) }

        return out
    }

    /** `SHA-256(SignedRecord)` — the ordering tie-breaker, as lowercase hex. */
    fun digestHex(signedRecord: ByteArray): HexKey = sha256(signedRecord).toHexKey()
}
