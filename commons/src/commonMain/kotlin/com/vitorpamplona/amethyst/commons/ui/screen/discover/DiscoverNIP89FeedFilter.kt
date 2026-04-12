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
package com.vitorpamplona.amethyst.commons.ui.screen.discover

import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.model.cache.filterIntoSet
import com.vitorpamplona.amethyst.commons.ui.dal.FilterByListParams
import com.vitorpamplona.amethyst.commons.ui.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.ui.feeds.DefaultFeedOrder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.utils.TimeUtils

open class DiscoverNIP89FeedFilter(
    val account: IAccount,
    val cache: ICacheProvider,
    val targetKind: Int = 5300,
) : AdditiveFeedFilter<Note>() {
    val lastAnnounced = TimeUtils.oneYearAgo()

    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + account.accountSettings.defaultDiscoveryFollowListCode.value

    override fun limit() = 50

    override fun showHiddenKey(): Boolean = account.getDiscoveryFollowList()?.isMutedFilter == true

    override fun feed(): List<Note> {
        val notes =
            cache.addressables.filterIntoSet(AppDefinitionEvent.KIND) { _, it ->
                acceptApp(it)
            }

        return sort(notes)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = innerApplyFilter(newItems)

    fun buildFilterParams(account: IAccount): FilterByListParams =
        FilterByListParams.create(
            account.getDiscoveryFollowList(),
            account.getLiveHiddenUsers(),
        )

    fun acceptApp(note: Note): Boolean {
        val noteEvent = note.event
        return if (noteEvent is AppDefinitionEvent) {
            acceptApp(noteEvent, note.relays)
        } else {
            false
        }
    }

    open fun acceptApp(
        noteEvent: AppDefinitionEvent,
        relays: List<NormalizedRelayUrl>,
    ): Boolean {
        val filterParams = buildFilterParams(account)
        return noteEvent.appMetaData()?.subscription != true &&
            filterParams.match(noteEvent, relays) &&
            noteEvent.includeKind(targetKind) &&
            noteEvent.createdAt > lastAnnounced
    }

    protected open fun innerApplyFilter(collection: Collection<Note>): Set<Note> =
        collection.filterTo(HashSet()) {
            acceptApp(it)
        }

    /**
     * Default sort by creation date. Android overrides this with participant-count-based sorting.
     */
    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(DefaultFeedOrder)
}
