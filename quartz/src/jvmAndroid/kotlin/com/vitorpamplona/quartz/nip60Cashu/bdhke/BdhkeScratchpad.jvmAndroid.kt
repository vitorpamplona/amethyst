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
package com.vitorpamplona.quartz.nip60Cashu.bdhke

/**
 * JVM/Android actual: one [BdhkeScratchpad] per thread, allocated
 * lazily on first access and reused for the lifetime of the thread.
 *
 * The hot Cashu paths (NUT-09 restore, swap output unblinding,
 * Carol verification) all run on a single Dispatchers.IO or
 * Dispatchers.Default thread per coroutine, so a thread-local pool
 * matches their access pattern perfectly — every call after the
 * first reuses the same holders, zero per-call allocation.
 */
private val threadLocal = ThreadLocal.withInitial { BdhkeScratchpad() }

internal actual fun bdhkeScratchpad(): BdhkeScratchpad = threadLocal.get()
