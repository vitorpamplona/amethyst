package com.vitorpamplona.amethyst.service.notifications

import android.app.NotificationManager
import android.util.Log
import android.util.LruCache
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.getOrCreateDMChannel
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.getOrCreateZapChannel
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.GiftWrapEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.time.measureTimedValue

class PushNotificationReceiverService : FirebaseMessagingService() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val eventCache = LruCache<String, String>(100)

    // this is called when a message is received
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        scope.launch(Dispatchers.IO) {
            val (value, elapsed) = measureTimedValue {
                parseMessage(remoteMessage.data)?.let {
                    receiveIfNew(it)
                }
            }
            Log.d("Time", "Notification processed in $elapsed")
        }
    }

    private suspend fun parseMessage(params: Map<String, String>): GiftWrapEvent? {
        params["encryptedEvent"]?.let { eventStr ->
            (Event.fromJson(eventStr) as? GiftWrapEvent)?.let {
                return it
            }
        }
        return null
    }

    private suspend fun receiveIfNew(event: GiftWrapEvent) {
        if (eventCache.get(event.id) == null) {
            eventCache.put(event.id, event.id)
            EventNotificationConsumer(applicationContext).consume(event)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("Lifetime Event", "PushNotificationReceiverService.onCreate")
    }

    override fun onDestroy() {
        Log.d("Lifetime Event", "PushNotificationReceiverService.onDestroy")

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
