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
package com.vitorpamplona.amethyst.model.commons

import com.vitorpamplona.amethyst.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.global.GlobalTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.muted.MutedAuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.muted.MutedAuthorsByProxyTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.relay.RelayTopNavFilter
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.amethyst.commons.model.IFeedTopNavFilter as CommonsIFeedTopNavFilter

/**
 * Adapter that wraps an Android-specific IFeedTopNavFilter into the
 * commons IFeedTopNavFilter interface.
 */
class FeedTopNavFilterAdapter(
    private val delegate: IFeedTopNavFilter,
) : CommonsIFeedTopNavFilter {
    override fun matchAuthor(pubkey: HexKey): Boolean = delegate.matchAuthor(pubkey)

    override fun match(noteEvent: Event): Boolean = delegate.match(noteEvent)

    override fun isGlobal(): Boolean = delegate is GlobalTopNavFilter

    override fun isRelayFilter(): Boolean = delegate is RelayTopNavFilter

    override fun isMutedFilter(): Boolean = delegate is MutedAuthorsByOutboxTopNavFilter || delegate is MutedAuthorsByProxyTopNavFilter

    override fun relayUrl(): NormalizedRelayUrl? = (delegate as? RelayTopNavFilter)?.relayUrl
}

/** Extension to wrap an Android IFeedTopNavFilter for commons usage. */
fun IFeedTopNavFilter.toCommons(): CommonsIFeedTopNavFilter = FeedTopNavFilterAdapter(this)
