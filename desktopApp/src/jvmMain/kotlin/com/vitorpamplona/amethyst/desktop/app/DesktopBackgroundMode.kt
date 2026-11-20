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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.vitorpamplona.amethyst.commons.account.AccountCacheState
import com.vitorpamplona.amethyst.desktop.platform.PlatformInfo
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.util.concurrent.TimeUnit

/**
 * What closing the main window does when it would otherwise quit. Android keeps its NIP-46 signer
 * answering apps from a background service; a desktop app keeps running instead, out of the way.
 */
enum class BackgroundClose {
    /** Nothing needs the app running: quit, as before. */
    QUIT,

    /** Hide the window and keep running behind the tray icon. */
    TRAY,

    /** No tray to come back from (a Linux desktop without a tray host): minimize instead of hiding. */
    MINIMIZE,
}

object BackgroundCloseRules {
    /**
     * Closing the main window when it would quit: an account's signer still answering apps keeps
     * the app running, in the tray where there is one, minimized where there is none (a hidden
     * window with no icon to bring it back would leave the user no way in but a relaunch).
     */
    fun onClose(
        signerRunning: Boolean,
        trayAvailable: Boolean,
    ): BackgroundClose =
        when {
            !signerRunning -> BackgroundClose.QUIT
            trayAvailable -> BackgroundClose.TRAY
            else -> BackgroundClose.MINIMIZE
        }
}

/** True while any loaded account has its NIP-46 signer on, whichever account is on screen. */
@OptIn(ExperimentalCoroutinesApi::class)
fun AccountCacheState.anySignerRunning(): Flow<Boolean> =
    accounts
        .flatMapLatest { loaded ->
            val flags = loaded.values.map { it.settings.nip46SignerEnabled }
            if (flags.isEmpty()) flowOf(false) else combine(flags) { enabled -> enabled.any { it } }
        }.distinctUntilChanged()

/**
 * Whether a tray icon would be seen. Windows and macOS always have one (the notification area, the
 * menu bar). On Linux the icon is a StatusNotifierItem, which shows only while a tray host
 * (`org.kde.StatusNotifierWatcher`) runs: KDE, Cinnamon, XFCE and others do, stock GNOME does not
 * without its AppIndicator extension. Blocking: call it off the UI thread.
 */
object SystemTrayHost {
    private const val TAG = "SystemTrayHost"
    private const val WATCHER = "org.kde.StatusNotifierWatcher"

    fun available(): Boolean {
        if (!PlatformInfo.isLinux) return PlatformInfo.isWindows || PlatformInfo.isMacOS
        // Either tool answers the bus's NameHasOwner; desktops ship at least one of them.
        val dbusSend =
            ask(
                listOf(
                    "dbus-send",
                    "--session",
                    "--print-reply",
                    "--dest=org.freedesktop.DBus",
                    "/org/freedesktop/DBus",
                    "org.freedesktop.DBus.NameHasOwner",
                    "string:$WATCHER",
                ),
            )
        if (dbusSend != null) return dbusSend.contains("boolean true")
        val gdbus =
            ask(
                listOf(
                    "gdbus",
                    "call",
                    "--session",
                    "--dest",
                    "org.freedesktop.DBus",
                    "--object-path",
                    "/org/freedesktop/DBus",
                    "--method",
                    "org.freedesktop.DBus.NameHasOwner",
                    WATCHER,
                ),
            )
        if (gdbus != null) return gdbus.contains("true")
        Log.i(TAG) { "Could not ask the session bus for a tray host; closing the window will minimize it" }
        return false
    }

    /** [command]'s output, or null when it is missing, fails or hangs. */
    private fun ask(command: List<String>): String? =
        try {
            val process =
                ProcessBuilder(command)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
            val output = process.inputStream.bufferedReader().readText()
            when {
                !process.waitFor(2, TimeUnit.SECONDS) -> {
                    process.destroy()
                    null
                }
                process.exitValue() != 0 -> null
                else -> output
            }
        } catch (e: Exception) {
            null
        }
}

/**
 * The main window's lifecycle as the screens see it: the window's own, but no further than CREATED
 * while the window is hidden ([hidden]). Compose Desktop reports a minimized window as CREATED and a
 * hidden one as STARTED, so without this a window closed to the tray would keep every feed
 * subscribed. Capped, the screens behave as Android's do when the app goes to the background: the
 * feeds unsubscribe after their grace period, and only what runs regardless (each account's
 * notifications, DMs and gift wraps, the NIP-46 signer) keeps its relays open.
 *
 * Driven from the UI thread: the window's lifecycle and [hidden] both change there.
 */
class HiddenWindowLifecycle(
    private val window: LifecycleOwner,
) : LifecycleOwner {
    private val registry = LifecycleRegistry.createUnsafe(this)

    override val lifecycle: Lifecycle get() = registry

    var hidden: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            sync()
        }

    private val observer = LifecycleEventObserver { _, _ -> sync() }

    init {
        window.lifecycle.addObserver(observer)
        sync()
    }

    private fun sync() {
        registry.currentState = capped(window.lifecycle.currentState, hidden)
    }

    /** Stops following the window; the screens see it destroyed. */
    fun dispose() {
        window.lifecycle.removeObserver(observer)
        if (registry.currentState != Lifecycle.State.INITIALIZED) registry.currentState = Lifecycle.State.DESTROYED
    }

    companion object {
        /** [windowState], held at CREATED while [hidden]. */
        fun capped(
            windowState: Lifecycle.State,
            hidden: Boolean,
        ): Lifecycle.State = if (hidden && windowState.isAtLeast(Lifecycle.State.STARTED)) Lifecycle.State.CREATED else windowState
    }
}
