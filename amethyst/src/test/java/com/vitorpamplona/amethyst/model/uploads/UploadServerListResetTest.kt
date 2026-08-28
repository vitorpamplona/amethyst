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
package com.vitorpamplona.amethyst.model.uploads

import com.vitorpamplona.amethyst.ui.actions.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ORIGINLESS_UPLOAD_TARGET
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadServerListResetTest {
    private fun server(host: String) = ServerName(host, "https://$host/", ServerType.Blossom)

    private val a = server("a.example")
    private val b = server("b.example")
    private val c = server("c.example")
    private val custom = server("my-custom.example")
    private val perNodeOriginless = ServerName("originless.gupt.app", "https://originless.gupt.app", ServerType.Originless)

    private fun host(url: String) = url.removePrefix("https://").removeSuffix("/")

    @Test
    fun transientDefaultEmissionDoesNotClobberSavedPick() {
        // Regression guard: before the user's BlossomServersEvent loads, the raw
        // published list is empty and `merged` is the DEFAULT_MEDIA_SERVERS fallback.
        // The saved custom pick must NOT be reset.
        val result =
            resetTargetOrNull(
                rawList = emptyList(),
                merged = listOf(a, b, c),
                current = custom,
            )

        assertNull(result)
    }

    @Test
    fun aDroppedBlossomPickResetsToAnotherBlossomServer() {
        val result =
            resetTargetOrNull(
                rawList = listOf("a.example", "b.example"),
                merged = listOf(a, b, c),
                current = custom,
            )

        assertEquals(a, result)
    }

    @Test
    fun originlessAndBlossomAppearTogetherInThePicker() {
        val merged =
            mergeUploadServerList(
                blossom = listOf("https://a.example/"),
                originlessUrls = listOf("https://originless.gupt.app"),
                host = ::host,
            )

        assertEquals(listOf(a, ORIGINLESS_UPLOAD_TARGET), merged)
    }

    @Test
    fun blossomIsUntouchedWhenNoOriginlessNodeIsConfigured() {
        val merged =
            mergeUploadServerList(
                blossom = listOf("https://a.example/"),
                originlessUrls = emptyList(),
                host = ::host,
            )

        assertEquals(listOf(a), merged)
    }

    @Test
    fun theOriginlessTargetIsAppendedToTheDefaultServersToo() {
        val merged =
            mergeUploadServerList(
                blossom = emptyList(),
                originlessUrls = listOf("https://originless.gupt.app"),
                host = ::host,
            )

        assertEquals(DEFAULT_MEDIA_SERVERS + ORIGINLESS_UPLOAD_TARGET, merged)
    }

    @Test
    fun anOriginlessPickSurvivesTheStartupRaceOnKind10062() {
        // kind 10062 has not loaded yet, so the node list reads empty and the picker
        // carries no Originless entry. Resetting here would clobber the saved pick on
        // every launch, exactly like the kind-10063 race above.
        val result =
            resetTargetOrNull(
                rawList = listOf("a.example"),
                merged = listOf(a, b),
                current = ORIGINLESS_UPLOAD_TARGET,
            )

        assertNull(result)
    }

    @Test
    fun anOriginlessPickIsKeptWhileNodesRemain() {
        val result =
            resetTargetOrNull(
                rawList = listOf("a.example"),
                merged = listOf(a, ORIGINLESS_UPLOAD_TARGET),
                current = ORIGINLESS_UPLOAD_TARGET,
            )

        assertNull(result)
    }

    @Test
    fun aLegacyPerNodePickMigratesToTheFanOutTarget() {
        val result =
            resetTargetOrNull(
                rawList = listOf("a.example"),
                merged = listOf(a, ORIGINLESS_UPLOAD_TARGET),
                current = perNodeOriginless,
            )

        assertEquals(ORIGINLESS_UPLOAD_TARGET, result)
    }

    @Test
    fun aResetNeverLandsOnOriginless() {
        val result =
            resetTargetOrNull(
                rawList = listOf("a.example", "b.example"),
                merged = listOf(a, b, ORIGINLESS_UPLOAD_TARGET),
                current = custom,
            )

        assertEquals(a, result)
        assertTrue(result!!.type != ServerType.Originless)
    }
}
