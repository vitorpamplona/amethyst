package com.vitorpamplona.amethyst.ui.dal

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.ThreadAssembler

@Immutable
class ThreadFeedFilter(val noteId: String) : FeedFilter<Note>() {

    override fun feed(): List<Note> {
        val cachedSignatures: MutableMap<Note, String> = mutableMapOf()
        val eventsToWatch = ThreadAssembler().findThreadFor(noteId) ?: emptySet()
        // Currently orders by date of each event, descending, at each level of the reply stack
        val order = compareByDescending<Note> { it.replyLevelSignature(cachedSignatures) }

        return eventsToWatch.sortedWith(order)
    }
}
