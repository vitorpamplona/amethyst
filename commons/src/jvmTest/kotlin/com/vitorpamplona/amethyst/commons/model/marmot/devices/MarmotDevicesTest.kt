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

import androidx.lifecycle.ViewModelStore
import com.vitorpamplona.amethyst.commons.marmot.EncryptedDeviceGroupBlobs
import com.vitorpamplona.amethyst.commons.marmot.devices.DeviceGroupAttention
import com.vitorpamplona.amethyst.commons.marmot.devices.DeviceLinkRole
import com.vitorpamplona.amethyst.commons.marmot.devices.MarmotDeviceLinkViewModel
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotChatSetting
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotChatSettings
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupList
import com.vitorpamplona.amethyst.commons.model.preferences.SecretEncryption
import com.vitorpamplona.marmot.OutboundGroupEvent
import com.vitorpamplona.marmot.WelcomeDelivery
import com.vitorpamplona.marmot.client.InMemoryMlsGroupStateStore
import com.vitorpamplona.marmot.client.MarmotManager
import com.vitorpamplona.marmot.client.MarmotPublisher
import com.vitorpamplona.marmot.client.ingest
import com.vitorpamplona.marmot.features.accountSync.SmallStateItem
import com.vitorpamplona.marmot.features.deviceGroup.DeviceGroupTrust
import com.vitorpamplona.marmot.features.deviceGroup.MarmotDeviceGroupManager
import com.vitorpamplona.marmot.groups.MarmotMessageStore
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.io.encoding.Base64

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

    /** Where a device keeps its imported-history read times; a save throws while [failSaves]. */
    private class ReadBlobs : DeviceGroupBlobs {
        val saved = HashMap<String, ByteArray>()
        var failSaves = false
        var refused = 0

        fun record() = saved[MarmotImportedHistoryReads.RECORD]?.decodeToString()

        override suspend fun load(name: String) = saved[name]

        override suspend fun save(
            name: String,
            bytes: ByteArray,
        ) {
            if (failSaves) {
                refused++
                throw IllegalStateException("disk full")
            }
            saved[name] = bytes
        }
    }

    /** A message store that notes, per chat, what [reads] held when the chat's first message was stored. */
    private class Messages(
        val reads: ReadBlobs,
    ) : MarmotMessageStore {
        val logs = HashMap<String, MutableList<String>>()
        val epochs = HashMap<String, MutableMap<String, Long>>()
        val recordAtFirstAppend = HashMap<String, String?>()

        override suspend fun appendMessage(
            nostrGroupId: String,
            innerEventJson: String,
        ) {
            val log = logs.getOrPut(nostrGroupId) { ArrayList() }
            if (log.isEmpty()) recordAtFirstAppend[nostrGroupId] = reads.record()
            if (innerEventJson !in log) log.add(innerEventJson)
        }

        override suspend fun loadMessages(nostrGroupId: String) = logs[nostrGroupId].orEmpty().toList()

        override suspend fun delete(nostrGroupId: String) {
            logs.remove(nostrGroupId)
            epochs.remove(nostrGroupId)
        }

        override suspend fun recordEpoch(
            nostrGroupId: String,
            innerEventId: String,
            epoch: Long,
        ) {
            epochs.getOrPut(nostrGroupId) { HashMap() }[innerEventId] = epoch
        }

        override suspend fun loadEpochs(nostrGroupId: String) = epochs[nostrGroupId].orEmpty().toMap()

        fun texts(nostrGroupId: String) = loadMessagesNow(nostrGroupId).filter { it.kind == 9 }.map { it.content }

        private fun loadMessagesNow(nostrGroupId: String) = logs[nostrGroupId].orEmpty().mapNotNull { Event.fromJsonOrNull(it) }
    }

    private inner class Device(
        keys: KeyPair,
        val dir: File,
        withMessages: Boolean = false,
    ) {
        val readBlobs = ReadBlobs()
        val messages = if (withMessages) Messages(readBlobs) else null
        val importedReads = MarmotImportedHistoryReads(readBlobs)
        val imported = ArrayList<MarmotDeviceGroupManager.ImportedHistory>()

        /** Pin, mute and archive as this device's app holds them, fed from the device group. */
        val chatSettings = MarmotChatSettings(null)
        val signer = NostrSignerInternal(keys)
        lateinit var peer: Device
        val keyPackages = HashMap<HexKey, KeyPackageEvent>()

        /** False: no relay confirms this device's group Commits. */
        var relaysUp = true

        /** Set: group-event publishes wait on it, as on a slow relay. */
        var slowRelay: CompletableDeferred<Unit>? = null
        var publishesWaiting = 0
        val ownEvents = ArrayList<Event>()
        val stores = DeviceGroupBlobStores(EncryptedDeviceGroupBlobs(dir.toOkioPath(), SecretEncryption(File(dir, "secret.key"))))
        val manager =
            MarmotManager(
                signer,
                InMemoryMlsGroupStateStore(),
                messages,
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
                    slowRelay?.let {
                        publishesWaiting++
                        it.await()
                    }
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
        val devices: MarmotDevices =
            MarmotDevices(
                manager,
                signer,
                transport,
                { emptySet() },
                { clock },
                signInAnswers = stores.blobs,
                onSmallState = { state ->
                    chatSettings.applySmallState(state, manager, onLocalChange = { id, setting, value -> this@Device.devices.changeSetting(id, setting, value) })
                },
                onHistoryImported = { imported.addAll(it) },
                importedReads = importedReads,
            )

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

    private suspend fun pair(
        startExisting: Boolean = true,
        withMessages: Boolean = false,
        beforeLink: suspend (existing: Device, fresh: Device) -> Unit = { _, _ -> },
    ): Pair<Device, Device> {
        val keys = KeyPair()
        val existing = Device(keys, folder.newFolder(), withMessages)
        val fresh = Device(keys, folder.newFolder(), withMessages)
        existing.peer = fresh
        fresh.peer = existing
        existing.manager.restoreAll()
        fresh.manager.restoreAll()
        if (startExisting) existing.devices.start()
        fresh.devices.start()
        pump()
        beforeLink(existing, fresh)
        return existing to fresh
    }

    /**
     * Two devices of one account, linked and agreeing on the roster; [beforeLink] runs on both
     * once they are started, before the link.
     */
    private suspend fun linked(
        withMessages: Boolean = false,
        beforeLink: suspend (existing: Device, fresh: Device) -> Unit = { _, _ -> },
    ): Pair<Device, Device> {
        val (existing, fresh) = pair(withMessages = withMessages, beforeLink = beforeLink)
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

    @Before
    fun mainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMainDispatcher() = Dispatchers.resetMain()

    /** Runs [cancel] on a link screen's view model, then closes the screen at once, as Cancel does. */
    private suspend fun cancelAndClose(
        devices: MarmotDevices,
        role: DeviceLinkRole,
        sessionId: HexKey?,
    ) {
        val accountScope = CoroutineScope(SupervisorJob())
        val viewModel = MarmotDeviceLinkViewModel(devices, role, sessionId, null, accountScope, { clock })
        val store = ViewModelStore()
        store.put("link", viewModel)
        viewModel.cancel()
        store.clear()
        accountScope.coroutineContext.job.children
            .toList()
            .joinAll()
    }

    @Test
    fun cancelOnTheNewDevicesLinkScreenReachesTheExistingDevice() =
        runBlocking<Unit> {
            val (existing, fresh) = pair()
            fresh.devices.startLinkAsNewDevice()
            pump()
            assertTrue(
                existing.devices.state.value.prompts
                    .single()
                    .state is DeviceLinkPrompt.State.Comparing,
            )

            cancelAndClose(fresh.devices, DeviceLinkRole.NEW_DEVICE, null)
            pump()

            assertEquals(NewDeviceLinkState.Ended(DeviceLinkEndReason.CANCELLED), fresh.devices.state.value.newDevice)
            assertEquals(
                DeviceLinkPrompt.State.Ended(DeviceLinkEndReason.CANCELLED_ELSEWHERE),
                existing.devices.state.value.prompts
                    .single()
                    .state,
            )
        }

    @Test
    fun cancelOnTheExistingDevicesLinkScreenReachesTheNewDevice() =
        runBlocking<Unit> {
            val (existing, fresh) = pair()
            fresh.devices.startLinkAsNewDevice()
            pump()
            val prompt =
                existing.devices.state.value.prompts
                    .single()

            cancelAndClose(existing.devices, DeviceLinkRole.EXISTING_DEVICE, prompt.sessionId)
            pump()

            assertTrue(
                existing.devices.state.value.prompts
                    .single()
                    .state is DeviceLinkPrompt.State.Ended,
            )
            val ended = fresh.devices.state.value.newDevice
            assertTrue("the new device's screen ends too: $ended", ended is NewDeviceLinkState.Ended)
            assertFalse(
                fresh.devices.groups
                    .trust()
                    .trustsDeviceGroup,
            )
        }

    @Test
    fun pinMuteAndArchiveReachTheOtherDeviceAndBack() =
        runBlocking<Unit> {
            val (existing, fresh) = linked()
            // The siblings hand each other private KeyPackages, so a new chat includes both.
            existing.devices.tick()
            fresh.devices.tick()
            pump()
            val chat = "c7".repeat(32)
            existing.devices.createChat(chat, listOf("wss://relay.invalid"), null)
            pump()
            assertTrue("the sibling joined the chat", fresh.manager.groupManager.isMember(chat))

            existing.devices.changeSetting(chat, MarmotChatSetting.PIN, true)
            pump()
            assertTrue(
                fresh.chatSettings.state.value
                    .isPinned(chat),
            )
            // An archive from the sibling unpins here (as in White Noise), and that unpin goes back.
            existing.devices.changeSetting(chat, MarmotChatSetting.ARCHIVE, true)
            pump()
            assertFalse(
                fresh.chatSettings.state.value
                    .isPinned(chat),
            )
            assertTrue(
                fresh.chatSettings.state.value
                    .isArchived(chat),
            )
            assertEquals(
                "false",
                (
                    existing.devices.groups
                        .smallState()
                        .held("${existing.manager.mlsGroupIdHex(chat)}/pin") as SmallStateItem.Versioned
                ).value,
            )

            fresh.devices.changeSetting(chat, MarmotChatSetting.MUTE, true)
            fresh.devices.changeSetting(chat, MarmotChatSetting.PIN, false)
            pump()
            assertTrue(
                existing.chatSettings.state.value
                    .isMuted(chat),
            )
            assertFalse(
                existing.chatSettings.state.value
                    .isPinned(chat),
            )
            assertTrue(
                existing.chatSettings.state.value
                    .isArchived(chat),
            )
        }

    /** Built with the account, so a device with no screen open still sends and fetches Blossom history. */
    @Test
    fun historyBlobsGivenAtConstructionReachTheDeviceGroup() {
        val device = Device(KeyPair(), folder.newFolder())
        assertTrue("none by default", device.devices.groups.historyBlobFetcher == null)
        val blobs =
            object : MarmotHistoryBlobs {
                override suspend fun upload(ciphertext: ByteArray) = listOf("https://blossom.invalid")

                override suspend fun fetch(
                    url: String,
                    maxBytes: Long,
                ) = byteArrayOf(1)
            }
        val withBlobs = MarmotDevices(device.manager, device.signer, device.transport, { emptySet() }, { clock }, historyBlobs = blobs)
        assertTrue(withBlobs.groups.historyBlobFetcher != null && withBlobs.groups.historyBlobUploader != null)
    }

    /** A chat the existing device had with someone else before the link, with one message in it. */
    private val earlierChat = "c9".repeat(32)

    private suspend fun chatBeforeTheLink(existing: Device) {
        // marmot-kmp dates a kept delivery by the wall clock and expires it a week later by the
        // tick's: the two must agree, or the first tick drops the delivery as two months old.
        clock = TimeUtils.now()
        // The founding Add of another account is what makes the chat require account devices.
        val other = MarmotManager(NostrSignerInternal(KeyPair()), InMemoryMlsGroupStateStore(), null, null, MarmotPublisher { _, _ -> true })
        val kp = other.generateKeyPackageEvent(relays = emptyList())
        existing.manager.createCurrentProfileGroup(earlierChat, listOf("wss://relay.invalid"), null)
        existing.manager.addMemberInvites(earlierChat, listOf(MarmotManager.MemberInvite(other.signer.pubKey, Base64.decode(kp.content), kp.id)), emptyList())
        existing.manager.buildTextMessage(earlierChat, "before the link")
        pump()
    }

    /** Runs both devices' timers until the new device holds [earlierChat]. */
    private suspend fun untilTheChatReaches(
        fresh: Device,
        existing: Device,
    ) {
        repeat(4) {
            existing.devices.tick()
            pump()
            fresh.devices.tick()
            pump()
        }
        assertTrue("the new device was added to the earlier chat", fresh.manager.groupManager.isMember(earlierChat))
    }

    /**
     * Imported history is old news: it must never light the badge, so its read-up-to time is on
     * disk before marmot-kmp stores the first of its messages. A process death in between restores
     * the messages as read.
     */
    @Test
    fun importedHistoryIsRecordedAsReadBeforeItIsStored() =
        runBlocking<Unit> {
            val (existing, fresh) = linked(withMessages = true, beforeLink = { e, _ -> chatBeforeTheLink(e) })
            untilTheChatReaches(fresh, existing)
            val messages = fresh.messages!!
            assertEquals(listOf("before the link"), messages.texts(earlierChat))
            val record = messages.recordAtFirstAppend[earlierChat]
            assertTrue("the read time was saved before the first message: $record", record?.contains(earlierChat) == true)
            assertEquals(setOf(earlierChat), fresh.importedReads.load().keys)
            assertEquals("the app is still handed the import to show", listOf(earlierChat), fresh.imported.map { it.nostrGroupId })
        }

    /** A read time that cannot be saved holds the import back; the next pass after the disk recovers imports it. */
    @Test
    fun aReadTimeThatCannotBeSavedHoldsTheImportBack() =
        runBlocking<Unit> {
            // From before the link: the history is delivered and imported while the devices link.
            val (existing, fresh) =
                linked(withMessages = true, beforeLink = { e, f ->
                    f.readBlobs.failSaves = true
                    chatBeforeTheLink(e)
                })
            untilTheChatReaches(fresh, existing)
            val messages = fresh.messages!!
            assertEquals("nothing of the history is stored", emptyList<String>(), messages.texts(earlierChat))
            assertTrue("nor shown", fresh.imported.isEmpty())
            assertTrue(fresh.importedReads.load().isEmpty())
            assertTrue("the read time was refused", fresh.readBlobs.refused > 0)
            assertEquals("the delivery waits", 1, fresh.devices.groups.keptHistoryCount())

            fresh.readBlobs.failSaves = false
            fresh.devices.tick()
            pump()
            assertEquals(listOf("before the link"), messages.texts(earlierChat))
            assertTrue(messages.recordAtFirstAppend[earlierChat]?.contains(earlierChat) == true)
            assertEquals(0, fresh.devices.groups.keptHistoryCount())
            assertEquals(listOf(earlierChat), fresh.imported.map { it.nostrGroupId })
        }

    @Test
    fun aReadPositionReachesTheOtherDevice() =
        runBlocking<Unit> {
            val (existing, fresh) = linked()
            existing.devices.tick()
            fresh.devices.tick()
            pump()
            val chat = "c8".repeat(32)
            existing.devices.createChat(chat, listOf("wss://relay.invalid"), null)
            pump()
            val reads = ArrayList<Long>()
            existing.devices.markRead(chat, 1, clock - 5, "ab".repeat(32))
            pump()
            fresh.devices.groups
                .smallState()
                .items
                .forEach { if (it.name == "read") reads.add((it as SmallStateItem.Read).at) }
            assertEquals(listOf(clock - 5), reads)
        }

    @Test
    fun thatWasMeIsKeptAcrossARestart() =
        runBlocking<Unit> {
            val (existing, fresh) = linked()
            // First check: only the two linked devices publish.
            existing.devices.tick()
            pump()
            // Later, a third device of the account signs in.
            val third = Device(KeyPair(fresh.signer.keyPair.privKey), folder.newFolder())
            third.manager.restoreAll()
            val kp = third.manager.generateKeyPackageEvent(emptyList())
            existing.keyPackages[kp.id] = kp
            clock += MarmotDevices.SIGN_IN_CHECK_EVERY_SECS + 1
            existing.devices.tick()
            val signIn =
                existing.devices.state.value.signIns
                    .single()
            assertEquals(MarmotDeviceGroupManager.SignInClass.NEW_SIGN_IN, signIn.kind)
            assertFalse(signIn.confirmed)

            assertTrue(existing.devices.acceptSignIn(signIn.slot))
            assertTrue(
                existing.devices.state.value.signIns
                    .single()
                    .confirmed,
            )
            assertEquals(null, DeviceGroupAttention.of(existing.devices.state.value, emptySet(), emptySet(), false, false).signIn)

            // A restart: a new runtime over the same stores still knows the answer.
            val restarted = Device(KeyPair(existing.signer.keyPair.privKey), existing.dir)
            restarted.peer = fresh
            restarted.keyPackages.putAll(existing.keyPackages)
            restarted.manager.restoreAll()
            restarted.devices.start()
            clock += MarmotDevices.SIGN_IN_CHECK_EVERY_SECS + 1
            restarted.devices.tick()
            val again =
                restarted.devices.state.value.signIns
                    .single { it.slot == signIn.slot }
            assertEquals(signIn.slot, again.slot)
            assertTrue("the answer survived the restart", again.confirmed)
            assertEquals(null, DeviceGroupAttention.of(restarted.devices.state.value, emptySet(), emptySet(), false, false).signIn)
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
    fun aSlowPublishDoesNotHoldTheDeviceLock() =
        runBlocking<Unit> {
            val (existing, _) = linked()
            val gate = CompletableDeferred<Unit>()
            existing.slowRelay = gate
            // A day later: heartbeat and broadcasts are due.
            clock += 24 * 60 * 60
            val tick = launch { existing.devices.tick() }
            withTimeout(5_000) { while (existing.publishesWaiting == 0) delay(10) }
            // Another chat's event meanwhile: handled without waiting for the tick's publishes.
            withTimeout(2_000) { existing.devices.afterIngest("ef".repeat(32)) }
            gate.complete(Unit)
            tick.join()
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
            val blobs = EncryptedDeviceGroupBlobs(dir.toOkioPath(), SecretEncryption(File(dir, "secret.key")))
            val trust = DeviceGroupTrust("ab".repeat(32), tagSigningSeed = "cd".repeat(32))
            DeviceGroupBlobStores(blobs).trust.save(trust)

            val onDisk = File(dir, "${EncryptedDeviceGroupBlobs.DIR}/${DeviceGroupBlobStores.TRUST}.bin").readBytes()
            val plain = trust.encode()
            assertFalse("the seed is not written in the clear", onDisk.toHexString().contains("cd".repeat(32)))
            assertFalse(onDisk.contentEquals(plain))

            val reopened = DeviceGroupBlobStores(EncryptedDeviceGroupBlobs(dir.toOkioPath(), SecretEncryption(File(dir, "secret.key")))).trust.load()
            assertEquals(trust.encode().toList(), reopened?.encode()?.toList())
        }

    @Test
    fun anUnreadableRecordIsAnErrorNotAMissingOne() =
        runBlocking<Unit> {
            val dir = folder.newFolder()
            val blobs = EncryptedDeviceGroupBlobs(dir.toOkioPath(), SecretEncryption(File(dir, "secret.key")))
            DeviceGroupBlobStores(blobs).trust.save(DeviceGroupTrust("ab".repeat(32)))
            // Another key: the record is there but cannot be read.
            val otherKey = EncryptedDeviceGroupBlobs(dir.toOkioPath(), SecretEncryption(File(dir, "other.key")))
            val failure = runCatching { DeviceGroupBlobStores(otherKey).trust.load() }
            assertTrue("a record that does not decrypt must not read as absent", failure.isFailure)
            assertEquals(null, DeviceGroupBlobStores(EncryptedDeviceGroupBlobs(folder.newFolder().toOkioPath(), SecretEncryption(File(dir, "secret.key")))).trust.load())
        }

    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
}
