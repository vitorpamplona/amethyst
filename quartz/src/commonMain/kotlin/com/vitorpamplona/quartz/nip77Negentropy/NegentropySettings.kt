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
package com.vitorpamplona.quartz.nip77Negentropy

/**
 * Server-side NIP-77 tuning. Defaults track strfry
 * (`hoytech/strfry`) so a Quartz-based relay accepts the same
 * workload shape and exchanges the same NEG-MSG round-trip size.
 *
 * @param frameSizeLimit Max bytes per NEG-MSG response payload
 *   (raw, before hex). 500_000 matches strfry's hard-coded
 *   `Negentropy ne(storage, 500'000)` in `RelayNegentropy.cpp`.
 *   The `kmp-negentropy` library enforces `>= 4096` (or `0` for
 *   unlimited).
 * @param maxSyncEvents Hard cap on the snapshot size for a single
 *   NEG-OPEN. Mirrors strfry's `relay__negentropy__maxSyncEvents`.
 *   Overflow returns NEG-ERR `"blocked: too many query results"`.
 * @param maxSessionsPerConnection Cap on concurrent NEG sessions
 *   held by one connection. strfry shares 200 with REQ subs; we
 *   count NEG independently. Overflow sends NOTICE
 *   `"too many concurrent NEG requests"`.
 */
data class NegentropySettings(
    val frameSizeLimit: Long = NegentropyServerSession.DEFAULT_FRAME_SIZE_LIMIT,
    val maxSyncEvents: Int = 1_000_000,
    val maxSessionsPerConnection: Int = 200,
) {
    companion object {
        /** strfry-equivalent defaults. */
        val Default = NegentropySettings()
    }
}
