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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.Continuation
import kotlin.coroutines.cancellation.CancellationException
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

        async {
            jobs.forEach {
                it.cancel("Timeout")
            }
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
suspend fun <T, K> collectSuccessfulOperationsReturning(
    items: List<T>,
    runRequestFor: (T, (K) -> Unit) -> Unit,
): List<K> {
    val output: MutableList<K> = mutableListOf()

    if (items.isEmpty()) {
        return output
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

    return output
}

/**
 * Executes multiple suspending functions concurrently using `async` and attempts to wait for all of them
 * to complete within a default 15-second timeout.
 *
 * If the timeout is reached, it returns the results of all tasks that successfully completed by then
 * and cancels any tasks that are still running. Tasks that completed with an exception are not
 * included in the returned list.
 *
 * @param tasks A list of suspending functions, each returning a value of type [T].
 * @return A list containing the results of tasks that successfully completed within the timeout.
 */
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun <T, K> mapNotNullAsync(
    items: List<T>,
    timeoutMillis: Long = 30000,
    runRequestFor: suspend (T) -> K?,
): List<K> {
    if (items.isEmpty()) {
        return emptyList()
    }

    return coroutineScope {
        // Launch all tasks asynchronously and get their Deferred handles.
        val jobs =
            items.map { item ->
                async {
                    runRequestFor(item)
                }
            }

        // Use withTimeout to impose a 15-second limit on waiting for all deferreds.
        // If all tasks complete within 15 seconds, awaitAll() will return their results,
        // and this block will return those results.
        withTimeoutOrNull(timeoutMillis) {
            jobs.joinAll()
        }

        async {
            jobs.forEach {
                it.cancel("Timeout")
            }
        }

        jobs.mapNotNull {
            if (it.isCompleted) {
                it.getCompleted()
            } else {
                null
            }
        }
    }
}

/**
 * Executes a mapping function asynchronously on each input in the list.
 * Returns true as soon as the first mapping returns true, cancelling all other ongoing operations.
 *
 * @param inputs A list of input objects to process.
 * @param mappingFunction A suspend function that takes an input object and returns a Boolean.
 * @return True if any mapping function returns true, false otherwise.
 */
suspend fun <T> anyAsync(
    inputs: List<T>,
    timeoutMillis: Long = 30000,
    mappingFunction: suspend (T) -> Boolean,
): Boolean =
    coroutineScope {
        // Create a list to hold all our deferred results
        val deferredResults =
            inputs.map { input ->
                async {
                    // Each async block will execute the mapping function.
                    // If this coroutine gets cancelled, CancellationException will be thrown,
                    // and we'll catch it to ensure it doesn't propagate further.
                    try {
                        mappingFunction(input)
                    } catch (e: CancellationException) {
                        // When cancelled, we treat it as if it didn't return true
                        false
                    }
                }
            }

        // Use select to wait for the first deferred to complete with 'true'
        val foundTrue =
            withTimeoutOrNull(timeoutMillis) {
                select {
                    deferredResults.forEach { deferred ->
                        // For each deferred, if it completes and its result is 'true',
                        // this branch of the select expression will be chosen.
                        deferred.onAwait { result ->
                            if (result) {
                                true // Return true from the select expression
                            } else {
                                // If a deferred completes with false, we don't want to
                                // immediately end the select, so we return false, which
                                // lets select continue waiting for other branches.
                                false
                            }
                        }
                    }
                }
            }

        // Once select returns (either with true or after all deferreds complete/are cancelled),
        // cancel any remaining ongoing operations.
        // If foundTrue is true, all other deferreds are implicitly cancelled by the select winning.
        // If foundTrue is false, it means all completed with false or were cancelled.
        deferredResults.forEach { it.cancel() } // Ensure all are cancelled.

        return@coroutineScope foundTrue == true
    }
