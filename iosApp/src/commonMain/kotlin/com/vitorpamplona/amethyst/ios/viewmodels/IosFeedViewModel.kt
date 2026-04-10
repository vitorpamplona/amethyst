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
package com.vitorpamplona.amethyst.ios.viewmodels

import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedFilter
import com.vitorpamplona.amethyst.commons.viewmodels.FeedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * iOS-specific FeedViewModel that loads existing cache data on creation.
 *
 * The base FeedViewModel only sets up event stream collectors — it doesn't
 * load data already in cache. This subclass fixes that by calling
 * refreshSuspended() on init.
 */
class IosFeedViewModel(
    filter: FeedFilter<Note>,
    cacheProvider: ICacheProvider,
) : FeedViewModel(filter, cacheProvider) {
    init {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                feedState.refreshSuspended()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                platform.Foundation.NSLog("IosFeedViewModel refresh error: " + (e.message ?: "unknown"))
            }
        }
    }

    /**
     * Cancel viewModelScope. ViewModel.clear() is internal in lifecycle KMP,
     * so composables use this for cleanup via DisposableEffect.
     */
    fun destroy() {
        viewModelScope.cancel()
    }
}
