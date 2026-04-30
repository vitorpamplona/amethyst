/*
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
package com.vitorpamplona.amethyst.demo.viewmodels

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.demo.data.NotesFeed
import com.vitorpamplona.quartz.nip01Core.cache.projection.ProjectionState
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.store.ObservableEventStore
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI scaffolding around a [NotesFeed]:
 *
 *  - [feed] hot-shares the cold projection inside [viewModelScope] so
 *    multiple collectors share one relay subscription, and
 *    `WhileSubscribed(5_000)` debounces relay tear-down when the UI
 *    leaves composition.
 *  - [send] takes a [NostrSigner] at call time rather than capturing
 *    one at construction, so the app can swap signers (login /
 *    logout) without rebuilding the view model.
 */
@Stable
class FeedViewModel(
    private val db: ObservableEventStore,
    private val client: NostrClient,
) : ViewModel() {
    private val notesFeed = NotesFeed(db, client)

    val feed: StateFlow<ProjectionState<TextNoteEvent>> =
        notesFeed.notes
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectionState.Loading)

    fun send(
        text: String,
        signer: NostrSigner,
    ) {
        viewModelScope.launch {
            val signed = signer.sign<TextNoteEvent>(TextNoteEvent.build(text))
            // Hits the bus → projection picks it up alongside any inbound relay copy.
            db.insert(signed)
            client.publish(signed, notesFeed.relays)
        }
    }
}
