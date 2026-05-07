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
package com.vitorpamplona.quic.connection

/**
 * Opt-in writer-side debug logging. Toggled by external code (the
 * interop endpoint sets it from the QUIC_INTEROP_DEBUG env var at
 * startup). Off by default — production must be silent.
 *
 * Cost when off: a single volatile read in the writer hot path,
 * negligible against the encode + AEAD seal cost. Worth keeping
 * inert in shipped code so the next interop investigation can flip
 * it on without code changes.
 */
@Volatile
var writerDebugEnabled: Boolean = false

/**
 * Build identifier injected into the boot log so we can verify the
 * deployed image actually has the latest debug code (i.e. that the
 * docker layer cache didn't serve a stale jar). Bump when adding new
 * trace lines to make them traceable from the wire run.
 */
const val WRITER_DEBUG_BUILD_ID: String = "2026-05-07-parallel-branch-v1"
