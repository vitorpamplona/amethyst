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

object NotificationUtils {
    private var dmChannel: NotificationChannel? = null
    private var zapChannel: NotificationChannel? = null

    private fun getOrCreateDMChannel(applicationContext: Context): NotificationChannel {
        if (dmChannel != null) return dmChannel!!

        dmChannel = NotificationChannel(
            applicationContext.getString(R.string.app_notification_dms_channel_id),
            applicationContext.getString(R.string.app_notification_dms_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = applicationContext.getString(R.string.app_notification_dms_channel_description)
        }

        // Register the channel with the system
        val notificationManager: NotificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(dmChannel!!)

        return dmChannel!!
    }

    private fun getOrCreateZapChannel(applicationContext: Context): NotificationChannel {
        if (zapChannel != null) return zapChannel!!

        zapChannel = NotificationChannel(
            applicationContext.getString(R.string.app_notification_zaps_channel_id),
            applicationContext.getString(R.string.app_notification_zaps_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = applicationContext.getString(R.string.app_notification_zaps_channel_description)
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
        applicationContext: Context
    ) {
        val zapChannel = getOrCreateZapChannel(applicationContext)
        val channelId = applicationContext.getString(R.string.app_notification_zaps_channel_id)

        sendNotification(id, messageBody, messageTitle, pictureUrl, uri, channelId, applicationContext)
    }

    fun NotificationManager.sendDMNotification(
        id: String,
        messageBody: String,
        messageTitle: String,
        pictureUrl: String?,
        uri: String,
        applicationContext: Context
    ) {
        val dmChannel = getOrCreateDMChannel(applicationContext)
        val channelId = applicationContext.getString(R.string.app_notification_dms_channel_id)

        sendNotification(id, messageBody, messageTitle, pictureUrl, uri, channelId, applicationContext)
    }

    fun NotificationManager.sendNotification(
        id: String,
        messageBody: String,
        messageTitle: String,
        pictureUrl: String?,
        uri: String,
        channelId: String,
        applicationContext: Context
    ) {
        if (pictureUrl != null) {
            val request = ImageRequest.Builder(applicationContext)
                .data(pictureUrl)
                .build()

            val imageLoader = ImageLoader(applicationContext)
            val imageResult = imageLoader.executeBlocking(request)
            sendNotification(
                id = id,
                messageBody = messageBody,
                messageTitle = messageTitle,
                picture = imageResult.drawable as? BitmapDrawable,
                uri = uri,
                channelId,
                applicationContext = applicationContext
            )
        } else {
            sendNotification(
                id = id,
                messageBody = messageBody,
                messageTitle = messageTitle,
                picture = null,
                uri = uri,
                channelId,
                applicationContext = applicationContext
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
        applicationContext: Context
    ) {
        val notId = id.hashCode()

        // dont notify twice
        val notifications: Array<StatusBarNotification> = getActiveNotifications()
        for (notification in notifications) {
            if (notification.id == notId) {
                return
            }
        }

        val contentIntent = Intent(applicationContext, MainActivity::class.java).apply {
            data = Uri.parse(uri)
        }

        val contentPendingIntent = PendingIntent.getActivity(
            applicationContext,
            notId,
            contentIntent,
            PendingIntent.FLAG_MUTABLE
        )

        // Build the notification
        val builderPublic = NotificationCompat.Builder(
            applicationContext,
            channelId
        )
            .setSmallIcon(R.drawable.amethyst)
            .setContentTitle(messageTitle)
            .setContentText(applicationContext.getString(R.string.app_notification_private_message))
            .setLargeIcon(picture?.bitmap)
            .setGroup(messageTitle)
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        // Build the notification
        val builder = NotificationCompat.Builder(
            applicationContext,
            channelId
        )
            .setSmallIcon(R.drawable.amethyst)
            .setContentTitle(messageTitle)
            .setContentText(messageBody)
            .setLargeIcon(picture?.bitmap)
            .setGroup(messageTitle)
            .setContentIntent(contentPendingIntent)
            .setPublicVersion(builderPublic.build())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notify(notId, builder.build())
    }

    /**
     * Cancels all notifications.
     */
    fun NotificationManager.cancelNotifications() {
        cancelAll()
    }
}
