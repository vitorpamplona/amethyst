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
package com.vitorpamplona.amethyst.commons.napplet

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Stable identity of a napplet for the permission ledger.
 *
 * Keyed by the **addressable coordinate** — the manifest author's pubkey plus the
 * `d` identifier — and deliberately **not** the bundle's content hash, so a user's
 * grants survive routine code updates. The current [aggregateHash] is carried
 * alongside (the NIP-5A aggregate over the manifest's `path` set, see
 * `NappletManifest.computeAggregateHash()`) so a user who chose "ask again when the
 * code changes" can be re-prompted when the bundle actually changes.
 *
 * [identifier] is empty for a root (kind 15129) napplet and the `d` value for a
 * named (kind 35129) one.
 */
@Immutable
data class NappletIdentity(
    val authorPubKey: HexKey,
    val identifier: String,
    val aggregateHash: HexKey? = null,
) {
    /** The ledger key: coordinate only, never the [aggregateHash], so grants survive updates. */
    val coordinate: String = "$authorPubKey:$identifier"
}
