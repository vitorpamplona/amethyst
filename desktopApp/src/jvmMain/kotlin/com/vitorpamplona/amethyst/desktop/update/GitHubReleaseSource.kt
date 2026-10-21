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
package com.vitorpamplona.amethyst.desktop.update

import com.vitorpamplona.amethyst.commons.appupdate.AvailableUpdate
import com.vitorpamplona.amethyst.commons.appupdate.DesktopAssetNames
import com.vitorpamplona.amethyst.commons.appupdate.DesktopRelease
import com.vitorpamplona.amethyst.commons.appupdate.UpdateSource
import com.vitorpamplona.amethyst.commons.appupdate.UpdateTarget
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.shared.Nip82VersionComparator
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.booleanOrNull
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.objectListOrNull
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.stringOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant

/**
 * The fallback: GitHub's latest stable release. It only tells that a version exists and links its
 * page, and only when the release has a file for this platform; nothing is ever installed from
 * it, since a GitHub release carries no signature the app checks. Asked only when the signed NIP-82 release found nothing to install.
 */
class GitHubReleaseSource(
    private val clientFor: (String) -> OkHttpClient,
) : UpdateSource {
    override suspend fun findUpdate(
        currentVersion: String,
        target: UpdateTarget,
    ): AvailableUpdate? =
        withContext(Dispatchers.IO) {
            val url = "https://api.github.com/repos/${DesktopRelease.GITHUB_OWNER}/${DesktopRelease.GITHUB_REPO}/releases/latest"
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github+json")
                    .build()
            clientFor(url).newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} from GitHub")
                parse(response.body.string(), currentVersion, target.platform)
            }
        }

    companion object {
        /** The release, when it is newer and has a file for [platform] (an Intel Mac has no DMG to get). */
        internal fun parse(
            json: String,
            currentVersion: String,
            platform: String,
        ): AvailableUpdate? {
            val release = Json.parseToJsonElement(json) as? JsonObject ?: return null
            if (release.booleanOrNull("draft") == true || release.booleanOrNull("prerelease") == true) return null
            val version =
                (release.stringOrNull("tag_name") ?: "")
                    .removePrefix("v")
                    .ifBlank { return null }
            if (Nip82VersionComparator.compare(version, currentVersion) <= 0) return null
            val hasBuild = release.objectListOrNull("assets").orEmpty().any { DesktopAssetNames.classify(it.stringOrNull("name") ?: "", version)?.platform == platform }
            if (!hasBuild) return null
            val publishedAt =
                (release.stringOrNull("published_at") ?: "")
                    .let { runCatching { Instant.parse(it).epochSecond }.getOrNull() } ?: 0L
            return AvailableUpdate(
                version = version,
                releaseNotes = "",
                asset = null,
                pageUrl = release.stringOrNull("html_url")?.takeIf { it.startsWith("https://github.com/") } ?: DesktopRelease.releasePage(version),
                publishedAt = publishedAt,
            )
        }
    }
}
