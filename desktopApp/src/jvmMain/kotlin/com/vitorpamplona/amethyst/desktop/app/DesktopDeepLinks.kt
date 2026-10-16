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

import com.vitorpamplona.amethyst.desktop.platform.PlatformInfo
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Deep links (`nostr:`/`amethyst:` URIs) handed to the desktop app by the OS.
 *
 * Registration is per platform and lives next to each package format:
 *  - macOS: `CFBundleURLTypes` merged into the packaged Info.plist
 *    (`desktopApp/build.gradle.kts`, `macOS { infoPlist { ... } }`). LaunchServices then
 *    delivers the URI to the *running* app as an AWT `OpenURIEvent` (`setOpenURIHandler`).
 *  - Linux: `MimeType=x-scheme-handler/nostr;...` plus an `Exec ... %u` field code in the
 *    desktop entries (AppImage, Flatpak, and the jpackage-generated .deb/.rpm entries via
 *    `scripts/fix-deb-desktop-entry.sh` and the rpm repackaging in `desktopApp/build.gradle.kts`).
 *  - Windows: `HKCU\Software\Classes\<scheme>` self-registration on startup
 *    ([registerWindowsUrlSchemes]) — per-user, no admin, no installer change.
 *
 * Delivery differs by OS, so there are two paths into [pending]:
 *  - macOS hands the URI to the running process directly ([installMacOsOpenUriHandler]).
 *  - Linux and Windows start a *new* process with the URI as argv. [forwardToRunningInstance]
 *    passes it to the already-running instance over the loopback instance bus and the new
 *    process exits; on a cold start there is no instance, so main() offers the argv URI itself.
 *
 * Consumption is in `DesktopAppRoot.NavigationEffects`: once a logged-in shell exists it
 * turns the URI into a route with the shared `uriToRoute` parser and navigates to it.
 */
object DesktopDeepLinks {
    private const val TAG = "DesktopDeepLinks"

    /** Schemes the packages register; mirrored by the manifest on Android. */
    private val SCHEMES = listOf("nostr:", "amethyst:")

    /**
     * The latest URI waiting for a logged-in navigation host. Conflated like Android's launch
     * intent (the newest link wins); [DesktopAppRoot.NavigationEffects] clears it as it routes.
     */
    val pending = MutableStateFlow<String?>(null)

    /** True when [arg] is a URI of one of the schemes the app registers for. */
    fun isDeepLink(arg: String): Boolean {
        val trimmed = arg.trim().trim('"')
        return SCHEMES.any { trimmed.startsWith(it, ignoreCase = true) }
    }

    /** The first deep-link URI in the process arguments, or null. Linux passes %u, Windows %1. */
    fun extractFromArgs(args: Array<String>): String? = args.firstOrNull(::isDeepLink)?.trim()?.trim('"')

    /** Queues [uri] for routing; anything that is not one of our schemes is refused and logged. */
    fun offer(uri: String) {
        if (isDeepLink(uri)) {
            Log.d(TAG) { "Deep link received: ${uri.take(24)}…" }
            pending.value = uri.trim().trim('"')
        } else {
            Log.w(TAG, "Refused a URI that is not a deep link: ${uri.take(24)}…")
        }
    }

    /** Installs the OS-side delivery mechanisms that are runtime's job (see the class docs). */
    fun installOsHandlers() {
        if (PlatformInfo.isMacOS) installMacOsOpenUriHandler()
        if (PlatformInfo.isWindows) registerWindowsUrlSchemes()
    }

    /**
     * macOS delivers an opened URL to the running app as an AWT OpenURIEvent — but only when the
     * packaged Info.plist declares the scheme (the gradle side of this). A no-op elsewhere, and
     * harmless from `gradle run`, where the event never fires.
     */
    private fun installMacOsOpenUriHandler() {
        try {
            val desktop = java.awt.Desktop.getDesktop()
            if (desktop.isSupported(java.awt.Desktop.Action.APP_OPEN_URI)) {
                desktop.setOpenURIHandler { event ->
                    bringToFront()
                    offer(event.uri.toString())
                }
            } else {
                Log.w(TAG, "APP_OPEN_URI is not supported; nostr: links will not reach the app")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Could not install the open-URI handler", e)
        }
    }

    /**
     * Windows has no per-user installer hook in jpackage's MSI, so the app registers the schemes
     * itself under HKCU (per-user, no elevation): `HKCU\Software\Classes\nostr` and
     * `...\amethyst`, each pointing `shell\open\command` at the running launcher with `%1`.
     * Idempotent and refreshed on every start, so an app update that moves the exe self-heals.
     * Skipped on dev runs: registering `java.exe` would steal the scheme for every JVM on the box.
     */
    private fun registerWindowsUrlSchemes() {
        val exe =
            ProcessHandle
                .current()
                .info()
                .command()
                .orElse(null) ?: return
        if (!exe.endsWith(".exe", ignoreCase = true) ||
            exe.endsWith("java.exe", ignoreCase = true) ||
            exe.endsWith("javaw.exe", ignoreCase = true)
        ) {
            return
        }

        for (scheme in listOf("nostr", "amethyst")) {
            runReg("add", "HKCU\\Software\\Classes\\$scheme", "/ve", "/d", "URL:Nostr URI", "/f")
            runReg("add", "HKCU\\Software\\Classes\\$scheme", "/v", "URL Protocol", "/d", "", "/f")
            runReg("add", "HKCU\\Software\\Classes\\$scheme\\shell\\open\\command", "/ve", "/d", "\"$exe\" \"%1\"", "/f")
        }
    }

    private fun runReg(vararg args: String) {
        try {
            val process = ProcessBuilder("reg", *args).redirectErrorStream(true).start()
            val output =
                process.inputStream
                    .bufferedReader()
                    .readText()
                    .trim()
            if (process.waitFor() != 0) {
                Log.w(TAG, "reg ${args.joinToString(" ")} failed: $output")
            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not run reg ${args.joinToString(" ")}", e)
        }
    }

    /** Asks the OS to surface the window; supported everywhere AWT is, ignored where it is not. */
    private fun bringToFront() {
        runCatching {
            val desktop = java.awt.Desktop.getDesktop()
            if (desktop.isSupported(java.awt.Desktop.Action.APP_REQUEST_FOREGROUND)) {
                desktop.requestForeground(true)
            }
        }
    }

    // --- The instance bus: how a second process hands its URI to the running app ---

    private const val INSTANCE_PORT_FILE = "instance.port"
    private const val MAX_URI_BYTES = 64 * 1024

    @Volatile
    private var server: ServerSocket? = null

    /**
     * Sends [uri] to the instance that owns the bus, if one is alive. The port file going stale
     * (a killed instance) reads as a connect failure and simply means "no running instance".
     */
    fun forwardToRunningInstance(
        uri: String,
        dataDir: File,
    ): Boolean {
        val port =
            File(dataDir, INSTANCE_PORT_FILE)
                .takeIf { it.isFile }
                ?.readText()
                ?.trim()
                ?.toIntOrNull() ?: return false
        return try {
            Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
                socket.soTimeout = 2_000
                socket.getOutputStream().writer().use { it.write(uri + "\n") }
            }
            true
        } catch (e: IOException) {
            false
        }
    }

    /**
     * Starts the loopback listener other processes forward their deep links to. Loopback-only,
     * and only lines that parse as one of our schemes are accepted — a local process can do no
     * more than ask the app to display a route, exactly what clicking a link does.
     */
    fun startInstanceBus(dataDir: File) {
        if (server != null) return
        val socket = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        server = socket

        val portFile = File(dataDir, INSTANCE_PORT_FILE)
        portFile.writeText(socket.localPort.toString())
        Runtime.getRuntime().addShutdownHook(
            Thread {
                runCatching { socket.close() }
                portFile.delete()
            },
        )

        thread(isDaemon = true, name = "deep-link-bus") {
            while (true) {
                val accepted =
                    try {
                        socket.accept()
                    } catch (e: IOException) {
                        return@thread // closed on shutdown
                    }
                thread(isDaemon = true) {
                    accepted.use { conn ->
                        val line =
                            runCatching {
                                String(conn.getInputStream().readNBytes(MAX_URI_BYTES), Charsets.UTF_8)
                                    .lineSequence()
                                    .firstOrNull()
                            }.getOrNull()
                        if (line != null && isDeepLink(line)) {
                            bringToFront()
                            offer(line)
                        }
                    }
                }
            }
        }
    }
}
