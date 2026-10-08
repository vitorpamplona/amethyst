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
package com.vitorpamplona.amethyst.desktop.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.moderation.notifications.AwtTrayNotifier
import com.vitorpamplona.amethyst.commons.moderation.notifications.NotificationDispatcher
import com.vitorpamplona.amethyst.commons.moderation.notifications.NotificationSettings
import com.vitorpamplona.amethyst.commons.moderation.notifications.NucleusNotificationDispatcher
import com.vitorpamplona.amethyst.commons.moderation.notifications.PermissionState
import com.vitorpamplona.amethyst.commons.moderation.notifications.PreferencesNotificationSettings
import com.vitorpamplona.amethyst.commons.moderation.notifications.nowEpochSeconds
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.desktop_notifications
import com.vitorpamplona.amethyst.commons.resources.desktop_notifications_denied
import com.vitorpamplona.amethyst.commons.resources.desktop_notifications_description
import com.vitorpamplona.amethyst.commons.resources.desktop_notifications_preview
import com.vitorpamplona.amethyst.commons.resources.desktop_notifications_preview_description
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsRow
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.desktop.ui.notifications.DesktopNotificationAutoDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * OS notifications for mentions, replies, reactions, zaps and messages: the native notifier
 * (Notification Center, the Windows toast, libnotify) with the system tray as the fallback.
 * Off until the user turns them on.
 */
class DesktopNotifications(
    private val scope: CoroutineScope,
) {
    val settings: NotificationSettings = PreferencesNotificationSettings()

    // Built on first use: it loads the native notifier.
    val dispatcher: NotificationDispatcher by lazy {
        NucleusNotificationDispatcher(bundleId = BUNDLE_ID, appLabel = APP_LABEL, fallback = AwtTrayNotifier(), scope = scope)
    }

    /** Events older than the app are history, not news. */
    private val sessionStartSec = nowEpochSeconds()

    /** Notifies [account] of new events for it while the window is not focused. */
    fun watch(
        account: Account,
        cache: LocalCache,
        isWindowFocused: StateFlow<Boolean>,
    ): Job =
        DesktopNotificationAutoDispatcher(
            dispatcher = dispatcher,
            settings = settings,
            myPubKeyHex = account.signer.pubKey,
            eventStream = cache.getEventStream(),
            authorOf = { id -> cache.getNoteIfExists(id)?.event?.pubKey },
            displayNameOf = { pubKey -> cache.getUserIfExists(pubKey)?.toBestDisplayName() },
            isWindowFocused = isWindowFocused,
            sessionStartSec = sessionStartSec,
            scope = scope,
        ).start()

    companion object {
        private const val BUNDLE_ID = "com.vitorpamplona.amethyst.desktop"
        private const val APP_LABEL = "Amethyst"
    }
}

/** The notification settings' delivery section on desktop: the OS notifications switch and preview. */
@Composable
fun DesktopNotificationDeliverySettings(notifications: DesktopNotifications) {
    val enabled by notifications.settings.enabled.collectAsState()
    val preview by notifications.settings.previewInToast.collectAsState()
    val permission by notifications.dispatcher.permission.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { notifications.dispatcher.refreshPermission() }

    SettingsRow(Res.string.desktop_notifications, Res.string.desktop_notifications_description) {
        Switch(
            checked = enabled,
            onCheckedChange = { on ->
                notifications.settings.setEnabled(on)
                if (on) scope.launch { notifications.dispatcher.requestPermission() }
            },
        )
    }

    if (enabled && permission == PermissionState.Denied) {
        Text(
            stringRes(Res.string.desktop_notifications_denied),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    SettingsRow(Res.string.desktop_notifications_preview, Res.string.desktop_notifications_preview_description) {
        Switch(checked = preview, enabled = enabled, onCheckedChange = notifications.settings::setPreviewInToast)
    }
}
