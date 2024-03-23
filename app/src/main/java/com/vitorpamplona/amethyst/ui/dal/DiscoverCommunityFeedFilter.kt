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
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.events.CommunityDefinitionEvent
import com.vitorpamplona.quartz.events.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.events.MuteListEvent
import com.vitorpamplona.quartz.events.PeopleListEvent

open class DiscoverCommunityFeedFilter(val account: Account) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String {
        return account.userProfile().pubkeyHex + "-" + account.defaultDiscoveryFollowList.value
    }

    override fun showHiddenKey(): Boolean {
        return account.defaultDiscoveryFollowList.value ==
            PeopleListEvent.blockListFor(account.userProfile().pubkeyHex) ||
            account.defaultDiscoveryFollowList.value ==
            MuteListEvent.blockListFor(account.userProfile().pubkeyHex)
    }

    override fun feed(): List<Note> {
        val filterParams =
            FilterByListParams.create(
                userHex = account.userProfile().pubkeyHex,
                selectedListName = account.defaultDiscoveryFollowList.value,
                followLists = account.liveDiscoveryFollowLists.value,
                hiddenUsers = account.flowHiddenUsers.value,
            )

        // Here we only need to look for CommunityDefinition Events
        val notes =
            LocalCache.addressables.mapNotNullIntoSet { key, note ->
                val noteEvent = note.event
                if (noteEvent == null && shouldInclude(ATag.parseAtagUnckecked(key), filterParams)) {
                    // send unloaded communities to the screen
                    note
                } else if (noteEvent is CommunityDefinitionEvent && filterParams.match(noteEvent)) {
                    note
                } else {
                    null
                }
            }

        return sort(notes)
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        return innerApplyFilter(collection)
    }

    protected open fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        // here, we need to look for CommunityDefinition in new collection AND new CommunityDefinition from Post Approvals
        val filterParams =
            FilterByListParams.create(
                userHex = account.userProfile().pubkeyHex,
                selectedListName = account.defaultDiscoveryFollowList.value,
                followLists = account.liveDiscoveryFollowLists.value,
                hiddenUsers = account.flowHiddenUsers.value,
            )

        return collection.mapNotNull { note ->
            // note event here will never be null
            val noteEvent = note.event
            if (noteEvent is CommunityDefinitionEvent && filterParams.match(noteEvent)) {
                listOf(note)
            } else if (noteEvent is CommunityPostApprovalEvent) {
                noteEvent.communities().mapNotNull {
                    val definitionNote = LocalCache.getOrCreateAddressableNote(it)
                    val definitionEvent = definitionNote.event

                    if (definitionEvent == null && shouldInclude(it, filterParams)) {
                        definitionNote
                    } else if (definitionEvent is CommunityDefinitionEvent && filterParams.match(definitionEvent)) {
                        definitionNote
                    } else {
                        null
                    }
                }
            } else {
                null
            }
        }.flatten().toSet()
    }

    private fun shouldInclude(
        aTag: ATag?,
        params: FilterByListParams,
    ) = aTag != null && aTag.kind == CommunityDefinitionEvent.KIND && params.match(aTag)

    override fun sort(collection: Set<Note>): List<Note> {
        val lastNote =
            collection.associateWith { note ->
                note.boosts.maxOfOrNull { it.createdAt() ?: 0 } ?: 0
            }

        return collection
            .sortedWith(
                compareBy(
                    { lastNote[it] },
                    { it.createdAt() },
                    { it.idHex },
                ),
            )
            .reversed()
    }
}
