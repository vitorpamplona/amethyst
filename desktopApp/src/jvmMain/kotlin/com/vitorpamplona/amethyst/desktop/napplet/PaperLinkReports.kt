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

/**
 * The links a reader clicks in a front page, told to the host while the page itself runs no script.
 *
 * A paper's frame is sandboxed without `allow-scripts` and served under a CSP with no script
 * source, so nothing in its own world runs, and the host's `executeJavaScript` is refused there too
 * ("Blocked script execution ... the document's frame is sandboxed"). Chromium also refuses an
 * off-screen page's own new windows before the host hears of them, and every link a paper carries
 * opens one (`target="_blank"`), so a click did nothing. The engine's guard extension runs
 * [SCRIPT] in its isolated world instead: the page can neither see nor reach it. It takes a real
 * click (`isTrusted`) on the main button on a link and logs the link's address. Chromium hands the
 * log line to the session's console handler, from the extension's address, which [parse] requires.
 */
object PaperLinkReports {
    /** The file the guard extension serves [SCRIPT] from. */
    const val FILE = "paper-links.js"

    /** The file an older start served the paper's scroll reader from: deleted when found. */
    const val OLD_FILE = "paper-scroll.js"

    private const val PREFIX = "amethyst-paper-link "

    /**
     * Runs at document start in every frame under `napplet.local`, and stays only in a paper's: a
     * frame at an `https` address whose origin is opaque, which only the paper's sandbox (no
     * `allow-same-origin`) gives.
     */
    val SCRIPT =
        """
        (function(){
        if(window===top||window.origin!=='null'||location.protocol!=='https:')return;
        addEventListener('click',function(ev){
        if(!ev.isTrusted||ev.defaultPrevented||ev.button!==0||!ev.target||!ev.target.closest)return;
        var a=ev.target.closest('a[href]');if(!a)return;var u=a.href;
        if(u.indexOf('https://')!==0&&u.indexOf('http://')!==0)return;
        ev.preventDefault();console.log('$PREFIX'+u);},true);
        })();
        """.trimIndent()

    /**
     * The web address of the link the reader clicked, if [message] is the guard extension's report
     * of one ([source] is where it was logged from); null otherwise.
     */
    fun parse(
        message: String,
        source: String,
    ): String? {
        if (!source.startsWith("chrome-extension://") || !source.endsWith("/$FILE") || !message.startsWith(PREFIX)) return null
        val url = message.substring(PREFIX.length)
        return url.takeIf { (it.startsWith("https://") || it.startsWith("http://")) && it.none(Char::isWhitespace) }
    }
}
