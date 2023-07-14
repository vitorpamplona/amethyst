package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.model.ReportEvent

class UserProfileReportsFeedFilter(val user: User) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String {
        return user.pubkeyHex
    }

    override fun feed(): List<Note> {
        return sort(innerApplyFilter(user.reports.values.flatten()))
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        return innerApplyFilter(collection)
    }

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        return collection.filter { it.event is ReportEvent && it.event?.isTaggedUser(user.pubkeyHex) == true }.toSet()
    }

    override fun sort(collection: Set<Note>): List<Note> {
        return collection.sortedWith(compareBy({ it.createdAt() }, { it.idHex })).reversed()
    }

    override fun limit() = 400
}
