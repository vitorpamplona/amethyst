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
package com.vitorpamplona.amethyst.commons.ui.cartridges

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.commons.cartridges.CartPlayerViewModel
import com.vitorpamplona.amethyst.commons.cartridges.CartPlayerViewModel.Buttons
import com.vitorpamplona.amethyst.commons.cartridges.CartPlayerViewModel.Companion.SCREEN_SIZE
import com.vitorpamplona.amethyst.commons.cartridges.cartSaveStore
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cart_button_o_description
import com.vitorpamplona.amethyst.commons.resources.cart_button_x_description
import com.vitorpamplona.amethyst.commons.resources.cart_dpad_description
import com.vitorpamplona.amethyst.commons.resources.cart_game_ended
import com.vitorpamplona.amethyst.commons.resources.cart_game_error
import com.vitorpamplona.amethyst.commons.resources.cart_keyboard_hint
import com.vitorpamplona.amethyst.commons.resources.cart_loading
import com.vitorpamplona.amethyst.commons.resources.cart_not_a_cart
import com.vitorpamplona.amethyst.commons.resources.cart_pause_menu
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/** Plays the cart at [url] full screen, over whatever opened it. */
@Composable
fun CartPlayerDialog(
    url: String,
    sha256: String?,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        CartPlayer(url, sha256, accountViewModel, onDismiss)
    }
}

/**
 * The game screen with an on-screen direction pad and O/X buttons, and the keyboard or a gamepad
 * (arrows, Z/C/N for O, X/V/M for X, Enter or P to pause), plus a pause button for touch screens.
 * The screen sits above the controls on a tall window and between them on a wide one.
 */
@Composable
fun CartPlayer(
    url: String,
    sha256: String?,
    accountViewModel: AccountViewModel,
    onClose: () -> Unit,
) {
    val appStores = LocalAppServices.current.appStores
    val viewModel =
        rememberViewModel(key = "cart:$url") {
            CartPlayerViewModel(url, sha256, accountViewModel.httpClientBuilder, appStores.cartSaveStore())
        }
    DisposableEffect(viewModel) {
        viewModel.resume()
        onDispose { viewModel.pause() }
    }
    val status by viewModel.status.collectAsState()
    val frame = viewModel.frame.collectAsState()

    // Plain holders, sent on every change: an effect keyed on them would merge a quick tap's
    // press and release into one recomposition and the game would never see the press.
    val held = remember { HeldButtons() }
    val send = { viewModel.setButtons(held.mask) }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.key == Key.Escape || event.key == Key.Back) {
                    if (event.type == KeyEventType.KeyUp) onClose()
                    return@onKeyEvent true
                }
                // Games that read the keyboard get every key typed, buttons included, as on the original.
                if (event.type == KeyEventType.KeyDown) typedText(event)?.let(viewModel::type)
                val bit = KEY_BITS[event.key] ?: return@onKeyEvent false
                held.keys =
                    when (event.type) {
                        KeyEventType.KeyDown -> held.keys or bit
                        KeyEventType.KeyUp -> held.keys and bit.inv()
                        else -> held.keys
                    }
                send()
                true
            },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val wide = maxWidth > maxHeight
            val control = min(maxWidth.value, maxHeight.value).coerceIn(120f, 200f).dp * if (wide) 0.9f else 0.8f
            val controls: @Composable (Modifier) -> Unit = { modifier ->
                DirectionPad(
                    onChange = {
                        held.pad = it
                        send()
                    },
                    modifier = modifier.size(control),
                )
            }
            val actions: @Composable () -> Unit = {
                ActionButtons(
                    size = control,
                    onO = {
                        held.o = if (it) Buttons.O else 0
                        send()
                    },
                    onX = {
                        held.x = if (it) Buttons.X else 0
                        send()
                    },
                )
            }
            if (wide) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    controls(Modifier)
                    GameScreen(frame, viewModel, Modifier.weight(1f).aspectRatio(1f, matchHeightConstraintsFirst = true))
                    actions()
                }
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(48.dp))
                    GameScreen(frame, viewModel, Modifier.fillMaxWidth().aspectRatio(1f))
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        controls(Modifier)
                        actions()
                    }
                    Hint(stringRes(Res.string.cart_keyboard_hint), Modifier.padding(16.dp))
                }
            }
        }

        StatusOverlay(status, Modifier.align(Alignment.Center))

        IconButton(onClick = viewModel::tapPause, modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding()) {
            Icon(symbol = MaterialSymbols.Pause, contentDescription = stringRes(Res.string.cart_pause_menu), tint = Color.White)
        }
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding()) {
            Icon(symbol = MaterialSymbols.Close, contentDescription = stringRes(Res.string.close), tint = Color.White)
        }
    }
}

/** What each control holds down, as [Buttons] bits. */
private class HeldButtons {
    var pad = 0
    var o = 0
    var x = 0
    var keys = 0
    val mask get() = pad or o or x or keys
}

/** Keyboard and gamepad keys and the button each holds down. */
private val KEY_BITS: Map<Key, Int> =
    mapOf(
        Key.DirectionLeft to Buttons.LEFT,
        Key.DirectionRight to Buttons.RIGHT,
        Key.DirectionUp to Buttons.UP,
        Key.DirectionDown to Buttons.DOWN,
        Key.Z to Buttons.O,
        Key.C to Buttons.O,
        Key.N to Buttons.O,
        Key.ButtonA to Buttons.O,
        Key.ButtonY to Buttons.O,
        Key.X to Buttons.X,
        Key.V to Buttons.X,
        Key.M to Buttons.X,
        Key.ButtonB to Buttons.X,
        Key.ButtonX to Buttons.X,
        Key.Enter to Buttons.PAUSE,
        Key.P to Buttons.PAUSE,
        Key.ButtonStart to Buttons.PAUSE,
    )

/** What a key types for games that read the keyboard: Enter is a carriage return and Backspace `\b`, as on the original. */
private fun typedText(event: KeyEvent): String? =
    when {
        event.key == Key.Enter -> "\r"
        event.key == Key.Backspace -> "\b"
        event.utf16CodePoint >= ' '.code -> event.utf16CodePoint.toChar().toString()
        else -> null
    }

/**
 * The 128x128 screen, as large as fits, scaled by a whole number when there is room so pixels
 * stay square. The pointer over it (a mouse, or a finger on a touch screen) goes to the game in
 * game pixels, for games that read it.
 */
@Composable
private fun GameScreen(
    frame: State<CartPlayerViewModel.Frame?>,
    viewModel: CartPlayerViewModel,
    modifier: Modifier,
) {
    val bitmap = remember { newCartScreenBitmap() }
    Canvas(
        modifier.pointerInput(viewModel) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: continue
                    if (event.type == PointerEventType.Scroll) {
                        val dy = change.scrollDelta.y
                        if (dy != 0f) viewModel.scroll(if (dy < 0) 1 else -1)
                        continue
                    }
                    val (left, top, side) = screenRect(size.width.toFloat(), size.height.toFloat())
                    val x = ((change.position.x - left) * SCREEN_SIZE / side).toInt().coerceIn(0, SCREEN_SIZE - 1)
                    val y = ((change.position.y - top) * SCREEN_SIZE / side).toInt().coerceIn(0, SCREEN_SIZE - 1)
                    val buttons =
                        if (change.type == PointerType.Mouse) {
                            (if (event.buttons.isPrimaryPressed) 1 else 0) or
                                (if (event.buttons.isSecondaryPressed) 2 else 0) or
                                (if (event.buttons.isTertiaryPressed) 4 else 0)
                        } else {
                            if (change.pressed) 1 else 0
                        }
                    viewModel.setPointer(x, y, buttons)
                }
            }
        },
    ) {
        // Read here, not in composition: a new frame redraws without recomposing.
        val current = frame.value ?: return@Canvas
        val image = bitmap.update(current.pixels)
        val (left, top, side) = screenRect(size.width, size.height)
        drawImage(
            image = image,
            srcSize = IntSize(SCREEN_SIZE, SCREEN_SIZE),
            dstOffset = IntOffset(left.toInt(), top.toInt()),
            dstSize = IntSize(side.toInt(), side.toInt()),
            filterQuality = FilterQuality.None,
        )
    }
}

/** Where the screen lands in a [width] x [height] area: left, top and side, in pixels. */
private fun screenRect(
    width: Float,
    height: Float,
): Triple<Float, Float, Float> {
    val fit = min(width, height)
    val side = if (fit >= SCREEN_SIZE * 2) floor(fit / SCREEN_SIZE) * SCREEN_SIZE else fit
    return Triple((width - side) / 2, (height - side) / 2, side)
}

@Composable
private fun StatusOverlay(
    status: CartPlayerViewModel.Status,
    modifier: Modifier,
) {
    when (status) {
        CartPlayerViewModel.Status.Loading ->
            Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Hint(stringRes(Res.string.cart_loading), Modifier.padding(16.dp))
            }
        CartPlayerViewModel.Status.NotACart -> Hint(stringRes(Res.string.cart_not_a_cart), modifier.padding(32.dp))
        is CartPlayerViewModel.Status.Stopped -> {
            // The game's last screen (with any error drawn on it) stays up; this only labels it.
            val text = if (status.error == null) Res.string.cart_game_ended else Res.string.cart_game_error
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Hint(stringRes(text), Modifier.safeDrawingPadding().padding(16.dp))
            }
        }
        CartPlayerViewModel.Status.Playing -> {}
    }
}

@Composable
private fun Hint(
    text: String,
    modifier: Modifier,
) {
    Text(text, modifier = modifier, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, textAlign = TextAlign.Center)
}

private val ControlColor = Color.White.copy(alpha = 0.22f)
private val PressedColor = Color.White.copy(alpha = 0.55f)

/** A cross that reads every finger on it, so sliding between directions and diagonals work. */
@Composable
private fun DirectionPad(
    onChange: (Int) -> Unit,
    modifier: Modifier,
) {
    var held by remember { mutableIntStateOf(0) }
    val description = stringRes(Res.string.cart_dpad_description)
    Canvas(
        modifier
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        var bits = 0
                        for (change in event.changes) {
                            if (!change.pressed) continue
                            bits = bits or directionOf(change.position, size)
                            change.consume()
                        }
                        if (bits != held) {
                            held = bits
                            onChange(bits)
                        }
                    }
                }
            },
    ) {
        val arm = size.width / 3
        val radius = CornerRadius(arm / 4)
        drawRoundRect(ControlColor, Offset(0f, arm), Size(size.width, arm), radius)
        drawRoundRect(ControlColor, Offset(arm, 0f), Size(arm, size.height), radius)

        fun press(
            bit: Int,
            x: Float,
            y: Float,
        ) {
            if (held and bit != 0) drawRoundRect(PressedColor, Offset(x, y), Size(arm, arm), radius)
        }
        press(Buttons.LEFT, 0f, arm)
        press(Buttons.RIGHT, arm * 2, arm)
        press(Buttons.UP, arm, 0f)
        press(Buttons.DOWN, arm, arm * 2)
    }
}

/** Which directions a touch at [position] holds: each axis counts within 67.5° of it, so corners give diagonals. */
private fun directionOf(
    position: Offset,
    size: IntSize,
): Int {
    val dx = position.x - size.width / 2f
    val dy = position.y - size.height / 2f
    val dead = size.width * 0.1f
    var bits = 0
    if (abs(dx) > dead && abs(dx) >= abs(dy) * TAN_22_5) bits = bits or if (dx < 0) Buttons.LEFT else Buttons.RIGHT
    if (abs(dy) > dead && abs(dy) >= abs(dx) * TAN_22_5) bits = bits or if (dy < 0) Buttons.UP else Buttons.DOWN
    return bits
}

private const val TAN_22_5 = 0.4142f

@Composable
private fun ActionButtons(
    size: Dp,
    onO: (Boolean) -> Unit,
    onX: (Boolean) -> Unit,
) {
    val button = size * 0.45f
    // Staggered like a handheld: O low on the left, X high on the right.
    Box(Modifier.width(size).height(size)) {
        ActionButton(isX = false, onPress = onO, modifier = Modifier.size(button).align(Alignment.BottomStart))
        ActionButton(isX = true, onPress = onX, modifier = Modifier.size(button).align(Alignment.TopEnd))
    }
}

@Composable
private fun ActionButton(
    isX: Boolean,
    onPress: (Boolean) -> Unit,
    modifier: Modifier,
) {
    var pressed by remember { mutableIntStateOf(0) }
    val description = stringRes(if (isX) Res.string.cart_button_x_description else Res.string.cart_button_o_description)
    Canvas(
        modifier
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val down = if (event.changes.any { it.pressed }) 1 else 0
                        event.changes.forEach { it.consume() }
                        if (down != pressed) {
                            pressed = down
                            onPress(down == 1)
                        }
                    }
                }
            },
    ) {
        drawCircle(if (pressed == 1) PressedColor else ControlColor)
        val mark = size.minDimension * 0.22f
        val stroke = size.minDimension * 0.07f
        if (isX) {
            drawLine(Color.White, center - Offset(mark, mark), center + Offset(mark, mark), stroke, StrokeCap.Round)
            drawLine(Color.White, center - Offset(mark, -mark), center + Offset(mark, -mark), stroke, StrokeCap.Round)
        } else {
            drawCircle(Color.White, mark, style = Stroke(stroke))
        }
    }
}
