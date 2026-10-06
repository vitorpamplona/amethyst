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
package com.vitorpamplona.quartz.nip34Git

import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusOpenEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Suppress("DEPRECATION")
class GitNotifiedUsersTest {
    private val pk = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val other = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val third = "7d7ffd720b907fe597a7f454afe02f2dc1eca440baa029e9117b1c3209839377"
    private val eventId = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val eventId2 = "b1a2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"
    private val addressId = "30023:$other:my-article"
    private val relay = "wss://relay.damus.io/"
    private val id = "00".repeat(32)
    private val sig = "00".repeat(64)

    private val repo = "30617:$other:amethyst"
    private val tags = arrayOf(arrayOf("a", repo, relay), arrayOf("p", other, relay), arrayOf("p", "short"), arrayOf("p", third))

    @Test
    fun everyNip34KindNamesItsNotifiedUsers() {
        val expected = listOf(other, third)

        assertEquals(expected, GitIssueEvent(id, pk, 1L, tags, "", sig).notifiedUsers())
        assertEquals(expected, GitPatchEvent(id, pk, 1L, tags, "", sig).notifiedUsers())
        assertEquals(expected, GitPullRequestEvent(id, pk, 1L, tags, "", sig).notifiedUsers())
        assertEquals(expected, GitPullRequestUpdateEvent(id, pk, 1L, tags, "", sig).notifiedUsers())
        assertEquals(expected, GitReplyEvent(id, pk, 1L, tags, "", sig).notifiedUsers())
        assertEquals(expected, GitStatusOpenEvent(id, pk, 1L, tags, "", sig).notifiedUsers())
    }

    @Test
    fun patchNamesThePreviousPatchInItsSeries() {
        val patch = GitPatchEvent(id, pk, 1L, tags + arrayOf(arrayOf("e", eventId, relay, "reply", other)), "", sig)

        assertEquals(eventId, patch.previousPatchId())
        assertNull(GitPatchEvent(id, pk, 1L, tags, "", sig).previousPatchId())
    }

    @Test
    fun patchRepositoryPrefersTheRootMarkedA() {
        val rootRepo = "30617:$third:fork"
        val patch = GitPatchEvent(id, pk, 1L, arrayOf(arrayOf("a", repo), arrayOf("a", rootRepo, relay, "root")), "", sig)

        assertEquals(rootRepo, patch.repositoryAddress()?.toValue())
        assertEquals(listOf(rootRepo), patch.linkedAddressIds())
    }
}
