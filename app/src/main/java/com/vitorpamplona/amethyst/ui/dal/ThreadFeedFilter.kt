package com.vitorpamplona.amethyst.ui.dal

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.ThreadAssembler
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class ThreadFeedFilter(val account: Account, val noteId: String) : FeedFilter<Note>() {

    override fun feedKey(): String {
        return noteId
    }

    override fun feed(): List<Note> {
        val cachedSignatures: MutableMap<Note, Note.LevelSignature> = mutableMapOf()
        val followingKeySet = account.liveKind3Follows.value.users
        val eventsToWatch = ThreadAssembler().findThreadFor(noteId)
        val eventsInHex = eventsToWatch.map { it.idHex }.toSet()
        val now = TimeUtils.now()

        // Currently orders by date of each event, descending, at each level of the reply stack
        val order = compareByDescending<Note> {
            it.replyLevelSignature(eventsInHex, cachedSignatures, account.userProfile(), followingKeySet, now).signature
        }

        return eventsToWatch.sortedWith(order)
    }
}
