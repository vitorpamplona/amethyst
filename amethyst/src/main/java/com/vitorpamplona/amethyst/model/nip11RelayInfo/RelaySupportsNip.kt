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
package com.vitorpamplona.amethyst.model.nip11RelayInfo

import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Whether [relay]'s cached NIP-11 document advertises support for [nip] (as a decimal string, e.g.
 * "29"). Reads only the in-memory cache — it never blocks on a network fetch — so it returns false
 * for a relay whose NIP-11 hasn't been loaded yet. Callers that need the answer to become true must
 * warm the document first (e.g. `loadRelayInfo`), then re-evaluate once it resolves.
 */
fun relayAdvertisesNip(
    relay: NormalizedRelayUrl,
    nip: String,
): Boolean =
    Amethyst.instance.nip11Cache
        .getFromCache(relay)
        .supported_nips
        ?.any { it == nip } == true

/** NIP-29 (relay-based groups): the relay must run it for its groups to be real. */
fun relayAdvertisesNip29(relay: NormalizedRelayUrl): Boolean = relayAdvertisesNip(relay, "29")
