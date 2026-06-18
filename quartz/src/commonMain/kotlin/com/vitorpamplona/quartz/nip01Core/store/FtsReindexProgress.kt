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
package com.vitorpamplona.quartz.nip01Core.store

/**
 * Progress token returned by the resumable
 * [IEventStore.reindexFullTextSearch] overload.
 *
 * Treat [cursor] as opaque: persist it (it survives process death) and
 * hand it back to the next call to continue where the previous one
 * stopped. Each store encodes its own resume position into it.
 *
 * @property cursor where to resume from on the next call, or `null` once
 *   [done] is `true` (nothing left to process).
 * @property processedThisBatch how many events this call (re)indexed —
 *   useful to drive a progress indicator.
 * @property done `true` when the whole store has been visited; further
 *   calls are no-ops that keep returning `done = true`.
 */
data class FtsReindexProgress(
    val cursor: String?,
    val processedThisBatch: Int,
    val done: Boolean,
)
