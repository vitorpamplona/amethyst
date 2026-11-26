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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.androiddevices.DeviceActivity
import com.vitorpamplona.amethyst.commons.androiddevices.DeviceConnection
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.android_device_agent_acted
import com.vitorpamplona.amethyst.commons.resources.android_device_agent_looked
import com.vitorpamplona.amethyst.commons.resources.android_device_did_key
import com.vitorpamplona.amethyst.commons.resources.android_device_did_long_press
import com.vitorpamplona.amethyst.commons.resources.android_device_did_open
import com.vitorpamplona.amethyst.commons.resources.android_device_did_swipe
import com.vitorpamplona.amethyst.commons.resources.android_device_did_tap
import com.vitorpamplona.amethyst.commons.resources.android_device_did_type
import com.vitorpamplona.amethyst.commons.resources.android_device_reconnect
import com.vitorpamplona.amethyst.commons.resources.android_device_screen_failed
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.androiddevice.adb.DeviceKey
import com.vitorpamplona.androiddevice.device.DeviceScreen
import com.vitorpamplona.androiddevice.device.PointerAction
import com.vitorpamplona.androiddevice.device.ScreenFrame
import com.vitorpamplona.androiddevice.emulator.PixelFormat
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import org.jetbrains.skia.Image as SkiaImage

/** A frame ready to draw, with what the pointer needs to know about it. */
private class DrawnFrame(
    val frame: ScreenFrame,
    val image: ImageBitmap,
)

private fun ScreenFrame.toBitmap(): ImageBitmap =
    when (format) {
        PixelFormat.PNG -> SkiaImage.makeFromEncoded(pixels).toComposeImageBitmap()
        PixelFormat.RGBA8888 -> SkiaImage.makeRaster(ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.OPAQUE), pixels, width * 4).toComposeImageBitmap()
        PixelFormat.RGB888 -> {
            val rgba = ByteArray(width * height * 4)
            var s = 0
            var d = 0
            while (s + 2 < pixels.size && d + 3 < rgba.size) {
                rgba[d] = pixels[s]
                rgba[d + 1] = pixels[s + 1]
                rgba[d + 2] = pixels[s + 2]
                rgba[d + 3] = -1
                s += 3
                d += 4
            }
            SkiaImage.makeRaster(ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.OPAQUE), rgba, width * 4).toComposeImageBitmap()
        }
    }

/**
 * The device's screen, live, as Android Studio's Running Devices shows it: the mouse is a finger
 * (press, drag, release; the wheel scrolls), and once clicked, typing goes to the device. Where an
 * agent touches flashes on top ([activity]), so the user sees what it does.
 */
@Composable
fun LiveDeviceScreen(
    connection: DeviceConnection,
    activity: Flow<DeviceActivity>,
    agentName: String,
    modifier: Modifier = Modifier,
) {
    var screen by remember(connection) { mutableStateOf<DeviceScreen?>(null) }
    // The device's last frame, drawn at once when the screen comes back (a tab switch, the panel
    // reopened) while the stream starts again, instead of a spinner.
    var drawn by remember(connection) { mutableStateOf(lastFrames[connection.serial]) }
    var failure by remember(connection) { mutableStateOf<String?>(null) }
    var attempt by remember(connection) { mutableStateOf(0) }
    val viewSize = remember(connection) { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(connection, attempt) {
        failure = null
        screen =
            try {
                connection.screen()
            } catch (e: IOException) {
                failure = e.message ?: "adb"
                null
            }
    }

    // A new stream when the view settles at a new size: the emulator scales the frames to it.
    val requested = remember(connection) { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(connection) {
        // The first size starts the stream at once; a resize waits for the view to settle.
        snapshotFlow { viewSize.value }
            .filter { it.width > 0 && it.height > 0 }
            .collectLatest { size ->
                if (requested.value != IntSize.Zero) delay(RESIZE_DEBOUNCE_MS)
                requested.value = size
            }
    }

    val current = screen
    LaunchedEffect(current, requested.value, attempt) {
        val size = requested.value
        if (current == null || size.width == 0) return@LaunchedEffect
        try {
            current
                .frames(size.width, size.height)
                .conflate()
                .map { frame -> DrawnFrame(frame, frame.toBitmap()) }
                .flowOn(Dispatchers.Default)
                .collect {
                    drawn = it
                    lastFrames[connection.serial] = it
                    failure = null
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w("LiveDeviceScreen", "The screen stream ended", e)
            failure = e.message ?: "stream"
        }
    }

    // Input goes out one event at a time, in order: a lift must not overtake its press. Off the UI
    // thread: while the device streams (as it does all through a drag) that thread is busy drawing
    // frames, and a touch waiting for a turn on it reached the device a redraw late, each one.
    val input = remember(current) { Channel<suspend () -> Unit>(Channel.UNLIMITED) }
    LaunchedEffect(input) {
        withContext(Dispatchers.IO) {
            for (event in input) {
                try {
                    event()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    Log.w("LiveDeviceScreen", "Input didn't reach the device", e)
                }
            }
        }
    }

    // Where the user's finger is, drawn at once: the device's own redraw comes a frame (or, through
    // adb, a screenshot) later, and without this a click feels like nothing happened.
    var touch by remember { mutableStateOf<Offset?>(null) }
    val moves = remember { AtomicLong() }
    var touching by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val clipboard = LocalClipboardManager.current

    val frame = drawn
    val ratio = frame?.let { it.frame.width.toFloat() / it.frame.height } ?: PHONE_RATIO
    DeviceFrame(
        ratio = ratio,
        focused = focused,
        modifier = modifier,
        screenModifier =
            Modifier
                .onSizeChanged { viewSize.value = it }
                .focusRequester(focus)
                .focusable(interactionSource = interaction)
                .onKeyEvent { event ->
                    val s = current ?: return@onKeyEvent false
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val key = keyOf(event.key)
                    when {
                        (event.isCtrlPressed || event.isMetaPressed) && event.key == Key.V -> {
                            val text = clipboard.getText()?.text
                            if (!text.isNullOrEmpty()) input.trySend { s.type(text) }
                            true
                        }
                        event.isCtrlPressed || event.isMetaPressed -> false
                        key != null -> {
                            input.trySend { s.key(key) }
                            true
                        }
                        event.utf16CodePoint in 0x20 until 0xFFFF && event.utf16CodePoint != 0x7F -> {
                            val text = String(Character.toChars(event.utf16CodePoint))
                            input.trySend { s.type(text) }
                            true
                        }
                        else -> false
                    }
                }.pointerInput(current) {
                    // Keyed on the screen only: a new frame mid-drag must not restart the gesture; it reads the latest frame itself.
                    val s = current ?: return@pointerInput
                    awaitPointerEventScope {
                        var down = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            val f = drawn?.frame ?: continue
                            val fx = change.position.x / size.width
                            val fy = change.position.y / size.height
                            when (event.type) {
                                PointerEventType.Press -> {
                                    focus.requestFocus()
                                    down = true
                                    touch = Offset(fx, fy)
                                    touching = true
                                    input.trySend { s.pointer(PointerAction.DOWN, fx, fy, f) }
                                }
                                PointerEventType.Move ->
                                    if (down) {
                                        touch = Offset(fx, fy)
                                        // Only the newest move goes out: a fast mouse reports moves quicker
                                        // than the device takes them, and a queue of old ones is lag.
                                        val mine = moves.incrementAndGet()
                                        input.trySend { if (moves.get() == mine) s.pointer(PointerAction.MOVE, fx, fy, f) }
                                    }
                                PointerEventType.Release -> {
                                    if (down) input.trySend { s.pointer(PointerAction.UP, fx, fy, f) }
                                    down = false
                                    touching = false
                                }
                                PointerEventType.Scroll -> {
                                    // A short drag the wheel's way: what scrolling is on a touch screen.
                                    val dy = change.scrollDelta.y
                                    if (dy != 0f) {
                                        val to = (fy - dy * WHEEL_STEP).coerceIn(0.02f, 0.98f)
                                        input.trySend {
                                            s.pointer(PointerAction.DOWN, fx, fy, f)
                                            s.pointer(PointerAction.MOVE, fx, (fy + to) / 2, f)
                                            delay(WHEEL_MOVE_MS)
                                            s.pointer(PointerAction.MOVE, fx, to, f)
                                            delay(WHEEL_MOVE_MS)
                                            s.pointer(PointerAction.UP, fx, to, f)
                                        }
                                    }
                                }
                            }
                            change.consume()
                        }
                    }
                },
    ) {
        if (frame != null) {
            Image(frame.image, contentDescription = null, contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Medium, modifier = Modifier.fillMaxSize())
        } else if (failure == null) {
            FrameMessage("", progress = true)
        }
        FingerDot(touch, touching)
        AgentTouches(activity, agentName)
        failure?.let { why ->
            Column(
                Modifier.align(Alignment.Center).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(MaterialSymbols.Error, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(28.dp))
                Text(stringRes(Res.string.android_device_screen_failed, why), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
                FilledTonalButton(onClick = { attempt++ }) { Text(stringRes(Res.string.android_device_reconnect)) }
            }
        }
    }
}

/** The user's finger on the screen, as Android's "show taps" draws it: solid while down, fading after. */
@Composable
private fun FingerDot(
    at: Offset?,
    down: Boolean,
) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(down, at == null) {
        if (down) alpha.snapTo(1f) else alpha.animateTo(0f, tween(FINGER_FADE_MS))
    }
    val p = at ?: return
    if (alpha.value <= 0f) return
    val density = LocalDensity.current
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(p.x * size.width, p.y * size.height)
        val radius = with(density) { 14.dp.toPx() }
        drawCircle(Color.White.copy(alpha = 0.35f * alpha.value), radius, center)
        drawCircle(Color.White.copy(alpha = 0.7f * alpha.value), radius, center, style = Stroke(with(density) { 1.5.dp.toPx() }))
    }
}

/** Where an agent last touched: a ring that fades, a line for a swipe, and what it did in words. */
@Composable
private fun AgentTouches(
    activity: Flow<DeviceActivity>,
    agentName: String,
) {
    var last by remember { mutableStateOf<DeviceActivity?>(null) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(activity) {
        activity.collect { a ->
            last = a
            launch {
                fade.snapTo(1f)
                fade.animateTo(0f, tween(TOUCH_FADE_MS))
            }
        }
    }
    val a = last ?: return
    val alpha = fade.value
    if (alpha <= 0f) return
    val ring = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    Canvas(Modifier.fillMaxSize()) {
        val x = a.x ?: return@Canvas
        val y = a.y ?: return@Canvas
        val from = Offset(x * size.width, y * size.height)
        val radius = with(density) { 18.dp.toPx() }
        val toX = a.toX
        val toY = a.toY
        if (a.kind == DeviceActivity.Kind.SWIPE && toX != null && toY != null) {
            val to = Offset(toX * size.width, toY * size.height)
            drawLine(ring.copy(alpha = alpha), from, to, strokeWidth = with(density) { 4.dp.toPx() })
            drawCircle(ring.copy(alpha = alpha * 0.5f), radius * 0.6f, to)
        }
        drawCircle(ring.copy(alpha = alpha * 0.35f), radius * (1.6f - alpha * 0.6f), from)
        drawCircle(ring.copy(alpha = alpha), radius, from, style = Stroke(with(density) { 3.dp.toPx() }))
    }
    if (a.label.isNotBlank() || a.kind == DeviceActivity.Kind.LOOK) {
        Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.BottomCenter) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f * (alpha * 2).coerceAtMost(1f)),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ) {
                Text(activityLine(a, agentName), style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun activityLine(
    a: DeviceActivity,
    agentName: String,
): String {
    if (a.kind == DeviceActivity.Kind.LOOK) return stringRes(Res.string.android_device_agent_looked, agentName)
    val what =
        when (a.kind) {
            DeviceActivity.Kind.TAP -> stringRes(Res.string.android_device_did_tap, a.label)
            DeviceActivity.Kind.LONG_PRESS -> stringRes(Res.string.android_device_did_long_press, a.label)
            DeviceActivity.Kind.SWIPE -> stringRes(Res.string.android_device_did_swipe, a.label)
            DeviceActivity.Kind.KEY -> stringRes(Res.string.android_device_did_key, a.label)
            DeviceActivity.Kind.TYPE -> stringRes(Res.string.android_device_did_type, a.label)
            else -> stringRes(Res.string.android_device_did_open, a.label)
        }
    return stringRes(Res.string.android_device_agent_acted, agentName, what)
}

private fun keyOf(key: Key): DeviceKey? =
    when (key) {
        Key.Enter, Key.NumPadEnter -> DeviceKey.ENTER
        Key.Backspace -> DeviceKey.BACKSPACE
        Key.Delete -> DeviceKey.DELETE
        Key.Tab -> DeviceKey.TAB
        // Esc is Back, as in Android Studio's emulator.
        Key.Escape -> DeviceKey.BACK
        Key.DirectionUp -> DeviceKey.UP
        Key.DirectionDown -> DeviceKey.DOWN
        Key.DirectionLeft -> DeviceKey.LEFT
        Key.DirectionRight -> DeviceKey.RIGHT
        Key.MoveHome -> DeviceKey.MOVE_HOME
        Key.MoveEnd -> DeviceKey.MOVE_END
        Key.PageUp -> DeviceKey.PAGE_UP
        Key.PageDown -> DeviceKey.PAGE_DOWN
        else -> null
    }

private const val RESIZE_DEBOUNCE_MS = 250L

/** Each device's latest frame, by serial: one picture per device, shown while a new stream starts. */
private val lastFrames = ConcurrentHashMap<String, DrawnFrame>()
private const val TOUCH_FADE_MS = 1_400
private const val FINGER_FADE_MS = 250
private const val WHEEL_STEP = 0.12f
private const val WHEEL_MOVE_MS = 30L
