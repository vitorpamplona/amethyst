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
package com.vitorpamplona.amethyst.desktop.platform

import com.vitorpamplona.quartz.utils.Log
import java.awt.Toolkit
import java.io.File
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.SocketChannel

/**
 * Picks how the app talks to a Linux display: natively over Wayland, or over X11 (XWayland on a
 * Wayland session). Must run in `main()` before anything touches AWT, since AWT reads the toolkit
 * once, when it starts.
 *
 * The native path needs two things the stock JDK lacks. The toolkit: OpenJDK's AWT only speaks X11,
 * so the Linux packages bundle the JetBrains Runtime, whose `WLToolkit` speaks Wayland. And a way to
 * draw: Skiko's window surface locks the window through JAWT to get its X11 handle, which fails under
 * `WLToolkit`, so on Wayland the windows draw through Swing ([WaylandWindow]) with Skiko's CPU
 * renderer.
 *
 * `AMETHYST_X11=1` keeps a Wayland session on XWayland, as before.
 */
object WaylandDisplay {
    /**
     * True when AWT runs on the Wayland toolkit, whoever picked it: [configure], or a hand-set
     * `-Dawt.toolkit.name=WLToolkit` that [configure] would have skipped. The windows must follow the
     * toolkit AWT actually started, or Compose's window opens where it can't draw. Reading it starts
     * AWT, so only ask once [configure] has run.
     */
    val isActive: Boolean by lazy {
        val active = Toolkit.getDefaultToolkit().javaClass.name == WAYLAND_TOOLKIT
        if (active) preferServerSideDecorations()
        active
    }

    /**
     * The title bar. Where the compositor draws window decorations (KDE, wlroots compositors such as
     * sway, Hyprland, COSMIC, niri), the window gets the same title bar as every other app there;
     * left to itself JBR draws its own: a plain gray bar on KDE, a GTK one elsewhere. GNOME draws
     * none, so it keeps JBR's GTK bar. JBR's "server" style falls back to the plain bar when the
     * compositor can't, so the compositor is asked first.
     *
     * JBR reads the style once, when the first window opens: this runs as the toolkit starts, before
     * any window. Needs `--add-exports java.desktop/sun.awt.wl` (set in the build); without it, or on a
     * runtime without the method, JBR's default stays.
     */
    private fun preferServerSideDecorations() {
        if (System.getProperty(DECORATION_STYLE) != null) return
        val available =
            try {
                Class.forName(WAYLAND_TOOLKIT).getMethod("isSSDAvailable").invoke(null) == true
            } catch (e: ReflectiveOperationException) {
                Log.w("WaylandDisplay", "Could not ask the compositor for its window decorations", e)
                false
            }
        if (available) System.setProperty(DECORATION_STYLE, "server")
    }

    fun configure() {
        if (!PlatformInfo.host.isLinux) return
        if (System.getenv("AMETHYST_X11") == "1") return
        // No check for a toolkit picked by hand: the JDK always sets awt.toolkit.name (XToolkit).
        if (!hasWaylandSocket()) return
        if (!runtimeHasWaylandToolkit()) return

        System.setProperty("awt.toolkit.name", "WLToolkit")
        // The Wayland app_id, which docks and app switchers match against a desktop entry, as X11's
        // WM_CLASS. The Flatpak's entry is named after its app ID; the .deb, .rpm and AppImage entries
        // declare StartupWMClass=Amethyst, which GNOME and KDE match the app_id against too.
        System.setProperty("awt.app.id", System.getenv("FLATPAK_ID")?.takeIf { it.isNotBlank() } ?: "Amethyst")
        // JBR draws its own drop shadow under undecorated windows. The app's only one is the video's
        // full-screen overlay, which is transparent while it fades in: the shadow darkened the screen
        // behind it.
        if (System.getProperty("sun.awt.wl.Shadow") == null) System.setProperty("sun.awt.wl.Shadow", "false")
        // Skiko's OpenGL path needs an X11 display; skip the failed attempt and its stack trace.
        if (System.getProperty("skiko.renderApi") == null && System.getenv("SKIKO_RENDER_API") == null) {
            System.setProperty("skiko.renderApi", "SOFTWARE")
        }
    }

    /**
     * The compositor's socket: `$WAYLAND_DISPLAY`, relative to `$XDG_RUNTIME_DIR` unless absolute. A
     * Flatpak without the wayland socket, or an X11 session, has none, and WLToolkit would fail to start.
     * So would a socket file left behind by a compositor that is gone (a shell that kept the variable
     * from an old Wayland session), so the socket must also accept a connection.
     */
    private fun hasWaylandSocket(): Boolean {
        val display = System.getenv("WAYLAND_DISPLAY")?.takeIf { it.isNotBlank() } ?: return false
        val socket =
            if (display.startsWith("/")) {
                File(display)
            } else {
                val runtimeDir = System.getenv("XDG_RUNTIME_DIR")?.takeIf { it.isNotBlank() } ?: return false
                File(runtimeDir, display)
            }
        if (!socket.exists()) return false
        return try {
            SocketChannel.open(StandardProtocolFamily.UNIX).use { it.connect(UnixDomainSocketAddress.of(socket.toPath())) }
            true
        } catch (e: IOException) {
            Log.w("WaylandDisplay", "No compositor answers on ${socket.path}; using X11", e)
            false
        }
    }

    /** The JetBrains Runtime has it; a stock JDK (a `./gradlew :desktopApp:run` on Temurin, say) does not. */
    private fun runtimeHasWaylandToolkit(): Boolean =
        try {
            Class.forName(WAYLAND_TOOLKIT, false, ClassLoader.getSystemClassLoader())
            true
        } catch (_: ClassNotFoundException) {
            false
        } catch (_: LinkageError) {
            false
        }
}

private const val WAYLAND_TOOLKIT = "sun.awt.wl.WLToolkit"
private const val DECORATION_STYLE = "sun.awt.wl.WindowDecorationStyle"
