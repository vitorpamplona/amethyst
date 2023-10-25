package com.vitorpamplona.amethyst.ui.components

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.service.checkNotInMainThread
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

/**
 * This class is designed to have a waiting time between two calls of invalidate
 */
@Stable
class BundledUpdate(
    val delay: Long,
    val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    val scope = CoroutineScope(dispatcher + SupervisorJob())

    private var onlyOneInBlock = AtomicBoolean()
    private var invalidatesAgain = false

    fun invalidate(ignoreIfDoing: Boolean = false, onUpdate: suspend () -> Unit) {
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

/**
 * This class is designed to have a waiting time between two calls of invalidate
 */
@Stable
class BundledInsert<T>(
    val delay: Long,
    val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    val scope = CoroutineScope(dispatcher + SupervisorJob())

    private var onlyOneInBlock = AtomicBoolean()
    private var queue = LinkedBlockingQueue<T>()

    fun invalidateList(newObject: T, onUpdate: suspend (Set<T>) -> Unit) {
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
                withContext(NonCancellable) {
                    onlyOneInBlock.set(false)
                }
            }
        }
    }

    fun cancel() {
        scope.cancel()
    }
}
