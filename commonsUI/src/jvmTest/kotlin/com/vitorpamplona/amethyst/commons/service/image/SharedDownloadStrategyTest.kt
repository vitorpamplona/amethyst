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
package com.vitorpamplona.amethyst.commons.service.image

import coil3.annotation.ExperimentalCoilApi
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.SourceFetchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.FileSystem
import okio.ForwardingSource
import okio.buffer
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoilApi::class, ExperimentalCoroutinesApi::class)
class SharedDownloadStrategyTest {
    /** A download the test controls: it reports [size], waits for [finish], and records how it ended. */
    private class FakeDownload(
        val size: Long? = null,
    ) {
        val finish = CompletableDeferred<Unit>()
        var runs = 0
        var completed = 0
        var cancelled = 0
        var sourceClosed = false

        val block: suspend () -> FetchResult = {
            runs++
            if (size != null) currentCoroutineContext()[DownloadSize]?.bytes?.value = size
            try {
                finish.await()
                completed++
                result()
            } catch (e: CancellationException) {
                cancelled++
                throw e
            }
        }

        fun result(): FetchResult {
            val source =
                object : ForwardingSource(Buffer().writeUtf8("image")) {
                    override fun close() {
                        sourceClosed = true
                        super.close()
                    }
                }
            return SourceFetchResult(ImageSource(source.buffer(), FileSystem.SYSTEM), "image/jpeg", DataSource.NETWORK)
        }
    }

    private fun TestScope.strategy(maxOrphans: Int = SharedDownloadStrategy.MAX_ORPHANS) = SharedDownloadStrategy(CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)), maxOrphans = maxOrphans)

    @Test
    fun aRequestThatTakesOverAMovedCardsDownloadDoesNotRestartIt() =
        runTest {
            val strategy = strategy()
            val download = FakeDownload()
            val old = async { strategy.apply("img", download.block) }
            runCurrent()
            // The card moved: its new place asks for the image, then the old place goes.
            val moved = async { strategy.apply("img", download.block) }
            runCurrent()
            old.cancel()
            runCurrent()

            download.finish.complete(Unit)
            val result = moved.await()

            assertEquals(1, download.runs)
            assertEquals(0, download.cancelled)
            assertEquals(DataSource.NETWORK, (result as SourceFetchResult).dataSource)
        }

    @Test
    fun aSmallDownloadNobodyWaitsForFinishesAndReleasesItsResult() =
        runTest {
            val strategy = strategy()
            val download = FakeDownload(size = 64L * 1024)
            val request = async { strategy.apply("img", download.block) }
            runCurrent()
            request.cancel()
            runCurrent()

            download.finish.complete(Unit)
            runCurrent()

            assertEquals(1, download.completed)
            assertEquals(0, download.cancelled)
            assertTrue(download.sourceClosed)
        }

    @Test
    fun aLargeDownloadNobodyWaitsForIsCut() =
        runTest {
            val strategy = strategy()
            val download = FakeDownload(size = 4L * 1024 * 1024)
            val request = async { strategy.apply("img", download.block) }
            runCurrent()
            request.cancel()
            runCurrent()

            assertEquals(1, download.cancelled)
            assertEquals(0, download.completed)
        }

    @Test
    fun aDownloadThatHasNotSaidItsSizeGetsAMomentThenIsCut() =
        runTest {
            val strategy = strategy()
            val download = FakeDownload(size = null)
            val request = async { strategy.apply("img", download.block) }
            runCurrent()
            request.cancel()

            advanceTimeBy(SharedDownloadStrategy.SIZE_WAIT_MS - 1)
            assertEquals(0, download.cancelled)

            advanceTimeBy(2)
            runCurrent()
            assertEquals(1, download.cancelled)
        }

    @Test
    fun aRequestDuringTheWaitKeepsTheDownload() =
        runTest {
            val strategy = strategy()
            val download = FakeDownload(size = null)
            val first = async { strategy.apply("img", download.block) }
            runCurrent()
            first.cancel()
            advanceTimeBy(SharedDownloadStrategy.SIZE_WAIT_MS / 2)

            val again = async { strategy.apply("img", download.block) }
            advanceTimeBy(SharedDownloadStrategy.SIZE_WAIT_MS)
            download.finish.complete(Unit)

            again.await()
            assertEquals(1, download.runs)
            assertEquals(0, download.cancelled)
        }

    @Test
    fun aRequestAfterACutStartsANewDownload() =
        runTest {
            val strategy = strategy()
            val download = FakeDownload(size = 4L * 1024 * 1024)
            val first = async { strategy.apply("img", download.block) }
            runCurrent()
            first.cancel()
            runCurrent()

            val again = async { strategy.apply("img", download.block) }
            runCurrent()
            download.finish.complete(Unit)
            again.await()

            assertEquals(2, download.runs)
            assertEquals(1, download.completed)
        }

    @Test
    fun onlyTheOrphanCapFinishesTheRestAreCut() =
        runTest {
            val strategy = strategy(maxOrphans = 1)
            val a = FakeDownload(size = 1024)
            val b = FakeDownload(size = 1024)
            val ra = async { strategy.apply("a", a.block) }
            val rb = async { strategy.apply("b", b.block) }
            runCurrent()
            ra.cancel()
            runCurrent()
            rb.cancel()
            runCurrent()

            assertEquals(0, a.cancelled)
            assertEquals(1, b.cancelled)
        }

    @Test
    fun theFirstRequestBackTakesTheResultTheOthersReadTheirOwn() =
        runTest {
            val strategy = strategy()
            val download = FakeDownload()
            val first = async { strategy.apply("img", download.block) }
            val second = async { strategy.apply("img", download.block) }
            runCurrent()
            download.finish.complete(Unit)

            val r1 = first.await()
            val r2 = second.await()

            // The second runs its own fetch, which in the loader reads the copy in the disk cache.
            assertEquals(2, download.runs)
            assertFalse(r1 === r2)
        }

    @Test
    fun aFailureGoesToTheFirstRequestAndTheOthersTryAgain() =
        runTest {
            val strategy = strategy()
            val failure = IOException("boom")
            var runs = 0
            val ok = FakeDownload().result()
            val gate = CompletableDeferred<Unit>()
            val block: suspend () -> FetchResult = {
                runs++
                gate.await()
                if (runs == 1) throw failure else ok
            }
            val first = async { runCatching { strategy.apply("img", block) } }
            val second = async { strategy.apply("img", block) }
            runCurrent()
            gate.complete(Unit)

            // Coroutines copy an exception across await to add the waiter's stack: same type and message.
            val thrown = first.await().exceptionOrNull()
            assertTrue(thrown is IOException && thrown.message == failure.message)
            assertSame(ok, second.await())
            assertEquals(2, runs)
        }

    @Test
    fun aCancelledRequestStillThrowsCancellation() =
        runTest {
            val strategy = strategy()
            val download = FakeDownload(size = 1024)
            val request = async { strategy.apply("img", download.block) }
            runCurrent()
            request.cancel()
            assertFailsWith<CancellationException> { request.await() }
        }

    @Test
    fun cancellingTheLoaderScopeEndsItsDownloads() =
        runTest {
            val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
            val strategy = SharedDownloadStrategy(scope)
            val download = FakeDownload(size = 1024)
            val request = async { runCatching { strategy.apply("img", download.block) } }
            runCurrent()
            scope.cancel()
            runCurrent()

            assertTrue(request.await().isFailure)
            assertEquals(1, download.cancelled)
        }
}
