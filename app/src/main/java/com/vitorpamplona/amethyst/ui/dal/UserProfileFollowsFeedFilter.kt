package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.model.ContactListEvent

class UserProfileFollowsFeedFilter(val user: User, val account: Account) : FeedFilter<User>() {

    val cache: MutableMap<ContactListEvent, List<User>> = mutableMapOf()

    override fun feed(): List<User> {
        val contactList = user.latestContactList ?: return emptyList()

        val previousList = cache[contactList]
        if (previousList != null) return previousList

        cache[contactList] = user.latestContactList
            ?.unverifiedFollowKeySet()?.mapNotNull {
                LocalCache.checkGetOrCreateUser(it)
            }?.toSet()
            ?.filter { !account.isHidden(it) }
            ?.reversed() ?: emptyList()

        return cache[contactList] ?: emptyList()
    }
}
