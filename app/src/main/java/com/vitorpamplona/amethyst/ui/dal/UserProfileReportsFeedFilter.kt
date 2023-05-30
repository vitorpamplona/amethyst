package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.model.ReportEvent

object UserProfileReportsFeedFilter : FeedFilter<Note>() {
    var user: User? = null

    fun loadUserProfile(user: User?) {
        this.user = user
    }

    override fun feed(): List<Note> {
        val myUser = user ?: return emptyList()

        val reportNotes = LocalCache.notes.values.filter { (it.event as? ReportEvent)?.isTaggedUser(myUser.pubkeyHex) == true }

        return reportNotes
            .sortedWith(compareBy({ it.createdAt() }, { it.idHex }))
            .reversed()
    }
}
