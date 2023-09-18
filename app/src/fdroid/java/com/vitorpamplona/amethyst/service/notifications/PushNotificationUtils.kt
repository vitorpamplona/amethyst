package com.vitorpamplona.amethyst.service.notifications

import com.vitorpamplona.amethyst.AccountInfo

object PushNotificationUtils {
    var hasInit: Boolean = true
    suspend fun init(accounts: List<AccountInfo>) {
    }
}
