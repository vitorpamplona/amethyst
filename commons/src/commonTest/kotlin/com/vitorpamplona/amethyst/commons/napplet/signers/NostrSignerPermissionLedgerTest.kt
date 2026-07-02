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

            // Profile (0), contacts (3), deletion (5), relay list (10002), nutzap (9321), NIP-98 HTTP
            // auth (27235), gift-wrapped DM (1059), report (1984), torrent (2003), and long-form
            // article (30023) must never auto-sign — they change config, delete, spend ecash, authorize
            // arbitrary HTTP calls, leak, carry reputational/legal weight, or overwrite prior versions.
            // A nutzap in particular *is* the payment (it carries the ecash proofs), unlike a zap
            // request (9734) which only fetches an invoice; and NIP-98 authorizes destructive/admin HTTP
            // requests, unlike NIP-42 relay auth (22242) which is a replay-bound read proof. Decryption
            // reveals private content, so it also asks.
            for (kind in listOf(0, 3, 5, 10002, 9321, 27235, 1059, 1984, 2003, 30023)) {
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

            // Contrast: NIP-42 relay auth (22242) auto-signs — it is a replay-bound read proof — but
            // NIP-98 HTTP auth (27235) does not, since it can authorize destructive/admin HTTP calls.
            assertEquals(NostrOpDecision.ALLOW, ledger.decide(coordinate, NostrSignerOp.SignKind(22242)))
            assertEquals(NostrOpDecision.ASK, ledger.decide(coordinate, NostrSignerOp.SignKind(27235)))
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
