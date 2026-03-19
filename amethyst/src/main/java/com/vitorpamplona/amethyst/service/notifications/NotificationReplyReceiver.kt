/*
 * Copyright (c) 2025 Vitor Pamplona
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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class NotificationReplyReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val notificationId = intent.getIntExtra(NotificationUtils.KEY_NOTIFICATION_ID, 0)
        val notificationManager =
            ContextCompat.getSystemService(context, NotificationManager::class.java)
                as NotificationManager

        when (intent.action) {
            NotificationUtils.MARK_READ_ACTION -> {
                notificationManager.cancel(notificationId)
            }

            NotificationUtils.REPLY_ACTION -> {
                val replyText =
                    RemoteInput
                        .getResultsFromIntent(intent)
                        ?.getCharSequence(NotificationUtils.KEY_REPLY_TEXT)
                        ?.toString()

                if (replyText.isNullOrBlank()) return

                val accountNpub = intent.getStringExtra(NotificationUtils.KEY_ACCOUNT_NPUB) ?: return
                val chatroomMembersStr = intent.getStringExtra(NotificationUtils.KEY_CHATROOM_MEMBERS) ?: return
                val members = chatroomMembersStr.split(",").filter { it.isNotBlank() }

                if (members.isEmpty()) return

                val pendingResult = goAsync()

                scope.launch {
                    // activates the relay to send the message.
                    val collectionJob =
                        scope.launch {
                            Amethyst.instance.relayProxyClientConnector.relayServices
                                .collect()
                        }

                    try {
                        sendReply(accountNpub, members, replyText)
                        notificationManager.cancel(notificationId)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e("NotificationReply", "Failed to send reply: ${e.message}")
                    } finally {
                        pendingResult.finish()

                        // closes the relay connection.
                        collectionJob.cancel()
                    }
                }
            }
        }
    }

    private suspend fun sendReply(
        accountNpub: String,
        chatroomMembers: List<String>,
        replyText: String,
    ) {
        val accountSettings = LocalPreferences.loadAccountConfigFromEncryptedStorage(accountNpub) ?: return
        val account = Amethyst.instance.accountsCache.loadAccount(accountSettings)

        val recipients = chatroomMembers.map { PTag(it) }
        val template = ChatMessageEvent.build(msg = replyText, to = recipients)

        account.sendNip17PrivateMessage(template)
    }
}
