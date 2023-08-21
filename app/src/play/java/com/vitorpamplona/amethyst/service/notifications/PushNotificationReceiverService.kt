package com.vitorpamplona.amethyst.service.notifications

import android.app.NotificationManager
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.getOrCreateDMChannel
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.getOrCreateZapChannel
import com.vitorpamplona.quartz.events.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PushNotificationReceiverService : FirebaseMessagingService() {
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // this is called when a message is received
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        scope.launch(Dispatchers.IO) {
            remoteMessage.data.let {
                val eventStr = remoteMessage.data["event"] ?: return@let
                val event = Event.fromJson(eventStr)
                EventNotificationConsumer(applicationContext).consume(event)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNewToken(token: String) {
        scope.launch(Dispatchers.IO) {
            RegisterAccounts(LocalPreferences.allSavedAccounts()).go(token)
            notificationManager().getOrCreateZapChannel(applicationContext)
            notificationManager().getOrCreateDMChannel(applicationContext)
        }
    }

    fun notificationManager(): NotificationManager {
        return ContextCompat.getSystemService(applicationContext, NotificationManager::class.java) as NotificationManager
    }
}
