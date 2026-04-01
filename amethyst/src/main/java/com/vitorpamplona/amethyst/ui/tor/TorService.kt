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
private const val MAX_PORT_RETRIES = 3
private const val STOP_TIMEOUT_MS = 10_000L

/**
 * Manages a single ArtiProxy instance with explicit start/stop lifecycle.
 *
 * ArtiProxy holds an exclusive lock on state files in the filesystem. Unlike
 * the old Android TorService (where bind/unbind was idempotent), we cannot
 * create and destroy ArtiProxy instances on every flow collection cycle —
 * the file lock from the old instance may not be released before the new
 * one tries to acquire it.
 *
 * Instead, TorService owns a single ArtiProxy and exposes its state via
 * a [StateFlow]. TorManager calls [start]/[stop] to control the lifecycle.
 */
class TorService(
    val context: Context,
) {
    private val mutex = Mutex()
    private var artiProxy: ArtiProxy? = null
    private var currentPort: Int = DEFAULT_SOCKS_PORT
    private val bootstrapped = AtomicBoolean(false)

    // Signalled by the log listener when Arti confirms it has stopped
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
                        _status.value = TorServiceStatus.Active(currentPort)
                        Log.d("TorService") { "Arti bootstrapped on port $currentPort" }
                    }
                }

                text.contains("state changed to Stopped", ignoreCase = true) -> {
                    bootstrapped.set(false)
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

    suspend fun start() {
        // NonCancellable ensures that if the calling coroutine is cancelled
        // (e.g., by transformLatest switching modes), we don't leak a
        // half-started ArtiProxy with no reference to stop it.
        withContext(NonCancellable) {
            mutex.withLock {
                if (artiProxy != null) {
                    // Already running — just ensure status is up to date
                    if (bootstrapped.get()) {
                        _status.value = TorServiceStatus.Active(currentPort)
                    } else {
                        _status.value = TorServiceStatus.Connecting
                    }
                    return@withContext
                }

                _status.value = TorServiceStatus.Connecting
                bootstrapped.set(false)

                withContext(Dispatchers.IO) {
                    var socksPort = DEFAULT_SOCKS_PORT
                    var lastError: Exception? = null

                    for (attempt in 0 until MAX_PORT_RETRIES) {
                        try {
                            val proxy =
                                ArtiProxy
                                    .Builder(context.applicationContext)
                                    .setSocksPort(socksPort)
                                    .setDnsPort(socksPort + 1)
                                    .setLogListener(logListener)
                                    .build()

                            proxy.start()
                            artiProxy = proxy
                            currentPort = socksPort
                            lastError = null
                            Log.d("TorService") { "Arti started on port $socksPort" }
                            break
                        } catch (e: Exception) {
                            lastError = e
                            Log.e("TorService") {
                                "Failed to start Arti on port $socksPort (attempt ${attempt + 1}): ${e.message}"
                            }
                            socksPort++
                        }
                    }

                    if (lastError != null) {
                        Log.e("TorService") { "Failed to start Arti after $MAX_PORT_RETRIES attempts" }
                        _status.value = TorServiceStatus.Off
                    }
                }
            }
        }
    }

    suspend fun stop() {
        // NonCancellable ensures stop() completes fully even if the caller
        // is cancelled, preventing leaked ArtiProxy instances and file locks.
        withContext(NonCancellable) {
            mutex.withLock {
                val proxy = artiProxy ?: return@withContext
                artiProxy = null
                bootstrapped.set(false)

                Log.d("TorService", "Stopping Arti")
                withContext(Dispatchers.IO) {
                    // Set up a signal to wait for the "Stopped" log confirmation
                    val signal = CompletableDeferred<Unit>()
                    stoppedSignal = signal

                    try {
                        proxy.stop()
                    } catch (e: Exception) {
                        Log.d("TorService") { "Failed to stop Arti: ${e.message}" }
                    }

                    // Wait for the native layer to confirm stop and release file locks
                    val confirmed = withTimeoutOrNull(STOP_TIMEOUT_MS) { signal.await() }
                    stoppedSignal = null

                    if (confirmed != null) {
                        Log.d("TorService") { "Arti confirmed stopped" }
                    } else {
                        Log.w("TorService") { "Arti stop timed out after ${STOP_TIMEOUT_MS / 1000}s" }
                    }
                }
                _status.value = TorServiceStatus.Off
            }
        }
    }
}
