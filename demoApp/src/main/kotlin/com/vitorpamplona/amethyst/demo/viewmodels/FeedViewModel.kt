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
import com.vitorpamplona.quartz.nip01Core.cache.projection.ProjectionState
import com.vitorpamplona.quartz.nip01Core.cache.projection.filterItems
import com.vitorpamplona.quartz.nip01Core.cache.projection.project
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.store.ObservableEventStore
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State + actions for the kind:1 "new threads" feed.
 *
 *  - [notes] is a [StateFlow] of [ProjectionState] over the local
 *    store, narrowed to top-level notes via [filterItems]. While it is
 *    being collected the view model holds a relay subscription open
 *    for the same filter; when the last collector goes away (5 s
 *    debounce) the subscription is released, then re-opened on the
 *    next collector.
 *  - [send] signs a new kind:1 event, drops it on the local bus, and
 *    publishes it to the relays.
 */
@Stable
class FeedViewModel(
    private val db: ObservableEventStore,
    private val client: NostrClient,
    private val signer: NostrSignerInternal,
    private val relays: Set<NormalizedRelayUrl>,
) : ViewModel() {
    private val subId = newSubId()
    private val filter = Filter(kinds = listOf(TextNoteEvent.KIND), limit = 100)

    val notes: StateFlow<ProjectionState<TextNoteEvent>> =
        db
            .project<TextNoteEvent>(filter)
            .filterItems { it.value.isNewThread() }
            .onStart { client.subscribe(subId, relays.associateWith { listOf(filter) }) }
            .onCompletion { client.unsubscribe(subId) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectionState.Loading)

    fun send(text: String) {
        viewModelScope.launch {
            val signed = signer.sign<TextNoteEvent>(TextNoteEvent.build(text))
            // Hits the bus → projection picks it up alongside any inbound relay copy.
            db.insert(signed)
            client.publish(signed, relays)
        }
    }
}

/** A kind:1 note is a "new thread" iff it isn't a reply to anything. */
private fun TextNoteEvent.isNewThread(): Boolean = replyingTo() == null
