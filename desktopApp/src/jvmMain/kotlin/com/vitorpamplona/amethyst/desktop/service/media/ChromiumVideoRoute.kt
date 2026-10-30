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

import com.vitorpamplona.amethyst.desktop.app.DesktopInstance
import com.vitorpamplona.amethyst.desktop.platform.PlatformInfo
import com.vitorpamplona.quartz.utils.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Which videos skip the native engine and play in Chromium ([ChromiumVideoSession]) from the start.
 *
 * The native engine takes several seconds to give up on a video it can't decode (on macOS the
 * player library polls for a frame size for 5 s before it reports the error), and a feed of such
 * videos paid that on every card. Two kinds go straight to Chromium instead:
 * - On macOS, WebM and Ogg, by the note's declared type or the file's extension: AVFoundation
 *   decodes neither.
 * - Any video the native engine failed on here and Chromium then played. Those are remembered on
 *   disk, so a failure is paid once per video, not once per scroll.
 */
object ChromiumVideoRoute {
    private const val TAG = "ChromiumVideoRoute"

    // Few enough to read at once; the oldest go first past this.
    private const val MAX_LEARNED = 2_000

    private val CHROMIUM_TYPES = setOf("video/webm", "audio/webm", "video/ogg", "audio/ogg")
    private val CHROMIUM_EXTENSIONS = setOf("webm", "ogv")

    private val declared = ConcurrentHashMap.newKeySet<String>()

    private val file: File by lazy { File(DesktopInstance.ownFolder(DesktopInstance.PerCopyFolder.VIDEOS), "chromium-videos.txt") }

    // Insertion order, so trimming drops the oldest.
    private val learned: MutableSet<String> by lazy {
        val loaded = runCatching { if (file.isFile) file.readLines().filter { it.isNotBlank() } else emptyList() }.getOrDefault(emptyList())
        LinkedHashSet(loaded)
    }

    /** Whether [url] goes to Chromium without trying the native engine first. */
    fun prefersChromium(url: String): Boolean {
        if (!ChromiumVideoSession.canTry(url)) return false
        if (PlatformInfo.isMacOS && (url in declared || extensionOf(url) in CHROMIUM_EXTENSIONS)) return true
        return synchronized(learned) { url in learned }
    }

    /** The note declares [url] as [mimeType] (its imeta `m`). */
    fun declare(
        url: String,
        mimeType: String?,
    ) {
        if (mimeType?.substringBefore(';')?.trim()?.lowercase() in CHROMIUM_TYPES) declared.add(url)
    }

    /** The native engine failed on [url] and Chromium played it: send it to Chromium from now on. */
    fun learn(url: String) {
        val lines =
            synchronized(learned) {
                if (!learned.add(url)) return
                while (learned.size > MAX_LEARNED) learned.remove(learned.first())
                learned.toList()
            }
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(lines.joinToString("\n"))
            if (!tmp.renameTo(file)) Log.w(TAG) { "could not save $file" }
        }.onFailure { Log.w(TAG, "could not save the Chromium video list", it) }
    }

    private fun extensionOf(url: String): String =
        url
            .substringBefore('#')
            .substringBefore('?')
            .substringAfterLast('/')
            .substringAfterLast('.', "")
            .lowercase()
}
