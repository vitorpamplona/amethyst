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
package androidx.work

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture

/**
 * JVM stand-in for androidx.work.CoroutineWorker.
 *
 * Kotlin rather than Java because `doWork` suspends and Java cannot declare
 * that. [WorkManager] never sees the suspension: [startWork] hands it the same
 * future every [ListenableWorker] returns, which is exactly how the real
 * library bridges the two worlds.
 *
 * Cancelling the work cancels the coroutine, so a worker that is stopped
 * mid-publish unwinds through its own `CancellationException` handling instead
 * of running on against a torn-down app.
 */
abstract class CoroutineWorker(
    appContext: Context,
    params: WorkerParameters,
) : ListenableWorker(appContext, params) {
    /** Override to run the work somewhere other than the default dispatcher. */
    open val coroutineContext: CoroutineDispatcher get() = Dispatchers.Default

    private val job = SupervisorJob()

    abstract suspend fun doWork(): Result

    final override fun startWork(): CompletableFuture<Result> {
        val future = CompletableFuture<Result>()
        CoroutineScope(coroutineContext + job).launch {
            try {
                future.complete(doWork())
            } catch (t: Throwable) {
                future.completeExceptionally(t)
            }
        }
        future.whenComplete { _, _ -> job.cancel() }
        return future
    }

    override fun onStopped() {
        job.cancel()
    }
}
