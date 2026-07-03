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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Native-backed [NotificationDispatcher] using Nucleus (kdroidfilter/Nucleus).
 *
 * - **macOS**: routes through `UNUserNotificationCenter` via a Swift JNI shim
 *   bundled inside `nucleus.notification-macos`. Requires an `.app` bundle at
 *   runtime — see [PermissionState.BundleRequired].
 * - **Windows**: routes through WinRT Toast via `nucleus.notification-windows`.
 *   Requires an AUMID; call [init] before any [send].
 * - **Linux**: routes through freedesktop D-Bus via `nucleus.notification-linux`.
 *   Works with any notification daemon.
 *
 * If the matching native library fails to load or the app is unbundled on
 * macOS, [send] falls back to [AwtTrayNotifier].
 */
class NucleusNotificationDispatcher(
    private val bundleId: String,
    private val appLabel: String,
    private val fallback: AwtTrayNotifier,
    @Suppress("unused") private val scope: CoroutineScope,
) : NotificationDispatcher {
    private val host: HostOs = detectHostOs()

    // Nucleus module availability probed once at construction. Native lib load
    // is triggered by the first class access — wrap in Throwable catch so a
    // missing JAR or unsatisfied link doesn't crash the app.
    private val _nativeAvailable = MutableStateFlow(probeNative())
    override val nativeAvailable: StateFlow<Boolean> = _nativeAvailable.asStateFlow()

    private val _permission = MutableStateFlow<PermissionState>(initialPermissionState())
    override val permission: StateFlow<PermissionState> = _permission.asStateFlow()

    @Volatile
    private var winInitialized: Boolean = false

    init {
        // Refresh macOS permission from the OS in case the user already granted
        // in a previous session. On unbundled runs, Nucleus reports isAvailable
        // = false and we won't touch the OS API.
        if (host == HostOs.MAC && _nativeAvailable.value) {
            try {
                refreshMacPermissionState()
            } catch (_: Throwable) {
                // Bundle-invalid or callback plumbing failure. Leave state as
                // BundleRequired / NotRequested. User can still trigger the
                // request from settings.
            }
        }
    }

    private fun initialPermissionState(): PermissionState =
        when (host) {
            HostOs.MAC -> if (_nativeAvailable.value) PermissionState.NotRequested else PermissionState.BundleRequired
            HostOs.WINDOWS, HostOs.LINUX ->
                if (_nativeAvailable.value) PermissionState.NotApplicable else PermissionState.Denied
            HostOs.UNKNOWN -> PermissionState.NotApplicable
        }

    override suspend fun requestPermission(): PermissionState {
        if (host != HostOs.MAC) {
            _permission.value =
                if (_nativeAvailable.value) PermissionState.NotApplicable else PermissionState.Denied
            return _permission.value
        }
        if (!_nativeAvailable.value) {
            _permission.value = PermissionState.BundleRequired
            return _permission.value
        }
        val granted =
            withContext(Dispatchers.IO) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    try {
                        io.github.kdroidfilter.nucleus.notification.NotificationCenter
                            .requestAuthorization(
                                options =
                                    setOf(
                                        io.github.kdroidfilter.nucleus.notification.AuthorizationOption.ALERT,
                                        io.github.kdroidfilter.nucleus.notification.AuthorizationOption.SOUND,
                                        io.github.kdroidfilter.nucleus.notification.AuthorizationOption.BADGE,
                                    ),
                                callback = { granted, _ -> cont.resume(granted) },
                            )
                    } catch (t: Throwable) {
                        cont.resume(false)
                    }
                }
            }
        _permission.value =
            if (granted) PermissionState.Granted else PermissionState.Denied
        return _permission.value
    }

    override suspend fun refreshPermission(): PermissionState {
        if (host != HostOs.MAC || !_nativeAvailable.value) {
            return _permission.value
        }
        // getNotificationSettings is callback-based; bridge to suspend + timeout.
        val newState =
            withContext(Dispatchers.IO) {
                try {
                    suspendCancellableCoroutine<PermissionState> { cont ->
                        try {
                            io.github.kdroidfilter.nucleus.notification.NotificationCenter.getNotificationSettings { settings ->
                                val mapped =
                                    when (settings.authorizationStatus) {
                                        io.github.kdroidfilter.nucleus.notification.AuthorizationStatus.AUTHORIZED,
                                        io.github.kdroidfilter.nucleus.notification.AuthorizationStatus.PROVISIONAL,
                                        io.github.kdroidfilter.nucleus.notification.AuthorizationStatus.EPHEMERAL,
                                        -> PermissionState.Granted
                                        io.github.kdroidfilter.nucleus.notification.AuthorizationStatus.DENIED -> PermissionState.Denied
                                        io.github.kdroidfilter.nucleus.notification.AuthorizationStatus.NOT_DETERMINED -> PermissionState.NotRequested
                                    }
                                if (cont.isActive) cont.resume(mapped)
                            }
                        } catch (t: Throwable) {
                            if (cont.isActive) cont.resume(PermissionState.BundleRequired)
                        }
                    }
                } catch (_: Throwable) {
                    _permission.value
                }
            }
        _permission.value = newState
        return newState
    }

    override suspend fun send(spec: NotificationSpec): SendResult {
        if (!_nativeAvailable.value) {
            return runFallback(spec, reason = "native pipeline unavailable")
        }
        if (host == HostOs.MAC && _permission.value !is PermissionState.Granted) {
            return SendResult.Suppressed("macOS notifications not authorized")
        }
        return try {
            withContext(Dispatchers.IO) {
                when (host) {
                    HostOs.MAC -> sendMac(spec)
                    HostOs.WINDOWS -> sendWindows(spec)
                    HostOs.LINUX -> sendLinux(spec)
                    HostOs.UNKNOWN -> runFallback(spec, reason = "unknown platform")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            SendResult.Failed(e)
        }
    }

    override fun release() {
        try {
            fallback.release()
        } catch (_: Throwable) {
        }
    }

    // ---------- macOS ----------

    private fun refreshMacPermissionState() {
        io.github.kdroidfilter.nucleus.notification.NotificationCenter.getNotificationSettings { settings ->
            _permission.value =
                when (settings.authorizationStatus) {
                    io.github.kdroidfilter.nucleus.notification.AuthorizationStatus.AUTHORIZED,
                    io.github.kdroidfilter.nucleus.notification.AuthorizationStatus.PROVISIONAL,
                    io.github.kdroidfilter.nucleus.notification.AuthorizationStatus.EPHEMERAL,
                    -> PermissionState.Granted
                    io.github.kdroidfilter.nucleus.notification.AuthorizationStatus.DENIED -> PermissionState.Denied
                    io.github.kdroidfilter.nucleus.notification.AuthorizationStatus.NOT_DETERMINED -> PermissionState.NotRequested
                }
        }
    }

    private fun sendMac(spec: NotificationSpec): SendResult {
        val content =
            io.github.kdroidfilter.nucleus.notification.NotificationContent(
                title = spec.title,
                body = spec.body,
                sound = io.github.kdroidfilter.nucleus.notification.NotificationSound.Default,
                userInfo =
                    buildMap {
                        spec.deepLinkNoteId?.let { put("noteId", it) }
                        put("kind", spec.kind.name)
                    },
                threadIdentifier = spec.threadId.orEmpty(),
            )
        val request =
            io.github.kdroidfilter.nucleus.notification.NotificationRequest(
                identifier = UUID.randomUUID().toString(),
                content = content,
            )
        io.github.kdroidfilter.nucleus.notification.NotificationCenter
            .add(request)
        return SendResult.Delivered
    }

    // ---------- Windows ----------

    private fun ensureWindowsInitialized() {
        if (winInitialized) return
        try {
            io.github.kdroidfilter.nucleus.notification.windows.WindowsNotificationCenter
                .initialize(aumid = bundleId, appName = appLabel)
            winInitialized = true
        } catch (_: Throwable) {
            // Best-effort; showSimple will surface any hard failure.
        }
    }

    private fun sendWindows(spec: NotificationSpec): SendResult {
        ensureWindowsInitialized()
        io.github.kdroidfilter.nucleus.notification.windows.WindowsNotificationCenter.showSimple(
            title = spec.title,
            body = spec.body,
            group = spec.threadId.orEmpty(),
        )
        return SendResult.Delivered
    }

    // ---------- Linux ----------

    private fun sendLinux(spec: NotificationSpec): SendResult {
        val notification =
            io.github.kdroidfilter.nucleus.notification.linux.Notification(
                appName = appLabel,
                summary = spec.title,
                body = spec.body,
            )
        val id =
            io.github.kdroidfilter.nucleus.notification.linux.LinuxNotificationCenter
                .notify(notification)
        return if (id > 0) SendResult.Delivered else SendResult.Failed(IllegalStateException("libnotify returned id=0"))
    }

    // ---------- Fallback ----------

    private fun runFallback(
        spec: NotificationSpec,
        reason: String,
    ): SendResult {
        val result = fallback.send(spec.title, spec.body)
        return result.fold(
            onSuccess = { SendResult.DeliveredViaFallback },
            onFailure = { SendResult.Failed(IllegalStateException("$reason; AWT fallback also failed: ${it.message}", it)) },
        )
    }

    // ---------- Native probe ----------

    private fun probeNative(): Boolean =
        try {
            when (host) {
                HostOs.MAC ->
                    io.github.kdroidfilter.nucleus.notification.NotificationCenter.isAvailable
                HostOs.WINDOWS ->
                    io.github.kdroidfilter.nucleus.notification.windows.WindowsNotificationCenter.isAvailable
                HostOs.LINUX ->
                    io.github.kdroidfilter.nucleus.notification.linux.LinuxNotificationCenter.isAvailable
                HostOs.UNKNOWN -> false
            }
        } catch (_: Throwable) {
            // ClassNotFoundError, NoClassDefFoundError, UnsatisfiedLinkError, etc.
            false
        }
}
