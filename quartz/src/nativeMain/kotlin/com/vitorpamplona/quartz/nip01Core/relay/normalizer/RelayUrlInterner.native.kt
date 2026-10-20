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
package com.vitorpamplona.quartz.nip01Core.relay.normalizer

/**
 * Native actual: passthrough, like [com.vitorpamplona.quartz.nip01Core.cache.interning.EventInterner].
 * Kotlin/Native has weak references but no built-in concurrent map; until the iOS port wants the
 * savings, native keeps the parse memo's dedup only.
 */
actual class RelayUrlInterner {
    internal actual fun intern(relay: NormalizedRelayUrl): NormalizedRelayUrl = relay

    actual fun get(url: String): NormalizedRelayUrl? = null

    actual fun size(): Int = 0

    internal actual fun clear() {}

    actual companion object {
        actual val Default: RelayUrlInterner = RelayUrlInterner()
    }
}
