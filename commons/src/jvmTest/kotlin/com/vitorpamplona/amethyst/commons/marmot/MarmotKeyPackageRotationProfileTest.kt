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

import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageBundleStore
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageUtils
import com.vitorpamplona.quartz.marmot.mls.group.MarmotMessageStore
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupStateStore
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A rotation must publish the same kind of KeyPackage the first publication
 * did.
 *
 * MIP-00 makes us replace a KeyPackage as soon as a Welcome consumes it, so
 * rotation is not a rare path — it runs right after the first group we are
 * ever invited to. Minting the replacement through the legacy generator meant
 * that from that moment on the only KeyPackage on relays for us was a
 * MIP-era one, without the account identity proof (`0x8009`) that a
 * current-profile peer requires. MDK then refuses it outright
 * (`member KeyPackage identity or profile is invalid`) and keeps inviting from
 * whatever stale copy it still has cached, so the account silently becomes
 * uninvitable one join after it was set up.
 */
class MarmotKeyPackageRotationProfileTest {
    private val relay =
        RelayUrlNormalizer.normalizeOrNull("wss://example.invalid/")
            ?: error("test relay must normalize")

    @Test
    fun aRotatedKeyPackageStaysOnTheCurrentProfile() =
        runBlocking {
            val signer = NostrSignerInternal(KeyPair())
            val manager = MarmotManager(signer, RotationStateStore(), RotationMessageStore(), RotationBundleStore())

            val first = manager.generateKeyPackageEvent(listOf(relay))
            assertTrue(first.isCurrentProfile(), "the first publication is current-profile")

            // A Welcome consumed it, which is what schedules the replacement.
            manager.keyPackageRotationManager.markConsumedByEventId(first.id)
            assertTrue(manager.needsKeyPackageRotation())

            val rotated = manager.rotateConsumedKeyPackages(listOf(relay))
            assertEquals(1, rotated.size, "one consumed slot means one replacement")
            val replacement = rotated.single()

            assertTrue(
                replacement.isCurrentProfile(),
                "the replacement must carry the account identity proof too, or no current-profile " +
                    "peer can add us after our first join",
            )
            assertEquals(
                first.dTag(),
                replacement.dTag(),
                "the replacement lands in the same addressable slot",
            )
            assertTrue(
                replacement.id != first.id,
                "the replacement must actually be a different KeyPackage",
            )
            assertTrue(
                KeyPackageUtils.isValid(replacement),
                "and it must validate under the same MIP-00 rules a peer applies",
            )
            assertTrue(
                !manager.needsKeyPackageRotation(),
                "the slot is no longer pending once its replacement is minted",
            )
        }
}

// Minimal in-memory stores, matching the ones the other MarmotManager tests
// use; the file-backed implementations live in the platform modules.

private class RotationStateStore : MlsGroupStateStore {
    private val states = mutableMapOf<String, ByteArray>()
    private val retained = mutableMapOf<String, List<ByteArray>>()

    override suspend fun save(
        nostrGroupId: String,
        state: ByteArray,
    ) {
        states[nostrGroupId] = state
    }

    override suspend fun load(nostrGroupId: String): ByteArray? = states[nostrGroupId]

    override suspend fun delete(nostrGroupId: String) {
        states.remove(nostrGroupId)
        retained.remove(nostrGroupId)
    }

    override suspend fun listGroups(): List<String> = states.keys.toList()

    override suspend fun saveRetainedEpochs(
        nostrGroupId: String,
        retainedSecrets: List<ByteArray>,
    ) {
        retained[nostrGroupId] = retainedSecrets
    }

    override suspend fun loadRetainedEpochs(nostrGroupId: String): List<ByteArray> = retained[nostrGroupId] ?: emptyList()
}

private class RotationMessageStore : MarmotMessageStore {
    override suspend fun appendMessage(
        nostrGroupId: String,
        innerEventJson: String,
    ) = Unit

    override suspend fun loadMessages(nostrGroupId: String): List<String> = emptyList()

    override suspend fun delete(nostrGroupId: String) = Unit
}

private class RotationBundleStore : KeyPackageBundleStore {
    private var snapshot: ByteArray? = null

    override suspend fun save(snapshot: ByteArray) {
        this.snapshot = snapshot
    }

    override suspend fun load(): ByteArray? = snapshot

    override suspend fun delete() {
        snapshot = null
    }
}
