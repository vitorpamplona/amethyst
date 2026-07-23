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
import android.content.Context
import androidx.core.content.ContextCompat
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip19Bech32.toNpub

/** Deep-link URIs consumed by `MainActivity.uriToRoute` when a notification is tapped. */
object NotificationRoutes {
    private const val ACCOUNT = "?account="
    private const val SCROLL_TO = "&scrollTo="

    fun accountNpub(account: Account): String =
        account.signer.pubKey
            .hexToByteArray()
            .toNpub()

    /** Opens the note directly (used for replies, mentions, DMs, media, git). */
    fun noteUri(
        note: Note,
        accountNpub: String,
    ): String = note.toNEvent() + ACCOUNT + accountNpub

    /** Opens the Notifications tab, scrolled to [scrollToId] (used for zaps, reactions, chess). */
    fun notificationsUri(
        accountNpub: String,
        scrollToId: String,
    ): String = "notifications$ACCOUNT$accountNpub$SCROLL_TO$scrollToId"

    /** Opens a Marmot group chatroom (welcome + group message). */
    fun marmotUri(
        nostrGroupId: String,
        accountNpub: String,
    ): String = "marmot:$nostrGroupId$ACCOUNT$accountNpub"
}

internal fun Context.notificationManager(): NotificationManager = ContextCompat.getSystemService(this, NotificationManager::class.java) as NotificationManager
