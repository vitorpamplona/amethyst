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
package com.vitorpamplona.amethyst.commons.androiddevices.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.androiddevices.AndroidDevices
import com.vitorpamplona.amethyst.commons.androiddevices.AppRunState
import com.vitorpamplona.amethyst.commons.androiddevices.DeviceConnection
import com.vitorpamplona.amethyst.commons.androiddevices.DeviceInfo
import com.vitorpamplona.amethyst.commons.androiddevices.DeviceKind
import com.vitorpamplona.amethyst.commons.androiddevices.DevicePanelViewModel
import com.vitorpamplona.amethyst.commons.androiddevices.DeviceStatus
import com.vitorpamplona.amethyst.commons.androiddevices.DevicesState
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.android_device_agent_not_allowed
import com.vitorpamplona.amethyst.commons.resources.android_device_allow_agent
import com.vitorpamplona.amethyst.commons.resources.android_device_allow_agent_desc
import com.vitorpamplona.amethyst.commons.resources.android_device_anr
import com.vitorpamplona.amethyst.commons.resources.android_device_back
import com.vitorpamplona.amethyst.commons.resources.android_device_booting
import com.vitorpamplona.amethyst.commons.resources.android_device_building
import com.vitorpamplona.amethyst.commons.resources.android_device_cold_boot
import com.vitorpamplona.amethyst.commons.resources.android_device_copy_hint
import com.vitorpamplona.amethyst.commons.resources.android_device_crashed
import com.vitorpamplona.amethyst.commons.resources.android_device_crashed_native
import com.vitorpamplona.amethyst.commons.resources.android_device_emulator
import com.vitorpamplona.amethyst.commons.resources.android_device_home
import com.vitorpamplona.amethyst.commons.resources.android_device_installing
import com.vitorpamplona.amethyst.commons.resources.android_device_live
import com.vitorpamplona.amethyst.commons.resources.android_device_logcat
import com.vitorpamplona.amethyst.commons.resources.android_device_logcat_app_only
import com.vitorpamplona.amethyst.commons.resources.android_device_logcat_empty
import com.vitorpamplona.amethyst.commons.resources.android_device_looking
import com.vitorpamplona.amethyst.commons.resources.android_device_no_adb
import com.vitorpamplona.amethyst.commons.resources.android_device_no_avds
import com.vitorpamplona.amethyst.commons.resources.android_device_no_emulator
import com.vitorpamplona.amethyst.commons.resources.android_device_no_permission
import com.vitorpamplona.amethyst.commons.resources.android_device_no_sdk
import com.vitorpamplona.amethyst.commons.resources.android_device_none
import com.vitorpamplona.amethyst.commons.resources.android_device_none_hint
import com.vitorpamplona.amethyst.commons.resources.android_device_offline
import com.vitorpamplona.amethyst.commons.resources.android_device_open_full
import com.vitorpamplona.amethyst.commons.resources.android_device_open_log
import com.vitorpamplona.amethyst.commons.resources.android_device_other_copy
import com.vitorpamplona.amethyst.commons.resources.android_device_own_copy
import com.vitorpamplona.amethyst.commons.resources.android_device_panel_toggle
import com.vitorpamplona.amethyst.commons.resources.android_device_phone
import com.vitorpamplona.amethyst.commons.resources.android_device_phone_hint
import com.vitorpamplona.amethyst.commons.resources.android_device_pick
import com.vitorpamplona.amethyst.commons.resources.android_device_polled
import com.vitorpamplona.amethyst.commons.resources.android_device_power
import com.vitorpamplona.amethyst.commons.resources.android_device_recents
import com.vitorpamplona.amethyst.commons.resources.android_device_reset
import com.vitorpamplona.amethyst.commons.resources.android_device_rotate
import com.vitorpamplona.amethyst.commons.resources.android_device_run
import com.vitorpamplona.amethyst.commons.resources.android_device_run_done
import com.vitorpamplona.amethyst.commons.resources.android_device_run_failed
import com.vitorpamplona.amethyst.commons.resources.android_device_run_variant
import com.vitorpamplona.amethyst.commons.resources.android_device_screenshot_to_chat
import com.vitorpamplona.amethyst.commons.resources.android_device_start
import com.vitorpamplona.amethyst.commons.resources.android_device_start_copy
import com.vitorpamplona.amethyst.commons.resources.android_device_start_failed
import com.vitorpamplona.amethyst.commons.resources.android_device_starting
import com.vitorpamplona.amethyst.commons.resources.android_device_status_live
import com.vitorpamplona.amethyst.commons.resources.android_device_status_polled
import com.vitorpamplona.amethyst.commons.resources.android_device_stop
import com.vitorpamplona.amethyst.commons.resources.android_device_unauthorized
import com.vitorpamplona.amethyst.commons.resources.android_device_virtual_devices
import com.vitorpamplona.amethyst.commons.resources.android_device_volume_down
import com.vitorpamplona.amethyst.commons.resources.android_device_volume_up
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.resources.copy
import com.vitorpamplona.amethyst.commons.resources.dismiss
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.components.RoundedDropdownMenu
import com.vitorpamplona.amethyst.commons.ui.components.RoundedMenuItemModifier
import com.vitorpamplona.amethyst.commons.ui.components.RoundedMenuSection
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.androiddevice.adb.DeviceCrash
import com.vitorpamplona.androiddevice.adb.DeviceKey
import com.vitorpamplona.androiddevice.avd.AvdInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/** How wide the device panel's dock column starts. */
val DevicePanelWidth = 380.dp

private val CardShape = RoundedCornerShape(16.dp)

private const val CONNECT_RETRY_MS = 3_000L
private const val CONNECT_RETRIES = 10

/** Under this, the keys pill drops its dividers and shrinks its buttons. */
private val CompactKeysWidth = 340.dp

/**
 * An Android device beside a coding chat, the way Android Studio's Running Devices window sits
 * beside the editor: pick or start a device, see and use its screen, press its keys, run the
 * chat's app on it and read its log. The chat's agent drives the same device with its device tools,
 * and its touches show here.
 *
 * [chatKey] keeps the device picked per chat; [folder] is where the Run button finds the app.
 * [onAttachScreenshot] puts a screenshot into the chat's next message.
 */
@Composable
fun DevicePanel(
    chatKey: String?,
    folder: File?,
    agentName: String,
    modifier: Modifier = Modifier,
    /** Whose device it is: the chat's workstream, else the chat. Null on the Devices screen. */
    owner: String? = chatKey,
    ownerLabel: String? = null,
    onAttachScreenshot: ((File) -> Unit)? = null,
    onOpenFull: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
) {
    val viewModel =
        rememberViewModel(key = "DevicePanel-$chatKey-$owner-${folder?.path}") {
            DevicePanelViewModel(AndroidDevices.shared, AndroidDevices.apps, chatKey, folder, owner, ownerLabel)
        }
    // The ViewModel outlives the panel (it is the chat screen's): it polls adb only while shown.
    DisposableEffect(viewModel) {
        val hide = viewModel.shown()
        onDispose { hide() }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val device = selected
    val online = device?.status == DeviceStatus.ONLINE
    val connection by produceState<DeviceConnection?>(null, device?.serial, online) {
        if (device == null || !online) {
            value = null
            return@produceState
        }
        // An emulator whose control port should answer but isn't yet (still coming up, or Android
        // Studio's not yet taking this app's key) is asked again for a while, so the live stream
        // takes over from adb screenshots once it can.
        var tries = 0
        do {
            value =
                try {
                    viewModel.connection(device.serial)
                } catch (_: IOException) {
                    null
                }
            if (value?.emulator != null || !(device.startedHere || device.liveStream)) break
            delay(CONNECT_RETRY_MS)
        } while (++tries < CONNECT_RETRIES)
    }

    Column(modifier.fillMaxHeight()) {
        Header(
            state = state,
            selected = device,
            live = connection?.emulator != null,
            viewModel = viewModel,
            owner = owner,
            onOpenFull = onOpenFull,
            onClose = onClose,
        )
        CrashBanner(viewModel)
        // Every state of a device draws in the same frame, so nothing jumps when it comes up.
        val stage = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
        when {
            !state.loaded -> EmptyState(MaterialSymbols.PhoneAndroid, stringRes(Res.string.android_device_looking), progress = true)
            !state.adbFound && state.sdkDir == null -> EmptyState(MaterialSymbols.PhoneAndroid, stringRes(Res.string.android_device_no_sdk))
            !state.adbFound -> EmptyState(MaterialSymbols.PhoneAndroid, stringRes(Res.string.android_device_no_adb, state.sdkDir?.path.orEmpty()))
            device == null -> NoDevice(state, startsCopies = viewModel.startsCopies, modifier = Modifier.weight(1f), onStart = { viewModel.start(it.name) })
            !online ->
                DeviceFrame(PHONE_RATIO, focused = false, modifier = stage) {
                    when (device.status) {
                        DeviceStatus.STARTING -> FrameMessage(stringRes(Res.string.android_device_starting, device.name), progress = true)
                        DeviceStatus.BOOTING -> FrameMessage(stringRes(Res.string.android_device_booting, device.name), progress = true)
                        DeviceStatus.UNAUTHORIZED -> FrameMessage(stringRes(Res.string.android_device_unauthorized, device.name), progress = false)
                        DeviceStatus.NO_PERMISSION -> FrameMessage(stringRes(Res.string.android_device_no_permission, device.name), progress = false)
                        else -> FrameMessage(stringRes(Res.string.android_device_offline, device.name), progress = false)
                    }
                }
            else -> {
                if (device.kind == DeviceKind.PHYSICAL) AgentLeave(device) { viewModel.allowAgent(device.serial, it) }
                val c = connection
                if (c != null) {
                    LiveDeviceScreen(connection = c, activity = viewModel.activity, agentName = agentName, modifier = stage)
                } else {
                    DeviceFrame(PHONE_RATIO, focused = false, modifier = stage) { FrameMessage("", progress = true) }
                }
                KeysBar(
                    onKey = viewModel::press,
                    onRotate = viewModel::rotate,
                    onScreenshot =
                        onAttachScreenshot?.let { attach ->
                            { scope.launch { viewModel.screenshotFile()?.let(attach) } }
                        },
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                )
            }
        }
        // A key that didn't reach the device; an AVD started from the picker while another device
        // shows (the device list shows its failure beside each AVD when nothing runs).
        val notes =
            listOfNotNull(error) +
                if (device != null) {
                    state.startFailures.map { (avd, why) -> stringRes(Res.string.android_device_start_failed, state.avds.firstOrNull { it.name == avd }?.displayName ?: avd, why) }
                } else {
                    emptyList()
                }
        notes.forEach { StatusCard(MaterialSymbols.Error, it, tint = MaterialTheme.colorScheme.error) }
        RunStatus(viewModel)
        if (online) Logcat(viewModel)
    }
}

@Composable
private fun Header(
    state: DevicesState,
    selected: DeviceInfo?,
    live: Boolean,
    viewModel: DevicePanelViewModel,
    owner: String?,
    onOpenFull: (() -> Unit)?,
    onClose: (() -> Unit)?,
) {
    var picking by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val targets by viewModel.targets.collectAsStateWithLifecycle()
    val run by viewModel.runState.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            // The device, and how it is: a button that opens the picker.
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { picking = true }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TonalIcon(if (selected?.kind == DeviceKind.PHYSICAL) MaterialSymbols.Usb else MaterialSymbols.PhoneAndroid, size = 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f, fill = false)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            selected?.name ?: stringRes(Res.string.android_device_panel_toggle),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Icon(MaterialSymbols.KeyboardArrowDown, contentDescription = stringRes(Res.string.android_device_pick), tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(18.dp))
                    }
                    // How the screen gets here, in full, on hover.
                    HoverTooltip(stringRes(if (live) Res.string.android_device_live else Res.string.android_device_polled)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(statusColor(selected?.status), size = 7.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                statusLine(selected, live, owner),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.grayText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            RoundedDropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                if (state.devices.isNotEmpty()) {
                    RoundedMenuSection {
                        state.devices.forEach { d ->
                            MenuItem(
                                title = d.name,
                                subtitle = deviceDetails(d, owner),
                                leading = { StatusDot(statusColor(d.status)) },
                                selected = d.serial == selected?.serial,
                            ) {
                                picking = false
                                viewModel.select(d.serial)
                            }
                        }
                    }
                }
                // A chat starts its own copy of any AVD it has none of; the Devices screen, AVDs not running at all.
                val startable =
                    if (viewModel.startsCopies) {
                        state.avds.filter { avd -> state.devices.none { it.avdName == avd.name && it.owner == owner } }
                    } else {
                        state.avds.filter { avd -> state.devices.none { it.avdName == avd.name } }
                    }
                if (startable.isNotEmpty()) {
                    MenuLabel(stringRes(Res.string.android_device_virtual_devices))
                    RoundedMenuSection {
                        startable.forEach { avd ->
                            MenuItem(
                                title = avd.displayName,
                                subtitle = avdLine(avd),
                                leading = { Icon(MaterialSymbols.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(18.dp)) },
                                action = stringRes(if (viewModel.startsCopies) Res.string.android_device_start_copy else Res.string.android_device_start),
                            ) {
                                picking = false
                                viewModel.start(avd.name)
                            }
                        }
                    }
                }
            }
        }
        // Run: the chat's Android app, built and opened on the device shown.
        if (selected?.isOnline == true && targets.isNotEmpty()) {
            val target = targets.first()
            val variant = target.variants.first()
            HoverTooltip(stringRes(Res.string.android_device_run_variant, target.label, variant)) {
                FilledIconButton(
                    onClick = { viewModel.run(target, variant) },
                    enabled = !run.isRunning,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                ) {
                    if (run.isRunning) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(MaterialSymbols.PlayArrow, contentDescription = stringRes(Res.string.android_device_run), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        if (selected != null || targets.size > 1 || onOpenFull != null) {
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) {
                    Icon(MaterialSymbols.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                RoundedDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    onOpenFull?.let { open ->
                        RoundedMenuSection {
                            MenuItem(stringRes(Res.string.android_device_open_full), leading = { MenuIcon(MaterialSymbols.AutoMirrored.OpenInNew) }) {
                                menu = false
                                open()
                            }
                        }
                    }
                    // Every variant of every app here.
                    if (targets.isNotEmpty()) {
                        RoundedMenuSection {
                            targets.forEach { target ->
                                target.variants.forEach { variant ->
                                    MenuItem(
                                        title = stringRes(Res.string.android_device_run_variant, target.label, variant),
                                        leading = { Icon(MaterialSymbols.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                                        enabled = selected?.isOnline == true && !run.isRunning,
                                    ) {
                                        menu = false
                                        viewModel.run(target, variant)
                                    }
                                }
                            }
                        }
                    }
                    if (selected?.kind == DeviceKind.EMULATOR) {
                        RoundedMenuSection {
                            // Over the emulator's gRPC endpoint, which lets only a token holder load a
                            // snapshot: an emulator this app started, not one Android Studio did.
                            if (live && selected.startedHere) {
                                MenuItem(stringRes(Res.string.android_device_reset), leading = { MenuIcon(MaterialSymbols.RestartAlt) }) {
                                    menu = false
                                    viewModel.reset()
                                }
                            }
                            selected.avdName?.let { avd ->
                                MenuItem(stringRes(Res.string.android_device_cold_boot), leading = { MenuIcon(MaterialSymbols.Refresh) }) {
                                    menu = false
                                    viewModel.coldBoot()
                                }
                            }
                        }
                        RoundedMenuSection {
                            MenuItem(
                                stringRes(Res.string.android_device_stop),
                                leading = { MenuIcon(MaterialSymbols.PowerSettingsNew, MaterialTheme.colorScheme.error) },
                                color = MaterialTheme.colorScheme.error,
                            ) {
                                menu = false
                                viewModel.stop(selected.serial)
                            }
                        }
                    }
                }
            }
        }
        onClose?.let {
            IconButton(onClick = it, modifier = Modifier.size(36.dp)) {
                Icon(MaterialSymbols.Close, contentDescription = stringRes(Res.string.close), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Under the device's name: live or polled, emulator or phone, whose copy. */
@Composable
private fun statusLine(
    device: DeviceInfo?,
    live: Boolean,
    owner: String?,
): String {
    if (device == null) return stringRes(Res.string.android_device_pick)
    val how =
        when (device.status) {
            DeviceStatus.ONLINE -> stringRes(if (live) Res.string.android_device_status_live else Res.string.android_device_status_polled)
            else -> null
        }
    return listOfNotNull(how, deviceDetails(device, owner)).joinToString(" · ")
}

@Composable
private fun deviceDetails(
    d: DeviceInfo,
    owner: String?,
): String =
    listOfNotNull(
        stringRes(if (d.kind == DeviceKind.EMULATOR) Res.string.android_device_emulator else Res.string.android_device_phone),
        when {
            d.owner == null -> null
            d.owner == owner -> stringRes(Res.string.android_device_own_copy)
            else -> stringRes(Res.string.android_device_other_copy, d.ownerLabel ?: d.owner)
        },
    ).joinToString(" · ")

@Composable
private fun avdLine(avd: AvdInfo): String = listOfNotNull(avd.apiLevel?.let { "API $it" }, avd.abi).joinToString(" · ")

@Composable
private fun MenuLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.grayText, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
}

@Composable
private fun MenuIcon(
    symbol: MaterialSymbol,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Icon(symbol, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
}

/** One row of the panel's menus, in the app's rounded menu style. */
@Composable
private fun MenuItem(
    title: String,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    action: String? = null,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(
        RoundedMenuItemModifier
            .widthIn(min = 240.dp, max = 340.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let {
            Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) { it() }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) color else MaterialTheme.colorScheme.grayText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        action?.let {
            Spacer(Modifier.width(12.dp))
            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(MaterialSymbols.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

/** An icon in a circle, a sentence under it, centered: what the panel shows before there's a device. */
@Composable
private fun ColumnScope.EmptyState(
    symbol: MaterialSymbol,
    text: String,
    progress: Boolean = false,
) {
    Column(
        Modifier.weight(1f).fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        if (progress) CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp) else TonalIcon(symbol, size = 72.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.grayText, textAlign = TextAlign.Center)
    }
}

/** No device picked or online: the AVDs to start, and how to bring a phone. */
@Composable
private fun NoDevice(
    state: DevicesState,
    startsCopies: Boolean,
    modifier: Modifier = Modifier,
    onStart: (AvdInfo) -> Unit,
) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TonalIcon(MaterialSymbols.PhoneAndroid, size = 72.dp)
        Text(stringRes(Res.string.android_device_none), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            stringRes(
                when {
                    !state.emulatorFound -> Res.string.android_device_no_emulator
                    state.avds.isEmpty() -> Res.string.android_device_no_avds
                    startsCopies -> Res.string.android_device_copy_hint
                    else -> Res.string.android_device_none_hint
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.grayText,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp),
        )
        if (state.emulatorFound && state.avds.isNotEmpty()) {
            Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(top = 6.dp)) {
                Column {
                    state.avds.forEachIndexed { i, avd ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Row(Modifier.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            TonalIcon(MaterialSymbols.PhoneAndroid, size = 36.dp, container = MaterialTheme.colorScheme.surfaceContainerHighest, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(avd.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(avdLine(avd), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                state.startFailures[avd.name]?.let {
                                    Text(stringRes(Res.string.android_device_start_failed, avd.displayName, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            FilledTonalButton(onClick = { onStart(avd) }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                                Text(stringRes(if (startsCopies) Res.string.android_device_start_copy else Res.string.android_device_start), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(MaterialSymbols.Usb, contentDescription = null, tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringRes(Res.string.android_device_phone_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText)
        }
        state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
    }
}

/** A phone: whether agents may use it, the user's call. */
@Composable
private fun AgentLeave(
    device: DeviceInfo,
    onChange: (Boolean) -> Unit,
) {
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TonalIcon(
                MaterialSymbols.SmartToy,
                size = 36.dp,
                container = if (device.agentAllowed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                tint = if (device.agentAllowed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringRes(Res.string.android_device_allow_agent), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringRes(if (device.agentAllowed) Res.string.android_device_allow_agent_desc else Res.string.android_device_agent_not_allowed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = device.agentAllowed, onCheckedChange = onChange)
        }
    }
}

/** The phone's buttons, in a floating pill: navigation, rotation, volume, power, and a picture for the chat. */
@Composable
private fun KeysBar(
    onKey: (DeviceKey) -> Unit,
    onRotate: () -> Unit,
    onScreenshot: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // A narrow panel gets smaller buttons and no dividers, so every button still fits on one line.
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val compact = maxWidth < CompactKeysWidth
        val button = if (compact) 30.dp else 36.dp
        ToolbarPill {
            ToolbarButton(MaterialSymbols.AutoMirrored.ArrowBack, stringRes(Res.string.android_device_back), size = button) { onKey(DeviceKey.BACK) }
            ToolbarButton(MaterialSymbols.RadioButtonUnchecked, stringRes(Res.string.android_device_home), size = button) { onKey(DeviceKey.HOME) }
            ToolbarButton(MaterialSymbols.CropSquare, stringRes(Res.string.android_device_recents), size = button) { onKey(DeviceKey.RECENTS) }
            if (!compact) ToolbarDivider()
            ToolbarButton(MaterialSymbols.ScreenRotation, stringRes(Res.string.android_device_rotate), size = button, onClick = onRotate)
            ToolbarButton(MaterialSymbols.AutoMirrored.VolumeDown, stringRes(Res.string.android_device_volume_down), size = button) { onKey(DeviceKey.VOLUME_DOWN) }
            ToolbarButton(MaterialSymbols.AutoMirrored.VolumeUp, stringRes(Res.string.android_device_volume_up), size = button) { onKey(DeviceKey.VOLUME_UP) }
            ToolbarButton(MaterialSymbols.PowerSettingsNew, stringRes(Res.string.android_device_power), size = button) { onKey(DeviceKey.POWER) }
            onScreenshot?.let {
                if (!compact) ToolbarDivider()
                ToolbarButton(MaterialSymbols.CameraAlt, stringRes(Res.string.android_device_screenshot_to_chat), size = button, tint = MaterialTheme.colorScheme.primary, onClick = it)
            }
        }
    }
}

/** A one-line note in a soft card: a run's progress or outcome, a failure. */
@Composable
private fun StatusCard(
    symbol: MaterialSymbol?,
    text: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    progress: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp).heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
            if (progress) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            } else if (symbol != null) {
                Icon(symbol, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            action?.invoke()
        }
    }
}

@Composable
private fun RunStatus(viewModel: DevicePanelViewModel) {
    val run by viewModel.runState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    when (val r = run) {
        is AppRunState.Building -> StatusCard(null, stringRes(Res.string.android_device_building, r.target.label, r.variant), progress = true)
        is AppRunState.Installing -> StatusCard(null, stringRes(Res.string.android_device_installing, r.packageName), progress = true)
        is AppRunState.Done -> StatusCard(MaterialSymbols.CheckCircle, stringRes(Res.string.android_device_run_done, r.packageName), tint = MaterialTheme.colorScheme.allGoodColor)
        is AppRunState.Failed ->
            StatusCard(
                MaterialSymbols.Error,
                stringRes(Res.string.android_device_run_failed, r.message),
                tint = MaterialTheme.colorScheme.error,
                action =
                    r.log?.let { log ->
                        { TextButton(onClick = { uriHandler.openUri(log.toURI().toString()) }) { Text(stringRes(Res.string.android_device_open_log)) } }
                    },
            )
        AppRunState.Idle -> Unit
    }
}

/** The app crashed or stopped responding on the device shown: what, and the top of the trace. */
@Composable
private fun CrashBanner(viewModel: DevicePanelViewModel) {
    val seen by viewModel.crash.collectAsStateWithLifecycle()
    AnimatedVisibility(seen != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        val crash = seen?.crash ?: return@AnimatedVisibility
        val clipboard = LocalClipboardManager.current
        val app = crash.packageName ?: "pid ${crash.pid}"
        Surface(
            shape = CardShape,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 2.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(MaterialSymbols.Error, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            stringRes(
                                when (crash.kind) {
                                    DeviceCrash.Kind.JAVA -> Res.string.android_device_crashed
                                    DeviceCrash.Kind.NATIVE -> Res.string.android_device_crashed_native
                                    DeviceCrash.Kind.ANR -> Res.string.android_device_anr
                                },
                                app,
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(crash.headline, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                if (crash.details.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.07f),
                        modifier = Modifier.fillMaxWidth().padding(start = 30.dp, end = 8.dp, top = 8.dp),
                    ) {
                        SelectionContainer {
                            Text(
                                crash.details.take(CRASH_LINES).joinToString("\n"),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                maxLines = CRASH_LINES,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                Row(Modifier.align(Alignment.End)) {
                    // The banner's own color, not the app's accent: purple on red fights.
                    val onBanner = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(colors = onBanner, onClick = { clipboard.setText(AnnotatedString(crash.summary(maxLines = Int.MAX_VALUE))) }) {
                        Icon(MaterialSymbols.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringRes(Res.string.copy))
                    }
                    TextButton(colors = onBanner, onClick = viewModel::dismissCrash) { Text(stringRes(Res.string.dismiss)) }
                }
            }
        }
    }
}

private const val CRASH_LINES = 5

/** The device's log, folded under the screen: the app's own lines, or everything at warning and up. */
@Composable
private fun Logcat(viewModel: DevicePanelViewModel) {
    var open by remember { mutableStateOf(false) }
    var appOnly by remember { mutableStateOf(true) }
    LaunchedEffect(open, appOnly) { viewModel.showLogcat(open, appOnly) }
    // Folded away with the panel, or the device going offline: logcat stops asking adb.
    DisposableEffect(viewModel) { onDispose { viewModel.showLogcat(false) } }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { open = !open }
            .padding(start = 16.dp, end = 10.dp)
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MaterialSymbols.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(stringRes(Res.string.android_device_logcat), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        if (open) {
            FilterChip(
                selected = appOnly,
                onClick = { appOnly = !appOnly },
                label = { Text(stringRes(Res.string.android_device_logcat_app_only), style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        Icon(if (open) MaterialSymbols.KeyboardArrowDown else MaterialSymbols.KeyboardArrowUp, contentDescription = null, tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(20.dp))
    }
    AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        val lines by viewModel.logcat.collectAsStateWithLifecycle()
        val list = rememberLazyListState()
        LaunchedEffect(lines.size) { if (lines.isNotEmpty()) list.scrollToItem(lines.size - 1) }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp).heightIn(min = 80.dp, max = 220.dp),
        ) {
            if (lines.isEmpty()) {
                Text(stringRes(Res.string.android_device_logcat_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText, modifier = Modifier.padding(12.dp))
            } else {
                SelectionContainer {
                    LazyColumn(state = list, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        items(lines) { line -> LogLine(line) }
                    }
                }
            }
        }
    }
}

private val LOG_FIELDS = Regex("\\s+")

/** One threadtime line: the time dimmed, the level as a colored letter, then tag and message. */
@Composable
private fun LogLine(line: String) {
    // Split once per line, not on every recomposition of the list.
    val parts = remember(line) { line.split(LOG_FIELDS, limit = 6) }
    val level = parts.getOrNull(4)?.takeIf { it.length == 1 }
    val color =
        when (level) {
            "E", "F" -> MaterialTheme.colorScheme.error
            "W" -> MaterialTheme.colorScheme.tertiary
            "D", "V" -> MaterialTheme.colorScheme.grayText
            else -> MaterialTheme.colorScheme.onSurface
        }
    Row(Modifier.padding(vertical = 1.dp)) {
        if (level != null) {
            Text(parts[1].substringBefore('.'), fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.grayText)
            Spacer(Modifier.width(6.dp))
            Text(level, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp, color = color)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            if (level != null) parts.getOrNull(5).orEmpty() else line,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            color = if (level == "E" || level == "F" || level == "W") color else MaterialTheme.colorScheme.onSurface,
        )
    }
}
