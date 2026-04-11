/*
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
package com.vitorpamplona.amethyst.commons.concurrency

/**
 * Creates a thread-safe [MutableMap].
 * On JVM/Android, backed by [java.util.concurrent.ConcurrentHashMap].
 */
expect fun <K, V> concurrentMutableMapOf(): MutableMap<K, V>

/**
 * Creates a thread-safe [MutableSet] backed by a concurrent map.
 * On JVM/Android, backed by [java.util.concurrent.ConcurrentHashMap.newKeySet].
 */
expect fun <T> concurrentMutableSetOf(): MutableSet<T>

/**
 * Creates a thread-safe sorted [MutableSet].
 * On JVM/Android, backed by [java.util.concurrent.ConcurrentSkipListSet].
 */
expect fun <T> concurrentSortedSetOf(comparator: Comparator<T>): MutableSet<T>

/**
 * Creates a thread-safe [MutableSet] backed by a synchronized wrapper.
 * On JVM/Android, backed by [java.util.Collections.synchronizedSet].
 */
expect fun <T> synchronizedMutableSetOf(): MutableSet<T>

/**
 * Creates a thread-safe [MutableSet] backed by a synchronized wrapper,
 * initialized with the given elements.
 * On JVM/Android, backed by [java.util.Collections.synchronizedSet].
 */
expect fun <T> synchronizedMutableSetOf(elements: MutableSet<T>): MutableSet<T>
