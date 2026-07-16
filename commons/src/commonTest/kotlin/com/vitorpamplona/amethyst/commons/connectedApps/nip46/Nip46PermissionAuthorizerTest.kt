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
package com.vitorpamplona.amethyst.commons.connectedApps.nip46

import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppSignerPolicy
import com.vitorpamplona.amethyst.commons.connectedApps.signers.InMemoryNostrSignerPermissionStore
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrOpDecision
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionLedger
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Decrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import com.vitorpamplona.quartz.nip46RemoteSigner.server.Nip46ConnectDecision
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Nip46PermissionAuthorizerTest {
    private val signer = "a".repeat(64)
    private val client = "c".repeat(64)
    private val coordinate = Nip46PermissionAuthorizer.coordinateFor(signer, client)

    private fun ledger() = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())

    private fun signRequest(kind: Int) = BunkerRequestSign("1", EventTemplate<Event>(createdAt = 1L, kind = kind, tags = emptyArray(), content = "x"))

    @Test
    fun connectWithValidSecretRegistersReasonablePolicyAndEchoesSecret() =
        runTest {
            val ledger = ledger()
            val authorizer = Nip46PermissionAuthorizer(ledger, signerPubKey = signer, validateSecret = { _, s -> s == "good" })

            val decision = authorizer.onConnect(client, BunkerRequestConnect(id = "1", remoteKey = client, secret = "good"))

            assertTrue(decision is Nip46ConnectDecision.Accept)
            assertEquals("good", decision.ackSecret)
            assertEquals(AppSignerPolicy.REASONABLE, ledger.store.loadPolicy(coordinate))
        }

    @Test
    fun connectWithBadSecretRejectsAndDoesNotRegister() =
        runTest {
            val ledger = ledger()
            val authorizer = Nip46PermissionAuthorizer(ledger, signerPubKey = signer, validateSecret = { _, s -> s == "good" })

            val decision = authorizer.onConnect(client, BunkerRequestConnect(id = "1", remoteKey = client, secret = "bad"))

            assertTrue(decision is Nip46ConnectDecision.Reject)
            assertEquals(null, ledger.store.loadPolicy(coordinate))
        }

    @Test
    fun connectDoesNotDowngradeAnExistingPolicy() =
        runTest {
            val ledger = ledger()
            ledger.setPolicy(coordinate, AppSignerPolicy.FULL_TRUST)
            val authorizer = Nip46PermissionAuthorizer(ledger, signerPubKey = signer, validateSecret = { _, _ -> true })

            authorizer.onConnect(client, BunkerRequestConnect(id = "1", remoteKey = client, secret = "x"))

            assertEquals(AppSignerPolicy.FULL_TRUST, ledger.store.loadPolicy(coordinate))
        }

    @Test
    fun reasonableAppAllowsTextNoteButRefusesDecrypt() =
        runTest {
            val ledger = ledger()
            ledger.setPolicy(coordinate, AppSignerPolicy.REASONABLE)
            val authorizer = Nip46PermissionAuthorizer(ledger, signerPubKey = signer, validateSecret = { _, _ -> true })

            assertTrue(authorizer.authorize(client, signRequest(TextNoteEvent.KIND)))
            assertFalse(authorizer.authorize(client, BunkerRequestNip44Decrypt("2", client, "ct")))
        }

    @Test
    fun paranoidAppRefusesEverythingUntilPerOpGrant() =
        runTest {
            val ledger = ledger()
            ledger.setPolicy(coordinate, AppSignerPolicy.PARANOID)
            val authorizer = Nip46PermissionAuthorizer(ledger, signerPubKey = signer, validateSecret = { _, _ -> true })

            assertFalse(authorizer.authorize(client, signRequest(TextNoteEvent.KIND)))

            ledger.setOpDecision(coordinate, NostrSignerOp.SignKind(TextNoteEvent.KIND), NostrOpDecision.ALLOW)
            assertTrue(authorizer.authorize(client, signRequest(TextNoteEvent.KIND)))
        }

    @Test
    fun coordinateRoundTrips() {
        assertEquals(client, Nip46PermissionAuthorizer.clientPubKeyOf(coordinate))
        assertEquals(null, Nip46PermissionAuthorizer.clientPubKeyOf("browser:https://x.com"))
    }

    @Test
    fun sameClientOnTwoAccountsGetsIndependentCoordinates() {
        val otherSigner = "d".repeat(64)
        val a = Nip46PermissionAuthorizer.coordinateFor(signer, client)
        val b = Nip46PermissionAuthorizer.coordinateFor(otherSigner, client)
        assertTrue(a != b)
        assertTrue(Nip46PermissionAuthorizer.belongsTo(a, signer))
        assertFalse(Nip46PermissionAuthorizer.belongsTo(a, otherSigner))
    }

    @Test
    fun logoutRevokesTheClientsGrant() =
        runTest {
            val ledger = ledger()
            ledger.setPolicy(coordinate, AppSignerPolicy.FULL_TRUST)
            val authorizer = Nip46PermissionAuthorizer(ledger, signerPubKey = signer, validateSecret = { _, _ -> true })

            authorizer.onLogout(client)

            assertEquals(null, ledger.store.loadPolicy(coordinate))
        }

    @Test
    fun forgetClearsGrantAndStoreAndSignalsDisconnect() =
        runTest {
            val ledger = ledger()
            ledger.setPolicy(coordinate, AppSignerPolicy.FULL_TRUST)
            val store = InMemoryNip46ClientStore()
            store.store(coordinate, Nip46ClientInfo(name = "X", relays = setOf("wss://relay.example.com")))
            var disconnected: String? = null
            val authorizer =
                Nip46PermissionAuthorizer(
                    ledger,
                    signerPubKey = signer,
                    validateSecret = { _, _ -> true },
                    clientStore = store,
                    onDisconnected = { disconnected = it },
                )

            authorizer.forget(client)

            assertEquals(null, ledger.store.loadPolicy(coordinate), "grant cleared")
            assertEquals(null, store.load(coordinate), "stored metadata + relays cleared")
            assertEquals(client, disconnected, "host notified so it can drop the relays this session")
        }

    @Test
    fun logoutIsEquivalentToForget() =
        runTest {
            val ledger = ledger()
            ledger.setPolicy(coordinate, AppSignerPolicy.REASONABLE)
            val store = InMemoryNip46ClientStore()
            store.store(coordinate, Nip46ClientInfo(name = "X"))
            val authorizer = Nip46PermissionAuthorizer(ledger, signerPubKey = signer, validateSecret = { _, _ -> true }, clientStore = store)

            authorizer.onLogout(client)

            assertEquals(null, ledger.store.loadPolicy(coordinate))
            assertEquals(null, store.load(coordinate))
        }
}
