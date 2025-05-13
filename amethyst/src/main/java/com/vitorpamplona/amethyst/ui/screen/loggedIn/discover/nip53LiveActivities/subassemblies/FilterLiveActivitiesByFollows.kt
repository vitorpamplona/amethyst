/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip53LiveActivities.subassemblies

import com.vitorpamplona.ammolite.relays.FeedType
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.EOSETime
import com.vitorpamplona.ammolite.relays.filters.SinceAuthorPerRelayFilter
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent

fun filterLiveActivitiesByFollows(
    follows: Map<String, List<HexKey>>?,
    followKeys: Set<String>?,
    since: Map<String, EOSETime>?,
): List<TypedFilter>? {
    if (follows != null && follows.isEmpty()) return null

    return listOfNotNull(
        TypedFilter(
            types = if (follows == null) setOf(FeedType.GLOBAL) else setOf(FeedType.FOLLOWS),
            filter =
                SinceAuthorPerRelayFilter(
                    authors = follows,
                    kinds =
                        listOf(
                            LiveActivitiesChatMessageEvent.KIND,
                            LiveActivitiesEvent.KIND,
                        ),
                    limit = 300,
                    since = since,
                ),
        ),
        followKeys?.let {
            TypedFilter(
                types = setOf(FeedType.FOLLOWS),
                filter =
                    SincePerRelayFilter(
                        tags = mapOf("p" to it.toList()),
                        kinds = listOf(LiveActivitiesEvent.KIND),
                        limit = 100,
                        since = since,
                    ),
            )
        },
    )
}
