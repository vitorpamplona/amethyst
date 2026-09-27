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
package com.vitorpamplona.amethyst.commons.marmot

import com.vitorpamplona.quartz.marmot.groups.MlsGroupStateStore
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What one outgoing message costs.
 *
 * Encrypting an application message is not free and not idempotent: it takes a
 * step on the sender's SecretTree ratchet and rewrites the whole encrypted
 * group state to disk. A front end that shows the message optimistically must
 * therefore be able to get the inner rumor — the thing the chat draws — WITHOUT
 * paying any of that, and must then encrypt exactly once.
 *
 * This is a regression guard for a real bug: the Android send path used to
 * build the outbound envelope twice, throwing the first one away, which
 * doubled the state writes and burned a ratchet generation per message.
 */
class MarmotSendCostTest {
    private val nostrGroupId = "c".repeat(64)

    /** A state store that counts what the MLS layer writes through it. */
    private class CountingStateStore : MlsGroupStateStore {
        val inner = SnapshotStateStore()
        var saves = 0

        override suspend fun save(
            nostrGroupId: String,
            state: ByteArray,
        ) {
            saves += 1
            inner.save(nostrGroupId, state)
        }

        override suspend fun load(nostrGroupId: String) = inner.load(nostrGroupId)

        override suspend fun delete(nostrGroupId: String) = inner.delete(nostrGroupId)

        override suspend fun listGroups() = inner.listGroups()

        override suspend fun saveRetainedEpochs(
            nostrGroupId: String,
            retainedSecrets: List<ByteArray>,
        ) = inner.saveRetainedEpochs(nostrGroupId, retainedSecrets)

        override suspend fun loadRetainedEpochs(nostrGroupId: String) = inner.loadRetainedEpochs(nostrGroupId)
    }

    private class Fixture {
        val signer = NostrSignerInternal(KeyPair())
        val mlsStore = CountingStateStore()
        val manager = MarmotManager(signer, mlsStore, SnapshotMessageStore(), SnapshotBundleStore(), publisher = ACCEPTING_RELAY)
    }

    private suspend fun Fixture.createGroup() =
        manager.createGroup(
            nostrGroupId,
            MarmotGroupData(
                nostrGroupId = nostrGroupId,
                name = "send cost",
                relays = listOf("wss://relay.invalid"),
            ),
        )

    @Test
    fun `building the inner rumor costs no group-state write`() =
        runBlocking {
            val f = Fixture()
            f.createGroup()
            val before = f.mlsStore.saves

            repeat(5) { f.manager.buildTextRumor("hello $it") }

            assertEquals(
                before,
                f.mlsStore.saves,
                "buildTextRumor must not touch MLS state — it is what a front end calls to show a message before encrypting it",
            )
        }

    @Test
    fun `the rumor carries the id the chat will draw`() =
        runBlocking {
            val f = Fixture()
            f.createGroup()

            val rumor = f.manager.buildTextRumor("hello")
            val outbound = f.manager.buildGroupMessage(nostrGroupId, rumor)

            // The optimistic insert keys the bubble by the rumor id, and the
            // envelope links back to it so relay OKs reach that same bubble.
            assertEquals(9, rumor.kind)
            assertEquals("hello", rumor.content)
            assertEquals("", rumor.sig, "MIP-03: the inner event must stay unsigned")
            assertEquals(nostrGroupId, outbound.nostrGroupId)
        }

    @Test
    fun `one message encrypts once`() =
        runBlocking {
            val f = Fixture()
            f.createGroup()
            val before = f.mlsStore.saves

            val rumor = f.manager.buildTextRumor("only once")
            f.manager.buildGroupMessage(nostrGroupId, rumor)

            assertEquals(
                before + 1,
                f.mlsStore.saves,
                "a single send must take a single ratchet step and a single state write",
            )
        }

    @Test
    fun `buildTextMessage still bundles a ready-to-publish envelope for headless callers`() =
        runBlocking {
            // The CLI publishes `bundle.outbound` directly, so the combined
            // form has to keep working — and cost exactly the one encryption.
            val f = Fixture()
            f.createGroup()
            val before = f.mlsStore.saves

            val bundle = f.manager.buildTextMessage(nostrGroupId, "from the cli")

            assertEquals(before + 1, f.mlsStore.saves)
            assertEquals("from the cli", bundle.innerEvent.content)
            assertEquals(nostrGroupId, bundle.outbound.nostrGroupId)
        }
}
