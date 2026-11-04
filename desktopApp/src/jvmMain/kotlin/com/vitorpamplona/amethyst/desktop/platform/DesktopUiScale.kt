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
import java.awt.GraphicsConfiguration
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import kotlin.math.abs

/**
 * The part of the desktop's scale that AWT leaves out, which the app applies itself through
 * Compose's density and font scale.
 *
 * A Linux desktop at 125% or 150% tells X11 apps through `Xft.dpi` (120, 144): KDE does when its
 * legacy X11 apps "apply scaling themselves". AWT only takes whole scales on X11 (`GDK_SCALE`), so
 * at 125% the app drew at 100%: sharp, but too small. Drawing at the full scale here keeps every
 * pixel on the screen's grid, unlike the Wayland toolkit, which draws at the next whole scale and
 * has the compositor shrink the picture (JBR-10582).
 *
 * GNOME has no fractional scale on X11: there `Xft.dpi` past AWT's scale is its text size (Large
 * Text), so it grows the fonts only, as it does in GTK apps.
 *
 * Only on X11. On Wayland the toolkit picks the scale, and on macOS and Windows AWT already takes
 * fractional ones. `AMETHYST_SCALE=1.25` sets the whole scale by hand, larger or smaller.
 */
object DesktopUiScale {
    /** How much denser than AWT the app draws: 1 wherever AWT already draws at the desktop's scale. */
    val extra: Float get() = scale.density

    /** How much larger than the desktop's own the app's text is: GNOME's text size on X11. */
    val extraFont: Float get() = scale.fontScale

    private val scale: UiScale by lazy { detect() }

    /** The X server's resources, read by [readXResources] before AWT started. */
    @Volatile private var xResources: String? = null

    /**
     * Reads the X server's resources, from `main()` before anything starts AWT. Xlib isn't
     * thread-safe unless every user calls `XInitThreads` first, and AWT doesn't: opening a second
     * connection once AWT's own thread is talking to the server could corrupt Xlib's state.
     */
    fun readXResources() {
        if (!PlatformInfo.host.isLinux || System.getenv("DISPLAY").isNullOrBlank()) return
        // Native Wayland ([WaylandDisplay.configure] picked it): there is no X11 scale to read.
        if (System.getProperty("awt.toolkit.name") == "WLToolkit") return
        xResources = XResources.read()
    }

    private fun detect(): UiScale {
        if (!PlatformInfo.host.isLinux || GraphicsEnvironment.isHeadless() || WaylandDisplay.isActive) return UiScale.NONE
        val awt = defaultScreenConfiguration()?.pixelScale() ?: 1f
        val scale =
            manualScale()?.let { UiScale(density = manualExtraScale(it, awt)) }
                ?: xftDpi()?.let { dpi ->
                    val extra = extraScale(dpi / BASE_DPI, awt)
                    if (PlatformInfo.host == Platform.GNOME) UiScale(fontScale = extra) else UiScale(density = extra)
                }
                ?: UiScale.NONE
        if (scale != UiScale.NONE) Log.i("DesktopUiScale") { "AWT draws at ${awt}x; the app adds ${scale.density}x density, ${scale.fontScale}x text" }
        return scale
    }

    private fun manualScale(): Float? =
        System
            .getenv("AMETHYST_SCALE")
            ?.trim()
            ?.toFloatOrNull()
            ?.takeIf { it in MIN_SCALE..MAX_SCALE }

    /** GNOME's settings daemon and KDE's gtkconfig publish it over XSETTINGS, in 1/1024ths of a DPI. */
    private fun xftDpi(): Float? {
        val xsettings = (Toolkit.getDefaultToolkit().getDesktopProperty("gnome.Xft/DPI") as? Int)?.let { it / 1024f }
        return xsettings?.takeIf(::isPlausibleDpi) ?: xResources?.let(::parseXftDpi)
    }

    private fun defaultScreenConfiguration(): GraphicsConfiguration? =
        try {
            GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
        } catch (e: Exception) {
            null
        }
}

internal data class UiScale(
    val density: Float = 1f,
    val fontScale: Float = 1f,
) {
    companion object {
        val NONE = UiScale()
    }
}

/** The DPI X11 calls 100%. */
private const val BASE_DPI = 96f
private const val MIN_SCALE = 0.5f
private const val MAX_SCALE = 4f

/** Below KDE's smallest step (105%), a difference is rounding in the DPI, not a scale. */
private const val MIN_STEP = 0.04f

/**
 * How much larger than AWT the app must draw for the desktop's [desktopScale]. Never smaller than
 * AWT: a desktop asking for less (a `GDK_SCALE=2` session whose `Xft.dpi` says 1.5) keeps AWT's.
 */
internal fun extraScale(
    desktopScale: Float,
    awtScale: Float,
): Float {
    val extra = desktopScale / awtScale
    return if (extra > 1f + MIN_STEP) extra.coerceAtMost(MAX_SCALE) else 1f
}

/** What `AMETHYST_SCALE=[manualScale]` adds to AWT's [awtScale]: by hand, it may also shrink. */
internal fun manualExtraScale(
    manualScale: Float,
    awtScale: Float,
): Float {
    val extra = manualScale / awtScale
    return if (abs(extra - 1f) > MIN_STEP) extra else 1f
}

private fun isPlausibleDpi(dpi: Float) = dpi / BASE_DPI in MIN_SCALE..MAX_SCALE

/** Draws [content] at [DesktopUiScale]: what every window's content goes through. */
@Composable
fun ProvideDesktopUiScale(content: @Composable () -> Unit) {
    val density = DesktopUiScale.extra
    val fontScale = DesktopUiScale.extraFont
    if (density == 1f && fontScale == 1f) {
        content()
        return
    }
    val base = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(base.density * density, base.fontScale * fontScale), content = content)
}

/**
 * Pixels per AWT unit for [component]: what converts AWT's window and event coordinates to
 * Compose's pixels. Not `LocalDensity`, which also carries [DesktopUiScale].
 */
fun awtPixelScale(component: Component?): Float = component?.graphicsConfiguration?.pixelScale() ?: 1f

private fun GraphicsConfiguration.pixelScale(): Float = defaultTransform.scaleX.toFloat()

/** The X server's resource database, as `xrdb -query` prints it. */
private object XResources {
    fun read(): String? =
        try {
            val xlib = Native.load("X11", Xlib::class.java)
            val display = xlib.XOpenDisplay(null)
            if (display == null) {
                null
            } else {
                try {
                    xlib.XResourceManagerString(display)
                } finally {
                    xlib.XCloseDisplay(display)
                }
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
        ?.takeIf(::isPlausibleDpi)
