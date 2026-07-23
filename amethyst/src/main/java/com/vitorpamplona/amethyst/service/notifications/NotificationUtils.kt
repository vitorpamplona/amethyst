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

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * Low-level tray-notification builder. Every per-kind renderer funnels through
 * [postStandard] (BigText / BigPicture) or [postConversation] (MessagingStyle),
 * both driven by a [NotificationCategory] that supplies the channel, accent
 * color, status-bar icon, group, and summary.
 *
 * Notifications are keyed by `id.hashCode()` (the triggering event id) so that
 * (a) reading the underlying post in-app clears the tray entry via
 * [dismissNotificationForEvent], and (b) re-posting the same id from the
 * enrichment path replaces the notification in place. `setOnlyAlertOnce(true)`
 * keeps those replacements silent.
 */
object NotificationUtils {
    const val REPLY_ACTION = "com.vitorpamplona.amethyst.REPLY_ACTION"
    const val PUBLIC_REPLY_ACTION = "com.vitorpamplona.amethyst.PUBLIC_REPLY_ACTION"
    const val MARMOT_REPLY_ACTION = "com.vitorpamplona.amethyst.MARMOT_REPLY_ACTION"
    const val MARK_READ_ACTION = "com.vitorpamplona.amethyst.MARK_READ_ACTION"
    const val KEY_REPLY_TEXT = "key_reply_text"
    const val KEY_NOTIFICATION_ID = "key_notification_id"
    const val KEY_ACCOUNT_NPUB = "key_account_npub"
    const val KEY_CHATROOM_MEMBERS = "key_chatroom_members"
    const val KEY_TARGET_EVENT_ID = "key_target_event_id"
    const val KEY_MARMOT_GROUP_ID = "key_marmot_group_id"
    const val KEY_MARMOT_REPLY_TO_INNER_ID = "key_marmot_reply_to_inner_id"
    const val KEY_MARMOT_REPLY_TO_INNER_AUTHOR = "key_marmot_reply_to_inner_author"

    const val REPLY_GROUP_KEY_PREFIX = "com.vitorpamplona.amethyst.REPLY_NOTIFICATION"
    private const val REPLY_SUMMARY_ID_BASE = 0x50000

    /**
     * Derives a stable summary notification id for a per-thread reply group.
     * Uses the thread root id hash mixed with the base id so different threads
     * don't collide with each other or with the other channel summaries.
     */
    fun replySummaryIdFor(threadRootId: String): Int = REPLY_SUMMARY_ID_BASE xor threadRootId.hashCode()

    fun replyGroupKeyFor(threadRootId: String): String = "$REPLY_GROUP_KEY_PREFIX:$threadRootId"

    /**
     * Payload for wiring a RemoteInput-powered inline reply action onto a public
     * note notification. The receiver resolves the target event from LocalCache
     * via [targetEventId] and signs the appropriate kind (1 for NIP-10, 1111
     * for NIP-22) under the account identified by [accountNpub].
     */
    data class InlineReplyTarget(
        val accountNpub: String,
        val targetEventId: String,
    )

    /**
     * Wiring for a direct-message / group inline reply action. One of the three
     * shapes routes to the matching action in [NotificationReplyReceiver].
     */
    sealed interface ReplyAction {
        data class Dm(
            val accountNpub: String,
            val chatroomMembers: String,
        ) : ReplyAction

        data class Marmot(
            val accountNpub: String,
            val nostrGroupId: String,
            val replyToInnerEventId: String?,
            val replyToInnerAuthor: String?,
        ) : ReplyAction
    }

    /** A prior message rendered above the main one in a MessagingStyle notification (thread context). */
    data class ParentMessage(
        val senderName: String,
        val body: String,
        val pictureUrl: String?,
    )

    // ---------------------------------------------------------------------
    // Standard notification (BigText, or BigPicture when [bigPictureUrl] set)
    // ---------------------------------------------------------------------

    suspend fun NotificationManager.postStandard(
        category: NotificationCategory,
        id: String,
        messageTitle: String,
        messageBody: String,
        time: Long,
        pictureUrl: String?,
        uri: String,
        applicationContext: Context,
        bigPictureUrl: String? = null,
        badgeUrl: String? = null,
        inlineReply: InlineReplyTarget? = null,
        groupKey: String = category.group,
        summaryId: Int = category.summaryId,
    ) {
        val channelId = category.ensureChannel(applicationContext)
        val notId = id.hashCode()

        val avatar = pictureUrl?.let { loadBitmap(it, applicationContext) }?.let { circleCrop(it) }
        val largeIcon = badgeUrl?.let { overlayBadge(avatar, it, applicationContext) } ?: avatar
        val bigPicture = bigPictureUrl?.let { loadBitmap(it, applicationContext) }

        val contentPendingIntent = contentIntent(applicationContext, notId, uri)

        val builderPublic =
            NotificationCompat
                .Builder(applicationContext, channelId)
                .setSmallIcon(category.smallIcon)
                .setColor(category.color)
                .setContentTitle(messageTitle)
                .setContentText(stringRes(applicationContext, R.string.app_notification_private_message))
                .setContentIntent(contentPendingIntent)
                .setPriority(category.priority())
                .setAutoCancel(true)
                .setWhen(time * 1000)

        val builder =
            NotificationCompat
                .Builder(applicationContext, channelId)
                .setSmallIcon(category.smallIcon)
                .setColor(category.color)
                .setContentTitle(messageTitle)
                .setContentText(messageBody)
                .setLargeIcon(largeIcon)
                .setContentIntent(contentPendingIntent)
                .setPublicVersion(builderPublic.build())
                .setPriority(category.priority())
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setGroup(groupKey)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setWhen(time * 1000)

        if (category.colorized) builder.setColorized(true)

        if (bigPicture != null) {
            builder.setStyle(
                NotificationCompat
                    .BigPictureStyle()
                    .bigPicture(bigPicture)
                    .bigLargeIcon(null as Bitmap?),
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(messageBody))
        }

        if (inlineReply != null) {
            builder.addAction(publicReplyAction(applicationContext, notId, inlineReply))
        }

        notify(notId, builder.build())
        sendGroupSummary(category, groupKey, summaryId, applicationContext)
    }

    // ---------------------------------------------------------------------
    // Conversation notification (MessagingStyle: DMs, replies, group chat)
    // ---------------------------------------------------------------------

    suspend fun NotificationManager.postConversation(
        category: NotificationCategory,
        id: String,
        senderName: String,
        pictureUrl: String?,
        messageBody: String,
        time: Long,
        uri: String,
        applicationContext: Context,
        accountPictureUrl: String? = null,
        parent: ParentMessage? = null,
        replyAction: ReplyAction? = null,
        publicInlineReply: InlineReplyTarget? = null,
        addMarkRead: Boolean = true,
        groupKey: String = category.group,
        summaryId: Int = category.summaryId,
    ) {
        val channelId = category.ensureChannel(applicationContext)
        val notId = id.hashCode()

        val avatar = pictureUrl?.let { loadBitmap(it, applicationContext) }?.let { circleCrop(it) }
        val accountAvatar = accountPictureUrl?.let { loadBitmap(it, applicationContext) }?.let { circleCrop(it) }

        val sender =
            Person
                .Builder()
                .setName(senderName)
                .apply { avatar?.let { setIcon(IconCompat.createWithBitmap(it)) } }
                .build()

        val me =
            Person
                .Builder()
                .setName(stringRes(applicationContext, R.string.app_notification_me))
                .apply { accountAvatar?.let { setIcon(IconCompat.createWithBitmap(it)) } }
                .build()

        val messagingStyle = NotificationCompat.MessagingStyle(me)

        if (parent != null) {
            val parentAvatar = parent.pictureUrl?.let { loadBitmap(it, applicationContext) }?.let { circleCrop(it) }
            val parentSender =
                Person
                    .Builder()
                    .setName(parent.senderName)
                    .apply { parentAvatar?.let { setIcon(IconCompat.createWithBitmap(it)) } }
                    .build()
            messagingStyle.addMessage(parent.body, (time - 1) * 1000, parentSender)
        }
        messagingStyle.addMessage(messageBody, time * 1000, sender)

        val contentPendingIntent = contentIntent(applicationContext, notId, uri)

        val builderPublic =
            NotificationCompat
                .Builder(applicationContext, channelId)
                .setSmallIcon(category.smallIcon)
                .setColor(category.color)
                .setContentTitle(senderName)
                .setContentText(stringRes(applicationContext, R.string.app_notification_private_message))
                .setLargeIcon(avatar)
                .setContentIntent(contentPendingIntent)
                .setPriority(category.priority())
                .setAutoCancel(true)
                .setWhen(time * 1000)

        val builder =
            NotificationCompat
                .Builder(applicationContext, channelId)
                .setSmallIcon(category.smallIcon)
                .setColor(category.color)
                .setLargeIcon(avatar)
                .setStyle(messagingStyle)
                .setContentIntent(contentPendingIntent)
                .setPublicVersion(builderPublic.build())
                .setPriority(category.priority())
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setGroup(groupKey)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setWhen(time * 1000)

        when (replyAction) {
            is ReplyAction.Dm -> builder.addAction(dmReplyAction(applicationContext, notId, replyAction))
            is ReplyAction.Marmot -> builder.addAction(marmotReplyAction(applicationContext, notId, replyAction))
            null -> publicInlineReply?.let { builder.addAction(publicReplyAction(applicationContext, notId, it)) }
        }

        if (addMarkRead) builder.addAction(markReadAction(applicationContext, notId))

        notify(notId, builder.build())
        sendGroupSummary(category, groupKey, summaryId, applicationContext)
    }

    // ---------------------------------------------------------------------
    // Intents & actions
    // ---------------------------------------------------------------------

    private fun contentIntent(
        applicationContext: Context,
        notId: Int,
        uri: String,
    ): PendingIntent {
        val contentIntent =
            Intent(applicationContext, MainActivity::class.java).apply { data = uri.toUri() }
        return PendingIntent.getActivity(
            applicationContext,
            notId,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun replyRemoteInput(applicationContext: Context): RemoteInput =
        RemoteInput
            .Builder(KEY_REPLY_TEXT)
            .setLabel(stringRes(applicationContext, R.string.app_notification_reply_label))
            .build()

    private fun buildReplyAction(
        applicationContext: Context,
        notId: Int,
        intent: Intent,
    ): NotificationCompat.Action {
        val replyPendingIntent =
            PendingIntent.getBroadcast(
                applicationContext,
                notId,
                intent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return NotificationCompat.Action
            .Builder(R.drawable.ic_notif_reply, stringRes(applicationContext, R.string.app_notification_reply_label), replyPendingIntent)
            .addRemoteInput(replyRemoteInput(applicationContext))
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .build()
    }

    private fun dmReplyAction(
        applicationContext: Context,
        notId: Int,
        action: ReplyAction.Dm,
    ): NotificationCompat.Action {
        val intent =
            Intent(applicationContext, NotificationReplyReceiver::class.java).apply {
                this.action = REPLY_ACTION
                putExtra(KEY_NOTIFICATION_ID, notId)
                putExtra(KEY_ACCOUNT_NPUB, action.accountNpub)
                putExtra(KEY_CHATROOM_MEMBERS, action.chatroomMembers)
            }
        return buildReplyAction(applicationContext, notId, intent)
    }

    private fun marmotReplyAction(
        applicationContext: Context,
        notId: Int,
        action: ReplyAction.Marmot,
    ): NotificationCompat.Action {
        val intent =
            Intent(applicationContext, NotificationReplyReceiver::class.java).apply {
                this.action = MARMOT_REPLY_ACTION
                putExtra(KEY_NOTIFICATION_ID, notId)
                putExtra(KEY_ACCOUNT_NPUB, action.accountNpub)
                putExtra(KEY_MARMOT_GROUP_ID, action.nostrGroupId)
                action.replyToInnerEventId?.let { putExtra(KEY_MARMOT_REPLY_TO_INNER_ID, it) }
                action.replyToInnerAuthor?.let { putExtra(KEY_MARMOT_REPLY_TO_INNER_AUTHOR, it) }
            }
        return buildReplyAction(applicationContext, notId, intent)
    }

    private fun publicReplyAction(
        applicationContext: Context,
        notId: Int,
        target: InlineReplyTarget,
    ): NotificationCompat.Action {
        val intent =
            Intent(applicationContext, NotificationReplyReceiver::class.java).apply {
                action = PUBLIC_REPLY_ACTION
                putExtra(KEY_NOTIFICATION_ID, notId)
                putExtra(KEY_ACCOUNT_NPUB, target.accountNpub)
                putExtra(KEY_TARGET_EVENT_ID, target.targetEventId)
            }
        return buildReplyAction(applicationContext, notId, intent)
    }

    private fun markReadAction(
        applicationContext: Context,
        notId: Int,
    ): NotificationCompat.Action {
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
        return NotificationCompat.Action
            .Builder(R.drawable.ic_notif_message, stringRes(applicationContext, R.string.app_notification_mark_read_label), markReadPendingIntent)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .build()
    }

    // ---------------------------------------------------------------------
    // Bitmap helpers
    // ---------------------------------------------------------------------

    private suspend fun loadBitmap(
        pictureUrl: String,
        applicationContext: Context,
    ): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    ImageRequest
                        .Builder(applicationContext)
                        .data(pictureUrl)
                        .allowHardware(false)
                        .build()
                val imageLoader = SingletonImageLoader.get(applicationContext)
                val result = imageLoader.execute(request)
                (result.image?.asDrawable(applicationContext.resources) as? BitmapDrawable)?.bitmap
            } catch (_: Exception) {
                null
            }
        }

    /** Crops [src] to a centered circle so avatars render round in the tray. */
    private suspend fun circleCrop(src: Bitmap): Bitmap =
        withContext(Dispatchers.Default) {
            try {
                val size = min(src.width, src.height)
                val squared =
                    if (src.width != src.height) {
                        Bitmap.createBitmap(src, (src.width - size) / 2, (src.height - size) / 2, size, size)
                    } else {
                        src
                    }
                val output = createBitmap(size, size)
                val canvas = Canvas(output)
                val paint =
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        isFilterBitmap = true
                        shader = BitmapShader(squared, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                    }
                val r = size / 2f
                canvas.drawCircle(r, r, r, paint)
                output
            } catch (_: Exception) {
                src
            }
        }

    /**
     * Draws the badge image (e.g. a NIP-30 custom-emoji reaction) onto the bottom-right corner
     * of the base avatar. Returns the base unchanged if the badge can't be loaded, or the badge
     * alone if there is no base avatar.
     */
    private suspend fun overlayBadge(
        base: Bitmap?,
        badgeUrl: String,
        applicationContext: Context,
    ): Bitmap? {
        val badge = loadBitmap(badgeUrl, applicationContext) ?: return base
        if (base == null) return badge

        return withContext(Dispatchers.Default) {
            val result = base.copy(base.config ?: Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(result)
            val badgeSize = min(result.width, result.height) * 0.45f
            val dest =
                RectF(
                    result.width - badgeSize,
                    result.height - badgeSize,
                    result.width.toFloat(),
                    result.height.toFloat(),
                )
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
            canvas.drawBitmap(badge, null, dest, paint)
            result
        }
    }

    // ---------------------------------------------------------------------
    // Group summaries, dedup, dismissal
    // ---------------------------------------------------------------------

    private fun NotificationManager.sendGroupSummary(
        category: NotificationCategory,
        groupKey: String,
        summaryId: Int,
        applicationContext: Context,
    ) {
        val activeCount = activeNotifications.count { it.notification.group == groupKey && it.id != summaryId }

        if (activeCount < 2) return

        val summaryBuilder =
            NotificationCompat
                .Builder(applicationContext, category.channelId(applicationContext))
                .setSmallIcon(category.smallIcon)
                .setColor(category.color)
                .setGroup(groupKey)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setStyle(
                    NotificationCompat
                        .InboxStyle()
                        .setSummaryText(stringRes(applicationContext, category.summaryTextRes)),
                )

        notify(summaryId, summaryBuilder.build())
    }

    /** Cancels all notifications. */
    fun NotificationManager.cancelNotifications() {
        cancelAll()
    }

    /**
     * Dismisses the tray notification posted for [eventId] — used to auto-clear a
     * notification once the user reads the underlying event in-app.
     *
     * Per-event notifications are keyed by `id.hashCode()`, so hashing the same
     * event id targets exactly the notification posted for it. Cancelling an id
     * that isn't currently shown is a harmless no-op. After removing the child,
     * any group summary left without children is cancelled too so the tray
     * doesn't keep an empty summary around.
     */
    fun NotificationManager.dismissNotificationForEvent(eventId: HexKey) {
        val notId = eventId.hashCode()

        // Most events the user reads never had a tray notification (regular feed
        // items), so bail out before touching anything when nothing is posted for it.
        if (activeNotifications.none { it.id == notId }) return

        cancel(notId)
        cancelChildlessGroupSummaries()
    }

    private fun NotificationManager.cancelChildlessGroupSummaries() {
        val active: Array<StatusBarNotification> = activeNotifications
        for (summary in active) {
            if (summary.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0) continue
            val group = summary.notification.group ?: continue
            val hasChildren = active.any { it.id != summary.id && it.notification.group == group }
            if (!hasChildren) cancel(summary.id)
        }
    }

    private fun NotificationCategory.priority(): Int =
        when (importance) {
            NotificationManager.IMPORTANCE_HIGH, NotificationManager.IMPORTANCE_MAX -> NotificationCompat.PRIORITY_HIGH
            NotificationManager.IMPORTANCE_LOW, NotificationManager.IMPORTANCE_MIN -> NotificationCompat.PRIORITY_LOW
            else -> NotificationCompat.PRIORITY_DEFAULT
        }
}
