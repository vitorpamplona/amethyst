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
package com.vitorpamplona.amethyst.commons.marmot.devices

import com.vitorpamplona.amethyst.commons.marmot.MarmotDeviceGroupSwitch
import com.vitorpamplona.amethyst.commons.model.marmot.devices.DeviceGroupSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

/**
 * The devices screen's switch: it writes the account's marker file, reads back what is saved, and
 * keeps "what runs now" apart from "what the next launch runs".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MarmotDevicesViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun theSwitchIsOffByDefaultAndWritesTheMarkerFile() =
        runBlocking {
            val dir = folder.newFolder()
            val vm = MarmotDevicesViewModel(devices = null, switch = MarmotDeviceGroupSwitch(dir.toOkioPath()), canSign = true)
            assertTrue(vm.switchAvailable)
            assertFalse(vm.runtimeOn)
            assertFalse(vm.switchOn.value)

            vm.setSwitch(true)
            withTimeout(5_000) { vm.switchOn.first { it } }
            assertTrue(File(dir, MarmotDeviceGroupSwitch.FILE_NAME).exists())
            // Saved for the next launch; this one still runs without device groups.
            assertFalse(vm.runtimeOn)

            vm.setSwitch(false)
            withTimeout(5_000) { vm.switchOn.first { !it } }
            assertFalse(File(dir, MarmotDeviceGroupSwitch.FILE_NAME).exists())
        }

    @Test
    fun aNewAccountStartsWithDeviceGroupsOnAndCanTurnThemOff() =
        runBlocking {
            val dir = File(folder.root, "accounts/new")
            assertTrue(MarmotDeviceGroupSwitch(dir.toOkioPath(), newAccount = true).isEnabled())
            val vm = MarmotDevicesViewModel(devices = null, switch = MarmotDeviceGroupSwitch(dir.toOkioPath()), canSign = true)
            assertTrue(vm.switchOn.value)
            vm.setSwitch(false)
            withTimeout(5_000) { vm.switchOn.first { !it } }
            // The next launch reads the account as one it already held: the opt-out stands.
            assertFalse(MarmotDeviceGroupSwitch(dir.toOkioPath()).isEnabled())
            // An account an earlier version held keeps it off.
            assertFalse(MarmotDeviceGroupSwitch(folder.newFolder().toOkioPath()).isEnabled())
        }

    @Test
    fun aSwitchThatCannotBeWrittenReportsAFailureAndKeepsWhatIsSaved() =
        runBlocking {
            val broken =
                object : DeviceGroupSwitch {
                    override fun isEnabled() = false

                    override fun setEnabled(enabled: Boolean): Unit = throw IllegalStateException("read-only disk")
                }
            val vm = MarmotDevicesViewModel(devices = null, switch = broken, canSign = true)
            vm.setSwitch(true)
            assertEquals(DeviceActionFailure.SWITCH, withTimeout(5_000) { vm.failure.first { it != null } })
            assertFalse(vm.switchOn.value)
            vm.clearFailure()
            assertEquals(null, vm.failure.value)
        }

    @Test
    fun withoutASwitchTheScreenShowsWhatRuns() {
        val vm = MarmotDevicesViewModel(devices = null, switch = null, canSign = false)
        assertFalse(vm.switchAvailable)
        assertFalse(vm.switchOn.value)
        assertEquals(false, vm.canSign)
    }

    @Test
    fun aNewDeviceLinkScreenWithoutDeviceGroupsStaysAtStarting() {
        val attention = DeviceGroupAttentionState()
        val vm = MarmotDeviceLinkViewModel(devices = null, role = DeviceLinkRole.NEW_DEVICE, initialSessionId = null, attention = attention)
        assertEquals(DeviceLinkScreenState.Starting, vm.state.value)
        // While it is open, incoming requests are not prompted over it.
        assertEquals(1, attention.openLinkScreens.value)
    }
}
