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
package com.vitorpamplona.amethyst.service.call

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.quartz.utils.Log

private const val TAG = "CallForegroundService"

class CallForegroundService : Service() {
    companion object {
        const val CHANNEL_ID = "amethyst_call_channel"
        const val NOTIFICATION_ID = 9001
        const val ACTION_START = "com.vitorpamplona.amethyst.CALL_START"
        const val ACTION_STOP = "com.vitorpamplona.amethyst.CALL_STOP"
        const val EXTRA_PEER_NAME = "peer_name"
        const val EXTRA_IS_VIDEO = "is_video"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_START -> {
                val peerName = intent.getStringExtra(EXTRA_PEER_NAME) ?: "Unknown"
                val isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)
                val notification = buildNotification(peerName)
                val hasAudioPermission =
                    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                val hasCameraPermission =
                    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
                try {
                    val fgsType =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            var type = 0
                            if (hasAudioPermission) {
                                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                            }
                            if (isVideo && hasCameraPermission) {
                                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                            }
                            type
                        } else {
                            0
                        }
                    ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
                } catch (e: SecurityException) {
                    Log.e(TAG, "Cannot start microphone foreground service, falling back", e)
                    try {
                        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0)
                    } catch (e2: Exception) {
                        Log.e(TAG, "Foreground service start failed entirely", e2)
                        stopSelf()
                    }
                }
            }

            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.call_ongoing),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.call_ongoing_description)
            }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(peerName: String): Notification =
        NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.call_with, peerName))
            .setSmallIcon(R.drawable.amethyst)
            .setOngoing(true)
            .build()
}
