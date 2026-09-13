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
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip19Bech32.toNpub

/** Deep-link URIs consumed by `MainActivity.uriToRoute` when a notification is tapped. */
object NotificationRoutes {
    private const val ACCOUNT = "?account="
    private const val SCROLL_TO = "&scrollTo="

    fun accountNpub(account: Account): String =
        account.signer.pubKey
            .hexToByteArray()
            .toNpub()

    /** Opens the note directly (used for replies, mentions, media, git). */
    fun noteUri(
        note: Note,
        accountNpub: String,
    ): String = note.toNEvent() + ACCOUNT + accountNpub

    /**
     * Opens a NIP-17 / NIP-04 private chatroom (DM notifications).
     *
     * A DM must NOT deep-link through its own note: [Note.toNEvent] cites the kind-1059
     * gift wrap that delivered the rumor (it has to — the rumor id is the private event's
     * identity and resolves to nothing on a public relay, see `RumorHostCitationTest`).
     * Resolving that nevent back to a room needs the wrap *and* seal notes to still be in
     * the in-memory `LocalCache`. A push usually wakes a cold process, so by the time the
     * user taps, the cache is empty — and no relay will ever serve that wrap again, so the
     * tap landed on a `Route.EventRedirect` that could never resolve.
     *
     * The room key is derived locally and needs no event at all, so this survives the
     * process dying. Same idea as [relayGroupUri], which routes a Buzz DM through the
     * channel's naddr rather than the message.
     */
    fun chatroomUri(
        room: ChatroomKey,
        accountNpub: String,
    ): String = "chatroom?id=${room.users.joinToString(",")}&account=$accountNpub"

    /** Opens the Notifications tab, scrolled to [scrollToId] (used for zaps, reactions, chess). */
    fun notificationsUri(
        accountNpub: String,
        scrollToId: String,
    ): String = "notifications$ACCOUNT$accountNpub$SCROLL_TO$scrollToId"

    /**
     * Opens a Marmot group chatroom (welcome + group message).
     *
     * Note the query here rides on an *opaque* URI (a scheme with no `//`), so its
     * `?account=` can only be read by [String.findQueryParameterValue] — see the note
     * there; `java.net.URI.rawQuery` is null for this shape.
     */
    fun marmotUri(
        nostrGroupId: String,
        accountNpub: String,
    ): String = "marmot:$nostrGroupId$ACCOUNT$accountNpub"

    /**
     * Opens a NIP-29 relay-group / Buzz chatroom. [channelNAddr] is the channel's
     * kind-39000 naddr, which `MainActivity.uriToRoute` already routes to
     * `Route.RelayGroup` via the standard nostr-entity path.
     */
    fun relayGroupUri(
        channelNAddr: String,
        accountNpub: String,
    ): String = "$channelNAddr$ACCOUNT$accountNpub"
}

internal fun Context.notificationManager(): NotificationManager = ContextCompat.getSystemService(this, NotificationManager::class.java) as NotificationManager
