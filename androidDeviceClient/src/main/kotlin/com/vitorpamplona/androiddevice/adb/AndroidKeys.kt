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
package com.vitorpamplona.androiddevice.adb

import com.vitorpamplona.devicecore.input.DeviceKey

/** The key's Android `KeyEvent` code, as `adb shell input keyevent` takes it. */
val DeviceKey.keyCode: Int
    get() =
        when (this) {
            DeviceKey.BACK -> 4
            DeviceKey.HOME -> 3
            DeviceKey.RECENTS -> 187
            DeviceKey.POWER -> 26
            DeviceKey.VOLUME_UP -> 24
            DeviceKey.VOLUME_DOWN -> 25
            DeviceKey.ENTER -> 66
            DeviceKey.BACKSPACE -> 67
            DeviceKey.DELETE -> 112
            DeviceKey.TAB -> 61
            DeviceKey.ESCAPE -> 111
            DeviceKey.UP -> 19
            DeviceKey.DOWN -> 20
            DeviceKey.LEFT -> 21
            DeviceKey.RIGHT -> 22
            DeviceKey.MOVE_HOME -> 122
            DeviceKey.MOVE_END -> 123
            DeviceKey.PAGE_UP -> 92
            DeviceKey.PAGE_DOWN -> 93
            DeviceKey.PASTE -> 279
            DeviceKey.WAKE -> 224
        }

/** The key's W3C name, as the emulator's gRPC `sendKey` takes it. */
val DeviceKey.w3c: String
    get() =
        when (this) {
            DeviceKey.BACK -> "GoBack"
            DeviceKey.HOME -> "GoHome"
            DeviceKey.RECENTS -> "AppSwitch"
            DeviceKey.POWER -> "Power"
            DeviceKey.VOLUME_UP -> "AudioVolumeUp"
            DeviceKey.VOLUME_DOWN -> "AudioVolumeDown"
            DeviceKey.ENTER -> "Enter"
            DeviceKey.BACKSPACE -> "Backspace"
            DeviceKey.DELETE -> "Delete"
            DeviceKey.TAB -> "Tab"
            DeviceKey.ESCAPE -> "Escape"
            DeviceKey.UP -> "ArrowUp"
            DeviceKey.DOWN -> "ArrowDown"
            DeviceKey.LEFT -> "ArrowLeft"
            DeviceKey.RIGHT -> "ArrowRight"
            DeviceKey.MOVE_HOME -> "Home"
            DeviceKey.MOVE_END -> "End"
            DeviceKey.PAGE_UP -> "PageUp"
            DeviceKey.PAGE_DOWN -> "PageDown"
            DeviceKey.PASTE -> "Paste"
            DeviceKey.WAKE -> "WakeUp"
        }
