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
import com.vitorpamplona.quartz.utils.Log
import info.guardianproject.arti.ArtiLogListener
import info.guardianproject.arti.ArtiProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

private const val DEFAULT_SOCKS_PORT = 19050

/**
 * Manages a single ArtiProxy instance for the app's lifetime.
 *
 * Arti's state file lock is tied to the TorClient object's lifetime —
 * it is only released when the object is garbage collected, not when
 * stop() is called. Calling stop()+start() on ArtiProxy creates a new
 * internal TorClient that conflicts with the old lock.
 *
 * Therefore, ArtiProxy is created and started once. It runs for the
 * entire process lifetime. When the user turns Tor "off", TorManager
 * simply stops emitting Active status — no traffic is routed through
 * the proxy, but the proxy itself stays alive. This is safe because
 * an idle Arti uses negligible resources and maintains no circuits
 * when no SOCKS connections are made.
 */
class TorService(
    val context: Context,
) {
    private val socksPort = DEFAULT_SOCKS_PORT
    private val bootstrapped = AtomicBoolean(false)
    private val started = AtomicBoolean(false)

    private val _status = MutableStateFlow<TorServiceStatus>(TorServiceStatus.Off)
    val status: StateFlow<TorServiceStatus> = _status.asStateFlow()

    private val logListener =
        ArtiLogListener { logLine ->
            val text = logLine ?: return@ArtiLogListener
            Log.d("TorService") { "Arti: $text" }

            when {
                text.contains("Sufficiently bootstrapped", ignoreCase = true) ||
                    text.contains("is usable", ignoreCase = true) -> {
                    if (bootstrapped.compareAndSet(false, true)) {
                        _status.value = TorServiceStatus.Active(socksPort)
                        Log.d("TorService") { "Arti bootstrapped on port $socksPort" }
                    }
                }

                text.contains(
                    "Another process has the lock",
                    ignoreCase = true,
                ) -> {
                    Log.e("TorService") { "Arti state file lock conflict" }
                }
            }
        }

    private val artiProxy: ArtiProxy =
        ArtiProxy
            .Builder(context.applicationContext)
            .setSocksPort(socksPort)
            .setDnsPort(socksPort + 1)
            .setLogListener(logListener)
            .build()

    suspend fun start() {
        if (started.get()) {
            // Already started — just emit current state
            if (bootstrapped.get()) {
                _status.value = TorServiceStatus.Active(socksPort)
            } else {
                _status.value = TorServiceStatus.Connecting
            }
            return
        }

        _status.value = TorServiceStatus.Connecting

        withContext(Dispatchers.IO) {
            try {
                artiProxy.start()
                started.set(true)
                Log.d("TorService") { "Arti started on port $socksPort" }
            } catch (e: Exception) {
                Log.e("TorService") { "Failed to start Arti: ${e.message}" }
                _status.value = TorServiceStatus.Off
            }
        }
    }
}
