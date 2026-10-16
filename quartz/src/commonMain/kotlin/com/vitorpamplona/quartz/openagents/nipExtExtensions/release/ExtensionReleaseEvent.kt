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
package com.vitorpamplona.quartz.openagents.nipExtExtensions.release

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.openagents.core.ArtifactRef
import com.vitorpamplona.quartz.openagents.core.EventRef
import com.vitorpamplona.quartz.openagents.core.OaIds
import com.vitorpamplona.quartz.openagents.core.OaMarkerTag
import com.vitorpamplona.quartz.openagents.core.PackageId
import com.vitorpamplona.quartz.openagents.core.oaMarkersWith
import com.vitorpamplona.quartz.openagents.nipExtExtensions.ExtRecordType
import com.vitorpamplona.quartz.openagents.nipExtExtensions.listing.ExtensionListingEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

/**
 * OpenAgents NIP-EXT release, kind 3184: an immutable declaration of one package version.
 *
 * ```json
 * {"v":1,"requires":[],"type":"release","package":"<root>:<slug>","version":"0.1.1",
 *  "manifest":{"digest":"sha256:…","size":1244,"media_type":"application/json","schema":"openagents.package.v1"}}
 * ```
 * tags `[["t","oa:ext:release:v1"]]`. The signer must be the package root.
 *
 * Regular, not addressable: a release is never replaced, only pointed at (by the package's
 * [ExtensionListingEvent]) or revoked (by a kind-3185 revocation, which this client does not
 * model yet).
 *
 * Searchable by the package slug and the version label, the only words a release carries; a
 * person finds a package by its listing's title, and its releases by name and version.
 */
@Immutable
class ExtensionReleaseEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressHintProvider,
    EventHintProvider,
    SearchableEvent {
    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    private var cachedRelease: ExtensionRelease? = null

    /** The parsed body, or null when the content is not a JSON release. */
    fun release(): ExtensionRelease? {
        cachedRelease?.let { return it }
        if (!content.startsWith("{")) return null
        return try {
            ExtensionRelease.parse(content).also { cachedRelease = it }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Content parse error on $id", e)
            null
        }
    }

    fun packageId(): PackageId? = release()?.parsedPackage()

    fun packageSlug(): String? = packageId()?.slug

    fun version(): String? = release()?.version?.takeIf { it.isNotBlank() }

    fun manifest(): ArtifactRef? = release()?.manifest

    /** The author's per-call fee, in millisatoshis; zero when the release charges nothing. */
    fun feeMsat(): Long = release()?.feeMsat ?: 0L

    /** Where a fee is paid: a Spark address, a Lightning address or a node key. */
    fun payout(): String? = release()?.payout?.takeIf { it.isNotBlank() }

    /** This release as the exact reference a listing or a lock records. */
    fun toEventRef() = EventRef(id, pubKey, kind)

    /**
     * The package's listing, `30184:<root>:<slug>` — only when the signer IS the root, since a
     * release signed by anyone else claims a namespace it does not own and has no listing.
     */
    fun listingAddress(): Address? {
        val pkg = packageId() ?: return null
        if (pkg.root != pubKey) return null
        return Address(ExtensionListingEvent.KIND, pkg.root, pkg.slug)
    }

    /**
     * Whether the body and the event agree, as NIP-EXT requires: one `oa:ext:` marker naming the
     * release type, a well-formed body, and the package root equal to the signer.
     */
    fun isConsistent(): Boolean {
        val markers = tags.oaMarkersWith(EXT_MARKER_PREFIX)
        if (markers.size != 1 || markers[0] != MARKER) return false
        val body = release() ?: return false
        return body.isWellFormed() && body.parsedPackage()?.root == pubKey
    }

    override fun indexableContent() = listOfNotNull(packageSlug(), version()).joinToString("\n")

    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(packageSlug())) return
        visitor.visit(version())
    }

    // Neither reference carries a relay; linking them lets the relay this release arrived from
    // stand in as their hint. The listing is published beside the release by the same CLI call.
    override fun addressHints(): List<AddressHint> = emptyList()

    override fun linkedAddressIds(): List<String> = listOfNotNull(listingAddress()?.toValue())

    override fun eventHints(): List<EventIdHint> = emptyList()

    override fun linkedEventIds(): List<HexKey> = listOfNotNull(manifest()?.event?.takeIf { it.isWellFormed() }?.id)

    companion object {
        const val KIND = 3184
        const val ALT = "OpenAgents package release"
        val MARKER = ExtRecordType.marker(ExtRecordType.RELEASE)

        private const val TAG = "ExtensionReleaseEvent"
        private const val EXT_MARKER_PREFIX = "oa:ext:"

        /**
         * One release of `<root>:<slug>`. A nonzero [feeMsat] needs a [payout]: the fee and where
         * it is paid are part of the signed release.
         */
        fun build(
            root: HexKey,
            slug: String,
            version: String,
            manifest: ArtifactRef,
            feeMsat: Long? = null,
            payout: String? = null,
            meta: JsonObject? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ExtensionReleaseEvent>.() -> Unit = {},
        ): EventTemplate<ExtensionReleaseEvent> {
            require(OaIds.isSlug(slug)) { "Not a package slug: $slug" }
            require(version.isNotEmpty() && version.none { it.isWhitespace() }) { "Not a version label: $version" }
            require((feeMsat ?: 0) >= 0) { "Negative fee" }
            require((feeMsat ?: 0) == 0L || !payout.isNullOrBlank()) { "A fee needs a payout" }
            val body =
                ExtensionRelease(
                    packageId = OaIds.packageId(root, slug),
                    version = version,
                    manifest = manifest,
                    feeMsat = feeMsat,
                    payout = payout,
                    meta = meta,
                )
            return eventTemplate(KIND, body.toJson(), createdAt) {
                add(OaMarkerTag.assemble(MARKER))
                initializer()
            }
        }
    }
}
