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
package com.vitorpamplona.quartz.marmot.appComponents

import com.vitorpamplona.quartz.marmot.mls.crypto.MlsCryptoProvider
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip44Encryption.crypto.ChaCha20Poly1305
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.sha256.sha256

/** One `locator <kind> <value>` field of an `encrypted-media-v2` reference. */
data class MediaLocatorV2(
    val kind: String,
    val value: String,
)

/**
 * One `encrypted-media-v2` attachment reference — the authenticated fields of
 * an `imeta` tag (`features/encrypted-media.md`).
 *
 * The source epoch is deliberately NOT a field: it is the MLS epoch of the
 * application message that carried the tag. Putting it in the tag would let a
 * sender name which epoch's media secret a receiver should use, and the
 * receiver already knows the real one.
 */
class EncryptedMediaReferenceV2(
    val locators: List<MediaLocatorV2>,
    val ciphertextSha256: ByteArray,
    val plaintextSha256: ByteArray,
    val nonce: ByteArray,
    /** Canonical media type, byte-for-byte as it must appear in `m`. */
    val mediaType: String,
    val filename: String,
    val dim: String? = null,
    val thumbhash: String? = null,
) {
    init {
        require(locators.isNotEmpty()) { "an encrypted-media reference needs at least one locator" }
        locators.forEach { locator ->
            require(locator.kind.isNotEmpty()) { "locator kind must not be empty" }
            require(locator.value.isNotEmpty()) { "locator value must not be empty" }
            if (locator.kind == EncryptedMediaPolicyV2.INITIAL_LOCATOR_KIND) {
                require(locator.value.startsWith("http://") || locator.value.startsWith("https://")) {
                    "a blossom-v1 locator must be an http or https URL"
                }
            }
        }
        require(ciphertextSha256.size == 32) { "ciphertext_sha256 must be 32 bytes" }
        require(plaintextSha256.size == 32) { "plaintext_sha256 must be 32 bytes" }
        require(nonce.size == EncryptedMediaV2.NONCE_LENGTH) {
            "nonce must be ${EncryptedMediaV2.NONCE_LENGTH} bytes"
        }
        require(MarmotMediaType.canonicalize(mediaType) == mediaType) {
            "m must already be byte-for-byte canonical, was '$mediaType'"
        }
        EncryptedMediaV2.requireValidFilename(filename)
    }

    /** The `imeta` tag, with `v` first and the locators in the producer's order. */
    fun toImetaTag(): Array<String> {
        val fields = mutableListOf("imeta", "v ${EncryptedMediaPolicyV2.MEDIA_FORMAT}")
        locators.forEach { fields.add("locator ${it.kind} ${it.value}") }
        fields.add("ciphertext_sha256 ${ciphertextSha256.toHexKey()}")
        fields.add("plaintext_sha256 ${plaintextSha256.toHexKey()}")
        fields.add("nonce ${nonce.toHexKey()}")
        fields.add("m $mediaType")
        fields.add("filename $filename")
        dim?.let { fields.add("dim $it") }
        thumbhash?.let { fields.add("thumbhash $it") }
        return fields.toTypedArray()
    }
}

/**
 * `encrypted-media-v2` key derivation and content encryption
 * (`features/encrypted-media.md`).
 *
 * ## Why the field checks are so unforgiving
 *
 * `plaintext_sha256`, `m` and `filename` all feed both the key derivation and
 * the AEAD associated data, joined by single `0x00` bytes with no length
 * prefixes. That is only unambiguous because each field is constrained to
 * exclude `0x00`: the hash is fixed-width, the media-type profile allows only
 * ASCII token bytes, and the filename profile forbids `U+0000` outright. Relax
 * any of those and two different field triples serialize to the same info
 * bytes.
 *
 * It is also why a duplicate single-occurrence field is REJECTED rather than
 * resolved. A first-wins decoder and a last-wins decoder would derive different
 * keys from the same authenticated tag, so one sender could hand two conformant
 * clients tags that decrypt to different content.
 */
object EncryptedMediaV2 {
    const val VERSION = "encrypted-media-v2"
    const val NONCE_LENGTH = 12
    const val KEY_LENGTH = 32
    const val EXPORTER_KEY_LENGTH = 32
    const val MAX_FILENAME_BYTES = 255

    private val VERSION_BYTES = VERSION.encodeToByteArray()
    private val KEY_SUFFIX = "key".encodeToByteArray()
    private val NUL = byteArrayOf(0x00)

    /** `filename` is display metadata: valid UTF-8, 1..255 bytes, no `U+0000`. */
    fun requireValidFilename(filename: String) {
        val bytes = filename.encodeToByteArray()
        require(bytes.isNotEmpty() && bytes.size <= MAX_FILENAME_BYTES) {
            "filename must be 1..$MAX_FILENAME_BYTES bytes, was ${bytes.size}"
        }
        require(!filename.contains('\u0000')) { "filename must not contain U+0000" }
    }

    /**
     * `file_key = HKDF-Expand(media_secret, info, 32)`.
     *
     * `media_secret` is used directly as the HKDF PRK — Expand only, no
     * Extract. That is fixed regardless of the group's MLS ciphersuite; only
     * the exporter itself is computed with the ciphersuite's own hash.
     */
    fun deriveFileKey(
        mediaSecret: ByteArray,
        plaintextSha256: ByteArray,
        mediaType: String,
        filename: String,
    ): ByteArray {
        require(mediaSecret.size == EXPORTER_KEY_LENGTH) {
            "media secret must be $EXPORTER_KEY_LENGTH bytes"
        }
        return MlsCryptoProvider.hkdfExpand(
            mediaSecret,
            buildInfo(plaintextSha256, mediaType, filename, KEY_SUFFIX),
            KEY_LENGTH,
        )
    }

    class EncryptionResult(
        val ciphertext: ByteArray,
        val nonce: ByteArray,
        val plaintextSha256: ByteArray,
        val ciphertextSha256: ByteArray,
    )

    /**
     * Encrypt an attachment.
     *
     * A fresh random nonce every time, including on a resend: the key is
     * deterministic in (plaintext hash, media type, filename, epoch), so
     * re-sending the same file in the same epoch reuses the key, and reusing a
     * nonce with it breaks ChaCha20-Poly1305 outright.
     */
    fun encrypt(
        plaintext: ByteArray,
        mediaSecret: ByteArray,
        mediaType: String,
        filename: String,
    ): EncryptionResult {
        require(MarmotMediaType.canonicalize(mediaType) == mediaType) {
            "media type must already be canonical, was '$mediaType'"
        }
        requireValidFilename(filename)

        val plaintextSha256 = sha256(plaintext)
        val fileKey = deriveFileKey(mediaSecret, plaintextSha256, mediaType, filename)
        val nonce = RandomInstance.bytes(NONCE_LENGTH)
        val aad = buildAad(plaintextSha256, mediaType, filename)
        val ciphertext = ChaCha20Poly1305.encrypt(plaintext, aad, nonce, fileKey)
        return EncryptionResult(
            ciphertext = ciphertext,
            nonce = nonce,
            plaintextSha256 = plaintextSha256,
            ciphertextSha256 = sha256(ciphertext),
        )
    }

    /**
     * Decrypt an attachment and verify it is the file the reference names.
     *
     * The plaintext-hash check is not redundant with the AEAD tag. The tag
     * proves the ciphertext was produced under this key and AAD; the hash check
     * proves the AAD described THIS file rather than another one the same
     * sender could also authenticate.
     */
    fun decrypt(
        ciphertext: ByteArray,
        mediaSecret: ByteArray,
        nonce: ByteArray,
        plaintextSha256: ByteArray,
        mediaType: String,
        filename: String,
    ): ByteArray {
        require(nonce.size == NONCE_LENGTH) { "nonce must be $NONCE_LENGTH bytes" }
        require(plaintextSha256.size == 32) { "plaintext_sha256 must be 32 bytes" }
        require(MarmotMediaType.canonicalize(mediaType) == mediaType) {
            "media type must already be canonical, was '$mediaType'"
        }
        requireValidFilename(filename)

        val fileKey = deriveFileKey(mediaSecret, plaintextSha256, mediaType, filename)
        val aad = buildAad(plaintextSha256, mediaType, filename)
        val plaintext = ChaCha20Poly1305.decrypt(ciphertext, aad, nonce, fileKey)
        check(sha256(plaintext).contentEquals(plaintextSha256)) {
            "decrypted attachment does not hash to plaintext_sha256"
        }
        return plaintext
    }

    /**
     * Parse an `imeta` tag, or throw naming the reason.
     *
     * @throws IllegalArgumentException for every rejection the spec lists.
     */
    fun parseImetaTag(tag: Array<String>): EncryptedMediaReferenceV2 {
        require(tag.isNotEmpty() && tag[0] == "imeta") { "not an imeta tag" }

        val locators = mutableListOf<MediaLocatorV2>()
        val single = mutableMapOf<String, String>()
        for (i in 1 until tag.size) {
            val field = tag[i]
            val name = field.substringBefore(' ')
            val rest = field.substringAfter(' ', "")
            if (name == "locator") {
                val kind = rest.substringBefore(' ')
                val value = rest.substringAfter(' ', "")
                locators.add(MediaLocatorV2(kind, value))
            } else {
                // Exactly `locator` repeats; everything else occurs at most
                // once, and a duplicate is refused rather than resolved.
                require(single.put(name, rest) == null) {
                    "imeta field '$name' appears more than once"
                }
            }
        }

        require(!single.containsKey("blurhash")) { "blurhash is invalid in $VERSION" }
        require(single["v"] == VERSION) { "imeta version is not $VERSION" }

        val storedMediaType = requireNotNull(single["m"]) { "imeta is missing m" }
        require(MarmotMediaType.canonicalize(storedMediaType) == storedMediaType) {
            "imeta m is not byte-for-byte canonical: '$storedMediaType'"
        }

        return EncryptedMediaReferenceV2(
            locators = locators,
            ciphertextSha256 = requireHash(single["ciphertext_sha256"], "ciphertext_sha256"),
            plaintextSha256 = requireHash(single["plaintext_sha256"], "plaintext_sha256"),
            nonce = requireNonce(single["nonce"]),
            mediaType = storedMediaType,
            filename = requireNotNull(single["filename"]) { "imeta is missing filename" },
            dim = single["dim"],
            thumbhash = single["thumbhash"],
        )
    }

    /**
     * [parseImetaTag], returning null instead of throwing.
     *
     * Rejection is attachment-local: the caller drops this attachment and keeps
     * the caption and every other valid attachment on the same message.
     */
    fun parseImetaTagOrNull(tag: Array<String>): EncryptedMediaReferenceV2? =
        try {
            parseImetaTag(tag)
        } catch (_: Exception) {
            null
        }

    private fun requireHash(
        value: String?,
        field: String,
    ): ByteArray {
        val hex = requireNotNull(value) { "imeta is missing $field" }
        require(hex.length == 64 && hex.all { it in '0'..'9' || it in 'a'..'f' }) {
            "$field must be 64 lowercase hex characters"
        }
        return hexToBytes(hex)
    }

    private fun requireNonce(value: String?): ByteArray {
        val hex = requireNotNull(value) { "imeta is missing nonce" }
        require(hex.length == NONCE_LENGTH * 2 && hex.all { it in '0'..'9' || it in 'a'..'f' }) {
            "nonce must be ${NONCE_LENGTH * 2} lowercase hex characters"
        }
        return hexToBytes(hex)
    }

    private fun hexToBytes(hex: String) =
        ByteArray(hex.length / 2) { i ->
            ((hexDigit(hex[i * 2]) shl 4) or hexDigit(hex[i * 2 + 1])).toByte()
        }

    private fun hexDigit(c: Char) = if (c in '0'..'9') c - '0' else c - 'a' + 10

    private fun buildInfo(
        plaintextSha256: ByteArray,
        mediaType: String,
        filename: String,
        suffix: ByteArray,
    ) = buildAad(plaintextSha256, mediaType, filename) + NUL + suffix

    private fun buildAad(
        plaintextSha256: ByteArray,
        mediaType: String,
        filename: String,
    ) = VERSION_BYTES + NUL + plaintextSha256 + NUL + mediaType.encodeToByteArray() +
        NUL + filename.encodeToByteArray()
}
