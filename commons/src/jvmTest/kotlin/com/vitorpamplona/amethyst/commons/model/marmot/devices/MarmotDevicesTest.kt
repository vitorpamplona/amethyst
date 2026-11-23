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
package com.vitorpamplona.amethyst.commons.model.marmot.devices

import com.vitorpamplona.amethyst.commons.marmot.EncryptedDeviceGroupBlobs
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupList
import com.vitorpamplona.amethyst.commons.model.preferences.SecretEncryption
import com.vitorpamplona.marmot.OutboundGroupEvent
import com.vitorpamplona.marmot.WelcomeDelivery
import com.vitorpamplona.marmot.client.InMemoryMlsGroupStateStore
import com.vitorpamplona.marmot.client.MarmotManager
import com.vitorpamplona.marmot.client.MarmotPublisher
import com.vitorpamplona.marmot.client.ingest
import com.vitorpamplona.marmot.features.deviceGroup.DeviceGroupTrust
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [MarmotDevices] end to end in one process: two devices of one account pair with the seven
 * emoji over a fake relay (every gift wrap reaches both devices, echoes included, as on Nostr),
 * link, and agree on the roster; the device group stays out of the chat list; the stores
 * round-trip through encryption at rest.
 */
class MarmotDevicesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var clock = 1_800_000_000L

    /** Everything published, delivered later by [pump] so no device's lock is held across a hop. */
    private val mailbox = ArrayDeque<Pair<Device, () -> Event>>()

    private inner class Device(
        keys: KeyPair,
        dir: File,
    ) {
        val signer = NostrSignerInternal(keys)
        lateinit var peer: Device
        val keyPackages = HashMap<HexKey, KeyPackageEvent>()

        /** False: no relay confirms this device's group Commits. */
        var relaysUp = true
        val ownEvents = ArrayList<Event>()
        val stores = DeviceGroupBlobStores(EncryptedDeviceGroupBlobs(dir, SecretEncryption(File(dir, "secret.key"))))
        val manager =
            MarmotManager(
                signer,
                InMemoryMlsGroupStateStore(),
                null,
                null,
                // The manager publishes commits itself (publish-before-apply): onto the same relay.
                MarmotPublisher { event, _ ->
                    if (relaysUp) mailbox.addLast(peer to { event })
                    relaysUp
                },
                deviceGroupConfig = stores.config(listOf("wss://relay.invalid")),
            )
        val transport =
            object : MarmotDevicesTransport {
                override suspend fun publishGroupEvent(event: OutboundGroupEvent): Boolean {
                    mailbox.addLast(peer to { event.signedEvent })
                    return true
                }

                override suspend fun publishToSelf(wrap: GiftWrapEvent): Boolean {
                    mailbox.addLast(peer to { wrap })
                    mailbox.addLast(this@Device to { wrap })
                    return true
                }

                override suspend fun publishWelcome(delivery: WelcomeDelivery) = true

                override suspend fun publishKeyPackage(event: KeyPackageEvent): Boolean {
                    keyPackages[event.id] = event
                    peer.keyPackages[event.id] = event
                    return true
                }

                override suspend fun fetchOwnKeyPackage(eventId: HexKey) = keyPackages[eventId]

                override suspend fun ownKeyPackages() = keyPackages.values.toList()

                override suspend fun publishOwnEvent(event: Event): Boolean {
                    ownEvents.add(event)
                    return true
                }
            }
        val devices = MarmotDevices(manager, signer, transport, { emptySet() }, { clock })

        suspend fun receive(event: Event) {
            if (event is GiftWrapEvent && devices.onGiftWrap(event)) return
            manager.ingest(event)
            devices.afterIngest()
        }
    }

    private suspend fun pump() {
        var guard = 0
        while (mailbox.isNotEmpty() && guard++ < 500) {
            val (to, event) = mailbox.removeFirst()
            to.receive(event())
        }
    }

    private suspend fun pair(startExisting: Boolean = true): Pair<Device, Device> {
        val keys = KeyPair()
        val existing = Device(keys, folder.newFolder())
        val fresh = Device(keys, folder.newFolder())
        existing.peer = fresh
        fresh.peer = existing
        existing.manager.restoreAll()
        fresh.manager.restoreAll()
        if (startExisting) existing.devices.start()
        fresh.devices.start()
        pump()
        return existing to fresh
    }

    /** Two devices of one account, linked and agreeing on the roster. */
    private suspend fun linked(): Pair<Device, Device> {
        val (existing, fresh) = pair()
        fresh.devices.startLinkAsNewDevice()
        pump()
        val prompt =
            existing.devices.state.value.prompts
                .single()
        fresh.devices.confirmMatch()
        existing.devices.approveLink(prompt.sessionId, "phone")
        pump()
        existing.devices.tick()
        pump()
        fresh.devices.tick()
        pump()
        assertTrue(existing.devices.state.value.linked && fresh.devices.state.value.linked)
        return existing to fresh
    }

    @Test
    fun aRemovalNoRelayConfirmedKeepsTheDeviceAndItsSlot() =
        runBlocking<Unit> {
            val (existing, fresh) = linked()
            val slot = checkNotNull(fresh.devices.state.value.ownSlot)
            existing.relaysUp = false

            assertFalse(existing.devices.removeDevice(slot))
            assertTrue("no KeyPackage deletion went out", existing.ownEvents.isEmpty())
            assertTrue(
                existing.devices.state.value.devices
                    .any { it.slot == slot },
            )

            existing.relaysUp = true
            assertTrue(existing.devices.removeDevice(slot))
            assertFalse(
                existing.devices.state.value.devices
                    .any { it.slot == slot },
            )
        }

    @Test
    fun aSignOutNoRelayConfirmedSaysSo() =
        runBlocking<Unit> {
            val (_, fresh) = linked()
            fresh.relaysUp = false
            assertFalse(fresh.devices.signOutThroughSiblings())
        }

    @Test
    fun aLinkRequestThatArrivesBeforeStartIsAnsweredOnceStarted() =
        runBlocking<Unit> {
            // The app opens gift wraps while restoreAll still runs, and never sees the wrap again.
            val (existing, fresh) = pair(startExisting = false)
            fresh.devices.startLinkAsNewDevice()
            pump()
            assertTrue(
                existing.devices.state.value.prompts
                    .isEmpty(),
            )

            existing.devices.start()
            pump()

            val prompt =
                existing.devices.state.value.prompts
                    .single()
            assertTrue(prompt.state is DeviceLinkPrompt.State.Comparing)
            assertTrue(fresh.devices.state.value.newDevice is NewDeviceLinkState.Comparing)
        }

    @Test
    fun twoDevicesPairWithTheSameSevenEmojiAndAgreeOnTheRoster() =
        runBlocking<Unit> {
            val (existing, fresh) = pair()

            fresh.devices.startLinkAsNewDevice()
            pump()

            val newSide = fresh.devices.state.value.newDevice as NewDeviceLinkState.Comparing
            val prompt =
                existing.devices.state.value.prompts
                    .single()
            val existingSide = prompt.state as DeviceLinkPrompt.State.Comparing
            assertEquals(7, newSide.code.size)
            assertEquals("both screens show the same code", newSide.code, existingSide.code)
            assertTrue(existingSide.canLink)
            assertEquals(fresh.devices.state.value.ownSlot, prompt.slot)

            fresh.devices.confirmMatch()
            assertEquals(NewDeviceLinkState.WaitingForOtherDevice, fresh.devices.state.value.newDevice)
            existing.devices.approveLink(prompt.sessionId, "phone")
            pump()

            assertEquals(NewDeviceLinkState.Linked, fresh.devices.state.value.newDevice)
            existing.devices.tick()
            pump()
            val a = existing.devices.state.value
            val b = fresh.devices.state.value
            assertTrue(a.linked && b.linked)
            assertEquals(setOf(a.ownSlot, b.ownSlot), a.devices.map { it.slot }.toSet())
            assertEquals(a.devices.map { it.slot }.toSet(), b.devices.map { it.slot }.toSet())
            assertEquals("phone", a.devices.single { it.slot == b.ownSlot }.label)
            assertEquals(
                DeviceGroupTrust.Outcome.MADE,
                existing.devices.groups
                    .trust()
                    .approvals
                    .single()
                    .outcome,
            )

            // The device group is never a chat on either device.
            val deviceGroupId = checkNotNull(fresh.devices.groups.deviceGroupNostrId())
            val list = MarmotGroupList(fresh.signer.pubKey)
            list.isHidden = fresh.devices::isDeviceGroup
            list.getOrCreateGroup(deviceGroupId)
            assertFalse(list.rooms.containsKey(deviceGroupId))
            assertTrue(existing.devices.isDeviceGroup(deviceGroupId))

            // A restart reads the trust record back through the encrypted store.
            val reloaded = checkNotNull(fresh.stores.trust.load())
            assertEquals(deviceGroupId, reloaded.trustedNostrGroupId)
        }

    @Test
    fun codesThatDifferEndTheLinkOnBothDevices() =
        runBlocking<Unit> {
            val (existing, fresh) = pair()
            fresh.devices.startLinkAsNewDevice()
            pump()
            fresh.devices.rejectMatch()
            pump()
            assertEquals(NewDeviceLinkState.Ended(DeviceLinkEndReason.CODES_DIFFER), fresh.devices.state.value.newDevice)
            assertEquals(
                DeviceLinkPrompt.State.Ended(DeviceLinkEndReason.CODES_DIFFER),
                existing.devices.state.value.prompts
                    .single()
                    .state,
            )
            assertFalse(existing.devices.state.value.linked)
            assertFalse(
                fresh.devices.groups
                    .trust()
                    .trustsDeviceGroup,
            )
        }

    @Test
    fun aDeviceDoesNotAnswerItsOwnRequest() =
        runBlocking<Unit> {
            val (_, fresh) = pair()
            fresh.devices.startLinkAsNewDevice()
            // Only the echo reaches the requester: nothing must answer it.
            mailbox.removeAll { it.first !== fresh }
            pump()
            assertEquals(NewDeviceLinkState.WaitingForAnswer, fresh.devices.state.value.newDevice)
            assertTrue(
                fresh.devices.state.value.prompts
                    .isEmpty(),
            )
        }

    @Test
    fun deviceGroupRecordsAreEncryptedAtRest() =
        runBlocking<Unit> {
            val dir = folder.newFolder()
            val blobs = EncryptedDeviceGroupBlobs(dir, SecretEncryption(File(dir, "secret.key")))
            val trust = DeviceGroupTrust("ab".repeat(32), tagSigningSeed = "cd".repeat(32))
            DeviceGroupBlobStores(blobs).trust.save(trust)

            val onDisk = File(dir, "${EncryptedDeviceGroupBlobs.DIR}/${DeviceGroupBlobStores.TRUST}.bin").readBytes()
            val plain = trust.encode()
            assertFalse("the seed is not written in the clear", onDisk.toHexString().contains("cd".repeat(32)))
            assertFalse(onDisk.contentEquals(plain))

            val reopened = DeviceGroupBlobStores(EncryptedDeviceGroupBlobs(dir, SecretEncryption(File(dir, "secret.key")))).trust.load()
            assertEquals(trust.encode().toList(), reopened?.encode()?.toList())
        }

    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
}
