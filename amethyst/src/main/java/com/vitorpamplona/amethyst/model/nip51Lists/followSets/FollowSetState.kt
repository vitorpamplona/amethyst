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

import androidx.compose.ui.util.fastAny
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.quartz.nip01Core.core.value
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.peopleList.PeopleListEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class FollowSetState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
) {
    val user = cache.getOrCreateUser(signer.pubKey)

    suspend fun getFollowSetNotes() =
        withContext(Dispatchers.Default) {
            val followSetNotes = LocalCache.getFollowSetNotesFor(user)
            Log.d(this.javaClass.simpleName, "Number of follow sets: ${followSetNotes.size}")
            return@withContext followSetNotes
        }

    private fun getFollowSetNotesFlow() =
        flow {
            val followSetNotes = getFollowSetNotes()
            val followSets = followSetNotes.map { mapNoteToFollowSet(it) }
            emit(followSets)
        }.flowOn(Dispatchers.IO)

    val profilesFlow =
        getFollowSetNotesFlow()
            .map { it ->
                it.flatMapTo(mutableSetOf()) { it.profiles }.toSet()
            }.stateIn(scope, SharingStarted.Eagerly, emptySet())

    fun isUserInFollowSets(user: User): Boolean =
        runBlocking(scope.coroutineContext) {
            LocalCache.getFollowSetNotesFor(user).fastAny { it ->
                val listEvent = it.event as PeopleListEvent
                val isInPublicSets =
                    listEvent
                        .publicPeople()
                        .fastAny { it.toTagArray().value() == user.pubkeyHex }
                val isInPrivateSets =
                    listEvent
                        .privatePeople(signer)
                        ?.fastAny { it.toTagArray().value() == user.pubkeyHex } ?: false

                isInPublicSets || isInPrivateSets
            }
        }

    fun mapNoteToFollowSet(note: Note): FollowSet =
        FollowSet
            .mapEventToSet(
                event = note.event as PeopleListEvent,
                signer,
            )
}
