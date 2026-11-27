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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication
import platform.UIKit.UIScreen

/**
 * Full brightness and no auto-lock while a QR code is on screen, as on Android. Both are put back
 * to what they were, not to fixed values: the player may hold the idle timer off while it plays.
 */
@Composable
actual fun KeepScreenBrightAndAwake() {
    DisposableEffect(Unit) {
        val app = UIApplication.sharedApplication
        val screen = UIScreen.mainScreen
        val previousBrightness = screen.brightness
        val previousIdleTimerDisabled = app.idleTimerDisabled
        screen.brightness = 1.0
        app.idleTimerDisabled = true
        onDispose {
            screen.brightness = previousBrightness
            app.idleTimerDisabled = previousIdleTimerDisabled
        }
    }
}
