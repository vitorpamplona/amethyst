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
package com.vitorpamplona.amethyst.commons.devices.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.devices.Devices
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.dock.PanelId
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.dock_panel_devices
import com.vitorpamplona.amethyst.commons.ui.navigation.dock.DockPanelSpec
import com.vitorpamplona.amethyst.commons.ui.navigation.dock.PanelBadge

/**
 * The device (an Android emulator or phone, or an iOS simulator) as a dock panel, in the column the
 * dock keeps for it, the way Android Studio's Running Devices sits beside the editor. It follows the
 * coding chat in front ([DeviceChatFocus]): that chat's workstream device, its app for Run, its
 * composer for screenshots. Its title opens the full Devices screen, where the panel steps aside.
 * Folded, its icon flags a crash.
 */
val DevicesDockPanel =
    DockPanelSpec(
        id = PanelId.DEVICES,
        icon = MaterialSymbols.PhoneAndroid,
        title = Res.string.dock_panel_devices,
        defaultWidth = DevicePanelWidth.value,
        hidesOn = setOf(Route.Devices::class),
        onTitleClick = { nav -> nav.nav(Route.Devices(DeviceChatFocus.current?.chatKey)) },
        badge = { scope -> if (crashedSince(scope.foldedAt)) PanelBadge.Attention else PanelBadge.None },
        // Docked beside the coding chat it serves, though that screen takes the whole width.
        besideFullWidth = true,
        content = { _ ->
            val focus = DeviceChatFocus.current
            // A new chat in front is a new panel state: its own device pick and Run target.
            key(focus?.owner, focus?.folder) {
                DevicePanel(
                    chatKey = focus?.chatKey,
                    folder = focus?.folder,
                    agentName = focus?.agentName ?: "Claude",
                    owner = focus?.owner,
                    ownerLabel = focus?.ownerLabel,
                    onAttachScreenshot = focus?.attach,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
    )

/** Whether an app crashed on any device after the panel was folded ([foldedAt], epoch seconds). */
@Composable
private fun crashedSince(foldedAt: Long?): Boolean {
    val crashes by Devices.shared.crashes.collectAsStateWithLifecycle()
    val since = foldedAt ?: return false
    return crashes.any { it.at / 1000 >= since }
}
