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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

private const val DEFAULT_SOCKS_PORT = 19050
private const val STOP_TIMEOUT_MS = 10_000L

/**
 * Manages a single ArtiProxy instance with explicit start/stop lifecycle.
 *
 * ArtiProxy is created once and reused for the lifetime of the app.
 * Only [start] and [stop] are called on it — never recreated — to
 * avoid state file lock conflicts in the native layer.
 */
class TorService(
    val context: Context,
) {
    private val mutex = Mutex()
    private val running = AtomicBoolean(false)
    private val bootstrapped = AtomicBoolean(false)
    private val socksPort = DEFAULT_SOCKS_PORT

    @Volatile
    private var stoppedSignal: CompletableDeferred<Unit>? = null

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

                text.contains("state changed to Stopped", ignoreCase = true) -> {
                    bootstrapped.set(false)
                    running.set(false)
                    _status.value = TorServiceStatus.Off
                    stoppedSignal?.complete(Unit)
                }

                text.contains(
                    "Another process has the lock",
                    ignoreCase = true,
                ) -> {
                    Log.e("TorService") { "Arti state file lock conflict" }
                    bootstrapped.set(false)
                    _status.value = TorServiceStatus.Off
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
        withContext(NonCancellable) {
            mutex.withLock {
                if (running.get()) {
                    if (bootstrapped.get()) {
                        _status.value = TorServiceStatus.Active(socksPort)
                    } else {
                        _status.value = TorServiceStatus.Connecting
                    }
                    return@withContext
                }

                _status.value = TorServiceStatus.Connecting
                bootstrapped.set(false)

                withContext(Dispatchers.IO) {
                    try {
                        artiProxy.start()
                        running.set(true)
                        Log.d("TorService") { "Arti started on port $socksPort" }
                    } catch (e: Exception) {
                        Log.e("TorService") { "Failed to start Arti: ${e.message}" }
                        _status.value = TorServiceStatus.Off
                    }
                }
            }
        }
    }

    suspend fun stop() {
        withContext(NonCancellable) {
            mutex.withLock {
                if (!running.get()) return@withContext

                Log.d("TorService", "Stopping Arti")
                withContext(Dispatchers.IO) {
                    val signal = CompletableDeferred<Unit>()
                    stoppedSignal = signal

                    try {
                        artiProxy.stop()
                    } catch (e: Exception) {
                        Log.d("TorService") { "Failed to stop Arti: ${e.message}" }
                    }

                    val confirmed = withTimeoutOrNull(STOP_TIMEOUT_MS) { signal.await() }
                    stoppedSignal = null

                    if (confirmed != null) {
                        Log.d("TorService") { "Arti confirmed stopped" }
                    } else {
                        Log.w("TorService") { "Arti stop timed out after ${STOP_TIMEOUT_MS / 1000}s" }
                        running.set(false)
                    }
                }
                _status.value = TorServiceStatus.Off
            }
        }
    }
}
