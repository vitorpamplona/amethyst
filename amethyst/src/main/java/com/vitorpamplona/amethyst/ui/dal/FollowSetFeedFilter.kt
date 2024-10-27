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
import com.vitorpamplona.quartz.events.PeopleListEvent

class FollowSetFeedFilter(
    val account: Account,
) : FeedFilter<PeopleListEvent.FollowSet>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex

    private fun mapEventToSet(event: PeopleListEvent): PeopleListEvent.FollowSet {
        val dTag = event.dTag()
        val listTitle = event.title() ?: dTag
        val listDescription = event.description() ?: ""
        val publicFollows = event.filterTagList("p", null)
        val privateFollows = mutableListOf<String>()
        event.privateTaggedUsers(account.signer) { userList -> privateFollows.addAll(userList) }
        return if (publicFollows.isEmpty() && privateFollows.isNotEmpty()) {
            PeopleListEvent.FollowSet(
                isPrivate = true,
                title = listTitle,
                description = listDescription,
                profileList = privateFollows.toSet(),
            )
        } else if (publicFollows.isNotEmpty() && privateFollows.isEmpty()) {
            PeopleListEvent.FollowSet(
                isPrivate = false,
                title = listTitle,
                description = listDescription,
                profileList = publicFollows.toSet(),
            )
        } else {
            throw Exception("Mixed follow sets are not yet supported.")
        }
    }

    override fun feed(): List<PeopleListEvent.FollowSet> =
        account
            .followSetNotesFlow()
            .value
            .user
            .followSets
            .map { setEntry -> mapEventToSet(setEntry.value) }
}
