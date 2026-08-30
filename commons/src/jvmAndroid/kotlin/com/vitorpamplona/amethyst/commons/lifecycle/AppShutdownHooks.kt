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
package com.vitorpamplona.amethyst.commons.lifecycle

import com.vitorpamplona.quartz.utils.Log
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Teardown that a subsystem registers for itself.
 *
 * The shared shutdown path used to name each subsystem it had to stop, which
 * meant naming platform-only types from code that compiles for every platform —
 * the same coupling the platform seams exist to remove, arriving through the
 * back door.
 *
 * A hook that throws is logged and the rest still run: shutdown is the worst
 * place to stop halfway, because whatever comes after it is what releases the
 * resources.
 */
object AppShutdownHooks {
    private val hooks = CopyOnWriteArrayList<Pair<String, () -> Unit>>()

    fun register(
        name: String,
        hook: () -> Unit,
    ) {
        hooks.add(name to hook)
    }

    /** Runs every hook, most recently registered first, and clears them. */
    fun runAll() {
        val pending = hooks.toList().asReversed()
        hooks.clear()
        pending.forEach { (name, hook) ->
            runCatching(hook).onFailure { Log.w("AppShutdownHooks", "$name failed to shut down", it) }
        }
    }
}
