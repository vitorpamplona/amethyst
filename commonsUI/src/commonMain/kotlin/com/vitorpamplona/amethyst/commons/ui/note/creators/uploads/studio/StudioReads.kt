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

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.video.VideoInfo
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async

/**
 * What the open Studio reads from its files: a video's or a GIF's frames, length and probe. Each is
 * read once however many parts of the Studio ask at the same time (the stage, its still, the Trim
 * panel), shown at once when a tool asks again, and dropped with the Studio, whose [scope] runs the
 * reads. A read that found nothing is forgotten, so the next ask tries again: a probe that failed
 * once isn't taken as the file's answer. Keyed by the pick itself, not its path, which a camera or a
 * temp file can reuse for another file. UI thread only.
 */
@Stable
internal class StudioReads(
    private val scope: CoroutineScope,
) {
    data class FrameKey(
        val media: SelectedMedia,
        val bucket: Long,
        val maxEdge: Int,
    )

    private val frames = Recent<FrameKey, ByteArray>(KEEP_FRAMES)
    private val durations = Recent<SelectedMedia, Long>(KEEP_FILES)
    private val probes = Recent<SelectedMedia, VideoInfo>(KEEP_FILES)

    fun frameNow(key: FrameKey): ByteArray? = frames[key].finished()

    /** Whether [key] was read or is being read: a caller that would wait for a drag to settle needn't. */
    fun hasFrame(key: FrameKey): Boolean = frames.contains(key)

    suspend fun frame(
        key: FrameKey,
        read: suspend () -> ByteArray?,
    ): ByteArray? = shared(frames, key, read)

    fun durationNow(media: SelectedMedia): Long? = durations[media].finished()

    suspend fun duration(
        media: SelectedMedia,
        read: suspend () -> Long?,
    ): Long? = shared(durations, media, read)

    fun probeNow(media: SelectedMedia): VideoInfo? = probes[media].finished()

    suspend fun probe(
        media: SelectedMedia,
        read: suspend () -> VideoInfo?,
    ): VideoInfo? = shared(probes, media, read)

    private suspend fun <K, V : Any> shared(
        reads: Recent<K, V>,
        key: K,
        read: suspend () -> V?,
    ): V? {
        val job = reads[key] ?: scope.async { readOrNull(read) }.also { reads[key] = it }
        val result = job.await()
        if (result == null) reads.removeIf(key, job)
        return result
    }

    /** A read that throws is a read that found nothing; one failure mustn't cancel the Studio's other reads. */
    private suspend fun <V> readOrNull(read: suspend () -> V?): V? =
        try {
            read()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("StudioReads", "Could not read the file", e)
            null
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <V> Deferred<V?>?.finished(): V? = this?.takeIf { it.isCompleted && !it.isCancelled }?.getCompleted()

    private companion object {
        const val KEEP_FRAMES = 12
        const val KEEP_FILES = 16
    }

    /** The last [keep] reads, the least recently used dropped first. Plain common collections: this runs on iOS too. */
    private class Recent<K, V>(
        private val keep: Int,
    ) {
        private val reads = LinkedHashMap<K, Deferred<V?>>()

        operator fun get(key: K): Deferred<V?>? = reads.remove(key)?.also { reads[key] = it }

        operator fun set(
            key: K,
            read: Deferred<V?>,
        ) {
            reads.remove(key)
            reads[key] = read
            while (reads.size > keep) reads.remove(reads.keys.first())
        }

        fun contains(key: K): Boolean = key in reads

        fun removeIf(
            key: K,
            read: Deferred<V?>,
        ) {
            if (reads[key] === read) reads.remove(key)
        }
    }
}

/** The open Studio's [StudioReads]; null outside it (a composer's card reads on its own). */
internal val LocalStudioReads = staticCompositionLocalOf<StudioReads?> { null }
