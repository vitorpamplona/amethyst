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
package com.vitorpamplona.iossimulator.device

import com.vitorpamplona.devicecore.input.DeviceKey
import com.vitorpamplona.iossimulator.idb.HidStep
import com.vitorpamplona.iossimulator.idb.IdbButton

/**
 * Keys as an iOS simulator takes them: hardware buttons, or USB HID keyboard usages (page 7) on
 * the simulator's hardware keyboard, which the companion sends as a real keyboard's presses.
 */
object HidKeys {
    const val SHIFT = 225
    const val COMMAND = 227

    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val DIGITS = "1234567890"
    private const val SHIFTED_DIGITS = "!@#$%^&*()"

    /** Unshifted, then shifted, for the punctuation keys from `-` (45) to `/` (56); 50 is the ISO key. */
    private val PUNCTUATION =
        mapOf(
            '-' to 45,
            '=' to 46,
            '[' to 47,
            ']' to 48,
            '\\' to 49,
            ';' to 51,
            '\'' to 52,
            '`' to 53,
            ',' to 54,
            '.' to 55,
            '/' to 56,
        )
    private val SHIFTED_PUNCTUATION =
        mapOf(
            '_' to 45,
            '+' to 46,
            '{' to 47,
            '}' to 48,
            '|' to 49,
            ':' to 51,
            '"' to 52,
            '~' to 53,
            '<' to 54,
            '>' to 55,
            '?' to 56,
        )

    /** The key (and whether Shift) a US keyboard types [c] with; null for what it has no key for. */
    fun of(c: Char): Pair<Int, Boolean>? =
        when {
            c in LOWER -> (4 + LOWER.indexOf(c)) to false
            c.lowercaseChar() in LOWER && c.isUpperCase() -> (4 + LOWER.indexOf(c.lowercaseChar())) to true
            c in DIGITS -> (30 + DIGITS.indexOf(c)) to false
            c in SHIFTED_DIGITS -> (30 + SHIFTED_DIGITS.indexOf(c)) to true
            c == '\n' -> 40 to false
            c == '\t' -> 43 to false
            c == ' ' -> 44 to false
            else -> PUNCTUATION[c]?.let { it to false } ?: SHIFTED_PUNCTUATION[c]?.let { it to true }
        }

    /** Whether every character of [text] has a key ([of]). */
    fun typeable(text: String): Boolean = text.all { of(it) != null }

    /** [text] as key presses on a US keyboard. Throws for a character it has no key for. */
    fun typing(text: String): List<HidStep> =
        text.flatMap { c ->
            val (code, shift) = of(c) ?: throw IllegalArgumentException("No key types '$c' on a US keyboard")
            if (shift) {
                listOf(HidStep.Key(SHIFT, true), HidStep.Key(code, true), HidStep.Key(code, false), HidStep.Key(SHIFT, false))
            } else {
                listOf(HidStep.Key(code, true), HidStep.Key(code, false))
            }
        }

    private fun tap(code: Int) = listOf(HidStep.Key(code, true), HidStep.Key(code, false))

    private fun press(button: IdbButton) = listOf(HidStep.Button(button, true), HidStep.Button(button, false))

    /**
     * What [key] is on an iPhone, or null for one it has no counterpart of ([DeviceKey.BACK]: iOS
     * goes back with a swipe from the left edge, which [com.vitorpamplona.iossimulator.device.SimulatorScreen] makes).
     */
    fun steps(key: DeviceKey): List<HidStep>? =
        when (key) {
            DeviceKey.HOME -> press(IdbButton.HOME)
            // Two presses of Home open the app switcher, on Face ID models too.
            DeviceKey.RECENTS -> press(IdbButton.HOME) + HidStep.Delay(0.12) + press(IdbButton.HOME)
            DeviceKey.POWER -> press(IdbButton.LOCK)
            DeviceKey.WAKE -> press(IdbButton.HOME)
            DeviceKey.VOLUME_UP -> press(IdbButton.VOLUME_UP)
            DeviceKey.VOLUME_DOWN -> press(IdbButton.VOLUME_DOWN)
            DeviceKey.ENTER -> tap(40)
            DeviceKey.ESCAPE -> tap(41)
            DeviceKey.BACKSPACE -> tap(42)
            DeviceKey.TAB -> tap(43)
            DeviceKey.MOVE_HOME -> tap(74)
            DeviceKey.PAGE_UP -> tap(75)
            DeviceKey.DELETE -> tap(76)
            DeviceKey.MOVE_END -> tap(77)
            DeviceKey.PAGE_DOWN -> tap(78)
            DeviceKey.RIGHT -> tap(79)
            DeviceKey.LEFT -> tap(80)
            DeviceKey.DOWN -> tap(81)
            DeviceKey.UP -> tap(82)
            DeviceKey.PASTE -> listOf(HidStep.Key(COMMAND, true)) + tap(25) + HidStep.Key(COMMAND, false)
            DeviceKey.BACK -> null
        }
}
