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
package com.vitorpamplona.amethyst.desktop.napplet

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.cef.browser.OffscreenCefBrowser
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.awt.Canvas
import java.awt.Cursor
import java.awt.Rectangle
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.nio.ByteBuffer
import javax.swing.SwingUtilities
import kotlin.math.roundToInt

/**
 * The page of an off-screen napplet browser, for a note to draw and drive: its frames as an
 * [ImageBitmap], its cursor, and the input the note forwards to it. Positions are in the page's
 * device-independent pixels.
 */
class OffscreenNappletView : OffscreenCefBrowser.Listener {
    private val mutableFrame = MutableStateFlow<ImageBitmap?>(null)

    /** The page's last frame, at its size times the screen's scale. */
    val frame: StateFlow<ImageBitmap?> = mutableFrame.asStateFlow()

    private val mutableCursor = MutableStateFlow(Cursor.getDefaultCursor())
    val cursor: StateFlow<Cursor> = mutableCursor.asStateFlow()

    @Volatile
    internal var browser: OffscreenCefBrowser? = null
        set(value) {
            field = value
            value?.resize(width, height, scale)
        }

    @Volatile private var width = 1

    @Volatile private var height = 1

    @Volatile private var scale = 1.0

    // Painting state, touched only on Chromium's UI thread.
    private var pixels = ByteArray(0)
    private var frameWidth = 0
    private var frameHeight = 0
    private var popupPixels: ByteArray? = null
    private var popupWidth = 0
    private var popupHeight = 0
    private var popupShown = false
    private var popupBounds: Rectangle? = null

    // Frames published lately, newest last (Chromium's UI thread). A bitmap's native pixels go when it
    // is closed, not when it is collected, and a frame a second's worth of garbage-free painting
    // would otherwise hold hundreds of megabytes until a collection. One replaced a few frames ago is
    // closed on the AWT thread, where Compose draws, so it is never closed mid-draw.
    private val published = ArrayDeque<Bitmap>()

    // Set once the last frame became the note's poster: nothing is published or closed after it.
    @Volatile
    private var stopped = false

    // A key press whose character no typed event delivered (see [key]).
    private var untyped: KeyEvent? = null

    /** The page is [width] x [height] device-independent pixels on a screen of [scale]. */
    fun resize(
        width: Int,
        height: Int,
        scale: Double,
    ) {
        if (width == this.width && height == this.height && scale == this.scale) return
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
        this.scale = scale
        browser?.resize(this.width, this.height, scale)
    }

    /**
     * Stops publishing frames and hands over the last one, which is never closed: it outlives the
     * page as the note's poster.
     */
    fun keepLastFrame(): ImageBitmap? {
        stopped = true
        return mutableFrame.value
    }

    fun focus(focused: Boolean) {
        browser?.setFocus(focused)
    }

    /** A mouse event like [source] (its button, modifiers and clicks), at [x], [y] on the page. */
    fun mouse(
        source: MouseEvent,
        id: Int,
        x: Int,
        y: Int,
    ) {
        val browser = browser ?: return
        browser.forwardMouse(MouseEvent(EVENT_SOURCE, id, source.`when`, source.modifiersEx, x, y, source.clickCount, false, source.button))
    }

    /** A wheel turn like [source], at [x], [y] on the page. */
    fun wheel(
        source: MouseWheelEvent,
        x: Int,
        y: Int,
    ) {
        val browser = browser ?: return
        browser.forwardWheel(
            MouseWheelEvent(
                EVENT_SOURCE,
                MouseEvent.MOUSE_WHEEL,
                source.`when`,
                source.modifiersEx,
                x,
                y,
                x,
                y,
                0,
                false,
                source.scrollType,
                source.scrollAmount,
                source.wheelRotation,
                source.preciseWheelRotation,
            ),
        )
    }

    /**
     * Forwards a key event as AWT delivered it, so Chromium reads the platform's own key codes. A
     * press that carries a character also needs a typed event for the page to get the text; when
     * the window doesn't pass one on after the press, one is made from it.
     */
    fun key(event: KeyEvent) {
        val browser = browser ?: return
        when (event.id) {
            KeyEvent.KEY_TYPED -> {
                untyped = null
                browser.forwardKey(event)
            }
            KeyEvent.KEY_PRESSED -> {
                browser.forwardKey(event)
                if (event.keyChar != KeyEvent.CHAR_UNDEFINED && !event.isControlDown && !event.isMetaDown) {
                    untyped = event
                    // AWT queues a press's typed event right behind it, so it has arrived by then.
                    SwingUtilities.invokeLater {
                        val pending = untyped ?: return@invokeLater
                        untyped = null
                        browser.forwardKey(KeyEvent(EVENT_SOURCE, KeyEvent.KEY_TYPED, pending.`when`, pending.modifiersEx, KeyEvent.VK_UNDEFINED, pending.keyChar))
                    }
                }
            }
            else -> browser.forwardKey(event)
        }
    }

    // Chromium calls these from native code: an exception escaping one would only be printed, without
    // its trace, so each one logs its own.
    override fun onPaint(
        popup: Boolean,
        frame: ByteBuffer,
        width: Int,
        height: Int,
    ) {
        try {
            paint(popup, frame, width, height)
        } catch (e: Exception) {
            Log.w(TAG, "A frame did not paint", e)
        }
    }

    private fun paint(
        popup: Boolean,
        frame: ByteBuffer,
        width: Int,
        height: Int,
    ) {
        val size = width * height * 4
        if (popup) {
            val copy = popupPixels?.takeIf { it.size == size } ?: ByteArray(size)
            frame.duplicate().get(copy, 0, size)
            popupPixels = copy
            popupWidth = width
            popupHeight = height
        } else {
            if (pixels.size != size) pixels = ByteArray(size)
            frame.duplicate().get(pixels, 0, size)
            frameWidth = width
            frameHeight = height
        }
        publish()
    }

    override fun onPopupShow(show: Boolean) {
        popupShown = show
        if (!show) {
            popupPixels = null
            popupBounds = null
        }
    }

    override fun onPopupSize(bounds: Rectangle) {
        popupBounds = Rectangle(bounds)
    }

    override fun onCursorChange(cursorType: Int) {
        mutableCursor.value = runCatching { Cursor.getPredefinedCursor(cursorType) }.getOrDefault(Cursor.getDefaultCursor())
    }

    private fun publish() {
        if (stopped || frameWidth == 0 || frameHeight == 0) return
        val bounds = popupBounds
        val popup = popupPixels
        val composed =
            if (popupShown && bounds != null && popup != null) {
                // A select menu paints apart from the page: lay it over a copy, so the page's own
                // next frame still finds itself whole.
                pixels.copyOf().also { blit(popup, popupWidth, popupHeight, it, (bounds.x * scale).roundToInt(), (bounds.y * scale).roundToInt()) }
            } else {
                pixels
            }
        val info = ImageInfo(frameWidth, frameHeight, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
        // installPixels copies, so the buffer is free for the next frame.
        val bitmap = Bitmap()
        if (!bitmap.installPixels(info, composed, frameWidth * 4)) {
            bitmap.close()
            return
        }
        bitmap.setImmutable()
        mutableFrame.value = bitmap.asComposeImageBitmap()
        published.addLast(bitmap)
        while (published.size > LIVE_FRAMES) {
            val old = published.removeFirst()
            SwingUtilities.invokeLater { old.close() }
        }
    }

    private fun blit(
        source: ByteArray,
        sourceWidth: Int,
        sourceHeight: Int,
        target: ByteArray,
        left: Int,
        top: Int,
    ) {
        val fromX = maxOf(0, -left)
        val toX = minOf(sourceWidth, frameWidth - left)
        if (toX <= fromX) return
        for (row in maxOf(0, -top) until minOf(sourceHeight, frameHeight - top)) {
            System.arraycopy(source, (row * sourceWidth + fromX) * 4, target, ((top + row) * frameWidth + left + fromX) * 4, (toX - fromX) * 4)
        }
    }

    private companion object {
        const val TAG = "OffscreenNappletView"

        // The frames kept open: the one shown, and the few before it a draw may still hold.
        const val LIVE_FRAMES = 3

        // Every forwarded event needs a source; Chromium reads only its fields.
        val EVENT_SOURCE = Canvas()
    }
}
