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
package com.vitorpamplona.amethyst.commons.moderation.notifications

import android.app.Notification
import android.app.NotificationManager
import com.vitorpamplona.amethyst.stubs.PlatformGaps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Routes the notifications the retargeted Android code posts into the desktop
 * app's existing native notification stack.
 *
 * There is nothing to invent here: [NucleusNotificationDispatcher] already
 * delivers through `UNUserNotificationCenter`, WinRT toasts and freedesktop
 * D-Bus, and falls back to an AWT balloon. This is the adapter that lets
 * `NotificationManagerCompat.notify(...)` — the call every notification path in
 * the Android code goes through — reach it.
 *
 * What the OS notification model does not have is Android's *persistent* row,
 * so the two kinds are separated rather than flattened:
 *
 *  - a **one-shot** notification is delivered as a toast, which is what it is;
 *  - an **ongoing or progress** notification would otherwise fire a toast per
 *    progress tick, so it is delivered once and its latest state kept in
 *    [ongoing] for an in-app status row to render.
 *
 * Action buttons do not survive the trip: no desktop toast API in this stack
 * carries them. Clicking a toast deep-links by note id where there is one,
 * which is what the actions were mostly there to do.
 */
class AndroidNotificationBridge(
    private val dispatcher: NotificationDispatcher,
    private val scope: CoroutineScope,
) {
    /** Ongoing notifications by key, latest state wins, for an in-app row. */
    val ongoing: Map<String, Notification> get() = live

    private val live = ConcurrentHashMap<String, Notification>()
    private val announced = ConcurrentHashMap.newKeySet<String>()

    /** Installs this as the process-wide presenter. */
    fun install() {
        NotificationManager.setPresenter(
            object : NotificationManager.Presenter {
                override fun notify(
                    tag: String?,
                    id: Int,
                    notification: Notification,
                ) = post(key(tag, id), notification)

                override fun cancel(
                    tag: String?,
                    id: Int,
                ) = dismiss(key(tag, id))
            },
        )
        PlatformGaps.report(
            "Notification.actions",
            "desktop toasts carry no action buttons; a click deep-links to the note instead",
        )
    }

    private fun key(
        tag: String?,
        id: Int,
    ) = if (tag == null) id.toString() else "$tag#$id"

    private fun post(
        key: String,
        notification: Notification,
    ) {
        if (notification.ongoing || notification.progressMax >= 0) {
            live[key] = notification
            if (!announced.add(key)) return
        }

        val spec =
            NotificationSpec(
                title = notification.title?.toString().orEmpty(),
                body = bodyOf(notification),
                kind = kindOf(notification),
                threadId = notification.group,
            )
        scope.launch { dispatcher.send(spec) }
    }

    private fun dismiss(key: String) {
        live.remove(key)
        announced.remove(key)
        // A delivered toast is the user's to dismiss; there is nothing to take back.
    }

    /** The expanded text if there is one, then the inbox lines, then the body. */
    private fun bodyOf(notification: Notification): String =
        listOf(
            (notification.bigText ?: notification.text)?.toString().orEmpty(),
            notification.lines.joinToString("\n"),
        ).filter { it.isNotBlank() }
            .joinToString("\n")

    /**
     * The category the Android builder set is the only classification that
     * survives; anything unrecognised is a mention, which is the kind the
     * settings default to showing.
     */
    private fun kindOf(notification: Notification): NotifKind =
        when (notification.category) {
            "msg" -> NotifKind.DM
            "social" -> NotifKind.REACTION
            else -> NotifKind.MENTION
        }
}
