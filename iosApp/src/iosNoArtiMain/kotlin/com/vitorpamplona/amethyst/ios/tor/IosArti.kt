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
package com.vitorpamplona.amethyst.ios.tor

import com.vitorpamplona.amethyst.commons.tor.ArtiBridge

/**
 * No Arti in this build: the cinterop needs the Apple SDK, so a non-Mac host compiles `:iosApp`
 * without it (iosApp/build.gradle.kts). Tor then reports unavailable and stays off; no app is ever
 * built from this variant, which only keeps the Kotlin compiling. The Mac build uses
 * `src/iosArtiMain`'s [IosArti].
 */
internal object IosArti {
    const val AVAILABLE: Boolean = false

    val bridge: ArtiBridge get() = NoArti

    /** Every call fails, so a TorService over it never leaves Connecting. Nothing starts one. */
    private object NoArti : ArtiBridge {
        override fun setLogCallback(callback: (String) -> Unit) = Unit

        override fun initialize(dataDir: String): Int = -1

        override fun isBootstrapped(): Int = -1

        override fun bootstrapProgressPermille(): Int = 0

        override fun startSocksProxy(port: Int): Int = -1

        override fun stopSocksProxy(): Int = -1

        override fun destroy(): Int = -1
    }
}
