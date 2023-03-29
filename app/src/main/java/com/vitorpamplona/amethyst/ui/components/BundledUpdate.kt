package com.vitorpamplona.amethyst.ui.components

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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

    fun invalidate() {
        if (onlyOneInBlock.getAndSet(true)) {
            invalidatesAgain = true
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
    private var atomicSet = AtomicReference<Set<T>>(setOf<T>())

    fun invalidateList(newObject: T, onUpdate: (Set<T>) -> Unit) {
        // atomicSet.updateAndGet() {
        //    it + newObject
        // }

        if (onlyOneInBlock.getAndSet(true)) {
            return
        }

        val scope = CoroutineScope(Job() + dispatcher)
        scope.launch {
            try {
                // onUpdate(atomicSet.getAndSet(emptySet()))
                onUpdate(emptySet())
                delay(delay)
                // onUpdate(atomicSet.getAndSet(emptySet()))
            } finally {
                withContext(NonCancellable) {
                    onlyOneInBlock.set(false)
                }
            }
        }
    }
}
