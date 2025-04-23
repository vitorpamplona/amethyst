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
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.note.combineWith
import com.vitorpamplona.quartz.nip01Core.core.Event

@Composable
fun observeNote(note: Note): State<NoteState?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

    // Subscribe in the LocalCache for changes that arrive in the device
    return note.live().metadata.observeAsState()
}

@Composable
fun <T : Event> observeNoteEvent(note: Note): State<T?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

    // Subscribe in the LocalCache for changes that arrive in the device
    return note
        .live()
        .metadata
        .map { it.note.event as? T? }
        .observeAsState(note.event as? T?)
}

@Composable
fun <T> observeNoteAndMap(
    note: Note,
    map: (Note) -> T,
): State<T> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

    // Subscribe in the LocalCache for changes that arrive in the device
    return note
        .live()
        .metadata
        .map { map(it.note) }
        .distinctUntilChanged()
        .observeAsState(map(note))
}

@Composable
fun <T, U> observeNoteEventAndMap(
    note: Note,
    map: (T) -> U,
): State<U?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

    // Subscribe in the LocalCache for changes that arrive in the device
    return note
        .live()
        .metadata
        .map { (it.note.event as? T)?.let { map(it) } }
        .distinctUntilChanged()
        .observeAsState(
            (note.event as? T)?.let { map(it) },
        )
}

@Composable
fun observeNoteHasEvent(note: Note): State<Boolean> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

    // Subscribe in the LocalCache for changes that arrive in the device
    return note
        .live()
        .metadata
        .map { it.note.event != null }
        .distinctUntilChanged()
        .observeAsState(note.event != null)
}

@Composable
fun observeNoteReplies(note: Note): State<NoteState?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

    // Subscribe in the LocalCache for changes that arrive in the device
    return note.live().replies.observeAsState()
}

@Composable
fun observeNoteReplyCount(note: Note): State<Int> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

    // Subscribe in the LocalCache for changes that arrive in the device
    return note
        .live()
        .replies
        .map { it.note.reactions.size }
        .distinctUntilChanged()
        .observeAsState(note.reactions.size)
}

@Composable
fun observeNoteReactions(note: Note): State<NoteState?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

    // Subscribe in the LocalCache for changes that arrive in the device
    return note.live().reactions.observeAsState()
}

@Composable
fun observeNoteReactionCount(note: Note): State<Int> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

    // Subscribe in the LocalCache for changes that arrive in the device
    return note
        .live()
        .reactions
        .map {
            var total = 0
            it.note.reactions.forEach { total += it.value.size }
            total
        }.distinctUntilChanged()
        .observeAsState(0)
}

@Composable
fun observeNoteZaps(note: Note): State<NoteState?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

    // Subscribe in the LocalCache for changes that arrive in the device
    return note.live().zaps.observeAsState()
}

@Composable
fun observeNoteReposts(note: Note): State<NoteState?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

    // Subscribe in the LocalCache for changes that arrive in the device
    return note.live().boosts.observeAsState()
}

@Composable
fun observeNoteRepostsBy(
    note: Note,
    user: User,
): State<Boolean> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

    // Subscribe in the LocalCache for changes that arrive in the device
    return note
        .live()
        .boosts
        .map { it.note.isBoostedBy(user) }
        .distinctUntilChanged()
        .observeAsState(note.isBoostedBy(user))
}

@Composable
fun observeNoteRepostCount(note: Note): State<Int> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

    // Subscribe in the LocalCache for changes that arrive in the device
    return note
        .live()
        .boosts
        .map { it.note.boosts.size }
        .distinctUntilChanged()
        .observeAsState(note.boosts.size)
}

@Composable
fun observeNoteReferences(note: Note): State<Boolean> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

    // Subscribe in the LocalCache for changes that arrive in the device
    return note
        .live()
        .zaps
        .combineWith(note.live().boosts, note.live().reactions) { zapState, boostState, reactionState ->
            zapState?.note?.zaps?.isNotEmpty() == true ||
                boostState?.note?.boosts?.isNotEmpty() == true ||
                reactionState?.note?.reactions?.isNotEmpty() == true
        }.distinctUntilChanged()
        .observeAsState(
            note.zaps.isNotEmpty() || note.boosts.isNotEmpty() || note.reactions.isNotEmpty(),
        )
}

@Composable
fun observeNoteOts(note: Note): State<NoteState?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

    // Subscribe in the LocalCache for changes that arrive in the device
    return note.live().ots.observeAsState()
}

@Composable
fun observeNoteEdits(note: Note): State<NoteState?> {
    // Subscribe in the relay for changes in this note.
    EventFinderFilterAssemblerSubscription(note)

    // Subscribe in the LocalCache for changes that arrive in the device
    return note.live().edits.observeAsState()
}
