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
package com.vitorpamplona.quartz.nip59Giftwrap

import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * A lightweight reference to the host event a [WrappedEvent] was extracted from — kept on the inner
 * event so callers can broadcast / delete / locate the outer wrap without holding the full event.
 *
 * [createdAt] is the host's own `created_at` (e.g. the kind:1059 gift-wrap timestamp, randomized per
 * NIP-59), carried here so a decrypted rumor self-describes its outer-wrap time. The history pager
 * cursors page gift wraps by that outer time, so the prune path uses it to realign the per-relay
 * download window when a wrapped message is pruned (the chatroom only keeps the inner rumor, whose
 * `created_at` is the real message time, not the wrap time).
 */
class HostStub(
    val id: HexKey,
    val pubKey: HexKey,
    val kind: Int,
    val createdAt: Long,
)
