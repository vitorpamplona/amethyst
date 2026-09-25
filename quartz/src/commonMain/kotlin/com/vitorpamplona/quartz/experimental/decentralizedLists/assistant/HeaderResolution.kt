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
package com.vitorpamplona.quartz.experimental.decentralizedLists.assistant

import com.vitorpamplona.quartz.experimental.decentralizedLists.header.AddressableListHeaderEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray

/**
 * Tapestry Assistant Designation, dual-author precedence: which header governs a user's
 * concept `slug` when both the user and their assistant may have written one.
 *
 * 1. the user's own `39998:<user>:<slug>`, if it exists;
 * 2. else their assistant's `39998:<assistant>:<slug>`, the assistant found through the user's
 *    kind 10040 `39998:dlist-header` entry;
 * 3. else none.
 *
 * Never by recency: a newer assistant header does not shadow a personal one. A user who wants
 * the assistant's to govern says so with `["b", <assistant header>, "inherit"]` on their own.
 */
object HeaderResolution {
    /** The candidate addresses, in precedence order. [treasureMap] is the user's 10040 tags. */
    fun candidates(
        user: HexKey,
        slug: String,
        treasureMap: TagArray?,
    ): List<Address> {
        val personal = Address(AddressableListHeaderEvent.KIND, user, slug)
        val assistant = treasureMap?.dListAssistant()?.assistant ?: return listOf(personal)
        if (assistant == user) return listOf(personal)
        return listOf(personal, Address(AddressableListHeaderEvent.KIND, assistant, slug))
    }

    /**
     * Picks the governing header among the fetched [headers]: the first candidate that has one.
     * Headers signed by anyone else, or for another slug, are ignored.
     */
    fun governing(
        user: HexKey,
        slug: String,
        treasureMap: TagArray?,
        headers: Collection<AddressableListHeaderEvent>,
    ): AddressableListHeaderEvent? =
        candidates(user, slug, treasureMap).firstNotNullOfOrNull { candidate ->
            // several versions of one addressable header may be at hand: the newest is the header
            headers.filter { it.pubKey == candidate.pubKeyHex && it.dTag() == candidate.dTag }.maxByOrNull { it.createdAt }
        }
}
