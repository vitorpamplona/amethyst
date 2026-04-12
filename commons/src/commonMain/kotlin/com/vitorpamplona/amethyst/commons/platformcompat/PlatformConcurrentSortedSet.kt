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
package com.vitorpamplona.amethyst.commons.platformcompat

/**
 * Creates a thread-safe sorted set backed by a platform-specific implementation.
 * On JVM/Android this uses ConcurrentSkipListSet.
 */
expect fun <T> platformConcurrentSortedSet(comparator: Comparator<T>): MutableSet<T>

/**
 * Creates a thread-safe sorted set from an existing sorted set.
 */
expect fun <T> platformConcurrentSortedSet(source: Set<T>): MutableSet<T>

/**
 * Wraps a mutable set in a synchronized wrapper.
 * On JVM/Android this uses Collections.synchronizedSet.
 */
expect fun <T> platformSynchronizedSet(set: MutableSet<T>): MutableSet<T>
