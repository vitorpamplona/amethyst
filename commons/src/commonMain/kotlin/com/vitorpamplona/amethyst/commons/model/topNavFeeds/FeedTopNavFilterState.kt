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
package com.vitorpamplona.amethyst.commons.model.topNavFeeds

import com.vitorpamplona.amethyst.commons.concurrency.Dispatchers_IO
import com.vitorpamplona.amethyst.commons.model.TopFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

class FeedTopNavFilterState(
    val feedFilterListName: MutableStateFlow<TopFilter>,
    val loadFlowsFor: (TopFilter) -> IFeedFlowsType,
    val scope: kotlinx.coroutines.CoroutineScope,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val flow: StateFlow<IFeedTopNavFilter> =
        feedFilterListName
            .transformLatest { listName ->
                emitAll(loadFlowsFor(listName).flow())
            }.onStart {
                loadFlowsFor(feedFilterListName.value).startValue(this)
            }.flowOn(Dispatchers_IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                loadFlowsFor(feedFilterListName.value).startValue(),
            )
}
