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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.dvms.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.observables.CreatedAtComparator
import com.vitorpamplona.amethyst.ui.dal.AdditiveFeedFilter
import com.vitorpamplona.amethyst.ui.dal.FilterByListParams
import com.vitorpamplona.quartz.nip01Core.tags.events.isTaggedEvent
import com.vitorpamplona.quartz.nip51Lists.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.PeopleListEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90ContentDiscoveryResponseEvent

open class NIP90ContentDiscoveryResponseFilter(
    val account: Account,
    val dvmkey: String,
    val request: String,
) : AdditiveFeedFilter<Note>() {
    var latestNote: Note? = null

    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + request

    open fun followList(): String = account.settings.defaultDiscoveryFollowList.value

    override fun showHiddenKey(): Boolean =
        followList() == PeopleListEvent.blockListFor(account.userProfile().pubkeyHex) ||
            followList() == MuteListEvent.blockListFor(account.userProfile().pubkeyHex)

    fun acceptableEvent(note: Note): Boolean {
        val noteEvent = note.event
        return noteEvent is NIP90ContentDiscoveryResponseEvent && noteEvent.isTaggedEvent(request)
    }

    override fun feed(): List<Note> {
        val params = buildFilterParams(account)

        latestNote =
            LocalCache.notes.maxOrNullOf(
                filter = { idHex: String, note: Note ->
                    acceptableEvent(note)
                },
                comparator = CreatedAtComparator,
            )

        val noteEvent = latestNote?.event as? NIP90ContentDiscoveryResponseEvent ?: return listOf()

        return noteEvent.innerTags().mapNotNull {
            LocalCache.checkGetOrCreateNote(it)
        }
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> = innerApplyFilter(collection)

    fun buildFilterParams(account: Account): FilterByListParams =
        FilterByListParams.create(
            account.userProfile().pubkeyHex,
            account.settings.defaultDiscoveryFollowList.value,
            account.liveDiscoveryFollowLists.value,
            account.flowHiddenUsers.value,
        )

    protected open fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        // val params = buildFilterParams(account)

        val maxNote = collection.filter { acceptableEvent(it) }.maxByOrNull { it.createdAt() ?: 0 } ?: return emptySet()

        if ((maxNote.createdAt() ?: 0) > (latestNote?.createdAt() ?: 0)) {
            latestNote = maxNote
        }

        val noteEvent = latestNote?.event as? NIP90ContentDiscoveryResponseEvent ?: return setOf()

        return noteEvent
            .innerTags()
            .mapNotNull {
                LocalCache.checkGetOrCreateNote(it)
            }.toSet()
    }

    override fun sort(collection: Set<Note>): List<Note> = collection.toList()
}
