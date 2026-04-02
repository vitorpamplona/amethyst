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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.amethyst.ui.stringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NotificationUtils {
    private var dmChannel: NotificationChannel? = null
    private var zapChannel: NotificationChannel? = null
    private var reactionChannel: NotificationChannel? = null
    private var chessChannel: NotificationChannel? = null
    private var callChannel: NotificationChannel? = null

    private const val DM_GROUP_KEY = "com.vitorpamplona.amethyst.DM_NOTIFICATION"
    private const val ZAP_GROUP_KEY = "com.vitorpamplona.amethyst.ZAP_NOTIFICATION"
    private const val REACTION_GROUP_KEY = "com.vitorpamplona.amethyst.REACTION_NOTIFICATION"
    private const val CHESS_GROUP_KEY = "com.vitorpamplona.amethyst.CHESS_NOTIFICATION"
    private const val CALL_CHANNEL_ID = "com.vitorpamplona.amethyst.CALL_CHANNEL"
    private const val CALL_NOTIFICATION_ID = 0x50000

    const val REPLY_ACTION = "com.vitorpamplona.amethyst.REPLY_ACTION"
    const val MARK_READ_ACTION = "com.vitorpamplona.amethyst.MARK_READ_ACTION"
    const val KEY_REPLY_TEXT = "key_reply_text"
    const val KEY_NOTIFICATION_ID = "key_notification_id"
    const val KEY_ACCOUNT_NPUB = "key_account_npub"
    const val KEY_CHATROOM_MEMBERS = "key_chatroom_members"

    private const val DM_SUMMARY_ID = 0x10000
    private const val ZAP_SUMMARY_ID = 0x20000
    private const val REACTION_SUMMARY_ID = 0x40000
    private const val CHESS_SUMMARY_ID = 0x30000

    fun getOrCreateDMChannel(applicationContext: Context): NotificationChannel {
        if (dmChannel != null) return dmChannel!!

        dmChannel =
            NotificationChannel(
                stringRes(applicationContext, R.string.app_notification_dms_channel_id),
                stringRes(applicationContext, R.string.app_notification_dms_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description =
                    stringRes(applicationContext, R.string.app_notification_dms_channel_description)
            }

        val notificationManager: NotificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(dmChannel!!)

        return dmChannel!!
    }

    fun getOrCreateZapChannel(applicationContext: Context): NotificationChannel {
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

        val notificationManager: NotificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(zapChannel!!)

        return zapChannel!!
    }

    fun getOrCreateReactionChannel(applicationContext: Context): NotificationChannel {
        if (reactionChannel != null) return reactionChannel!!

        reactionChannel =
            NotificationChannel(
                stringRes(applicationContext, R.string.app_notification_reactions_channel_id),
                stringRes(applicationContext, R.string.app_notification_reactions_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description =
                    stringRes(applicationContext, R.string.app_notification_reactions_channel_description)
            }

        val notificationManager: NotificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(reactionChannel!!)

        return reactionChannel!!
    }

    fun getOrCreateChessChannel(applicationContext: Context): NotificationChannel {
        if (chessChannel != null) return chessChannel!!

        chessChannel =
            NotificationChannel(
                stringRes(applicationContext, R.string.app_notification_chess_channel_id),
                stringRes(applicationContext, R.string.app_notification_chess_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description =
                    stringRes(applicationContext, R.string.app_notification_chess_channel_description)
            }

        val notificationManager: NotificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(chessChannel!!)

        return chessChannel!!
    }

    suspend fun NotificationManager.sendReactionNotification(
        id: String,
        messageBody: String,
        messageTitle: String,
        time: Long,
        pictureUrl: String?,
        uri: String,
        applicationContext: Context,
    ) {
        getOrCreateReactionChannel(applicationContext)
        val channelId = stringRes(applicationContext, R.string.app_notification_reactions_channel_id)

        sendNotification(
            id = id,
            messageBody = messageBody,
            messageTitle = messageTitle,
            time = time,
            pictureUrl = pictureUrl,
            uri = uri,
            channelId = channelId,
            notificationGroupKey = REACTION_GROUP_KEY,
            category = NotificationCompat.CATEGORY_SOCIAL,
            summaryId = REACTION_SUMMARY_ID,
            summaryText = stringRes(applicationContext, R.string.app_notification_reactions_summary),
            applicationContext = applicationContext,
        )
    }

    suspend fun NotificationManager.sendChessNotification(
        id: String,
        messageBody: String,
        messageTitle: String,
        time: Long,
        pictureUrl: String?,
        uri: String,
        applicationContext: Context,
    ) {
        getOrCreateChessChannel(applicationContext)
        val channelId = stringRes(applicationContext, R.string.app_notification_chess_channel_id)

        sendNotification(
            id = id,
            messageBody = messageBody,
            messageTitle = messageTitle,
            time = time,
            pictureUrl = pictureUrl,
            uri = uri,
            channelId = channelId,
            notificationGroupKey = CHESS_GROUP_KEY,
            category = NotificationCompat.CATEGORY_SOCIAL,
            summaryId = CHESS_SUMMARY_ID,
            summaryText = stringRes(applicationContext, R.string.app_notification_chess_summary),
            applicationContext = applicationContext,
        )
    }

    suspend fun NotificationManager.sendZapNotification(
        id: String,
        messageBody: String,
        messageTitle: String,
        time: Long,
        pictureUrl: String?,
        uri: String,
        applicationContext: Context,
    ) {
        getOrCreateZapChannel(applicationContext)
        val channelId = stringRes(applicationContext, R.string.app_notification_zaps_channel_id)

        sendNotification(
            id = id,
            messageBody = messageBody,
            messageTitle = messageTitle,
            time = time,
            pictureUrl = pictureUrl,
            uri = uri,
            channelId = channelId,
            notificationGroupKey = ZAP_GROUP_KEY,
            category = NotificationCompat.CATEGORY_SOCIAL,
            summaryId = ZAP_SUMMARY_ID,
            summaryText = stringRes(applicationContext, R.string.app_notification_zaps_summary),
            applicationContext = applicationContext,
        )
    }

    suspend fun NotificationManager.sendDMNotification(
        id: String,
        messageBody: String,
        senderName: String,
        time: Long,
        pictureUrl: String?,
        uri: String,
        applicationContext: Context,
        accountNpub: String? = null,
        accountPictureUrl: String? = null,
        chatroomMembers: String? = null,
    ) {
        getOrCreateDMChannel(applicationContext)
        val channelId = stringRes(applicationContext, R.string.app_notification_dms_channel_id)

        sendDMNotificationStyled(
            id = id,
            messageBody = messageBody,
            senderName = senderName,
            time = time,
            pictureUrl = pictureUrl,
            uri = uri,
            channelId = channelId,
            applicationContext = applicationContext,
            accountNpub = accountNpub,
            accountPictureUrl = accountPictureUrl,
            chatroomMembers = chatroomMembers,
        )
    }

    private suspend fun loadBitmap(
        pictureUrl: String,
        applicationContext: Context,
    ): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(applicationContext).data(pictureUrl).build()
                val imageLoader = ImageLoader(applicationContext)
                val result = imageLoader.execute(request)
                (result.image?.asDrawable(applicationContext.resources) as? BitmapDrawable)?.bitmap
            } catch (_: Exception) {
                null
            }
        }

    private suspend fun NotificationManager.sendDMNotificationStyled(
        id: String,
        messageBody: String,
        senderName: String,
        time: Long,
        pictureUrl: String?,
        uri: String,
        channelId: String,
        applicationContext: Context,
        accountNpub: String?,
        accountPictureUrl: String?,
        chatroomMembers: String?,
    ) {
        val notId = id.hashCode()

        if (isDuplicate(notId)) return

        val bitmap = pictureUrl?.let { loadBitmap(it, applicationContext) }
        val accountBitmap = accountPictureUrl?.let { loadBitmap(it, applicationContext) }

        val senderIcon = bitmap?.let { IconCompat.createWithBitmap(it) }
        val accountIcon = accountBitmap?.let { IconCompat.createWithBitmap(it) }

        val sender =
            Person
                .Builder()
                .setName(senderName)
                .apply { senderIcon?.let { setIcon(it) } }
                .build()

        val messagingStyle =
            NotificationCompat
                .MessagingStyle(
                    Person
                        .Builder()
                        .setName("Me")
                        .setIcon(accountIcon)
                        .build(),
                ).addMessage(messageBody, time * 1000, sender)

        val contentIntent =
            Intent(applicationContext, MainActivity::class.java).apply { data = uri.toUri() }

        val contentPendingIntent =
            PendingIntent.getActivity(
                applicationContext,
                notId,
                contentIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val builderPublic =
            NotificationCompat
                .Builder(applicationContext, channelId)
                .setSmallIcon(R.drawable.amethyst)
                .setContentTitle(senderName)
                .setContentText(stringRes(applicationContext, R.string.app_notification_private_message))
                .setLargeIcon(bitmap)
                .setContentIntent(contentPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setWhen(time * 1000)

        val builder =
            NotificationCompat
                .Builder(applicationContext, channelId)
                .setSmallIcon(R.drawable.amethyst)
                .setLargeIcon(bitmap)
                .setStyle(messagingStyle)
                .setContentIntent(contentPendingIntent)
                .setPublicVersion(builderPublic.build())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setGroup(DM_GROUP_KEY)
                .setAutoCancel(true)
                .setWhen(time * 1000)

        // Direct Reply action
        if (accountNpub != null && chatroomMembers != null) {
            val remoteInput =
                RemoteInput
                    .Builder(KEY_REPLY_TEXT)
                    .setLabel(stringRes(applicationContext, R.string.app_notification_reply_label))
                    .build()

            val replyIntent =
                Intent(applicationContext, NotificationReplyReceiver::class.java).apply {
                    action = REPLY_ACTION
                    putExtra(KEY_NOTIFICATION_ID, notId)
                    putExtra(KEY_ACCOUNT_NPUB, accountNpub)
                    putExtra(KEY_CHATROOM_MEMBERS, chatroomMembers)
                }

            val replyPendingIntent =
                PendingIntent.getBroadcast(
                    applicationContext,
                    notId,
                    replyIntent,
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )

            val replyAction =
                NotificationCompat.Action
                    .Builder(R.drawable.amethyst, stringRes(applicationContext, R.string.app_notification_reply_label), replyPendingIntent)
                    .addRemoteInput(remoteInput)
                    .setAllowGeneratedReplies(true)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                    .build()

            builder.addAction(replyAction)
        }

        // Mark as Read action
        val markReadIntent =
            Intent(applicationContext, NotificationReplyReceiver::class.java).apply {
                action = MARK_READ_ACTION
                putExtra(KEY_NOTIFICATION_ID, notId)
            }

        val markReadPendingIntent =
            PendingIntent.getBroadcast(
                applicationContext,
                notId + 1,
                markReadIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val markReadAction =
            NotificationCompat.Action
                .Builder(R.drawable.amethyst, stringRes(applicationContext, R.string.app_notification_mark_read_label), markReadPendingIntent)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                .build()

        builder.addAction(markReadAction)

        notify(notId, builder.build())

        // Group summary notification
        sendGroupSummary(channelId, DM_GROUP_KEY, DM_SUMMARY_ID, stringRes(applicationContext, R.string.app_notification_dms_summary), applicationContext)
    }

    private suspend fun NotificationManager.sendNotification(
        id: String,
        messageBody: String,
        messageTitle: String,
        time: Long,
        pictureUrl: String?,
        uri: String,
        channelId: String,
        notificationGroupKey: String,
        category: String,
        summaryId: Int,
        summaryText: String,
        applicationContext: Context,
    ) {
        val notId = id.hashCode()

        if (isDuplicate(notId)) return

        val bitmap = pictureUrl?.let { loadBitmap(it, applicationContext) }

        val contentIntent =
            Intent(applicationContext, MainActivity::class.java).apply { data = uri.toUri() }

        val contentPendingIntent =
            PendingIntent.getActivity(
                applicationContext,
                notId,
                contentIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val builderPublic =
            NotificationCompat
                .Builder(applicationContext, channelId)
                .setSmallIcon(R.drawable.amethyst)
                .setContentTitle(messageTitle)
                .setContentText(stringRes(applicationContext, R.string.app_notification_private_message))
                .setLargeIcon(bitmap)
                .setContentIntent(contentPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setWhen(time * 1000)

        val builder =
            NotificationCompat
                .Builder(applicationContext, channelId)
                .setSmallIcon(R.drawable.amethyst)
                .setContentTitle(messageTitle)
                .setContentText(messageBody)
                .setStyle(NotificationCompat.BigTextStyle().bigText(messageBody))
                .setLargeIcon(bitmap)
                .setContentIntent(contentPendingIntent)
                .setPublicVersion(builderPublic.build())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(category)
                .setGroup(notificationGroupKey)
                .setAutoCancel(true)
                .setWhen(time * 1000)

        notify(notId, builder.build())

        sendGroupSummary(channelId, notificationGroupKey, summaryId, summaryText, applicationContext)
    }

    private fun NotificationManager.sendGroupSummary(
        channelId: String,
        groupKey: String,
        summaryId: Int,
        summaryText: String,
        applicationContext: Context,
    ) {
        val activeCount = activeNotifications.count { it.notification.group == groupKey && it.id != summaryId }

        if (activeCount < 2) return

        val summaryBuilder =
            NotificationCompat
                .Builder(applicationContext, channelId)
                .setSmallIcon(R.drawable.amethyst)
                .setGroup(groupKey)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setStyle(
                    NotificationCompat
                        .InboxStyle()
                        .setSummaryText(summaryText),
                )

        notify(summaryId, summaryBuilder.build())
    }

    fun getOrCreateCallChannel(applicationContext: Context): NotificationChannel {
        if (callChannel != null) return callChannel!!

        callChannel =
            NotificationChannel(
                CALL_CHANNEL_ID,
                stringRes(applicationContext, R.string.app_notification_calls_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = stringRes(applicationContext, R.string.app_notification_calls_channel_description)
            }

        val notificationManager: NotificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(callChannel!!)

        return callChannel!!
    }

    fun sendCallNotification(
        callerName: String,
        callerBitmap: Bitmap?,
        uri: String,
        applicationContext: Context,
    ) {
        val notificationManager: NotificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = getOrCreateCallChannel(applicationContext)

        val contentIntent =
            Intent(applicationContext, MainActivity::class.java).apply { data = uri.toUri() }

        val contentPendingIntent =
            PendingIntent.getActivity(
                applicationContext,
                CALL_NOTIFICATION_ID,
                contentIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val fullScreenIntent =
            Intent(applicationContext, MainActivity::class.java).apply {
                data = uri.toUri()
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

        val fullScreenPendingIntent =
            PendingIntent.getActivity(
                applicationContext,
                CALL_NOTIFICATION_ID + 1,
                fullScreenIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val builder =
            NotificationCompat
                .Builder(applicationContext, channel.id)
                .setSmallIcon(R.drawable.amethyst)
                .setContentTitle(stringRes(applicationContext, R.string.call_incoming))
                .setContentText(callerName)
                .setLargeIcon(callerBitmap)
                .setContentIntent(contentPendingIntent)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setAutoCancel(true)
                .setOngoing(true)
                .setTimeoutAfter(60_000)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(R.drawable.amethyst, stringRes(applicationContext, R.string.call_reject), contentPendingIntent)
                .addAction(R.drawable.amethyst, stringRes(applicationContext, R.string.call_accept), fullScreenPendingIntent)

        notificationManager.notify("call", CALL_NOTIFICATION_ID, builder.build())
    }

    fun cancelCallNotification(applicationContext: Context) {
        val notificationManager: NotificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel("call", CALL_NOTIFICATION_ID)
    }

    private fun NotificationManager.isDuplicate(notId: Int): Boolean {
        val notifications: Array<StatusBarNotification> = activeNotifications
        for (notification in notifications) {
            if (notification.id == notId) {
                return true
            }
        }
        return false
    }

    /** Cancels all notifications. */
    fun NotificationManager.cancelNotifications() {
        cancelAll()
    }
}
