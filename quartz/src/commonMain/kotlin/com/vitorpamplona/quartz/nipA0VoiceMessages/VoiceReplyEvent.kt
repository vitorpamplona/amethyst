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
import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkProvider
import com.vitorpamplona.quartz.graph.Relation
import com.vitorpamplona.quartz.graph.each
import com.vitorpamplona.quartz.graph.links
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyAddressTag
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyIdentifierTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootAddressTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootAuthorTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootEventTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootIdentifierTag
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
) : BaseVoiceEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    LinkProvider {
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

    /** Whether the reply names its NIP-22 root scope (`E`, `A` or `I`), not only its parent. */
    fun hasRootScope() = tags.any { RootEventTag.match(it) || RootAddressTag.match(it) || RootIdentifierTag.match(it) }

    /** Whether the parent is a voice message (`k` 1222): then a reply without a root scope replies to the root. */
    fun repliesToVoiceMessage() = tags.any { ReplyKindTag.isKind(it, VoiceEvent.KIND.toString()) }

    /**
     * The root-scope tags a reply to this event inherits, or null when this event names no root.
     *
     * A NIP-22 reply carries them verbatim: `E`, `A` or `I` (a thread may be rooted at an
     * address or an external id, not only at an event) plus `K` and `P`. A reply published
     * before voice replies followed NIP-22 has only the lowercase parent tags; when that parent
     * is a voice message (`k` 1222) the parent IS the root, so its `e` / `p` are rewritten as
     * `E` / `P` (identical layouts). An older reply to a reply has lost its root: null.
     */
    fun rootScopeTags(): List<Array<String>>? {
        if (hasRootScope()) {
            return tags.filter { RootEventTag.match(it) || RootAddressTag.match(it) || RootIdentifierTag.match(it) || RootKindTag.match(it) || RootAuthorTag.match(it) }
        }

        if (!repliesToVoiceMessage()) return null
        val parentTag = tags.lastOrNull { ReplyEventTag.parseKey(it) != null } ?: return null
        val authorTag = tags.lastOrNull { ReplyAuthorTag.parseKey(it) != null }
        return listOfNotNull(
            parentTag.copyOf().also { it[0] = RootEventTag.TAG_NAME },
            RootKindTag.assemble(VoiceEvent.KIND),
            authorTag?.copyOf()?.also { it[0] = RootAuthorTag.TAG_NAME },
        )
    }

    /**
     * NIP-A0 replies follow NIP-22: the root scope (`E`/`A`/`I`, `K`, `P`) and the parent item
     * (`e`/`a`/`i`, `k`, `p`). A reply written before that carries only the parent; [rootScopeTags]
     * recovers its root when the parent is itself a voice message, and those links keep the name of
     * the tag they were read from.
     */
    override fun links(): List<Link<*>> =
        links {
            if (hasRootScope()) {
                each(tags, RootEventTag::parseKey) { event(Relation.ROOT, it, RootEventTag.TAG_NAME) }
                each(tags, RootAddressTag::parseAddressId) { address(Relation.ROOT, it, RootAddressTag.TAG_NAME) }
                each(tags, RootIdentifierTag.Companion::parse) { tag(Relation.ROOT, RootIdentifierTag.TAG_NAME, it) }
                each(tags, RootKindTag::parse) { tag(Relation.TAG, RootKindTag.TAG_NAME, it) }
                each(tags, RootAuthorTag::parseKey) { user(Relation.ROOT_AUTHOR, it, RootAuthorTag.TAG_NAME) }
            } else if (repliesToVoiceMessage()) {
                // An older reply to a voice message: its parent is also its root (see rootScopeTags).
                event(Relation.ROOT, replyingTo(), ReplyEventTag.TAG_NAME)
                user(Relation.ROOT_AUTHOR, tags.lastNotNullOfOrNull(ReplyAuthorTag::parseKey), ReplyAuthorTag.TAG_NAME)
            }
            each(tags, ReplyEventTag::parseKey) { event(Relation.PARENT, it, ReplyEventTag.TAG_NAME) }
            each(tags, ReplyAddressTag::parseAddressId) { address(Relation.PARENT, it, ReplyAddressTag.TAG_NAME) }
            each(tags, ReplyIdentifierTag::parse) { tag(Relation.PARENT, ReplyIdentifierTag.TAG_NAME, it) }
            each(tags, ReplyKindTag::parse) { tag(Relation.TAG, ReplyKindTag.TAG_NAME, it) }
            each(tags, ReplyAuthorTag::parseKey) { user(Relation.PARENT_AUTHOR, it, ReplyAuthorTag.TAG_NAME) }
        }

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
            // NIP-A0: a voice reply MUST follow NIP-22, so it names the thread's root scope as
            // well as its parent. Replying to the voice message itself makes it the root.
            val parent = replyingTo.event
            val inherited = if (parent is VoiceReplyEvent) parent.rootScopeTags() else null
            if (inherited != null) {
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
