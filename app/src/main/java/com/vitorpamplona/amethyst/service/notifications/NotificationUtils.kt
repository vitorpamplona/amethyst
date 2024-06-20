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
package com.vitorpamplona.amethyst.service.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import coil.ImageLoader
import coil.executeBlocking
import coil.request.ImageRequest
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.amethyst.ui.stringRes

object NotificationUtils {
    private var dmChannel: NotificationChannel? = null
    private var zapChannel: NotificationChannel? = null
    private const val DM_GROUP_KEY = "com.vitorpamplona.amethyst.DM_NOTIFICATION"
    private const val ZAP_GROUP_KEY = "com.vitorpamplona.amethyst.ZAP_NOTIFICATION"

    fun NotificationManager.getOrCreateDMChannel(applicationContext: Context): NotificationChannel {
        if (dmChannel != null) return dmChannel!!

        dmChannel =
            NotificationChannel(
                stringRes(applicationContext, R.string.app_notification_dms_channel_id),
                stringRes(applicationContext, R.string.app_notification_dms_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description =
                    stringRes(applicationContext, R.string.app_notification_dms_channel_description)
            }

        // Register the channel with the system
        val notificationManager: NotificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(dmChannel!!)

        return dmChannel!!
    }

    fun NotificationManager.getOrCreateZapChannel(applicationContext: Context): NotificationChannel {
        if (zapChannel != null) return zapChannel!!

        zapChannel =
            NotificationChannel(
                stringRes(applicationContext, R.string.app_notification_zaps_channel_id),
                stringRes(applicationContext, R.string.app_notification_zaps_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description =
                    stringRes(applicationContext, R.string.app_notification_zaps_channel_description)
            }

        // Register the channel with the system
        val notificationManager: NotificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(zapChannel!!)

        return zapChannel!!
    }

    fun NotificationManager.sendZapNotification(
        id: String,
        messageBody: String,
        messageTitle: String,
        pictureUrl: String?,
        uri: String,
        applicationContext: Context,
    ) {
        val zapChannel = getOrCreateZapChannel(applicationContext)
        val channelId = stringRes(applicationContext, R.string.app_notification_zaps_channel_id)

        sendNotification(
            id,
            messageBody,
            messageTitle,
            pictureUrl,
            uri,
            channelId,
            ZAP_GROUP_KEY,
            applicationContext,
        )
    }

    fun NotificationManager.sendDMNotification(
        id: String,
        messageBody: String,
        messageTitle: String,
        pictureUrl: String?,
        uri: String,
        applicationContext: Context,
    ) {
        val dmChannel = getOrCreateDMChannel(applicationContext)
        val channelId = stringRes(applicationContext, R.string.app_notification_dms_channel_id)

        sendNotification(
            id,
            messageBody,
            messageTitle,
            pictureUrl,
            uri,
            channelId,
            DM_GROUP_KEY,
            applicationContext,
        )
    }

    fun NotificationManager.sendNotification(
        id: String,
        messageBody: String,
        messageTitle: String,
        pictureUrl: String?,
        uri: String,
        channelId: String,
        notificationGroupKey: String,
        applicationContext: Context,
    ) {
        if (pictureUrl != null) {
            val request = ImageRequest.Builder(applicationContext).data(pictureUrl).build()

            val imageLoader = ImageLoader(applicationContext)
            val imageResult = imageLoader.executeBlocking(request)
            sendNotification(
                id = id,
                messageBody = messageBody,
                messageTitle = messageTitle,
                picture = imageResult.drawable as? BitmapDrawable,
                uri = uri,
                channelId,
                notificationGroupKey,
                applicationContext = applicationContext,
            )
        } else {
            sendNotification(
                id = id,
                messageBody = messageBody,
                messageTitle = messageTitle,
                picture = null,
                uri = uri,
                channelId,
                notificationGroupKey,
                applicationContext = applicationContext,
            )
        }
    }

    private fun NotificationManager.sendNotification(
        id: String,
        messageBody: String,
        messageTitle: String,
        picture: BitmapDrawable?,
        uri: String,
        channelId: String,
        notificationGroupKey: String,
        applicationContext: Context,
    ) {
        val notId = id.hashCode()

        // dont notify twice
        val notifications: Array<StatusBarNotification> = getActiveNotifications()
        for (notification in notifications) {
            if (notification.id == notId) {
                return
            }
        }

        val contentIntent =
            Intent(applicationContext, MainActivity::class.java).apply { data = Uri.parse(uri) }

        val contentPendingIntent =
            PendingIntent.getActivity(
                applicationContext,
                notId,
                contentIntent,
                PendingIntent.FLAG_MUTABLE,
            )

        // Build the notification
        val builderPublic =
            NotificationCompat
                .Builder(
                    applicationContext,
                    channelId,
                ).setSmallIcon(R.drawable.amethyst)
                .setContentTitle(messageTitle)
                .setContentText(stringRes(applicationContext, R.string.app_notification_private_message))
                .setLargeIcon(picture?.bitmap)
                // .setGroup(messageTitle)
                // .setGroup(notificationGroupKey) //-> Might need a Group summary as well before we
                // activate this
                .setContentIntent(contentPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

        // Build the notification
        val builder =
            NotificationCompat
                .Builder(
                    applicationContext,
                    channelId,
                ).setSmallIcon(R.drawable.amethyst)
                .setContentTitle(messageTitle)
                .setContentText(messageBody)
                .setLargeIcon(picture?.bitmap)
                // .setGroup(messageTitle)
                // .setGroup(notificationGroupKey)  //-> Might need a Group summary as well before we
                // activate this
                .setContentIntent(contentPendingIntent)
                .setPublicVersion(builderPublic.build())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

        notify(notId, builder.build())
    }

    /** Cancels all notifications. */
    fun NotificationManager.cancelNotifications() {
        cancelAll()
    }
}
