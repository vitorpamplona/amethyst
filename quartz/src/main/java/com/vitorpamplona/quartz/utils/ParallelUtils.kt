/**
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

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

/**
 * Launches an async coroutine for each item, runs the
 * function and waits for everybody to finsih
 */
suspend fun <T> launchAndWaitAll(
    items: List<T>,
    asyncFunc: suspend (T) -> Unit,
) {
    coroutineScope {
        val jobs =
            items.map { next ->
                async {
                    asyncFunc(next)
                }
            }

        // runs in parallel to avoid overcrowding Amber.
        withTimeoutOrNull(15000) {
            jobs.joinAll()
        }
    }
}

/**
 * Runs the function and waits for 10 seconds for any result.
 */
suspend inline fun <T> tryAndWait(
    timeoutMillis: Long = 10000,
    crossinline asyncFunc: (Continuation<T>) -> Unit,
): T? =
    withTimeoutOrNull(timeoutMillis) {
        suspendCancellableCoroutine { continuation ->
            asyncFunc(continuation)
        }
    }

/**
 * Runs an async coroutine for each one of the items,
 * runs the request for that item,
 * and gathers all the results in the output map.
 */
suspend fun <T, K> collectSuccessfulOperations(
    items: List<T>,
    runRequestFor: (T, (K) -> Unit) -> Unit,
    output: MutableList<K> = mutableListOf(),
    onReady: suspend (List<K>) -> Unit,
) {
    if (items.isEmpty()) {
        onReady(output)
        return
    }

    launchAndWaitAll(items) {
        val result =
            tryAndWait { continuation ->
                runRequestFor(it) { result: K -> continuation.resume(result) }
            }

        if (result != null) {
            output.add(result)
        }
    }

    onReady(output)
}
