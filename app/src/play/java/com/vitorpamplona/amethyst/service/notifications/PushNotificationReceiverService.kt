package com.vitorpamplona.amethyst.service.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.service.model.Event

class PushNotificationReceiverService : FirebaseMessagingService() {

    // this is called when a message is received
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        remoteMessage.data.let {
            val eventStr = remoteMessage.data["event"] ?: return
            val event = Event.fromJson(eventStr, true)
            EventNotificationConsumer(applicationContext).consume(event)
        }
    }

    override fun onNewToken(token: String) {
        RegisterAccounts(LocalPreferences.allSavedAccounts()).go(token)
    }
}
