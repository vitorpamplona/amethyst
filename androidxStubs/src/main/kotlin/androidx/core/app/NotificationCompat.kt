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
import android.app.PendingIntent
import android.content.Context

/**
 * JVM stand-in for androidx.core.app.NotificationCompat.
 *
 * The builder records what a notification would say so a desktop backend can
 * render it; the Android-only knobs (small icon resource, vibration pattern,
 * lights) accept their arguments and drop them rather than being removed, so
 * the app's long builder chains compile unchanged.
 */
object NotificationCompat {
    const val PRIORITY_MIN = -2
    const val PRIORITY_LOW = -1
    const val PRIORITY_DEFAULT = 0
    const val PRIORITY_HIGH = 1
    const val PRIORITY_MAX = 2
    const val DEFAULT_ALL = -1
    const val VISIBILITY_PRIVATE = 0
    const val VISIBILITY_PUBLIC = 1
    const val VISIBILITY_SECRET = -1
    const val CATEGORY_MESSAGE = "msg"
    const val CATEGORY_CALL = "call"
    const val CATEGORY_SERVICE = "service"
    const val CATEGORY_SOCIAL = "social"

    class Builder(
        context: Context,
        val channelId: String,
    ) {
        var contentTitle: CharSequence? = null
            private set
        var contentText: CharSequence? = null
            private set
        var contentIntent: PendingIntent? = null
            private set

        fun setContentTitle(title: CharSequence?) = apply { contentTitle = title }

        fun setContentText(text: CharSequence?) = apply { contentText = text }

        fun setContentIntent(intent: PendingIntent?) = apply { contentIntent = intent }

        fun setSmallIcon(icon: Int) = apply { }

        fun setLargeIcon(icon: Any?) = apply { }

        fun setPriority(priority: Int) = apply { }

        fun setCategory(category: String?) = apply { }

        fun setVisibility(visibility: Int) = apply { }

        fun setAutoCancel(autoCancel: Boolean) = apply { }

        fun setOngoing(ongoing: Boolean) = apply { }

        fun setSilent(silent: Boolean) = apply { }

        fun setDefaults(defaults: Int) = apply { }

        fun setGroup(group: String?) = apply { }

        fun setGroupSummary(summary: Boolean) = apply { }

        fun setWhen(whenMs: Long) = apply { }

        fun setShowWhen(show: Boolean) = apply { }

        fun setOnlyAlertOnce(once: Boolean) = apply { }

        fun setStyle(style: Any?) = apply { }

        fun setSubText(text: CharSequence?) = apply { }

        fun setDeleteIntent(intent: PendingIntent?) = apply { }

        fun setFullScreenIntent(
            intent: PendingIntent?,
            highPriority: Boolean,
        ) = apply { }

        fun addAction(
            icon: Int,
            title: CharSequence?,
            intent: PendingIntent?,
        ) = apply { }

        fun build(): Notification = Notification()
    }

    class BigTextStyle {
        fun bigText(text: CharSequence?) = apply { }

        fun setBigContentTitle(title: CharSequence?) = apply { }

        fun setSummaryText(text: CharSequence?) = apply { }
    }

    class InboxStyle {
        fun addLine(line: CharSequence?) = apply { }

        fun setBigContentTitle(title: CharSequence?) = apply { }

        fun setSummaryText(text: CharSequence?) = apply { }
    }
}
