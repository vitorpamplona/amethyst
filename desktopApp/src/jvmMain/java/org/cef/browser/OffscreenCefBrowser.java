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
package org.cef.browser;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.callback.CefDragData;
import org.cef.handler.CefRenderHandler;
import org.cef.handler.CefScreenInfo;

/**
 * A Chromium browser with no window and no OpenGL: it paints into a buffer handed to a
 * {@link Listener}, so Compose can draw the page like an image, clipped and layered with the rest of
 * the UI, and it takes input only as events forwarded to it.
 *
 * <p>JCEF's own off-screen browser draws through a JOGL canvas and drops every frame until that
 * canvas is on screen, which a note in a scrolling feed can't give it. Its base class is
 * package-private, which is why this lives in {@code org.cef.browser}, and why it is Java: Kotlin
 * can't subclass a package-private class. It uses only the base class's protected members.
 */
public final class OffscreenCefBrowser extends CefBrowser_N implements CefRenderHandler {
    /** Called on Chromium's UI thread. A frame's buffer is valid only during the call. */
    public interface Listener {
        /** A frame of the page, or of its popup (a select menu) when {@code popup}, in BGRA. */
        void onPaint(boolean popup, ByteBuffer frame, int width, int height);

        void onPopupShow(boolean show);

        /** Where the popup sits, in the page's coordinates. */
        void onPopupSize(Rectangle bounds);

        void onCursorChange(int cursorType);
    }

    private final Listener listener;
    private final Component placeholder = new Canvas();
    private volatile Rectangle viewRect = new Rectangle(0, 0, 1, 1);
    private volatile double scale = 1.0;
    private final AtomicBoolean focused = new AtomicBoolean(false);

    /** Paints at most {@code frameRate} frames a second. */
    public OffscreenCefBrowser(CefClient client, String url, CefRequestContext context, int frameRate, Listener listener) {
        super(client, url, context, null, null, settings(frameRate));
        this.listener = listener;
    }

    private static CefBrowserSettings settings(int frameRate) {
        CefBrowserSettings settings = new CefBrowserSettings();
        settings.windowless_frame_rate = frameRate;
        return settings;
    }

    /**
     * The page's size in device-independent pixels and the screen's scale; frames come at the size
     * times the scale. Chromium asks for both again when told.
     */
    public void resize(int width, int height, double scale) {
        viewRect = new Rectangle(0, 0, Math.max(1, width), Math.max(1, height));
        this.scale = scale;
        if (getNativeRef("CefBrowser") != 0) wasResized(viewRect.width, viewRect.height);
    }

    /** A mouse event in the page's coordinates (device-independent pixels). */
    public void forwardMouse(MouseEvent event) {
        sendMouseEvent(event);
    }

    public void forwardWheel(MouseWheelEvent event) {
        sendMouseWheelEvent(event);
    }

    public void forwardKey(KeyEvent event) {
        sendKeyEvent(event);
    }

    /**
     * Tells Chromium only when the focus changes. Chromium answers SetFocus(true) on a windowless
     * browser with OnGotFocus, and JCEF's client answers that with setFocus(true): passed on each
     * time, the two called each other until Chromium's UI thread ran out of stack, every time an
     * inline napplet took focus (a StackOverflowError on the AWT thread on Linux; on macOS that
     * thread is AppKit's main thread).
     */
    @Override
    public void setFocus(boolean enable) {
        if (focused.getAndSet(enable) == enable) return;
        super.setFocus(enable);
    }

    @Override
    public void createImmediately() {
        createBrowser(getClient(), 0L, getUrl(), true, false, null, getRequestContext());
    }

    /**
     * Never shown: there is no window to place. JCEF's focus code still asks for one when the page
     * hands focus back (Tab past its last field), and calls getParent() on it, so it can't be null.
     */
    @Override
    public Component getUIComponent() {
        return placeholder;
    }

    @Override
    public CefRenderHandler getRenderHandler() {
        return this;
    }

    @Override
    protected CefBrowser_N createDevToolsBrowser(CefClient client, String url, CefRequestContext context, CefBrowser_N parent, Point inspectAt) {
        return null;
    }

    @Override
    public CompletableFuture<BufferedImage> createScreenshot(boolean nativeResolution) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("An off-screen browser has no screenshot; its frames are the picture"));
    }

    @Override
    public Rectangle getViewRect(CefBrowser browser) {
        return viewRect;
    }

    @Override
    public boolean getScreenInfo(CefBrowser browser, CefScreenInfo info) {
        Rectangle rect = viewRect;
        info.Set(scale, 32, 8, false, rect, rect);
        return true;
    }

    /** No place on the screen: popups are placed in the page's own coordinates. */
    @Override
    public Point getScreenPoint(CefBrowser browser, Point viewPoint) {
        return viewPoint;
    }

    @Override
    public void onPopupShow(CefBrowser browser, boolean show) {
        listener.onPopupShow(show);
    }

    @Override
    public void onPopupSize(CefBrowser browser, Rectangle size) {
        listener.onPopupSize(size);
    }

    @Override
    public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer, int width, int height) {
        listener.onPaint(popup, buffer, width, height);
    }

    // Frames go to the listener; JCEF's paint-event listeners belong to its own off-screen browser.
    @Override
    public void addOnPaintListener(Consumer<CefPaintEvent> paintListener) {}

    @Override
    public void setOnPaintListener(Consumer<CefPaintEvent> paintListener) {}

    @Override
    public void removeOnPaintListener(Consumer<CefPaintEvent> paintListener) {}

    @Override
    public boolean onCursorChange(CefBrowser browser, int cursorType) {
        listener.onCursorChange(cursorType);
        return true;
    }

    // No drag and drop out of the page.
    @Override
    public boolean startDragging(CefBrowser browser, CefDragData dragData, int mask, int x, int y) {
        return false;
    }

    @Override
    public void updateDragCursor(CefBrowser browser, int operation) {}
}
