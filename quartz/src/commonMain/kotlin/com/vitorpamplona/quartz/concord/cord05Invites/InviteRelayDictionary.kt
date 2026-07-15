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
package com.vitorpamplona.quartz.concord.cord05Invites

/**
 * The invite-link relay dictionary (CORD-05 §Relay Dictionary), version 4,
 * pinned to the Concord v2 reference client. Referencing a relay by its dictionary
 * id keeps invite links compact; the stock set is selected by a single flag so the
 * common invite carries zero relay bytes.
 *
 * Dictionary ids run 1–254. Id 0 is reserved as the "wss:// literal host" marker
 * and 255 as the "full URL" marker in the fragment encoding (see [ConcordInviteLink]).
 */
object InviteRelayDictionary {
    /** The stock relay set carried by flag 0x01 (the four v4 primaries). */
    val STOCK: List<String> =
        listOf(
            "wss://jskitty.com/nostr",
            "wss://asia.vectorapp.io/nostr",
            "wss://relay.ditto.pub",
            "wss://relay.dreamith.to",
        )

    /** id → relay url, for the ids that fit in a single dictionary byte (1–254). */
    val BY_ID: Map<Int, String> =
        mapOf(
            1 to "wss://jskitty.com/nostr",
            2 to "wss://asia.vectorapp.io/nostr",
            3 to "wss://relay.ditto.pub",
            4 to "wss://relay.dreamith.to",
        )

    private val ID_BY_URL: Map<String, Int> = BY_ID.entries.associate { (id, url) -> url to id }

    fun idOf(url: String): Int? = ID_BY_URL[url]

    fun urlOf(id: Int): String? = BY_ID[id]
}
