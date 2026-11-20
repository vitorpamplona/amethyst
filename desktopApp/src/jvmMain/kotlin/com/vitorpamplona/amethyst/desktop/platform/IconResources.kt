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

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.vitorpamplona.quartz.utils.Log
import org.jetbrains.skia.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

/**
 * Lazily-memoized icon resources shared across all consumers of `/icon.png`.
 *
 * Before this object existed the desktop launch path read and decoded the
 * icon four separate times on the cold-boot critical path (taskbar setup,
 * Window icon, Tor splash, account-loading splash). The taskbar and Window
 * sites also paid an `ImageIO.read` to obtain a `BufferedImage` before
 * either passing it straight to `Taskbar.iconImage` (taskbar) or
 * round-tripping it back through `ImageIO.write` so Skia can decode it
 * (Window icon).
 *
 * This object collapses the work to one resource read plus at most one
 * decode per output shape. All properties are `lazy { ... }` so the cost
 * is paid only when first observed.
 *
 * See desktopApp/plans/2026-06-17-feat-app-launch-optimization-plan.md
 * § Phase 5.1.
 */
object IconResources {
    /** Raw PNG bytes read from the `/icon.png` classpath resource. */
    val iconBytes: ByteArray by lazy {
        IconResources::class.java.getResourceAsStream("/icon.png")!!.readBytes()
    }

    /**
     * The icon as files for the system tray, which loads its image from a path: an `.ico` for
     * Windows' notification area, the PNG for macOS's menu bar and Linux's StatusNotifierItem.
     * Kept in [dir] (the app's data folder, so nothing piles up in the temp folder after a crash)
     * and rewritten only when missing or changed. Null when that fails: the tray is then left out.
     * Blocking: call it off the UI thread, when the tray is about to show.
     */
    fun trayIconFiles(dir: File): TrayIconFiles? =
        try {
            if (!dir.isDirectory && !dir.mkdirs()) throw IOException("could not create $dir")

            fun copy(resource: String): String {
                val bytes = IconResources::class.java.getResourceAsStream("/$resource")!!.use { it.readBytes() }
                val file = File(dir, resource)
                if (!file.isFile || !file.readBytes().contentEquals(bytes)) file.writeBytes(bytes)
                return file.absolutePath
            }
            TrayIconFiles(png = copy("icon.png"), ico = copy("icon.ico"))
        } catch (e: Exception) {
            Log.w("IconResources", "Could not write the tray icon", e)
            null
        }

    /** Decoded `BufferedImage` for AWT consumers (Taskbar, Window icon source). */
    val rawBufferedImage: BufferedImage by lazy {
        ImageIO.read(ByteArrayInputStream(iconBytes))
    }

    /**
     * Platform-adapted icon (squircle on macOS, raw transparent PNG elsewhere).
     * Returns `null` when adaptation fails or is unsupported — callers should
     * fall back to [rawBufferedImage].
     */
    val adaptedBufferedImage: BufferedImage? by lazy {
        PlatformAppIcon.adaptForHost(rawBufferedImage)
    }

    /**
     * Compose `BitmapPainter` for the raw PNG bytes. Used by splash screens
     * (Tor connecting, account loading) that paint the un-adapted logo with
     * a Material `tint`.
     */
    val rawBitmapPainter: BitmapPainter by lazy {
        BitmapPainter(Image.makeFromEncoded(iconBytes).toComposeImageBitmap())
    }

    /**
     * Compose `BitmapPainter` for the platform-adapted icon. Used by the
     * main `Window(icon = …)` parameter so the title-bar / taskbar thumbnail
     * matches the dock icon shape on macOS.
     */
    val adaptedBitmapPainter: BitmapPainter by lazy {
        if (PlatformInfo.host == Platform.MACOS) {
            // Straight from the pixels: a PNG round-trip of the 1024 px icon cost an encode and a decode.
            BitmapPainter((adaptedBufferedImage ?: rawBufferedImage).toComposeImageBitmap())
        } else {
            // Elsewhere the adapted icon is the PNG itself: Skia decodes it natively, where ImageIO
            // plus a pixel-by-pixel copy cost ~200 ms of the window's creation on the UI thread.
            rawBitmapPainter
        }
    }
}

/** The tray icon's files: [png] for macOS and Linux, [ico] for Windows. */
class TrayIconFiles(
    val png: String,
    val ico: String,
)
