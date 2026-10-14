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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.vitorpamplona.quartz.utils.Log
import java.awt.Component
import java.awt.GraphicsEnvironment
import java.awt.Toolkit

/**
 * The part of the desktop's scale that AWT leaves out, which the app applies itself as Compose
 * density.
 *
 * A Linux desktop at 125% or 150% tells X11 apps through `Xft.dpi` (120, 144): KDE does when its
 * legacy X11 apps "apply scaling themselves", GNOME and others always do. AWT only takes whole
 * scales on X11 (`GDK_SCALE`), so at 125% the app drew at 100%: sharp, but too small. Drawing at
 * the full scale here keeps every pixel on the screen's grid, unlike the Wayland toolkit, which
 * draws at the next whole scale and has the compositor shrink the picture (JBR-10582).
 *
 * Only on X11. On Wayland the toolkit picks the scale, and on macOS and Windows AWT already takes
 * fractional ones. `AMETHYST_SCALE=1.25` sets it by hand where the desktop says nothing.
 */
object DesktopUiScale {
    /** At least 1: 1 wherever AWT already draws at the desktop's scale. */
    val extra: Float by lazy { detect() }

    private fun detect(): Float {
        if (!PlatformInfo.host.isLinux || GraphicsEnvironment.isHeadless() || WaylandDisplay.isActive) return 1f
        val desktop = manualScale() ?: XftDpi.read()?.let { it / BASE_DPI } ?: return 1f
        val awt = awtScale()
        val extra = extraScale(desktop, awt)
        if (extra != 1f) Log.i("DesktopUiScale") { "Desktop scale $desktop, AWT $awt: drawing ${extra}x larger" }
        return extra
    }

    private fun manualScale(): Float? =
        System
            .getenv("AMETHYST_SCALE")
            ?.trim()
            ?.toFloatOrNull()
            ?.takeIf { it in MIN_SCALE..MAX_SCALE }

    private fun awtScale(): Float =
        try {
            GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .defaultScreenDevice.defaultConfiguration.defaultTransform
                .scaleX
                .toFloat()
        } catch (e: Exception) {
            1f
        }
}

/** The DPI X11 calls 100%. */
private const val BASE_DPI = 96f
private const val MIN_SCALE = 0.5f
private const val MAX_SCALE = 4f

/**
 * How much larger than AWT the app must draw for the desktop's [desktopScale]. Never smaller than
 * AWT: a desktop asking for less (a `GDK_SCALE=2` session whose `Xft.dpi` says 1.5) keeps AWT's.
 */
internal fun extraScale(
    desktopScale: Float,
    awtScale: Float,
): Float {
    val extra = desktopScale / awtScale
    // Below KDE's smallest step (105%) is rounding in the DPI, not a scale.
    return if (extra > 1.04f) extra.coerceAtMost(MAX_SCALE) else 1f
}

/** Draws [content] at [DesktopUiScale]: what every window's content goes through. */
@Composable
fun ProvideDesktopUiScale(content: @Composable () -> Unit) {
    val extra = DesktopUiScale.extra
    if (extra == 1f) {
        content()
        return
    }
    val base = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(base.density * extra, base.fontScale), content = content)
}

/**
 * Pixels per AWT unit for [component]: what converts AWT's window and event coordinates to
 * Compose's pixels. Not `LocalDensity`, which also carries [DesktopUiScale].
 */
fun awtPixelScale(component: Component?): Float =
    component
        ?.graphicsConfiguration
        ?.defaultTransform
        ?.scaleX
        ?.toFloat() ?: 1f

/** `Xft.dpi`, from the XSETTINGS daemon or, failing that, the X server's resource database. */
internal object XftDpi {
    fun read(): Float? = fromXSettings() ?: fromResources()

    /** GNOME's settings daemon and KDE's gtkconfig publish it here, in 1/1024ths of a DPI. */
    private fun fromXSettings(): Float? = (Toolkit.getDefaultToolkit().getDesktopProperty("gnome.Xft/DPI") as? Int)?.takeIf { it > 0 }?.let { it / 1024f }

    /** What `xrdb -query` prints: KDE and most desktops set `Xft.dpi` there. */
    private fun fromResources(): Float? =
        try {
            val xlib = Native.load("X11", Xlib::class.java)
            val display = xlib.XOpenDisplay(null) ?: return null
            try {
                xlib.XResourceManagerString(display)?.let(::parseXftDpi)
            } finally {
                xlib.XCloseDisplay(display)
            }
        } catch (e: Throwable) {
            // No libX11, or no server to ask: no scale to read.
            Log.w("DesktopUiScale", "Could not read the X resources", e)
            null
        }

    @Suppress("FunctionName")
    private interface Xlib : Library {
        fun XOpenDisplay(name: String?): Pointer?

        fun XResourceManagerString(display: Pointer): String?

        fun XCloseDisplay(display: Pointer): Int
    }
}

/** `Xft.dpi` in an X resource database (`Xft.dpi:\t120`), or null when it is missing or nonsense. */
internal fun parseXftDpi(resources: String): Float? =
    resources
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("Xft.dpi:") }
        ?.substringAfter(':')
        ?.trim()
        ?.toFloatOrNull()
        ?.takeIf { it / BASE_DPI in MIN_SCALE..MAX_SCALE }
