/**
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
package com.vitorpamplona.amethyst.model.nip51Lists.followSets

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip01Core.core.value
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.peopleList.PeopleListEvent
import kotlinx.coroutines.runBlocking

@Stable
data class FollowSet(
    val identifierTag: String,
    val title: String,
    val description: String?,
    val visibility: SetVisibility,
    val profiles: Set<String>,
) : NostrSet(setVisibility = visibility, content = profiles) {
    companion object {
        fun mapEventToSet(
            event: PeopleListEvent,
            signer: NostrSigner,
        ): FollowSet {
            val address = event.address()
            val dTag = event.dTag()
            val listTitle = event.nameOrTitle() ?: dTag
            val listDescription = event.description()
            val publicFollows = event.publicPeople().map { it.toTagArray() }.map { it.value() }
            val privateFollows =
                runBlocking { event.privatePeople(signer) }
                    ?.map { it.toTagArray() }
                    ?.map { it.value() } ?: emptyList()
            return if (publicFollows.isEmpty() && privateFollows.isNotEmpty()) {
                FollowSet(
                    identifierTag = dTag,
                    title = listTitle,
                    description = listDescription,
                    visibility = SetVisibility.Private,
                    profiles = privateFollows.toSet(),
                )
            } else if (publicFollows.isNotEmpty() && privateFollows.isEmpty()) {
                FollowSet(
                    identifierTag = dTag,
                    title = listTitle,
                    description = listDescription,
                    visibility = SetVisibility.Public,
                    profiles = publicFollows.toSet(),
                )
            } else {
                // Follow set is empty, so assume public. Why? Nostr limitation.
                // TODO: Could this be fixed at protocol level?
                FollowSet(
                    identifierTag = dTag,
                    title = listTitle,
                    description = listDescription,
                    visibility = SetVisibility.Public,
                    profiles = publicFollows.toSet(),
                )
            }
        }
    }
}
