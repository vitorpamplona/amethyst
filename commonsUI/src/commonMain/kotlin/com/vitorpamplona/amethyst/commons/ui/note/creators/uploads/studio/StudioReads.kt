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

import androidx.collection.LruCache
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
 * once isn't taken as the file's answer. A read nobody waits for any more (a drag moved on to
 * another frame) is stopped. Keyed by the pick itself, not its path, which a camera or a temp file
 * can reuse for another file. UI thread only.
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

    val frames = Shared<FrameKey, ByteArray>(KEEP_FRAMES)
    val durations = Shared<SelectedMedia, Long>(KEEP_FILES)
    val probes = Shared<SelectedMedia, VideoInfo>(KEEP_FILES)

    /** One kind of read, the last [keep] of them kept, the least recently used dropped first. */
    inner class Shared<K : Any, V : Any>(
        keep: Int,
    ) {
        private val reads = LruCache<K, Read<V>>(keep)

        /** What [key] read, if it is done; never waits. */
        fun now(key: K): V? = reads[key]?.finished()

        /** Whether [key] was read or is being read: a caller that would wait for a drag to settle needn't. */
        fun has(key: K): Boolean = reads[key] != null

        /** What [key] reads, sharing a read already running. */
        suspend fun get(
            key: K,
            read: suspend () -> V?,
        ): V? {
            val entry = reads[key] ?: Read(scope.async { readOrNull(read) }).also { reads.put(key, it) }
            entry.waiters++
            try {
                return entry.job.await().also { if (it == null) forget(key, entry) }
            } finally {
                entry.waiters--
                if (entry.waiters == 0 && !entry.job.isCompleted) {
                    entry.job.cancel()
                    forget(key, entry)
                }
            }
        }

        private fun forget(
            key: K,
            entry: Read<V>,
        ) {
            if (reads[key] === entry) reads.remove(key)
        }
    }

    private class Read<V>(
        val job: Deferred<V?>,
    ) {
        /** Callers awaiting [job]; when the last one leaves before it is done, it is cancelled. */
        var waiters = 0

        @OptIn(ExperimentalCoroutinesApi::class)
        fun finished(): V? = job.takeIf { it.isCompleted && !it.isCancelled }?.getCompleted()
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

    private companion object {
        const val KEEP_FRAMES = 12
        const val KEEP_FILES = 16
    }
}

/** [key] through the open Studio's shared reads ([this]), or read here when there is no Studio. */
internal suspend fun <K : Any, V : Any> StudioReads.Shared<K, V>?.getOrRead(
    key: K,
    read: suspend () -> V?,
): V? = if (this != null) get(key, read) else read()

/** The open Studio's [StudioReads]; null outside it (a composer's card reads on its own). */
internal val LocalStudioReads = staticCompositionLocalOf<StudioReads?> { null }
