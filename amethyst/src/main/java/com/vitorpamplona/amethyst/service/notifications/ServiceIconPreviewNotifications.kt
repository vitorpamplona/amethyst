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
package com.vitorpamplona.amethyst.service.notifications

// =====================================================================================
// TEMPORARY / DEBUG-ONLY — REMOVE BEFORE MERGE.
//
// Posts one always-on-style notification per candidate small-icon so the different
// "service icon" designs can be compared side by side in the status bar / shade on a
// real device. These mimic the real NotificationRelayService notification (same
// channel, ongoing, silent, low priority) but each uses a different drawable and a
// label naming the design.
//
// To remove the whole experiment later: delete this file, the four extra drawables
// (amethyst_service2..6), and the single ServiceIconPreviewNotifications.postAll(...)
// call in MainActivity.onCreate.
// =====================================================================================

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vitorpamplona.amethyst.R

object ServiceIconPreviewNotifications {
    private const val CHANNEL_ID = "service_icon_preview"
    private const val BASE_ID = 920_000

    private data class Candidate(
        @DrawableRes val icon: Int,
        val label: String,
    )

    private val candidates =
        listOf(
            Candidate(R.drawable.amethyst_service, "1 · Hollow gem outline (current)"),
            Candidate(R.drawable.amethyst_service2, "2 · Solid circle, gem cut-out"),
            Candidate(R.drawable.amethyst_service3, "3 · Orbit + server nodes"),
            Candidate(R.drawable.amethyst_service4, "4 · Sync arrows (running)"),
            Candidate(R.drawable.amethyst_service5, "5 · Hub & spoke (connected)"),
            Candidate(R.drawable.amethyst_service6, "6 · Broadcast waves"),
        )

    fun postAll(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        ensureChannel(context)

        candidates.forEachIndexed { index, candidate ->
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setContentTitle("Service icon ${index + 1}")
                    .setContentText(candidate.label)
                    .setSmallIcon(candidate.icon)
                    .setOngoing(true)
                    .setSilent(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .build()

            manager.notify(BASE_ID + index, notification)
        }
    }

    fun clearAll(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        candidates.indices.forEach { manager.cancel(BASE_ID + it) }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Service icon preview (debug)",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Temporary side-by-side preview of always-on service icons"
                setShowBadge(false)
            }
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
