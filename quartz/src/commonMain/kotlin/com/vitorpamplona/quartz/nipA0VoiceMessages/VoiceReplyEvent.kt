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
package com.vitorpamplona.quartz.nipA0VoiceMessages

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip22Comments.tags.RootAuthorTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootEventTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootKindTag
import com.vitorpamplona.quartz.nipA0VoiceMessages.tags.ReplyAuthorTag
import com.vitorpamplona.quartz.nipA0VoiceMessages.tags.ReplyEventTag
import com.vitorpamplona.quartz.nipA0VoiceMessages.tags.ReplyKindTag
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.lastNotNullOfOrNull

@Immutable
class VoiceReplyEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseVoiceEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun replyAuthor() = tags.firstNotNullOfOrNull(ReplyAuthorTag::parse)

    fun replyAuthors() = tags.filter(ReplyAuthorTag::match)

    fun replyAuthorKeys() = tags.mapNotNull(ReplyAuthorTag::parseKey)

    fun replyAuthorHints() = tags.mapNotNull(ReplyAuthorTag::parseAsHint)

    fun directReplies() = tags.filter { ReplyEventTag.match(it) }

    fun directKinds() = tags.filter(ReplyKindTag::match)

    fun markedReplyTos(): List<HexKey> = tags.mapNotNull(ReplyEventTag::parseKey)

    fun replyingTo(): HexKey? = tags.lastNotNullOfOrNull(ReplyEventTag::parseKey)

    /** The thread's root scope (NIP-22 `E`): the voice message the conversation started from. */
    fun rootEventId(): HexKey? = tags.firstNotNullOfOrNull(RootEventTag::parseKey)

    /** The root scope's author (NIP-22 `P`). */
    fun rootAuthorKey(): HexKey? = tags.firstNotNullOfOrNull(RootAuthorTag::parseKey)

    companion object {
        const val KIND = 1244

        fun build(
            url: String,
            mimeType: String?,
            hash: String,
            duration: Int,
            waveform: List<Float>,
            replyingTo: EventHintBundle<BaseVoiceEvent>,
        ) = build(AudioMeta(url, mimeType, hash, duration, waveform), replyingTo)

        fun build(
            voiceMessage: AudioMeta,
            replyingTo: EventHintBundle<BaseVoiceEvent>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<VoiceReplyEvent>.() -> Unit = {},
        ) = build(voiceMessage, KIND, createdAt) {
            // NIP-A0: a voice reply MUST follow NIP-22, so it names the thread's root scope
            // (E / K / P) as well as its parent. Replying to a reply inherits that reply's root;
            // replying to the voice message itself makes it the root.
            val parent = replyingTo.event
            val inherited =
                if (parent is VoiceReplyEvent) {
                    parent.tags.filter { RootEventTag.match(it) || RootKindTag.match(it) || RootAuthorTag.match(it) }
                } else {
                    emptyList()
                }
            if (inherited.any { RootEventTag.match(it) }) {
                inherited.forEach { addUnique(it) }
            } else {
                rootEvent(parent.id, replyingTo.relay, parent.pubKey)
                rootKind(parent.kind)
                rootAuthor(parent.pubKey, replyingTo.authorHomeRelay)
            }
            replyEvent(replyingTo.event.id, replyingTo.relay, replyingTo.event.pubKey)
            replyKind(replyingTo.event.kind)
            replyAuthor(replyingTo.event.pubKey, replyingTo.authorHomeRelay)
            initializer()
        }
    }
}
