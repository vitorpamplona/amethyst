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
package com.vitorpamplona.amethyst.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.moderation.notifications.HostOs
import com.vitorpamplona.amethyst.commons.moderation.notifications.NotifKind
import com.vitorpamplona.amethyst.commons.moderation.notifications.NotificationDispatcher
import com.vitorpamplona.amethyst.commons.moderation.notifications.NotificationReadState
import com.vitorpamplona.amethyst.commons.moderation.notifications.NotificationSettings
import com.vitorpamplona.amethyst.commons.moderation.notifications.NotificationSpec
import com.vitorpamplona.amethyst.commons.moderation.notifications.PermissionState
import com.vitorpamplona.amethyst.commons.moderation.notifications.PreferencesNotificationSettings
import com.vitorpamplona.amethyst.commons.moderation.notifications.SendResult
import com.vitorpamplona.amethyst.commons.moderation.notifications.detectHostOs
import com.vitorpamplona.amethyst.commons.moderation.notifications.nowEpochSeconds
import com.vitorpamplona.amethyst.desktop.ui.ReadingColumn
import com.vitorpamplona.amethyst.desktop.ui.notifications.LocalNotificationDispatcher
import com.vitorpamplona.amethyst.desktop.ui.notifications.LocalNotificationReadState
import com.vitorpamplona.amethyst.desktop.ui.notifications.LocalNotificationSettings
import com.vitorpamplona.amethyst.desktop.ui.readingHorizontalPadding
import kotlinx.coroutines.launch

@Composable
fun NotificationSettingsScreen(onBack: (() -> Unit)? = null) {
    val hoistedSettings = LocalNotificationSettings.current
    val settings: NotificationSettings =
        remember(hoistedSettings) { hoistedSettings ?: PreferencesNotificationSettings() }
    val readState: NotificationReadState? = LocalNotificationReadState.current

    val enabled by settings.enabled.collectAsState()
    val kinds by settings.kinds.collectAsState()
    val dnd by settings.dnd.collectAsState()
    val preview by settings.previewInToast.collectAsState()

    ReadingColumn {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = readingHorizontalPadding(), vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onBack != null) {
                    TextButton(onClick = onBack) { Text("← Back") }
                }
                Text(
                    "Notifications",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(Modifier.height(12.dp))

            // Platform status
            val host = remember { detectHostOs() }
            val dispatcher: NotificationDispatcher? = LocalNotificationDispatcher.current
            val permissionState by (
                dispatcher?.permission?.collectAsState()
                    ?: remember { mutableStateOf<PermissionState>(PermissionState.NotApplicable) }
            )
            val nativeAvailable by (
                dispatcher?.nativeAvailable?.collectAsState()
                    ?: remember { mutableStateOf(false) }
            )
            val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
            var testStatus by remember { mutableStateOf<String?>(null) }
            var requestingPermission by remember { mutableStateOf(false) }
            var sendingTest by remember { mutableStateOf(false) }

            // Re-sync permission state whenever this screen enters composition
            // and whenever the window regains focus — user may have toggled
            // Amethyst in System Settings → Notifications while we were open.
            val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
            androidx.compose.runtime.LaunchedEffect(dispatcher) {
                dispatcher?.refreshPermission()
            }
            androidx.compose.runtime.LaunchedEffect(dispatcher, windowInfo) {
                androidx.compose.runtime
                    .snapshotFlow { windowInfo.isWindowFocused }
                    .collect { focused ->
                        if (focused) dispatcher?.refreshPermission()
                    }
            }

            PlatformStatusCard(
                host = host,
                nativeAvailable = nativeAvailable,
                permission = permissionState,
            )

            Spacer(Modifier.height(12.dp))

            // Master switch — guard against enabling while permission unauthorized on macOS
            val masterAllowed =
                when (permissionState) {
                    PermissionState.Granted, PermissionState.NotApplicable -> true
                    else -> false
                }
            SwitchRow(
                title = "Enable desktop notifications",
                subtitle =
                    if (masterAllowed) {
                        "Master toggle for all OS toasts."
                    } else {
                        "Grant permission (button below) before enabling."
                    },
                checked = enabled && masterAllowed,
                onCheckedChange = { v ->
                    if (v && !masterAllowed) return@SwitchRow
                    settings.setEnabled(v)
                },
            )

            Spacer(Modifier.height(6.dp))

            // Permission-aware action button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (permissionState) {
                    PermissionState.NotRequested -> {
                        OutlinedButton(
                            onClick = {
                                if (requestingPermission) return@OutlinedButton
                                coroutineScope.launch {
                                    requestingPermission = true
                                    testStatus = "Waiting for OS prompt…"
                                    val newState =
                                        try {
                                            dispatcher?.requestPermission() ?: PermissionState.Denied
                                        } finally {
                                            requestingPermission = false
                                        }
                                    testStatus =
                                        when (newState) {
                                            PermissionState.Granted -> "Permission granted. Try the test toast below."
                                            PermissionState.Denied -> "Permission denied. Enable in System Settings if you change your mind."
                                            PermissionState.BundleRequired -> "Notifications need a bundled app — run `./gradlew :desktopApp:runDistributable`."
                                            else -> null
                                        }
                                }
                            },
                            enabled = dispatcher != null && !requestingPermission,
                        ) {
                            if (requestingPermission) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.size(8.dp))
                                Text("Requesting…")
                            } else {
                                Text("Enable OS notifications")
                            }
                        }
                    }
                    PermissionState.Granted, PermissionState.NotApplicable -> {
                        OutlinedButton(
                            onClick = {
                                if (sendingTest) return@OutlinedButton
                                coroutineScope.launch {
                                    sendingTest = true
                                    testStatus = "Sending…"
                                    val result =
                                        try {
                                            dispatcher?.send(
                                                NotificationSpec(
                                                    title = "Amethyst",
                                                    body = "Test notification — you're all set.",
                                                    kind = NotifKind.MENTION,
                                                ),
                                            )
                                        } finally {
                                            sendingTest = false
                                        }
                                    testStatus =
                                        when (result) {
                                            SendResult.Delivered -> "Sent via native pipeline."
                                            SendResult.DeliveredViaFallback -> "Sent via AWT fallback (native lib unavailable)."
                                            is SendResult.Suppressed -> "Suppressed: ${result.reason}"
                                            is SendResult.Failed -> "Failed: ${result.error.message ?: result.error::class.simpleName}"
                                            null -> "Dispatcher not initialized."
                                        }
                                }
                            },
                            enabled = dispatcher != null && !sendingTest,
                        ) {
                            if (sendingTest) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.size(8.dp))
                                Text("Sending…")
                            } else {
                                Text("Send a test toast")
                            }
                        }
                    }
                    PermissionState.Denied -> {
                        OutlinedButton(
                            onClick = {
                                try {
                                    java.awt.Desktop
                                        .getDesktop()
                                        .browse(java.net.URI("x-apple.systempreferences:com.apple.preference.notifications"))
                                } catch (_: Throwable) {
                                    // Deep-link is macOS-only; ignore on other platforms.
                                }
                            },
                        ) { Text("Open System Settings") }
                        Text(
                            "Enable in System Settings → Notifications → Amethyst",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    PermissionState.BundleRequired -> {
                        Text(
                            "Run `./gradlew :desktopApp:runDistributable` — OS notifications need a bundled `.app`.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                testStatus?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            SectionTitle("Which events fire notifications?")

            KindRow(
                label = "Zaps",
                subtitle = "Someone sent you sats.",
                enabled = kinds.zap,
                onToggle = { settings.setKindToggle(NotifKind.ZAP, it) },
            )
            KindRow(
                label = "Direct messages",
                subtitle = "Encrypted DMs. Preview coming after decryption pipeline lands.",
                enabled = kinds.dm,
                onToggle = { settings.setKindToggle(NotifKind.DM, it) },
            )
            KindRow(
                label = "Replies to your posts",
                subtitle = null,
                enabled = kinds.reply,
                onToggle = { settings.setKindToggle(NotifKind.REPLY, it) },
            )
            KindRow(
                label = "Mentions",
                subtitle = "Someone tagged you in a post.",
                enabled = kinds.mention,
                onToggle = { settings.setKindToggle(NotifKind.MENTION, it) },
            )
            KindRow(
                label = "Reposts of your posts",
                subtitle = null,
                enabled = kinds.repost,
                onToggle = { settings.setKindToggle(NotifKind.REPOST, it) },
            )
            KindRow(
                label = "Reactions",
                subtitle = "Emoji reactions on your posts.",
                enabled = kinds.reaction,
                onToggle = { settings.setKindToggle(NotifKind.REACTION, it) },
            )
            KindRow(
                label = "New followers",
                subtitle = null,
                enabled = kinds.follow,
                onToggle = { settings.setKindToggle(NotifKind.FOLLOW, it) },
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            SectionTitle("Do Not Disturb")
            DndRow(
                activeUntil = dnd.manualUntilEpochSec,
                onSelect = { hours ->
                    settings.setManualDndUntil(if (hours <= 0) null else nowEpochSeconds() + hours * 3600)
                },
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            SectionTitle("Privacy")
            SwitchRow(
                title = "Show note preview in toast",
                subtitle = "Off is safer for screen sharing. Titles never include note content.",
                checked = preview,
                onCheckedChange = { settings.setPreviewInToast(it) },
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            SectionTitle("Inbox")
            OutlinedButton(
                onClick = { readState?.markAsRead(nowEpochSeconds()) },
                enabled = readState != null,
            ) {
                Text("Mark all notifications as read")
            }
        }
    }
}

private fun platformLabel(host: HostOs): String =
    when (host) {
        HostOs.MAC -> "macOS"
        HostOs.WINDOWS -> "Windows"
        HostOs.LINUX -> "Linux"
        HostOs.UNKNOWN -> "this platform"
    }

@Composable
private fun PlatformStatusCard(
    host: HostOs,
    nativeAvailable: Boolean,
    permission: PermissionState,
) {
    val label = platformLabel(host)
    val (title, subtitle) =
        when {
            !nativeAvailable && permission == PermissionState.BundleRequired ->
                "$label: launch from an `.app` bundle" to
                    "OS notifications need a bundled process. Run `./gradlew :desktopApp:runDistributable` and open the resulting `Amethyst.app`. `gradle run` will never trigger the macOS permission prompt because the JVM has no bundle identity."
            !nativeAvailable ->
                "$label: native pipeline unavailable" to
                    "The Nucleus native library could not be loaded. Notifications will fall back to a legacy AWT balloon (works on Windows/Linux; silently drops on macOS 11+)."
            host == HostOs.MAC && permission == PermissionState.NotRequested ->
                "$label: permission needed" to
                    "Click \"Enable OS notifications\" below. macOS will show its permission prompt — after granting, Amethyst appears in System Settings → Notifications."
            host == HostOs.MAC && permission == PermissionState.Denied ->
                "$label: permission denied" to
                    "You (or your system admin) previously denied notifications for Amethyst. Enable them again in System Settings → Notifications → Amethyst."
            host == HostOs.MAC && permission == PermissionState.Granted ->
                "$label: ready" to
                    "Notifications route through the native UNUserNotificationCenter. Toasts land in Notification Center and persist to history."
            host == HostOs.WINDOWS ->
                "$label: ready" to
                    "Toasts route through the WinRT Toast pipeline (Action Center). AUMID: com.vitorpamplona.amethyst.desktop. Verify Settings → System → Notifications → Amethyst is enabled."
            host == HostOs.LINUX ->
                "$label: ready" to
                    "Toasts route through freedesktop D-Bus. Any modern notification daemon (GNOME, KDE, XFCE, Sway, dunst, mako) will render them."
            else ->
                "$label: unknown platform" to
                    "OS notifications may not work reliably. Please file a bug."
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun KindRow(
    label: String,
    subtitle: String?,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) = SwitchRow(title = label, subtitle = subtitle, checked = enabled, onCheckedChange = onToggle)

@Composable
private fun DndRow(
    activeUntil: Long?,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val now = nowEpochSeconds()
    val label =
        when {
            activeUntil == null || activeUntil <= now -> "DND: Off"
            else -> {
                val remaining = activeUntil - now
                val hours = (remaining + 1800) / 3600
                if (hours <= 1) "DND on for ~1h" else "DND on for ~${hours}h"
            }
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { expanded = true }) { Text("Change") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Off") },
                onClick = {
                    onSelect(0)
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text("Mute for 1 hour") },
                onClick = {
                    onSelect(1)
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text("Mute for 8 hours") },
                onClick = {
                    onSelect(8)
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text("Mute for 24 hours") },
                onClick = {
                    onSelect(24)
                    expanded = false
                },
            )
        }
    }
}
