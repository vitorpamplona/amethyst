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

import com.vitorpamplona.quartz.marmot.appComponents.accountIdentityProof.AccountIdentityProofV2
import com.vitorpamplona.quartz.marmot.foundation.authorizationProofs.MarmotAuthorizationProof
import com.vitorpamplona.quartz.marmot.mip01Groups.MlsCiphersuite
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip89AppHandlers.clientTag.NostrSignerWithClientTag
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `marmot.member.account-identity-proof.v2` against the fixed vector published
 * in the spec (`app-components/account-identity-proof-v2.md`, "Signing test
 * vector").
 *
 * This vector is the whole reason Stage 2 could be built before the
 * `app_data_dictionary` carrier exists: it pins the canonical event
 * serialization, the event id, the BIP-340 signature and the 104-byte
 * component layout independently of any MLS plumbing. If these pass, a proof
 * we emit is one MDK accepts.
 */
class AccountIdentityProofV2Test {
    // BIP-340 secret key 3 — test material from the spec, never a real key.
    private val accountPubKey = "f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9"
    private val mlsSignatureKey = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f".hexToByteArray()
    private val createdAt = 1700000000L
    private val expectedEventId = "b7e9a15dd85990fb0f49c33db3cc9875f73986207b038404ceb6b7fec4e0af6b"
    private val expectedSignature =
        "c5315d3c85b9d4907cb03395a2a97b3ba2eab393f8e45b13a5d5233acedac60a" +
            "51d2a295e1b1b5ee372d18a49bdb8041a7dba9dedce722c7c6f712f78bbdfb5d"
    private val expectedComponent =
        "f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9" +
            "000000006553f100" +
            expectedSignature

    private val ciphersuite = MlsCiphersuite.DEFAULT

    private fun specProof() =
        MarmotAuthorizationProof(
            signerPubKey = accountPubKey.hexToByteArray(),
            createdAt = createdAt,
            signature = expectedSignature.hexToByteArray(),
        )

    @Test
    fun specVectorTagsAreExactAndOrdered() {
        val tags = AccountIdentityProofV2.tags(ciphersuite, mlsSignatureKey)
        assertEquals(5, tags.size, "the proof event has exactly five tags")
        assertContentEquals(arrayOf("d", "marmot.account-identity-proof.v2"), tags[0])
        assertContentEquals(arrayOf("component", "0x8009"), tags[1])
        assertContentEquals(arrayOf("ciphersuite", "0x0001"), tags[2])
        assertContentEquals(arrayOf("signature_scheme", "0x0807"), tags[3])
        assertContentEquals(
            arrayOf("mls_signature_key", "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"),
            tags[4],
        )
    }

    @Test
    fun specVectorEventId() {
        assertEquals(
            expectedEventId,
            AccountIdentityProofV2
                .proofEventId(accountPubKey, createdAt, ciphersuite, mlsSignatureKey)
                .toHexKey(),
        )
    }

    @Test
    fun specVectorComponentBytes() {
        assertEquals(MarmotAuthorizationProof.SIZE, specProof().encode().size)
        assertEquals(expectedComponent, specProof().encode().toHexKey())
    }

    @Test
    fun specVectorComponentRoundTrips() {
        val decoded = MarmotAuthorizationProof.decode(expectedComponent.hexToByteArray())
        assertEquals(accountPubKey, decoded.signerPubKeyHex)
        assertEquals(createdAt, decoded.createdAt)
        assertEquals(expectedSignature, decoded.signature.toHexKey())
        assertContentEquals(expectedComponent.hexToByteArray(), decoded.encode())
    }

    @Test
    fun specVectorValidates() {
        assertEquals(
            AccountIdentityProofV2.Result.VALID,
            AccountIdentityProofV2.validate(
                componentData = expectedComponent.hexToByteArray(),
                credentialIdentity = accountPubKey.hexToByteArray(),
                mlsSignatureKey = mlsSignatureKey,
                ciphersuite = ciphersuite,
            ),
        )
    }

    // --- every signed input actually binds ------------------------------------

    @Test
    fun aDifferentLeafSignatureKeyFailsVerification() {
        val otherLeafKey = ByteArray(32) { 0x7f }
        assertEquals(
            AccountIdentityProofV2.Result.BAD_SIGNATURE,
            AccountIdentityProofV2.validate(
                expectedComponent.hexToByteArray(),
                accountPubKey.hexToByteArray(),
                otherLeafKey,
                ciphersuite,
            ),
            "the proof must not carry over to a different MLS leaf key",
        )
    }

    @Test
    fun aDifferentCiphersuiteFailsVerification() {
        // 0x0003 shares Ed25519 with 0x0001, so only the `ciphersuite` tag
        // differs — the narrowest possible way to get this wrong.
        assertEquals(
            AccountIdentityProofV2.Result.BAD_SIGNATURE,
            AccountIdentityProofV2.validate(
                expectedComponent.hexToByteArray(),
                accountPubKey.hexToByteArray(),
                mlsSignatureKey,
                MlsCiphersuite.MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_ED25519,
            ),
            "the ciphersuite is a signed input even when the signature scheme is unchanged",
        )
    }

    @Test
    fun aDifferentCredentialIdentityIsRejectedBeforeCrypto() {
        assertEquals(
            AccountIdentityProofV2.Result.CREDENTIAL_IDENTITY_MISMATCH,
            AccountIdentityProofV2.validate(
                expectedComponent.hexToByteArray(),
                ByteArray(32) { 0x01 },
                mlsSignatureKey,
                ciphersuite,
            ),
        )
    }

    @Test
    fun missingAndMalformedComponents() {
        assertEquals(
            AccountIdentityProofV2.Result.MISSING,
            AccountIdentityProofV2.validate(null, accountPubKey.hexToByteArray(), mlsSignatureKey, ciphersuite),
        )
        val bytes = expectedComponent.hexToByteArray()
        assertEquals(
            AccountIdentityProofV2.Result.MALFORMED,
            AccountIdentityProofV2.validate(
                bytes.copyOf(bytes.size - 1),
                accountPubKey.hexToByteArray(),
                mlsSignatureKey,
                ciphersuite,
            ),
            "a truncated component is malformed",
        )
        assertEquals(
            AccountIdentityProofV2.Result.MALFORMED,
            AccountIdentityProofV2.validate(
                bytes + 0x00,
                accountPubKey.hexToByteArray(),
                mlsSignatureKey,
                ciphersuite,
            ),
            "trailing bytes are malformed, not an appended proof",
        )
    }

    @Test
    fun aFlippedSignatureBitFails() {
        val tampered = expectedComponent.hexToByteArray()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 0x01).toByte()
        assertEquals(
            AccountIdentityProofV2.Result.BAD_SIGNATURE,
            AccountIdentityProofV2.validate(
                tampered,
                accountPubKey.hexToByteArray(),
                mlsSignatureKey,
                ciphersuite,
            ),
        )
    }

    // --- envelope bounds ------------------------------------------------------

    @Test
    fun createdAtZeroIsRejected() {
        assertFailsWith<IllegalArgumentException>("created_at 0 is never a valid signing timestamp") {
            MarmotAuthorizationProof(
                accountPubKey.hexToByteArray(),
                0L,
                expectedSignature.hexToByteArray(),
            )
        }
    }

    @Test
    fun createdAtAboveTheJsonSafeIntegerIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            MarmotAuthorizationProof(
                accountPubKey.hexToByteArray(),
                MarmotAuthorizationProof.MAX_CREATED_AT + 1,
                expectedSignature.hexToByteArray(),
            )
        }
    }

    @Test
    fun aCreatedAtWithTheTopBitSetDecodesAsOutOfRange() {
        // uint64 on the wire, Long in Kotlin: a value past 2^63 reads back
        // negative, which the bounds check has to catch rather than wrap.
        val hostile =
            accountPubKey.hexToByteArray() +
                ByteArray(8) { 0xff.toByte() } +
                expectedSignature.hexToByteArray()
        assertEquals(MarmotAuthorizationProof.SIZE, hostile.size)
        assertNull(MarmotAuthorizationProof.decodeOrNull(hostile))
        assertEquals(
            AccountIdentityProofV2.Result.MALFORMED,
            AccountIdentityProofV2.validate(
                hostile,
                accountPubKey.hexToByteArray(),
                mlsSignatureKey,
                ciphersuite,
            ),
        )
    }

    // --- production round trip ------------------------------------------------

    @Test
    fun createThenValidateRoundTrip() =
        runBlocking {
            val keyPair = KeyPair()
            val signer = NostrSignerInternal(keyPair)
            val leafKey = ByteArray(32) { it.toByte() }

            val proof = AccountIdentityProofV2.create(signer, ciphersuite, leafKey, createdAt)

            assertEquals(signer.pubKey, proof.signerPubKeyHex)
            assertEquals(createdAt, proof.createdAt)
            assertEquals(
                AccountIdentityProofV2.Result.VALID,
                AccountIdentityProofV2.validate(
                    proof.encode(),
                    keyPair.pubKey,
                    leafKey,
                    ciphersuite,
                ),
            )
        }

    @Test
    fun aClientTaggedSignerStillMintsAValidProof() =
        runBlocking {
            // Amethyst's account signer appends the NIP-89 client tag to everything it signs, and
            // the setting is on by default, so this is the ordinary path on Android rather than an
            // exotic one. Signing the proof through the decorator produced tags that did not match
            // the template, and `create` -- which cannot tell a decorator's addition from a hostile
            // substitution -- threw. Every KeyPackage mint on device died there.
            val keyPair = KeyPair()
            val leafKey = ByteArray(32) { (it + 7).toByte() }
            val tagged = NostrSignerWithClientTag(NostrSignerInternal(keyPair), "Amethyst")

            val proof = AccountIdentityProofV2.create(tagged, ciphersuite, leafKey, createdAt)

            assertEquals(
                AccountIdentityProofV2.Result.VALID,
                AccountIdentityProofV2.validate(proof.encode(), keyPair.pubKey, leafKey, ciphersuite),
            )
            // Everything the proof commits to, except the signature: the decorator must not reach
            // the signed bytes at all. Not compared byte-for-byte against a bare-signer proof --
            // BIP-340 signs with auxiliary randomness, so two signatures over the same message
            // differ by design and such an assertion would fail for a reason that is not this bug.
            val bare = AccountIdentityProofV2.create(NostrSignerInternal(keyPair), ciphersuite, leafKey, createdAt)
            assertContentEquals(bare.encode().copyOfRange(0, 40), proof.encode().copyOfRange(0, 40))
            assertEquals(bare.signerPubKeyHex, proof.signerPubKeyHex)
            assertEquals(bare.createdAt, proof.createdAt)
        }

    @Test
    fun aProofDoesNotCarryToAnotherAccount() =
        runBlocking {
            val leafKey = ByteArray(32) { it.toByte() }
            val alice = KeyPair()
            val bob = KeyPair()
            val proof = AccountIdentityProofV2.create(NostrSignerInternal(alice), ciphersuite, leafKey, createdAt)

            assertNotEquals(alice.pubKey.toHexKey(), bob.pubKey.toHexKey())
            assertEquals(
                AccountIdentityProofV2.Result.CREDENTIAL_IDENTITY_MISMATCH,
                AccountIdentityProofV2.validate(proof.encode(), bob.pubKey, leafKey, ciphersuite),
                "Alice's proof must not authenticate a leaf claiming to be Bob",
            )
        }

    @Test
    fun reuseIsAllowedOnlyWhileEverySignedInputIsIdentical() =
        runBlocking {
            val signer = NostrSignerInternal(KeyPair())
            val leafKey = ByteArray(32) { it.toByte() }
            val first = AccountIdentityProofV2.create(signer, ciphersuite, leafKey, createdAt)
            val second = AccountIdentityProofV2.create(signer, ciphersuite, leafKey, createdAt)

            // Same inputs, same signed event: either proof validates for the
            // other's leaf. (BIP-340 signatures need not be byte-identical, so
            // compare behaviour rather than bytes.)
            assertTrue(
                AccountIdentityProofV2.isValid(first.encode(), signer.pubKey.hexToByteArray(), leafKey, ciphersuite),
            )
            assertTrue(
                AccountIdentityProofV2.isValid(second.encode(), signer.pubKey.hexToByteArray(), leafKey, ciphersuite),
            )

            val rotatedLeafKey = ByteArray(32) { (it + 1).toByte() }
            assertEquals(
                AccountIdentityProofV2.Result.BAD_SIGNATURE,
                AccountIdentityProofV2.validate(
                    first.encode(),
                    signer.pubKey.hexToByteArray(),
                    rotatedLeafKey,
                    ciphersuite,
                ),
                "rotating the MLS leaf key requires a fresh proof",
            )
        }
}
