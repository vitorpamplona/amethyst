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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
            val stage = async { reads.frame(key, read) }
            val still = async { reads.frame(key, read) }
            testScheduler.runCurrent()
            gate.complete(Unit)
            assertContentEquals(byteArrayOf(1, 2, 3), stage.await())
            assertContentEquals(byteArrayOf(1, 2, 3), still.await())
            assertEquals(1, decodes)
            assertContentEquals(byteArrayOf(1, 2, 3), reads.frameNow(key), "shown at once next time")
        }

    @Test
    fun aReadThatFoundNothingIsTriedAgain() =
        runTest {
            val reads = StudioReads(backgroundScope)
            var probes = 0
            assertNull(reads.duration(video) { probes++.let { null } })
            assertNull(reads.durationNow(video))
            assertEquals(6_000L, reads.duration(video) { probes++.let { 6_000L } })
            assertEquals(2, probes)
        }

    @Test
    fun aReadThatThrowsDoesNotStopTheOthers() =
        runTest {
            val reads = StudioReads(backgroundScope)
            assertNull(reads.duration(video) { error("ffprobe crashed") })
            assertEquals(6_000L, reads.duration(video) { 6_000L })
        }
}
