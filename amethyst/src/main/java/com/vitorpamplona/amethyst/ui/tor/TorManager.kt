/**
 * Copyright (c) 2024 Vitor Pamplona
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
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.appcompat.app.AppCompatActivity.BIND_AUTO_CREATE
import com.vitorpamplona.amethyst.service.okhttp.HttpClientManager
import org.torproject.jni.TorService
import org.torproject.jni.TorService.LocalBinder
import java.util.concurrent.atomic.AtomicBoolean

object TorManager {
    var runningIntent: Intent? = null
    var torService: TorService? = null

    // To make sure we don't start two services.
    var isConnectingMutex = AtomicBoolean(false)

    fun startTorIfNotAlreadyOn(ctx: Context) {
        if (runningIntent == null && isConnectingMutex.compareAndSet(false, true)) {
            try {
                startTor(ctx)
            } finally {
                isConnectingMutex.set(false)
            }
        }
    }

    fun stopTor(ctx: Context) {
        Log.d("TorManager", "Stopping Tor Service")
        runningIntent?.let {
            ctx.stopService(runningIntent)
        }
        runningIntent = null
        torService = null
    }

    private fun startTor(ctx: Context) {
        Log.d("TorManager", "Binding Tor Service")
        val currentIntent = Intent(ctx, TorService::class.java)
        runningIntent = currentIntent
        ctx.bindService(
            currentIntent,
            object : ServiceConnection {
                override fun onServiceConnected(
                    name: ComponentName,
                    service: IBinder,
                ) {
                    // moved torService to a local variable, since we only need it once
                    torService = (service as LocalBinder).service

                    while (!isSocksReady()) {
                        try {
                            Thread.sleep(100)
                        } catch (e: InterruptedException) {
                            e.printStackTrace()
                        }
                    }

                    HttpClientManager.setDefaultProxyOnPort(socksPort())

                    Log.d("TorManager", "Tor Service Connected ${socksPort()}")
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    runningIntent = null
                    torService = null
                    Log.d("TorManager", "Tor Service Disconected")
                }
            },
            BIND_AUTO_CREATE,
        )
    }

    fun isSocksReady() =
        torService?.let {
            it.socksPort > 0
        } ?: false

    fun socksPort(): Int = torService?.socksPort ?: 9050

    fun httpPort(): Int = torService?.httpTunnelPort ?: 9050
}
