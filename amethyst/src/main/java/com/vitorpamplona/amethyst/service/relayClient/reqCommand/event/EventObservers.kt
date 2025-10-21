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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.event

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.isForCommunity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.sample

@Composable
fun observeNote(
    note: Note,
    accountViewModel: AccountViewModel,
): State<NoteState> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    return note
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()
}

@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun <T : Event> observeNoteEvent(
    note: Note,
    accountViewModel: AccountViewModel,
): State<T?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(note) {
            note
                .flow()
                .metadata.stateFlow
                .mapLatest { it.note.event as? T? }
        }

    return flow.collectAsStateWithLifecycle(note.event as? T?)
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun <T> observeNoteAndMap(
    note: Note,
    accountViewModel: AccountViewModel,
    map: (Note) -> T,
): State<T> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, accountViewModel)

    val flow =
        remember(note) {
            note
                .flow()
                .metadata.stateFlow
                .mapLatest { map(it.note) }
                .distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    // Subscribe in the LocalCache for changes that arrive in the device
    return flow.collectAsStateWithLifecycle(map(note))
}

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("UNCHECKED_CAST")
@Composable
fun <T, U> observeNoteEventAndMap(
    note: Note,
    accountViewModel: AccountViewModel,
    map: (T) -> U,
): State<U?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(note) {
            note
                .flow()
                .metadata.stateFlow
                .mapLatest { (it.note.event as? T)?.let { map(it) } }
                .distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    // Subscribe in the LocalCache for changes that arrive in the device
    return flow.collectAsStateWithLifecycle((note.event as? T)?.let { map(it) })
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun observeNoteHasEvent(
    note: Note,
    accountViewModel: AccountViewModel,
): State<Boolean> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(note) {
            note
                .flow()
                .metadata.stateFlow
                .mapLatest { it.note.event != null }
                .distinctUntilChanged()
        }

    return flow.collectAsStateWithLifecycle(note.event != null)
}

@Composable
fun observeNoteReplies(
    note: Note,
    accountViewModel: AccountViewModel,
): State<NoteState?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    return note
        .flow()
        .replies.stateFlow
        .collectAsStateWithLifecycle()
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeNoteReplyCount(
    note: Note,
    accountViewModel: AccountViewModel,
): State<Int> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(note) {
            note
                .flow()
                .replies.stateFlow
                .sample(200)
                .mapLatest { it.note.replies.size }
                .distinctUntilChanged()
        }

    return flow.collectAsStateWithLifecycle(note.replies.size)
}

@Composable
fun observeNoteReactions(
    note: Note,
    accountViewModel: AccountViewModel,
): State<NoteState?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    return note
        .flow()
        .reactions.stateFlow
        .collectAsStateWithLifecycle()
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeNoteReactionCount(
    note: Note,
    accountViewModel: AccountViewModel,
): State<Int> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(note) {
            note
                .flow()
                .reactions.stateFlow
                .sample(200)
                .mapLatest { it.note.countReactions() }
                .distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    // Subscribe in the LocalCache for changes that arrive in the device
    return flow.collectAsStateWithLifecycle(note.countReactions())
}

@Composable
fun observeNoteZaps(
    note: Note,
    accountViewModel: AccountViewModel,
): State<NoteState?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    return note
        .flow()
        .zaps.stateFlow
        .collectAsStateWithLifecycle()
}

@Composable
fun observeNoteReposts(
    note: Note,
    accountViewModel: AccountViewModel,
): State<NoteState?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    return note
        .flow()
        .boosts.stateFlow
        .collectAsStateWithLifecycle()
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun observeNoteRepostsBy(
    note: Note,
    user: User,
    accountViewModel: AccountViewModel,
): State<Boolean> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(note) {
            note
                .flow()
                .boosts.stateFlow
                .mapLatest { it.note.isBoostedBy(user) }
                .distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    return flow.collectAsStateWithLifecycle(note.isBoostedBy(user))
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeNoteRepostCount(
    note: Note,
    accountViewModel: AccountViewModel,
): State<Int> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(note) {
            note
                .flow()
                .boosts.stateFlow
                .sample(200)
                .mapLatest { note.boosts.size }
                .distinctUntilChanged()
        }

    return flow.collectAsStateWithLifecycle(note.boosts.size)
}

@Composable
fun observeNoteReferences(
    note: Note,
    accountViewModel: AccountViewModel,
): State<Boolean> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(note) {
            combine(
                note.flow().zaps.stateFlow,
                note.flow().boosts.stateFlow,
                note.flow().reactions.stateFlow,
            ) { zapState, boostState, reactionState ->
                zapState.note.hasZapsBoostsOrReactions()
            }.distinctUntilChanged()
        }

    return flow.collectAsStateWithLifecycle(note.hasZapsBoostsOrReactions())
}

@Composable
fun observeNoteOts(
    note: Note,
    accountViewModel: AccountViewModel,
): State<NoteState?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    return note
        .flow()
        .ots
        .stateFlow
        .collectAsStateWithLifecycle()
}

@Composable
fun observeNoteEdits(
    note: Note,
    accountViewModel: AccountViewModel,
): State<NoteState?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    return note
        .flow()
        .edits
        .stateFlow
        .collectAsStateWithLifecycle()
}

@Composable
fun observeCommunityApprovalNeedStatus(
    note: Note,
    community: Note,
    accountViewModel: AccountViewModel,
): State<Boolean?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note, accountViewModel)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(note, community) {
            combine(
                community.flow().metadata.stateFlow,
                note.flow().boosts.stateFlow,
            ) { communityMetadata, boosts ->
                (communityMetadata.note.event as? CommunityDefinitionEvent)?.let { communityDefEvent ->
                    val moderators = communityDefEvent.moderatorKeys().toSet()

                    if (note.author?.pubkeyHex in moderators) {
                        false
                    } else {
                        val isModerator = accountViewModel.account.userProfile().pubkeyHex in moderators

                        if (isModerator) {
                            val wasAlreadyApproved =
                                note.boosts.any {
                                    val approvalEvent = it.event
                                    (approvalEvent is CommunityPostApprovalEvent || approvalEvent is RepostEvent || approvalEvent is GenericRepostEvent) &&
                                        approvalEvent.pubKey in moderators &&
                                        approvalEvent.isForCommunity(community.idHex)
                                }
                            !wasAlreadyApproved
                        } else {
                            false
                        }
                    }
                }
            }.distinctUntilChanged()
                .flowOn(Dispatchers.IO)
        }

    // Subscribe in the LocalCache for changes that arrive in the device
    return flow.collectAsStateWithLifecycle(false)
}
