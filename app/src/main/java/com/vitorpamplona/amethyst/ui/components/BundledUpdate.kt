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
