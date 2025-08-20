/**
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

    companion object {
        const val KIND = 1244
        const val ALT_DESCRIPTION = "Voice reply"

        fun build(
            url: String,
            mimeType: String?,
            hash: String,
            duration: Int,
            waveform: List<Float>,
            replyingTo: EventHintBundle<VoiceEvent>,
        ) = build(AudioMeta(url, mimeType, hash, duration, waveform), replyingTo)

        fun build(
            voiceMessage: AudioMeta,
            replyingTo: EventHintBundle<VoiceEvent>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<VoiceReplyEvent>.() -> Unit = {},
        ) = build(voiceMessage, KIND, ALT_DESCRIPTION, createdAt) {
            replyEvent(replyingTo.event.id, replyingTo.relay, replyingTo.event.pubKey)
            replyKind(replyingTo.event.kind)
            replyAuthor(replyingTo.event.pubKey, replyingTo.authorHomeRelay)
            initializer()
        }
    }
}
