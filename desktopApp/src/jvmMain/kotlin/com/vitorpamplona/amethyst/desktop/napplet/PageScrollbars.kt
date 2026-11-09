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

import com.vitorpamplona.amethyst.commons.napplet.NappletThemeColors
import kotlinx.serialization.json.JsonPrimitive
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame

/**
 * The scrollbars of every page Chromium shows (web pages, napplets, nSites), in the app's colors.
 *
 * Two halves. Every frame gets [CSS] at document start: a thin rounded thumb, no arrow buttons,
 * colored from two CSS variables, gray on a clear track until they arrive. The engine's extension
 * gives it to napplets and nSites; the in-app browser's pages, in contexts no extension reaches, run
 * [DOCUMENT_SCRIPT] instead (see [DesktopCefEngine.webPageScripts]).
 * Then each session sets the variables in every frame ([apply]): when a frame starts loading, again
 * when it has loaded (a page that replaced the document's adopted sheets gets them back), and in
 * every frame of every page when the app's theme changes ([update]). The track takes the app's
 * background and the thumb its text color, faded.
 *
 * The extension's stylesheet comes before the page's, and [DOCUMENT_SCRIPT] puts the same rules in a
 * cascade layer, which unlayered rules beat, so either way a page's own `::-webkit-scrollbar` rules
 * win, and Chromium drops these for `scrollbar-color` / `scrollbar-width` where a page sets those.
 * The variables live in a constructed stylesheet adopted by the document, not in its DOM, so a page
 * never finds an element or attribute of ours; it can read the two colors, as it can read
 * `prefers-color-scheme`.
 */
object PageScrollbars {
    private const val TRACK = "--amethyst-scrollbar-track"
    private const val THUMB = "--amethyst-scrollbar-thumb"

    val CSS =
        """
        ::-webkit-scrollbar { width: 8px; height: 8px; background: transparent; }
        ::-webkit-scrollbar-track, ::-webkit-scrollbar-corner { background: var($TRACK, transparent); }
        ::-webkit-scrollbar-thumb { background-color: color-mix(in srgb, var($THUMB, rgb(128, 128, 128)) 40%, transparent); border-radius: 4px; border: 2px solid transparent; background-clip: content-box; }
        ::-webkit-scrollbar-thumb:hover { background-color: color-mix(in srgb, var($THUMB, rgb(128, 128, 128)) 70%, transparent); }
        ::-webkit-scrollbar-button { display: none; }
        """.trimIndent()

    /**
     * [CSS] for a document no extension reaches, adopted as a constructed stylesheet in a cascade layer
     * of its own. Runs as the document is created, before its own scripts and styles.
     */
    val DOCUMENT_SCRIPT =
        "(function(){try{var s=new CSSStyleSheet();s.replaceSync(" + JsonPrimitive("@layer amethyst-scrollbars {\n$CSS\n}") + ");" +
            "document.adoptedStyleSheets=document.adoptedStyleSheets.concat([s]);}catch(e){}})();"

    private val HEX = Regex("#[0-9A-Fa-f]{6}")

    @Volatile
    private var script: String? = null

    /** The app's colors from now on, in pages that load next; the host then re-applies open pages. */
    fun update(colors: NappletThemeColors) {
        // Only a plain hex color goes into the script; anything else leaves the gray fallback.
        script =
            if (HEX.matches(colors.background) && HEX.matches(colors.text)) {
                "(function(){var d=document,k=Symbol.for('amethyst.scrollbars'),s=d[k];" +
                    "if(!s){try{s=new CSSStyleSheet();}catch(e){return;}Object.defineProperty(d,k,{value:s});}" +
                    "s.replaceSync(':root{$TRACK:${colors.background};$THUMB:${colors.text}}');" +
                    "if(d.adoptedStyleSheets.indexOf(s)<0)d.adoptedStyleSheets=d.adoptedStyleSheets.concat([s]);})();"
            } else {
                null
            }
    }

    /** The current colors in [frame]'s document. Any thread. */
    fun apply(frame: CefFrame) {
        val script = script ?: return
        frame.executeJavaScript(script, "", 0)
    }

    /** The current colors in every frame of [browser]. Any thread. */
    fun apply(browser: CefBrowser) {
        if (script == null) return
        browser.frameIdentifiers?.forEach { id -> browser.getFrameByIdentifier(id)?.let(::apply) }
    }
}
