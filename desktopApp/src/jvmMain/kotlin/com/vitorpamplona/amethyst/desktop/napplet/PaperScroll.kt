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
 * How far a front page has scrolled, told to the host while the page itself runs no script.
 *
 * A paper's frame is sandboxed without `allow-scripts` and served under a CSP with no script
 * source, so nothing in its own world runs, and the host's `executeJavaScript` is refused there too
 * ("Blocked script execution ... the document's frame is sandboxed"). The engine's guard extension
 * runs [SCRIPT] in its isolated world instead: the page can neither see nor reach it, and it only
 * reads two numbers, the document's scroll offset and how far it can scroll, and logs them when they
 * change. Chromium hands that log line to the session's console handler, from the extension's
 * address, which [parse] requires.
 */
object PaperScrollReports {
    /** The file the guard extension serves [SCRIPT] from. */
    const val FILE = "paper-scroll.js"

    private const val PREFIX = "amethyst-paper-scroll "

    /**
     * Runs at document start in every frame under `napplet.local`, and stays only in a paper's: a
     * frame at an `https` address whose origin is opaque, which only the paper's sandbox (no
     * `allow-same-origin`) gives. One report per animation frame at most, and only on a change.
     */
    val SCRIPT =
        """
        (function(){
        if(window===top||window.origin!=='null'||location.protocol!=='https:')return;
        var last='',queued=false;
        function send(){queued=false;var e=document.scrollingElement||document.documentElement;if(!e)return;
        var m='$PREFIX'+e.scrollTop.toFixed(1)+' '+Math.max(0,e.scrollHeight-e.clientHeight).toFixed(1);
        if(m!==last){last=m;console.log(m);}}
        function queue(){if(!queued){queued=true;requestAnimationFrame(send);}}
        addEventListener('scroll',queue,{passive:true});addEventListener('scrollend',queue);
        addEventListener('resize',queue);addEventListener('load',queue);
        var ro=new ResizeObserver(queue);
        if(document.documentElement)ro.observe(document.documentElement);
        addEventListener('DOMContentLoaded',function(){if(document.body)ro.observe(document.body);queue();});
        queue();
        })();
        """.trimIndent()

    /**
     * The page's scroll offset and its furthest offset, in CSS pixels, if [message] is a report
     * from the guard extension's [FILE] ([source] is where it was logged from); null otherwise.
     */
    fun parse(
        message: String,
        source: String,
    ): Pair<Double, Double>? {
        if (!source.startsWith("chrome-extension://") || !source.endsWith("/$FILE")) return null
        if (!message.startsWith(PREFIX)) return null
        val parts = message.substring(PREFIX.length).split(' ')
        if (parts.size != 2) return null
        val top = parts[0].toDoubleOrNull() ?: return null
        val max = parts[1].toDoubleOrNull() ?: return null
        if (!top.isFinite() || !max.isFinite() || max < 0) return null
        return top to max
    }
}

/**
 * Where an off-screen page's document is scrolled, as the host steers it. Its frame's reports
 * ([PaperScrollReports]) come a frame or more behind, and Chromium animates each turn the host
 * sends, so while the host's own turns are settling ([settleMs] after the last) the position is the
 * one they lead to, and reports only bring the range; once settled, a report (a key, a find, a drag
 * of its scrollbar) is the truth again. Units are the page's CSS pixels. Not thread-safe.
 */
class PageScrollTracker(
    private val settleMs: Long = 400,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var top = 0.0
    private var max = -1.0
    private var settlesAt = 0L

    /** Whether the page has said how far it scrolls. */
    val known: Boolean get() = max >= 0

    fun report(
        top: Double,
        max: Double,
    ) {
        this.max = max.coerceAtLeast(0.0)
        if (now() >= settlesAt) this.top = top
        this.top = this.top.coerceIn(0.0, this.max)
    }

    /** How far the page can still go [towardEnd] or back; under half a pixel is none. */
    fun room(towardEnd: Boolean): Double {
        if (!known) return 0.0
        val room = if (towardEnd) max - top else top
        return if (room < 0.5) 0.0 else room
    }

    /** Moves the page by up to [delta] (positive toward its end) and returns how far it goes. */
    fun take(delta: Double): Double {
        if (!known) return 0.0
        val target = (top + delta).coerceIn(0.0, max)
        val taken = target - top
        top = target
        if (taken != 0.0) settlesAt = now() + settleMs
        return taken
    }
}
