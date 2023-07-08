package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.model.ReportEvent

class UserProfileReportsFeedFilter(val user: User) : FeedFilter<Note>() {
    override fun feedKey(): String {
        return user.pubkeyHex ?: ""
    }

    override fun feed(): List<Note> {
        val reportNotes = LocalCache.notes.values.filter { (it.event as? ReportEvent)?.isTaggedUser(user.pubkeyHex) == true }

        return reportNotes
            .sortedWith(compareBy({ it.createdAt() }, { it.idHex }))
            .reversed()
    }
}
