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
package com.vitorpamplona.quartz.nipXXBolt12Zaps.builder

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nipXXBolt12Zaps.verify.Bolt12ProofFixture
import com.vitorpamplona.quartz.nipXXBolt12Zaps.verify.Bolt12ZapValidation
import com.vitorpamplona.quartz.nipXXBolt12Zaps.verify.Bolt12ZapValidator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Proves the send-side assembly ([Bolt12ZapBuilder.buildProfileIntent] →
 * [Bolt12ZapBuilder.payerNote] → [Bolt12ZapBuilder.buildZap]) — the exact path
 * `Account.sendBolt12Zap` drives — produces a kind:9736 the validator accepts. Uses
 * a self-consistent fixture proof bound to the built intent; byte-exact wallet
 * interop is covered separately by [Bolt12PayerProofVectorTest].
 */
class Bolt12ZapBuilderTest {
    private val validator = Bolt12ZapValidator()
    private val recipient = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
    private val amount = 21_000L

    @Test
    fun payerNoteBindsToTheIntentId() =
        runTest {
            val signer = NostrSignerInternal(KeyPair())
            val offer = Bolt12ProofFixture.buildOffer(KeyPair(), amount)
            val intent = Bolt12ZapBuilder.buildProfileIntent(signer, recipient, amount, offer, comment = "nice")

            assertEquals(Bolt12ZapValidator.NIP_URI_PREFIX + intent.id, Bolt12ZapBuilder.payerNote(intent))
        }

    @Test
    fun builderProducesAValidatorAcceptedAttributedZap() =
        runTest {
            val signer = NostrSignerInternal(KeyPair())
            val nodeKey = KeyPair()
            val preimage = ByteArray(32) { (it + 7).toByte() }

            val offer = Bolt12ProofFixture.buildOffer(nodeKey, amount)
            val intent = Bolt12ZapBuilder.buildProfileIntent(signer, recipient, amount, offer, comment = "nice")
            val proof = Bolt12ProofFixture.buildProof(nodeKey, KeyPair(), preimage, amount, Bolt12ZapBuilder.payerNote(intent))

            val zap = Bolt12ZapBuilder.buildZap(signer, intent, proof, anonymous = false)

            val result = validator.validate(zap)
            assertIs<Bolt12ZapValidation.Valid>(result)
            assertTrue(result.proofCryptoVerified)
            assertEquals(recipient, result.recipient)
            assertEquals(signer.pubKey, result.payer)
            assertEquals(amount, result.amountMillisats)
        }

    @Test
    fun anonymousBuilderProducesAValidZapWithNoPayerTag() =
        runTest {
            // Anonymous zaps sign intent AND zap with one ephemeral key and carry no P tag.
            val ephemeral = NostrSignerInternal(KeyPair())
            val nodeKey = KeyPair()
            val preimage = ByteArray(32) { (it + 11).toByte() }

            val offer = Bolt12ProofFixture.buildOffer(nodeKey, amount)
            val intent = Bolt12ZapBuilder.buildProfileIntent(ephemeral, recipient, amount, offer)
            val proof = Bolt12ProofFixture.buildProof(nodeKey, KeyPair(), preimage, amount, Bolt12ZapBuilder.payerNote(intent))

            val zap = Bolt12ZapBuilder.buildZap(ephemeral, intent, proof, anonymous = true)

            val result = validator.validate(zap)
            assertIs<Bolt12ZapValidation.Valid>(result)
            assertTrue(result.proofCryptoVerified)
            assertNull(result.payer, "an anonymous BOLT12 zap carries no P tag")
        }
}
