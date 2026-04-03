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
package com.vitorpamplona.quartz.marmot.mls.crypto

import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.codec.TlsSerializable
import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter
import com.vitorpamplona.quartz.nip44Encryption.crypto.Hkdf
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.ciphers.AESGCM
import com.vitorpamplona.quartz.utils.sha256.sha256

/**
 * Cryptographic provider for MLS ciphersuite 0x0001:
 * MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519.
 *
 * Wraps existing Quartz primitives (HKDF, AES-GCM, SHA-256) and adds
 * MLS-specific derivation functions (ExpandWithLabel, DeriveSecret, RefHash).
 *
 * Ed25519 and X25519 operations are delegated to expect/actual implementations.
 */
object MlsCryptoProvider {
    // Ciphersuite 0x0001 constants
    const val HASH_OUTPUT_LENGTH = 32 // SHA-256
    const val KEM_SECRET_LENGTH = 32 // X25519
    const val AEAD_KEY_LENGTH = 16 // AES-128-GCM
    const val AEAD_NONCE_LENGTH = 12
    const val SIGNATURE_PUBLIC_KEY_LENGTH = 32 // Ed25519
    const val SIGNATURE_SECRET_KEY_LENGTH = 64 // Ed25519 (seed + public)
    const val KEM_PUBLIC_KEY_LENGTH = 32 // X25519
    const val KEM_SECRET_KEY_LENGTH = 32 // X25519

    private val hkdf = Hkdf("HmacSHA256", HASH_OUTPUT_LENGTH)

    // --- Hash ---

    fun hash(data: ByteArray): ByteArray = sha256(data)

    // --- HKDF ---

    fun hkdfExtract(
        salt: ByteArray,
        ikm: ByteArray,
    ): ByteArray = hkdf.extract(ikm, salt)

    /**
     * MLS ExpandWithLabel (RFC 9420 Section 8):
     * ```
     * HKDF-Expand-Label(Secret, Label, Context, Length) =
     *     HKDF-Expand(Secret, HkdfLabel, Length)
     *
     * struct {
     *     uint16 length;
     *     opaque label<V> = "MLS 1.0 " + Label;
     *     opaque context<V> = Context;
     * } KDFLabel;
     * ```
     * Label and context lengths use QUIC-style variable-length integer encoding.
     */
    fun expandWithLabel(
        secret: ByteArray,
        label: String,
        context: ByteArray,
        length: Int,
    ): ByteArray {
        val fullLabel = "MLS 1.0 $label".encodeToByteArray()
        val hkdfLabel = TlsWriter(4 + fullLabel.size + context.size)
        hkdfLabel.putUint16(length)
        hkdfLabel.putOpaqueVarInt(fullLabel)
        hkdfLabel.putOpaqueVarInt(context)
        return hkdfExpand(secret, hkdfLabel.toByteArray(), length)
    }

    /**
     * ExpandWithLabel variant that accepts a raw byte array label.
     * The "MLS 1.0 " prefix is prepended to the raw label bytes.
     */
    fun expandWithLabelRaw(
        secret: ByteArray,
        label: ByteArray,
        context: ByteArray,
        length: Int,
    ): ByteArray {
        val prefix = "MLS 1.0 ".encodeToByteArray()
        val fullLabel = prefix + label
        val hkdfLabel = TlsWriter(4 + fullLabel.size + context.size)
        hkdfLabel.putUint16(length)
        hkdfLabel.putOpaqueVarInt(fullLabel)
        hkdfLabel.putOpaqueVarInt(context)
        return hkdfExpand(secret, hkdfLabel.toByteArray(), length)
    }

    /**
     * MLS DeriveSecret (RFC 9420 Section 8):
     * DeriveSecret(Secret, Label) = ExpandWithLabel(Secret, Label, "", Nh)
     */
    fun deriveSecret(
        secret: ByteArray,
        label: String,
    ): ByteArray = expandWithLabel(secret, label, ByteArray(0), HASH_OUTPUT_LENGTH)

    /**
     * RefHash (RFC 9420 Section 5.2): Hash of a labeled TLS-serialized value.
     * RefHash(label, value) = Hash(label_len || label || value_len || value)
     */
    fun refHash(
        label: String,
        value: ByteArray,
    ): ByteArray {
        val labelBytes = label.encodeToByteArray()
        val writer = TlsWriter(2 + labelBytes.size + value.size)
        writer.putOpaqueVarInt(labelBytes)
        writer.putOpaqueVarInt(value)
        return hash(writer.toByteArray())
    }

    // --- AEAD (AES-128-GCM) ---

    fun aeadEncrypt(
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
    ): ByteArray = AESGCM(key, nonce).encrypt(plaintext, aad)

    fun aeadDecrypt(
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray = AESGCM(key, nonce).decrypt(ciphertext, aad)

    // --- Random ---

    fun randomBytes(length: Int): ByteArray = RandomInstance.bytes(length)

    // --- HKDF-Expand (general) ---

    /**
     * HKDF-Expand using HMAC-SHA256. Supports arbitrary output length.
     * Implementation per RFC 5869 Section 2.3.
     */
    fun hkdfExpand(
        prk: ByteArray,
        info: ByteArray,
        length: Int,
    ): ByteArray {
        require(length <= 255 * HASH_OUTPUT_LENGTH) { "HKDF-Expand output too long" }

        val n = (length + HASH_OUTPUT_LENGTH - 1) / HASH_OUTPUT_LENGTH
        val output = ByteArray(n * HASH_OUTPUT_LENGTH)
        var prev = ByteArray(0)

        for (i in 1..n) {
            val mac =
                com.vitorpamplona.quartz.utils.mac
                    .MacInstance("HmacSHA256", prk)
            mac.update(prev)
            mac.update(info)
            mac.update(i.toByte())
            prev = mac.doFinal()
            prev.copyInto(output, (i - 1) * HASH_OUTPUT_LENGTH)
        }

        return output.copyOfRange(0, length)
    }

    // --- Signature Helpers (RFC 9420 Section 5.1.2) ---

    /**
     * SignWithLabel(SignatureKey, Label, Content):
     * Signs the TLS-serialized SignContent structure.
     */
    fun signWithLabel(
        signatureKey: ByteArray,
        label: String,
        content: ByteArray,
    ): ByteArray {
        val signContent = makeSignContent(label, content)
        return Ed25519.sign(signContent, signatureKey)
    }

    /**
     * VerifyWithLabel(VerificationKey, Label, Content, Signature):
     * Verifies signature over TLS-serialized SignContent structure.
     */
    fun verifyWithLabel(
        verificationKey: ByteArray,
        label: String,
        content: ByteArray,
        signature: ByteArray,
    ): Boolean {
        val signContent = makeSignContent(label, content)
        return Ed25519.verify(signContent, signature, verificationKey)
    }

    /**
     * MLS SignContent structure:
     * ```
     * struct {
     *     opaque label<V> = "MLS 1.0 " + Label;
     *     opaque content<V> = Content;
     * } SignContent;
     * ```
     */
    private fun makeSignContent(
        label: String,
        content: ByteArray,
    ): ByteArray {
        val fullLabel = "MLS 1.0 $label".encodeToByteArray()
        val writer = TlsWriter(2 + fullLabel.size + 4 + content.size)
        writer.putOpaqueVarInt(fullLabel)
        writer.putOpaqueVarInt(content)
        return writer.toByteArray()
    }

    // --- EncryptWithLabel / DecryptWithLabel for HPKE ---

    /**
     * EncryptWithLabel(PublicKey, Label, Context, Plaintext):
     * HPKE single-shot encryption used in TreeKEM UpdatePath.
     */
    fun encryptWithLabel(
        publicKey: ByteArray,
        label: String,
        context: ByteArray,
        plaintext: ByteArray,
    ): HpkeCiphertext {
        val fullLabel = "MLS 1.0 $label".encodeToByteArray()
        val info = TlsWriter()
        info.putOpaqueVarInt(fullLabel)
        info.putOpaqueVarInt(context)
        return Hpke.seal(publicKey, info.toByteArray(), ByteArray(0), plaintext)
    }

    /**
     * DecryptWithLabel(PrivateKey, Label, Context, KEMOutput, Ciphertext):
     * HPKE single-shot decryption used in TreeKEM UpdatePath processing.
     */
    fun decryptWithLabel(
        privateKey: ByteArray,
        label: String,
        context: ByteArray,
        kemOutput: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray {
        val fullLabel = "MLS 1.0 $label".encodeToByteArray()
        val info = TlsWriter()
        info.putOpaqueVarInt(fullLabel)
        info.putOpaqueVarInt(context)
        return Hpke.open(privateKey, kemOutput, info.toByteArray(), ByteArray(0), ciphertext)
    }
}

/**
 * HPKE ciphertext container (RFC 9180).
 * Contains both the KEM output (ephemeral public key) and the encrypted payload.
 */
data class HpkeCiphertext(
    val kemOutput: ByteArray,
    val ciphertext: ByteArray,
) : TlsSerializable {
    override fun encodeTls(writer: TlsWriter) {
        writer.putOpaqueVarInt(kemOutput)
        writer.putOpaqueVarInt(ciphertext)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HpkeCiphertext) return false
        return kemOutput.contentEquals(other.kemOutput) && ciphertext.contentEquals(other.ciphertext)
    }

    override fun hashCode(): Int {
        var result = kemOutput.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        return result
    }

    companion object {
        fun decodeTls(reader: TlsReader): HpkeCiphertext =
            HpkeCiphertext(
                kemOutput = reader.readOpaqueVarInt(),
                ciphertext = reader.readOpaqueVarInt(),
            )
    }
}
