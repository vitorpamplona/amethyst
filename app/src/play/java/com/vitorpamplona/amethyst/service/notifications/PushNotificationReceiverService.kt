package com.vitorpamplona.amethyst.service.notifications

import android.app.NotificationManager
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.getOrCreateDMChannel
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.getOrCreateZapChannel
import com.vitorpamplona.quartz.events.Event

class PushNotificationReceiverService : FirebaseMessagingService() {

    // this is called when a message is received
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        remoteMessage.data.let {
            val eventStr = remoteMessage.data["event"] ?: return
            val event = Event.fromJson(eventStr)
            EventNotificationConsumer(applicationContext).consume(event)
        }
    }

    override fun onNewToken(token: String) {
        RegisterAccounts(LocalPreferences.allSavedAccounts()).go(token)
        notificationManager().getOrCreateZapChannel(applicationContext)
        notificationManager().getOrCreateDMChannel(applicationContext)
    }

    fun notificationManager(): NotificationManager {
        return ContextCompat.getSystemService(applicationContext, NotificationManager::class.java) as NotificationManager
    }
}
