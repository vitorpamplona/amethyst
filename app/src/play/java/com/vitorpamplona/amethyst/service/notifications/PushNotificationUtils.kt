package com.vitorpamplona.amethyst.service.notifications

import android.util.Log
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import com.vitorpamplona.amethyst.AccountInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PushNotificationUtils {
    fun init(accounts: List<AccountInfo>) {
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        scope.launch {
            // get user notification token provided by firebase
            FirebaseMessaging.getInstance().token.addOnCompleteListener(
                OnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.w("FirebaseMsgService", "Fetching FCM registration token failed", task.exception)
                        return@OnCompleteListener
                    }
                    // Get new FCM registration token
                    val notificationToken = task.result

                    RegisterAccounts(accounts).go(notificationToken)
                }
            )
        }
    }
}
