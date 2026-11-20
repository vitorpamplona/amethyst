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
package com.vitorpamplona.amethyst.commons.ui.prototypes.mediacomposer

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.insert
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/*
 * PROTOTYPE ONLY. The state behind the media-composer prototypes: what one picked file carries
 * between being picked and being posted, and a fake composer that uploads on Post.
 *
 * The production counterparts are SelectedMedia + MultiOrchestrator + UploadOrchestrator
 * (commons/service/uploads) and ShortNotePostViewModel.upload(). The one structural change the
 * prototypes argue for is visible here: every per-file choice (crop, trim, quality, alt, sensitive,
 * metadata) lives on the draft, and nothing is uploaded until [PrototypeComposer.post].
 */

enum class DraftKind { IMAGE, VIDEO }

/**
 * Four named stops instead of six levels plus a paragraph of explanation. Each stop shows the
 * size it produces, which says more than the explainer did. Factors are made-up estimates.
 */
enum class QualityStop(
    val label: String,
    val maxEdge: Int?,
    val bytesFactor: Float,
    val videoLabel: String,
) {
    SMALL("Small", 1024, 0.05f, "720p"),
    STANDARD("Standard", 1920, 0.16f, "1080p"),
    HIGH("High", 2560, 0.32f, "1440p"),
    ORIGINAL("Original", null, 1f, "Original"),
}

enum class AspectPreset(
    val label: String,
    val ratio: Float?,
) {
    FREE("Free", null),
    SQUARE("1:1", 1f),
    PORTRAIT("4:5", 4f / 5f),
    LANDSCAPE("16:9", 16f / 9f),
    BANNER("3:1", 3f),
}

/** The crop in normalized image coordinates (0..1), plus quarter turns and a mirror. */
data class CropSpec(
    val rect: Rect = Rect(0f, 0f, 1f, 1f),
    val aspect: AspectPreset = AspectPreset.FREE,
    val quarterTurns: Int = 0,
    val mirrored: Boolean = false,
) {
    val isIdentity get() = rect == Rect(0f, 0f, 1f, 1f) && quarterTurns % 4 == 0 && !mirrored
}

data class TrimSpec(
    val startMs: Long,
    val endMs: Long,
) {
    val lengthMs get() = endMs - startMs
}

sealed interface DraftStatus {
    /** Picked, edited locally, nothing sent. */
    data object Local : DraftStatus

    data class Preparing(
        val progress: Float,
    ) : DraftStatus

    data class Uploading(
        val progress: Float,
    ) : DraftStatus

    data class Failed(
        val reason: String,
    ) : DraftStatus

    data class Uploaded(
        val url: String,
    ) : DraftStatus
}

@Stable
class MediaDraft(
    val id: Int,
    val kind: DraftKind,
    /** Stand-in for the decoded file; the prototypes never touch disk. */
    val bitmap: ImageBitmap,
    val widthPx: Int,
    val heightPx: Int,
    val originalBytes: Long,
    val durationMs: Long? = null,
) {
    var quality by mutableStateOf(QualityStop.STANDARD)
    var alt by mutableStateOf("")
    var altSuggested by mutableStateOf(false)
    var sensitive by mutableStateOf(false)
    var stripLocation by mutableStateOf(true)
    var muted by mutableStateOf(false)
    var crop by mutableStateOf(CropSpec())
    var trim by mutableStateOf(durationMs?.let { TrimSpec(0, it) })
    var status by mutableStateOf<DraftStatus>(DraftStatus.Local)

    val token: String get() = MediaTokens.of(id)

    val aspectRatio: Float get() {
        val r = crop.rect
        val w = widthPx * r.width
        val h = heightPx * r.height
        return if (crop.quarterTurns % 2 == 1) h / w else w / h
    }

    /** Pixel size after crop and the quality cap. */
    fun outputSize(stop: QualityStop = quality): Pair<Int, Int> {
        val r = crop.rect
        var w = (widthPx * r.width).roundToInt()
        var h = (heightPx * r.height).roundToInt()
        if (crop.quarterTurns % 2 == 1) w = h.also { h = w }
        val edge = stop.maxEdge ?: return w to h
        val long = maxOf(w, h)
        if (long <= edge) return w to h
        val scale = edge.toFloat() / long
        return (w * scale).roundToInt() to (h * scale).roundToInt()
    }

    fun estimatedBytes(stop: QualityStop = quality): Long {
        val area = crop.rect.width * crop.rect.height
        val trimmed = trim?.let { t -> durationMs?.let { t.lengthMs.toFloat() / it } } ?: 1f
        // Original with a crop has to be re-encoded, but keeps its quality: roughly the area share.
        return (originalBytes * area * trimmed * stop.bytesFactor).roundToLong().coerceAtLeast(12_000)
    }

    val isEdited: Boolean get() = !crop.isIdentity || (trim != null && trim != TrimSpec(0, durationMs ?: 0))
}

/**
 * The marker a picked file leaves in the text, where its URL will go once it uploads. It is plain
 * text so undo, cut/paste, drafts and the existing OutputTransformation machinery all just work;
 * the field draws it as a chip or a block, never as these characters.
 */
object MediaTokens {
    val REGEX = Regex("""\[\[media:(\d+)]]""")

    fun of(id: Int) = "[[media:$id]]"

    fun idsInOrder(text: CharSequence): List<Int> = REGEX.findAll(text).map { it.groupValues[1].toInt() }.toList()
}

enum class ComposerPhase { EDITING, PUBLISHING, NEEDS_ATTENTION, PUBLISHED }

/**
 * A fake composer: picks add drafts and markers, Post uploads every draft that isn't up yet, and
 * only publishes once all of them are. A failure keeps the screen, the text and the edits exactly
 * as they were, and points at the file that failed.
 */
@Stable
class PrototypeComposer(
    private val scope: CoroutineScope,
) {
    val message = TextFieldState()
    val drafts = mutableStateListOf<MediaDraft>()
    var server by mutableStateOf(SERVERS[0])
    var phase by mutableStateOf(ComposerPhase.EDITING)
    var editing by mutableStateOf<MediaDraft?>(null)

    /**
     * Files just picked, open in the Studio and not in the text yet. Done inserts their markers;
     * Cancel drops them, so a pick the user backs out of leaves no trace in the post.
     */
    var picking by mutableStateOf<SnapshotStateList<MediaDraft>?>(null)

    /** Simulation knob: drafts with these ids fail on the next Post. */
    var failNext = mutableSetOf<Int>()

    private var publishJob: Job? = null

    fun draft(id: Int) = drafts.firstOrNull { it.id == id }

    /** Drafts in the order their markers appear in the text: the numbering the user sees. */
    fun orderedDrafts(): List<MediaDraft> = MediaTokens.idsInOrder(message.text).mapNotNull(::draft)

    fun numberOf(draft: MediaDraft): Int = MediaTokens.idsInOrder(message.text).indexOf(draft.id) + 1

    fun pick(newDrafts: List<MediaDraft>) {
        picking = mutableStateListOf<MediaDraft>().apply { addAll(newDrafts) }
    }

    fun confirmPick() {
        picking?.forEach(::addMedia)
        picking = null
    }

    fun cancelPick() {
        picking = null
    }

    /** Inserts the marker at the cursor, on its own line, the way the URL used to land after upload. */
    fun addMedia(newDraft: MediaDraft) {
        drafts += newDraft
        message.edit {
            val at = selection.max
            val before = if (at > 0 && asCharSequence()[at - 1] != '\n') "\n" else ""
            val after = if (at < length && asCharSequence()[at] == '\n') "" else "\n"
            val insertion = before + newDraft.token + after
            insert(at, insertion)
            selection = TextRange(at + insertion.length)
        }
    }

    fun remove(target: MediaDraft) {
        drafts.remove(target)
        message.edit {
            val text = asCharSequence().toString()
            val start = text.indexOf(target.token)
            if (start >= 0) {
                var end = start + target.token.length
                if (end < text.length && text[end] == '\n') end++
                replace(start, end, "")
            }
        }
    }

    fun canPost() = phase == ComposerPhase.EDITING || phase == ComposerPhase.NEEDS_ATTENTION

    /** Upload everything not yet uploaded, then publish. Never inserts a URL before all succeed. */
    fun post() {
        if (!canPost()) return
        phase = ComposerPhase.PUBLISHING
        publishJob =
            scope.launch {
                orderedDrafts()
                    .filter { it.status !is DraftStatus.Uploaded }
                    .mapIndexed { index, d -> launch { simulateUpload(d, index, server, fail = d.id in failNext) } }
                    .joinAll()

                if (orderedDrafts().any { it.status is DraftStatus.Failed }) {
                    phase = ComposerPhase.NEEDS_ATTENTION
                } else {
                    // The only moment markers become URLs: right before signing.
                    message.edit {
                        orderedDrafts().forEach { d ->
                            val text = asCharSequence().toString()
                            val start = text.indexOf(d.token)
                            val url = (d.status as? DraftStatus.Uploaded)?.url
                            if (start >= 0 && url != null) replace(start, start + d.token.length, url)
                        }
                    }
                    phase = ComposerPhase.PUBLISHED
                }
            }
    }

    /** Back to editing; whatever already uploaded stays uploaded (Blossom dedups by hash anyway). */
    fun cancelPublishing() {
        publishJob?.cancel()
        drafts.forEach { if (it.status !is DraftStatus.Uploaded) it.status = DraftStatus.Local }
        phase = ComposerPhase.EDITING
    }

    fun retry(target: MediaDraft) {
        failNext.remove(target.id)
        target.status = DraftStatus.Local
        post()
    }

    companion object {
        val SERVERS = listOf("blossom.primal.net", "nostr.download", "cdn.satellite.earth")
    }
}

/**
 * A fake upload: compress, then send, with progress. Every host (post, chat, profile) uses it, as
 * every host would use the same UploadOrchestrator.
 */
suspend fun simulateUpload(
    d: MediaDraft,
    index: Int,
    server: String,
    fail: Boolean = false,
) {
    delay(150L * index)
    for (i in 1..6) {
        d.status = DraftStatus.Preparing(i / 6f)
        delay(90)
    }
    for (i in 1..10) {
        d.status = DraftStatus.Uploading(i / 10f)
        delay(110)
        if (i == 6 && fail) {
            d.status = DraftStatus.Failed("$server refused the file: too large (413)")
            return
        }
    }
    d.status = DraftStatus.Uploaded("https://$server/${(d.id * 7919).toString(16)}e3b0c44298fc1c149afb.jpg")
}

fun formatBytes(bytes: Long): String =
    when {
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000f)
        bytes >= 1_000 -> "${(bytes / 1_000f).roundToInt()} KB"
        else -> "$bytes B"
    }

fun formatDuration(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}
