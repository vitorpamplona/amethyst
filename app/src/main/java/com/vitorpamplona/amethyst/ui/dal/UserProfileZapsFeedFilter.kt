package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.screen.ZapReqResponse
import com.vitorpamplona.quartz.events.zaps.UserZaps

class UserProfileZapsFeedFilter(val user: User) : FeedFilter<ZapReqResponse>() {

    override fun feedKey(): String {
        return user.pubkeyHex
    }

    override fun feed(): List<ZapReqResponse> {
        return UserZaps.forProfileFeed(user.zaps)
    }

    override fun limit() = 400
}
