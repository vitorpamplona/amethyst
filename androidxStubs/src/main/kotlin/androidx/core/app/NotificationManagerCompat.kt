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
package androidx.core.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.service.notification.StatusBarNotification

/** JVM stand-in for androidx.core.app.NotificationManagerCompat. */
class NotificationManagerCompat private constructor(
    private val delegate: NotificationManager,
) {
    companion object {
        private val shared = NotificationManager()

        const val IMPORTANCE_NONE = NotificationManager.IMPORTANCE_NONE
        const val IMPORTANCE_MIN = NotificationManager.IMPORTANCE_MIN
        const val IMPORTANCE_LOW = NotificationManager.IMPORTANCE_LOW
        const val IMPORTANCE_DEFAULT = NotificationManager.IMPORTANCE_DEFAULT
        const val IMPORTANCE_HIGH = NotificationManager.IMPORTANCE_HIGH

        @JvmStatic
        fun from(context: Context): NotificationManagerCompat = NotificationManagerCompat(shared)
    }

    fun createNotificationChannel(channel: NotificationChannelCompat) = delegate.createNotificationChannel(channel.channel)

    fun areNotificationsEnabled(): Boolean = delegate.areNotificationsEnabled()

    fun createNotificationChannel(channel: NotificationChannel) = delegate.createNotificationChannel(channel)

    fun createNotificationChannelGroup(group: NotificationChannelGroup) = delegate.createNotificationChannelGroup(group)

    fun getNotificationChannel(channelId: String): NotificationChannel? = delegate.getNotificationChannel(channelId)

    fun deleteNotificationChannel(channelId: String) = delegate.deleteNotificationChannel(channelId)

    fun notify(
        id: Int,
        notification: Notification,
    ) = delegate.notify(id, notification)

    fun notify(
        tag: String?,
        id: Int,
        notification: Notification,
    ) = delegate.notify(tag, id, notification)

    fun cancel(id: Int) = delegate.cancel(id)

    fun cancel(
        tag: String?,
        id: Int,
    ) = delegate.cancel(tag, id)

    fun cancelAll() = delegate.cancelAll()

    /** What is still showing. Read by the group-summary cleanup. */
    val activeNotifications: List<StatusBarNotification> get() = delegate.activeNotifications.toList()
}
