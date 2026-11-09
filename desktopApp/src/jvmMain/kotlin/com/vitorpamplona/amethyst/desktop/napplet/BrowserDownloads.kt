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

import com.vitorpamplona.quartz.utils.Log
import java.awt.Desktop
import java.io.File

/** A file a page in the in-app browser started to download. */
data class BrowserDownload(
    /** Chromium's id for it, unique while the app runs. */
    val id: Int,
    /** The name it is saved under, without a folder. */
    val name: String,
    /** The host it comes from, as the user knows it. */
    val host: String,
    /** Its size, or -1 when the server didn't say. */
    val totalBytes: Long,
    val state: State,
    val percent: Int = 0,
    /** Where it is saved, once the user said yes. */
    val file: File? = null,
) {
    enum class State {
        /** The page asked; nothing is written until the user says yes. */
        ASKING,
        DOWNLOADING,
        DONE,

        /** Canceled, or the network dropped it. */
        STOPPED,
    }
}

/**
 * Where the in-app browser saves files: the user's Downloads folder, under a name no other file there
 * (or download on its way) has.
 */
object BrowserDownloads {
    private const val TAG = "BrowserDownloads"
    private const val MAX_NAME = 180
    private const val FALLBACK_NAME = "download"

    // Paths given to downloads still running: Chromium creates the file only as it finishes, so two
    // downloads of the same name would otherwise both be given the same free name.
    private val reserved = HashSet<String>()

    fun folder(): File {
        val home = File(System.getProperty("user.home"))
        val downloads = File(home, "Downloads")
        return if (downloads.isDirectory || downloads.mkdirs()) {
            downloads
        } else {
            Log.w(TAG) { "No Downloads folder; saving in $home" }
            home
        }
    }

    /**
     * [suggested] as a plain file name: no folder in it, nothing a file system refuses, not hidden,
     * not empty. Chromium already cleans the name a page or server suggests; this makes sure.
     */
    fun safeName(suggested: String): String {
        val base =
            suggested
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .filter { it >= ' ' && it != '\u007f' && it !in "<>:\"|?*" }
                .trim()
                .trimStart('.')
                .trim()
        if (base.isEmpty()) return FALLBACK_NAME
        if (base.length <= MAX_NAME) return base
        // Keep the extension of a long name: it says what the file is.
        val extension = base.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it.length < 16 }
        return if (extension == null) base.take(MAX_NAME) else base.take(MAX_NAME - extension.length - 1) + "." + extension
    }

    /** A file in [folder] named [name], or "name (1).ext", "name (2).ext"… if that one is taken. */
    fun reserve(
        folder: File,
        name: String,
    ): File =
        synchronized(reserved) {
            val stem = name.substringBeforeLast('.', name).takeIf { it.isNotEmpty() } ?: name
            val extension = if (stem == name) "" else "." + name.substringAfterLast('.')
            var candidate = File(folder, name)
            var n = 1
            while (candidate.exists() || candidate.absolutePath in reserved) {
                candidate = File(folder, "$stem ($n)$extension")
                n++
            }
            reserved += candidate.absolutePath
            candidate
        }

    /** [file]'s download ended, saved or not: its name is free again for the next one. */
    fun release(file: File) {
        synchronized(reserved) { reserved -= file.absolutePath }
    }

    /** Opens [folder] in the system's file manager. Blocks: call it off the UI thread. */
    fun showFolder(folder: File) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(folder)
            } else {
                Log.w(TAG) { "This system can't open a folder" }
            }
        }.onFailure { Log.w(TAG, "Could not open $folder", it) }
    }
}
