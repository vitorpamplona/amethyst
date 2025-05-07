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
package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.quartz.nip51Lists.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.PeopleListEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.utils.TimeUtils

open class DiscoverNIP89FeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    val lastAnnounced = 365 * 24 * 60 * 60 // 365 Days ago
    // TODO better than announced would be last active, as this requires the DVM provider to regularly update the NIP89 announcement

    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + followList()

    open fun followList(): String = account.settings.defaultDiscoveryFollowList.value

    override fun showHiddenKey(): Boolean =
        followList() == PeopleListEvent.blockListFor(account.userProfile().pubkeyHex) ||
            followList() == MuteListEvent.blockListFor(account.userProfile().pubkeyHex)

    override fun feed(): List<Note> {
        val notes =
            LocalCache.addressables.filterIntoSet { _, it ->
                acceptDVM(it)
            }

        return sort(notes)
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> = innerApplyFilter(collection)

    fun buildFilterParams(account: Account): FilterByListParams =
        FilterByListParams.create(
            account.userProfile().pubkeyHex,
            account.settings.defaultDiscoveryFollowList.value,
            account.liveDiscoveryFollowLists.value,
            account.flowHiddenUsers.value,
        )

    fun acceptDVM(note: Note): Boolean {
        val noteEvent = note.event
        return if (noteEvent is AppDefinitionEvent) {
            acceptDVM(noteEvent)
        } else {
            false
        }
    }

    open fun acceptDVM(noteEvent: AppDefinitionEvent): Boolean {
        val filterParams = buildFilterParams(account)
        // only include DVM definitions (kind 5300) announced within the lastAnnounced period (1 year)
        return noteEvent.appMetaData()?.subscription != true &&
            filterParams.match(noteEvent) &&
            noteEvent.includeKind(5300) &&
            noteEvent.createdAt > TimeUtils.now() - lastAnnounced
    }

    protected open fun innerApplyFilter(collection: Collection<Note>): Set<Note> =
        collection.filterTo(HashSet()) {
            acceptDVM(it)
        }

    override fun sort(collection: Set<Note>): List<Note> = collection.sortedWith(compareBy({ it.createdAt() }, { it.idHex })).reversed()
}

/**
 * Specialized filter that discovers only Text Generation DVMs (kind 5050)
 */
class TextGenerationDVMFeedFilter(
    account: Account,
) : DiscoverNIP89FeedFilter(account) {
    override fun feed(): List<Note> {
        val notes =
            LocalCache.addressables.filterIntoSet { _, it ->
                acceptDVM(it)
            }

        return sort(notes)
    }

    override fun innerApplyFilter(collection: Collection<Note>): Set<Note> =
        collection.filterTo(HashSet()) {
            acceptDVM(it)
        }

    override fun acceptDVM(noteEvent: AppDefinitionEvent): Boolean {
        val filterParams = buildFilterParams(account)

        // Include only DVMs that explicitly support kind 5050 (Text Generation)
        val supportedKinds = noteEvent.supportedKinds()
        val supportsTextGeneration = noteEvent.includeKind(5050)

        // Log for debugging
        android.util.Log.d(
            "DVM",
            "Checking DVM with id=${noteEvent.id.take(8)}, " +
                "kinds=${supportedKinds.joinToString()}, " +
                "supports5050=$supportsTextGeneration",
        )

        // Only include DVMs active in the past week
        val oneWeekAgo = TimeUtils.now() - (7 * 24 * 60 * 60)
        return noteEvent.appMetaData()?.subscription != true &&
            filterParams.match(noteEvent) &&
            supportsTextGeneration &&
            noteEvent.createdAt > oneWeekAgo
    }
}
