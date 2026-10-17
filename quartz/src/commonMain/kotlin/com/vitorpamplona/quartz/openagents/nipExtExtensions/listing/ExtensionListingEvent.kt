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
package com.vitorpamplona.quartz.openagents.nipExtExtensions.listing

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.openagents.core.EventRef
import com.vitorpamplona.quartz.openagents.core.OaIds
import com.vitorpamplona.quartz.openagents.core.OaMarkerTag
import com.vitorpamplona.quartz.openagents.core.PackageId
import com.vitorpamplona.quartz.openagents.core.oaMarkersWith
import com.vitorpamplona.quartz.openagents.nipExtExtensions.ExtRecordType
import com.vitorpamplona.quartz.openagents.nipExtExtensions.release.ExtensionReleaseEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

/**
 * OpenAgents NIP-EXT package listing, kind 30184: a publisher's current-release pointer for one
 * package, addressed by `(root, 30184, package-slug)`.
 *
 * ```json
 * {"v":1,"requires":[],"type":"listing","package":"<root>:<slug>","state":"published",
 *  "release":{"id":"…","pubkey":"<root>","kind":3184},"title":"…","description":"…"}
 * ```
 * tags `[["d","<slug>"],["t","oa:ext:listing:v1"]]`.
 *
 * The listing is discovery only. It points at an immutable [ExtensionReleaseEvent] by exact
 * EventRef; it never authorizes installation, and hiding a listing does not revoke a release.
 *
 * Searchable by its `title`, its `description` and the package slug — the text a person picks a
 * plugin by. The body is JSON, so it is parsed and those fields joined; the raw JSON, the
 * release reference and the root key are never indexed.
 */
@Immutable
class ExtensionListingEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    SearchableEvent {
    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    private var cachedListing: ExtensionListing? = null

    /** The parsed body, or null when the content is not a JSON listing. */
    fun listing(): ExtensionListing? {
        cachedListing?.let { return it }
        if (!content.startsWith("{")) return null
        return try {
            ExtensionListing.parse(content).also { cachedListing = it }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Content parse error on $id", e)
            null
        }
    }

    /** The package slug, which is also this listing's `d`. */
    fun slug() = dTag()

    fun packageId(): PackageId? = listing()?.parsedPackage()

    fun title(): String? = listing()?.title?.takeIf { it.isNotBlank() }

    fun description(): String? = listing()?.description?.takeIf { it.isNotBlank() }

    fun isPublished() = listing()?.isPublished() == true

    fun isHidden() = listing()?.isHidden() == true

    /** The exact release this listing points at; null when hidden or malformed. */
    fun release(): EventRef? = listing()?.release?.takeIf { it.isWellFormed() && it.kind == ExtensionReleaseEvent.KIND }

    /**
     * Whether the body and the event agree, as NIP-EXT requires before a listing counts: one
     * `oa:ext:` marker naming the listing type, a well-formed body, the package root equal to the
     * signer and its slug equal to the `d` tag, and a published release signed by that same root.
     */
    fun isConsistent(): Boolean {
        val markers = tags.oaMarkersWith(EXT_MARKER_PREFIX)
        if (markers.size != 1 || markers[0] != MARKER) return false
        val body = listing() ?: return false
        if (!body.isWellFormed()) return false
        val pkg = body.parsedPackage() ?: return false
        if (pkg.root != pubKey || pkg.slug != dTag()) return false
        val release = body.release ?: return true
        return release.pubkey == pubKey && release.kind == ExtensionReleaseEvent.KIND
    }

    override fun indexableContent() = listOfNotNull(title(), description(), slug().ifBlank { null }).joinToString("\n")

    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(title())) return
        if (!visitor.visit(description())) return
        visitor.visit(slug().ifBlank { null })
    }

    // The release reference carries no relay, so there is no hint to give; linking the id lets
    // the relay this listing arrived from stand in as one, which is where the CLI publishes both.
    override fun eventHints(): List<EventIdHint> = emptyList()

    override fun linkedEventIds(): List<HexKey> = listOfNotNull(release()?.id)

    companion object {
        const val KIND = 30184
        const val ALT = "OpenAgents package listing"
        val MARKER = ExtRecordType.marker(ExtRecordType.LISTING)

        private const val TAG = "ExtensionListingEvent"
        private const val EXT_MARKER_PREFIX = "oa:ext:"

        /**
         * A published listing pointing at [release]. [root] is the signer's pubkey: the package id
         * in the body names it, and the template cannot know who will sign it.
         */
        fun build(
            root: HexKey,
            slug: String,
            release: EventRef,
            title: String,
            description: String,
            meta: JsonObject? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ExtensionListingEvent>.() -> Unit = {},
        ) = build(root, slug, ExtensionListing.STATE_PUBLISHED, release, title, description, meta, createdAt, initializer)

        /** A hidden listing: shown nowhere, pointing at nothing, and revoking nothing. */
        fun buildHidden(
            root: HexKey,
            slug: String,
            title: String,
            description: String,
            meta: JsonObject? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ExtensionListingEvent>.() -> Unit = {},
        ) = build(root, slug, ExtensionListing.STATE_HIDDEN, null, title, description, meta, createdAt, initializer)

        private fun build(
            root: HexKey,
            slug: String,
            state: String,
            release: EventRef?,
            title: String,
            description: String,
            meta: JsonObject?,
            createdAt: Long,
            initializer: TagArrayBuilder<ExtensionListingEvent>.() -> Unit,
        ): EventTemplate<ExtensionListingEvent> {
            require(OaIds.isSlug(slug)) { "Not a package slug: $slug" }
            val body =
                ExtensionListing(
                    packageId = OaIds.packageId(root, slug),
                    state = state,
                    release = release,
                    title = title,
                    description = description,
                    meta = meta,
                )
            return eventTemplate(KIND, body.toJson(), createdAt) {
                dTag(slug)
                add(OaMarkerTag.assemble(MARKER))
                initializer()
            }
        }
    }
}
