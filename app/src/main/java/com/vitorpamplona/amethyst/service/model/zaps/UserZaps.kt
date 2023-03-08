package com.vitorpamplona.amethyst.service.model.zaps

import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.LnZapEventInterface

object UserZaps {
    fun forProfileFeed(zaps: Map<Note, Note?>?): List<Pair<Note, Note>> {
        if (zaps == null) return emptyList()

        return (
            zaps
                .filter { it.value != null }
                .toList()
                .sortedBy { (it.second?.event as? LnZapEventInterface)?.amount() }
                .reversed()
            ) as List<Pair<Note, Note>>
    }
}
