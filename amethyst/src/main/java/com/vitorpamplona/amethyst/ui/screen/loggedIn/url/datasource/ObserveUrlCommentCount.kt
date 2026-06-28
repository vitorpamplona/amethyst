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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.url.datasource

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.nip22Comments.CommentKinds
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip22Comments.tags.RootIdentifierTag
import com.vitorpamplona.quartz.nip73ExternalIds.urls.UrlId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Observes how many NIP-22 comments (kind 1111) are scoped to [url] through its
 * NIP-73 `web` external identity, returning a live count.
 *
 * Two halves, mirroring [com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteReplyCount]:
 * - [UrlFilterAssemblerSubscription] keeps a relay REQ open for kind-1111 events whose
 *   root `I` tag equals the normalized URL, so the cache fills in while the page is on screen.
 * - [LocalCache.observeNotes] reads back the matching events and emits their count.
 *
 * This is the discovery primitive: a URL preview (and, later, the in-app browser chrome) can
 * show "this page has N comments" without the user opening the thread. Returns `0` until the
 * URL normalizes and matching comments arrive.
 */
@Composable
fun observeUrlCommentCount(
    url: String,
    accountViewModel: AccountViewModel,
): State<Int> {
    UrlFilterAssemblerSubscription(url, accountViewModel)

    val flow =
        remember(url) {
            val normalizedUrl = UrlId.toScopeOrNull(url)
            if (normalizedUrl == null) {
                flowOf(0)
            } else {
                val filter =
                    Filter(
                        kinds = CommentKinds,
                        tags = mapOf(RootIdentifierTag.TAG_NAME to listOf(normalizedUrl)),
                    )
                LocalCache
                    .observeNotes(filter)
                    .map { it.size }
                    .distinctUntilChanged()
                    .flowOn(Dispatchers.Default)
            }
        }

    return flow.collectAsStateWithLifecycle(initialValue = 0)
}
