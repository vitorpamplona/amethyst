package com.vitorpamplona.quartz.utils

/**
 * On Kotlin/Native with the new memory model, shared mutable state is allowed.
 * This provides a no-op synchronized wrapper since K/N coroutines dispatch
 * on a single thread by default. For truly concurrent access, use Mutex.
 */
actual inline fun <R> kmpSynchronized(lock: Any, block: () -> R): R = block()
