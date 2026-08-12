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
package com.vitorpamplona.quartz.utils.concurrent

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

// Linux/JVM-less target: Kotlin/Native's stdlib ships no parking lock and there is
// no Foundation here, so this keeps a test-and-test-and-set spin. Correct but not
// scalable. Acceptable ONLY because linuxX64 is a build/CI target for quartz, not a
// host for the many-relay client workload whose contention motivated the parking
// actuals on jvmAndroid and Apple. If that ever changes, swap in a pthread mutex.
@OptIn(ExperimentalAtomicApi::class)
actual class PlatformLock {
    private val held = AtomicBoolean(false)

    actual fun lock() {
        while (held.exchange(true)) {
            while (held.load()) { }
        }
    }

    actual fun unlock() {
        held.store(false)
    }
}
