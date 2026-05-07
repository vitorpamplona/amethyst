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
package com.vitorpamplona.amethyst.service.scheduledposts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.Event

/**
 * Posts user-visible system notifications when a scheduled post completes
 * (sent or failed). Without this, a worker firing in background offers zero
 * diagnostic to the user — see the silent-publish bug the ack-aware worker
 * now guards against; the notification closes the loop.
 */
object ScheduledPostNotifier {
    private var channel: NotificationChannel? = null
    private const val SCHEDULED_POST_NOT_ID_BASE = 0x70000

    fun notifySent(
        context: Context,
        post: ScheduledPost,
    ) {
        ensureChannel(context)
        post(
            context = context,
            notId = idFor(post.id),
            title = stringRes(context, R.string.scheduled_posts_notification_sent_title),
            body = previewOf(post),
        )
    }

    fun notifyFailed(
        context: Context,
        post: ScheduledPost,
        error: String?,
    ) {
        ensureChannel(context)
        val snippet = previewOf(post)
        val body =
            if (error.isNullOrBlank()) {
                snippet
            } else {
                "$snippet\n${stringRes(context, R.string.scheduled_posts_error_prefix, error)}"
            }
        post(
            context = context,
            notId = idFor(post.id),
            title = stringRes(context, R.string.scheduled_posts_notification_failed_title),
            body = body,
        )
    }

    private fun post(
        context: Context,
        notId: Int,
        title: String,
        body: String,
    ) {
        val channelId = stringRes(context, R.string.app_notification_scheduled_posts_channel_id)
        val tapIntent =
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        val tapPendingIntent =
            PendingIntent.getActivity(
                context,
                notId,
                tapIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val builder =
            NotificationCompat
                .Builder(context, channelId)
                .setSmallIcon(R.drawable.amethyst)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(tapPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
        // Silently no-ops on Android 13+ if POST_NOTIFICATIONS isn't granted.
        NotificationManagerCompat.from(context).notify(notId, builder.build())
    }

    private fun ensureChannel(context: Context) {
        if (channel != null) return
        channel =
            NotificationChannel(
                stringRes(context, R.string.app_notification_scheduled_posts_channel_id),
                stringRes(context, R.string.app_notification_scheduled_posts_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = stringRes(context, R.string.app_notification_scheduled_posts_channel_description)
            }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel!!)
    }

    private fun previewOf(post: ScheduledPost): String =
        runCatching {
            Event
                .fromJson(post.signedEventJson)
                .content
                .take(120)
                .trim()
        }.getOrDefault("")

    // Distinct id per post so multiple completions don't collapse onto one row.
    private fun idFor(postId: String): Int = SCHEDULED_POST_NOT_ID_BASE xor postId.hashCode()
}
