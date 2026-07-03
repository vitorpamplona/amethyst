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

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-neutral OS notification dispatch API. JVM implementation delegates
 * to Nucleus (macOS UNUserNotificationCenter / Windows WinRT / Linux libnotify).
 * Future Android/iOS actuals implement the same contract.
 */
interface NotificationDispatcher {
    val permission: StateFlow<PermissionState>

    /**
     * Whether the native pipeline is available at all. On unbundled macOS
     * runs (`./gradlew run` without `.app`), Nucleus reports false because
     * `NSBundle.mainBundle.bundleIdentifier` is nil. Sends will fall back
     * to the AWT balloon.
     */
    val nativeAvailable: StateFlow<Boolean>

    /**
     * Trigger the OS-level permission prompt (macOS only — no-op elsewhere).
     * Suspends until the user answers. Updates [permission] as a side effect.
     */
    suspend fun requestPermission(): PermissionState

    /**
     * Re-read the current OS permission state (no prompt). Useful after the
     * user toggled Amethyst in System Settings → Notifications while the app
     * was running — call this on window focus / settings screen open to keep
     * the UI in sync. No-op on non-macOS.
     */
    suspend fun refreshPermission(): PermissionState

    suspend fun send(spec: NotificationSpec): SendResult

    /** Release any held OS resources (tray icon, JNI callbacks). */
    fun release()
}

@Immutable
sealed interface PermissionState {
    object NotRequested : PermissionState

    object Granted : PermissionState

    object Denied : PermissionState

    /** Windows/Linux: no OS-level permission prompt needed. */
    object NotApplicable : PermissionState

    /** macOS but running unbundled — user must `runDistributable`. */
    object BundleRequired : PermissionState
}

@Immutable
data class NotificationSpec(
    val title: String,
    val body: String,
    val kind: NotifKind,
    /** OS grouping key (macOS `threadIdentifier`, Windows Tag+Group). */
    val threadId: String? = null,
    /** Amethyst event id for deep-linking from a click. */
    val deepLinkNoteId: String? = null,
)

sealed interface SendResult {
    object Delivered : SendResult

    data class Suppressed(
        val reason: String,
    ) : SendResult

    data class Failed(
        val error: Throwable,
    ) : SendResult

    /** No native pipeline; fell back to AWT balloon (still delivered on Win/Linux; drops on macOS 11+). */
    object DeliveredViaFallback : SendResult
}
