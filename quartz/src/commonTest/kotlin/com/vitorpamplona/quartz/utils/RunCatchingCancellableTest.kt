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
package com.vitorpamplona.quartz.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RunCatchingCancellableTest {
    @Test
    fun returnsSuccess() =
        runTest {
            assertEquals(Result.success(42), runCatchingCancellable { 42 })
        }

    @Test
    fun returnsOrdinaryFailure() =
        runTest {
            val result = runCatchingCancellable { throw IllegalStateException("boom") }
            assertIs<IllegalStateException>(result.exceptionOrNull())
        }

    @Test
    fun returnsInnerTimeoutAsFailure() =
        runTest {
            val result = runCatchingCancellable { withTimeout(10) { awaitCancellation() } }
            assertIs<TimeoutCancellationException>(result.exceptionOrNull())
        }

    @Test
    fun rethrowsWhenCallerIsCancelled() =
        runTest {
            val started = CompletableDeferred<Unit>()
            var carriedOn = false
            var cancelled = false
            val job =
                launch {
                    try {
                        runCatchingCancellable {
                            started.complete(Unit)
                            awaitCancellation()
                        }
                        carriedOn = true
                    } catch (e: CancellationException) {
                        cancelled = true
                        throw e
                    }
                }
            started.await()
            job.cancel()
            job.join()

            assertTrue(cancelled)
            assertFalse(carriedOn)
        }
}
