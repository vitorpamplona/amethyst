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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.devices

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.devices.DeviceSession
import com.vitorpamplona.amethyst.commons.devices.DeviceStreamService
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.dock.PanelId
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.devices_panel_back
import com.vitorpamplona.amethyst.commons.resources.devices_panel_connecting
import com.vitorpamplona.amethyst.commons.resources.devices_panel_failed
import com.vitorpamplona.amethyst.commons.resources.devices_panel_home
import com.vitorpamplona.amethyst.commons.resources.devices_panel_no_avds
import com.vitorpamplona.amethyst.commons.resources.devices_panel_no_devices
import com.vitorpamplona.amethyst.commons.resources.devices_panel_no_sdk
import com.vitorpamplona.amethyst.commons.resources.devices_panel_overview
import com.vitorpamplona.amethyst.commons.resources.devices_panel_refresh
import com.vitorpamplona.amethyst.commons.resources.devices_panel_rotate
import com.vitorpamplona.amethyst.commons.resources.devices_panel_screen
import com.vitorpamplona.amethyst.commons.resources.devices_panel_start
import com.vitorpamplona.amethyst.commons.resources.devices_panel_starting
import com.vitorpamplona.amethyst.commons.resources.devices_panel_stop
import com.vitorpamplona.amethyst.commons.resources.devices_panel_volume_down
import com.vitorpamplona.amethyst.commons.resources.devices_panel_volume_up
import com.vitorpamplona.amethyst.commons.resources.dock_panel_devices
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.navigation.dock.DockPanelScope
import com.vitorpamplona.amethyst.commons.ui.navigation.dock.DockPanelSpec
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.emulator.KeyboardEvent
import com.vitorpamplona.emulator.RunningEmulator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.skia.Image as SkiaImage

/**
 * The Android emulators on this computer, as Android Studio's Running Devices: the screen of a
 * running emulator, live, taking clicks, scrolls and typing, with the device's buttons above it, one
 * tab per running emulator, and a list of virtual devices to start when none runs. The emulator
 * keeps no window of its own; the stream outlives the panel's moves (DeviceStreamService).
 */
val DevicesDockPanel =
    DockPanelSpec(
        id = PanelId.DEVICES,
        icon = MaterialSymbols.Smartphone,
        title = Res.string.dock_panel_devices,
        defaultWidth = 380f,
        actions = {
            val label = stringRes(Res.string.devices_panel_refresh)
            HoverTooltip(label) {
                IconButton(onClick = { DeviceStreamService.shared.rescan() }, modifier = Modifier.size(32.dp)) {
                    Icon(symbol = MaterialSymbols.Refresh, contentDescription = label, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        content = { scope -> DevicesPanel(scope) },
    )

@Composable
private fun DevicesPanel(
    @Suppress("UNUSED_PARAMETER") scope: DockPanelScope,
) {
    val service = DeviceStreamService.shared
    DisposableEffect(service) {
        val release = service.watch()
        onDispose { release() }
    }
    val emulators by service.emulators.collectAsState()
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }

    if (emulators.isEmpty()) {
        NoDevices(service)
        return
    }
    val emulator = emulators.firstOrNull { it.pid == selected } ?: emulators.first()
    Column(Modifier.fillMaxSize()) {
        if (emulators.size > 1) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp)) {
                emulators.forEach { device ->
                    DeviceTab(device, device.pid == emulator.pid) { selected = device.pid }
                }
            }
        }
        val session = remember(emulator.pid) { service.session(emulator) }
        DeviceButtons(session, onStop = { service.stop(emulator) })
        HorizontalDivider()
        DeviceScreen(session, Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun DeviceTab(
    device: RunningEmulator,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = device.name.replace('_', ' '),
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        modifier =
            Modifier
                .padding(end = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) colors.secondaryContainer else colors.surface)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun NoDevices(service: DeviceStreamService) {
    val avds by service.avds.collectAsState()
    val starting by service.starting.collectAsState()
    val errors by service.startErrors.collectAsState()
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            service.sdk == null -> Text(stringRes(Res.string.devices_panel_no_sdk), color = colors.onSurfaceVariant)
            avds.isEmpty() -> Text(stringRes(Res.string.devices_panel_no_avds), color = colors.onSurfaceVariant)
            else -> {
                Text(stringRes(Res.string.devices_panel_no_devices), color = colors.onSurfaceVariant)
                avds.forEach { avd ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(symbol = MaterialSymbols.Smartphone, contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(avd.replace('_', ' '), style = MaterialTheme.typography.bodyLarge)
                            errors[avd]?.let { Text(stringRes(Res.string.devices_panel_failed, avd, it), style = MaterialTheme.typography.bodySmall, color = colors.error) }
                        }
                        if (avd in starting) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringRes(Res.string.devices_panel_starting, ""), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                        } else {
                            OutlinedButton(onClick = { service.start(avd) }) { Text(stringRes(Res.string.devices_panel_start)) }
                        }
                    }
                }
            }
        }
        TextButton(onClick = { service.rescan() }) { Text(stringRes(Res.string.devices_panel_refresh)) }
    }
}

@Composable
private fun DeviceButtons(
    session: DeviceSession,
    onStop: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DeviceButton(MaterialSymbols.AutoMirrored.ArrowBack, Res.string.devices_panel_back) { session.key(KeyboardEvent.GO_BACK) }
        DeviceButton(MaterialSymbols.Circle, Res.string.devices_panel_home) { session.key(KeyboardEvent.GO_HOME) }
        DeviceButton(MaterialSymbols.CropSquare, Res.string.devices_panel_overview) { session.key(KeyboardEvent.APP_SWITCH) }
        Spacer(Modifier.weight(1f))
        DeviceButton(MaterialSymbols.VolumeDown, Res.string.devices_panel_volume_down) { session.key(VOLUME_DOWN) }
        DeviceButton(MaterialSymbols.AutoMirrored.VolumeUp, Res.string.devices_panel_volume_up) { session.key(VOLUME_UP) }
        DeviceButton(MaterialSymbols.RotateRight, Res.string.devices_panel_rotate) { session.rotate() }
        DeviceButton(MaterialSymbols.PowerSettingsNew, Res.string.devices_panel_stop, onStop)
    }
}

@Composable
private fun DeviceButton(
    symbol: MaterialSymbol,
    label: StringResource,
    onClick: () -> Unit,
) {
    val text = stringRes(label)
    HoverTooltip(text) {
        IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
            Icon(symbol = symbol, contentDescription = text, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The emulator's screen, fitted to the panel: the panel asks for frames at its own size, decodes
 * them off the main thread, and turns pointer, wheel and key events into the device's input. It
 * takes the keyboard focus when clicked, so typing goes to the device.
 */
@Composable
private fun DeviceScreen(
    session: DeviceSession,
    modifier: Modifier,
) {
    DisposableEffect(session) {
        val release = session.acquire()
        onDispose { release() }
    }
    var image by remember(session) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(session) {
        session.frame.filterNotNull().conflate().collect { frame ->
            val decoded =
                withContext(Dispatchers.Default) {
                    try {
                        SkiaImage.makeFromEncoded(frame.pixels).toComposeImageBitmap()
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                }
            if (decoded != null) image = decoded
        }
    }
    val display by session.displaySize.collectAsState()
    val error by session.error.collectAsState()
    val focus = remember { FocusRequester() }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val name = session.emulator.name.replace('_', ' ')

    BoxWithConstraints(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .onSizeChanged {
                box = it
                session.requestSize(it.width, it.height)
            },
        contentAlignment = Alignment.Center,
    ) {
        val shown = image
        if (shown == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(24.dp)) {
                val problem = error
                if (problem == null) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Text(stringRes(Res.string.devices_panel_connecting, name), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                } else {
                    Text(stringRes(Res.string.devices_panel_failed, name, problem), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
            }
        } else {
            Image(
                bitmap = shown,
                contentDescription = stringRes(Res.string.devices_panel_screen, name),
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .focusRequester(focus)
                        .focusable()
                        .onKeyEvent { event -> forwardKey(session, event) }
                        .pointerInput(session) {
                            awaitPointerEventScope {
                                var pressed = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: continue
                                    val point = toDevice(change.position, box, shown.width, shown.height, display) ?: continue
                                    when (event.type) {
                                        PointerEventType.Press -> {
                                            focus.requestFocus()
                                            pressed = true
                                            session.mouse(point.first, point.second, pressed = true)
                                            change.consume()
                                        }
                                        PointerEventType.Move -> if (pressed) session.mouse(point.first, point.second, pressed = true)
                                        PointerEventType.Release -> {
                                            if (pressed) session.mouse(point.first, point.second, pressed = false)
                                            pressed = false
                                        }
                                        PointerEventType.Scroll -> {
                                            val dy = change.scrollDelta.y
                                            if (dy != 0f) session.wheel(0, if (dy > 0) -1 else 1)
                                            change.consume()
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        },
            )
        }
    }
}

/**
 * [at], a point of the panel ([box] pixels) showing a [frameWidth] x [frameHeight] frame fitted
 * inside it, as device pixels on a [display] (width to height; swapped while the frame shows the
 * device turned); null outside the picture.
 */
internal fun toDevice(
    at: Offset,
    box: IntSize,
    frameWidth: Int,
    frameHeight: Int,
    display: Pair<Int, Int>?,
): Pair<Int, Int>? {
    if (box.width <= 0 || box.height <= 0 || frameWidth <= 0 || frameHeight <= 0) return null
    val scale = minOf(box.width.toFloat() / frameWidth, box.height.toFloat() / frameHeight)
    val drawnW = frameWidth * scale
    val drawnH = frameHeight * scale
    val left = (box.width - drawnW) / 2
    val top = (box.height - drawnH) / 2
    val u = (at.x - left) / drawnW
    val v = (at.y - top) / drawnH
    if (u < 0f || v < 0f || u > 1f || v > 1f) return null
    val (w, h) =
        display?.let { (dw, dh) ->
            // The frame shows the screen as turned; the display's size is given upright.
            if ((frameWidth > frameHeight) != (dw > dh)) dh to dw else dw to dh
        } ?: (frameWidth to frameHeight)
    return (u * (w - 1)).toInt() to (v * (h - 1)).toInt()
}

/** Sends a key the device understands; shortcuts with Ctrl or Cmd stay with the app. */
private fun forwardKey(
    session: DeviceSession,
    event: KeyEvent,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (event.isCtrlPressed || event.isMetaPressed) return false
    val named = NAMED_KEYS[event.key]
    if (named != null) {
        session.key(named)
        return true
    }
    val code = event.utf16CodePoint
    if (code >= ' '.code) {
        session.type(String(Character.toChars(code)))
        return true
    }
    return false
}

private const val VOLUME_UP = "AudioVolumeUp"
private const val VOLUME_DOWN = "AudioVolumeDown"

/** The W3C names the emulator takes for the keys that type no character. */
private val NAMED_KEYS =
    mapOf(
        Key.Enter to KeyboardEvent.ENTER,
        Key.NumPadEnter to KeyboardEvent.ENTER,
        Key.Backspace to KeyboardEvent.BACKSPACE,
        Key.Delete to "Delete",
        Key.Tab to "Tab",
        Key.Escape to KeyboardEvent.GO_BACK,
        Key.DirectionLeft to "ArrowLeft",
        Key.DirectionRight to "ArrowRight",
        Key.DirectionUp to "ArrowUp",
        Key.DirectionDown to "ArrowDown",
        Key.MoveHome to "Home",
        Key.MoveEnd to "End",
        Key.PageUp to "PageUp",
        Key.PageDown to "PageDown",
    )
