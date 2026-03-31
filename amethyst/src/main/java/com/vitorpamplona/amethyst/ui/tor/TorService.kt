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
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

private const val DEFAULT_SOCKS_PORT = 19050
private const val MAX_PORT_RETRIES = 3

class TorService(
    val context: Context,
) {
    val status =
        callbackFlow {
            Log.d("TorService", "Starting Arti Tor Service")
            trySend(TorServiceStatus.Connecting)

            var socksPort = DEFAULT_SOCKS_PORT
            var artiProxy: ArtiProxy? = null
            var started = false

            val logListener =
                ArtiLogListener { logLine ->
                    val text = logLine ?: return@ArtiLogListener
                    Log.d("TorService") { "Arti: $text" }

                    when {
                        text.contains("Sufficiently bootstrapped", ignoreCase = true) ||
                            text.contains("is usable", ignoreCase = true) -> {
                            if (!started) {
                                started = true
                                trySend(TorServiceStatus.Active(socksPort))
                                Log.d("TorService") { "Arti bootstrapped on port $socksPort" }
                            }
                        }

                        text.contains("state changed to Stopped", ignoreCase = true) -> {
                            started = false
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
                    Log.e("TorService") { "Failed to start Arti on port $socksPort (attempt ${attempt + 1}): ${e.message}" }
                    socksPort++
                }
            }

            if (lastError != null) {
                Log.e("TorService") { "Failed to start Arti after $MAX_PORT_RETRIES attempts" }
                trySend(TorServiceStatus.Off)
            }

            awaitClose {
                Log.d("TorService", "Stopping Arti Tor Service")
                try {
                    artiProxy?.stop()
                } catch (e: Exception) {
                    Log.d("TorService") { "Failed to stop Arti: ${e.message}" }
                }
                trySend(TorServiceStatus.Off)
            }
        }.flowOn(Dispatchers.IO)
}
