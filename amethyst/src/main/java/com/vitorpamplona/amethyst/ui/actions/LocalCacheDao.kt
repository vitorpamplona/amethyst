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
package com.vitorpamplona.amethyst.ui.actions

import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * A [Dao] backed directly by [LocalCache], for resolving `@`/`nostr:` mentions with
 * [NewMessageTagger] from paths that have no `AccountViewModel` to hand — the model-layer
 * send helpers ([com.vitorpamplona.amethyst.model.Account]) and the background notification
 * quick-reply receiver. Mirrors `AccountViewModel`'s own Dao delegation, which is just this.
 *
 * Resolving an npub/nprofile to its pubkey needs no populated cache (the key is in the
 * bech32 itself), so this works even in a cold `:napplet`-free receiver process where
 * LocalCache hasn't been rehydrated; only note-author lookups degrade there, which is fine.
 */
object LocalCacheDao : Dao {
    override suspend fun getOrCreateUser(hex: HexKey): User = LocalCache.getOrCreateUser(hex)

    override suspend fun getOrCreateNote(hex: HexKey): Note = LocalCache.getOrCreateNote(hex)

    override fun getOrCreateAddressableNote(address: Address): AddressableNote = LocalCache.getOrCreateAddressableNote(address)
}
