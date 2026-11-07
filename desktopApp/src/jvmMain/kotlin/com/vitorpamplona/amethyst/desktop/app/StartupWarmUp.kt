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
package com.vitorpamplona.amethyst.desktop.app

import com.vitorpamplona.amethyst.desktop.platform.IconResources
import com.vitorpamplona.amethyst.desktop.platform.Platform
import com.vitorpamplona.amethyst.desktop.platform.PlatformInfo
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import okhttp3.OkHttpClient
import kotlin.concurrent.thread

/**
 * Does slow, side-effect-free first-time setups of a start on spare cores while the main thread
 * builds the modules, so the steps that need them find them done. Measured on a cold start (no
 * AppCDS), one step after another on one core: the login spent ~250 ms loading the secp256k1 native
 * library, the HTTP client managers ~250 ms on the first TLS setup (the trust store's certificates,
 * the SSLContext) on the main thread, and the window's creation ~200 ms decoding its icon.
 * Nothing here changes what the app does: a thread that reaches a step still running waits for
 * it, as it would for its own. See desktopApp/plans/2026-11-07-desktop-startup-performance.md.
 */
object StartupWarmUp {
    private const val TAG = "StartupWarmUp"

    fun start() {
        // Off for A/B runs against a start without it.
        if (System.getProperty("amethyst.startupWarmUp") == "false") return
        thread(isDaemon = true, name = "startup-warm-up-0") {
            // The login's crypto, then the window's icon, which also loads Skiko's native library
            // before the window does. Not on macOS: its icon is drawn with Java2D, which must not
            // start before main() sets the macOS AWT properties.
            step("secp256k1") { Secp256k1Instance.isPrivateKeyValid(ByteArray(32) { 1 }) }
            if (PlatformInfo.host != Platform.MACOS) step("icon") { IconResources.adaptedBitmapPainter }
        }
        thread(isDaemon = true, name = "startup-warm-up-1") {
            // The JDK keeps the trust store and the SSL context once built.
            step("tls") { OkHttpClient.Builder().build() }
        }
    }

    private inline fun step(
        name: String,
        block: () -> Unit,
    ) {
        try {
            block()
        } catch (e: Throwable) {
            // A warm-up that fails only means the real step pays for it later.
            Log.w(TAG, "Warm-up step $name failed", e)
        }
    }
}
