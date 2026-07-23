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
package com.vitorpamplona.quartz.buzz.huddles

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.buzz.huddles.reaction.reaction
import com.vitorpamplona.quartz.buzz.huddles.reaction.reactionChannel
import com.vitorpamplona.quartz.buzz.huddles.reaction.reactionCustomEmojis
import com.vitorpamplona.quartz.buzz.huddles.reaction.reactionSenderName
import com.vitorpamplona.quartz.buzz.huddles.reaction.senderName
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz huddle emoji reaction burst (`kind:24810`): a transient emoji animation shown to
 * everyone in an ephemeral huddle channel. Ephemeral (20000–29999) — never stored. The emoji
 * is in `content` and mirrored in a `reaction` tag; the burst is scoped to the huddle channel
 * by an `h` tag, carries a `sender_name` display name, and may carry a NIP-30 `emoji` tag for
 * a custom emoji. Ground truth: `desktop/.../huddle/components/HuddleBar.tsx`.
 */
@Immutable
class HuddleReactionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The ephemeral huddle channel UUID — the `h` tag. */
    fun channelId(): String? = tags.reactionChannel()

    /** The emoji — the `reaction` tag, or the `content` fallback. */
    fun emoji(): String = tags.reaction() ?: content

    /** The sender's display name — the `sender_name` tag. */
    fun senderName(): String? = tags.reactionSenderName()

    /** Any NIP-30 custom-emoji descriptors carried on the burst. */
    fun customEmojis() = tags.reactionCustomEmojis()

    companion object {
        const val KIND = 24810

        fun build(
            channelId: String,
            emoji: String,
            senderName: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<HuddleReactionEvent>.() -> Unit = {},
        ) = eventTemplate<HuddleReactionEvent>(KIND, emoji, createdAt) {
            reactionChannel(channelId)
            reaction(emoji)
            senderName(senderName)
            initializer()
        }
    }
}
