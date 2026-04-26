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
package com.vitorpamplona.amethyst.service.audiorooms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.MainActivity

/**
 * Process-anchor for an active audio-room session. Holds a partial wake-lock
 * and a foreground notification so playback continues when the screen is off
 * or the user briefly leaves the app.
 *
 * Scope decisions:
 *   - The service does NOT own the MoQ session / decoder / player. Those
 *     live in `AudioRoomViewModel`. This service exists to keep the process
 *     alive while audio is in flight; the VM still drives all wire activity.
 *   - Foreground type is `mediaPlayback`; if the user starts broadcasting,
 *     the screen calls [promoteToMicrophone] which re-`startForeground`s
 *     with `mediaPlayback|microphone` (Android 14+ requires the explicit
 *     microphone type while the mic is open).
 *   - One service per process — multiple audio-room screens are not a real
 *     scenario today (there's one room per screen and the room screen is a
 *     full-screen activity). The service uses a shared notification id so
 *     start/promote/stop is idempotent.
 */
class AudioRoomForegroundService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var promoted = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "amethyst:audio-room")
                .apply { setReferenceCounted(false) }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // Always call startForeground first — Android's contract is that
        // every onStartCommand for a service started via
        // startForegroundService MUST call startForeground within 5 s, on
        // every invocation (audit Android #8). Even the STOP path needs a
        // foreground state before stopForeground can demote it cleanly.
        val mic =
            when (intent?.action) {
                ACTION_PROMOTE_TO_MIC -> true
                else -> promoted
            }
        runCatching { startForegroundWithType(includeMic = mic) }
            .onFailure {
                // foreground-not-allowed (mic-only restrictions, etc.) —
                // bail cleanly rather than leak a wake-lock / partial state.
                stopSelf()
                return START_NOT_STICKY
            }

        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (wakeLock?.isHeld != true) {
            wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
        }
        return START_STICKY
    }

    private fun startForegroundWithType(includeMic: Boolean) {
        promoted = includeMic
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val type =
                if (includeMic) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val stopIntent =
            PendingIntent.getService(
                this,
                1,
                Intent(this, AudioRoomForegroundService::class.java).apply { action = ACTION_STOP },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val title =
            if (promoted) {
                getString(R.string.audio_room_notification_broadcasting)
            } else {
                getString(R.string.audio_room_notification_listening)
            }

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(getString(R.string.audio_room_notification_text))
            .setSmallIcon(R.drawable.amethyst)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.audio_room_notification_stop), stopIntent)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
    }

    private fun createNotificationChannel() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.audio_room_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = getString(R.string.audio_room_notification_channel_description)
                    setShowBadge(false)
                },
            )
        }
    }

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "audio_room_foreground"
        private const val NOTIFICATION_ID = 0xA0D10
        private const val ACTION_PROMOTE_TO_MIC = "com.vitorpamplona.amethyst.audio_room.PROMOTE_MIC"
        private const val ACTION_STOP = "com.vitorpamplona.amethyst.audio_room.STOP"

        // 4-hour hard cap — long enough for typical audio-room sessions
        // (2-3 h podcasts / panels) plus headroom, short enough to release
        // the device from a stuck connection that fails to detect a
        // network drop. Refresh on each ACTION_PROMOTE_TO_MIC arrival.
        private const val WAKE_LOCK_TIMEOUT_MS = 4L * 60 * 60 * 1000

        /** Start the service in listener-only foreground mode. Idempotent. */
        fun startListening(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AudioRoomForegroundService::class.java),
            )
        }

        /**
         * Re-`startForeground` with mediaPlayback + microphone type while the
         * user is broadcasting. Required by Android 14's split foreground-type
         * permission model.
         */
        fun promoteToMicrophone(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AudioRoomForegroundService::class.java).apply {
                    action = ACTION_PROMOTE_TO_MIC
                },
            )
        }

        /** Stop and remove the foreground notification. Idempotent. */
        fun stop(context: Context) {
            context.stopService(Intent(context, AudioRoomForegroundService::class.java))
        }
    }
}
