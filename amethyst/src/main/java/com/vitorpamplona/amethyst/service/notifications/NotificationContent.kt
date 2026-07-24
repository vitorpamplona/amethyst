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

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoEvent

/**
 * Content-extraction helpers shared by the per-kind notification renderers:
 * decrypting private payloads, pulling a clean one-line excerpt, and resolving
 * the media URL to show as a notification's big picture.
 */
object NotificationContent {
    /** First non-blank line of [content], trimmed to [max] chars, or "" if none. */
    fun excerpt(
        content: String?,
        max: Int = 280,
    ): String =
        content
            ?.split("\n")
            ?.firstOrNull { it.isNotBlank() }
            ?.take(max)
            ?: ""

    /**
     * The result of [resolveMentions]: the excerpt with every `nostr:npub` /
     * `nostr:nprofile` token swapped for the cited user's `@DisplayName`, plus the
     * [User]s that were cited so a renderer can add them to its enrichment window
     * and re-render as their metadata loads.
     */
    data class ResolvedText(
        val text: String,
        val citedUsers: List<User>,
    )

    /**
     * Like [excerpt], but rewrites inline user mentions to readable names: each
     * `nostr:npub1…` / `nostr:nprofile1…` (optionally `@`-prefixed) becomes
     * `@<best display name>` for the cited user, and that user is returned in
     * [ResolvedText.citedUsers]. Event/address references (`nevent`, `note`,
     * `naddr`, …) are left untouched — they aren't people and have no name to show.
     *
     * Called from the build closure on every re-render, so as a cited user's
     * kind:0 arrives the notification text updates in place from `@npub1abc…` to
     * `@RealName`.
     */
    fun resolveMentions(
        content: String?,
        max: Int = 280,
    ): ResolvedText {
        val line =
            content
                ?.split("\n")
                ?.firstOrNull { it.isNotBlank() }
                ?: return ResolvedText("", emptyList())

        // Cheap opt-out: no mention tokens means no work and no allocations.
        if (!line.contains("npub1", ignoreCase = true) && !line.contains("nprofile1", ignoreCase = true)) {
            return ResolvedText(line.take(max), emptyList())
        }

        val cited = mutableListOf<User>()
        val rewritten =
            Nip19Parser.nip19regex.replace(line) { match ->
                val type = match.groups[3]?.value ?: match.groups[5]?.value
                val key = match.groups[4]?.value ?: match.groups[6]?.value
                val trailing = match.groups[7]?.value ?: ""

                val hex =
                    when (val entity = Nip19Parser.parseComponents(type ?: "", key, null)?.entity) {
                        is NPub -> entity.hex
                        is NProfile -> entity.hex
                        else -> null
                    }

                if (hex != null) {
                    val user = LocalCache.getOrCreateUser(hex)
                    cited.add(user)
                    "@${user.toBestDisplayName()}$trailing"
                } else {
                    // nevent / note / naddr / parse failure — leave as-is.
                    match.value
                }
            }

        return ResolvedText(rewritten.take(max), cited)
    }

    suspend fun decryptZapContentAuthor(
        event: LnZapRequestEvent,
        signer: NostrSigner,
    ): Event? =
        if (event.isPrivateZap() && event.zappedAuthor().contains(event.pubKey)) {
            signer.decryptZapEvent(event)
        } else {
            event
        }

    suspend fun decryptContent(
        note: Note,
        signer: NostrSigner,
    ): String? =
        when (val event = note.event) {
            is PrivateDmEvent -> event.decryptContent(signer)
            is LnZapRequestEvent -> decryptZapContentAuthor(event, signer)?.content
            else -> event?.content
        }

    /**
     * The primary image/thumbnail URL to render as a notification's big picture,
     * or null if the event carries no displayable media. Pictures use their first
     * imeta url; videos prefer the poster-frame `image`, falling back to the video
     * url only when no poster is present.
     */
    fun mediaImageUrl(event: Event?): String? =
        when (event) {
            is PictureEvent -> event.imetaTags().firstOrNull()?.url
            is VideoEvent -> {
                val meta = event.imetaTags().firstOrNull()
                meta?.image?.firstOrNull() ?: meta?.url
            }
            else -> null
        }
}
