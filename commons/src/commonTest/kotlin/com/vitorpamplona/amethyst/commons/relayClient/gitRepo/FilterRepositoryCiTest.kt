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
package com.vitorpamplona.amethyst.commons.relayClient.gitRepo

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip34Git.ci.jobResult.CiJobResultEvent
import com.vitorpamplona.quartz.nip34Git.ci.workflowProgress.CiWorkflowProgressEvent
import com.vitorpamplona.quartz.nip34Git.ci.workflowResult.CiWorkflowResultEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FilterRepositoryCiTest {
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.example")!!
    private val owner = "1".repeat(64)
    private val maintainer = "2".repeat(64)

    private val repository =
        GitRepositoryEvent(
            "9".repeat(64),
            owner,
            1,
            arrayOf(arrayOf("d", "repo"), arrayOf("maintainers", owner, maintainer)),
            "",
            "0".repeat(128),
        )

    @Test
    fun asksForRunsUnderEveryMaintainerCoordinate() {
        val filter = filterRepositoryCi(relay, repository, null).filter

        assertEquals(listOf("30617:$owner:repo", "30617:$maintainer:repo"), filter.tags?.get("a"))
        assertEquals(listOf(CiWorkflowResultEvent.KIND, CiWorkflowProgressEvent.KIND), filter.kinds)
        // Log tails stay out of the repository-wide fetch.
        assertFalse(filter.kinds!!.contains(CiJobResultEvent.KIND))
    }

    @Test
    fun ciKindsDoNotShareTheIssueAndPrLimit() {
        assertFalse(RepositoryContentKinds.any { it in RepositoryCiKinds })
    }
}
