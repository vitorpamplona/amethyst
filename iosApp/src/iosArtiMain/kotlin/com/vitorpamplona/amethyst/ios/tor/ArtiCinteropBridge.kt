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
import com.vitorpamplona.amethyst.commons.util.excludeDirectoryFromBackup

/**
 * iOS's [ArtiBridge]: the C ABI of the Arti wrapper through the cinterop [ArtiNative], so the
 * shared `TorService` / `TorManager` lifecycle runs over the same engine Android ships.
 *
 * `IosAppModules` runs the shared `TorService` over it (plans/2026-11-25-ios-tor.md, B12d), and
 * Xcode links `libarti.a` into the app for it.
 *
 * Unlike Android's JNI shim, [startSocksProxy] and [stopSocksProxy] here wait for the aborted
 * listener to drop, so a restart on the same port binds instead of failing with "address in use".
 */
internal object ArtiCinteropBridge : ArtiBridge {
    override fun setLogCallback(callback: (String) -> Unit) {
        ArtiNative.setLogCallback(callback)
    }

    /**
     * Arti's directory holds its consensus cache and guard sample: rebuilt by any bootstrap, specific
     * to this device, and megabytes in size, so it stays out of iCloud and computer backups. Set here,
     * before every initialize, because a clean-state reset deletes the whole directory (and the flag
     * with it) and this is the first call after one.
     */
    override fun initialize(dataDir: String): Int {
        excludeDirectoryFromBackup(dataDir)
        return ArtiNative.initialize(dataDir)
    }

    override fun isBootstrapped(): Int = ArtiNative.isBootstrapped()

    override fun bootstrapProgressPermille(): Int = ArtiNative.bootstrapProgressPermille()

    override fun startSocksProxy(port: Int): Int = ArtiNative.startSocksProxy(port)

    override fun stopSocksProxy(): Int = ArtiNative.stopSocksProxy()

    override fun destroy(): Int = ArtiNative.destroy()
}
