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
package com.vitorpamplona.quartz.experimental.walletScrutiny.assetBundle

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.SoftwareAssetEvent
import com.vitorpamplona.quartz.experimental.walletScrutiny.assetFiles
import com.vitorpamplona.quartz.experimental.walletScrutiny.assetHashes
import com.vitorpamplona.quartz.experimental.walletScrutiny.assetPlatform
import com.vitorpamplona.quartz.experimental.walletScrutiny.productId
import com.vitorpamplona.quartz.experimental.walletScrutiny.productVersion
import com.vitorpamplona.quartz.experimental.walletScrutiny.tags.AssetFileTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * WalletScrutiny asset bundle registration (kind 9401): someone registers the files of one release
 * of a product — an APK and its splits, a tarball, an installer — by their SHA-256, so build
 * verifiers can try to reproduce them. Spec: WalletScrutiny `docs/verifications.md` ("Asset Bundle
 * Registration"). Not a NIP. It replaced WalletScrutiny's use of NIP-94 kind 1063 for the same job
 * (debug sites use kind 9605).
 *
 * - `i` = the product id (`io.bluewallet.bluewallet`), `version`, `platform`;
 * - one `x` = `[sha256, file name]` per file ([AssetFileTag]);
 * - `content` = a short note on where the files came from ("installed from Play Store, uploaded
 *   by WalletScrutiny Android"), at most 120 chars.
 *
 * **Relation to NIP-82.** `i` and `version` mean what they mean on a NIP-82 software asset (3063)
 * and release (30063): the application's identifier and the release version, so this reuses
 * NIP-82's `AppIdTag` and `VersionTag`. A bundle never points at a 3063 or 30063 event, though:
 * it names the release by those two values and its files by hash. The hashes are the join key —
 * a Zapstore-published 3063 for the same APK has the same `x` ([registers]), and every build
 * verification of the bundle (kind 30301, `BuildVerificationEvent`) lists these hashes in its own
 * `x` tags.
 *
 * No references, so no hint provider: `i` is a value (the app id), `x` are file hashes. Not a
 * SearchableEvent: the only text is the upload note, which describes the upload rather than the
 * software, and the product id and version are identifiers that `#i`-style tag queries serve
 * better than full-text search.
 */
@Immutable
class AssetBundleEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: TagArray,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun productId() = tags.productId()

    fun version() = tags.productVersion()

    fun platform() = tags.assetPlatform()

    /** Each registered file (`x`), in tag order. */
    fun files() = tags.assetFiles()

    /** The SHA-256 of each registered file, lowercase, in tag order. */
    fun hashes() = tags.assetHashes()

    /** The registrant's note on where the files came from (`content`). */
    fun note() = content.ifBlank { null }

    /** The registered file whose hash is [sha256] (any hex case), if any. */
    fun file(sha256: HexKey): AssetFileTag? = files().firstOrNull { it.hash.equals(sha256, ignoreCase = true) }

    /**
     * True when [asset] (a NIP-82 software asset) is one of this bundle's files: same SHA-256.
     * The product ids are not compared — a store may republish a file under its own id.
     */
    fun registers(asset: SoftwareAssetEvent): Boolean = asset.hash()?.let { file(it) } != null

    companion object {
        const val KIND = 9401

        fun build(
            productId: String,
            version: String,
            platform: String,
            files: List<AssetFileTag>,
            note: String = "",
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<AssetBundleEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, note, createdAt) {
            productId(productId)
            version(version)
            platform(platform)
            files(files)
            initializer()
        }
    }
}
