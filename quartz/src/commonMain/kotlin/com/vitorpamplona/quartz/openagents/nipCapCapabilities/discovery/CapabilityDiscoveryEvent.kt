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
package com.vitorpamplona.quartz.openagents.nipCapCapabilities.discovery

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.openagents.core.DefinitionRef
import com.vitorpamplona.quartz.openagents.core.OaIds
import com.vitorpamplona.quartz.openagents.core.OaMarkerTag
import com.vitorpamplona.quartz.openagents.core.QualifiedId
import com.vitorpamplona.quartz.openagents.core.hasOaMarker
import com.vitorpamplona.quartz.openagents.core.oaMarkersWith
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException

/**
 * OpenAgents NIP-CAP capability discovery head, kind 30180: a publisher's public description of
 * operations it serves, addressed by `(publisher, 30180, slug)`.
 *
 * ```json
 * {"v":1,"requires":["oa-x402-v1"],"id":"<publisher>:x402/echo","profile":"adapter",
 *  "summary":"echo bytes over Nostr","input":{…},"output":{…},"effects":{…},"minimum":{},
 *  "support":{…},"binding_contract":{"interface":"openagents.x402.native.v1","transport":"nostr-cj",
 *  "operations":["echo"],"remote":{"worker":"<pubkey>","relays":["wss://relay.openagents.com"]}}}
 * ```
 * tags `[["d","echo"],["t","oa:cap:v1"],["t","oa:profile:adapter"],["t","oa:transport:nostr-cj"]]`,
 * plus `oa:test` / `oa:dev` on listings a catalog hides by default.
 *
 * A head is discovery, not authority: it grants nothing, and resolving it is not admitting it.
 *
 * Searchable by its `summary` (the selector text the publisher wrote for it), the qualified id's
 * `package/component` path, the operation ids and the interface id — the words a person or an
 * agent looks a capability up by. The schemas, effects and payment terms are machine contract and
 * stay out, as do keys.
 *
 * Hints: a `nostr-cj` worker is a pubkey with the relays it is reached on, so each one becomes a
 * [PubKeyHint] — the relays a client must use to send it a job.
 */
@Immutable
class CapabilityDiscoveryEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    PubKeyHintProvider,
    EventHintProvider,
    SearchableEvent {
    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    private var cachedDefinition: CapabilityDefinition? = null

    /** The parsed body, or null when the content is not a JSON definition. */
    fun capability(): CapabilityDefinition? {
        cachedDefinition?.let { return it }
        if (!content.startsWith("{")) return null
        return try {
            CapabilityDefinition.parse(content).also { cachedDefinition = it }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Content parse error on $id", e)
            null
        }
    }

    fun isHead() = capability()?.isHead() == true

    fun qualifiedId(): QualifiedId? = capability()?.qualifiedId()

    fun summary(): String? = capability()?.summary?.takeIf { it.isNotBlank() }

    fun operations(): List<String> =
        capability()?.bindingContract?.let { contract ->
            listOfNotNull(contract.operation) + contract.operations.orEmpty()
        } ?: emptyList()

    fun interfaceId(): String? = capability()?.bindingContract?.interfaceId?.takeIf { it.isNotBlank() }

    /** The profile the tags declare; a head's body carries none, so the tag is the only place a reader finds it. */
    fun profile(): String? = tags.oaMarkersWith(PROFILE_PREFIX).firstOrNull()?.substring(PROFILE_PREFIX.length)

    fun transports(): List<String> = tags.oaMarkersWith(TRANSPORT_PREFIX).map { it.substring(TRANSPORT_PREFIX.length) }

    fun isTest() = tags.hasOaMarker(TEST_MARKER)

    fun isDev() = tags.hasOaMarker(DEV_MARKER)

    /**
     * Whether the tags agree with the body the way NIP-CAP requires: exactly one `oa:cap:v1` and
     * one `oa:profile:` marker, and for an inline definition the profile equal to the body's and
     * the transports equal to the body's — exactly one, or one per distinct lane for a service. A
     * head's profile and transports live in the definition it references, which is not here.
     */
    fun isConsistent(): Boolean {
        if (tags.oaMarkersWith(MARKER).size != 1) return false
        if (tags.oaMarkersWith(PROFILE_PREFIX).size != 1) return false
        val body = capability() ?: return false
        if (body.v != 1 || !OaIds.isSlug(dTag())) return false
        val transports = transports()
        if (transports.size != transports.distinct().size) return false
        if (body.isHead()) return body.definition?.let { QualifiedId.parse(it.id) != null && it.artifact.isWellFormed() } == true
        if (profile() != body.profile || body.summary.isNullOrEmpty() || body.qualifiedId() == null) return false
        return transports.sorted() == body.transports().sorted() && transports.isNotEmpty()
    }

    override fun indexableContent() =
        listOfNotNull(
            summary(),
            qualifiedId()?.path(),
            operations().joinToString(" ").ifEmpty { null },
            interfaceId(),
        ).joinToString("\n")

    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(summary())) return
        if (!visitor.visit(qualifiedId()?.path())) return
        if (!visitor.visit(operations().joinToString(" ").ifEmpty { null })) return
        visitor.visit(interfaceId())
    }

    override fun pubKeyHints(): List<PubKeyHint> =
        capability()?.workers().orEmpty().flatMap { (worker, relays) ->
            if (!isWorkerKey(worker)) return@flatMap emptyList()
            relays.mapNotNull { url -> RelayUrlNormalizer.normalizeHintOrNull(url)?.let { PubKeyHint(worker, it) } }
        }

    override fun linkedPubKeys(): List<HexKey> =
        capability()
            ?.workers()
            .orEmpty()
            .map { it.first }
            .filter(::isWorkerKey)
            .distinct()

    // A head's definition may name the publisher's signed declaration of it; there is no relay,
    // so the relay this head came from stands in.
    override fun eventHints(): List<EventIdHint> = emptyList()

    override fun linkedEventIds(): List<HexKey> =
        listOfNotNull(
            capability()
                ?.definition
                ?.event
                ?.takeIf { it.isWellFormed() }
                ?.id,
        )

    private fun isWorkerKey(key: String) = key.length == 64 && Hex.isHex64(key)

    companion object {
        const val KIND = 30180
        const val ALT = "OpenAgents capability"

        const val MARKER = "oa:cap:v1"
        const val PROFILE_PREFIX = "oa:profile:"
        const val TRANSPORT_PREFIX = "oa:transport:"
        const val TEST_MARKER = "oa:test"
        const val DEV_MARKER = "oa:dev"

        private const val TAG = "CapabilityDiscoveryEvent"

        /**
         * A head that carries its [definition] inline. The profile and transport markers are
         * derived from the definition, so the tags cannot disagree with the body.
         */
        fun build(
            slug: String,
            definition: CapabilityDefinition,
            test: Boolean = false,
            dev: Boolean = false,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<CapabilityDiscoveryEvent>.() -> Unit = {},
        ): EventTemplate<CapabilityDiscoveryEvent> {
            val profile = requireNotNull(definition.profile) { "A definition names its profile" }
            return build(slug, definition, profile, definition.transports(), test, dev, createdAt, initializer)
        }

        /**
         * A head that only references a definition published elsewhere. The body carries no
         * profile, so the caller states the definition's [profile] and [transports] for the tags.
         */
        fun buildHead(
            slug: String,
            definition: DefinitionRef,
            profile: String,
            transports: List<String>,
            test: Boolean = false,
            dev: Boolean = false,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<CapabilityDiscoveryEvent>.() -> Unit = {},
        ) = build(slug, CapabilityDefinition(definition = definition), profile, transports, test, dev, createdAt, initializer)

        private fun build(
            slug: String,
            body: CapabilityDefinition,
            profile: String,
            transports: List<String>,
            test: Boolean,
            dev: Boolean,
            createdAt: Long,
            initializer: TagArrayBuilder<CapabilityDiscoveryEvent>.() -> Unit,
        ): EventTemplate<CapabilityDiscoveryEvent> {
            require(OaIds.isSlug(slug)) { "Not a capability slug: $slug" }
            return eventTemplate(KIND, body.toJson(), createdAt) {
                dTag(slug)
                add(OaMarkerTag.assemble(MARKER))
                add(OaMarkerTag.assemble(PROFILE_PREFIX + profile))
                transports.distinct().forEach { add(OaMarkerTag.assemble(TRANSPORT_PREFIX + it)) }
                if (test) add(OaMarkerTag.assemble(TEST_MARKER))
                if (dev) add(OaMarkerTag.assemble(DEV_MARKER))
                initializer()
            }
        }
    }
}
