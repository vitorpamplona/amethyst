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
package com.vitorpamplona.devicecore.input

/**
 * A key or hardware button a device panel or an agent presses, whatever the device: each platform
 * maps it to its own (Android's `KeyEvent` codes and the emulator's W3C names in
 * `androidDeviceClient`, an iPhone's buttons and keyboard usages in `iosSimulatorClient`).
 */
enum class DeviceKey {
    BACK,
    HOME,
    RECENTS,
    POWER,
    VOLUME_UP,
    VOLUME_DOWN,
    ENTER,
    BACKSPACE,
    DELETE,
    TAB,
    ESCAPE,
    UP,
    DOWN,
    LEFT,
    RIGHT,
    MOVE_HOME,
    MOVE_END,
    PAGE_UP,
    PAGE_DOWN,
    PASTE,
    WAKE,
    ;

    companion object {
        /** By its name, case- and separator-insensitive ("volume_up", "Volume Up", "back"). */
        fun named(name: String): DeviceKey? {
            val n =
                name
                    .trim()
                    .uppercase()
                    .replace(' ', '_')
                    .replace('-', '_')
            return entries.firstOrNull { it.name == n }
                ?: when (n) {
                    "APP_SWITCH", "OVERVIEW" -> RECENTS
                    "DEL" -> BACKSPACE
                    "RETURN" -> ENTER
                    "ESC" -> ESCAPE
                    "WAKEUP" -> WAKE
                    else -> null
                }
        }
    }
}
