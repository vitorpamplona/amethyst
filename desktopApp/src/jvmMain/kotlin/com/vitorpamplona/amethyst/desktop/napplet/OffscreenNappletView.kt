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
import org.cef.OS
import org.cef.browser.CefFrame
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

    // The document's scroll, for a front page that reports it (PaperScrollReports). Guarded by itself:
    // reports come on Chromium's thread, the screen's wheel on the AWT thread.
    private val scroll = PageScrollTracker()
    private val mutableScrollKnown = MutableStateFlow(false)

    /** Whether the page reports how far it scrolls, so the screen around it can share its wheel. */
    val scrollKnown: StateFlow<Boolean> = mutableScrollKnown.asStateFlow()

    // Where the pointer last was on the page (device-independent pixels), for a wheel turn the screen
    // hands over; the middle until it moves. AWT thread.
    private var pointerX = -1
    private var pointerY = -1

    // The part of a pixel the last turns handed over didn't send yet. AWT thread.
    private var wheelCarry = 0.0

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

    /** Re-asserts the page's focus after something in Chromium dropped it (see [OffscreenCefBrowser.refocus]). */
    fun refocus() {
        browser?.refocus()
    }

    /** A mouse event like [source] (its button, modifiers and clicks), at [x], [y] on the page. */
    fun mouse(
        source: MouseEvent,
        id: Int,
        x: Int,
        y: Int,
    ) {
        val browser = browser ?: return
        if (id == MouseEvent.MOUSE_EXITED) {
            pointerX = -1
            pointerY = -1
        } else {
            pointerX = x
            pointerY = y
        }
        browser.forwardMouse(MouseEvent(EVENT_SOURCE, id, source.`when`, source.modifiersEx, x, y, source.clickCount, false, source.button))
    }

    /** How far the document reports it scrolled ([top]) and could ([max]), in CSS pixels. Any thread. */
    fun reportScroll(
        top: Double,
        max: Double,
    ) {
        synchronized(scroll) { scroll.report(top, max) }
        mutableScrollKnown.value = true
    }

    /** How far the document can still scroll [towardEnd] or back, in CSS pixels; 0 when it never said. */
    fun scrollRoom(towardEnd: Boolean): Double = synchronized(scroll) { scroll.room(towardEnd) }

    /**
     * Scrolls the document by up to [delta] CSS pixels (positive toward its end), as a wheel turn at
     * the pointer, and returns how far that takes it. Chromium reads the turn's delta as pixels.
     */
    fun scrollBy(delta: Double): Double {
        val taken = synchronized(scroll) { scroll.take(delta) }
        if (taken == 0.0) return 0.0
        wheelCarry += taken
        val pixels = wheelCarry.roundToInt()
        if (pixels == 0) return taken
        wheelCarry -= pixels
        val x = if (pointerX >= 0) pointerX else width / 2
        val y = if (pointerY >= 0) pointerY else height / 2
        // Chromium's positive is up, as in [wheel].
        browser?.forwardWheel(wheelEvent(System.currentTimeMillis(), 0, x, y, -pixels))
        return taken
    }

    /**
     * A wheel turn like [source], at [x], [y] on the page. JCEF hands Chromium the event's rotation as
     * a pixel delta, unconverted: AWT counts a notch towards the user (down) as +1, three units for a
     * unit scroll, where Chromium reads positive as up and a notch as [WHEEL_DELTA]. So the delta goes
     * over as a block scroll (which JCEF passes as it is) already in Chromium's terms: a page that got
     * AWT's own numbers scrolled a few pixels the wrong way, so nothing moved at its top.
     */
    fun wheel(
        source: MouseWheelEvent,
        x: Int,
        y: Int,
    ) {
        val browser = browser ?: return
        val delta = -(source.preciseWheelRotation * UNIT_PIXELS).roundToInt()
        if (delta == 0) return
        browser.forwardWheel(wheelEvent(source.`when`, source.modifiersEx, x, y, delta))
    }

    /** A wheel turn of [delta] in Chromium's terms (pixels, positive up), as a block scroll JCEF passes as it is. */
    private fun wheelEvent(
        time: Long,
        modifiers: Int,
        x: Int,
        y: Int,
        delta: Int,
    ) = MouseWheelEvent(EVENT_SOURCE, MouseEvent.MOUSE_WHEEL, time, modifiers, x, y, x, y, 0, false, MouseWheelEvent.WHEEL_BLOCK_SCROLL, 1, delta, delta.toDouble())

    /** A turn of [rotation] notches (positive down, as AWT counts) in CSS pixels, as [wheel] sends it. */
    fun notchPixels(rotation: Double): Double = rotation * UNIT_PIXELS

    /**
     * Forwards a key event as AWT delivered it, so Chromium reads the platform's own key codes. A
     * press that carries a character also needs a typed event for the page to get the text; when
     * the window doesn't pass one on after the press, one is made from it.
     */
    fun key(event: KeyEvent) {
        val browser = browser ?: return
        if (editCommand(browser, event)) return
        when (event.id) {
            KeyEvent.KEY_TYPED -> {
                untyped = null
                browser.forwardKey(event)
            }
            KeyEvent.KEY_PRESSED -> {
                browser.forwardKey(named(event))
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
            else -> browser.forwardKey(named(event))
        }
    }

    /**
     * [event] as JCEF can name it. On Linux JCEF names only Backspace, Delete, the arrows, Enter,
     * Escape and Tab itself, and takes every other key's X keysym from its character. A navigation
     * key has none (`CHAR_UNDEFINED`, 0xFFFF, which is X's Delete), so Page Down, Page Up, Home and
     * End all reached the page as Delete and scrolled nothing. Given its keysym as the character,
     * each is read as itself. Only presses and releases carry it: no typed event follows these keys.
     */
    private fun named(event: KeyEvent): KeyEvent {
        if (!IS_LINUX) return event
        val keysym = linuxKeysym(event.keyCode) ?: return event
        return KeyEvent(EVENT_SOURCE, event.id, event.`when`, event.modifiersEx, event.keyCode, keysym.toChar(), event.keyLocation)
    }

    /**
     * Runs ⌘C, ⌘V, ⌘X, ⌘A, ⌘Z and ⇧⌘Z on the page on macOS, and reports whether [event] was one. A
     * windowless Chromium on macOS does not act on these keys (a native one gets them from the Edit
     * menu), so forwarded they did nothing: no copy, paste or select-all in any page. Other systems'
     * Ctrl shortcuts are left to Chromium: running them here as well could paste twice.
     */
    private fun editCommand(
        browser: OffscreenCefBrowser,
        event: KeyEvent,
    ): Boolean {
        if (!IS_MAC || !event.isMetaDown || event.isControlDown || event.isAltDown) return false
        val command: ((CefFrame) -> Unit)? =
            when (event.keyCode) {
                KeyEvent.VK_C -> CefFrame::copy
                KeyEvent.VK_V -> CefFrame::paste
                KeyEvent.VK_X -> CefFrame::cut
                KeyEvent.VK_A -> CefFrame::selectAll
                KeyEvent.VK_Z -> if (event.isShiftDown) CefFrame::redo else CefFrame::undo
                else -> null
            }
        if (command == null) return false
        // The command runs once, on the press; its typed and released events go nowhere.
        if (event.id == KeyEvent.KEY_PRESSED) (browser.focusedFrame ?: browser.mainFrame)?.let(command)
        return true
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

        // Chromium's wheel delta for one notch, as Windows reports it and CEF's own sample sends.
        const val WHEEL_DELTA = 120

        // What one unit of AWT's wheel rotation scrolls. On macOS a trackpad or a Magic Mouse reports
        // fractions of a unit, many a second, and the rest of the app (Compose's MacOSCocoaConfig) moves
        // 10 dp for each: at a notch's 120 the paper ran twelve times faster than the screen around it.
        val UNIT_PIXELS: Double = if (OS.isMacintosh()) 10.0 else WHEEL_DELTA.toDouble()

        // Every forwarded event needs a source; Chromium reads only its fields.
        val EVENT_SOURCE = Canvas()

        private val IS_MAC = OS.isMacintosh()

        private val IS_LINUX = OS.isLinux()
    }
}

/** The X keysym of an AWT key JCEF can't name on Linux by itself (see [OffscreenNappletView.key]), or null. */
internal fun linuxKeysym(keyCode: Int): Int? =
    when (keyCode) {
        KeyEvent.VK_PAGE_UP -> 0xff55
        KeyEvent.VK_PAGE_DOWN -> 0xff56
        KeyEvent.VK_HOME -> 0xff50
        KeyEvent.VK_END -> 0xff57
        else -> null
    }
