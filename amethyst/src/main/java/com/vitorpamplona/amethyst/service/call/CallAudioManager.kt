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

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class CallAudioManager(
    private val context: Context,
) {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var proximityWakeLock: PowerManager.WakeLock? = null

    fun startRinging() {
        startRingtone()
        startVibration()
    }

    fun stopRinging() {
        stopRingtone()
        stopVibration()
    }

    fun acquireProximityWakeLock() {
        if (proximityWakeLock != null) return
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        proximityWakeLock =
            powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "amethyst:call_proximity",
            )
        proximityWakeLock?.acquire(60 * 60 * 1000L) // 1 hour max
    }

    fun releaseProximityWakeLock() {
        proximityWakeLock?.let {
            if (it.isHeld) it.release()
        }
        proximityWakeLock = null
    }

    fun release() {
        stopRinging()
        releaseProximityWakeLock()
    }

    private fun startRingtone() {
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone =
                RingtoneManager.getRingtone(context, ringtoneUri)?.apply {
                    audioAttributes =
                        AudioAttributes
                            .Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    isLooping = true
                    play()
                }
        } catch (_: Exception) {
            // Ringtone may not be available
        }
    }

    private fun stopRingtone() {
        ringtone?.stop()
        ringtone = null
    }

    private fun startVibration() {
        try {
            vibrator =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }

            val pattern = longArrayOf(0, 1000, 1000) // vibrate 1s, pause 1s, repeat
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } catch (_: Exception) {
            // Vibrator may not be available
        }
    }

    private fun stopVibration() {
        vibrator?.cancel()
        vibrator = null
    }
}
