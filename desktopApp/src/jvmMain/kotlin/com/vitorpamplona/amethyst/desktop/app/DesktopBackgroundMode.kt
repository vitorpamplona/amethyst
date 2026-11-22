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
import dev.nucleusframework.composenativetray.menu.api.TrayMenuBuilder
import dev.nucleusframework.core.runtime.NativeLibraryLoader
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
}

object BackgroundCloseRules {
    /**
     * Closing the main window when it would quit: an account's signer still answering apps keeps
     * the app running in the tray. Without a tray (a Linux desktop with no tray host) the app quits
     * as before: running on with nothing on screen to show it, or to quit it from, is never wanted.
     */
    fun onClose(
        signerRunning: Boolean,
        trayAvailable: Boolean,
    ): BackgroundClose =
        when {
            signerRunning && trayAvailable -> BackgroundClose.TRAY
            else -> BackgroundClose.QUIT
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
 * Whether a tray icon would be seen, asked again each time it matters: the tray library reports no
 * failure of its own, so the window must never hide on a tray that is not there. Blocking: call it
 * off the UI thread.
 */
object SystemTrayHost {
    private const val TAG = "SystemTrayHost"
    private const val WATCHER = "org.kde.StatusNotifierWatcher"

    /** How often a Linux desktop without a tray host is asked again, while a signer runs. */
    const val RECHECK_MS = 30_000L

    /**
     * The tray's native library loads here (ComposeNativeTray's Win32, AppKit or sd-bus library; on
     * Linux it needs the system's libsystemd). Loaded through the same loader the library uses, which
     * remembers it, so the library's own load later is a no-op. Once per run.
     */
    private val nativeTrayLoads: Boolean by lazy {
        val library =
            when {
                PlatformInfo.isLinux -> "LinuxTray"
                PlatformInfo.isWindows -> "WinTray"
                PlatformInfo.isMacOS -> "MacTray"
                else -> return@lazy false
            }
        try {
            NativeLibraryLoader.load(library, TrayMenuBuilder::class.java, resourcePrefix = "/composetray/native")
        } catch (e: Throwable) {
            Log.w(TAG, "The tray's native library did not load; closing the window will quit", e)
            false
        }.also { loaded -> if (!loaded) Log.w(TAG) { "No native tray library for this platform; closing the window will quit" } }
    }

    /**
     * True when a tray icon can show now: its native library loads and, on Linux, a tray host runs.
     * Windows and macOS always have one (the notification area, the menu bar). On Linux the icon is
     * a StatusNotifierItem, shown only while `org.kde.StatusNotifierWatcher` runs: KDE, Cinnamon,
     * XFCE and others do, stock GNOME does not without its AppIndicator extension. The host can come
     * and go while the app runs, so callers ask again before relying on it.
     */
    fun available(): Boolean = nativeTrayLoads && (!PlatformInfo.isLinux || nameOwned(WATCHER))

    /**
     * True when our tray icon is up now, for the moment the window is about to hide behind it. On
     * Linux that means the tray host still runs and the icon's item owns its bus name,
     * `org.kde.StatusNotifierItem-<pid>-1`: the library claims it before registering with the host,
     * and gives up silently when it can't (in a Flatpak sandbox without that `--own-name`, say),
     * leaving no icon at all. The pid is this process's own, as the library's native side sees it,
     * inside a sandbox too. Windows and macOS: the native library loaded.
     */
    fun showing(): Boolean = available() && (!PlatformInfo.isLinux || nameOwned("org.kde.StatusNotifierItem-${ProcessHandle.current().pid()}-1"))

    /** Whether [name] has an owner on the session bus. False when the bus can't be asked. */
    private fun nameOwned(name: String): Boolean {
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
                    "string:$name",
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
                    name,
                ),
            )
        if (gdbus != null) return gdbus.contains("true")
        Log.i(TAG) { "Could not ask the session bus about $name; closing the window will quit" }
        return false
    }

    /**
     * [command]'s output, or null when it is missing, fails or hangs. Waited on before it is read:
     * reading first would block for as long as a stuck command holds its output open, past any
     * timeout. Its answer is a line or two, well under the pipe's buffer.
     */
    private fun ask(command: List<String>): String? =
        try {
            val process =
                ProcessBuilder(command)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                null
            } else if (process.exitValue() != 0) {
                null
            } else {
                process.inputStream.bufferedReader().readText()
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
