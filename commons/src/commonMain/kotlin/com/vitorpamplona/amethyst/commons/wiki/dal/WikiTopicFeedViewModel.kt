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
package com.vitorpamplona.amethyst.commons.wiki.dal

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.OwnedUniverseFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.rebuildFeed
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.observables.CandidateSet
import com.vitorpamplona.amethyst.commons.model.observables.IFilter
import com.vitorpamplona.amethyst.commons.nipsOnNostr.NipsOnNostr
import com.vitorpamplona.amethyst.commons.viewmodels.AndroidFeedViewModel
import com.vitorpamplona.amethyst.commons.wiki.WikiTopics
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

/**
 * Every version of the topic [slug] in the cache (articles and redirects, at their addresses), in
 * reading order ([WikiTopics.order]): the topic screen's list, kept current as versions arrive.
 */
class WikiTopicFeedFilter(
    val slug: String,
    val from: HexKey?,
    val account: Account,
) : AdditiveFeedFilter<Note>(),
    OwnedUniverseFeedFilter {
    override val universe = IFilter { WikiTopics.belongsTo(it, slug) }

    override var candidates: CandidateSet? = null

    override fun feedKey(): String = account.userProfile().pubkeyHex + "-wiki-" + slug

    override fun limit() = 200

    override fun feed(): List<Note> = sort(rebuildFeed("wiki $slug", candidates, keep = { accept(it) }) { WikiTopics.cached(slug, account) })

    override fun applyFilter(newItems: Set<Note>): Set<Note> = newItems.filterTo(HashSet()) { accept(it) }

    private fun accept(note: Note): Boolean = WikiTopics.belongsTo(note, slug) && NipsOnNostr.isListable(note, account)

    override fun sort(items: Set<Note>): List<Note> = WikiTopics.order(items, account, from)
}

/** The topic screen's versions of [slug], rebuilt when the account hides or unhides someone. */
@Stable
class WikiTopicFeedViewModel(
    val slug: String,
    val from: HexKey?,
    val account: Account,
) : AndroidFeedViewModel(WikiTopicFeedFilter(slug, from, account)) {
    init {
        viewModelScope.launch(Dispatchers.IO) {
            account.hiddenUsers.flow
                .drop(1)
                .collect { invalidateData() }
        }
    }

    class Factory(
        val slug: String,
        val from: HexKey?,
        val account: Account,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: KClass<T>,
            extras: CreationExtras,
        ): T = WikiTopicFeedViewModel(slug, from, account) as T
    }
}
