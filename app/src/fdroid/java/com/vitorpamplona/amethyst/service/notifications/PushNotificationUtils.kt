package com.vitorpamplona.amethyst.service.notifications

import android.util.Log
import com.vitorpamplona.amethyst.AccountInfo

object PushNotificationUtils {
    var hasInit: Boolean = false
    private val pushHandler = PushDistributorHandler
    suspend fun init(accounts: List<AccountInfo>) {
        if (hasInit || pushHandler.savedDistributorExists()) {
            return
        }
        try {
            if (pushHandler.savedDistributorExists()) {
                RegisterAccounts(accounts).go(pushHandler.getSavedEndpoint())
            }
        } catch (e: Exception) {
            Log.d("Amethyst-OSSPushUtils", "Failed to get endpoint.")
        }
    }
}
