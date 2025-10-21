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
package com.vitorpamplona.ammolite.relays

import androidx.compose.runtime.Stable
import com.vitorpamplona.ammolite.service.checkNotInMainThread
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/** This class is designed to have a waiting time between two calls of invalidate */
@Stable
class BundledUpdate(
    val delay: Long,
    val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    // Exists to avoid exceptions stopping the coroutine
    val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            Log.e("BundledUpdate", "Caught exception: ${throwable.message}", throwable)
        }

    val scope = CoroutineScope(dispatcher + SupervisorJob() + exceptionHandler)

    val bundledUpdate = BasicBundledUpdate(delay, dispatcher, scope)

    fun invalidate(
        ignoreIfDoing: Boolean = false,
        onUpdate: suspend () -> Unit,
    ) = bundledUpdate.invalidate(ignoreIfDoing, onUpdate)

    fun cancel() {
        scope.cancel()
    }
}

/** This class is designed to have a waiting time between two calls of invalidate */
@Stable
class BasicBundledUpdate(
    val delay: Long,
    val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    val scope: CoroutineScope,
) {
    private var onlyOneInBlock = AtomicBoolean()
    private var invalidatesAgain = false

    fun invalidate(
        ignoreIfDoing: Boolean = false,
        onUpdate: suspend () -> Unit,
    ) {
        if (onlyOneInBlock.getAndSet(true)) {
            if (!ignoreIfDoing) {
                invalidatesAgain = true
            }
            return
        }

        scope.launch(dispatcher) {
            try {
                onUpdate()
                delay(delay)
                if (invalidatesAgain) {
                    onUpdate()
                }
            } finally {
                withContext(NonCancellable) {
                    invalidatesAgain = false
                    onlyOneInBlock.set(false)
                }
            }
        }
    }
}

/** This class is designed to have a waiting time between two calls of invalidate */
@Stable
class BundledInsert<T>(
    val delay: Long,
    val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    // Exists to avoid exceptions stopping the coroutine
    val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            Log.e("BundledInsert", "Caught exception: ${throwable.message}", throwable)
        }

    val scope = CoroutineScope(dispatcher + SupervisorJob() + exceptionHandler)

    val bundledInsert = BasicBundledInsert<T>(delay, dispatcher, scope)

    fun invalidateList(
        newObject: T,
        onUpdate: suspend (Set<T>) -> Unit,
    ) = bundledInsert.invalidateList(newObject, onUpdate)

    fun cancel() {
        scope.cancel()
    }
}

/** This class is designed to have a waiting time between two calls of invalidate */
@Stable
class BasicBundledInsert<T>(
    val delay: Long,
    val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    val scope: CoroutineScope,
) {
    private var onlyOneInBlock = AtomicBoolean()
    private var queue = LinkedBlockingQueue<T>()

    fun invalidateList(
        newObject: T,
        onUpdate: suspend (Set<T>) -> Unit,
    ) {
        checkNotInMainThread()

        queue.put(newObject)

        if (onlyOneInBlock.getAndSet(true)) {
            // if it was true already, returns.
            return
        }

        scope.launch(dispatcher) {
            try {
                while (true) {
                    val batch = mutableSetOf<T>()
                    queue.drainTo(batch)
                    if (batch.isNotEmpty()) {
                        onUpdate(batch)
                    } else {
                        break
                    }

                    delay(delay)
                }
            } finally {
                withContext(NonCancellable) {
                    onlyOneInBlock.set(false)
                }
            }
        }
    }
}
