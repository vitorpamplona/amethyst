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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull

/**
 * Splitting a cordn message into the parts a screen renders differently.
 *
 * ## Why cordn needs its own, small version of this
 *
 * Amethyst's rich-text rendering is built on `Note`, and a `Note` comes from
 * `LocalCache`. Nothing cordn receives may go in there — a cordn message is an
 * MLS payload that never touched a relay, and giving it a `Note` would make
 * private group chat searchable and notifiable alongside published events.
 * So the parsing happens here, on the envelope's own content, and the result
 * is plain data a composable can walk.
 *
 * Resolving a mentioned pubkey to a **display name** is a different question
 * and a safe one: a profile is public relay data that the cache holds anyway,
 * and reading one puts nothing of the conversation into it. The rule is about
 * what goes in, not what is looked up.
 *
 * ## Scope
 *
 * Mentions only — `nostr:npub1…` and `nostr:nprofile1…`. Not URLs, not
 * hashtags, not embedded events. Each of those renders as something, and
 * something that renders is something that can leak: an auto-loaded preview
 * would fetch a URL the moment a private message arrived, telling its host
 * that a specific person opened a specific message. That belongs with the
 * media work, behind the same decisions, not smuggled in with mentions.
 */
object CordnMentions {
    /** One run of a message: either text to print, or a person to name. */
    sealed interface Segment {
        data class Text(
            val value: String,
        ) : Segment

        data class Mention(
            val pubKey: HexKey,
            /** The exact text matched, for a renderer that wants to fall back to it. */
            val raw: String,
        ) : Segment
    }

    /**
     * Splits [content] into text and mention runs, in order.
     *
     * Concatenating every segment's original text reproduces [content] exactly
     * — there is a test for that. A renderer that drops a segment type would
     * otherwise silently eat part of someone's message, which is worse than
     * not rendering mentions at all.
     */
    fun segment(content: String): List<Segment> {
        if (content.isEmpty()) return emptyList()

        val out = mutableListOf<Segment>()
        var last = 0

        MENTION.findAll(content).forEach { match ->
            val pubKey = decodePublicKeyAsHexOrNull(match.value.removePrefix(NOSTR_PREFIX)) ?: return@forEach

            if (match.range.first > last) {
                out += Segment.Text(content.substring(last, match.range.first))
            }
            out += Segment.Mention(pubKey, match.value)
            last = match.range.last + 1
        }

        if (last < content.length) out += Segment.Text(content.substring(last))
        return out
    }

    /** Every account mentioned in [content], deduplicated, in first-seen order. */
    fun mentioned(content: String): List<HexKey> =
        segment(content)
            .filterIsInstance<Segment.Mention>()
            .map { it.pubKey }
            .distinct()

    private const val NOSTR_PREFIX = "nostr:"

    /**
     * `nostr:` is required, unlike NIP-19's looser scan.
     *
     * A bare `npub1…` in the middle of a sentence is as likely to be someone
     * quoting a key as mentioning a person, and turning it into a name changes
     * what they wrote. The bech32 alphabet excludes `1`, `b`, `i` and `o`,
     * which is what keeps the match from running past the entity into ordinary
     * words.
     */
    private val MENTION = Regex("nostr:(npub1|nprofile1)[qpzry9x8gf2tvdw0s3jn54khce6mua7l]+")
}
