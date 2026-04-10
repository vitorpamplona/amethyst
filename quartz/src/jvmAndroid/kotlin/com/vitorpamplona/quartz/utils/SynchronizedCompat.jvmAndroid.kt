package com.vitorpamplona.quartz.utils

actual inline fun <R> kmpSynchronized(lock: Any, block: () -> R): R = synchronized(lock, block)
