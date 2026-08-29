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
package com.vitorpamplona.amethyst.shared.platform

import android.app.Notification
import android.app.NotificationManager
import com.vitorpamplona.amethyst.stubs.PlatformGaps
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap

/**
 * Posts the app's notifications to the desktop, through the system tray.
 *
 * `SystemTray` is in the JDK and works on Windows, most Linux desktops, and
 * (with a menu-bar item rather than a balloon) macOS, so it needs no extra
 * dependency and no per-OS bridge. It is also less capable than Android's
 * shade, and the differences are handled rather than hidden:
 *
 *  - **Ongoing / progress notifications** have no tray equivalent — a balloon
 *    is a transient toast, not a row that updates. Rather than flash one
 *    balloon per progress tick, the latest state of an ongoing notification is
 *    kept in [ongoing] (readable by a UI that wants to draw its own progress
 *    row) and only the first one is shown.
 *  - **Action buttons and inline reply** cannot ride on a balloon. The tap
 *    target is the balloon itself, which fires the content intent, so the app
 *    still opens where the notification points.
 *  - **Grouping and per-channel importance** are Android shade concepts; every
 *    notification is posted individually at its mapped severity.
 *
 * A platform with a richer bridge can replace this by installing its own
 * [NotificationManager.Presenter]; nothing here is load-bearing beyond being
 * better than silence.
 */
object DesktopNotifications {
    /** Ongoing notifications by key, latest state wins. For an in-app status row. */
    val ongoing: Map<String, Notification> get() = live

    private val live = ConcurrentHashMap<String, Notification>()
    private val shown = ConcurrentHashMap.newKeySet<String>()

    private val trayIcon: TrayIcon? by lazy { installTrayIcon() }

    /** Installs this as the process-wide presenter. Safe to call more than once. */
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
            // A balloon per progress tick would be unusable; announce once.
            if (!shown.add(key)) return
        }

        val icon = trayIcon
        if (icon == null) {
            PlatformGaps.report(
                "Notifications.systemTray",
                "this desktop session has no system tray, so '${notification.title}' had nowhere to appear; " +
                    "an in-window notification surface would cover it",
            )
            return
        }

        icon.displayMessage(
            notification.title?.toString().orEmpty(),
            bodyOf(notification),
            severityOf(notification),
        )
    }

    private fun dismiss(key: String) {
        live.remove(key)
        shown.remove(key)
        // A tray balloon dismisses itself; there is nothing to take back.
    }

    /** The expanded text if there is one, then the inbox lines, then the body. */
    private fun bodyOf(notification: Notification): String {
        val main = notification.bigText ?: notification.text
        val lines = notification.lines.joinToString("\n")
        return listOf(main?.toString().orEmpty(), lines)
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun severityOf(notification: Notification): TrayIcon.MessageType =
        when {
            notification.category == "err" -> TrayIcon.MessageType.ERROR
            notification.priority >= Notification.PRIORITY_HIGH -> TrayIcon.MessageType.WARNING
            else -> TrayIcon.MessageType.INFO
        }

    private fun installTrayIcon(): TrayIcon? {
        if (!SystemTray.isSupported()) return null
        return runCatching {
            // A 16x16 transparent square: the app replaces this with its own
            // icon by holding its own TrayIcon; this exists so notifications
            // work before any UI is up.
            val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
            TrayIcon(image, "Amethyst").also {
                it.isImageAutoSize = true
                SystemTray.getSystemTray().add(it)
            }
        }.getOrNull()
    }
}
