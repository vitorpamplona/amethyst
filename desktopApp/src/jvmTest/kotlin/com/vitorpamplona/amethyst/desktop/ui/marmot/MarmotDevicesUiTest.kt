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
package com.vitorpamplona.amethyst.desktop.ui.marmot

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.vitorpamplona.amethyst.commons.marmot.devices.DeviceLinkRole
import com.vitorpamplona.amethyst.commons.marmot.devices.DeviceLinkScreenState
import com.vitorpamplona.amethyst.commons.model.marmot.devices.LinkedDevice
import com.vitorpamplona.amethyst.commons.model.marmot.devices.MarmotDevicesSnapshot
import com.vitorpamplona.amethyst.commons.model.marmot.devices.SasSymbol
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.marmotDevices.MarmotDeviceLinkContent
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.marmotDevices.MarmotDevicesContent
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.marmotDevices.MarmotDevicesScreenModel
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import com.vitorpamplona.marmot.appComponents.DeviceStatusV1
import com.vitorpamplona.marmot.features.devicePairing.SasEmoji
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The device screens' buttons reach the right action, and a removal asks first. */
class MarmotDevicesUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val code = listOf(0, 1, 2, 3, 4, 5, 6).map { SasSymbol.of(SasEmoji.TABLE[it]) }

    @Test
    fun theExistingDeviceConfirmsWithTheLabelTyped() {
        var confirmed: String? = null
        var rejected = 0
        compose.setContent {
            AmethystPreviewTheme(dark = false) {
                MarmotDeviceLinkContent(
                    DeviceLinkRole.EXISTING_DEVICE,
                    DeviceLinkScreenState.Comparing(code, true, "f".repeat(64)),
                    onConfirm = { confirmed = it },
                    onReject = { rejected++ },
                    onCancel = {},
                    onRetry = {},
                    onDone = {},
                )
            }
        }
        // Every emoji carries its name, translated from the table's number.
        compose.onNodeWithContentDescription("Dog").assertExists()
        compose.onNodeWithContentDescription("Unicorn").assertExists()
        compose.onNodeWithText("New device").performTextReplacement("Work laptop")
        compose.onNodeWithText("They match").performClick()
        compose.waitForIdle()
        assertEquals("Work laptop", confirmed)

        compose.onNodeWithText("They don't match").performClick()
        compose.waitForIdle()
        assertEquals(1, rejected)
    }

    @Test
    fun aRemovalAsksBeforeItRuns() {
        var removed: LinkedDevice? = null
        val other = LinkedDevice("b".repeat(64), "Laptop", "White Noise", "Linux", false, DeviceStatusV1.ACTIVE, 1, null, true, false, null)
        val mine = other.copy(slot = "a".repeat(64), label = "Phone", isThisDevice = true)
        compose.setContent {
            AmethystPreviewTheme(dark = false) {
                MarmotDevicesContent(
                    MarmotDevicesScreenModel(true, true, true, true, MarmotDevicesSnapshot(ownSlot = mine.slot, linked = true, devices = listOf(mine, other))),
                    onSwitch = {},
                    onLinkNewDevice = {},
                    onLinkThisDevice = {},
                    onRemove = { removed = it },
                    onRejectSignIn = {},
                )
            }
        }
        compose.onNodeWithText("This device").assertExists()
        // Only the sibling has a remove button.
        compose.onNodeWithContentDescription("Remove").performClick()
        compose.waitForIdle()
        assertNull(removed)
        compose.onNodeWithText("Remove Laptop?").assertExists()
        compose.onNodeWithText("Remove").performClick()
        compose.waitForIdle()
        assertEquals(other, removed)
    }

    @Test
    fun whenOffOnlyTheSwitchIsOffered() {
        var switched: Boolean? = null
        compose.setContent {
            AmethystPreviewTheme(dark = false) {
                MarmotDevicesContent(
                    MarmotDevicesScreenModel(true, false, false, true, MarmotDevicesSnapshot()),
                    onSwitch = { switched = it },
                    onLinkNewDevice = {},
                    onLinkThisDevice = {},
                    onRemove = {},
                    onRejectSignIn = {},
                )
            }
        }
        compose.onNodeWithText("Link a new device").assertDoesNotExist()
        compose.onNodeWithText("On for accounts you add from this version on. A change applies the next time Amethyst starts.").performClick()
        compose.waitForIdle()
        assertEquals(true, switched)
    }
}
