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

import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefMessageRouterHandlerAdapter
import java.util.UUID

/**
 * How a page in an in-app browser reaches the broker: a message router of its own, under a name of
 * its own ([newRouter]), whose queries go to that page's [CefBrowserSession].
 *
 * The engine's extension runs [contentScript] at document start in the top frame of every web page.
 * It looks for a browser router's query function on the page's window and gives the shared shim
 * ([NappletWebContract.BRIDGE_NAME], the direct-bridge path Android's browser uses) a `postMessage`
 * that sends over it. The page's channel is one persistent query, opened first: its first message is
 * a token the host mints for that document, which every request carries, so the host judges a
 * request by the document that sent it, not by wherever the frame has navigated by the time the
 * request is read. A request's answer comes back on its own query, which Chromium cancels when the
 * document goes, so an answer never lands in the page that replaced it. Pushes (a live
 * subscription's events) come back on the channel.
 *
 * One name per page, not one for all: removing a router from a client unregisters its name in the
 * renderer for every page there, so with a shared name, closing one page took `window.nostr` from
 * the pages opened after it. A page can't know its own name before its scripts run, so the script
 * opens its channel on every browser router function it finds; only its own page's client answers
 * (a query to another name reaches a client with no router for it), and it keeps that one.
 *
 * Nothing the page says is trusted: the session checks the frame Chromium reports (the main frame,
 * an origin that may have NIP-07) and keys consent on that origin. The page's own scripts can call
 * the query function too (Chromium makes it non-configurable, so it can't be deleted), which gets
 * them nothing the shim's bridge doesn't: the same origin, the same consent.
 */
object DesktopBrowserBridge {
    /** Every browser router's query function starts with this; napplet shells use another prefix. */
    const val QUERY_FUNCTION_PREFIX = "__amethystBrowserQuery_"

    private const val CANCEL_SUFFIX = "Cancel"

    /** The one persistent query a document opens: its channel for pushes. */
    const val CHANNEL_REQUEST = """{"type":"__amethyst.channel"}"""

    /** Queries a page makes, from Chromium's UI thread. */
    interface Handler {
        fun onQuery(
            frame: CefFrame,
            queryId: Long,
            request: String,
            persistent: Boolean,
            callback: CefQueryCallback,
        )

        fun onQueryCanceled(queryId: Long)
    }

    /** A router under a fresh name, whose queries go to [handler]. Its client removes and disposes it. */
    fun newRouter(handler: Handler): CefMessageRouter {
        val name = QUERY_FUNCTION_PREFIX + UUID.randomUUID().toString().replace("-", "")
        val router = CefMessageRouter.create(CefMessageRouter.CefMessageRouterConfig(name, name + CANCEL_SUFFIX))
        router.addHandler(
            object : CefMessageRouterHandlerAdapter() {
                override fun onQuery(
                    browser: CefBrowser?,
                    frame: CefFrame?,
                    queryId: Long,
                    request: String,
                    persistent: Boolean,
                    callback: CefQueryCallback,
                ): Boolean {
                    if (frame == null) {
                        callback.failure(404, "No frame")
                    } else {
                        handler.onQuery(frame, queryId, request, persistent, callback)
                    }
                    return true
                }

                // Chromium passes no browser or frame when the browser itself is closing: its session
                // is closing too, and drops its documents itself.
                override fun onQueryCanceled(
                    browser: CefBrowser?,
                    frame: CefFrame?,
                    queryId: Long,
                ) {
                    handler.onQueryCanceled(queryId)
                }
            },
            true,
        )
        return router
    }

    /**
     * The content script: in a top frame whose window has browser router functions, the bridge the
     * shim expects, then the shim with NIP-07 on. Anywhere else (a video, an nSite's embed) there is
     * no such function and it does nothing.
     */
    fun contentScript(shimJs: String): String =
        "(function(){\n" +
            "if (window.top !== window) return;\n" +
            "var found = Object.getOwnPropertyNames(window).filter(function(n) {\n" +
            "  return n.indexOf('$QUERY_FUNCTION_PREFIX') === 0 && n.slice(-$CANCEL_SUFFIX_LENGTH) !== '$CANCEL_SUFFIX' && typeof window[n] === 'function';\n" +
            "});\n" +
            "if (!found.length) return;\n" +
            "var bridge = { onmessage: null };\n" +
            "function deliver(r) { if (r && typeof bridge.onmessage === 'function') { try { bridge.onmessage({ data: r }); } catch (_) {} } }\n" +
            "function ignore() {}\n" +
            // The router that answers the channel is this page's; its first message is the token, and
            // requests made before it wait.
            "var q = null, token = null, waiting = [];\n" +
            "function send(s) { q({ request: token + '\\n' + s, persistent: false, onSuccess: deliver, onFailure: ignore }); }\n" +
            "bridge.postMessage = function(s) { s = String(s); if (token === null) waiting.push(s); else send(s); };\n" +
            // New windows: Chromium refuses an off-screen page's own, so they go over the bridge, and the
            // session opens each as a screen of its own. window.open hands the page no window back.
            "function openWindow(href) {\n" +
            "  var url; try { url = new URL(String(href), location.href).href; } catch (_) { return; }\n" +
            "  if (url.indexOf('https://') !== 0 && url.indexOf('http://') !== 0) return;\n" +
            "  var s = '${CefBrowserSession.OPEN_WINDOW}' + url; if (token === null) waiting.push(s); else send(s);\n" +
            "}\n" +
            "window.open = function(url) { if (url) openWindow(url); return null; };\n" +
            // On window, in the bubble phase: last, so a page that handles its own links (and prevents
            // the default) keeps them.
            "function onLink(e) {\n" +
            "  if (e.defaultPrevented || !e.target || !e.target.closest) return;\n" +
            "  var a = e.target.closest('a[href]'); if (!a) return;\n" +
            "  var base = document.querySelector('base[target]');\n" +
            "  var t = (a.getAttribute('target') || (base && base.getAttribute('target')) || '').toLowerCase();\n" +
            "  var named = t !== '' && t !== '_self' && t !== '_parent' && t !== '_top';\n" +
            "  if (!named && !e.ctrlKey && !e.metaKey && e.button !== 1) return;\n" +
            "  e.preventDefault(); openWindow(a.href);\n" +
            "}\n" +
            "window.addEventListener('click', onLink);\n" +
            "window.addEventListener('auxclick', function(e) { if (e.button === 1) onLink(e); });\n" +
            "found.forEach(function(n) {\n" +
            "  var f = window[n];\n" +
            "  f({ request: '$CHANNEL_REQUEST', persistent: true, onFailure: ignore, onSuccess: function(r) {\n" +
            "    if (q === null) { q = f; token = String(r); var held = waiting; waiting = []; held.forEach(send); }\n" +
            "    else if (q === f) deliver(r);\n" +
            "  } });\n" +
            "});\n" +
            "Object.defineProperty(window, '${NappletWebContract.BRIDGE_NAME}', { value: bridge, configurable: false, enumerable: false, writable: false });\n" +
            "window.__nappletDirectBridge = true;\n" +
            "window.__nappletNip07 = true;\n" +
            shimJs +
            "\n})();\n"

    private const val CANCEL_SUFFIX_LENGTH = CANCEL_SUFFIX.length
}
