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
package com.vitorpamplona.quartz.marmot.mip00KeyPackages

import com.vitorpamplona.quartz.marmot.appComponents.CurrentProfileGroupFactory
import com.vitorpamplona.quartz.marmot.mls.tree.Extension
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A KeyPackage marked LastResort (MLS extension `0x000A`) is deliberately
 * NOT single-use. OpenMLS says so in as many words — on the Welcome path it
 * deletes the consumed bundle only `if !key_package_bundle.key_package().last_resort()`,
 * and logs "KeyPackage has a last-resort marker, not deleting" otherwise — and
 * MDK marks every KeyPackage it publishes as last resort, caches the peer
 * KeyPackage it resolved in its user directory, and re-uses that same cached
 * copy for every later invite of that peer.
 *
 * We publish the LastResort marker too (OpenMLS's own KeyPackage validation
 * wants it). So we have to honour the contract we advertise: dropping the
 * private keys the moment one Welcome consumed the KeyPackage makes every
 * subsequent invite addressed to that same KeyPackage unjoinable, which is
 * exactly what the MDK interop harness saw — one invite worked and the four
 * that followed failed with "No matching KeyPackageBundle".
 */
class LastResortKeyPackageReuseTest {
    private val identity = ByteArray(32) { 0x11 }

    @Test
    fun aLastResortKeyPackageStaysUsableAfterItIsConsumed() =
        runBlocking {
            val manager = KeyPackageRotationManager()
            val slot = manager.getOrCreateSlotDTag("primary")
            val bundle = manager.generateKeyPackage(identity, slot)
            assertTrue(
                "generateKeyPackage must mark the KeyPackage last-resort — MDK requires it",
                bundle.keyPackage.isLastResort(),
            )

            val eventId = "a".repeat(64)
            manager.recordPublishedEventId(slot, eventId)

            manager.markConsumedByEventId(eventId)

            assertNotNull(
                "a last-resort KeyPackage must still resolve by event id after it was consumed",
                manager.findBundleByEventId(eventId),
            )
            assertNotNull(
                "a last-resort KeyPackage must still resolve by MLS KeyPackageRef after it was consumed",
                manager.findBundleByRef(bundle.keyPackage.reference()),
            )
            assertTrue(
                "consuming it still schedules a fresh publication for the slot",
                manager.needsRotation(),
            )
        }

    @Test
    fun aSingleUseKeyPackageIsStillDroppedWhenConsumed() =
        runBlocking {
            val manager = KeyPackageRotationManager()
            val slot = manager.getOrCreateSlotDTag("primary")
            val bundle = manager.generateKeyPackage(identity, slot)
            // Strip the LastResort marker: without it MLS single-use applies
            // and forward secrecy says the init key must not survive.
            val singleUse = bundle.copy(keyPackage = bundle.keyPackage.copy(extensions = emptyList()))
            manager.installBundle(slot, singleUse)
            assertFalse(singleUse.keyPackage.isLastResort())

            val eventId = "b".repeat(64)
            manager.recordPublishedEventId(slot, eventId)
            manager.markConsumedByEventId(eventId)

            assertNull(
                "a KeyPackage without the last-resort marker is single-use and must be dropped",
                manager.findBundleByEventId(eventId),
            )
            assertNull(manager.findBundleByRef(singleUse.keyPackage.reference()))
        }

    @Test
    fun rotatingTheSlotKeepsTheConsumedKeyPackageReachable() =
        runBlocking {
            val manager = KeyPackageRotationManager()
            val slot = manager.getOrCreateSlotDTag("primary")
            val consumed = manager.generateKeyPackage(identity, slot)
            val firstEventId = "c".repeat(64)
            manager.recordPublishedEventId(slot, firstEventId)
            manager.markConsumedByEventId(firstEventId)

            // MIP-00 rotation publishes a replacement into the same d-tag slot.
            val rotated = manager.rotateSlot(identity, slot)
            val secondEventId = "d".repeat(64)
            manager.recordPublishedEventId(slot, secondEventId)

            assertEquals(
                "the slot now serves the rotated KeyPackage",
                rotated.keyPackage.reference().toList(),
                manager
                    .findBundleByEventId(secondEventId)!!
                    .keyPackage
                    .reference()
                    .toList(),
            )
            assertEquals(
                "an invite that still references the consumed KeyPackage must keep working",
                consumed.keyPackage.reference().toList(),
                manager
                    .findBundleByEventId(firstEventId)!!
                    .keyPackage
                    .reference()
                    .toList(),
            )
        }

    @Test
    fun retainedKeyPackagesAreBoundedAndDropTheOldestFirst() =
        runBlocking {
            val manager = KeyPackageRotationManager()
            val slot = manager.getOrCreateSlotDTag("primary")
            val eventIds = mutableListOf<String>()
            repeat(KeyPackageRotationManager.MAX_RETAINED_BUNDLES + 2) { i ->
                manager.generateKeyPackage(identity, slot)
                val eventId = i.toString().padStart(64, '0')
                eventIds.add(eventId)
                manager.recordPublishedEventId(slot, eventId)
                manager.markConsumedByEventId(eventId)
            }

            assertNull(
                "the oldest consumed KeyPackage must age out of the bounded retention window",
                manager.findBundleByEventId(eventIds.first()),
            )
            assertNotNull(
                "the most recently consumed KeyPackage must still be reachable",
                manager.findBundleByEventId(eventIds.last()),
            )
        }

    @Test
    fun retainedKeyPackagesSurviveAPersistenceRoundTrip() =
        runBlocking {
            val store = InMemoryKeyPackageBundleStore()
            val manager = KeyPackageRotationManager(store)
            val slot = manager.getOrCreateSlotDTag("primary")
            val consumed = manager.generateKeyPackage(identity, slot)
            val eventId = "e".repeat(64)
            manager.recordPublishedEventId(slot, eventId)
            manager.markConsumedByEventId(eventId)
            manager.rotateSlot(identity, slot)

            val restored = KeyPackageRotationManager(store)
            restored.restoreFromStore()

            assertEquals(
                "a restart must not lose the private keys of a consumed last-resort KeyPackage",
                consumed.keyPackage.reference().toList(),
                restored
                    .findBundleByEventId(eventId)!!
                    .keyPackage
                    .reference()
                    .toList(),
            )
        }

    /**
     * The current profile does not use the MLS Extensions draft's `0x000A`
     * extension type at all — it carries a `last_resort_key_package` component
     * inside the KeyPackage-level `app_data_dictionary` (`0x0006`). Reading only
     * the MIP-era carrier makes every KeyPackage we actually publish today look
     * single-use, which is exactly the case the interop harness exercises.
     */
    @Test
    fun aCurrentProfileKeyPackageIsRecognisedAsLastResort() =
        runBlocking {
            val signer = NostrSignerInternal(KeyPair())
            val manager = KeyPackageRotationManager()
            val slot = manager.getOrCreateSlotDTag("primary")
            val bundle = manager.generateCurrentProfileKeyPackage(signer, slot)
            assertTrue(
                "the current profile marks last resort with a dictionary component, not extension 0x000A",
                bundle.keyPackage.isLastResort(),
            )

            val eventId = "f".repeat(64)
            manager.recordPublishedEventId(slot, eventId)
            manager.markConsumedByRef(bundle.keyPackage.reference())

            assertNotNull(
                "a consumed current-profile KeyPackage must stay reusable",
                manager.findBundleByRef(bundle.keyPackage.reference()),
            )
            assertNotNull(manager.findBundleByEventId(eventId))
        }

    @Test
    fun aCurrentProfileKeyPackageWithoutTheComponentIsSingleUse() =
        runBlocking {
            val signer = NostrSignerInternal(KeyPair())
            val bundle = CurrentProfileGroupFactory.createKeyPackage(signer, lastResort = false)
            assertFalse(bundle.keyPackage.isLastResort())
        }

    @Test
    fun theLastResortMarkerIsReadFromTheKeyPackageExtensions() {
        val manager = KeyPackageRotationManager()
        val bundle = runBlocking { manager.generateKeyPackage(identity) }
        assertTrue(bundle.keyPackage.isLastResort())
        assertFalse(bundle.keyPackage.copy(extensions = emptyList()).isLastResort())
        assertFalse(
            bundle.keyPackage
                .copy(extensions = listOf(Extension(extensionType = 0x0001, extensionData = ByteArray(0))))
                .isLastResort(),
        )
    }

    private class InMemoryKeyPackageBundleStore : KeyPackageBundleStore {
        private var bytes: ByteArray? = null

        override suspend fun load(): ByteArray? = bytes

        override suspend fun save(snapshot: ByteArray) {
            bytes = snapshot
        }

        override suspend fun delete() {
            bytes = null
        }
    }
}
