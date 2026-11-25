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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
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
import com.vitorpamplona.amethyst.commons.resources.android_device
import com.vitorpamplona.amethyst.commons.resources.android_device_agent_not_allowed
import com.vitorpamplona.amethyst.commons.resources.android_device_allow_agent
import com.vitorpamplona.amethyst.commons.resources.android_device_allow_agent_desc
import com.vitorpamplona.amethyst.commons.resources.android_device_back
import com.vitorpamplona.amethyst.commons.resources.android_device_booting
import com.vitorpamplona.amethyst.commons.resources.android_device_building
import com.vitorpamplona.amethyst.commons.resources.android_device_cold_boot
import com.vitorpamplona.amethyst.commons.resources.android_device_emulator
import com.vitorpamplona.amethyst.commons.resources.android_device_home
import com.vitorpamplona.amethyst.commons.resources.android_device_installing
import com.vitorpamplona.amethyst.commons.resources.android_device_live
import com.vitorpamplona.amethyst.commons.resources.android_device_logcat
import com.vitorpamplona.amethyst.commons.resources.android_device_logcat_empty
import com.vitorpamplona.amethyst.commons.resources.android_device_looking
import com.vitorpamplona.amethyst.commons.resources.android_device_no_adb
import com.vitorpamplona.amethyst.commons.resources.android_device_no_app
import com.vitorpamplona.amethyst.commons.resources.android_device_no_avds
import com.vitorpamplona.amethyst.commons.resources.android_device_no_emulator
import com.vitorpamplona.amethyst.commons.resources.android_device_no_permission
import com.vitorpamplona.amethyst.commons.resources.android_device_no_sdk
import com.vitorpamplona.amethyst.commons.resources.android_device_none
import com.vitorpamplona.amethyst.commons.resources.android_device_offline
import com.vitorpamplona.amethyst.commons.resources.android_device_open_full
import com.vitorpamplona.amethyst.commons.resources.android_device_open_log
import com.vitorpamplona.amethyst.commons.resources.android_device_phone
import com.vitorpamplona.amethyst.commons.resources.android_device_phone_hint
import com.vitorpamplona.amethyst.commons.resources.android_device_pick
import com.vitorpamplona.amethyst.commons.resources.android_device_polled
import com.vitorpamplona.amethyst.commons.resources.android_device_power
import com.vitorpamplona.amethyst.commons.resources.android_device_recents
import com.vitorpamplona.amethyst.commons.resources.android_device_rotate
import com.vitorpamplona.amethyst.commons.resources.android_device_run
import com.vitorpamplona.amethyst.commons.resources.android_device_run_done
import com.vitorpamplona.amethyst.commons.resources.android_device_run_failed
import com.vitorpamplona.amethyst.commons.resources.android_device_run_variant
import com.vitorpamplona.amethyst.commons.resources.android_device_screenshot_to_chat
import com.vitorpamplona.amethyst.commons.resources.android_device_start
import com.vitorpamplona.amethyst.commons.resources.android_device_start_failed
import com.vitorpamplona.amethyst.commons.resources.android_device_starting
import com.vitorpamplona.amethyst.commons.resources.android_device_stop
import com.vitorpamplona.amethyst.commons.resources.android_device_type_hint
import com.vitorpamplona.amethyst.commons.resources.android_device_unauthorized
import com.vitorpamplona.amethyst.commons.resources.android_device_virtual_devices
import com.vitorpamplona.amethyst.commons.resources.android_device_volume_down
import com.vitorpamplona.amethyst.commons.resources.android_device_volume_up
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.components.RoundedDropdownMenu
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.androiddevice.adb.DeviceKey
import com.vitorpamplona.androiddevice.avd.AvdInfo
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/** How wide the device panel starts, and how far it can be dragged. */
val DevicePanelWidth = 380.dp
val DevicePanelWidthRange = 280f..720f

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
    onAttachScreenshot: ((File) -> Unit)? = null,
    onOpenFull: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
) {
    val viewModel =
        rememberViewModel(key = "DevicePanel-$chatKey-${folder?.path}") {
            DevicePanelViewModel(AndroidDevices.shared, AndroidDevices.apps, chatKey, folder)
        }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxHeight().padding(top = 4.dp)) {
        Header(
            state = state,
            selected = selected,
            viewModel = viewModel,
            onOpenFull = onOpenFull,
            onClose = onClose,
        )
        val device = selected
        when {
            !state.loaded -> Centered(stringRes(Res.string.android_device_looking), progress = true)
            !state.adbFound && state.sdkDir == null -> Centered(stringRes(Res.string.android_device_no_sdk))
            !state.adbFound -> Centered(stringRes(Res.string.android_device_no_adb, state.sdkDir?.path.orEmpty()))
            device == null -> NoDevice(state, onStart = { viewModel.start(it.name) })
            device.status == DeviceStatus.STARTING -> Centered(stringRes(Res.string.android_device_starting, device.name), progress = true)
            device.status == DeviceStatus.BOOTING -> Centered(stringRes(Res.string.android_device_booting, device.name), progress = true)
            device.status == DeviceStatus.UNAUTHORIZED -> Centered(stringRes(Res.string.android_device_unauthorized, device.name))
            device.status == DeviceStatus.NO_PERMISSION -> Centered(stringRes(Res.string.android_device_no_permission, device.name))
            device.status == DeviceStatus.OFFLINE -> Centered(stringRes(Res.string.android_device_offline, device.name))
            else -> {
                val connection by produceState<DeviceConnection?>(null, device.serial) {
                    value =
                        try {
                            viewModel.connection(device.serial)
                        } catch (_: IOException) {
                            null
                        }
                }
                if (device.kind == DeviceKind.PHYSICAL) AgentLeave(device) { viewModel.allowAgent(device.serial, it) }
                connection?.let { c ->
                    LiveDeviceScreen(
                        connection = c,
                        activity = viewModel.activity,
                        agentName = agentName,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                } ?: Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                Text(
                    stringRes(if (device.liveStream) Res.string.android_device_live else Res.string.android_device_polled) + " · " + stringRes(Res.string.android_device_type_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.grayText,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                KeysBar(
                    onKey = viewModel::press,
                    onRotate = viewModel::rotate,
                    onScreenshot =
                        onAttachScreenshot?.let { attach ->
                            { scope.launch { viewModel.screenshotFile()?.let(attach) } }
                        },
                )
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) }
        // An AVD started from the picker while another device shows: why it didn't start goes here
        // (the device list shows it beside each AVD when nothing runs).
        if (selected != null) {
            state.startFailures.forEach { (avd, why) ->
                val name = state.avds.firstOrNull { it.name == avd }?.displayName ?: avd
                Text(
                    stringRes(Res.string.android_device_start_failed, name, why),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
        }
        RunStatus(viewModel)
        Logcat(viewModel, enabled = selected?.isOnline == true)
    }
}

@Composable
private fun Header(
    state: DevicesState,
    selected: DeviceInfo?,
    viewModel: DevicePanelViewModel,
    onOpenFull: (() -> Unit)?,
    onClose: (() -> Unit)?,
) {
    var picking by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val targets by viewModel.targets.collectAsStateWithLifecycle()
    val run by viewModel.runState.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            TextButton(onClick = { picking = true }) {
                Icon(MaterialSymbols.PhoneAndroid, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(selected?.name ?: stringRes(Res.string.android_device), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Icon(MaterialSymbols.KeyboardArrowDown, contentDescription = stringRes(Res.string.android_device_pick), modifier = Modifier.size(18.dp))
            }
            RoundedDropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                state.devices.forEach { d ->
                    PickRow(
                        title = d.name,
                        subtitle = "${stringRes(if (d.kind == DeviceKind.EMULATOR) Res.string.android_device_emulator else Res.string.android_device_phone)} · ${d.serial}",
                        selected = d.serial == selected?.serial,
                        onClick = {
                            picking = false
                            viewModel.select(d.serial)
                        },
                    )
                }
                val stopped = state.avds.filter { avd -> state.devices.none { it.avdName == avd.name } }
                if (stopped.isNotEmpty()) {
                    Text(stringRes(Res.string.android_device_virtual_devices), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.grayText, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    stopped.forEach { avd ->
                        PickRow(title = avd.displayName, subtitle = avdLine(avd), action = stringRes(Res.string.android_device_start)) {
                            picking = false
                            viewModel.start(avd.name)
                        }
                    }
                }
            }
        }
        // Run: the chat's Android app, built and opened on the device shown.
        if (selected?.isOnline == true) {
            val target = targets.firstOrNull()
            val variant = target?.variants?.firstOrNull()
            HoverTooltip(if (target != null && variant != null) stringRes(Res.string.android_device_run_variant, target.label, variant) else stringRes(Res.string.android_device_no_app)) {
                IconButton(onClick = { if (target != null && variant != null) viewModel.run(target, variant) }, enabled = target != null && !run.isRunning, modifier = Modifier.size(34.dp)) {
                    Icon(MaterialSymbols.PlayArrow, contentDescription = stringRes(Res.string.android_device_run), tint = if (target != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.grayText)
                }
            }
        }
        if (selected != null || targets.size > 1) {
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(34.dp)) {
                    Icon(MaterialSymbols.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(20.dp))
                }
                RoundedDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    // Every variant of every app here, when there is more than the Run button's one.
                    targets.forEach { target ->
                        target.variants.forEach { variant ->
                            PickRow(title = stringRes(Res.string.android_device_run_variant, target.label, variant), enabled = selected?.isOnline == true && !run.isRunning) {
                                menu = false
                                viewModel.run(target, variant)
                            }
                        }
                    }
                    if (selected?.kind == DeviceKind.EMULATOR) {
                        selected.avdName?.let { avd ->
                            PickRow(title = stringRes(Res.string.android_device_cold_boot)) {
                                menu = false
                                viewModel.stop(selected.serial)
                                viewModel.start(avd, coldBoot = true)
                            }
                        }
                        PickRow(title = stringRes(Res.string.android_device_stop)) {
                            menu = false
                            viewModel.stop(selected.serial)
                        }
                    }
                }
            }
        }
        onOpenFull?.let {
            HoverTooltip(stringRes(Res.string.android_device_open_full)) {
                IconButton(onClick = it, modifier = Modifier.size(34.dp)) {
                    Icon(MaterialSymbols.AutoMirrored.OpenInNew, contentDescription = stringRes(Res.string.android_device_open_full), tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(18.dp))
                }
            }
        }
        onClose?.let {
            IconButton(onClick = it, modifier = Modifier.size(34.dp)) {
                Icon(MaterialSymbols.Close, contentDescription = stringRes(Res.string.close), tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun avdLine(avd: AvdInfo): String = listOfNotNull(avd.apiLevel?.let { "API $it" }, avd.abi, avd.name).joinToString(" · ")

@Composable
private fun PickRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    action: String? = null,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        action?.let {
            Spacer(Modifier.width(12.dp))
            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun Centered(
    text: String,
    progress: Boolean = false,
) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (progress) CircularProgressIndicator(Modifier.size(28.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.grayText)
    }
}

/** No device picked or online: the AVDs to start, and how to bring a phone. */
@Composable
private fun NoDevice(
    state: DevicesState,
    onStart: (AvdInfo) -> Unit,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringRes(Res.string.android_device_none), style = MaterialTheme.typography.titleSmall)
        when {
            !state.emulatorFound -> Text(stringRes(Res.string.android_device_no_emulator), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText)
            state.avds.isEmpty() -> Text(stringRes(Res.string.android_device_no_avds), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText)
            else -> {
                Text(stringRes(Res.string.android_device_virtual_devices), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.grayText)
                state.avds.forEach { avd ->
                    Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(MaterialSymbols.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(avd.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(avdLine(avd), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                state.startFailures[avd.name]?.let { Text(stringRes(Res.string.android_device_start_failed, avd.displayName, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                            }
                            FilledTonalButton(onClick = { onStart(avd) }) { Text(stringRes(Res.string.android_device_start)) }
                        }
                    }
                }
            }
        }
        Text(stringRes(Res.string.android_device_phone_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText)
        state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

/** A phone: whether agents may use it, the user's call. */
@Composable
private fun AgentLeave(
    device: DeviceInfo,
    onChange: (Boolean) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (device.agentAllowed) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringRes(Res.string.android_device_allow_agent), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringRes(if (device.agentAllowed) Res.string.android_device_allow_agent_desc else Res.string.android_device_agent_not_allowed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                )
            }
            Switch(checked = device.agentAllowed, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun KeysBar(
    onKey: (DeviceKey) -> Unit,
    onRotate: () -> Unit,
    onScreenshot: (() -> Unit)?,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        KeyButton(MaterialSymbols.AutoMirrored.ArrowBack, stringRes(Res.string.android_device_back)) { onKey(DeviceKey.BACK) }
        KeyButton(MaterialSymbols.RadioButtonUnchecked, stringRes(Res.string.android_device_home)) { onKey(DeviceKey.HOME) }
        KeyButton(MaterialSymbols.CropSquare, stringRes(Res.string.android_device_recents)) { onKey(DeviceKey.RECENTS) }
        KeyButton(MaterialSymbols.ScreenRotation, stringRes(Res.string.android_device_rotate), onRotate)
        KeyButton(MaterialSymbols.AutoMirrored.VolumeDown, stringRes(Res.string.android_device_volume_down)) { onKey(DeviceKey.VOLUME_DOWN) }
        KeyButton(MaterialSymbols.AutoMirrored.VolumeUp, stringRes(Res.string.android_device_volume_up)) { onKey(DeviceKey.VOLUME_UP) }
        KeyButton(MaterialSymbols.PowerSettingsNew, stringRes(Res.string.android_device_power)) { onKey(DeviceKey.POWER) }
        onScreenshot?.let { KeyButton(MaterialSymbols.CameraAlt, stringRes(Res.string.android_device_screenshot_to_chat), it) }
    }
}

@Composable
private fun KeyButton(
    symbol: MaterialSymbol,
    label: String,
    onClick: () -> Unit,
) {
    HoverTooltip(label) {
        IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
            Icon(symbol, contentDescription = label, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun RunStatus(viewModel: DevicePanelViewModel) {
    val run by viewModel.runState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    when (val r = run) {
        is AppRunState.Building -> {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
            StatusLine(stringRes(Res.string.android_device_building, r.target.label, r.variant))
        }
        is AppRunState.Installing -> {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
            StatusLine(stringRes(Res.string.android_device_installing, r.packageName))
        }
        is AppRunState.Done -> StatusLine(stringRes(Res.string.android_device_run_done, r.packageName))
        is AppRunState.Failed ->
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(stringRes(Res.string.android_device_run_failed, r.message), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                r.log?.let { log -> TextButton(onClick = { uriHandler.openUri(log.toURI().toString()) }) { Text(stringRes(Res.string.android_device_open_log)) } }
            }
        AppRunState.Idle -> Unit
    }
}

@Composable
private fun StatusLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
}

@Composable
private fun Logcat(
    viewModel: DevicePanelViewModel,
    enabled: Boolean,
) {
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(open, enabled) { viewModel.showLogcat(open && enabled) }
    HorizontalDivider(Modifier.padding(top = 4.dp))
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { open = !open }, enabled = enabled) {
            Icon(MaterialSymbols.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringRes(Res.string.android_device_logcat))
            Icon(if (open) MaterialSymbols.KeyboardArrowDown else MaterialSymbols.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
    if (open && enabled) {
        val lines by viewModel.logcat.collectAsStateWithLifecycle()
        val list = rememberLazyListState()
        LaunchedEffect(lines.size) { if (lines.isNotEmpty()) list.scrollToItem(lines.size - 1) }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 220.dp)) {
            if (lines.isEmpty()) {
                Text(stringRes(Res.string.android_device_logcat_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText, modifier = Modifier.padding(12.dp))
            } else {
                SelectionContainer {
                    LazyColumn(state = list, modifier = Modifier.padding(horizontal = 8.dp)) {
                        items(lines) { line ->
                            Text(
                                line,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                lineHeight = 13.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                color = logColor(line),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** threadtime's level letter is the fifth field: `11-25 04:28:01.123  1234  1260 E Tag: …`. */
@Composable
private fun logColor(line: String) =
    when (line.split(Regex("\\s+")).getOrNull(4)) {
        "E", "F" -> MaterialTheme.colorScheme.error
        "W" -> MaterialTheme.colorScheme.tertiary
        "D", "V" -> MaterialTheme.colorScheme.grayText
        else -> MaterialTheme.colorScheme.onSurface
    }
