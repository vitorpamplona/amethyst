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
package com.vitorpamplona.amethyst.commons.napplet.signers

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NostrSignerPermissionLedgerTest {
    private val coordinate = "aa".repeat(32) + ":demo"

    @Test
    fun reasonableAutoApprovesAdditivePublicContentKinds() =
        runTest {
            val ledger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())
            ledger.setPolicy(coordinate, AppSignerPolicy.REASONABLE)

            // Every kind in the curated set auto-allows; encryption too.
            for (kind in NostrSignerPermissionLedger.REASONABLE_SIGN_KINDS) {
                assertEquals(
                    NostrOpDecision.ALLOW,
                    ledger.decide(coordinate, NostrSignerOp.SignKind(kind)),
                    "kind $kind should be auto-approved under REASONABLE",
                )
            }
            assertEquals(NostrOpDecision.ALLOW, ledger.decide(coordinate, NostrSignerOp.Encrypt))
        }

    @Test
    fun reasonableStillAsksForRiskyKindsAndDecryption() =
        runTest {
            val ledger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())
            ledger.setPolicy(coordinate, AppSignerPolicy.REASONABLE)

            // Profile (0), contacts (3), deletion (5), relay list (10002), nutzap (9321), and
            // gift-wrapped DM (1059) must never auto-sign — they change config, delete, spend ecash,
            // or leak. A nutzap in particular *is* the payment (it carries the ecash proofs), unlike a
            // zap request (9734) which only fetches an invoice. Decryption reveals private content, so
            // it also asks.
            for (kind in listOf(0, 3, 5, 10002, 9321, 1059)) {
                assertEquals(
                    NostrOpDecision.ASK,
                    ledger.decide(coordinate, NostrSignerOp.SignKind(kind)),
                    "kind $kind must still prompt under REASONABLE",
                )
            }
            assertEquals(NostrOpDecision.ASK, ledger.decide(coordinate, NostrSignerOp.Decrypt))

            // Contrast: a Lightning zap request (9734) auto-signs (the payment prompts separately),
            // but an ecash nutzap (9321) does not — publishing it spends the tokens.
            assertEquals(NostrOpDecision.ALLOW, ledger.decide(coordinate, NostrSignerOp.SignKind(9734)))
            assertEquals(NostrOpDecision.ASK, ledger.decide(coordinate, NostrSignerOp.SignKind(9321)))
        }

    @Test
    fun fullTrustAllowsEveryKindAndDecryption() =
        runTest {
            val ledger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())
            ledger.setPolicy(coordinate, AppSignerPolicy.FULL_TRUST)

            assertEquals(NostrOpDecision.ALLOW, ledger.decide(coordinate, NostrSignerOp.SignKind(9999)))
            assertEquals(NostrOpDecision.ALLOW, ledger.decide(coordinate, NostrSignerOp.Decrypt))
        }

    @Test
    fun paranoidAsksForEverything() =
        runTest {
            val ledger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())
            ledger.setPolicy(coordinate, AppSignerPolicy.PARANOID)

            assertEquals(NostrOpDecision.ASK, ledger.decide(coordinate, NostrSignerOp.SignKind(1)))
            assertEquals(NostrOpDecision.ASK, ledger.decide(coordinate, NostrSignerOp.Encrypt))
        }

    @Test
    fun noPolicyAsks() =
        runTest {
            val ledger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())
            assertEquals(NostrOpDecision.ASK, ledger.decide(coordinate, NostrSignerOp.SignKind(1)))
        }
}
