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

import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.StringMediaUri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudioReadsTest {
    private val video = SelectedMedia(StringMediaUri("/tmp/reads.mp4"), "video/mp4")

    @Test
    fun partsOfTheStudioAskingAtOnceShareOneRead() =
        runTest {
            val reads = StudioReads(backgroundScope)
            val key = StudioReads.FrameKey(video, 0, 1280)
            val gate = CompletableDeferred<Unit>()
            var decodes = 0
            val read: suspend () -> ByteArray? = {
                decodes++
                gate.await()
                byteArrayOf(1, 2, 3)
            }
            val stage = async { reads.frames.get(key, read) }
            val still = async { reads.frames.get(key, read) }
            testScheduler.runCurrent()
            gate.complete(Unit)
            assertContentEquals(byteArrayOf(1, 2, 3), stage.await())
            assertContentEquals(byteArrayOf(1, 2, 3), still.await())
            assertEquals(1, decodes)
            assertContentEquals(byteArrayOf(1, 2, 3), reads.frames.now(key), "shown at once next time")
        }

    @Test
    fun aReadThatFoundNothingIsTriedAgain() =
        runTest {
            val reads = StudioReads(backgroundScope)
            var probes = 0
            assertNull(reads.durations.get(video) { probes++.let { null } })
            assertNull(reads.durations.now(video))
            assertEquals(6_000L, reads.durations.get(video) { probes++.let { 6_000L } })
            assertEquals(2, probes)
        }

    @Test
    fun aReadThatThrowsDoesNotStopTheOthers() =
        runTest {
            val reads = StudioReads(backgroundScope)
            assertNull(reads.durations.get(video) { error("ffprobe crashed") })
            assertEquals(6_000L, reads.durations.get(video) { 6_000L })
        }

    @Test
    fun aReadNobodyWaitsForAnyMoreIsStopped() =
        runTest {
            val reads = StudioReads(backgroundScope)
            val key = StudioReads.FrameKey(video, 0, 1280)
            var stopped = false
            val read: suspend () -> ByteArray? = {
                try {
                    awaitCancellation()
                } finally {
                    stopped = true
                }
            }
            // The drag moved on before the frame was decoded.
            val stage = async { reads.frames.get(key, read) }
            testScheduler.runCurrent()
            stage.cancel()
            testScheduler.runCurrent()
            assertTrue(stopped, "the decode stops with its last caller")
            assertFalse(reads.frames.has(key), "and is forgotten")
        }

    @Test
    fun aReadAnotherPartStillWaitsForKeepsGoing() =
        runTest {
            val reads = StudioReads(backgroundScope)
            val key = StudioReads.FrameKey(video, 0, 1280)
            val gate = CompletableDeferred<Unit>()
            val read: suspend () -> ByteArray? = {
                gate.await()
                byteArrayOf(7)
            }
            val stage = async { reads.frames.get(key, read) }
            val still = async { reads.frames.get(key, read) }
            testScheduler.runCurrent()
            stage.cancel()
            gate.complete(Unit)
            assertContentEquals(byteArrayOf(7), still.await())
        }
}
