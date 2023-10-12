package com.vitorpamplona.amethyst.service.notifications

import android.content.Context
import android.content.Intent
import org.unifiedpush.android.connector.MessagingReceiver

class PushMessageReceiver: MessagingReceiver() {
    override fun onMessage(context: Context, message: ByteArray, instance: String) {
        super.onMessage(context, message, instance)
    }

    override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
        super.onNewEndpoint(context, endpoint, instance)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
    }

    override fun onRegistrationFailed(context: Context, instance: String) {
        super.onRegistrationFailed(context, instance)
    }

    override fun onUnregistered(context: Context, instance: String) {
        super.onUnregistered(context, instance)
    }
}