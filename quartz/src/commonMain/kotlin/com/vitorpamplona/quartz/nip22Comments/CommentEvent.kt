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
package com.vitorpamplona.quartz.nip22Comments

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.any
import com.vitorpamplona.quartz.nip01Core.core.fastAny
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkProvider
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.contentMentions
import com.vitorpamplona.quartz.nip01Core.links.each
import com.vitorpamplona.quartz.nip01Core.links.hashtags
import com.vitorpamplona.quartz.nip01Core.links.links
import com.vitorpamplona.quartz.nip01Core.links.quotes
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHashTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip18Reposts.quotes.QTag
import com.vitorpamplona.quartz.nip19Bech32.addressHints
import com.vitorpamplona.quartz.nip19Bech32.addressIds
import com.vitorpamplona.quartz.nip19Bech32.eventHints
import com.vitorpamplona.quartz.nip19Bech32.eventIds
import com.vitorpamplona.quartz.nip19Bech32.pubKeyHints
import com.vitorpamplona.quartz.nip19Bech32.pubKeys
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
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
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
    AddressHintProvider,
    SearchableEvent,
    LinkProvider {
    override fun indexableContent() = (listOf(content) + tags.hashtags()).joinToString("\n")

    // The read path: the same fields indexableContent() joins, without the join.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(content)) return
        tags.hashtags().forEach { if (!visitor.visit(it)) return }
    }

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

    fun rootEventIds() = tags.mapNotNull(RootEventTag::parseKey)

    fun replyEventIds() = tags.mapNotNull(ReplyEventTag::parseKey)

    fun rootAddressIds() = tags.mapNotNull(RootAddressTag::parseAddressId)

    fun hasRootAddress(addressId: String) = tags.any(RootAddressTag::isTagged, addressId)

    fun hasReplyAddress(addressId: String) = tags.any(ReplyAddressTag::isTagged, addressId)

    fun replyAddressIds() = tags.mapNotNull(ReplyAddressTag::parseAddressId)

    fun replyAddress() = tags.mapNotNull(ReplyAddressTag::parseAddress)

    fun rootScopes() = tags.filter { RootIdentifierTag.match(it) || RootAddressTag.match(it) || RootEventTag.match(it) }

    fun rootKinds() = tags.filter(RootKindTag::match)

    fun directReplies() = tags.filter { ReplyIdentifierTag.match(it) || ReplyAddressTag.match(it) || ReplyEventTag.match(it) }

    /** Whether a parent is named at all, without materialising [directReplies]. */
    fun hasDirectReplies() = tags.fastAny { ReplyIdentifierTag.match(it) || ReplyAddressTag.match(it) || ReplyEventTag.match(it) }

    fun directKinds() = tags.filter(ReplyKindTag::match)

    /** Whether a parent kind (`k`) is declared at all, without materialising [directKinds]. */
    fun hasDirectKinds() = tags.fastAny(ReplyKindTag::match)

    fun rootAuthor() = tags.firstNotNullOfOrNull(RootAuthorTag::parse)

    fun replyAuthor() = tags.firstNotNullOfOrNull(ReplyAuthorTag::parse)

    fun rootAuthors() = tags.filter(RootAuthorTag::match)

    fun replyAuthors() = tags.filter(ReplyAuthorTag::match)

    fun rootAuthorKeys() = tags.mapNotNull(RootAuthorTag::parseKey)

    fun replyAuthorKeys() = tags.mapNotNull(ReplyAuthorTag::parseKey)

    fun rootAuthorHints() = tags.mapNotNull(RootAuthorTag::parseAsHint)

    fun replyAuthorHints() = tags.mapNotNull(ReplyAuthorTag::parseAsHint)

    /** root and reply scope search */
    fun isTaggedScope(scopeId: String) = tags.any { RootIdentifierTag.isTagged(it, scopeId) || ReplyIdentifierTag.isTagged(it, scopeId) }

    fun isTaggedScopes(scopeIds: Set<String>) = tags.any { RootIdentifierTag.isTagged(it, scopeIds) || ReplyIdentifierTag.isTagged(it, scopeIds) }

    fun isTaggedScope(
        value: String,
        match: (String, String) -> Boolean,
    ) = tags.any { RootIdentifierTag.isTagged(it, value, match) || ReplyIdentifierTag.isTagged(it, value, match) }

    fun firstTaggedScopeIn(scopeIds: Set<String>) = tags.firstNotNullOfOrNull { RootIdentifierTag.matchOrNull(it, scopeIds) ?: ReplyIdentifierTag.matchOrNull(it, scopeIds) }

    fun isScoped(scopeTest: (String) -> Boolean) = tags.any { RootIdentifierTag.isTagged(it, scopeTest) || ReplyIdentifierTag.isTagged(it, scopeTest) }

    /** True when the comment points at an external identifier (`I` tag), e.g. a hashtag, geohash or url. */
    fun hasRootScopeIdentifier() = tags.any { RootIdentifierTag.match(it) }

    fun hasRootScopeKind(kind: String) = tags.any(RootKindTag::isKind, kind)

    fun hasReplyScopeKind(kind: String) = tags.any(ReplyKindTag::isKind, kind)

    fun hasScopeKind(kind: String) = tags.any { RootKindTag.isKind(it, kind) || ReplyKindTag.isKind(it, kind) }

    fun scopeValues(parser: (String) -> String?) = tags.mapNotNull { RootIdentifierTag.parse(it)?.let { parser(it) } }

    fun firstScopeValue(parser: (String) -> String?) = tags.firstNotNullOfOrNull { RootIdentifierTag.parse(it)?.let { parser(it) } }

    override fun markedReplyTos(): List<HexKey> =
        tags.mapNotNull(ReplyEventTag::parseKey) +
            tags.mapNotNull(RootEventTag::parseKey)

    override fun unmarkedReplyTos() = emptyList<String>()

    /**
     * NIP-22 addresses two distinct recipients: the direct-reply author
     * (lowercase `p` via [ReplyAuthorTag]) and the root-scope author
     * (uppercase `P` via [RootAuthorTag]). A comment several levels deep
     * only tags the root author with uppercase `P`, so the base-class
     * lowercase-only default would miss them.
     */
    override fun notifies(userHex: HexKey): Boolean = super.notifies(userHex) || rootAuthorKeys().contains(userHex)

    override fun replyingTo(): HexKey? =
        tags.lastNotNullOfOrNull(ReplyEventTag::parseKey)
            ?: tags.lastNotNullOfOrNull(RootEventTag::parseKey)

    fun rootAddress() = tags.mapNotNull(RootAddressTag::parseAddress)

    /**
     * The authors the parent tags themselves name: the pubkey slot of an `e` ([ReplyEventTag]),
     * the coordinate's pubkey of an `a` ([ReplyAddressTag]). Only a `p` among these is the
     * parent's author; NIP-22 also adds a `p` for every pubkey the content mentions.
     */
    fun parentTagAuthors(): Set<HexKey> {
        val authors = HashSet<HexKey>()
        tags.fastForEach { tag ->
            ReplyEventTag.parse(tag)?.author?.let { authors.add(it) }
            ReplyAddressTag.parseAddress(tag)?.let { authors.add(it.pubKeyHex) }
        }
        return authors
    }

    fun rootAddressId() = tags.mapNotNull(RootAddressTag::parseAddressId)

    fun replyingToAddressId(): String? =
        tags.lastNotNullOfOrNull(ReplyAddressTag::parseAddressId)
            ?: tags.lastNotNullOfOrNull(RootAddressTag::parseAddressId)

    override fun replyingToAddressOrEvent(): HexKey? = replyingToAddressId() ?: replyingTo()

    override fun tagsWithoutCitations(): List<String> {
        val rootAddress = rootAddressIds()
        val replyAddress = replyAddressIds()

        if (
            rootAddress.any { Address.isOfKind(it, CommunityDefinitionEvent.KIND_STR) } &&
            replyAddress.any { Address.isOfKind(it, CommunityDefinitionEvent.KIND_STR) }
        ) {
            // this is the root of the community post
            return emptyList()
        }

        return rootAddress + replyAddress + rootEventIds() + replyEventIds()
    }

    /**
     * NIP-22: the uppercase tags are the root scope (`E`/`A`/`I` → `ROOT`, `P` → `ROOT_AUTHOR`),
     * the lowercase ones the parent item (`e`/`a`/`i` → `PARENT`). An external-identifier scope
     * (`I`/`i`: a URL, a hashtag, a geohash) is the NIP-73 id it names, one node whichever case
     * named it. A lowercase `p` is the `PARENT_AUTHOR` only when it is the author the parent tag
     * itself names (the `e`'s pubkey slot, or an `a`'s coordinate): NIP-22 also asks for a `p` per
     * pubkey mentioned in the content, and those are `MENTION`s. An `A` root at a NIP-72 community
     * is also the `COMMUNITY` the comment is posted in.
     */
    override fun links(): List<Link<*>> =
        links {
            // An external id is one node whichever case named it: the target is always an `i`.
            each(tags, RootEventTag::parse) { event(Relation.ROOT, it, RootEventTag.TAG_NAME) }
            each(tags, RootAddressTag::parse) {
                address(Relation.ROOT, it, RootAddressTag.TAG_NAME)
                if (Address.isOfKind(it.addressId, CommunityDefinitionEvent.KIND_STR)) address(Relation.COMMUNITY, it, RootAddressTag.TAG_NAME)
            }
            each(tags, RootIdentifierTag.Companion::parse) { tag(Relation.ROOT, ReplyIdentifierTag.TAG_NAME, it, RootIdentifierTag.TAG_NAME) }
            each(tags, RootKindTag::parse) { tag(Relation.TAG, ReplyKindTag.TAG_NAME, it, RootKindTag.TAG_NAME) }
            each(tags, RootAuthorTag::parse) { user(Relation.ROOT_AUTHOR, it, RootAuthorTag.TAG_NAME) }

            each(tags, ReplyEventTag::parse) { event(Relation.PARENT, it, ReplyEventTag.TAG_NAME) }
            each(tags, ReplyAddressTag::parse) { address(Relation.PARENT, it, ReplyAddressTag.TAG_NAME) }
            each(tags, ReplyIdentifierTag::parse) { tag(Relation.PARENT, ReplyIdentifierTag.TAG_NAME, it) }
            each(tags, ReplyKindTag::parse) { tag(Relation.TAG, ReplyKindTag.TAG_NAME, it) }

            val parentAuthors = parentTagAuthors()
            each(tags, ReplyAuthorTag::parse) { user(if (it.pubKey in parentAuthors) Relation.PARENT_AUTHOR else Relation.MENTION, it, ReplyAuthorTag.TAG_NAME) }

            quotes(tags)
            hashtags(tags)

            contentMentions(citedNIP19())
        }

    companion object {
        const val KIND = 1111

        fun replyBuilder(
            msg: String,
            replyingTo: EventHintBundle<Event>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<CommentEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, msg, createdAt) {
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
