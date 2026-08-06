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
package com.vitorpamplona.amethyst.service.notifications.renderers

import android.content.Context
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.notifications.NotificationCategory
import com.vitorpamplona.amethyst.service.notifications.NotificationContent
import com.vitorpamplona.amethyst.service.notifications.NotificationEnricher
import com.vitorpamplona.amethyst.service.notifications.NotificationRoutes
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.postStandard
import com.vitorpamplona.amethyst.service.notifications.notificationManager
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusAppliedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusClosedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusDraftEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusOpenEvent

/**
 * Git / code notifications — NIP-34 issues (1621), patches (1617), pull requests
 * (1618), PR updates (1619), replies (1622, legacy), and status transitions
 * (1630 open, 1631 applied/merged, 1632 closed, 1633 draft) on repos or threads
 * you're p-tagged into. Rendered as a slate card titled by the action ("X opened
 * an issue", "X merged a pull request", …) with the subject as the body.
 * Author name + avatar enriched observably.
 *
 * Status kinds resolve their title from the *target* event's kind (patch/PR/issue)
 * when it's in cache, so a merge on a PR reads "merged a pull request" but the
 * same 1631 targeting a plain kind-1617 patch reads "applied a patch". Falls
 * back to a generic wording when the target isn't yet resolved (rare: the
 * notification lands after the target because the p-tag subscription pulls
 * status events regardless of whether the target has been seen).
 */
object CodeNotification {
    suspend fun notify(
        context: Context,
        account: Account,
        event: GitIssueEvent,
    ) = post(context, account, event.id, event.createdAt, event.pubKey, R.string.app_notification_code_channel_message_issue, event.subject() ?: event.content)

    suspend fun notify(
        context: Context,
        account: Account,
        event: GitPatchEvent,
    ) = post(context, account, event.id, event.createdAt, event.pubKey, R.string.app_notification_code_channel_message_patch, event.subject() ?: event.content)

    suspend fun notify(
        context: Context,
        account: Account,
        event: GitPullRequestEvent,
    ) = post(context, account, event.id, event.createdAt, event.pubKey, R.string.app_notification_code_channel_message_pr, event.subject() ?: event.content)

    suspend fun notify(
        context: Context,
        account: Account,
        event: GitPullRequestUpdateEvent,
    ) = post(context, account, event.id, event.createdAt, event.pubKey, R.string.app_notification_code_channel_message_pr_update, event.content)

    suspend fun notify(
        context: Context,
        account: Account,
        event: GitReplyEvent,
    ) = post(context, account, event.id, event.createdAt, event.pubKey, R.string.app_notification_code_channel_message_reply, event.content)

    suspend fun notify(
        context: Context,
        account: Account,
        event: GitStatusOpenEvent,
    ) = post(context, account, event.id, event.createdAt, event.pubKey, R.string.app_notification_code_channel_message_status_open, event.content)

    suspend fun notify(
        context: Context,
        account: Account,
        event: GitStatusAppliedEvent,
    ) = post(
        context,
        account,
        event.id,
        event.createdAt,
        event.pubKey,
        titleRes =
            titleForStatusOnTarget(
                event.rootEventId(),
                pr = R.string.app_notification_code_channel_message_status_applied_pr,
                patch = R.string.app_notification_code_channel_message_status_applied_patch,
                issue = R.string.app_notification_code_channel_message_status_applied_issue,
                fallback = R.string.app_notification_code_channel_message_status_applied,
            ),
        subject = event.content,
    )

    suspend fun notify(
        context: Context,
        account: Account,
        event: GitStatusClosedEvent,
    ) = post(
        context,
        account,
        event.id,
        event.createdAt,
        event.pubKey,
        titleRes =
            titleForStatusOnTarget(
                event.rootEventId(),
                pr = R.string.app_notification_code_channel_message_status_closed_pr,
                patch = R.string.app_notification_code_channel_message_status_closed_patch,
                issue = R.string.app_notification_code_channel_message_status_closed_issue,
                fallback = R.string.app_notification_code_channel_message_status_closed,
            ),
        subject = event.content,
    )

    suspend fun notify(
        context: Context,
        account: Account,
        event: GitStatusDraftEvent,
    ) = post(context, account, event.id, event.createdAt, event.pubKey, R.string.app_notification_code_channel_message_status_draft, event.content)

    /**
     * Pick a title string for a status event based on the *target*'s kind, so
     * a 1631 on a kind-1618 PR reads "merged a pull request" while the same
     * status kind on a kind-1617 patch reads "applied a patch". [rootId] is
     * the marked-`root` `e` tag on the status event; when the target isn't in
     * cache we return [fallback] which is deliberately generic.
     */
    private fun titleForStatusOnTarget(
        rootId: String?,
        pr: Int,
        patch: Int,
        issue: Int,
        fallback: Int,
    ): Int {
        val targetKind = rootId?.let { LocalCache.getNoteIfExists(it)?.event?.kind } ?: return fallback
        return when (targetKind) {
            GitPullRequestEvent.KIND -> pr
            GitPatchEvent.KIND -> patch
            GitIssueEvent.KIND -> issue
            else -> fallback
        }
    }

    private suspend fun post(
        context: Context,
        account: Account,
        id: String,
        createdAt: Long,
        authorPubkey: String,
        titleRes: Int,
        subject: String?,
    ) {
        val note = LocalCache.getNoteIfExists(id) ?: return
        if (!account.isAcceptable(note)) return

        val author = LocalCache.getOrCreateUser(authorPubkey)
        val accountNpub = NotificationRoutes.accountNpub(account)
        val uri = NotificationRoutes.noteUri(note, accountNpub)
        val body = NotificationContent.excerpt(subject, 140)
        val nm = context.notificationManager()

        NotificationEnricher.enrichAndPost(
            context = context,
            account = account,
            notificationId = id,
            users = listOf(author),
            notes = listOf(note),
            isComplete = { author.metadataOrNull()?.bestName() != null },
        ) {
            nm.postStandard(
                category = NotificationCategory.CODE,
                id = id,
                messageTitle = stringRes(context, titleRes, author.toBestDisplayName()),
                messageBody = body,
                time = createdAt,
                pictureUrl = author.profilePicture(),
                uri = uri,
                applicationContext = context,
            )
        }
    }
}
