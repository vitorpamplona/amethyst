/**
 * Copyright (c) 2024 Vitor Pamplona
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
import kotlinx.coroutines.CoroutineDispatcher
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
    val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    val scope = CoroutineScope(dispatcher + SupervisorJob())

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

    fun cancel() {
        scope.cancel()
    }
}

/** This class is designed to have a waiting time between two calls of invalidate */
@Stable
class BundledInsert<T>(
    val delay: Long,
    val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    val scope = CoroutineScope(dispatcher + SupervisorJob())

    private var onlyOneInBlock = AtomicBoolean()
    private var queue = LinkedBlockingQueue<T>()

    fun invalidateList(
        newObject: T,
        onUpdate: suspend (Set<T>) -> Unit,
    ) {
        checkNotInMainThread()

        queue.put(newObject)
        if (onlyOneInBlock.getAndSet(true)) {
            return
        }

        scope.launch(dispatcher) {
            try {
                val mySet = mutableSetOf<T>()
                queue.drainTo(mySet)
                if (mySet.isNotEmpty()) {
                    onUpdate(mySet)
                }

                delay(delay)

                val mySet2 = mutableSetOf<T>()
                queue.drainTo(mySet2)
                if (mySet2.isNotEmpty()) {
                    onUpdate(mySet2)
                }
            } finally {
                withContext(NonCancellable) { onlyOneInBlock.set(false) }
            }
        }
    }

    fun cancel() {
        scope.cancel()
    }
}
