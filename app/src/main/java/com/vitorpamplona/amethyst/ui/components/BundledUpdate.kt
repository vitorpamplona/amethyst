package com.vitorpamplona.amethyst.ui.components

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * This class is designed to have a waiting time between two calls of invalidate
 */
class BundledUpdate(
    val delay: Long,
    val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    val onUpdate: () -> Unit
) {
    private var onlyOneInBlock = AtomicBoolean()
    private var invalidatesAgain = false

    fun invalidate(ignoreIfDoing: Boolean = false) {
        if (onlyOneInBlock.getAndSet(true)) {
            if (!ignoreIfDoing) {
                invalidatesAgain = true
            }
            return
        }

        val scope = CoroutineScope(Job() + dispatcher)
        scope.launch {
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

/**
 * This class is designed to have a waiting time between two calls of invalidate
 */
class BundledInsert<T>(
    val delay: Long,
    val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private var onlyOneInBlock = AtomicBoolean()
    private var queue = LinkedBlockingQueue<T>()

    fun invalidateList(newObject: T, onUpdate: suspend (Set<T>) -> Unit) {
        queue.put(newObject)
        if (onlyOneInBlock.getAndSet(true)) {
            return
        }

        val scope = CoroutineScope(Job() + dispatcher)
        scope.launch(Dispatchers.IO) {
            try {
                val mySet = mutableSetOf<T>()
                queue.drainTo(mySet)
                onUpdate(mySet)

                delay(delay)

                val mySet2 = mutableSetOf<T>()
                queue.drainTo(mySet2)
                onUpdate(mySet2)
            } finally {
                withContext(NonCancellable) {
                    onlyOneInBlock.set(false)
                }
            }
        }
    }
}
