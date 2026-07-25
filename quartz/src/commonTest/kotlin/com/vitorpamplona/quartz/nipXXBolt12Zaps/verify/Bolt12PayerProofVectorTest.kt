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

import com.vitorpamplona.quartz.TestResourceLoader
import com.vitorpamplona.quartz.nipXXBolt12Zaps.bolt12.Bolt12PayerProof
import com.vitorpamplona.quartz.nipXXBolt12Zaps.bolt12.Bolt12ProofBuilder
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Byte-exact interop against the lightning/bolts#1346 conformance suite
 * (`bolt12/payer-proof-test.json`), the draft's own CLN/LDK-generated vectors.
 *
 *  - Every `valid_vectors` entry must fully verify ([Bolt12ProofVerifier] returns
 *    [Bolt12ProofResult.Valid]) — this exercises the compressed-proof merkle
 *    reconstruction and both BIP-340 signature checks against real proofs.
 *  - Every `invalid_vectors` entry must be rejected (never [Bolt12ProofResult.Valid]).
 *  - The writer ([Bolt12ProofBuilder]) reproduces each valid vector's
 *    `proof_omitted_tlvs` / `proof_missing_hashes` / `proof_leaf_hashes` exactly.
 */
class Bolt12PayerProofVectorTest {
    private val vectors by lazy {
        Json.parseToJsonElement(TestResourceLoader().loadString("bolt12/payer-proof-test.json")).jsonObject
    }

    private val verifier = Bolt12ProofVerifier()

    @Test
    fun everyValidVectorVerifies() {
        val valid = vectors["valid_vectors"]!!.jsonArray
        assertTrue(valid.size >= 5, "expected the full valid vector set")
        for (vector in valid) {
            val obj = vector.jsonObject
            val name = obj["name"]!!.jsonPrimitive.content
            val bech32 = obj["result"]!!.jsonObject["bech32"]!!.jsonPrimitive.content
            val proof = Bolt12PayerProof.parse(bech32) ?: fail("valid vector '$name' failed to parse")
            val result = verifier.verify(proof)
            assertIs<Bolt12ProofResult.Valid>(result, "valid vector '$name' did not verify: $result")
        }
    }

    @Test
    fun everyInvalidVectorIsRejected() {
        val invalid = vectors["invalid_vectors"]!!.jsonArray
        assertTrue(invalid.size >= 20, "expected the full invalid vector set")
        for (vector in invalid) {
            val obj = vector.jsonObject
            val reason = obj["reason"]?.jsonPrimitive?.content ?: "?"
            val bech32 = obj["bech32"]!!.jsonPrimitive.content
            val proof = Bolt12PayerProof.parse(bech32)
            // Rejection is either an unparseable stream or any non-Valid crypto result.
            val verified = proof?.let { verifier.verify(it) }
            assertTrue(
                verified !is Bolt12ProofResult.Valid,
                "invalid vector '$reason' was accepted",
            )
        }
    }

    @Test
    fun writerReproducesEveryValidVectorCompressionFields() {
        val valid = vectors["valid_vectors"]!!.jsonArray
        for (vector in valid) {
            val obj = vector.jsonObject
            val name = obj["name"]!!.jsonPrimitive.content
            val working = obj["working"]!!.jsonObject

            val invoiceFields =
                obj["input"]!!
                    .jsonObject["invoice_fields"]!!
                    .jsonArray
                    .map { it.jsonObject }
                    .filter { it["type"]!!.jsonPrimitive.content.toLong() !in 240L..1000L }
                    .map {
                        Bolt12ProofBuilder.InvoiceField(
                            type = it["type"]!!.jsonPrimitive.content.toLong(),
                            value = Hex.decode(it["hex"]!!.jsonPrimitive.content),
                            include = it["included"]!!.jsonPrimitive.content.toBoolean(),
                        )
                    }

            // Deterministic compression fields don't depend on the signatures, so
            // dummy signers suffice; we read the minted proof back and compare.
            val note = obj["input"]!!.jsonObject["note"]?.jsonPrimitive?.content
            val minted =
                Bolt12ProofBuilder.build(
                    invoiceFields = invoiceFields,
                    preimage = Hex.decode(obj["input"]!!.jsonObject["preimage"]!!.jsonPrimitive.content),
                    proofNote = note,
                    signInvoiceDigest = { ByteArray(64) },
                    signProofDigest = { ByteArray(64) },
                )
            val proof = Bolt12PayerProof.parse(minted) ?: fail("writer output for '$name' failed to parse")

            val expectedMarkers = working["proof_omitted_tlvs"]!!.jsonArray.map { it.jsonPrimitive.content.toLong() }
            assertEquals(expectedMarkers, proof.omittedTlvMarkers(), "proof_omitted_tlvs mismatch for '$name'")

            val expectedMissing = working["proof_missing_hashes"]!!.jsonArray.map { it.jsonPrimitive.content }
            assertEquals(expectedMissing, proof.missingHashList()!!.map { Hex.encode(it) }, "proof_missing_hashes mismatch for '$name'")

            val expectedLeaves = working["proof_leaf_hashes"]!!.jsonArray.map { it.jsonPrimitive.content }
            assertEquals(expectedLeaves, proof.leafHashList()!!.map { Hex.encode(it) }, "proof_leaf_hashes mismatch for '$name'")

            // The optional proof_note (1005) must round-trip when the vector carries one.
            assertEquals(note, proof.proofNote(), "proof_note mismatch for '$name'")
        }
    }
}
