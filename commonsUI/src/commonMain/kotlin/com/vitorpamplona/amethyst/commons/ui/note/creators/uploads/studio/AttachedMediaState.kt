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
package com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.studio

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.service.upload.MediaUploadTracker
import com.vitorpamplona.amethyst.commons.service.uploads.MediaMarkers
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploadOptions
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMediaProcessing
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.ui.text.onUiThread
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner

/**
 * The media a composer attaches to the text in [message], from the pick to the post:
 *
 * 1. [pick] holds the picked files ([picking]) while the media studio is open.
 * 2. [attachPicked] (the studio's Add) leaves a [MediaMarkers] marker per file at the cursor, on a
 *    line of its own, and keeps the file under the marker's id ([items]). Nothing is uploaded.
 *    A file shows while its marker is in the text: deleting the marker (typing or Remove) drops
 *    it, undo brings it back. A pasted copy of a marker shows the file again but sends it once.
 * 3. [uploadAll] (on Post) uploads every file with its own options, swaps each marker for its URL
 *    (the composer records the `imeta` in `attach`), and only then may the composer sign. When an
 *    upload fails it returns false and changes nothing: the composer stays, and Post retries only
 *    what failed.
 *
 * A marker never leaves the device: drafts and link previews read [stripMarkers] of the text, and a
 * marker whose file is gone is dropped at Post. One holder for every composer, so they all behave
 * alike; the UI is `AttachedMediaStudioDialogs` and `rememberAttachedMediaBlocks`.
 */
@Stable
class AttachedMediaState(
    private val message: TextFieldState,
) {
    /** Just picked, open in the studio, not in the text yet. */
    var picking by mutableStateOf<MultiOrchestrator?>(null)

    /** The files in the text, by their marker's id. */
    val items = mutableStateMapOf<Int, SelectedMediaProcessing>()
    private var nextId = 1

    /** Where the files go on Post: the server last chosen in the studio; null for the account's default. */
    var server by mutableStateOf<ServerName?>(null)

    /** A file of the text reopened in the studio. */
    var reopened by mutableStateOf<SelectedMediaProcessing?>(null)

    /** Bumped when files go into the text, so the field takes the focus back from the studio. */
    var attachCount by mutableIntStateOf(0)
        private set

    fun pick(
        media: List<SelectedMedia>,
        stripMetadata: Boolean,
    ) {
        picking = MultiOrchestrator(media) { MediaUploadOptions.defaultFor(it, stripMetadata = stripMetadata) }
    }

    /** Drops one file from the studio's pick; the pick ends when none is left. */
    fun removePicked(item: SelectedMediaProcessing) {
        val batch = picking ?: return
        batch.remove(item)
        if (batch.size() == 0) picking = null
    }

    /**
     * The marker ids in the text, in reading order, each once: a pasted copy of a marker is the same
     * file, sent once. Scanned again only when the text changes, not on every recomposition.
     */
    private val markerIds by derivedStateOf { MediaMarkers.idsIn(message.text).distinct() }

    /** The files in the order their markers appear in the text: the numbers the user sees. */
    fun inOrder(): List<SelectedMediaProcessing> = markerIds.mapNotNull { items[it] }

    fun hasMedia() = markerIds.isNotEmpty()

    /**
     * The studio's Add: each picked file gets a marker at the cursor, on a line of its own. Files
     * added at the very start of the text (an empty composer, most of the time) go under an empty
     * first line that keeps the cursor: a card fills its line and opens the studio when tapped, so
     * without that line there was no place to write the text above it.
     */
    fun attachPicked(chosenServer: ServerName) {
        val batch = picking ?: return
        server = chosenServer
        message.edit {
            val start = selection.max
            var cursor = start
            batch.items().forEach { item ->
                val id = nextId++
                items[id] = item
                // Held while the composer keeps the file (undo can bring back a deleted marker),
                // and taken before the Studio lets go, so a trimmed GIF's copy isn't written again.
                item.holdTrimmedGif()
                val insertion = MediaMarkers.insertion(asCharSequence(), cursor, id)
                replace(cursor, cursor, insertion)
                cursor += insertion.length
            }
            if (start == 0 && cursor > 0) {
                replace(0, 0, "\n")
                cursor = 0
            }
            selection = TextRange(cursor)
        }
        picking = null
        attachCount++
    }

    private fun idOf(item: SelectedMediaProcessing): Int? = items.entries.firstOrNull { it.value === item }?.key

    /** Takes [item] out: its marker and the line it sat on. */
    fun remove(item: SelectedMediaProcessing) {
        removeMarker(idOf(item) ?: return)
    }

    /**
     * Takes the marker [id] out, with the line it sat on. Its file stays known, like a marker deleted
     * by typing, so undo brings the file back with it; only markers in the text are ever sent.
     */
    fun removeMarker(id: Int) {
        message.edit {
            val text = asCharSequence().toString()
            val marker = MediaMarkers.of(id)
            val start = text.indexOf(marker)
            if (start >= 0) {
                var end = start + marker.length
                if (end < text.length && text[end] == '\n') end++
                replace(start, end, "")
            }
        }
    }

    /**
     * Uploads every file in the text with its own options to [server] (or [defaultServer]) and swaps
     * each marker for the URL [attach] returns, after it records the file's `imeta`. Markers whose
     * file is gone are dropped. False, with the text untouched, when any upload failed.
     */
    suspend fun uploadAll(
        defaultServer: ServerName,
        account: Account,
        uploader: MediaUploader,
        tracker: MediaUploadTracker,
        onStrippingFailed: suspend () -> Boolean,
        forcedSigner: NostrSigner?,
        attach: (uploaded: UploadOrchestrator.OrchestratorResult.ServerResult, options: MediaUploadOptions) -> String,
    ): Boolean {
        val ids = markerIds
        if (ids.isEmpty()) return true
        val files = ids.mapNotNull { items[it] }

        val urlById = HashMap<Int, String>()
        if (files.isNotEmpty()) {
            val batch = MultiOrchestrator.of(files)
            tracker.startUpload(batch.hasNonMedia())
            val results =
                try {
                    batch.uploadEach(server ?: defaultServer, account, uploader, onStrippingFailed, forcedSigner)
                } finally {
                    tracker.finishUpload()
                }
            if (!results.allGood) return false

            results.successful.forEach { uploaded ->
                val id = idOf(uploaded.item) ?: return@forEach
                (uploaded.finished.result as? UploadOrchestrator.OrchestratorResult.ServerResult)
                    ?.let { urlById[id] = attach(it, uploaded.item.options) }
            }
        }

        onUiThread {
            // The empty line left above files added at the start (see attachPicked) goes if nothing was written there.
            message.edit { replace(0, length, MediaMarkers.replace(asCharSequence().toString()) { urlById[it] }.removePrefix("\n")) }
        }
        releaseAll()
        return true
    }

    /** Lets go of every file this composer kept. */
    private fun releaseAll() {
        items.values.forEach { it.releaseTrimmedGif() }
        items.clear()
    }

    fun clear() {
        picking = null
        releaseAll()
        reopened = null
        server = null
    }

    companion object {
        /** [text] without markers: what drafts save and link previews read. */
        fun stripMarkers(text: String): String = MediaMarkers.strip(text)
    }
}
