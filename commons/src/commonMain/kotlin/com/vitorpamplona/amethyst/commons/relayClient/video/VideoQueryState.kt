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
package com.vitorpamplona.amethyst.commons.relayClient.video

import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.relayClient.AccountScopedQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * One screen's picture/video feed subscription. Several screens may show the feed at once,
 * so each gets its own key.
 *
 * @param listName the top-nav selection driving the feed (follows, a list, a hashtag, ...).
 * @param followsPerRelay [listName] resolved through the outbox model into per-relay author/tag sets.
 * @param lastNoteCreatedAtWhenFullyLoaded created_at of the oldest note once the feed filled;
 *   bounds `since` so the REQ stops re-pulling the page already on screen.
 * @param scope where the flow watchers that re-issue the REQ on changes run.
 */
class VideoQueryState(
    override val account: IAccount,
    val listName: StateFlow<TopFilter>,
    val followsPerRelay: StateFlow<IFeedTopNavPerRelayFilterSet>,
    val lastNoteCreatedAtWhenFullyLoaded: StateFlow<Long?>,
    val scope: CoroutineScope,
) : AccountScopedQuery
