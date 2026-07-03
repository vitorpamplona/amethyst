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
package com.vitorpamplona.amethyst.desktop.ui.notifications

import com.vitorpamplona.amethyst.commons.moderation.notifications.NotifKind
import com.vitorpamplona.amethyst.commons.moderation.notifications.NotificationDispatcher
import com.vitorpamplona.amethyst.commons.moderation.notifications.NotificationKinds
import com.vitorpamplona.amethyst.commons.moderation.notifications.NotificationSettings
import com.vitorpamplona.amethyst.commons.moderation.notifications.NotificationSpec
import com.vitorpamplona.amethyst.commons.moderation.notifications.PermissionState
import com.vitorpamplona.amethyst.commons.moderation.notifications.nowEpochSeconds
import com.vitorpamplona.amethyst.commons.moderation.notifications.sanitizeForToast
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.logging.Logger

/**
 * Subscribes to [DesktopLocalCache]'s new-event stream and fires OS toasts
 * through [NotificationDispatcher] for events that pass the shared
 * `tagsAnEventForUser` semantic gate + a lightweight suppression pipeline
 * (master toggle, per-kind toggle, DND, window focus, cold-boot,
 * per-event 30-second dedupe).
 *
 * Runs one collector for the app's lifetime, scoped to [scope]. Cancel
 * the scope to stop.
 */
class DesktopNotificationAutoDispatcher(
    private val dispatcher: NotificationDispatcher,
    private val settings: NotificationSettings,
    private val myPubKeyHex: String,
    private val localCache: DesktopLocalCache,
    private val isWindowFocused: StateFlow<Boolean>,
    private val sessionStartSec: Long,
    private val scope: CoroutineScope,
) {
    private val log = Logger.getLogger(DesktopNotificationAutoDispatcher::class.java.simpleName)
    private val recentFires = HashMap<String, Long>()

    fun start(): Job =
        scope.launch {
            log.info("Auto-dispatcher started (pubKey=${myPubKeyHex.take(8)}, sessionStart=$sessionStartSec)")
            localCache.eventStream.newEventBundles.collect { bundle ->
                for (note in bundle) {
                    val event = note.event ?: continue
                    tryDispatch(event)
                }
            }
        }

    private fun logSkip(
        eventId: String,
        kindLabel: String,
        reason: String,
    ) {
        log.info("[AutoDispatch] SKIP kind=$kindLabel id=${eventId.take(8)} reason=$reason")
    }

    private fun tryDispatch(event: Event) {
        val eid = event.id
        // Fast-path rejections — cheapest first.
        if (event.kind !in NotificationKinds.SUBSCRIPTION_KINDS) {
            // Skip logging: most events aren't notification kinds and would spam.
            return
        }

        val kind = notifKindFor(event)
        if (kind == null) {
            logSkip(eid, "kind=${event.kind}", "unmapped-kind")
            return
        }

        if (!settings.enabled.value) {
            logSkip(eid, kind.name, "master-off")
            return
        }
        if (!settings.kinds.value.enabledFor(kind)) {
            logSkip(eid, kind.name, "kind-toggle-off")
            return
        }

        val now = nowEpochSeconds()
        if (settings.dnd.value.isActive(now)) {
            logSkip(eid, kind.name, "dnd-active")
            return
        }
        if (isWindowFocused.value) {
            logSkip(eid, kind.name, "window-focused")
            return
        }

        if (event.createdAt < sessionStartSec) {
            logSkip(eid, kind.name, "pre-session (created=${event.createdAt} < start=$sessionStartSec)")
            return
        }
        if ((now - event.createdAt) > 30) {
            logSkip(eid, kind.name, "stale (${now - event.createdAt}s old)")
            return
        }

        val perm = dispatcher.permission.value
        if (perm != PermissionState.Granted && perm != PermissionState.NotApplicable) {
            logSkip(eid, kind.name, "permission=$perm")
            return
        }

        val accepts =
            NotificationKinds.tagsAnEventForUser(
                event = event,
                myPubKeyHex = myPubKeyHex,
                isTargetAuthoredByMe = { targetId ->
                    localCache.notes
                        .get(targetId)
                        ?.event
                        ?.pubKey == myPubKeyHex
                },
            )
        if (!accepts) {
            logSkip(eid, kind.name, "not-tagged-for-user (author=${event.pubKey.take(8)})")
            return
        }

        val dedupeKey = kind.name + "|" + event.id
        val last = recentFires[dedupeKey]
        if (last != null && (now - last) < 30) {
            logSkip(eid, kind.name, "deduped (${now - last}s ago)")
            return
        }
        recentFires[dedupeKey] = now
        recentFires.entries.removeAll { now - it.value > 300 }

        val spec = buildSpec(event, kind)
        scope.launch {
            try {
                val result = dispatcher.send(spec)
                log.info("[AutoDispatch] FIRED kind=$kind id=${eid.take(8)} result=$result title='${spec.title}'")
            } catch (t: Throwable) {
                log.warning("[AutoDispatch] FAILED kind=$kind id=${eid.take(8)} error=${t.message}")
            }
        }
    }

    private fun notifKindFor(event: Event): NotifKind? =
        when (event) {
            is ReactionEvent -> NotifKind.REACTION
            is RepostEvent, is GenericRepostEvent -> NotifKind.REPOST
            is LnZapEvent, is NutzapEvent, is OnchainZapEvent -> NotifKind.ZAP
            is TextNoteEvent -> {
                val isReply = event.tags.any { it.size > 1 && it[0] == "e" }
                if (isReply) NotifKind.REPLY else NotifKind.MENTION
            }
            is CommentEvent -> NotifKind.REPLY
            is ChannelMessageEvent -> NotifKind.MENTION
            is PrivateDmEvent,
            is ChatMessageEvent,
            is GiftWrapEvent,
            is ChatMessageEncryptedFileHeaderEvent,
            -> NotifKind.DM
            else -> null
        }

    private fun buildSpec(
        event: Event,
        kind: NotifKind,
    ): NotificationSpec {
        val effectivePubKey =
            when (event) {
                is LnZapEvent -> event.zapRequest?.pubKey ?: event.pubKey
                else -> event.pubKey
            }
        val displayName =
            localCache.getUserIfExists(effectivePubKey)?.toBestDisplayName()
                ?: "Someone"

        val amountText =
            (event as? LnZapEvent)?.amount?.let { " ${it.toLong() / 1000} sats" } ?: ""

        val title =
            when (kind) {
                NotifKind.ZAP -> "⚡ $displayName zapped you$amountText"
                NotifKind.DM -> "New encrypted message"
                NotifKind.REPLY -> "$displayName replied"
                NotifKind.MENTION -> "$displayName mentioned you"
                NotifKind.REPOST -> "$displayName reposted"
                NotifKind.REACTION -> "$displayName reacted"
                NotifKind.FOLLOW -> "$displayName followed you"
            }

        // Never leak DM ciphertext into the body — decryption pipeline
        // isn't wired yet.
        val body =
            when {
                kind == NotifKind.DM -> ""
                !settings.previewInToast.value -> ""
                else -> sanitizeForToast(event.content, maxLen = 120)
            }

        return NotificationSpec(
            title = title,
            body = body,
            kind = kind,
            threadId = event.id,
            deepLinkNoteId = event.id,
        )
    }
}
