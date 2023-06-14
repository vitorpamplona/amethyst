package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.model.zaps.UserZaps
import com.vitorpamplona.amethyst.ui.screen.ZapReqResponse

class UserProfileZapsFeedFilter(val user: User) : FeedFilter<ZapReqResponse>() {
    override fun feed(): List<ZapReqResponse> {
        return UserZaps.forProfileFeed(user.zaps)
    }
}
