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

/**
 * JNI bridge to the custom-built Arti native library (libarti_android.so).
 *
 * The native TorClient is created once via [initialize] and persists for the
 * app's lifetime — its state file lock is never released until the process exits.
 *
 * The SOCKS proxy can be started and stopped independently via [startSocksProxy]
 * and [stopSocksProxy] without affecting the TorClient.
 */
object ArtiNative {
    init {
        System.loadLibrary("arti_android")
    }

    external fun getVersion(): String

    external fun setLogCallback(callback: ArtiLogCallback)

    /**
     * Initialize the Arti runtime and bootstrap the Tor client.
     * @param dataDir Path to the app's private data directory for Arti state/cache.
     * @return 0 on success, negative on error.
     */
    external fun initialize(dataDir: String): Int

    /**
     * Start the SOCKS5 proxy on the given port.
     * Can be called multiple times — stops any existing listener first.
     * @return 0 on success, negative on error.
     */
    external fun startSocksProxy(port: Int): Int

    /**
     * Stop the SOCKS5 proxy listener and release the port.
     * The TorClient stays alive — no state file lock issues.
     * @return 0 on success.
     */
    external fun stopSocksProxy(): Int
}

/**
 * Callback interface for Arti log messages from the native layer.
 */
fun interface ArtiLogCallback {
    fun onLogLine(line: String)
}
