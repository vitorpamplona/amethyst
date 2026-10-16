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
import com.vitorpamplona.quartz.openagents.core.ArtifactRef
import com.vitorpamplona.quartz.openagents.core.OaJson
import com.vitorpamplona.quartz.openagents.core.PackageId
import com.vitorpamplona.quartz.openagents.nipExtExtensions.ExtRecordType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * The body of a kind-3184 release: one immutable package version, bound to the exact bytes of its
 * `openagents.package.v1` manifest, and optionally the per-call fee and where it is paid.
 *
 * A release is identified by its event id and manifest digest, never by its [version] label: two
 * releases rebinding one package/version to different manifests are publisher equivocation, which
 * a client reports and never resolves by timestamp.
 */
@Immutable
@Serializable
data class ExtensionRelease(
    val v: Int? = OaJson.VERSION,
    val requires: List<String>? = emptyList(),
    val type: String? = ExtRecordType.RELEASE,
    @SerialName("package")
    val packageId: String? = null,
    val version: String? = null,
    val manifest: ArtifactRef? = null,
    @SerialName("fee_msat")
    val feeMsat: Long? = null,
    val payout: String? = null,
    val meta: JsonObject? = null,
) {
    fun parsedPackage() = PackageId.parse(packageId)

    /**
     * The shape NIP-EXT gives a release: v1, the release type, a valid package id, a version label
     * with no whitespace, a package-manifest ArtifactRef, and a payout whenever a fee is charged.
     */
    fun isWellFormed(): Boolean {
        if (v != OaJson.VERSION || type != ExtRecordType.RELEASE || parsedPackage() == null) return false
        if (version.isNullOrEmpty() || version.any { it.isWhitespace() }) return false
        val manifest = manifest ?: return false
        if (!manifest.isWellFormed() || manifest.schema != MANIFEST_SCHEMA) return false
        val fee = feeMsat ?: 0
        if (fee < 0) return false
        return fee == 0L || !payout.isNullOrBlank()
    }

    fun toJson(): String = OaJson.json.encodeToString(serializer(), this)

    companion object {
        const val MANIFEST_SCHEMA = "openagents.package.v1"

        fun parse(content: String): ExtensionRelease = OaJson.json.decodeFromString(serializer(), content)
    }
}
