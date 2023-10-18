package com.vitorpamplona.amethyst.service.notifications

import android.util.Log
import com.vitorpamplona.amethyst.AccountInfo
import kotlinx.coroutines.Dispatchers

object PushNotificationUtils {
    var hasInit: Boolean = false
    private val pushHandler = PushDistributorHandler()
    suspend fun init(accounts: List<AccountInfo>) = with(Dispatchers.IO) {
        if (hasInit || pushHandler.savedDistributorExists()) {
            return@with
        }
        try {
            RegisterAccounts(accounts).go(pushHandler.getEndpoint())
        } catch (e: Exception) {
            Log.d("Amethyst-OSSPushUtils", "Failed to get endpoint.")
        }
    }
}
