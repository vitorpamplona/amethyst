/**
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.datasource

import com.vitorpamplona.ammolite.relays.FeedType
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.EOSETime
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import kotlin.collections.toList

fun filterNip04DMs(
    group: Set<HexKey>?,
    user: HexKey?,
    since: Map<String, EOSETime>?,
): List<TypedFilter>? {
    if (group == null || group.isEmpty() || user == null || user.isEmpty()) return null

    return listOf(
        TypedFilter(
            types = setOf(FeedType.PRIVATE_DMS),
            filter =
                SincePerRelayFilter(
                    kinds = listOf(PrivateDmEvent.KIND),
                    authors = group.toList(),
                    tags = mapOf("p" to listOf(user)),
                    since = since,
                ),
        ),
        TypedFilter(
            types = setOf(FeedType.PRIVATE_DMS),
            filter =
                SincePerRelayFilter(
                    kinds = listOf(PrivateDmEvent.KIND),
                    authors = listOf(user),
                    tags = mapOf("p" to group.toList()),
                    since = since,
                ),
        ),
    )
}
