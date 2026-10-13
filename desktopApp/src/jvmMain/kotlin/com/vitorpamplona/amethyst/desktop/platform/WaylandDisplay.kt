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

import java.awt.Toolkit
import java.io.File

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
    val isActive: Boolean by lazy { Toolkit.getDefaultToolkit().javaClass.name == WAYLAND_TOOLKIT }

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
        return socket.exists()
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
