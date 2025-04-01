/**
 * Copyright (c) 2024 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
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
import com.vitorpamplona.amethyst.service.okhttp.HttpClientManager
import com.vitorpamplona.amethyst.ui.tor.TorManager
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
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
        Log.d("Time", "Notification received $remoteMessage")
        scope.launch(Dispatchers.IO) {
            val (value, elapsed) =
                measureTimedValue { parseMessage(remoteMessage.data)?.let { receiveIfNew(it) } }
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
            Log.d("Lifetime Event", "PushNotificationReceiverService.onNewToken")
            // if the app is running, try to get tor. if not, goes open web.
            val okHttpClient = HttpClientManager.getHttpClient(TorManager.isSocksReady())
            PushNotificationUtils.checkAndInit(token, LocalPreferences.allSavedAccounts(), okHttpClient)
            notificationManager().getOrCreateZapChannel(applicationContext)
            notificationManager().getOrCreateDMChannel(applicationContext)
        }
    }

    fun notificationManager(): NotificationManager =
        ContextCompat.getSystemService(applicationContext, NotificationManager::class.java)
            as NotificationManager
}
