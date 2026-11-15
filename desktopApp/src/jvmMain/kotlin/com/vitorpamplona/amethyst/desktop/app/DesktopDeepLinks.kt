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
import java.awt.Desktop
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
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
 *    ([registerWindowsUrlSchemes]) — per-user, no admin, no installer change, off the startup
 *    path, and only for schemes that are unclaimed or already point at this app.
 *
 * Delivery differs by OS, so there are two paths into [pending]:
 *  - macOS hands the URI to the running process directly ([installMacOsOpenUriHandler]).
 *  - Linux and Windows start a *new* process with the URI as argv. [forwardToRunningInstance]
 *    passes it to the already-running instance over the loopback instance bus and the new
 *    process exits once the instance acknowledges the link; on a cold start there is no
 *    instance, so main() offers the argv URI itself.
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

    /**
     * Installs the OS-side delivery mechanisms that are runtime's job (see the class docs). The
     * macOS handler must be in place before AWT can deliver an open-URI event, so it installs
     * synchronously; the Windows registry writes shell out to reg.exe once per value, so they
     * run on a daemon thread instead of delaying the first window.
     */
    fun installOsHandlers() {
        if (PlatformInfo.isMacOS) installMacOsOpenUriHandler()
        if (PlatformInfo.isWindows) {
            thread(isDaemon = true, name = "win-url-schemes") { registerWindowsUrlSchemes() }
        }
    }

    /**
     * macOS delivers an opened URL to the running app as an AWT OpenURIEvent — but only when the
     * packaged Info.plist declares the scheme (the gradle side of this). A no-op elsewhere, and
     * harmless from `gradle run`, where the event never fires.
     */
    private fun installMacOsOpenUriHandler() {
        try {
            val desktop = Desktop.getDesktop()
            if (desktop.isSupported(Desktop.Action.APP_OPEN_URI)) {
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
     * A scheme is only written when nobody owns it or the current handler already points at this
     * exe — a user who picked another Nostr client keeps it, because the registration would
     * otherwise steal the scheme back on every start. Skipped on dev runs: registering
     * `java.exe` would steal the scheme for every JVM on the box.
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

        val toRegister =
            listOf("nostr", "amethyst").filter { scheme ->
                val current = currentWindowsHandler(scheme)
                val ownedElsewhere = current != null && !current.contains(exe, ignoreCase = true)
                if (ownedElsewhere) {
                    Log.i(TAG) { "Another app handles $scheme:, leaving its registration alone" }
                }
                !ownedElsewhere
            }
        if (toRegister.isEmpty()) return

        importWindowsUrlSchemes(exe, toRegister)
    }

    /**
     * Builds the .reg file content registering [schemes] to open with [exe]. The open command
     * — `"<exe>" "%1"` — cannot go through `reg add`: the JVM flattens ProcessBuilder
     * arguments into one command line and the value's embedded quotes come out mangled, so
     * reg.exe exits with "Invalid syntax" and the scheme keys end up without their
     * shell\open\command (observed on real Windows 11 ARM64). The .reg file format has real
     * escaping rules — `\\` for a backslash, `\"` for a quote inside a string — so the
     * value survives the import intact.
     */
    internal fun windowsUrlSchemeRegFile(
        exe: String,
        schemes: List<String>,
    ): String {
        val escapedExe = exe.replace("\\", "\\\\").replace("\"", "\\\"")
        val body =
            schemes.joinToString("\r\n") { scheme ->
                """
                |[HKEY_CURRENT_USER\Software\Classes\$scheme]
                |@="URL:Nostr URI"
                |"URL Protocol"=""
                |
                |[HKEY_CURRENT_USER\Software\Classes\$scheme\shell\open\command]
                |@="\"$escapedExe\" \"%1\""
                """.trimMargin()
            }
        return "Windows Registry Editor Version 5.00\r\n\r\n$body\r\n"
    }

    /**
     * Writes [schemes]' registration to a temporary .reg file and imports it with reg.exe.
     * UTF-16 LE with a BOM because that is what reg.exe's own export writes and its import
     * reads; the file path is the only command-line argument, and a plain path (unlike the
     * quoted open command) survives ProcessBuilder argument flattening.
     */
    private fun importWindowsUrlSchemes(
        exe: String,
        schemes: List<String>,
    ) {
        val file =
            try {
                Files.createTempFile("amethyst-url-schemes-", ".reg")
            } catch (e: IOException) {
                Log.w(TAG, "Could not create the URL scheme registration file", e)
                return
            }
        try {
            val content = windowsUrlSchemeRegFile(exe, schemes)
            Files.write(
                file,
                byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + content.toByteArray(Charsets.UTF_16LE),
            )
            val process =
                ProcessBuilder("reg", "import", file.toString())
                    .redirectErrorStream(true)
                    .start()
            val output =
                process.inputStream
                    .bufferedReader()
                    .readText()
                    .trim()
            if (process.waitFor() != 0) {
                Log.w(TAG, "reg import $file failed: $output")
            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not import the URL scheme registration", e)
        } finally {
            runCatching { Files.deleteIfExists(file) }
        }
    }

    /**
     * The open command currently registered for [scheme], or null when the scheme is unclaimed
     * (or the query fails, in which case the adds below log their own failure). Read from HKCR,
     * the merged view of HKLM and HKCU — an HKCU entry wins there — so this sees the handler a
     * link click would actually launch, whichever app registered it.
     */
    private fun currentWindowsHandler(scheme: String): String? {
        val output =
            try {
                val process =
                    ProcessBuilder("reg", "query", "HKCR\\$scheme\\shell\\open\\command", "/ve")
                        .redirectErrorStream(true)
                        .start()
                val text = process.inputStream.bufferedReader().readText()
                if (process.waitFor() != 0) return null
                text
            } catch (e: IOException) {
                Log.w(TAG, "Could not read the current $scheme: handler", e)
                return null
            }
        // The value line looks like:    (Default)    REG_SZ    "C:\...\app.exe" "%1"
        return output
            .lineSequence()
            .firstOrNull { it.contains("REG_SZ") }
            ?.substringAfter("REG_SZ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    /** How a routed link brings the app forward; a test swaps it for a slow one. */
    @Volatile
    internal var requestForeground: () -> Unit = ::bringToFront

    /** Asks the OS to surface the window; supported everywhere AWT is, ignored where it is not. */
    private fun bringToFront() {
        runCatching {
            val desktop = Desktop.getDesktop()
            if (desktop.isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) {
                desktop.requestForeground(true)
            }
        }
    }

    // --- The instance bus: how a second process hands its URI to the running app ---

    private const val INSTANCE_PORT_FILE = "instance.port"

    /** How long an accepted connection may take to send its line before it is dropped. */
    private const val BUS_READ_TIMEOUT_MS = 5_000

    /** The running instance's answer to a link it accepted; no answer means no live instance. */
    private const val BUS_ACK = "ok"

    @Volatile
    private var server: ServerSocket? = null

    /**
     * Sends [uri] to the instance that owns the bus, if one is alive, and waits for its ack.
     * The port file going stale (a killed instance) reads as a connect failure, and a port some
     * other process now owns never answers with the ack — both simply mean "no running
     * instance", so the caller routes the link itself instead of losing it.
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
                socket.getOutputStream().write((uri + "\n").toByteArray(Charsets.UTF_8))
                socket.getOutputStream().flush()
                socket.getInputStream().bufferedReader().readLine() == BUS_ACK
            }
        } catch (e: IOException) {
            false
        }
    }

    /**
     * Starts the loopback listener other processes forward their deep links to. Loopback-only,
     * and only lines that parse as one of our schemes are accepted — a local process can do no
     * more than ask the app to display a route, exactly what clicking a link does. Each accepted
     * link is answered with the ack [forwardToRunningInstance] waits for. A failure to start
     * (the socket, the port file) is logged and leaves the app running without deep links.
     */
    fun startInstanceBus(dataDir: File) {
        if (server != null) return
        try {
            bindInstanceBus(dataDir)
        } catch (e: Exception) {
            Log.w(TAG, "Could not start the instance bus; deep links are off this session", e)
        }
    }

    /** Binds the bus and starts its accept loop, cleaning up the socket and port file on failure. */
    private fun bindInstanceBus(dataDir: File) {
        val socket = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        val portFile = File(dataDir, INSTANCE_PORT_FILE)
        try {
            portFile.writeText(socket.localPort.toString())
            server = socket

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
                    thread(isDaemon = true) { handleBusConnection(accepted) }
                }
            }
        } catch (e: Exception) {
            server = null
            runCatching { socket.close() }
            portFile.delete()
            throw e
        }
    }

    /**
     * Reads a single line — bounded by a read timeout so a stalled sender cannot pin the thread
     * — routes it if it is one of our schemes, and answers with the ack the forwarder waits for.
     * Anything else gets no ack and is dropped when the connection closes.
     */
    private fun handleBusConnection(conn: Socket) {
        try {
            conn.soTimeout = BUS_READ_TIMEOUT_MS
            val line = conn.getInputStream().bufferedReader().readLine()
            if (line != null && isDeepLink(line)) {
                offer(line)
                conn.getOutputStream().write("$BUS_ACK\n".toByteArray(Charsets.UTF_8))
                conn.getOutputStream().flush()
                // After the ack: the first foreground request initialises the desktop toolkit (GTK
                // on Linux), which can outlast the forwarder's 2 s wait, and a late ack made it
                // open a second copy of the app.
                requestForeground()
            }
        } catch (e: IOException) {
            Log.w(TAG, "Instance bus connection failed", e)
        } finally {
            runCatching { conn.close() }
        }
    }
}
