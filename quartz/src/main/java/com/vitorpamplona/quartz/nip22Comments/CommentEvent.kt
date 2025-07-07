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
package com.vitorpamplona.quartz.nip22Comments

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag.Companion.parseAsHint
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHashTag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag.Companion.parseAsHint
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag.Companion.parseAsHint
import com.vitorpamplona.quartz.nip18Reposts.quotes.QTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.QTag.Companion.parseAddressAsHint
import com.vitorpamplona.quartz.nip18Reposts.quotes.QTag.Companion.parseEventAsHint
import com.vitorpamplona.quartz.nip19Bech32.addressHints
import com.vitorpamplona.quartz.nip19Bech32.addressIds
import com.vitorpamplona.quartz.nip19Bech32.eventHints
import com.vitorpamplona.quartz.nip19Bech32.eventIds
import com.vitorpamplona.quartz.nip19Bech32.pubKeyHints
import com.vitorpamplona.quartz.nip19Bech32.pubKeys
import com.vitorpamplona.quartz.nip21UriScheme.toNostrUri
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyAddressTag
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyAuthorTag
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyEventTag
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyIdentifierTag
import com.vitorpamplona.quartz.nip22Comments.tags.ReplyKindTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootAddressTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootAuthorTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootEventTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootIdentifierTag
import com.vitorpamplona.quartz.nip22Comments.tags.RootKindTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip73ExternalIds.ExternalId
import com.vitorpamplona.quartz.nip73ExternalIds.location.GeohashId
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.lastNotNullOfOrNull

@Immutable
class CommentEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseThreadedEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    RootScope,
    EventHintProvider,
    PubKeyHintProvider,
    AddressHintProvider {
    override fun pubKeyHints(): List<PubKeyHint> {
        val pHints =
            tags.mapNotNull(RootAuthorTag::parseAsHint) +
                tags.mapNotNull(ReplyAuthorTag::parseAsHint)
        val nip19Hints = citedNIP19().pubKeyHints()

        return pHints + nip19Hints
    }

    override fun linkedPubKeys(): List<HexKey> {
        val pHints =
            tags.mapNotNull(RootAuthorTag::parseKey) +
                tags.mapNotNull(ReplyAuthorTag::parseKey)
        val nip19Hints = citedNIP19().pubKeys()

        return pHints + nip19Hints
    }

    override fun eventHints(): List<EventIdHint> {
        val eHints = tags.mapNotNull(RootEventTag::parseAsHint) + tags.mapNotNull(ReplyEventTag::parseAsHint)
        val qHints = tags.mapNotNull(QTag::parseEventAsHint)
        val nip19Hints = citedNIP19().eventHints()

        return eHints + qHints + nip19Hints
    }

    override fun linkedEventIds(): List<HexKey> {
        val eHints = tags.mapNotNull(RootEventTag::parseKey) + tags.mapNotNull(ReplyEventTag::parseKey)
        val qHints = tags.mapNotNull(QTag::parseEventId)
        val nip19Hints = citedNIP19().eventIds()

        return eHints + qHints + nip19Hints
    }

    override fun addressHints(): List<AddressHint> {
        val aHints = tags.mapNotNull(RootAddressTag::parseAsHint) + tags.mapNotNull(ReplyAddressTag::parseAsHint)
        val qHints = tags.mapNotNull(QTag::parseAddressAsHint)
        val nip19Hints = citedNIP19().addressHints()

        return aHints + qHints + nip19Hints
    }

    override fun linkedAddressIds(): List<String> {
        val aHints = tags.mapNotNull(RootAddressTag::parseAddressId) + tags.mapNotNull(ReplyAddressTag::parseAddressId)
        val qHints = tags.mapNotNull(QTag::parseAddressId)
        val nip19Hints = citedNIP19().addressIds()

        return aHints + qHints + nip19Hints
    }

    fun rootAuthor() = tags.firstNotNullOfOrNull(RootAuthorTag::parse)

    fun replyAuthor() = tags.firstNotNullOfOrNull(ReplyAuthorTag::parse)

    fun rootAuthors() = tags.filter(RootAuthorTag::match)

    fun replyAuthors() = tags.filter(ReplyAuthorTag::match)

    fun rootAuthorKeys() = tags.mapNotNull(RootAuthorTag::parseKey)

    fun replyAuthorKeys() = tags.mapNotNull(ReplyAuthorTag::parseKey)

    fun rootAuthorHints() = tags.mapNotNull(RootAuthorTag::parseAsHint)

    fun replyAuthorHints() = tags.mapNotNull(ReplyAuthorTag::parseAsHint)

    fun rootScopes() = tags.filter { RootIdentifierTag.match(it) || RootAddressTag.match(it) || RootEventTag.match(it) }

    fun rootKinds() = tags.filter(RootKindTag::match)

    fun directReplies() = tags.filter { ReplyIdentifierTag.match(it) || ReplyAddressTag.match(it) || ReplyEventTag.match(it) }

    fun directKinds() = tags.filter(ReplyKindTag::match)

    /** root and reply scope search */
    fun isTaggedScope(scopeId: String) = tags.any { RootIdentifierTag.isTagged(it, scopeId) || ReplyIdentifierTag.isTagged(it, scopeId) }

    fun isTaggedScopes(scopeIds: Set<String>) = tags.any { RootIdentifierTag.isTagged(it, scopeIds) || ReplyIdentifierTag.isTagged(it, scopeIds) }

    fun isTaggedScope(
        value: String,
        match: (String, String) -> Boolean,
    ) = tags.any { RootIdentifierTag.isTagged(it, value, match) || ReplyIdentifierTag.isTagged(it, value, match) }

    fun firstTaggedScopeIn(scopeIds: Set<String>) = tags.firstNotNullOfOrNull { RootIdentifierTag.matchOrNull(it, scopeIds) ?: ReplyIdentifierTag.matchOrNull(it, scopeIds) }

    fun isScoped(scopeTest: (String) -> Boolean) = tags.any { RootIdentifierTag.isTagged(it, scopeTest) || ReplyIdentifierTag.isTagged(it, scopeTest) }

    fun hasRootScopeKind(kind: String) = tags.any { RootKindTag.isKind(it, kind) }

    fun hasReplyScopeKind(kind: String) = tags.any { ReplyKindTag.isKind(it, kind) }

    fun hasScopeKind(kind: String) = tags.any { RootKindTag.isKind(it, kind) || ReplyKindTag.isKind(it, kind) }

    fun scopeValues(parser: (String) -> String?) = tags.mapNotNull { RootIdentifierTag.parse(it)?.let { parser(it) } }

    fun firstScopeValue(parser: (String) -> String?) = tags.firstNotNullOfOrNull { RootIdentifierTag.parse(it)?.let { parser(it) } }

    override fun markedReplyTos(): List<HexKey> =
        tags.mapNotNull(ReplyEventTag::parseKey) +
            tags.mapNotNull(RootEventTag::parseKey)

    override fun unmarkedReplyTos() = emptyList<String>()

    override fun replyingTo(): HexKey? =
        tags.lastNotNullOfOrNull(ReplyEventTag::parseKey)
            ?: tags.lastNotNullOfOrNull(RootEventTag::parseKey)

    fun replyingToAddressId(): String? =
        tags.lastNotNullOfOrNull(RootAddressTag::parseAddressId)
            ?: tags.lastNotNullOfOrNull(ReplyAddressTag::parseAddressId)

    override fun replyingToAddressOrEvent(): HexKey? = replyingToAddressId() ?: replyingTo()

    companion object {
        const val KIND = 1111
        const val ALT = "Reply to "

        fun replyBuilder(
            msg: String,
            replyingTo: EventHintBundle<Event>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<CommentEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, msg, createdAt) {
            alt(ALT + replyingTo.toNostrUri())

            if (replyingTo.event is CommentEvent) {
                addAll(replyingTo.event.rootScopes())
                addAll(replyingTo.event.rootKinds())
                addAll(replyingTo.event.rootAuthors())
            } else {
                if (replyingTo.event is AddressableEvent) {
                    rootAddress(replyingTo.event.addressTag(), replyingTo.relay)
                    replyAddress(replyingTo.event.addressTag(), replyingTo.relay)
                }

                rootEvent(replyingTo.event.id, replyingTo.relay, replyingTo.event.pubKey)
                rootKind(replyingTo.event.kind)
                rootAuthor(replyingTo.event.pubKey, replyingTo.authorHomeRelay)
            }

            replyEvent(replyingTo.event.id, replyingTo.relay, replyingTo.event.pubKey)
            replyKind(replyingTo.event.kind)
            replyAuthor(replyingTo.event.pubKey, replyingTo.authorHomeRelay)

            initializer()
        }

        fun replyExternalIdentity(
            msg: String,
            extId: ExternalId,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<CommentEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, msg, createdAt) {
            alt(ALT + extId.toScope())

            if (extId is GeohashId) {
                GeoHashTag.geoMipMap(extId.geohash).forEach { rootExternalIdentity(GeohashId(it, extId.hint)) }
            } else {
                rootExternalIdentity(extId)
            }
            rootKind(extId)

            if (extId is GeohashId) {
                GeoHashTag.geoMipMap(extId.geohash).forEach { replyExternalIdentity(GeohashId(it, extId.hint)) }
            } else {
                replyExternalIdentity(extId)
            }
            replyKind(extId)

            initializer()
        }
    }
}
