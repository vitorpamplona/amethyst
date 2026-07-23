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
package com.vitorpamplona.quartz.nipXXBolt12Zaps.verify

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nipXXBolt12Zaps.intent.Bolt12ZapIntentEvent
import com.vitorpamplona.quartz.nipXXBolt12Zaps.zap.Bolt12ZapEvent
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Bolt12ZapValidatorTest {
    private val validator = Bolt12ZapValidator()
    private val recipient = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
    private val amount = 21_000L
    private val zapId = "ab".repeat(16)

    private suspend fun signedIntent(
        signer: NostrSigner,
        offer: String,
    ) = signer.sign(Bolt12ZapIntentEvent.buildProfileZap(recipient, amount, offer, zapId, comment = "nice"))

    private suspend fun signedZap(
        signer: NostrSigner,
        intent: Bolt12ZapIntentEvent,
        proof: String,
        attributed: Boolean = true,
    ) = signer.sign(Bolt12ZapEvent.build(intent, proof, payerPubKey = if (attributed) signer.pubKey else null))

    @Test
    fun acceptsAWellFormedFullyVerifiedZap() =
        runTest {
            val signer = NostrSignerInternal(KeyPair())
            val nodeKey = KeyPair()
            val payerLnKey = KeyPair()
            val preimage = ByteArray(32) { (it + 7).toByte() }

            val offer = Bolt12ProofFixture.buildOffer(nodeKey, amount)
            val intent = signedIntent(signer, offer)
            val note = Bolt12ZapValidator.NIP_URI_PREFIX + intent.id
            val proof = Bolt12ProofFixture.buildProof(nodeKey, payerLnKey, preimage, amount, note)
            val zap = signedZap(signer, intent, proof)

            val result = validator.validate(zap)
            assertIs<Bolt12ZapValidation.Valid>(result)
            assertTrue(result.proofCryptoVerified)
            assertEquals(recipient, result.recipient)
            assertEquals(signer.pubKey, result.payer)
            assertEquals(amount, result.amountMillisats)
            assertEquals(Hex.encode(sha256(preimage)), result.paymentHashHex)
            assertTrue(result.isProfileZap)
        }

    @Test
    fun acceptsButFlagsACompressedProofAsUnverified() =
        runTest {
            val signer = NostrSignerInternal(KeyPair())
            val nodeKey = KeyPair()
            val preimage = ByteArray(32) { (it + 1).toByte() }
            val offer = Bolt12ProofFixture.buildOffer(nodeKey, amount)
            val intent = signedIntent(signer, offer)
            val note = Bolt12ZapValidator.NIP_URI_PREFIX + intent.id
            val proof = Bolt12ProofFixture.buildProof(nodeKey, KeyPair(), preimage, amount, note, compressed = true)

            val result = validator.validate(signedZap(signer, intent, proof))
            assertIs<Bolt12ZapValidation.Valid>(result)
            assertTrue(!result.proofCryptoVerified, "a compressed proof is bound but not yet crypto-verified")
        }

    @Test
    fun rejectsAProofBoundToTheWrongIntent() =
        runTest {
            val signer = NostrSignerInternal(KeyPair())
            val nodeKey = KeyPair()
            val preimage = ByteArray(32) { (it + 2).toByte() }
            val offer = Bolt12ProofFixture.buildOffer(nodeKey, amount)
            val intent = signedIntent(signer, offer)
            val wrongNote = Bolt12ZapValidator.NIP_URI_PREFIX + "f".repeat(64)
            val proof = Bolt12ProofFixture.buildProof(nodeKey, KeyPair(), preimage, amount, wrongNote)

            val result = validator.validate(signedZap(signer, intent, proof))
            assertEquals(Bolt12ZapValidation.Invalid(Bolt12ZapValidation.Reason.PROOF_NOTE_MISMATCH), result)
        }

    @Test
    fun rejectsWhenTheProofAmountDiffersFromTheZapAmount() =
        runTest {
            val signer = NostrSignerInternal(KeyPair())
            val nodeKey = KeyPair()
            val preimage = ByteArray(32) { (it + 3).toByte() }
            val offer = Bolt12ProofFixture.buildOffer(nodeKey, amount)
            val intent = signedIntent(signer, offer)
            val note = Bolt12ZapValidator.NIP_URI_PREFIX + intent.id
            val proof = Bolt12ProofFixture.buildProof(nodeKey, KeyPair(), preimage, amount + 1, note)

            val result = validator.validate(signedZap(signer, intent, proof))
            assertEquals(Bolt12ZapValidation.Invalid(Bolt12ZapValidation.Reason.PROOF_AMOUNT_MISMATCH), result)
        }

    @Test
    fun rejectsAnInvalidPayerProofSignature() =
        runTest {
            val signer = NostrSignerInternal(KeyPair())
            val nodeKey = KeyPair()
            val preimage = ByteArray(32) { (it + 4).toByte() }
            val offer = Bolt12ProofFixture.buildOffer(nodeKey, amount)
            val intent = signedIntent(signer, offer)
            val note = Bolt12ZapValidator.NIP_URI_PREFIX + intent.id
            val proof = Bolt12ProofFixture.buildProof(nodeKey, KeyPair(), preimage, amount, note, breakProofSignature = true)

            val result = validator.validate(signedZap(signer, intent, proof))
            assertEquals(Bolt12ZapValidation.Invalid(Bolt12ZapValidation.Reason.PROOF_SIGNATURE_INVALID), result)
        }

    @Test
    fun rejectsWhenTheEmbeddedIntentWasSignedByAnotherKey() =
        runTest {
            val signer = NostrSignerInternal(KeyPair())
            val otherSigner = NostrSignerInternal(KeyPair())
            val nodeKey = KeyPair()
            val preimage = ByteArray(32) { (it + 5).toByte() }
            val offer = Bolt12ProofFixture.buildOffer(nodeKey, amount)
            // Intent signed by someone other than the zap author.
            val intent = signedIntent(otherSigner, offer)
            val note = Bolt12ZapValidator.NIP_URI_PREFIX + intent.id
            val proof = Bolt12ProofFixture.buildProof(nodeKey, KeyPair(), preimage, amount, note)

            val result = validator.validate(signedZap(signer, intent, proof))
            assertEquals(Bolt12ZapValidation.Invalid(Bolt12ZapValidation.Reason.INTENT_PUBKEY_MISMATCH), result)
        }

    @Test
    fun rejectsWhenThePreimageDoesNotHashToThePaymentHash() =
        runTest {
            val signer = NostrSignerInternal(KeyPair())
            val nodeKey = KeyPair()
            val preimage = ByteArray(32) { (it + 6).toByte() }
            val offer = Bolt12ProofFixture.buildOffer(nodeKey, amount)
            val intent = signedIntent(signer, offer)
            val note = Bolt12ZapValidator.NIP_URI_PREFIX + intent.id
            val proof = Bolt12ProofFixture.buildProof(nodeKey, KeyPair(), preimage, amount, note, corruptPaymentHash = true)

            val result = validator.validate(signedZap(signer, intent, proof))
            assertEquals(Bolt12ZapValidation.Invalid(Bolt12ZapValidation.Reason.PROOF_PREIMAGE_MISMATCH), result)
        }

    @Test
    fun rejectsAnInvalidInvoiceSignature() =
        runTest {
            val signer = NostrSignerInternal(KeyPair())
            val nodeKey = KeyPair()
            val preimage = ByteArray(32) { (it + 8).toByte() }
            val offer = Bolt12ProofFixture.buildOffer(nodeKey, amount)
            val intent = signedIntent(signer, offer)
            val note = Bolt12ZapValidator.NIP_URI_PREFIX + intent.id
            val proof = Bolt12ProofFixture.buildProof(nodeKey, KeyPair(), preimage, amount, note, breakInvoiceSignature = true)

            val result = validator.validate(signedZap(signer, intent, proof))
            assertEquals(Bolt12ZapValidation.Invalid(Bolt12ZapValidation.Reason.PROOF_INVOICE_SIGNATURE_INVALID), result)
        }

    @Test
    fun rejectsAPayerTagThatIsNotTheEventAuthor() =
        runTest {
            val signer = NostrSignerInternal(KeyPair())
            val nodeKey = KeyPair()
            val preimage = ByteArray(32) { (it + 9).toByte() }
            val offer = Bolt12ProofFixture.buildOffer(nodeKey, amount)
            val intent = signedIntent(signer, offer)
            val note = Bolt12ZapValidator.NIP_URI_PREFIX + intent.id
            val proof = Bolt12ProofFixture.buildProof(nodeKey, KeyPair(), preimage, amount, note)

            // Attribute the zap to someone other than its signer.
            val zap = signer.sign(Bolt12ZapEvent.build(intent, proof, payerPubKey = "c".repeat(64)))
            assertEquals(Bolt12ZapValidation.Invalid(Bolt12ZapValidation.Reason.PAYER_TAG_MISMATCH), validator.validate(zap))
        }

    @Test
    fun rejectsWhenTheProofDoesNotMatchTheOffer() =
        runTest {
            val signer = NostrSignerInternal(KeyPair())
            val offerNodeKey = KeyPair()
            val proofNodeKey = KeyPair() // a different node than the offer's issuer
            val preimage = ByteArray(32) { (it + 10).toByte() }
            val offer = Bolt12ProofFixture.buildOffer(offerNodeKey, amount)
            val intent = signedIntent(signer, offer)
            val note = Bolt12ZapValidator.NIP_URI_PREFIX + intent.id
            val proof = Bolt12ProofFixture.buildProof(proofNodeKey, KeyPair(), preimage, amount, note)

            val result = validator.validate(signedZap(signer, intent, proof))
            assertEquals(Bolt12ZapValidation.Invalid(Bolt12ZapValidation.Reason.OFFER_PROOF_MISMATCH), result)
        }

    @Test
    fun skipsTheEventSignatureCheckWhenTheCallerAlreadyVerifiedIt() =
        runTest {
            val signer = NostrSignerInternal(KeyPair())
            val nodeKey = KeyPair()
            val preimage = ByteArray(32) { (it + 11).toByte() }
            val offer = Bolt12ProofFixture.buildOffer(nodeKey, amount)
            val intent = signedIntent(signer, offer)
            val note = Bolt12ZapValidator.NIP_URI_PREFIX + intent.id
            val proof = Bolt12ProofFixture.buildProof(nodeKey, KeyPair(), preimage, amount, note)
            val validZap = signedZap(signer, intent, proof)

            // Same event, but its own signature is corrupted (id still matches content).
            val tamperedSig =
                Bolt12ZapEvent(validZap.id, validZap.pubKey, validZap.createdAt, validZap.tags, validZap.content, "0".repeat(128))

            // Default: the bad event signature is caught.
            assertEquals(
                Bolt12ZapValidation.Invalid(Bolt12ZapValidation.Reason.BAD_EVENT_SIGNATURE),
                validator.validate(tamperedSig),
            )
            // Ingest path: the pipeline already verified the event, so skipping is safe and it validates.
            assertIs<Bolt12ZapValidation.Valid>(validator.validate(tamperedSig, verifyEventSignature = false))
        }
}
