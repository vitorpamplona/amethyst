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
 * The builder fills in a real [Notification] rather than accumulating nothing:
 * a desktop presenter can only show what it is given, and a builder that
 * accepted a title and a body and then discarded them would post blank
 * notifications that look, from the calling code, exactly like working ones.
 *
 * The knobs with no desktop meaning — vibration patterns, lights, LED colour,
 * lock-screen visibility, the small-icon drawable id — take their arguments and
 * drop them, so the app's long builder chains compile unchanged.
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

    const val CATEGORY_ALARM = "alarm"
    const val CATEGORY_CALL = "call"
    const val CATEGORY_EMAIL = "email"
    const val CATEGORY_ERROR = "err"
    const val CATEGORY_EVENT = "event"
    const val CATEGORY_LOCATION_SHARING = "location_sharing"
    const val CATEGORY_MESSAGE = "msg"
    const val CATEGORY_MISSED_CALL = "missed_call"
    const val CATEGORY_NAVIGATION = "navigation"
    const val CATEGORY_PROGRESS = "progress"
    const val CATEGORY_PROMO = "promo"
    const val CATEGORY_RECOMMENDATION = "recommendation"
    const val CATEGORY_REMINDER = "reminder"
    const val CATEGORY_SERVICE = "service"
    const val CATEGORY_SOCIAL = "social"
    const val CATEGORY_STATUS = "status"
    const val CATEGORY_STOPWATCH = "stopwatch"
    const val CATEGORY_SYSTEM = "sys"
    const val CATEGORY_TRANSPORT = "transport"
    const val CATEGORY_WORKOUT = "workout"

    const val GROUP_ALERT_ALL = 0
    const val GROUP_ALERT_SUMMARY = 1
    const val GROUP_ALERT_CHILDREN = 2

    const val FOREGROUND_SERVICE_DEFAULT = 0
    const val FOREGROUND_SERVICE_IMMEDIATE = 1
    const val FOREGROUND_SERVICE_DEFERRED = 2

    class Builder(
        context: Context,
        val channelId: String,
    ) {
        private val notification = Notification().also { it.channelId = channelId }

        val contentTitle: CharSequence? get() = notification.title
        val contentText: CharSequence? get() = notification.text
        val contentIntent: PendingIntent? get() = notification.contentIntent

        fun setContentTitle(title: CharSequence?) = apply { notification.title = title }

        fun setContentText(text: CharSequence?) = apply { notification.text = text }

        fun setContentIntent(intent: PendingIntent?) = apply { notification.contentIntent = intent }

        fun setSmallIcon(icon: Int) = apply { notification.smallIcon = icon }

        fun setLargeIcon(icon: Any?) = apply { notification.largeIcon = icon }

        fun setPriority(priority: Int) = apply { notification.priority = priority }

        fun setCategory(category: String?) = apply { notification.category = category }

        fun setVisibility(visibility: Int) = apply { }

        fun setAutoCancel(autoCancel: Boolean) = apply { notification.autoCancel = autoCancel }

        fun setOngoing(ongoing: Boolean) =
            apply {
                notification.ongoing = ongoing
                notification.flags =
                    if (ongoing) {
                        notification.flags or Notification.FLAG_ONGOING_EVENT
                    } else {
                        notification.flags and Notification.FLAG_ONGOING_EVENT.inv()
                    }
            }

        fun setSilent(silent: Boolean) = apply { notification.silent = silent }

        fun setDefaults(defaults: Int) = apply { }

        fun setGroup(group: String?) = apply { notification.group = group }

        fun setGroupSummary(summary: Boolean) = apply { notification.groupSummary = summary }

        fun setWhen(whenMs: Long) = apply { notification.`when` = whenMs }

        fun setShowWhen(show: Boolean) = apply { }

        fun setOnlyAlertOnce(once: Boolean) = apply { notification.onlyAlertOnce = once }

        fun setForegroundServiceBehavior(behavior: Int) = apply { }

        fun setTimeoutAfter(durationMs: Long) = apply { }

        /** The redacted copy shown on a lock screen. Kept for a presenter that has one. */
        fun setPublicVersion(value: Notification?) = apply { notification.publicVersion = value }

        fun setGroupAlertBehavior(behavior: Int) = apply { }

        fun addPerson(person: Person?) = apply { }

        fun setShortcutId(id: String?) = apply { }

        fun setWhen(
            whenMs: Long,
            show: Boolean,
        ) = apply { notification.`when` = whenMs }

        fun setUsesChronometer(uses: Boolean) = apply { }

        fun setLocalOnly(localOnly: Boolean) = apply { }

        fun setColor(color: Int) = apply { }

        fun setColorized(colorized: Boolean) = apply { }

        fun setSortKey(key: String?) = apply { }

        fun setTicker(ticker: CharSequence?) = apply { }

        fun setNumber(number: Int) = apply { }

        fun setVibrate(pattern: LongArray?) = apply { }

        fun setLights(
            argb: Int,
            onMs: Int,
            offMs: Int,
        ) = apply { }

        fun setSound(sound: Any?) = apply { }

        fun setSubText(text: CharSequence?) = apply { notification.subText = text }

        fun setDeleteIntent(intent: PendingIntent?) = apply { notification.deleteIntent = intent }

        fun setProgress(
            max: Int,
            progress: Int,
            indeterminate: Boolean,
        ) = apply {
            notification.progressMax = max
            notification.progress = progress
            notification.progressIndeterminate = indeterminate
        }

        /**
         * Styles are what carry the expanded body and the progress bar, so they
         * are folded into the notification here rather than ignored.
         */
        fun setStyle(style: Any?) =
            apply {
                when (style) {
                    is BigTextStyle -> {
                        notification.bigText = style.bigText
                        style.bigContentTitle?.let { notification.title = it }
                        style.summaryText?.let { notification.subText = it }
                    }
                    is InboxStyle -> {
                        notification.lines.clear()
                        notification.lines.addAll(style.lines)
                        style.bigContentTitle?.let { notification.title = it }
                        style.summaryText?.let { notification.subText = it }
                    }
                    is MessagingStyle -> {
                        // The last message is what a one-line presenter shows;
                        // the whole conversation stays on the notification for
                        // one that can show more.
                        notification.messagingStyle = style
                        style.messages.lastOrNull()?.let { last ->
                            notification.text = last.text
                            last.person?.name?.let { notification.title = it }
                        }
                        style.conversationTitle?.let { notification.subText = it }
                        notification.lines.clear()
                        notification.lines.addAll(style.messages.mapNotNull { it.text })
                    }
                    is BigPictureStyle -> {
                        style.bigContentTitle?.let { notification.title = it }
                        style.summaryText?.let { notification.subText = it }
                    }
                    is ProgressStyle -> {
                        notification.progressIndeterminate = style.isIndeterminate
                        notification.progressMax = style.total
                        notification.progress = style.progress
                    }
                    else -> Unit
                }
            }

        fun setFullScreenIntent(
            intent: PendingIntent?,
            highPriority: Boolean,
        ) = apply { notification.fullScreenIntent = intent }

        fun addAction(
            icon: Int,
            title: CharSequence?,
            intent: PendingIntent?,
        ) = apply { notification.actions.add(Notification.Action(icon, title, intent)) }

        fun addAction(action: Action?) =
            apply {
                if (action != null) {
                    notification.actions.add(Notification.Action(0, action.title, action.actionIntent))
                }
            }

        fun build(): Notification = notification
    }

    /**
     * An action button on a notification. Carries its label and intent so a
     * desktop notification backend can render and fire it; the icon is an
     * Android drawable id with no desktop meaning and is dropped.
     */
    class Action(
        icon: Int,
        val title: CharSequence?,
        val actionIntent: PendingIntent?,
        val remoteInputs: List<RemoteInput> = emptyList(),
    ) {
        class Builder(
            icon: Int,
            private val title: CharSequence?,
            private val intent: PendingIntent?,
        ) {
            private val remoteInputs = mutableListOf<RemoteInput>()

            fun addRemoteInput(input: RemoteInput?) = apply { if (input != null) remoteInputs.add(input) }

            fun setAllowGeneratedReplies(allowed: Boolean) = apply { }

            fun setSemanticAction(action: Int) = apply { }

            fun setShowsUserInterface(shows: Boolean) = apply { }

            fun build() = Action(0, title, intent, remoteInputs.toList())
        }

        companion object {
            const val SEMANTIC_ACTION_REPLY = 1
            const val SEMANTIC_ACTION_MARK_AS_READ = 2
            const val SEMANTIC_ACTION_MUTE = 6
        }
    }

    class BigTextStyle {
        var bigText: CharSequence? = null
            private set
        var bigContentTitle: CharSequence? = null
            private set
        var summaryText: CharSequence? = null
            private set

        fun bigText(text: CharSequence?) = apply { bigText = text }

        fun setBigContentTitle(title: CharSequence?) = apply { bigContentTitle = title }

        fun setSummaryText(text: CharSequence?) = apply { summaryText = text }
    }

    /**
     * A conversation, as a series of messages with senders.
     *
     * Kept in full — every message, with its [Person] and timestamp — because
     * that is the whole content of a DM notification. Flattening it to one line
     * of text on the way in would leave a presenter that *can* render a
     * conversation with nothing to render.
     */
    class MessagingStyle(
        val user: Person,
    ) {
        val messages = mutableListOf<Message>()

        var conversationTitle: CharSequence? = null
            private set
        var isGroupConversation: Boolean = false
            private set

        fun addMessage(
            text: CharSequence?,
            timestamp: Long,
            person: Person?,
        ) = apply { messages.add(Message(text, timestamp, person)) }

        fun addMessage(message: Message) = apply { messages.add(message) }

        fun setConversationTitle(title: CharSequence?) = apply { conversationTitle = title }

        fun setGroupConversation(group: Boolean) = apply { isGroupConversation = group }

        class Message(
            val text: CharSequence?,
            val timestamp: Long,
            val person: Person?,
        )
    }

    class BigPictureStyle {
        var picture: Any? = null
            private set
        var bigContentTitle: CharSequence? = null
            private set
        var summaryText: CharSequence? = null
            private set

        fun bigPicture(value: Any?) = apply { picture = value }

        fun bigLargeIcon(value: Any?) = apply { }

        fun setBigContentTitle(title: CharSequence?) = apply { bigContentTitle = title }

        fun setSummaryText(text: CharSequence?) = apply { summaryText = text }
    }

    class InboxStyle {
        val lines = mutableListOf<CharSequence>()

        var bigContentTitle: CharSequence? = null
            private set
        var summaryText: CharSequence? = null
            private set

        fun addLine(line: CharSequence?) = apply { if (line != null) lines.add(line) }

        fun setBigContentTitle(title: CharSequence?) = apply { bigContentTitle = title }

        fun setSummaryText(text: CharSequence?) = apply { summaryText = text }
    }

    /**
     * JVM stand-in for the segmented progress style.
     *
     * Android draws each segment separately; a desktop presenter has one bar,
     * so the segments are collapsed into a total the same way the platform
     * computes its own: the sum of the segment lengths. Collapsing is the
     * honest reduction — the progress value keeps meaning, only the visual
     * subdivision is lost.
     */
    class ProgressStyle {
        var segments: List<Segment> = emptyList()
            private set
        var points: List<Point> = emptyList()
            private set
        var progress: Int = 0
            private set
        var isIndeterminate: Boolean = false
            private set

        /** The sum of the segment lengths, or 100 when no segments were given. */
        val total: Int get() = segments.sumOf { it.length }.takeIf { it > 0 } ?: 100

        fun setProgressSegments(value: List<Segment>) = apply { segments = value }

        fun setProgressPoints(value: List<Point>) = apply { points = value }

        fun setProgress(value: Int) = apply { progress = value }

        fun setProgressIndeterminate(value: Boolean) = apply { isIndeterminate = value }

        fun setStyledByProgress(value: Boolean) = apply { }

        fun setProgressTrackerIcon(icon: Any?) = apply { }

        fun setBigContentTitle(title: CharSequence?) = apply { }

        class Segment(
            val length: Int,
        ) {
            fun setColor(color: Int) = apply { }
        }

        class Point(
            val position: Int,
        ) {
            fun setColor(color: Int) = apply { }
        }
    }
}
