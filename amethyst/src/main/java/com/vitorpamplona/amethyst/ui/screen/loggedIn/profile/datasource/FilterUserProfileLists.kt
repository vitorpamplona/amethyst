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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.datasource

import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent
import com.vitorpamplona.quartz.nip51Lists.hashtagList.HashtagListEvent
import com.vitorpamplona.quartz.nip51Lists.peopleList.PeopleListEvent
import com.vitorpamplona.quartz.nip89AppHandlers.recommendation.AppRecommendationEvent

val UserProfileListKinds =
    listOf(
        BookmarkListEvent.KIND,
        PeopleListEvent.KIND,
        FollowListEvent.KIND,
        HashtagListEvent.KIND,
        AppRecommendationEvent.KIND,
    )

fun filterUserProfileLists(
    users: Map<NormalizedRelayUrl, Set<String>>,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> =
    users.map {
        RelayBasedFilter(
            relay = it.key,
            filter =
                Filter(
                    kinds = UserProfileListKinds,
                    authors = it.value.sorted(),
                    limit = 100,
                    since = since?.get(it.key)?.time,
                ),
        )
    }
