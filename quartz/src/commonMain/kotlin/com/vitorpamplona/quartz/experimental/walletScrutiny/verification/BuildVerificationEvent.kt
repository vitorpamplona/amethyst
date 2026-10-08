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
package com.vitorpamplona.quartz.experimental.walletScrutiny.verification

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.tags.AppIdTag
import com.vitorpamplona.quartz.experimental.walletScrutiny.assetBundle.AssetBundleEvent
import com.vitorpamplona.quartz.experimental.walletScrutiny.assetFiles
import com.vitorpamplona.quartz.experimental.walletScrutiny.assetHashes
import com.vitorpamplona.quartz.experimental.walletScrutiny.assetPlatform
import com.vitorpamplona.quartz.experimental.walletScrutiny.productId
import com.vitorpamplona.quartz.experimental.walletScrutiny.productVersion
import com.vitorpamplona.quartz.experimental.walletScrutiny.verification.tags.BuildStatus
import com.vitorpamplona.quartz.experimental.walletScrutiny.verification.tags.BuildStatusTag
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.fastAny
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A WalletScrutiny reproducible-build verification (kind 30301): a verifier built a product's
 * release from its public source and reports whether the result matches the official files.
 * Spec: WalletScrutiny `docs/verifications.md` ("Verification"). Not a NIP.
 *
 * - `d` = `<product id>:<version>:<platform>:<sha256 of the first file>` on every live event (the
 *   spec shows no `d`; drafts, kind 30801, use `<product id>:<version>:<platform>`);
 * - `i` (product id), `version`, `platform`: as on the asset bundle (kind 9401) being verified;
 * - `status` = the verdict ([BuildStatus]) for the whole set of files;
 * - `x` = each verified file's **registered download** hash — a join key to the
 *   [AssetBundleEvent] (and to a NIP-82 software asset of the same file), not the hash of what the
 *   verifier built;
 * - `file-attachment` = events holding scripts used for the build, `output-file` = name + hash of
 *   logs and recordings, `issue-tracker-url`, and `based-on` = the verification whose method this
 *   one reuses, with its author;
 * - `content` = JSON `{description, content}` ([BuildVerificationContent]): a one-line description
 *   and the markdown report.
 *
 * **The kind is shared.** NIP PR #1665 defines 30301 as a Kanban board, and an encrypted planner
 * app stores its tasks on it too. `EventFactory` builds this class only for [isBuildVerification]
 * tags (`i` + `status`), the board for board-shaped tags, and `UnrecognizedKind30301Event` for the
 * rest; kind-level probes answer as the board (see `KanbanBoardEvent`).
 *
 * **Searchable**: the description and the report. The report is prose written for people deciding
 * whether to trust a wallet build (what was built, how, what differed), whether a person or the
 * build server wrote it. Hashes, ids and the verdict code are not indexed.
 *
 * **Edges**: the attachment events and the `based-on` verification (and its author). The verified
 * release is not an edge: the event names it by product id, version and file hashes, all values.
 */
@Immutable
class BuildVerificationEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: TagArray,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    PubKeyHintProvider,
    SearchableEvent {
    private val parsedContent by lazy { BuildVerificationContent.parse(content) }

    fun productId() = tags.productId()

    fun version() = tags.productVersion()

    fun platform() = tags.assetPlatform()

    /** The verdict as written (`status`), including codes [BuildStatus] does not know. */
    fun statusCode() = tags.buildStatusCode()

    fun status(): BuildStatus? = tags.buildStatus()

    fun isReproducible() = status() == BuildStatus.REPRODUCIBLE

    /** The verified files (`x`), by their registered download hash. */
    fun files() = tags.assetFiles()

    fun hashes() = tags.assetHashes()

    fun fileAttachments() = tags.fileAttachments()

    fun outputFiles() = tags.outputFiles()

    fun issueTrackerUrl() = tags.issueTrackerUrl()

    fun basedOn() = tags.basedOn()

    /** The one-line description of what was reproduced (`content.description`). */
    fun description(): String? = parsedContent?.description

    /** The markdown report (`content.content`). */
    fun report(): String? = parsedContent?.report

    /** True when this verdict covers every file [bundle] registered (by hash). */
    fun covers(bundle: AssetBundleEvent): Boolean {
        val verified = hashes().toSet()
        val registered = bundle.hashes()
        return registered.isNotEmpty() && verified.containsAll(registered)
    }

    override fun indexableContent() = listOfNotNull(description(), report()).joinToString("\n")

    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(description())) return
        visitor.visit(report())
    }

    /** No tag here carries a relay hint. */
    override fun eventHints() = emptyList<EventIdHint>()

    /**
     * `ATTACHMENT`: each `file-attachment` event (a build script the verifier used);
     * `BASED_ON`: the `based-on` verification whose method this one reuses.
     */
    override fun linkedEventIds(): List<HexKey> = fileAttachments() + listOfNotNull(basedOn()?.eventId)

    /** No tag here carries a relay hint. */
    override fun pubKeyHints() = emptyList<PubKeyHint>()

    /** `BASED_ON_AUTHOR`: the author of the `based-on` verification (rule 3: its own relation). */
    override fun linkedPubKeys(): List<HexKey> = listOfNotNull(basedOn()?.author)

    companion object {
        const val KIND = 30301

        /**
         * True for a WalletScrutiny verification: a product id (`i`) and a verdict (`status`). A
         * Kanban board has neither; the planner app has a `status` but no `i`.
         */
        fun isBuildVerification(tags: TagArray): Boolean =
            tags.fastAny { it.size > 1 && it[0] == AppIdTag.TAG_NAME && it[1].isNotEmpty() } &&
                tags.fastAny(BuildStatusTag::isTag)

        /** The `d` WalletScrutiny publishes verifications under. */
        fun buildDTag(
            productId: String,
            version: String,
            platform: String,
            firstHash: HexKey,
        ) = "$productId:$version:$platform:$firstHash"

        fun build(
            productId: String,
            version: String,
            platform: String,
            status: BuildStatus,
            verifiedHashes: List<HexKey>,
            description: String,
            report: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<BuildVerificationEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, BuildVerificationContent(description, report).toJson(), createdAt) {
            dTag(buildDTag(productId, version, platform, verifiedHashes.firstOrNull() ?: ""))
            productId(productId)
            version(version)
            platform(platform)
            buildStatus(status)
            verifiedHashes(verifiedHashes)
            initializer()
        }
    }
}
