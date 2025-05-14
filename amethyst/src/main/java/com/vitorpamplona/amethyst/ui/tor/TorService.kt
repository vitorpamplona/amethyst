/**
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

import android.content.ComponentName
import android.content.Context
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import org.torproject.jni.TorService
import org.torproject.jni.TorService.LocalBinder

class TorService(
    val context: Context,
) {
    val status =
        callbackFlow {
            Log.d("TorService", "Binding Tor Service")
            trySend(TorServiceStatus.Connecting)

            val currentIntent = Intent(context, TorService::class.java)

            context.bindService(
                currentIntent,
                object : ServiceConnection {
                    override fun onServiceConnected(
                        name: ComponentName,
                        service: IBinder,
                    ) {
                        launch {
                            // moved torService to a local variable, since we only need it once
                            val torService = (service as LocalBinder).service

                            while (torService.socksPort < 0) {
                                delay(100)
                            }

                            trySend(TorServiceStatus.Active(torService.socksPort))
                            Log.d("TorService", "Tor Service Connected ${torService.socksPort}")
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName) {
                        Log.d("TorService", "Tor Service Disconnected")
                        trySend(TorServiceStatus.Off)
                    }
                },
                BIND_AUTO_CREATE,
            )

            awaitClose {
                checkNotInMainThread()
                Log.d("TorService", "Stopping Tor Service")
                launch {
                    context.stopService(currentIntent)
                }
                trySend(TorServiceStatus.Off)
            }
        }.distinctUntilChanged().flowOn(Dispatchers.IO)
}
