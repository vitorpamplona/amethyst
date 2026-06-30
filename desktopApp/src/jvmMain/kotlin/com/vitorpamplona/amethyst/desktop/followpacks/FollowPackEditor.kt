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
package com.vitorpamplona.amethyst.desktop.followpacks

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent

/**
 * Lightweight helpers for follow-pack viewing actions.
 *
 * v1 scope: view + follow + share. No create/edit/delete.
 */
object FollowPackEditor {
    /** Encodes the pack as a `nostr:naddr1…` URI for sharing via clipboard. */
    fun toShareUri(pack: FollowListEvent): String {
        val naddr =
            com.vitorpamplona.quartz.nip19Bech32.entities
                .NAddress
                .create(
                    kind = FollowListEvent.KIND,
                    pubKeyHex = pack.pubKey,
                    dTag = pack.dTag(),
                    relays = emptyList(),
                )
        return "nostr:$naddr"
    }

    /** Address coordinate (e.g. "39089:<pubkey>:<dTag>") — stable identity. */
    fun aTag(pack: FollowListEvent): String = Address.assemble(FollowListEvent.KIND, pack.pubKey, pack.dTag())
}
