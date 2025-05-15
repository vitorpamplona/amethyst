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

import android.util.Log
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.observables.CreatedAtComparator
import com.vitorpamplona.amethyst.ui.dal.AdditiveFeedFilter
import com.vitorpamplona.amethyst.ui.dal.FilterByListParams
import com.vitorpamplona.quartz.nip01Core.tags.events.isTaggedEvent
import com.vitorpamplona.quartz.nip51Lists.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.PeopleListEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90TextGenDiscoveryResponseEvent

open class NIP90TextGenDiscoveryResponseFilter(
    val account: Account,
    val request: String,
) : AdditiveFeedFilter<Note>() {
    companion object {
        const val TEXT_GENERATION_RESPONSE_KIND = 6050
    }

    var latestNote: Note? = null

    override fun feedKey(): String = account.userProfile().pubkeyHex + "-textgen-" + request

    open fun followList(): String = account.settings.defaultDiscoveryFollowList.value

    override fun showHiddenKey(): Boolean =
        followList() == PeopleListEvent.blockListFor(account.userProfile().pubkeyHex) ||
            followList() == MuteListEvent.blockListFor(account.userProfile().pubkeyHex)

    fun acceptableEvent(note: Note): Boolean {
        val noteEvent = note.event
        return noteEvent is NIP90TextGenDiscoveryResponseEvent && noteEvent.isTaggedEvent(request)
    }

    override fun feed(): List<Note> {
        latestNote =
            LocalCache.notes.maxOrNullOf(
                filter = { idHex: String, note: Note ->
                    acceptableEvent(note)
                },
                comparator = CreatedAtComparator,
            )

        if (!validateLatestNote()) return listOf()

        val noteEvent = latestNote?.event as? NIP90TextGenDiscoveryResponseEvent ?: return listOf()

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
        val maxNote = collection.filter { acceptableEvent(it) }.maxByOrNull { it.createdAt() ?: 0 } ?: return emptySet()

        if ((maxNote.createdAt() ?: 0) > (latestNote?.createdAt() ?: 0)) {
            latestNote = maxNote
        }

        if (!validateLatestNote()) return emptySet()

        val noteEvent = latestNote?.event as? NIP90TextGenDiscoveryResponseEvent ?: return setOf()

        return noteEvent
            .innerTags()
            .mapNotNull {
                LocalCache.checkGetOrCreateNote(it)
            }.toSet()
    }

    /**
     * Validates that latestNote contains a NIP90TextGenDiscoveryResponseEvent.
     * If not, logs an error and resets latestNote to null.
     * @return true if latestNote is valid, false otherwise
     */
    private fun validateLatestNote(): Boolean {
        if (latestNote != null && latestNote?.event !is NIP90TextGenDiscoveryResponseEvent) {
            Log.e("DVM_DEBUG", "latestNote's event is not a NIP90TextGenDiscoveryResponseEvent: ${latestNote?.event?.javaClass?.simpleName}")
            latestNote = null
            return false
        }
        return true
    }

    override fun sort(collection: Set<Note>): List<Note> = collection.toList()
} 
