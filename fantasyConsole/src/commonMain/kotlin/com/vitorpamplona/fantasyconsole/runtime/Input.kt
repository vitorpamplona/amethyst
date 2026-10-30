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
package com.vitorpamplona.fantasyconsole.runtime

/**
 * Buttons for up to eight players: 0 left, 1 right, 2 up, 3 down, 4 O, 5 X, 6 pause. The host
 * sets what is held with [setButtons] from any thread; [beginFrame] takes it in at the start of
 * each frame, so a frame sees one consistent state. A press that is released before the next
 * frame still counts for that frame, so a quick tap or keypress is never lost.
 */
class Input(
    private val ram: Ram,
) {
    private val held = IntArray(8)
    private val pressedSinceFrame = IntArray(8)
    private val current = IntArray(8)
    private val framesHeld = Array(8) { IntArray(8) }

    // Devkit mode (0x5f2d): the pointer in screen pixels, its buttons, the wheel and typed keys.
    private var pointerX = 0
    private var pointerY = 0
    private var pointerButtons = 0
    private var pointerPressedSinceFrame = 0
    private var wheelPending = 0
    private val typed = ArrayDeque<Char>()

    var mouseX = 0
        private set
    var mouseY = 0
        private set
    var mouseButtons = 0
        private set
    var wheel = 0
        private set

    fun setButtons(
        player: Int,
        mask: Int,
    ) {
        if (player !in 0..7) return
        held[player] = mask
        pressedSinceFrame[player] = pressedSinceFrame[player] or mask
    }

    /** Where the pointer is, in screen pixels (0..127), and which of its buttons (1 left, 2 right, 4 middle) are down. */
    fun setPointer(
        x: Int,
        y: Int,
        buttons: Int,
    ) {
        pointerX = x
        pointerY = y
        pointerButtons = buttons
        pointerPressedSinceFrame = pointerPressedSinceFrame or buttons
    }

    fun scroll(steps: Int) {
        wheelPending += steps
    }

    /** Typed text, as P8SCII: read back one character at a time with `stat(31)`. Dropped unless the cart turned the keyboard on. */
    fun type(text: String) {
        if (ram[Addr.DEVKIT] and 1 == 0) return
        for (c in text) if (typed.size < MAX_TYPED) typed.addLast(c)
    }

    fun beginFrame() {
        mouseX = pointerX
        mouseY = pointerY
        mouseButtons = pointerButtons or pointerPressedSinceFrame
        pointerPressedSinceFrame = 0
        wheel = wheelPending
        wheelPending = 0
        val devkit = ram[Addr.DEVKIT]
        // Devkit flag 2: the mouse buttons also press O, X and pause for player 0.
        val mouseAsButtons = if (devkit and 1 != 0 && devkit and 2 != 0) (mouseButtons and 7) shl 4 else 0
        for (p in 0 until 8) {
            current[p] = held[p] or pressedSinceFrame[p] or (if (p == 0) mouseAsButtons else 0)
            pressedSinceFrame[p] = 0
            for (b in 0 until 8) {
                framesHeld[p][b] = if ((current[p] shr b) and 1 != 0) framesHeld[p][b] + 1 else 0
            }
        }
    }

    fun hasKey(): Boolean = typed.isNotEmpty()

    fun nextKey(): String = if (typed.isEmpty()) "" else typed.removeFirst().toString()

    fun btn(
        b: Int,
        p: Int,
    ): Boolean = b in 0..7 && p in 0..7 && (current[p] shr b) and 1 != 0

    /** `btn()` with no arguments: player 0 in bits 0-7, player 1 in bits 8-15. */
    fun bits(): Int = (current[0] and 0xff) or ((current[1] and 0xff) shl 8)

    /** Pressed this frame, then again every few frames while held (15 frames, then every 4, at 30 fps). */
    fun btnp(
        b: Int,
        p: Int,
        fps: Int,
    ): Boolean {
        if (b !in 0..7 || p !in 0..7) return false
        val n = framesHeld[p][b]
        if (n == 1) return true
        if (n == 0) return false
        val delaySetting = ram[Addr.BTNP_DELAY]
        if (delaySetting == 255) return false
        val scale = if (fps >= 60) 2 else 1
        val delay = (if (delaySetting == 0) 15 else delaySetting) * scale
        val interval = (ram[Addr.BTNP_INTERVAL].let { if (it == 0) 4 else it }) * scale
        return n > delay && (n - delay - 1) % interval == 0
    }

    fun btnpBits(fps: Int): Int {
        var bits = 0
        for (p in 0..1) for (b in 0..7) if (btnp(b, p, fps)) bits = bits or (1 shl (b + p * 8))
        return bits
    }

    private companion object {
        const val MAX_TYPED = 64
    }
}
