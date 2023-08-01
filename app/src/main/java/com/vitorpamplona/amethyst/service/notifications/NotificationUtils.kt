package com.vitorpamplona.amethyst.service.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.executeBlocking
import coil.request.ImageRequest
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.MainActivity

object NotificationUtils {
    private var dmChannel: NotificationChannel? = null
    private var zapChannel: NotificationChannel? = null
    private const val DMS_SUMMARY_ID = 1725900883 // This is the absolute value hashcode of "Amethyst", i.e "Amethyst".hashCode().absoluteValue
    private const val DM_GROUP_KEY = "com.vitorpamplona.amethyst.DM_NOTIFICATION"
    private const val ZAP_GROUP_KEY = "com.vitorpamplona.amethyst.ZAP_NOTIFICATION"

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

        sendNotification(id, messageBody, messageTitle, pictureUrl, uri, channelId, ZAP_GROUP_KEY, applicationContext)
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

        sendNotification(id, messageBody, messageTitle, pictureUrl, uri, channelId, DM_GROUP_KEY, applicationContext)
    }

    fun NotificationManager.sendNotification(
        id: String,
        messageBody: String,
        messageTitle: String,
        pictureUrl: String?,
        uri: String,
        channelId: String,
        notificationGroupKey: String,
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
                notificationGroupKey,
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
                notificationGroupKey,
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
        notificationGroupKey: String,
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
            .useMessagingStyleIf(
                notificationGroupKey == DM_GROUP_KEY,
                picture?.toBitmap(),
                messageTitle,
                messageBody
            )
            .setContentText(applicationContext.getString(R.string.app_notification_private_message))
            .setLargeIcon(picture?.bitmap)
            .setGroup(notificationGroupKey)
            .setGroupSummaryBehaviorIf(notificationGroupKey == DM_GROUP_KEY)
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
            .useMessagingStyleIf(
                notificationGroupKey == DM_GROUP_KEY,
                picture?.toBitmap(),
                messageTitle,
                messageBody
            )
            .setContentText(messageBody)
            .setLargeIcon(picture?.bitmap)
            .setGroup(notificationGroupKey)
            .setGroupSummaryBehaviorIf(notificationGroupKey == DM_GROUP_KEY)
            .setContentIntent(contentPendingIntent)
            .setPublicVersion(builderPublic.build())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        // For getting the notifications grouped by user.
        val userNotifications = notifications.groupBy { activeNotification ->
            activeNotification.notification.extras.getString("android.title")
        }
        val numberOfUsers = userNotifications.keys.filterNotNull().size
        val numberOfDMs = userNotifications.values.sumOf { notificationsPerUser ->
            notificationsPerUser.size
        }

        // Builds the group(summary) notification
        val summaryNotification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.amethyst)
            .setContentText("$numberOfDMs messages from $numberOfUsers users")
            .setAutoCancel(true)
            .setStyle(
                NotificationCompat.InboxStyle()
                    .setSummaryText("$numberOfDMs messages from $numberOfUsers users")
            )
            .setGroup(DM_GROUP_KEY)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroupSummary(true)
            .build()

        notify(notId, builder.build())
        if (notificationGroupKey == DM_GROUP_KEY) {
            notify(DMS_SUMMARY_ID, summaryNotification)
        }
    }

    /**
     * Cancels all notifications.
     */
    fun NotificationManager.cancelNotifications() {
        cancelAll()
    }

    /**
     * Applies the GROUP_ALERT_SUMMARY behaviour if condition is met. In this context,
     * it is very useful for DM notifications, to let only the summary notification show, with
     * the updated info from the individual DM notifications.
     */
    private fun NotificationCompat.Builder.setGroupSummaryBehaviorIf(condition: Boolean): NotificationCompat.Builder {
        if (condition) {
            return setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
        }
        return this
    }

    /**
     * Applies this style to really fit in with the messaging notification aesthetic,
     * when the given condition is met.
     * This applies particularly to DM notifications.
     */
    private fun NotificationCompat.Builder.useMessagingStyleIf(
        condition: Boolean,
        userPicture: Bitmap?,
        messageTitle: String,
        messageBody: String
    ): NotificationCompat.Builder {
        if (condition) {
            val senderInfoBuilder = Person.Builder()
            senderInfoBuilder.setName(messageTitle)
            if (userPicture != null) {
                senderInfoBuilder.setIcon(IconCompat.createWithBitmap(userPicture))
            }
            val sender = senderInfoBuilder.build()
            return setStyle(
                NotificationCompat.MessagingStyle(Person.Builder().setName("Me").build())
                    .addMessage(
                        messageBody,
                        System.currentTimeMillis(),
                        sender
                    )
            )
        }
        return this
    }
}
