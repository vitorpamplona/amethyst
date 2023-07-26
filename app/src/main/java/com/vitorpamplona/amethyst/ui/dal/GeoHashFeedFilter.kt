package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.LongTextNoteEvent
import com.vitorpamplona.amethyst.service.model.PrivateDmEvent
import com.vitorpamplona.amethyst.service.model.TextNoteEvent

class GeoHashFeedFilter(val tag: String, val account: Account) : AdditiveFeedFilter<Note>() {

    override fun feedKey(): String {
        return account.userProfile().pubkeyHex + "-" + tag
    }

    override fun feed(): List<Note> {
        return sort(innerApplyFilter(LocalCache.notes.values))
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        return innerApplyFilter(collection)
    }

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val myTag = tag ?: return emptySet()

        return collection
            .asSequence()
            .filter {
                (
                    it.event is TextNoteEvent ||
                        it.event is LongTextNoteEvent ||
                        it.event is ChannelMessageEvent ||
                        it.event is PrivateDmEvent
                    ) &&
                    it.event?.isTaggedGeoHash(myTag) == true
            }
            .filter { account.isAcceptable(it) }
            .toSet()
    }

    override fun sort(collection: Set<Note>): List<Note> {
        return collection.sortedWith(compareBy({ it.createdAt() }, { it.idHex })).reversed()
    }
}
