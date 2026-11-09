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
import java.util.concurrent.ConcurrentHashMap

/**
 * How a page in an in-app browser window reaches the broker: one message router, under one name,
 * shared by every browser window, which hands each query to the window it came from.
 *
 * The engine's extension runs [contentScript] at document start in the top frame of every web page.
 * In a browser window it finds the router's query function and gives the shared shim
 * ([NappletWebContract.BRIDGE_NAME], the direct-bridge path Android's browser uses) a `postMessage`
 * that sends over it. A request's answer comes back on its own query, which
 * Chromium cancels when the document goes, so an answer never lands in the page that replaced it.
 * Pushes (a live subscription's events) come back on one persistent query the script opens first:
 * the document's channel.
 *
 * Nothing the page says is trusted: the window checks the frame Chromium reports (the main frame,
 * an origin that may have NIP-07) and keys consent on that origin. The page's own scripts can call
 * the query function too (Chromium makes it non-configurable, so it can't be deleted), which gets
 * them nothing the shim's bridge doesn't: the same origin, the same consent.
 *
 * One router for all, rather than one per window: Chromium keeps one router per name in a renderer,
 * and a page can't know a per-window name before its scripts run.
 */
class DesktopBrowserBridge {
    private val sessions = ConcurrentHashMap<Int, CefBrowserSession>()

    private var router: CefMessageRouter? = null

    /** The shared router, made the first time a browser window opens. Never disposed: it lives as long as Chromium. */
    @Synchronized
    fun router(): CefMessageRouter =
        router ?: CefMessageRouter
            .create(CefMessageRouter.CefMessageRouterConfig(QUERY_FUNCTION, "${QUERY_FUNCTION}Cancel"))
            .also {
                it.addHandler(handler, true)
                router = it
            }

    /** Routes [browser]'s queries to [session], from the moment Chromium has made it. */
    fun register(
        browser: CefBrowser,
        session: CefBrowserSession,
    ) {
        sessions[browser.identifier] = session
    }

    fun unregister(browser: CefBrowser) {
        sessions.remove(browser.identifier)
    }

    private val handler =
        object : CefMessageRouterHandlerAdapter() {
            override fun onQuery(
                browser: CefBrowser,
                frame: CefFrame,
                queryId: Long,
                request: String,
                persistent: Boolean,
                callback: CefQueryCallback,
            ): Boolean {
                val session = sessions[browser.identifier]
                if (session == null) {
                    callback.failure(404, "Not a browser window")
                    return true
                }
                session.onQuery(frame, queryId, request, persistent, callback)
                return true
            }

            override fun onQueryCanceled(
                browser: CefBrowser,
                frame: CefFrame,
                queryId: Long,
            ) {
                sessions[browser.identifier]?.onQueryCanceled(queryId)
            }
        }

    companion object {
        const val QUERY_FUNCTION = "__amethystBrowserQuery"

        /** The one persistent query a document opens: its channel for pushes. */
        const val CHANNEL_REQUEST = """{"type":"__amethyst.channel"}"""

        /**
         * The content script: in a top frame whose window has the browser's query function, the
         * bridge the shim expects, then the shim with NIP-07 on. Anywhere else (a video, an nSite's
         * embed) there is no such function and it does nothing.
         */
        fun contentScript(shimJs: String): String =
            "(function(){\n" +
                "if (window.top !== window) return;\n" +
                "var q = window.$QUERY_FUNCTION;\n" +
                "if (typeof q !== 'function') return;\n" +
                "var bridge = { onmessage: null };\n" +
                "function deliver(r) { if (r && typeof bridge.onmessage === 'function') { try { bridge.onmessage({ data: r }); } catch (_) {} } }\n" +
                "function ignore() {}\n" +
                "bridge.postMessage = function(s) { q({ request: String(s), persistent: false, onSuccess: deliver, onFailure: ignore }); };\n" +
                "q({ request: '$CHANNEL_REQUEST', persistent: true, onSuccess: deliver, onFailure: ignore });\n" +
                "Object.defineProperty(window, '${NappletWebContract.BRIDGE_NAME}', { value: bridge, configurable: false, enumerable: false, writable: false });\n" +
                "window.__nappletDirectBridge = true;\n" +
                "window.__nappletNip07 = true;\n" +
                shimJs +
                "\n})();\n"
    }
}
