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
package com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-82 Software Asset (kind 3063).
 *
 * A regular non-replaceable event that cryptographically ties an app install
 * artifact to a SHA-256 hash. It extends the NIP-94 kind 1063 tag set with
 * software-asset-specific tags such as `version`, `f` (platform) and
 * `apk_certificate_hash`.
 */
@Immutable
class SoftwareAssetEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun appId() = tags.appId()

    fun url() = tags.url()

    fun mimeType() = tags.mimeType()

    fun hash() = tags.hash()

    fun sizeInBytes() = tags.sizeInBytes()

    fun version() = tags.version()

    fun platforms() = tags.platforms()

    fun minPlatformVersion() = tags.minPlatformVersion()

    fun targetPlatformVersion() = tags.targetPlatformVersion()

    fun supportedNips() = tags.supportedNips()

    fun variant() = tags.variant()

    fun commit() = tags.commit()

    fun minAllowedVersion() = tags.minAllowedVersion()

    fun versionCode() = tags.versionCode()

    fun minAllowedVersionCode() = tags.minAllowedVersionCode()

    fun apkCertificateHashes() = tags.apkCertificateHashes()

    fun webContent() = tags.webContent()

    companion object {
        const val KIND = 3063
        const val ALT_DESCRIPTION = "Software asset"

        fun build(
            appId: String,
            mimeType: String,
            hash: String,
            version: String,
            assetUrl: String? = null,
            sizeInBytes: Int? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<SoftwareAssetEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            alt(ALT_DESCRIPTION)
            appId(appId)
            mimeType(mimeType)
            hash(hash)
            version(version)
            assetUrl?.let { url(it) }
            sizeInBytes?.let { sizeInBytes(it) }
            initializer()
        }
    }
}
