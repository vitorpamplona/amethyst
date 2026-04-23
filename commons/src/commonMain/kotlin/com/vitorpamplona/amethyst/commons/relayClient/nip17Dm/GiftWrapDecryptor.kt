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
package com.vitorpamplona.amethyst.commons.relayClient.nip17Dm

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent

/**
 * Fully decrypt a NIP-59 gift wrap down to the inner rumor.
 *
 * A gift wrap carries two encryption layers:
 *   kind:1059 [GiftWrapEvent] → kind:13 [SealedRumorEvent] → the rumor.
 *
 * [GiftWrapEvent.unwrapOrNull] peels only the outer layer. Every non-Android
 * consumer that looks at the rumor directly (the CLI's `dm list`, the desktop
 * chat receive path, the Marmot welcome ingest in commons) needs both peels.
 *
 * If the inner content isn't a [SealedRumorEvent] (malformed, or a future
 * NIP-59 variant that wraps a rumor directly), the outer unwrap result is
 * returned as-is so callers can still route on kind. Either unwrap returning
 * null propagates as null.
 */
suspend fun GiftWrapEvent.unwrapAndUnsealOrNull(signer: NostrSigner): Event? {
    val inner = unwrapOrNull(signer) ?: return null
    return if (inner is SealedRumorEvent) inner.unsealOrNull(signer) else inner
}
