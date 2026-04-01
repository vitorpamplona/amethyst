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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private const val DEFAULT_SOCKS_PORT = 19050
private const val MAX_PORT_RETRIES = 3
private const val BOOTSTRAP_TIMEOUT_MS = 120_000L
private const val BOOTSTRAP_CHECK_INTERVAL_MS = 10_000L

class TorService(
    val context: Context,
) {
    val status =
        callbackFlow {
            Log.d("TorService", "Starting Arti Tor Service")
            trySend(TorServiceStatus.Connecting)

            var socksPort = DEFAULT_SOCKS_PORT
            var artiProxy: ArtiProxy? = null
            val bootstrapped = AtomicBoolean(false)
            val lastLogTime = AtomicLong(System.currentTimeMillis())

            val logListener =
                ArtiLogListener { logLine ->
                    val text = logLine ?: return@ArtiLogListener
                    Log.d("TorService") { "Arti: $text" }
                    lastLogTime.set(System.currentTimeMillis())

                    when {
                        text.contains("Sufficiently bootstrapped", ignoreCase = true) ||
                            text.contains("is usable", ignoreCase = true) -> {
                            if (bootstrapped.compareAndSet(false, true)) {
                                trySend(TorServiceStatus.Active(socksPort))
                                Log.d("TorService") { "Arti bootstrapped on port $socksPort" }
                            }
                        }

                        text.contains("state changed to Stopped", ignoreCase = true) -> {
                            bootstrapped.set(false)
                            trySend(TorServiceStatus.Off)
                        }

                        text.contains(
                            "Another process has the lock",
                            ignoreCase = true,
                        ) -> {
                            Log.e("TorService") { "Arti state file lock conflict" }
                            bootstrapped.set(false)
                            trySend(TorServiceStatus.Off)
                        }
                    }
                }

            var lastError: Exception? = null
            for (attempt in 0 until MAX_PORT_RETRIES) {
                try {
                    artiProxy =
                        ArtiProxy
                            .Builder(context.applicationContext)
                            .setSocksPort(socksPort)
                            .setDnsPort(socksPort + 1)
                            .setLogListener(logListener)
                            .build()

                    artiProxy!!.start()
                    lastError = null
                    break
                } catch (e: Exception) {
                    lastError = e
                    Log.e("TorService") {
                        "Failed to start Arti on port $socksPort (attempt ${attempt + 1}): ${e.message}"
                    }
                    // Stop the failed instance before retrying
                    try {
                        artiProxy?.stop()
                    } catch (_: Exception) {
                    }
                    artiProxy = null
                    socksPort++
                }
            }

            if (lastError != null) {
                Log.e("TorService") { "Failed to start Arti after $MAX_PORT_RETRIES attempts" }
                trySend(TorServiceStatus.Off)
            }

            // Monitor for bootstrap stalls: if Arti stops producing logs
            // during bootstrap, restart it.
            val monitorJob =
                launch {
                    val startTime = System.currentTimeMillis()
                    while (!bootstrapped.get()) {
                        delay(BOOTSTRAP_CHECK_INTERVAL_MS)

                        if (bootstrapped.get()) break

                        val elapsed = System.currentTimeMillis() - startTime
                        val timeSinceLastLog = System.currentTimeMillis() - lastLogTime.get()

                        if (elapsed > BOOTSTRAP_TIMEOUT_MS) {
                            Log.w("TorService") {
                                "Arti bootstrap timed out after ${elapsed / 1000}s"
                            }
                            // Stop and restart once
                            try {
                                artiProxy?.stop()
                            } catch (_: Exception) {
                            }

                            delay(1000)
                            try {
                                artiProxy =
                                    ArtiProxy
                                        .Builder(context.applicationContext)
                                        .setSocksPort(socksPort)
                                        .setDnsPort(socksPort + 1)
                                        .setLogListener(logListener)
                                        .build()
                                artiProxy!!.start()
                                lastLogTime.set(System.currentTimeMillis())
                            } catch (e: Exception) {
                                Log.e("TorService") { "Arti restart failed: ${e.message}" }
                                trySend(TorServiceStatus.Off)
                            }
                            break // Only retry once
                        } else if (timeSinceLastLog > BOOTSTRAP_CHECK_INTERVAL_MS * 3) {
                            Log.w("TorService") {
                                "Arti log stall detected (${timeSinceLastLog / 1000}s since last log)"
                            }
                        }
                    }
                }

            awaitClose {
                Log.d("TorService", "Stopping Arti Tor Service")
                monitorJob.cancel()
                try {
                    artiProxy?.stop()
                } catch (e: Exception) {
                    Log.d("TorService") { "Failed to stop Arti: ${e.message}" }
                }
            }
        }.flowOn(Dispatchers.IO)
}
