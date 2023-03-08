package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User

object UserProfileReportsFeedFilter : FeedFilter<Note>() {
    var user: User? = null

    fun loadUserProfile(userId: String) {
        user = LocalCache.checkGetOrCreateUser(userId)
    }

    override fun feed(): List<Note> {
        return user?.reports
            ?.values
            ?.flatten()
            ?.sortedBy { it.createdAt() }
            ?.reversed() ?: emptyList()
    }
}
