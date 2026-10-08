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
package com.vitorpamplona.quartz.experimental.zapstore.identityProof

import com.vitorpamplona.quartz.mls.crypto.Ed25519
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.utils.EventFactory
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.sha256.sha256
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class IdentityProofVerificationTest {
    /**
     * A live proof (relay nos.lol, fetched 2026-10-08): Stormberry AS's npub vouching for the
     * certificate that signs its MetadataScrubber APKs. Its `d` matches the `apk_certificate_hash`
     * of the developer's NIP-82 assets for `no.stormberry.metadatascrubber` 0.0.1 to 1.0.1.
     */
    private val stormberryProof =
        """{"id":"c0a5e30b347ba9f14a8882885d616857d103b1f4de015dddb993c84a65dae5ba","pubkey":"108aef782a95a65a2df059578e67a71366a65a697e163711b54574e8da0e6e30","created_at":1791029405,"kind":30509,"tags":[["d","30e916e7d44860a2c00c839ad2a6b5806e15d14d59b2811c962866959f3e3a98"],["signature","E3m20Wl10UEQr2ooc4MugY1B/rAFheq42rKDUd9cP6WV19Kr3xxArORxk9v+B2SsVcnZJ6avQoR+OP8Y6qp2F44QMoQNgy9RiopAxoDIsVudyEeQz2mHc16vKJ2S+JCTm68MbDZo/hzy31qGnwg0lmL2G1Xqu6jWlSLyKRTzjx22HnyfIoP4bpyclDlugFTWNXoHVaxP8HIJt0Yhn0qeZLdnPzUk9aNdjvOsL/SqRBwinWGX5OH0q2sJUUliOVkHY2xXG+HcC8aUsnKVROopwmtyAa59gUXb0xqSq8tBVqrmeXJIalsKcCsLNbYRojrOsXZxAlkIDueppWzMQ2+dL20v14GyUtFTC/tWc4SgvX3ntuYRFmynZ30LiBn5Dcm07IUYzlH/eoxg0tlC6ZG6uH0uB2ljMecCdqBD2PYbSpBIm2LUtkYKMILTcPwDZvqzp2YgPBuBt9ZmwmSSYle5dLrvtWdrofZZl0Wk4jBP+gkfASuwUDKcuc7ZTBMSVdFKEu3LwHcsINEUDsLGqz2vwxNTOv9vSwBot/yq1CrDxHemrQWK+TnfXjk0fLbwjKqYWBzKJNlh7uMeOZlz9BQDoSPmza20FLKmLnQ1f1mm17DZ5VSquqqTpJYLGxtp//CLl2O4KyzXP3H5uzYvDckK1LnthWiEml/wOz5Ctp7I/Pc="],["expiry","1822565405"]],"content":"","sig":"60e74fb0211b068ecab226bc291f8061f792b1710388f6531e02e810cc40df6f7ad7321a6837b3b912d4e9932b4acae8fc7ef20ddaddc2e25237bdcfe4c3177e"}"""

    /**
     * The RSA-4096 certificate that signs MetadataScrubber v1.0.1, as published at
     * `github.com/StormberryAS/MetadataScrubber/releases/download/android-v1.0.1/MetadataScrubber-v1.0.1.apk`
     * (APK SHA-256 edc50761…0f57, the `x` of the developer's 3063 asset). Extracted with
     * `apksigner verify --print-certs-pem` (v2 + v3 signer, `CN=Stormberry AS`), converted to DER
     * with `openssl x509 -outform DER`; its SHA-256 is the proof's `d`. A public certificate.
     */
    private val stormberryCertificate: ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/zapstore/stormberry-metadatascrubber-signing-cert.der")) {
            "missing zapstore certificate resource"
        }.use { it.readBytes() }

    private val createdAt = 1791029405L
    private val expiry = 1822565405L
    private val developer = "108aef782a95a65a2df059578e67a71366a65a697e163711b54574e8da0e6e30"
    private val stranger = "2".repeat(64)

    private fun proof(
        tags: TagArray,
        createdAt: Long = this.createdAt,
        pubKey: String = developer,
    ): IdentityProofEvent = assertIs<IdentityProofEvent>(EventFactory.create("0".repeat(64), pubKey, createdAt, IdentityProofEvent.KIND, tags, "", ""))

    private fun live() = assertIs<IdentityProofEvent>(Event.fromJson(stormberryProof))

    private fun TagArray.replacing(
        name: String,
        vararg value: String,
    ): TagArray = map { if (it[0] == name) arrayOf(name, *value) else it }.toTypedArray()

    @Test
    fun liveRsa4096ProofVerifies() {
        assertEquals(Hex.encode(sha256(stormberryCertificate)), live().certificateHash())
        assertEquals(512, live().signatureBytes()?.size)
        assertEquals(IdentityProofVerification.VALID, live().verify(stormberryCertificate, now = createdAt + 1))
        assertEquals(IdentityProofVerification.VALID, live().verify(stormberryCertificate, now = expiry))
    }

    @Test
    fun liveProofTamperedSignatureFails() {
        val signature = live().signatureBytes()!!
        signature[100] = (signature[100].toInt() xor 1).toByte()
        val tampered = proof(live().tags.replacing("signature", Base64.encode(signature)))
        assertEquals(IdentityProofVerification.BAD_SIGNATURE, tampered.verify(stormberryCertificate, now = createdAt))

        // A truncated signature is a bad signature, not a crash.
        val truncated = proof(live().tags.replacing("signature", Base64.encode(signature.copyOf(256))))
        assertEquals(IdentityProofVerification.BAD_SIGNATURE, truncated.verify(stormberryCertificate, now = createdAt))
    }

    @Test
    fun liveSignatureDoesNotVouchForAnotherNpubOrTime() {
        assertEquals(IdentityProofVerification.BAD_SIGNATURE, proof(live().tags, pubKey = stranger).verify(stormberryCertificate, now = createdAt))
        assertEquals(IdentityProofVerification.BAD_SIGNATURE, proof(live().tags, createdAt = createdAt + 1).verify(stormberryCertificate, now = createdAt))
        val laterExpiry = proof(live().tags.replacing("expiry", (expiry + 1).toString()))
        assertEquals(IdentityProofVerification.BAD_SIGNATURE, laterExpiry.verify(stormberryCertificate, now = createdAt))
    }

    @Test
    fun anotherCertificateIsAMismatch() {
        val other = selfSignedCertificate(rsaKeyPair())
        assertEquals(IdentityProofVerification.CERT_MISMATCH, live().verify(other, now = createdAt))
        val flipped = stormberryCertificate.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertEquals(IdentityProofVerification.CERT_MISMATCH, live().verify(flipped, now = createdAt))
    }

    @Test
    fun expiredAndRevoked() {
        assertEquals(IdentityProofVerification.EXPIRED, live().verify(stormberryCertificate, now = expiry + 1))
        val revoked = proof(live().tags + arrayOf(arrayOf("revoked", "key rotated")))
        assertEquals(IdentityProofVerification.REVOKED, revoked.verify(stormberryCertificate, now = createdAt))
        // Revocation wins over expiry: both are reported in check order.
        assertEquals(IdentityProofVerification.REVOKED, revoked.verify(stormberryCertificate, now = expiry + 1))
    }

    @Test
    fun malformedProofsAndCertificates() {
        val noSignature = proof(live().tags.filter { it[0] != "signature" }.toTypedArray())
        assertEquals(IdentityProofVerification.MALFORMED, noSignature.verify(stormberryCertificate, now = createdAt))
        val notBase64 = proof(live().tags.replacing("signature", "%%% not base64 %%%"))
        assertEquals(IdentityProofVerification.MALFORMED, notBase64.verify(stormberryCertificate, now = createdAt))
        val noExpiry = proof(live().tags.filter { it[0] != "expiry" }.toTypedArray())
        assertEquals(IdentityProofVerification.MALFORMED, noExpiry.verify(stormberryCertificate, now = createdAt))

        val garbage = "not a certificate".encodeToByteArray()
        val forGarbage = proof(live().tags.replacing("d", Hex.encode(sha256(garbage))))
        assertEquals(IdentityProofVerification.BAD_CERTIFICATE, forGarbage.verify(garbage, now = createdAt))
    }

    @Test
    fun rsaPkcs1AndPss() {
        val keys = rsaKeyPair()
        assertEquals(IdentityProofVerification.VALID, signedProof(keys, "SHA256withRSA").second)
        assertEquals(IdentityProofVerification.VALID, signedProof(keys, "RSASSA-PSS", PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1)).second)
        // Pinned to SHA-256 and to the 32-byte PSS salt Go verifies with.
        assertEquals(IdentityProofVerification.BAD_SIGNATURE, signedProof(keys, "SHA512withRSA").second)
        assertEquals(IdentityProofVerification.BAD_SIGNATURE, signedProof(keys, "RSASSA-PSS", PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 20, 1)).second)
    }

    @Test
    fun ecdsaP256() {
        val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val (proof, result) = signedProof(keys, "SHA256withECDSA")
        assertEquals(IdentityProofVerification.VALID, result)
        val certificate = selfSignedCertificate(keys)
        assertEquals(IdentityProofVerification.BAD_SIGNATURE, proof(proof.tags, pubKey = stranger).verify(certificate, now = createdAt))
        assertEquals(IdentityProofVerification.BAD_SIGNATURE, signedProof(keys, "SHA384withECDSA").second)
    }

    @Test
    fun ed25519UsesQuartzVerifier() {
        val keys = Ed25519.keyPairFromSeed(ByteArray(32) { it.toByte() })
        val spki = ED25519_SPKI_PREFIX + keys.publicKey
        val certificate = certificate(spki)
        val signature = Ed25519.sign(IdentityProofEvent.signedMessage(createdAt, expiry, developer).encodeToByteArray(), keys.privateKey)
        val proof = proofFor(certificate, signature)
        assertEquals(IdentityProofVerification.VALID, proof.verify(certificate, now = createdAt))
        assertEquals(IdentityProofVerification.BAD_SIGNATURE, proof(proof.tags, pubKey = stranger).verify(certificate, now = createdAt))
    }

    @Test
    fun dsaIsUnsupported() {
        val keys = KeyPairGenerator.getInstance("DSA").apply { initialize(2048) }.generateKeyPair()
        assertEquals(IdentityProofVerification.UNSUPPORTED_ALGORITHM, signedProof(keys, "SHA256withDSA").second)
    }

    // --- helpers -----------------------------------------------------------------------------

    private fun rsaKeyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    /** Signs the proof message with [keys] using [algorithm], then verifies against a certificate for [keys]. */
    private fun signedProof(
        keys: KeyPair,
        algorithm: String,
        parameters: PSSParameterSpec? = null,
    ): Pair<IdentityProofEvent, IdentityProofVerification> {
        val certificate = selfSignedCertificate(keys)
        val signature = sign(keys.private, algorithm, parameters, IdentityProofEvent.signedMessage(createdAt, expiry, developer).encodeToByteArray())
        val proof = proofFor(certificate, signature)
        return proof to proof.verify(certificate, now = createdAt)
    }

    private fun proofFor(
        certificate: ByteArray,
        signature: ByteArray,
    ): IdentityProofEvent {
        val template = IdentityProofEvent.build(Hex.encode(sha256(certificate)), Base64.encode(signature), expiry, createdAt)
        return proof(template.tags, template.createdAt)
    }

    private fun sign(
        key: PrivateKey,
        algorithm: String,
        parameters: PSSParameterSpec?,
        message: ByteArray,
    ): ByteArray =
        Signature.getInstance(algorithm).run {
            if (parameters != null) setParameter(parameters)
            initSign(key)
            update(message)
            sign()
        }

    private fun selfSignedCertificate(keys: KeyPair) = certificate(keys.public.encoded)

    /**
     * A minimal X.509 v3 certificate around [spki]. The outer signature is filler: the proof
     * check only reads the subject key, and neither zsp nor Android checks the self-signature.
     */
    private fun certificate(spki: ByteArray): ByteArray {
        val sha256WithRsa = der(0x30, der(0x06, Hex.decode("2a864886f70d01010b")), byteArrayOf(0x05, 0x00))
        val name = der(0x30, der(0x31, der(0x30, der(0x06, Hex.decode("550403")), der(0x0c, "Quartz test".encodeToByteArray()))))
        val validity = der(0x30, der(0x17, "260101000000Z".encodeToByteArray()), der(0x17, "360101000000Z".encodeToByteArray()))
        val tbs =
            der(
                0x30,
                der(0xa0, der(0x02, byteArrayOf(2))),
                der(0x02, byteArrayOf(1)),
                sha256WithRsa,
                name,
                validity,
                name,
                spki,
            )
        return der(0x30, tbs, sha256WithRsa, der(0x03, ByteArray(33)))
    }

    private fun der(
        tag: Int,
        vararg parts: ByteArray,
    ): ByteArray {
        val body = parts.fold(ByteArray(0)) { acc, part -> acc + part }
        val length =
            when {
                body.size < 0x80 -> byteArrayOf(body.size.toByte())
                body.size < 0x100 -> byteArrayOf(0x81.toByte(), body.size.toByte())
                else -> byteArrayOf(0x82.toByte(), (body.size shr 8).toByte(), body.size.toByte())
            }
        return byteArrayOf(tag.toByte()) + length + body
    }

    companion object {
        private val ED25519_SPKI_PREFIX = Hex.decode("302a300506032b6570032100")
    }
}
