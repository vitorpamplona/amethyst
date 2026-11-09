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

import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.cef.browser.CefBrowser
import org.cef.callback.CefBeforeDownloadCallback
import org.cef.callback.CefDownloadItem
import org.cef.callback.CefDownloadItemCallback
import org.cef.handler.CefDownloadHandler
import java.awt.Desktop
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** A file a page in the in-app browser started to download. */
data class BrowserDownload(
    /** Chromium's id for it, unique within the page's jar while the app runs. */
    val id: Int,
    /** The name it is saved under, without a folder. */
    val name: String,
    /** The host it comes from, as the user knows it. */
    val host: String,
    /** Its size, or -1 when the server didn't say. */
    val totalBytes: Long,
    val state: State,
    val percent: Int = 0,
    /** Where it was saved, once it is [State.DONE]. */
    val file: File? = null,
) {
    enum class State(
        /** Over: only dismissing it is left. */
        val finished: Boolean,
    ) {
        /** The page asked; it is held, and nothing reaches the user's folders, until the user says yes. */
        ASKING(false),
        DOWNLOADING(false),
        DONE(true),

        /** Canceled, or the network dropped it. */
        STOPPED(true),
    }
}

/**
 * One page's downloads, as its Chromium download handler.
 *
 * Chromium starts fetching a file before it asks where to put it, and won't pause one that has
 * nowhere to go yet. So each download gets a place at once, in a folder of the app's own ([holding]),
 * and is paused there while the user is asked: a little of it arrives, not all of it. Save resumes
 * it; once complete it moves to the Downloads folder under a name of its own
 * ([BrowserDownloads.reserve]), so nothing partial ever shows up there. Cancel stops it, and
 * Chromium deletes what it wrote.
 *
 * A page with [MAX_QUESTIONS] questions waiting has its next downloads refused unasked (Chromium's
 * own limit on automatic downloads usually comes first). When the page closes ([close]) its downloads
 * stop: CEF doesn't stop them with the browser, and they'd run on, unseen, in the account's jar.
 */
class PageDownloads(
    holding: File,
) : CefDownloadHandler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // This page's own folder in [holding]: Chromium's download ids are per jar, not per app.
    private val pageFolder = File(holding, UUID.randomUUID().toString())

    private val mutableList = MutableStateFlow<List<BrowserDownload>>(emptyList())

    /** Oldest first: each waits for [save] or [cancel], then shows its progress until [dismiss]ed. */
    val list: StateFlow<List<BrowserDownload>> = mutableList.asStateFlow()

    // How to pause, resume or stop each one, from its latest report.
    private val controls = ConcurrentHashMap<Int, CefDownloadItemCallback>()

    // Where Chromium writes each one.
    private val targets = ConcurrentHashMap<Int, File>()

    // Which stage each one is at; changed together, under [lock].
    private val lock = Any()
    private val asking = HashSet<Int>()
    private val saved = HashSet<Int>()
    private val refused = HashSet<Int>()

    // Complete before the user answered (a small file): it moves out when they say yes.
    private val completeWhileAsking = HashSet<Int>()

    private val deliveries = ArrayList<Job>()

    @Volatile
    private var closed = false

    override fun onBeforeDownload(
        browser: CefBrowser,
        item: CefDownloadItem,
        suggestedName: String,
        callback: CefBeforeDownloadCallback,
    ): Boolean {
        val id = item.id
        val name = BrowserDownloads.safeName(suggestedName)
        val accepted =
            synchronized(lock) {
                if (closed || asking.size >= MAX_QUESTIONS) {
                    refused += id
                    false
                } else {
                    asking += id
                    true
                }
            }
        if (!accepted) {
            // Chromium reports on a download before it asks, so its handle is here already.
            controls.remove(id)?.cancel()
            return true
        }
        val target = File(File(pageFolder, id.toString()), name)
        targets[id] = target
        val download =
            BrowserDownload(
                id = id,
                name = name,
                host = BrowserChrome.displayHost(item.url.orEmpty()),
                totalBytes = item.totalBytes.takeIf { it > 0 } ?: -1,
                state = BrowserDownload.State.ASKING,
            )
        mutableList.update { list -> withinShown(list.filter { it.id != id } + download) }
        scope.launch {
            val folder = target.parentFile
            if (folder.isDirectory || folder.mkdirs()) {
                callback.Continue(target.absolutePath, false)
            } else {
                Log.w(TAG) { "Cannot create $folder; refusing a download" }
                cancel(id)
            }
        }
        return true
    }

    override fun onDownloadUpdated(
        browser: CefBrowser,
        item: CefDownloadItem,
        callback: CefDownloadItemCallback,
    ) {
        val id = item.id
        val ended = item.isComplete || !item.isInProgress
        if (ended) controls.remove(id) else controls[id] = callback
        val stage =
            synchronized(lock) {
                when {
                    id in refused -> {
                        if (ended) refused -= id
                        Stage.REFUSED
                    }
                    id in saved -> Stage.SAVED
                    id in asking -> {
                        if (item.isComplete) completeWhileAsking += id
                        if (ended && !item.isComplete) asking -= id
                        Stage.ASKING
                    }
                    else -> Stage.UNKNOWN
                }
            }
        when (stage) {
            Stage.UNKNOWN -> {}
            Stage.REFUSED -> if (ended) discard(id) else callback.cancel()
            Stage.ASKING ->
                when {
                    // Held until the user answers (a no-op until it has its place, and once paused).
                    !ended -> {
                        callback.pause()
                        setSize(id, item)
                    }
                    item.isComplete -> setSize(id, item)
                    // Dropped while the user was deciding: there is nothing left to save.
                    else -> {
                        setState(id, BrowserDownload.State.STOPPED)
                        discard(id)
                    }
                }
            Stage.SAVED ->
                when {
                    item.isComplete -> deliver(id)
                    ended -> {
                        setState(id, BrowserDownload.State.STOPPED)
                        discard(id)
                    }
                    else -> {
                        val total = item.totalBytes.takeIf { it > 0 } ?: -1
                        val percent = item.percentComplete.coerceIn(0, 100)
                        mutableList.update { list -> list.map { if (it.id == id) it.copy(percent = percent, totalBytes = total) else it } }
                    }
                }
        }
    }

    /** The user said yes to download [id]: it carries on, and goes to the Downloads folder when done. */
    fun save(id: Int) {
        val completed =
            synchronized(lock) {
                if (!asking.remove(id)) return
                saved += id
                completeWhileAsking.remove(id)
            }
        setState(id, BrowserDownload.State.DOWNLOADING)
        if (completed) deliver(id) else controls[id]?.resume()
    }

    /** The user said no to download [id], or stopped it: nothing of it stays. */
    fun cancel(id: Int) {
        val wasAsking =
            synchronized(lock) {
                val wasAsking = asking.remove(id)
                if (wasAsking) refused += id
                completeWhileAsking.remove(id)
                wasAsking
            }
        if (wasAsking) mutableList.update { list -> list.filter { it.id != id } }
        val control = controls.remove(id)
        if (control != null) control.cancel() else discard(id)
    }

    /** Takes a finished download off the list. */
    fun dismiss(id: Int) {
        mutableList.update { list -> list.filter { it.id != id || !it.state.finished } }
    }

    /**
     * The page closed: every download of it stops, and its folder goes once those already complete
     * have reached the Downloads folder.
     */
    fun close() {
        synchronized(lock) {
            closed = true
            refused += asking
            refused += saved
            asking.clear()
            saved.clear()
            completeWhileAsking.clear()
        }
        controls.values.forEach { control -> runCatching { control.cancel() }.onFailure { Log.w(TAG, "A download did not stop", it) } }
        controls.clear()
        val pending = synchronized(deliveries) { deliveries.toList() }
        scope.launch {
            pending.joinAll()
            // Chromium deletes what it wrote for a canceled download; anything else goes here.
            delay(CLEANUP_DELAY_MS)
            if (pageFolder.exists() && !pageFolder.deleteRecursively()) Log.w(TAG) { "Could not delete $pageFolder" }
        }
    }

    // Moves a complete download out of [holding] into the Downloads folder.
    private fun deliver(id: Int) {
        // Taken, so a second report of it complete delivers nothing.
        val source = targets.remove(id) ?: return
        val name = source.name
        val job =
            scope.launch {
                val target = BrowserDownloads.reserve(BrowserDownloads.folder(), name)
                try {
                    move(source, target)
                    mutableList.update { list -> list.map { if (it.id == id) it.copy(state = BrowserDownload.State.DONE, percent = 100, file = target) else it } }
                } catch (e: IOException) {
                    Log.w(TAG, "Could not move a download to $target", e)
                    setState(id, BrowserDownload.State.STOPPED)
                } finally {
                    // Moved, it exists: [reserve] sees it from now on. Not moved, the name is free.
                    BrowserDownloads.release(target)
                    deleteLater(source.parentFile)
                }
            }
        synchronized(deliveries) { deliveries += job }
    }

    // Deletes what is left of download [id] in [holding].
    private fun discard(id: Int) {
        targets.remove(id)?.parentFile?.let(::deleteLater)
    }

    private fun deleteLater(folder: File) {
        scope.launch { if (folder.exists() && !folder.deleteRecursively()) Log.w(TAG) { "Could not delete $folder" } }
    }

    private fun setState(
        id: Int,
        state: BrowserDownload.State,
    ) = mutableList.update { list -> list.map { if (it.id == id) it.copy(state = state) else it } }

    private fun setSize(
        id: Int,
        item: CefDownloadItem,
    ) {
        val total = item.totalBytes.takeIf { it > 0 } ?: -1
        mutableList.update { list -> list.map { if (it.id == id) it.copy(totalBytes = total) else it } }
    }

    // At most MAX_SHOWN, dropping the oldest finished ones: one still asking or running stays, or
    // its question and its Cancel would be lost.
    private fun withinShown(list: List<BrowserDownload>): List<BrowserDownload> {
        var shown = list
        while (shown.size > MAX_SHOWN) {
            val oldest = shown.firstOrNull { it.state.finished } ?: break
            shown = shown - oldest
        }
        return shown
    }

    private enum class Stage { UNKNOWN, REFUSED, ASKING, SAVED }

    companion object {
        private const val TAG = "PageDownloads"

        /** Questions a page may have waiting at once; more downloads are refused unasked. */
        const val MAX_QUESTIONS = 3

        /** Downloads the screen lists at once. */
        private const val MAX_SHOWN = 5

        private const val CLEANUP_DELAY_MS = 2_000L

        // A rename where it can be one; a copy across file systems (the cache and the home folder may
        // be on different disks).
        private fun move(
            source: File,
            target: File,
        ) {
            try {
                Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(source.toPath(), target.toPath())
            }
        }
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
    // downloads of the same name would otherwise both be given the same free name. Kept without case:
    // macOS and Windows file systems tell no case apart, so "Report.pdf" and "report.pdf" are one file.
    private val reserved = HashSet<String>()

    private fun keyOf(file: File) = file.absolutePath.lowercase(Locale.ROOT)

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
            while (candidate.exists() || keyOf(candidate) in reserved) {
                candidate = File(folder, "$stem ($n)$extension")
                n++
            }
            reserved += keyOf(candidate)
            candidate
        }

    /** [file]'s download ended, saved or not: its name is free again for the next one. */
    fun release(file: File) {
        synchronized(reserved) { reserved -= keyOf(file) }
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
