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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.quartz.cordn.spec01GroupMetadata.CordnGroupMetadata
import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnMessageKinds
import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnMessageReferences
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The send side of cordn's annotation kinds.
 *
 * The read side has been able to fold reactions, edits, deletions and pins
 * since the protocol work landed; until now nothing could produce them. What
 * is worth testing here is not the tag shapes — `CordnMessageReferences` owns
 * those and has its own round-trip tests — but that the manager refuses the
 * sends whose results would be silently discarded by the very fold that
 * receives them.
 */
class CordnMessageActionsTest {
    private val coordinatorKey = "d".repeat(64)
    private val aliceSigner = NostrSignerSync(KeyPair())
    private val alice: HexKey get() = aliceSigner.pubKey
    private val bob: HexKey = "b".repeat(64)
    private val gid = "actions"

    private fun manager(coordinator: FakeCoordinator) =
        CordnGroupManager(
            accountPubKey = alice,
            config =
                CoordinatorConfig(
                    pubKey = coordinatorKey,
                    relays = listOf(RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!),
                    origin = CoordinatorConfig.Origin.DEFAULT,
                ),
            coordinator = coordinator,
            store = InMemoryCordnGroupStore(),
            clock = { 1_757_000_000L },
        )

    private fun target(
        author: HexKey,
        id: String = "aa".repeat(32),
    ) = CordnMessageReferences.Target(id = id, pubKey = author, kind = CordnMessageKinds.TEXT)

    @Test
    fun `a reply goes out as a NIP-22 thread reply`() =
        runTest {
            val manager = manager(FakeCoordinator(callerPubKey = alice))
            manager.createGroup(gid, CordnGroupMetadata(name = "Actions"))

            val sent = manager.post(gid, "in reply", replyTo = target(bob)).envelope

            assertEquals(CordnMessageKinds.THREAD_REPLY, sent.kind)
            assertEquals("in reply", sent.content)
            assertTrue(sent.tags.any { it.firstOrNull() == "E" }, "a reply carries a thread root")
        }

    @Test
    fun `a reaction is anyone's to send`() =
        runTest {
            val manager = manager(FakeCoordinator(callerPubKey = alice))
            manager.createGroup(gid, CordnGroupMetadata(name = "Actions"))

            val sent = manager.post(gid, "👍", reactionTo = target(bob)).envelope

            assertEquals(CordnMessageKinds.REACTION, sent.kind)
            assertEquals("👍", sent.content)
        }

    @Test
    fun `editing someone else's message fails at the call instead of on delivery`() =
        runTest {
            // CordnAnnotationIndex is author-only for edits, so this would be
            // accepted by the coordinator, stored, delivered -- and then
            // dropped by every client including the sender's. A send nobody
            // can see fail is worse than one that throws.
            val manager = manager(FakeCoordinator(callerPubKey = alice))
            manager.createGroup(gid, CordnGroupMetadata(name = "Actions"))

            assertFailsWith<IllegalArgumentException> {
                manager.post(gid, "not mine to change", editTo = target(bob))
            }
        }

    @Test
    fun `deleting someone else's message fails at the call`() =
        runTest {
            val manager = manager(FakeCoordinator(callerPubKey = alice))
            manager.createGroup(gid, CordnGroupMetadata(name = "Actions"))

            assertFailsWith<IllegalArgumentException> {
                manager.post(gid, deleteTo = target(bob))
            }
        }

    @Test
    fun `editing and deleting your own message is allowed`() =
        runTest {
            val manager = manager(FakeCoordinator(callerPubKey = alice))
            manager.createGroup(gid, CordnGroupMetadata(name = "Actions"))

            assertEquals(CordnMessageKinds.EDIT, manager.post(gid, "fixed", editTo = target(alice)).envelope.kind)
            assertEquals(CordnMessageKinds.DELETION, manager.post(gid, deleteTo = target(alice)).envelope.kind)
        }

    @Test
    fun `pinning someone else's message is allowed, because a pin is any member's`() =
        runTest {
            // §5.1: reaction is anyone's, pin is any member's, only edit and
            // deletion are author-only. Checking all five the same way would
            // invent a rule cordn does not have.
            val manager = manager(FakeCoordinator(callerPubKey = alice))
            manager.createGroup(gid, CordnGroupMetadata(name = "Actions"))

            val sent = manager.post(gid, pinTo = target(bob)).envelope

            assertEquals(CordnMessageKinds.PIN, sent.kind)
        }
}
