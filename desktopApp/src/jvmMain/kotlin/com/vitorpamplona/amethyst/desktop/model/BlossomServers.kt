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
package com.vitorpamplona.amethyst.desktop.model

import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent

/** Fallback media server used when the account has published no kind-10063 list yet. */
const val DEFAULT_BLOSSOM_SERVER = "https://blossom.primal.net"

/**
 * The account's Blossom media servers (NIP-B7 / kind 10063), read straight from
 * the cache. This is the per-account source of truth — the same event the
 * account-config subscription loads and the mobile app uses — so upload sites
 * read it here instead of a process-global preference.
 */
fun ICacheProvider.blossomServers(pubKeyHex: HexKey): List<String> =
    (getOrCreateAddressableNote(BlossomServersEvent.createAddress(pubKeyHex)).event as? BlossomServersEvent)
        ?.servers()
        .orEmpty()

/** First declared Blossom server for [pubKeyHex], or [DEFAULT_BLOSSOM_SERVER] when none is set. */
fun ICacheProvider.preferredBlossomServer(pubKeyHex: HexKey): String = blossomServers(pubKeyHex).firstOrNull() ?: DEFAULT_BLOSSOM_SERVER
