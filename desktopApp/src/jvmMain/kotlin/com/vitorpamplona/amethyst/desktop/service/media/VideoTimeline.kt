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
package com.vitorpamplona.amethyst.desktop.service.media

import com.vitorpamplona.quartz.utils.Log

/**
 * One debug line per step a video takes from its card coming on screen to its first frame, each
 * stamped with the milliseconds since that card appeared, so a slow start shows where its time went:
 * the engine held back, the open, the index prefetch, each relay request (disk or network, the
 * host's time to first byte, bytes, how it ended), the size known, the first frame.
 *
 * `adb`-free: read it with `grep VideoTimeline` in the desktop log. Off below debug level, at no cost.
 */
internal object VideoTimeline {
    private const val TAG = "VideoTimeline"
    private const val MAX_TRACKED = 512

    // When each video's card last came on screen, by URL.
    private val appeared =
        object : LinkedHashMap<String, Long>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > MAX_TRACKED
        }

    /** [url]'s card came on screen: its timeline starts over from now. */
    fun appeared(url: String) {
        synchronized(appeared) { appeared[url] = System.currentTimeMillis() }
        Log.d(TAG) { "+0 ms ${short(url)} card on screen" }
    }

    /** A step [url] took; [detail] is computed only when debug logging is on. */
    fun mark(
        url: String,
        detail: () -> String,
    ) {
        Log.d(TAG) {
            val since = synchronized(appeared) { appeared[url] }
            val stamp = if (since == null) "+?" else "+${System.currentTimeMillis() - since}"
            "$stamp ms ${short(url)} ${detail()}"
        }
    }

    // The host and the end of the file name: enough to tell the videos of one feed apart.
    private fun short(url: String): String {
        val host = url.substringAfter("://").substringBefore('/')
        val name = url.substringBefore('?').substringAfterLast('/')
        return "$host/…${name.takeLast(14)}"
    }
}
