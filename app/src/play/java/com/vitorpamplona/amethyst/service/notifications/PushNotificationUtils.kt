package com.vitorpamplona.amethyst.service.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.vitorpamplona.amethyst.AccountInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await

object PushNotificationUtils {
    var hasInit: Boolean = false
    suspend fun init(accounts: List<AccountInfo>) = with(Dispatchers.IO) {
        if (hasInit) {
            return@with
        }
        // get user notification token provided by firebase
        try {
            RegisterAccounts(accounts).go(FirebaseMessaging.getInstance().token.await())
        } catch (e: Exception) {
            Log.e("Firebase token", "failed to get firebase token", e)
        }
    }
}
