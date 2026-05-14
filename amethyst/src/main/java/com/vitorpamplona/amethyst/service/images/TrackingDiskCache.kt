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
package com.vitorpamplona.amethyst.service.images

import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache

/**
 * `DiskCache` wrapper that records every key access into [keyLog].
 *
 * Coil's built-in `DiskLruCache` evicts inline on every `commit()` once
 * `size` exceeds `maxSize`. That inline eviction is what stalls feed-scroll
 * IO on a saturated cache. By recording access timestamps here we give
 * [CoilDiskTrimmer] enough information to run an external eviction pass on a
 * background coroutine — which keeps `size < maxSize` and turns the inline
 * trim into a no-op during normal use.
 *
 * Delegates every method to the real Coil cache via Kotlin's interface
 * delegation. Recording happens before delegation so a key seen via `openEditor`
 * always shows up in [keyLog] regardless of whether the editor is later
 * aborted — that's fine: the trimmer is robust against `remove(key)` of an
 * already-evicted key.
 */
@OptIn(ExperimentalCoilApi::class)
class TrackingDiskCache(
    private val delegate: DiskCache,
    private val keyLog: KeyAccessLog,
) : DiskCache by delegate {
    override fun openEditor(key: String): DiskCache.Editor? {
        keyLog.recordAccess(key)
        return delegate.openEditor(key)
    }

    override fun openSnapshot(key: String): DiskCache.Snapshot? {
        val snap = delegate.openSnapshot(key)
        if (snap != null) keyLog.recordAccess(key)
        return snap
    }

    override fun remove(key: String): Boolean {
        val removed = delegate.remove(key)
        if (removed) keyLog.forget(key)
        return removed
    }

    override fun clear() {
        delegate.clear()
        keyLog.forgetAll()
    }
}
