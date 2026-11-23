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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.vitorpamplona.amethyst.commons.marmot.devices.DeviceGroupAttention
import com.vitorpamplona.amethyst.commons.marmot.devices.DeviceLinkRole
import com.vitorpamplona.amethyst.commons.marmot.devices.DeviceLinkScreenState
import com.vitorpamplona.amethyst.commons.model.marmot.devices.DeviceLinkEndReason
import com.vitorpamplona.amethyst.commons.model.marmot.devices.DeviceLinkPrompt
import com.vitorpamplona.amethyst.commons.model.marmot.devices.LinkedDevice
import com.vitorpamplona.amethyst.commons.model.marmot.devices.MarmotDevicesSnapshot
import com.vitorpamplona.amethyst.commons.model.marmot.devices.SasSymbol
import com.vitorpamplona.amethyst.commons.model.marmot.devices.SignIn
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.marmotDevices.MarmotDeviceLinkContent
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.marmotDevices.MarmotDevicesContent
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.marmotDevices.MarmotDevicesPrompts
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.marmotDevices.MarmotDevicesScreenModel
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import com.vitorpamplona.marmot.appComponents.DeviceStatusV1
import com.vitorpamplona.marmot.features.deviceGroup.MarmotDeviceGroupManager
import com.vitorpamplona.marmot.features.devicePairing.SasEmoji
import com.vitorpamplona.quartz.utils.TimeUtils
import org.jetbrains.skia.EncodedImageFormat
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the Marmot device-group screens and prompts headlessly from made-up states (the live
 * state needs two linked devices). Checks that each draws, not exact pixels. Set
 * MARMOT_DEVICES_RENDER_DIR to also write the PNGs for a human to look at.
 */
class MarmotDevicesRenderTest {
    private val now = TimeUtils.now()
    private val code = listOf(2, 17, 36, 41, 54, 61, 63).map { SasSymbol.of(SasEmoji.TABLE[it]) }

    private val roster =
        listOf(
            device("a", "Pixel 8", "Amethyst", "Android", isThis = true, lastActive = now - 30),
            device("b", "Laptop", "White Noise", "Linux", lastActive = now - 3 * 3600),
            device("c", "", "Amethyst", "macOS", status = DeviceStatusV1.INACTIVE, lastActive = now - 9 * 86400),
        )

    private fun device(
        slot: String,
        label: String,
        client: String,
        platform: String,
        isThis: Boolean = false,
        status: DeviceStatusV1 = DeviceStatusV1.ACTIVE,
        lastActive: Long?,
    ) = LinkedDevice(slot.repeat(64), label, client, platform, isThis, status, now - 20 * 86400, null, true, false, lastActive)

    private fun render(
        name: String,
        dark: Boolean = false,
        widthDp: Int = 420,
        heightDp: Int = 860,
        content: @Composable () -> Unit,
    ): BufferedImage {
        val density = 2f
        val scene =
            ImageComposeScene(width = (widthDp * density).toInt(), height = (heightDp * density).toInt(), density = Density(density)) {
                AmethystPreviewTheme(dark = dark) {
                    // A Surface, as the app's Scaffold is: it sets the text colour for the theme.
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
                }
            }
        return try {
            // Strings and fonts load on the first frames.
            var image = scene.render(0)
            repeat(5) { i ->
                Thread.sleep(150)
                image = scene.render((i + 1) * 100_000_000L)
            }
            val png = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
            System.getenv("MARMOT_DEVICES_RENDER_DIR")?.let { dir -> File(dir, "$name${if (dark) "-dark" else ""}.png").writeBytes(png) }
            ImageIO.read(ByteArrayInputStream(png))
        } finally {
            scene.close()
        }
    }

    private fun BufferedImage.distinctColours(): Int {
        val seen = mutableSetOf<Int>()
        for (x in 0 until width step 3) {
            for (y in 0 until height step 3) seen += getRGB(x, y)
        }
        return seen.size
    }

    private fun assertDraws(
        name: String,
        image: BufferedImage,
    ) = assertTrue(image.distinctColours() > 20, "$name drew ${image.distinctColours()} colours")

    private fun devices(
        model: MarmotDevicesScreenModel,
        name: String,
        dark: Boolean = false,
    ) = assertDraws(
        name,
        render(name, dark) {
            MarmotDevicesContent(model, onSwitch = {}, onLinkNewDevice = {}, onLinkThisDevice = {}, onRemove = {}, onRejectSignIn = {})
        },
    )

    private fun link(
        role: DeviceLinkRole,
        state: DeviceLinkScreenState,
        name: String,
        dark: Boolean = false,
    ) = assertDraws(
        name,
        render(name, dark) {
            MarmotDeviceLinkContent(role, state, onConfirm = {}, onReject = {}, onCancel = {}, onRetry = {}, onDone = {})
        },
    )

    @Test
    fun theDevicesScreenRendersOffPendingAndOn() {
        devices(MarmotDevicesScreenModel(true, switchOn = false, runtimeOn = false, canSign = true, MarmotDevicesSnapshot()), "devices-switch-off")
        devices(MarmotDevicesScreenModel(true, switchOn = true, runtimeOn = false, canSign = true, MarmotDevicesSnapshot()), "devices-switch-on-pending-restart")
        devices(
            MarmotDevicesScreenModel(true, switchOn = true, runtimeOn = true, canSign = true, MarmotDevicesSnapshot(ownSlot = "a".repeat(64))),
            "devices-on-not-linked",
        )
        val linked =
            MarmotDevicesSnapshot(
                ownSlot = "a".repeat(64),
                linked = true,
                devices = roster,
                remainingChats = 2,
                signIns =
                    listOf(
                        SignIn("d".repeat(64), now - 600, MarmotDeviceGroupManager.SignInClass.NEW_SIGN_IN),
                        SignIn("e".repeat(64), now - 40 * 86400, MarmotDeviceGroupManager.SignInClass.ALREADY_SIGNED_IN),
                    ),
            )
        devices(MarmotDevicesScreenModel(true, switchOn = true, runtimeOn = true, canSign = true, linked), "devices-on-linked")
        devices(MarmotDevicesScreenModel(true, switchOn = true, runtimeOn = true, canSign = true, linked), "devices-on-linked", dark = true)
        devices(
            MarmotDevicesScreenModel(true, switchOn = true, runtimeOn = true, canSign = true, MarmotDevicesSnapshot(ownSlot = "a".repeat(64), unlinked = true)),
            "devices-unlinked",
        )
    }

    @Test
    fun theLinkScreenRendersEveryStep() {
        link(DeviceLinkRole.NEW_DEVICE, DeviceLinkScreenState.WaitingForAnswer, "link-new-waiting")
        link(DeviceLinkRole.NEW_DEVICE, DeviceLinkScreenState.Comparing(code, true, null), "link-new-sas")
        link(DeviceLinkRole.NEW_DEVICE, DeviceLinkScreenState.Comparing(code, true, null), "link-new-sas", dark = true)
        link(DeviceLinkRole.EXISTING_DEVICE, DeviceLinkScreenState.WaitingForRequest, "link-existing-waiting")
        link(DeviceLinkRole.EXISTING_DEVICE, DeviceLinkScreenState.Comparing(code, false, "f".repeat(64)), "link-existing-sas")
        link(DeviceLinkRole.NEW_DEVICE, DeviceLinkScreenState.Joining, "link-new-joining")
        link(DeviceLinkRole.NEW_DEVICE, DeviceLinkScreenState.Linked(3), "link-new-linked")
        link(DeviceLinkRole.EXISTING_DEVICE, DeviceLinkScreenState.Ended(DeviceLinkEndReason.CODES_DIFFER), "link-existing-codes-differ")
        link(DeviceLinkRole.NEW_DEVICE, DeviceLinkScreenState.StartFailed, "link-new-start-failed")
    }

    @Test
    fun thePromptsRender() {
        fun prompts(
            attention: DeviceGroupAttention,
            name: String,
        ) = assertDraws(
            name,
            render(name) {
                MarmotDevicesPrompts(attention, onCompare = {}, onIgnore = {}, onSignInMine = {}, onSignInNotMe = {}, onUnlinkedSeen = {})
            },
        )
        prompts(DeviceGroupAttention(linkRequest = DeviceLinkPrompt("f".repeat(64), null, now, DeviceLinkPrompt.State.Preparing)), "prompt-link-request")
        prompts(DeviceGroupAttention(signIn = SignIn("d".repeat(64), now - 600, MarmotDeviceGroupManager.SignInClass.NEW_SIGN_IN)), "prompt-sign-in")
        prompts(DeviceGroupAttention(unlinked = true), "prompt-unlinked")
    }
}
