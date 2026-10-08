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

import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.SoftwareAssetEvent
import com.vitorpamplona.quartz.mls.crypto.Ed25519
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.EventFactory
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdentityProofEventTest {
    /**
     * A real proof from the 2026-10-08 census: an app developer's npub vouching for its APK
     * signing certificate (an RSA-2048 key: the signature is 256 bytes). Public infrastructure.
     */
    private val realProof =
        """{"id":"58e4ef00c71a8fb45ff5f870beeff5574b4f9551f869e63011dccd0aec1f4775","pubkey":"3a2e22b77ef44d27cdcbc88cf734de5e9a9114bd3fb51de4245306d01f4cb907","created_at":1791415946,"kind":30509,"tags":[["d","797b6922b13e6f0819ccaee0e3ccd1860605051d18c0bd1149c12151b70ce6f2"],["signature","lZdDpTrSYoeOWSk30DloHCXT62NLh8/VIb10T/G2CRNgPKHs+ihqdUnkhBMOtJmiGTy9Uy2tYCMQ8o6kSt6pON3M7BUdW132uW5X/DJH7bP1kYfvyer5ZHG2zv7E/5BrAnWrqDm0z6vDkz0o6H5kfK5WmeUFdPNh22MklTu8/n9CbngM3Bp6zgW3C7x+iFp3iisvlvGaQ2rz/wuMa4FPTQkIw+QDuYibSXkadSw6kZf9klDSxsVThkvIgftV+gbjheYqB8NHGTxRgfnjcEc30k47X4Q4ee5xZqtZ5rf2DbtsuvEWpDg9/D+7bNujrlLSItesRfuAfaBzTa5N+bYHhw=="],["expiry","1822951946"]],"content":"","sig":"e04883808eb1c813fbbb896d20831dfba35882a62f2bb92a9d8c25f16e65bfb516ba4f05e4ff928a60fa3548a1477b6afe0b1f4a8cb25a78d9794fafd5dcd4c4"}"""

    private val developer = "3a2e22b77ef44d27cdcbc88cf734de5e9a9114bd3fb51de4245306d01f4cb907"
    private val certHash = "797b6922b13e6f0819ccaee0e3ccd1860605051d18c0bd1149c12151b70ce6f2"

    private fun create(
        tags: TagArray,
        createdAt: Long = 1000L,
        pubKey: String = developer,
        kind: Int = IdentityProofEvent.KIND,
    ): Event = EventFactory.create("0".repeat(64), pubKey, createdAt, kind, tags, "", "")

    @Test
    fun factoryBuildsTheProof() {
        assertTrue(EventFactory.isKnownKind(IdentityProofEvent.KIND))
        assertIs<IdentityProofEvent>(Event.fromJson(realProof))
    }

    @Test
    fun realProofAccessors() {
        val proof = assertIs<IdentityProofEvent>(Event.fromJson(realProof))
        assertEquals(certHash, proof.certificateHash())
        assertEquals(1822951946L, proof.expiry())
        assertEquals(256, proof.signatureBytes()?.size)
        assertFalse(proof.isRevoked())
        assertNull(proof.revocationReason())
        assertTrue(proof.isWellFormed())
        assertFalse(proof.isExpired(now = 1791415946L))
        assertTrue(proof.isExpired(now = 1822951947L))
        assertEquals(
            "Verifying at 1791415946 until 1822951946 that I control the following Nostr public key: $developer",
            proof.signedMessage(),
        )
        assertTrue(proof.isForCertificate(certHash.uppercase()))
        // An RSA signature is not an Ed25519 one.
        assertFalse(proof.verifyEd25519Signature(ByteArray(32)))
    }

    @Test
    fun noEdgesNotSearchable() {
        val proof = Event.fromJson(realProof)
        assertFalse(proof is SearchableEvent)
        assertFalse(proof is PubKeyHintProvider)
        assertFalse(proof is EventHintProvider)
        assertFalse(proof is AddressHintProvider)
    }

    @Test
    fun pairsWithTheSameDevelopersAssetSignedByThatCertificate() {
        val proof = assertIs<IdentityProofEvent>(Event.fromJson(realProof))
        val signedAsset = assertIs<SoftwareAssetEvent>(create(arrayOf(arrayOf("apk_certificate_hash", certHash)), kind = SoftwareAssetEvent.KIND))
        val otherCert = assertIs<SoftwareAssetEvent>(create(arrayOf(arrayOf("apk_certificate_hash", "1".repeat(64))), kind = SoftwareAssetEvent.KIND))
        val otherPublisher = assertIs<SoftwareAssetEvent>(create(arrayOf(arrayOf("apk_certificate_hash", certHash)), pubKey = "2".repeat(64), kind = SoftwareAssetEvent.KIND))
        assertTrue(proof.claimsSignerOf(signedAsset))
        assertFalse(proof.claimsSignerOf(otherCert))
        assertFalse(proof.claimsSignerOf(otherPublisher))
    }

    @Test
    fun certificateHashIsTheDerSha256() {
        val der = "a certificate's DER bytes".encodeToByteArray()
        val proof = assertIs<IdentityProofEvent>(create(arrayOf(arrayOf("d", Hex.encode(sha256(der))))))
        assertTrue(proof.certificateHashMatches(der))
        assertFalse(proof.certificateHashMatches("another certificate".encodeToByteArray()))
    }

    @Test
    fun ed25519ProofsVerify() {
        val certificateKey = Ed25519.keyPairFromSeed(ByteArray(32) { it.toByte() })
        val createdAt = 1791415946L
        val expiry = createdAt + 365L * 24 * 60 * 60
        val message = IdentityProofEvent.signedMessage(createdAt, expiry, developer)
        val signature = Ed25519.sign(message.encodeToByteArray(), certificateKey.privateKey)

        val template = IdentityProofEvent.build(certHash, Base64.encode(signature), expiry, createdAt)
        val proof = assertIs<IdentityProofEvent>(create(template.tags, template.createdAt))
        assertTrue(proof.verifyEd25519Signature(certificateKey.publicKey))

        // The same signature does not vouch for another npub, another timestamp or another key.
        assertFalse(assertIs<IdentityProofEvent>(create(template.tags, template.createdAt, pubKey = "2".repeat(64))).verifyEd25519Signature(certificateKey.publicKey))
        assertFalse(assertIs<IdentityProofEvent>(create(template.tags, template.createdAt + 1)).verifyEd25519Signature(certificateKey.publicKey))
        val stranger = Ed25519.keyPairFromSeed(ByteArray(32) { 7 })
        assertFalse(proof.verifyEd25519Signature(stranger.publicKey))
        assertFalse(proof.verifyEd25519Signature(ByteArray(5)))
    }

    @Test
    fun malformedTagsReadAsNull() {
        val proof =
            assertIs<IdentityProofEvent>(
                create(
                    arrayOf(
                        arrayOf("d", "not-a-hash"),
                        arrayOf("signature", "%%% not base64 %%%"),
                        arrayOf("expiry", "next year"),
                    ),
                ),
            )
        assertNull(proof.certificateHash())
        assertNull(proof.signatureBytes())
        assertNull(proof.expiry())
        assertNull(proof.signedMessage())
        assertFalse(proof.isWellFormed())
        assertTrue(proof.isExpired())
        assertFalse(proof.isForCertificate("not-a-hash"))
        assertFalse(proof.verifyEd25519Signature(ByteArray(32)))

        // Expiry must come after created_at.
        val backwards = assertIs<IdentityProofEvent>(create(arrayOf(arrayOf("d", certHash), arrayOf("signature", "AA=="), arrayOf("expiry", "999")), createdAt = 1000L))
        assertFalse(backwards.isWellFormed())
        assertNull(assertIs<IdentityProofEvent>(create(arrayOf(arrayOf("signature", "")))).signature())
    }

    @Test
    fun revocationIsReadWithOrWithoutAReason() {
        assertTrue(assertIs<IdentityProofEvent>(create(arrayOf(arrayOf("d", certHash), arrayOf("revoked")))).isRevoked())
        val withReason = assertIs<IdentityProofEvent>(create(arrayOf(arrayOf("d", certHash), arrayOf("revoked", "key compromised"))))
        assertTrue(withReason.isRevoked())
        assertEquals("key compromised", withReason.revocationReason())
    }

    @Test
    fun buildRoundTrips() {
        val template =
            IdentityProofEvent.build(certHash, "c2ln", 2000L, createdAt = 1000L) {
                revoked("rotated")
            }
        assertEquals(IdentityProofEvent.KIND, template.kind)
        assertEquals(1000L, template.createdAt)
        val proof = assertIs<IdentityProofEvent>(create(template.tags, template.createdAt))
        assertEquals(certHash, proof.certificateHash())
        assertEquals("c2ln", proof.signature())
        assertEquals(2000L, proof.expiry())
        assertTrue(proof.isRevoked())
        assertEquals("rotated", proof.revocationReason())
        assertTrue(proof.isWellFormed())
    }
}
