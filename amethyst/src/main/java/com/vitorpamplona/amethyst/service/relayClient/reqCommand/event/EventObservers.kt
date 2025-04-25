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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.event

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.sample

@Composable
fun observeNote(note: Note): State<NoteState> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

    // Subscribe in the LocalCache for changes that arrive in the device
    return note
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()
}

@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun <T : Event> observeNoteEvent(note: Note): State<T?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

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

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun <T> observeNoteAndMap(
    note: Note,
    map: (Note) -> T,
): State<T> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

    val flow =
        remember(note) {
            note
                .flow()
                .metadata.stateFlow
                .mapLatest { map(it.note) }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
        }

    // Subscribe in the LocalCache for changes that arrive in the device
    return flow.collectAsStateWithLifecycle(map(note))
}

@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun <T, U> observeNoteEventAndMap(
    note: Note,
    map: (T) -> U,
): State<U?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(note) {
            note
                .flow()
                .metadata.stateFlow
                .mapLatest { (it.note.event as? T)?.let { map(it) } }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
        }

    // Subscribe in the LocalCache for changes that arrive in the device
    return flow.collectAsStateWithLifecycle((note.event as? T)?.let { map(it) })
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeNoteHasEvent(note: Note): State<Boolean> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

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
fun observeNoteReplies(note: Note): State<NoteState?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

    // Subscribe in the LocalCache for changes that arrive in the device
    return note
        .flow()
        .replies.stateFlow
        .collectAsStateWithLifecycle()
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeNoteReplyCount(note: Note): State<Int> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(note) {
            note
                .flow()
                .reactions.stateFlow
                .mapLatest { it.note.replies.size }
                .sample(1000)
                .distinctUntilChanged()
        }

    return flow.collectAsStateWithLifecycle(note.replies.size)
}

@Composable
fun observeNoteReactions(note: Note): State<NoteState?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

    // Subscribe in the LocalCache for changes that arrive in the device
    return note
        .flow()
        .reactions.stateFlow
        .collectAsStateWithLifecycle()
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeNoteReactionCount(note: Note): State<Int> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(note) {
            note
                .flow()
                .reactions.stateFlow
                .mapLatest { it.note.countReactions() }
                .sample(1000)
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
        }

    // Subscribe in the LocalCache for changes that arrive in the device
    return flow.collectAsStateWithLifecycle(note.countReactions())
}

@Composable
fun observeNoteZaps(note: Note): State<NoteState?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

    // Subscribe in the LocalCache for changes that arrive in the device
    return note
        .flow()
        .zaps.stateFlow
        .collectAsStateWithLifecycle()
}

@Composable
fun observeNoteReposts(note: Note): State<NoteState?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

    // Subscribe in the LocalCache for changes that arrive in the device
    return note
        .flow()
        .boosts.stateFlow
        .collectAsStateWithLifecycle()
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeNoteRepostsBy(
    note: Note,
    user: User,
): State<Boolean> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(note) {
            note
                .flow()
                .boosts.stateFlow
                .mapLatest { it.note.isBoostedBy(user) }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
        }

    return flow.collectAsStateWithLifecycle(note.isBoostedBy(user))
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Composable
fun observeNoteRepostCount(note: Note): State<Int> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

    // Subscribe in the LocalCache for changes that arrive in the device
    val flow =
        remember(note) {
            note
                .flow()
                .boosts.stateFlow
                .sample(1000)
                .mapLatest { note.boosts.size }
                .distinctUntilChanged()
        }

    return flow.collectAsStateWithLifecycle(note.boosts.size)
}

@Composable
fun observeNoteReferences(note: Note): State<Boolean> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

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
fun observeNoteOts(note: Note): State<NoteState?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

    // Subscribe in the LocalCache for changes that arrive in the device
    return note
        .flow()
        .ots
        .stateFlow
        .collectAsStateWithLifecycle()
}

@Composable
fun observeNoteEdits(note: Note): State<NoteState?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

    // Subscribe in the LocalCache for changes that arrive in the device
    return note
        .flow()
        .edits
        .stateFlow
        .collectAsStateWithLifecycle()
}
