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
package com.vitorpamplona.amethyst.commons.nipsOnNostr.dal

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.viewmodels.AndroidFeedViewModel
import kotlin.reflect.KClass

/** The kind page's NIPs on Nostr that define [kind], kept current from the cache like any feed. */
@Stable
class NipsDefiningKindFeedViewModel(
    val kind: Int,
    val account: Account,
) : AndroidFeedViewModel(NipsDefiningKindFeedFilter(kind, account)) {
    class Factory(
        val kind: Int,
        val account: Account,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: KClass<T>,
            extras: CreationExtras,
        ): T = NipsDefiningKindFeedViewModel(kind, account) as T
    }
}

/** The kind page's sample of events of [kind], kept current from the cache like any feed. */
@Stable
class KindEventsFeedViewModel(
    val kind: Int,
    val account: Account,
) : AndroidFeedViewModel(KindEventsFeedFilter(kind, account)) {
    class Factory(
        val kind: Int,
        val account: Account,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: KClass<T>,
            extras: CreationExtras,
        ): T = KindEventsFeedViewModel(kind, account) as T
    }
}
