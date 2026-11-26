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
 * iOS's [ArtiBridge]: the C ABI of the Arti wrapper through the cinterop [ArtiNative], so the
 * shared `TorService` / `TorManager` lifecycle runs over the same engine Android ships.
 *
 * `internal` and unreferenced on purpose until B12d (plans/2026-11-25-ios-tor.md): nothing in
 * the app builds a `TorService` yet, so Kotlin/Native drops this along with [ArtiNative] and the
 * static AmethystKit framework keeps no undefined `_arti_*` symbols. B12d links `libarti.a`
 * into the app and wires this into `IosAppModules`.
 *
 * Unlike Android's JNI shim, [startSocksProxy] and [stopSocksProxy] here wait for the aborted
 * listener to drop, so a restart on the same port binds instead of failing with "address in use".
 */
internal object ArtiCinteropBridge : ArtiBridge {
    override fun setLogCallback(callback: (String) -> Unit) {
        ArtiNative.setLogCallback(callback)
    }

    override fun initialize(dataDir: String): Int = ArtiNative.initialize(dataDir)

    override fun isBootstrapped(): Int = ArtiNative.isBootstrapped()

    override fun bootstrapProgressPermille(): Int = ArtiNative.bootstrapProgressPermille()

    override fun startSocksProxy(port: Int): Int = ArtiNative.startSocksProxy(port)

    override fun stopSocksProxy(): Int = ArtiNative.stopSocksProxy()

    override fun destroy(): Int = ArtiNative.destroy()
}
