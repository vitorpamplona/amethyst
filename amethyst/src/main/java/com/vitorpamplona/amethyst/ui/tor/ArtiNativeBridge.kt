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
package com.vitorpamplona.amethyst.ui.tor

import android.content.Context
import com.vitorpamplona.amethyst.commons.tor.ArtiBridge
import com.vitorpamplona.amethyst.commons.tor.TorService
import kotlinx.coroutines.CoroutineScope
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * Android's [ArtiBridge]: the JNI calls of [ArtiNative] (`libarti_android.so`).
 *
 * [ArtiNative] stays in this package because its JNI symbol names (`Java_com_vitorpamplona_
 * amethyst_ui_tor_ArtiNative_*`) are compiled into the `.so`, and R8 keeps it and
 * [ArtiLogCallback] by name (`proguard-rules.pro`, `tools/r8-verify/reflection-contract.txt`).
 *
 * Touching [ArtiNative] runs `System.loadLibrary`, so this object never does it on its own: the
 * first bridge call [TorService] makes, on its IO dispatcher, loads the library, as it did before
 * the lifecycle moved to commons.
 */
object ArtiNativeBridge : ArtiBridge {
    override fun setLogCallback(callback: (String) -> Unit) = ArtiNative.setLogCallback(ArtiLogCallback { callback(it) })

    override fun initialize(dataDir: String): Int = ArtiNative.initialize(dataDir)

    override fun isBootstrapped(): Int = ArtiNative.isBootstrapped()

    override fun bootstrapProgressPermille(): Int = ArtiNative.bootstrapProgressPermille()

    override fun startSocksProxy(port: Int): Int = ArtiNative.startSocksProxy(port)

    override fun stopSocksProxy(): Int = ArtiNative.stopSocksProxy()

    override fun destroy(): Int = ArtiNative.destroy()
}

/** The app's [TorService]: Arti over JNI, its state and cache in `<filesDir>/arti`. */
fun androidTorService(
    context: Context,
    scope: CoroutineScope,
): TorService =
    TorService(
        engine = ArtiNativeBridge,
        dataDir = File(context.filesDir, "arti").toOkioPath(),
        fileSystem = FileSystem.SYSTEM,
        scope = scope,
    )
