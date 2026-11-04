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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class GitHubReleaseSourceTest {
    private fun release(
        tag: String,
        prerelease: Boolean = false,
        files: List<String> = listOf("macos-arm64.dmg", "linux-x64.deb", "x86_64.AppImage"),
    ): String {
        val v = tag.removePrefix("v")
        val assets = files.joinToString(",") { """{"name":"amethyst-desktop-$v-$it"}""" }
        return """{"tag_name":"$tag","prerelease":$prerelease,"draft":false,"html_url":"https://github.com/vitorpamplona/amethyst/releases/tag/$tag","published_at":"2026-10-01T12:00:00Z","assets":[$assets]}"""
    }

    @Test
    fun aNewerReleaseIsOfferedWithoutAFile() {
        val update = GitHubReleaseSource.parse(release("v1.21.0"), "1.20.0", "linux-x86_64")!!
        assertEquals("1.21.0", update.version)
        assertFalse(update.canInstall, "nothing is installed from GitHub's word alone")
        assertEquals("https://github.com/vitorpamplona/amethyst/releases/tag/v1.21.0", update.pageUrl)
        assertEquals(1_790_856_000L, update.publishedAt)
    }

    @Test
    fun ignoresOlderAndPrereleases() {
        assertNull(GitHubReleaseSource.parse(release("v1.20.0"), "1.20.0", "linux-x86_64"))
        assertNull(GitHubReleaseSource.parse(release("v1.19.0"), "1.20.0", "linux-x86_64"))
        assertNull(GitHubReleaseSource.parse(release("v1.21.0-rc1", prerelease = true), "1.20.0", "linux-x86_64"))
    }

    /** No build for this machine (an Intel Mac; the release has only the arm64 DMG): nothing to offer. */
    @Test
    fun ignoresAReleaseWithNoBuildForThePlatform() {
        assertNull(GitHubReleaseSource.parse(release("v1.21.0"), "1.20.0", "darwin-x86_64"))
        assertEquals("1.21.0", GitHubReleaseSource.parse(release("v1.21.0"), "1.20.0", "darwin-arm64")?.version)
    }
}
