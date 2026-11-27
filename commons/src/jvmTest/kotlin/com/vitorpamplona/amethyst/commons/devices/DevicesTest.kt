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
package com.vitorpamplona.amethyst.commons.devices

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What [Devices] promises over its platforms, against stand-in backends. */
class DevicesTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @AfterTest
    fun stop() = scope.cancel()

    /** A platform whose look the test sets: what it lists, how long it takes, or that it fails. */
    private class FakeBackend(
        override val platform: DevicePlatform,
        private val claim: (String) -> Boolean,
    ) : DeviceBackend {
        @Volatile var devices: List<DeviceInfo> = emptyList()

        @Volatile var lookMs = 0L

        @Volatile var fail: Exception? = null

        @Volatile var hang = false
        val calls = mutableListOf<String>()

        override fun claims(serial: String) = claim(serial)

        override suspend fun refresh(): BackendSnapshot {
            if (hang) awaitCancellation()
            delay(lookMs)
            fail?.let { throw it }
            val setup = if (platform == DevicePlatform.IOS) IosSetup(xcodeFound = true, idbFound = true) else AndroidSetup(adbFound = true, emulatorFound = true)
            return BackendSnapshot(devices, listOf(StartableDevice("v-$platform", platform, "Virtual", "", "0001")), setup)
        }

        override suspend fun start(
            id: String,
            options: StartOptions,
            owner: String?,
            ownerLabel: String?,
        ): String {
            calls += "start $id $owner"
            return "started-$id"
        }

        override suspend fun stop(serial: String) {
            calls += "stop $serial"
        }

        override suspend fun reset(serial: String): String {
            calls += "reset $serial"
            return "$serial-fresh"
        }

        override suspend fun connection(serial: String): DeviceDriver = throw IOException("no driver for $serial in this test")

        override fun releaseOwner(owner: String) {
            calls += "release $owner"
        }

        override fun shutdown() {
            calls += "shutdown"
        }
    }

    private fun device(
        serial: String,
        platform: DevicePlatform,
        kind: DeviceKind = if (platform == DevicePlatform.IOS) DeviceKind.SIMULATOR else DeviceKind.EMULATOR,
        owner: String? = null,
    ) = DeviceInfo(serial, serial, platform, kind, DeviceStatus.ONLINE, agentAllowed = kind != DeviceKind.PHYSICAL, owner = owner)

    private val android = FakeBackend(DevicePlatform.ANDROID) { !it.contains('-') }
    private val ios = FakeBackend(DevicePlatform.IOS) { it.contains('-') }

    @Test
    fun everyPlatformIsAskedAtOnce() =
        runTest {
            android.lookMs = 400
            ios.lookMs = 400
            android.devices = listOf(device("emulator-5554", DevicePlatform.ANDROID))
            ios.devices = listOf(device("AAAA-1", DevicePlatform.IOS))
            val devices = Devices(listOf(android, ios), scope)

            // Virtual time: the looks' delays pass on the test's clock, so a slow machine can't fail this.
            val started = testScheduler.currentTime
            devices.refresh()

            // At once, the two 400 ms looks take 400 ms; one after the other would take 800.
            assertEquals(400, testScheduler.currentTime - started)
            assertEquals(
                listOf("emulator-5554", "AAAA-1"),
                devices.state.value.devices
                    .map { it.serial },
            )
            assertTrue(devices.state.value.android.adbFound)
            assertTrue(devices.state.value.ios.idbFound)
            assertEquals(
                listOf("v-ANDROID", "v-IOS"),
                devices.state.value.startable
                    .map { it.id },
            )
        }

    @Test
    fun aPlatformThatFailsKeepsWhatItShowedAndSaysWhy() =
        runBlocking {
            android.devices = listOf(device("emulator-5554", DevicePlatform.ANDROID))
            ios.devices = listOf(device("AAAA-1", DevicePlatform.IOS))
            val devices = Devices(listOf(android, ios), scope)
            devices.refresh()

            ios.fail = IOException("simctl: CoreSimulatorService connection became invalid")
            android.devices = listOf(device("emulator-5554", DevicePlatform.ANDROID), device("emulator-5556", DevicePlatform.ANDROID))
            devices.refresh()

            val state = devices.state.value
            // The simulator still shows (it didn't go away: simctl stumbled), and the other platform moved on.
            assertEquals(listOf("emulator-5554", "emulator-5556", "AAAA-1"), state.devices.map { it.serial })
            assertEquals("simctl: CoreSimulatorService connection became invalid", state.ios.error)
            assertNull(state.android.error)
        }

    @Test
    fun anUnexpectedErrorInOnePlatformDoesNotTakeTheOtherDown() =
        runBlocking {
            ios.fail = IllegalStateException("unexpected JSON")
            android.devices = listOf(device("emulator-5554", DevicePlatform.ANDROID))
            val devices = Devices(listOf(android, ios), scope)

            devices.refresh()

            assertEquals(
                listOf("emulator-5554"),
                devices.state.value.devices
                    .map { it.serial },
            )
            assertTrue(devices.state.value.loaded)
        }

    @Test
    fun aPlatformThatHangsDoesNotHoldUpTheOther() =
        runTest {
            android.devices = listOf(device("emulator-5554", DevicePlatform.ANDROID))
            ios.devices = listOf(device("AAAA-1", DevicePlatform.IOS))
            val devices = Devices(listOf(android, ios), scope, refreshTimeoutMs = 300)
            devices.refresh()

            ios.hang = true
            android.devices = emptyList()
            // Virtual time, as above: the hanging platform is cut off at its 300 ms, not waited on.
            val started = testScheduler.currentTime
            devices.refresh()

            assertEquals(300, testScheduler.currentTime - started)
            assertEquals(
                listOf("AAAA-1"),
                devices.state.value.devices
                    .map { it.serial },
            )
            assertEquals("didn't answer in 300ms", devices.state.value.ios.error)
        }

    @Test
    fun eachCallGoesToTheDevicesOwnPlatform(): Unit =
        runBlocking {
            android.devices = listOf(device("emulator-5554", DevicePlatform.ANDROID))
            ios.devices = listOf(device("AAAA-1", DevicePlatform.IOS))
            val devices = Devices(listOf(android, ios), scope)
            devices.refresh()

            devices.stop("AAAA-1")
            devices.stop("emulator-5554")
            // A copy just made, before any look lists it, is still its platform's.
            devices.stop("BBBB-2")
            assertEquals("started-v-IOS", devices.start("v-IOS", owner = "ws"))

            assertEquals(listOf("stop AAAA-1", "stop BBBB-2", "start v-IOS ws"), ios.calls)
            assertEquals(listOf("stop emulator-5554"), android.calls)
            assertFailsWith<IOException> { devices.start("nothing") }
        }

    @Test
    fun aResetThatMakesAFreshCopyMovesTheChatsPickToIt() =
        runBlocking {
            ios.devices = listOf(device("AAAA-1", DevicePlatform.IOS, owner = "ws"))
            val devices = Devices(listOf(android, ios), scope)
            devices.refresh()
            devices.select("ws", "AAAA-1")

            assertEquals("AAAA-1-fresh", devices.reset("AAAA-1"))

            assertEquals("AAAA-1-fresh", devices.selection.value["ws"])
        }

    @Test
    fun thePhonesLeaveIsTheUsersAndLastsThroughEachLook(): Unit =
        runBlocking {
            android.devices = listOf(device("R58M", DevicePlatform.ANDROID, kind = DeviceKind.PHYSICAL), device("emulator-5554", DevicePlatform.ANDROID))
            val devices = Devices(listOf(android, ios), scope)
            devices.refresh()
            assertFalse(devices.device("R58M")!!.agentAllowed)
            assertTrue(devices.device("emulator-5554")!!.agentAllowed)

            devices.allowAgent("R58M", true)
            devices.refresh()

            assertTrue(assertNotNull(devices.device("R58M")).agentAllowed)
            assertFailsWith<IOException> { devices.stop("R58M") }
        }

    @Test
    fun releasingAWorkstreamReachesEveryPlatform() {
        val devices = Devices(listOf(android, ios), scope)
        devices.releaseOwner("ws")
        devices.shutdown()
        assertEquals(listOf("release ws", "shutdown"), android.calls)
        assertEquals(listOf("release ws", "shutdown"), ios.calls)
    }

    @Test
    fun aPlatformThatFailsItsFirstLookSaysWhyInsteadOfLookingMissing() =
        runBlocking {
            ios.fail = IOException("simctl: CoreSimulatorService is not running")
            val devices = Devices(listOf(android, ios), scope)

            devices.refresh()

            val setup = devices.state.value.ios
            assertTrue(setup.xcodeFound)
            assertEquals("simctl: CoreSimulatorService is not running", setup.error)
        }

    @Test
    fun aDeviceThatCannotReportCrashesIsNotAsked() =
        runBlocking {
            // The fake's connection throws: a poll that asked it would fail.
            ios.devices = listOf(device("AAAA-1", DevicePlatform.IOS).copy(canReadCrashes = false))
            val devices = Devices(listOf(android, ios), scope)
            devices.refresh()

            assertEquals(emptyList(), devices.pollCrashes("AAAA-1"))
        }
}
