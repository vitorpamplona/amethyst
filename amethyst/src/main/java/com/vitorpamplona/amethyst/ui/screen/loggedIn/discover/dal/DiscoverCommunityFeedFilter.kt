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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.dal.AdditiveFeedFilter
import com.vitorpamplona.amethyst.ui.dal.FilterByListParams
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address
import com.vitorpamplona.quartz.nip51Lists.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.PeopleListEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent

open class DiscoverCommunityFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + account.settings.defaultDiscoveryFollowList.value

    override fun showHiddenKey(): Boolean =
        account.settings.defaultDiscoveryFollowList.value ==
            PeopleListEvent.blockListFor(account.userProfile().pubkeyHex) ||
            account.settings.defaultDiscoveryFollowList.value ==
            MuteListEvent.blockListFor(account.userProfile().pubkeyHex)

    override fun feed(): List<Note> {
        val filterParams =
            FilterByListParams.Companion.create(
                userHex = account.userProfile().pubkeyHex,
                selectedListName = account.settings.defaultDiscoveryFollowList.value,
                followLists = account.liveDiscoveryFollowLists.value,
                hiddenUsers = account.flowHiddenUsers.value,
            )

        // Here we only need to look for CommunityDefinition Events
        val notes =
            LocalCache.addressables.mapNotNullIntoSet { key, note ->
                val noteEvent = note.event
                if (noteEvent == null && shouldInclude(Address.parse(key), filterParams)) {
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

    override fun applyFilter(collection: Set<Note>): Set<Note> = innerApplyFilter(collection)

    protected open fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        // here, we need to look for CommunityDefinition in new collection AND new CommunityDefinition from Post Approvals
        val filterParams =
            FilterByListParams.Companion.create(
                userHex = account.userProfile().pubkeyHex,
                selectedListName = account.settings.defaultDiscoveryFollowList.value,
                followLists = account.liveDiscoveryFollowLists.value,
                hiddenUsers = account.flowHiddenUsers.value,
            )

        return collection
            .mapNotNull { note ->
                // note event here will never be null
                val noteEvent = note.event
                if (noteEvent is CommunityDefinitionEvent && filterParams.match(noteEvent)) {
                    listOf(note)
                } else if (noteEvent is CommunityPostApprovalEvent) {
                    noteEvent.communityAddresses().mapNotNull {
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
            }.flatten()
            .toSet()
    }

    private fun shouldInclude(
        aTag: Address?,
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
            ).reversed()
    }
}
