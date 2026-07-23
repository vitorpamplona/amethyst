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

/**
 * Git / code notifications — NIP-34 issues (1621), patches (1617), pull requests
 * (1618) and PR updates (1619) on repos you maintain. Rendered as a slate card
 * titled by the action ("X opened an issue" …) with the subject as the body.
 * Author name + avatar enriched observably.
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
