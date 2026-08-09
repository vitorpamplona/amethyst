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
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.accountsCache.AccountCacheState
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.cancelAndPrune
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.cancelChildlessGroupSummaries
import com.vitorpamplona.amethyst.ui.actions.NewMessageTagger
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip10Notes.tags.notify
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip22Comments.notify
import com.vitorpamplona.quartz.nip89AppHandlers.clientTag.isClient
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class NotificationReplyReceiver : BroadcastReceiver() {
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
                notificationManager.cancelAndPrune(notificationId)
            }

            // The user swiped the notification away. It is already gone; all that is left
            // is to take its group summary with it when it was the last child.
            NotificationUtils.DISMISS_ACTION -> {
                notificationManager.cancelChildlessGroupSummaries(alreadyGone = notificationId)
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

                runOnRelay(notificationManager, notificationId) {
                    sendReply(accountNpub, members, replyText)
                }
            }

            NotificationUtils.PUBLIC_REPLY_ACTION -> {
                val replyText =
                    RemoteInput
                        .getResultsFromIntent(intent)
                        ?.getCharSequence(NotificationUtils.KEY_REPLY_TEXT)
                        ?.toString()

                if (replyText.isNullOrBlank()) return

                val accountNpub = intent.getStringExtra(NotificationUtils.KEY_ACCOUNT_NPUB) ?: return
                val targetEventId = intent.getStringExtra(NotificationUtils.KEY_TARGET_EVENT_ID) ?: return

                runOnRelay(notificationManager, notificationId) {
                    sendPublicReply(accountNpub, targetEventId, replyText)
                }
            }

            NotificationUtils.MARMOT_REPLY_ACTION -> {
                val replyText =
                    RemoteInput
                        .getResultsFromIntent(intent)
                        ?.getCharSequence(NotificationUtils.KEY_REPLY_TEXT)
                        ?.toString()

                if (replyText.isNullOrBlank()) return

                val accountNpub = intent.getStringExtra(NotificationUtils.KEY_ACCOUNT_NPUB) ?: return
                val nostrGroupId = intent.getStringExtra(NotificationUtils.KEY_MARMOT_GROUP_ID) ?: return
                val replyToInnerId = intent.getStringExtra(NotificationUtils.KEY_MARMOT_REPLY_TO_INNER_ID)
                val replyToInnerAuthor = intent.getStringExtra(NotificationUtils.KEY_MARMOT_REPLY_TO_INNER_AUTHOR)

                runOnRelay(notificationManager, notificationId) {
                    sendMarmotReply(accountNpub, nostrGroupId, replyToInnerId, replyToInnerAuthor, replyText)
                }
            }
        }
    }

    private fun runOnRelay(
        notificationManager: NotificationManager,
        notificationId: Int,
        block: suspend () -> Unit,
    ) {
        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        scope.launch {
            val collectionJob =
                scope.launch {
                    Amethyst.instance.relayProxyClientConnector.relayServices
                        .collect()
                }

            try {
                block()
                notificationManager.cancelAndPrune(notificationId)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("NotificationReply") { "Failed to send reply: ${e.message}" }
            } finally {
                pendingResult.finish()
                collectionJob.cancel()
                scope.cancel()
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

    private suspend fun sendMarmotReply(
        accountNpub: String,
        nostrGroupId: String,
        replyToInnerEventId: String?,
        replyToInnerAuthor: String?,
        replyText: String,
    ) {
        val accountSettings = LocalPreferences.loadAccountConfigFromEncryptedStorage(accountNpub) ?: return
        val account = Amethyst.instance.accountsCache.loadAccount(accountSettings)

        val manager = account.marmotManager ?: return

        // Use id+author from the Intent so the reply is threaded even when
        // LocalCache hasn't been rehydrated yet (cold-process broadcast
        // receiver: Account.restoreAll runs async on init and may not have
        // finished by the time we get here).
        val bundle =
            manager.buildTextMessage(
                nostrGroupId = nostrGroupId,
                text = replyText,
                replyToEventId = replyToInnerEventId,
                replyToAuthorPubKey = replyToInnerAuthor,
                persistOwn = false,
            )

        account.marmot.sendMarmotGroupMessage(nostrGroupId, bundle.innerEvent, account.marmot.marmotGroupRelays(nostrGroupId))
    }

    private suspend fun sendPublicReply(
        accountNpub: String,
        targetEventId: String,
        replyText: String,
    ) {
        val accountSettings = LocalPreferences.loadAccountConfigFromEncryptedStorage(accountNpub) ?: return
        val account = Amethyst.instance.accountsCache.loadAccount(accountSettings)

        val targetEvent = LocalCache.getNoteIfExists(targetEventId)?.event ?: return

        // Resolve @/nostr: mentions typed into the notification reply, so a cited member is tagged
        // (`p`) and linkable — the same enrichment the in-app composers do. The comment builders
        // already tag the reply-parent author, so drop it from the body mentions to avoid a
        // duplicate `p` (kind-1 doesn't auto-tag the parent, so nothing is lost there).
        val tagger = NewMessageTagger(replyText, dao = LocalCache)
        tagger.run()
        val mentions = tagger.pTags?.mapNotNull { pt -> pt.pubkeyHex.takeIf { it != targetEvent.pubKey } }.orEmpty()

        val template =
            when {
                // A brand-new Amethyst kind-1 thread root is replied to with a NIP-22
                // kind 1111 Comment instead of a kind 1 reply.
                targetEvent is TextNoteEvent &&
                    targetEvent.isNewThread() &&
                    targetEvent.isClient(AccountCacheState.CLIENT_TAG_NAME) -> {
                    CommentEvent.replyBuilder(
                        msg = tagger.message,
                        replyingTo = EventHintBundle(targetEvent),
                    ) {
                        notify(mentions.map { PTag(it) })
                    }
                }

                targetEvent is TextNoteEvent -> {
                    TextNoteEvent.build(
                        note = tagger.message,
                        replyingTo = EventHintBundle(targetEvent),
                    ) {
                        notify(mentions.map { PTag(it) })
                    }
                }

                else -> {
                    // NIP-22 CommentEvent and other non-threaded events (e.g. long-form articles)
                    // both reply via NIP-22 comments.
                    CommentEvent.replyBuilder(
                        msg = tagger.message,
                        replyingTo = EventHintBundle(targetEvent),
                    ) {
                        notify(mentions.map { PTag(it) })
                    }
                }
            }

        account.signAndComputeBroadcast(template)
    }
}
