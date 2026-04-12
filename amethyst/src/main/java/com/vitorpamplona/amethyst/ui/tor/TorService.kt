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
import com.vitorpamplona.amethyst.commons.tor.TorServiceStatus
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

private const val DEFAULT_SOCKS_PORT = 17392
private const val MAX_PORT_RETRIES = 10

/**
 * Manages the Arti Tor client via custom JNI bindings.
 *
 * The native TorClient is initialized once and persists for the app's
 * lifetime — its state file lock is never released until the process exits.
 * The SOCKS proxy can be started/stopped independently without affecting
 * the TorClient or its file locks.
 *
 * All JNI calls (including System.loadLibrary) run on [Dispatchers.IO]
 * to avoid blocking the main thread.
 */
class TorService(
    val context: Context,
) {
    private var socksPort = DEFAULT_SOCKS_PORT
    private val initialized = AtomicBoolean(false)
    private val proxyRunning = AtomicBoolean(false)

    private val _status = MutableStateFlow<TorServiceStatus>(TorServiceStatus.Off)
    val status: StateFlow<TorServiceStatus> = _status.asStateFlow()

    /**
     * Initialize the TorClient (once) and start the SOCKS proxy.
     * Must be called from a coroutine on [Dispatchers.IO].
     */
    suspend fun start() {
        if (proxyRunning.get()) {
            if (_status.value is TorServiceStatus.Active) return
            _status.value = TorServiceStatus.Connecting
            return
        }

        _status.value = TorServiceStatus.Connecting

        withContext(Dispatchers.IO) {
            // Initialize TorClient once — this bootstraps the Tor network.
            // setLogCallback and initialize are the first ArtiNative calls,
            // which triggers System.loadLibrary on this IO thread.
            if (initialized.compareAndSet(false, true)) {
                ArtiNative.setLogCallback { text ->
                    Log.d("TorService") {
                        val newLine = text.indexOf('\n')
                        if (newLine > 1) {
                            "Arti: ${text.substring(0, newLine)}"
                        } else {
                            "Arti: $text"
                        }
                    }

                    when {
                        text.contains("Sufficiently bootstrapped", ignoreCase = true) -> {
                            _status.value = TorServiceStatus.Active(socksPort)
                            Log.d("TorService") { "Arti SOCKS proxy active on port $socksPort" }
                        }
                    }
                }

                val dataDir = File(context.filesDir, "arti").absolutePath
                Log.d("TorService") { "Initializing Arti with data dir: $dataDir" }

                val initResult = ArtiNative.initialize(dataDir)
                if (initResult != 0) {
                    Log.e("TorService") { "Failed to initialize Arti: error $initResult" }
                    initialized.set(false)
                    _status.value = TorServiceStatus.Off
                    return@withContext
                }
            }

            // Start the SOCKS proxy, retrying on next port if address is in use
            var port = socksPort
            var started = false
            for (attempt in 0 until MAX_PORT_RETRIES) {
                val proxyResult = ArtiNative.startSocksProxy(port)
                if (proxyResult == 0) {
                    socksPort = port
                    started = true
                    break
                }
                Log.w("TorService") { "Port $port in use, trying ${port + 1}" }
                port++
            }

            if (!started) {
                Log.e("TorService") { "Failed to start SOCKS proxy after $MAX_PORT_RETRIES attempts" }
                _status.value = TorServiceStatus.Off
                return@withContext
            }

            proxyRunning.set(true)
        }
    }

    /**
     * Stop the SOCKS proxy and release the port.
     * The TorClient stays alive — no file lock issues on restart.
     */
    suspend fun stop() {
        if (!proxyRunning.compareAndSet(true, false)) return

        withContext(Dispatchers.IO) {
            ArtiNative.stopSocksProxy()
            Log.d("TorService") { "SOCKS proxy stopped" }
        }

        _status.value = TorServiceStatus.Off
    }
}
