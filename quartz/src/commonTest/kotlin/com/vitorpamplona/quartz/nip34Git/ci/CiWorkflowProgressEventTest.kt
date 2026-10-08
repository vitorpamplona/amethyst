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
package com.vitorpamplona.quartz.nip34Git.ci

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiConclusion
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiJobResultQuote
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiProvenanceKind
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiProvenanceQuote
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiTrigger
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiWorkflowFile
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiWorkflowStatus
import com.vitorpamplona.quartz.nip34Git.ci.workflowProgress.CiWorkflowProgressEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Kind 39842 against two real markers of one run (in progress, then concluded), verbatim. */
class CiWorkflowProgressEventTest {
    private val concluded =
        """{"id":"901a81282d9174423c32c38c759478eb36277f7c90ec855ded3ebbb232f15554","pubkey":"f294f947268d6574fffee3d59b99aa9f1b9fa99fa3714e50ac089158b3991103","created_at":1791427794,"kind":39842,"tags":[["a","30617:0461fcbecc4c3374439932d6b8f11269ccdb7cc973ad7a50ae362db135a474dd:soapbox.pub"],["a","30617:3f770d65d3a764a9c5cb503ae123e62ec7598ad035d836e2a810f3877a745b24:soapbox.pub"],["a","30617:781a1527055f74c1f70230f10384609b34548f8ab6a0a6caa74025827f9fdae5:soapbox.pub"],["c","38299e943366ffe0e151c5bd5002146d1a803342"],["w",".ngit/act/workflows/deploy-nsite.yml","83bc660aebfbaefc7151d691772de55e7fa36c22a12ba4cad60aadc810d6787e"],["o","push"],["d","63986297ac25fddafa4d299f9394c77da069455592e3b2811ad24d4587534427"],["status","concluded"],["conclusion","success"],["expiration","1791428394"],["queued_at","1791427494"],["started_at","1791427494"],["q","08e1399572fe37b15897f730a15009ee7597fc34754d89c46c38a9b415015b39","wss://git.shakespeare.diy","f294f947268d6574fffee3d59b99aa9f1b9fa99fa3714e50ac089158b3991103","deploy"],["r","refs/heads/main"]],"content":"","sig":"31a60b3f34e79bc9de82759db0710767108dcc0df020515b80e84c48305fe01e53838bf5a61c8455fd64268a2015bbec4645e500f36718d2c3ce15a03b9783af"}"""

    private val inProgress =
        """{"id":"c5ba3f4864f1c219b2cb01c845b270806c0440e09170f318d6fb44d0d64e416b","pubkey":"f294f947268d6574fffee3d59b99aa9f1b9fa99fa3714e50ac089158b3991103","created_at":1791427494,"kind":39842,"tags":[["a","30617:0461fcbecc4c3374439932d6b8f11269ccdb7cc973ad7a50ae362db135a474dd:soapbox.pub"],["a","30617:3f770d65d3a764a9c5cb503ae123e62ec7598ad035d836e2a810f3877a745b24:soapbox.pub"],["a","30617:781a1527055f74c1f70230f10384609b34548f8ab6a0a6caa74025827f9fdae5:soapbox.pub"],["c","38299e943366ffe0e151c5bd5002146d1a803342"],["w",".ngit/act/workflows/deploy-nsite.yml","83bc660aebfbaefc7151d691772de55e7fa36c22a12ba4cad60aadc810d6787e"],["o","push"],["d","63986297ac25fddafa4d299f9394c77da069455592e3b2811ad24d4587534427"],["status","in_progress"],["expiration","1791428094"],["queued_at","1791427494"],["started_at","1791427494"],["r","refs/heads/main"]],"content":"","sig":"7f8e23d146e82377799bd71a8cddd0912ef5aea6e7b5db6533b474e4aa1a28f29039b7d225b6765158972173356fe026636687587372af216a066b68148d2f21"}"""

    private val coordinator = "f294f947268d6574fffee3d59b99aa9f1b9fa99fa3714e50ac089158b3991103"
    private val runId = "63986297ac25fddafa4d299f9394c77da069455592e3b2811ad24d4587534427"

    @Test
    fun factoryBuildsTheAddressableMarker() {
        val progress = assertIs<CiWorkflowProgressEvent>(Event.fromJson(concluded))
        assertTrue(EventFactory.isKnownKind(CiWorkflowProgressEvent.KIND))
        assertEquals("39842:$coordinator:$runId", progress.address().toValue())
    }

    @Test
    fun readsTheConcludedMarker() {
        val progress = assertIs<CiWorkflowProgressEvent>(Event.fromJson(concluded))
        assertEquals(runId, progress.runId())
        assertEquals(CiWorkflowStatus.CONCLUDED, progress.status())
        assertEquals(CiConclusion.SUCCESS, progress.conclusion())
        assertEquals(CiTrigger.PUSH, progress.trigger())
        assertEquals("refs/heads/main", progress.gitRef())
        assertEquals(listOf("deploy"), progress.jobResults().map { it.jobId })
        assertEquals(1791428394L, progress.validExpiration())
        assertTrue(progress.isLive(now = 1791428000L))
        assertFalse(progress.isLive(now = 1791428394L))
        assertFalse(progress.isPending(now = 1791428000L))
    }

    @Test
    fun readsTheInProgressMarker() {
        val progress = assertIs<CiWorkflowProgressEvent>(Event.fromJson(inProgress))
        assertEquals(CiWorkflowStatus.IN_PROGRESS, progress.status())
        assertNull(progress.conclusion())
        assertEquals(emptyList(), progress.jobResults())
        assertTrue(progress.isPending(now = 1791427600L))
        assertFalse(progress.isPending(now = 1791428094L))
    }

    @Test
    fun graphEdges() {
        val progress = assertIs<CiWorkflowProgressEvent>(Event.fromJson(concluded))
        assertEquals(listOf("08e1399572fe37b15897f730a15009ee7597fc34754d89c46c38a9b415015b39"), progress.linkedEventIds())
        assertEquals(listOf(coordinator), progress.linkedPubKeys())
        assertEquals(3, progress.linkedAddressIds().size)
        assertTrue(progress.linkedAddressIds().all { it.startsWith("30617:") })
    }

    @Test
    fun specRulesTheReaderMirrors() {
        fun progress(vararg tags: Array<String>) =
            assertIs<CiWorkflowProgressEvent>(
                EventFactory.create<Event>("1".repeat(64), coordinator, 1_000L, CiWorkflowProgressEvent.KIND, arrayOf(arrayOf("d", runId), *tags), "", "0".repeat(128)),
            )

        // A conclusion on a still-running marker is not a result.
        assertNull(progress(arrayOf("status", "in_progress"), arrayOf("conclusion", "failure")).conclusion())
        // A queued marker must omit the service-request quote.
        val quote = arrayOf("q", "2".repeat(64), "wss://a.com", "3".repeat(64), "service-request")
        assertNull(progress(arrayOf("status", "queued"), quote).provenance())
        assertEquals(CiProvenanceKind.SERVICE_REQUEST, progress(arrayOf("status", "in_progress"), quote).provenance()?.kind)
        // An expiration beyond 30 minutes, or not after created_at, is not a valid lifetime.
        assertNull(progress(arrayOf("expiration", "${1_000L + 30 * 60 + 1}")).validExpiration())
        assertNull(progress(arrayOf("expiration", "1000")).validExpiration())
        assertEquals(1_000L + 30 * 60, progress(arrayOf("expiration", "${1_000L + 30 * 60}")).validExpiration())
        // An unknown status reads as null, never as running.
        val unknown = progress(arrayOf("status", "paused"), arrayOf("expiration", "2000"))
        assertNull(unknown.status())
        assertFalse(unknown.isPending(now = 1_500L))
        // The queue estimate and in-progress jobs.
        val queued = progress(arrayOf("status", "queued"), arrayOf("queue", "4"), arrayOf("in-progress", "lint", "test"))
        assertEquals(4L, queued.queue())
        assertEquals(listOf("lint", "test"), queued.inProgressJobs())
        assertNull(progress(arrayOf("queue", "-1")).queue())
    }

    @Test
    fun builderRoundTrip() {
        val context =
            CiRunContext(
                repositories = listOf(ATag(30617, "a".repeat(64), "soapbox.pub", null)),
                commits = listOf("38299e943366ffe0e151c5bd5002146d1a803342"),
                workflow = CiWorkflowFile(".ngit/act/workflows/deploy-nsite.yml", "b".repeat(64)),
                pullRequest = CiPullRequestContext("c".repeat(64), "d".repeat(64), 1618, "e".repeat(64), 1619, "d".repeat(64)),
            )
        val jobs = listOf(CiJobResultQuote("f".repeat(64), null, coordinator, "deploy"))
        val provenance = CiProvenanceQuote(CiProvenanceKind.MANUAL_TRIGGER, "1".repeat(64), null, "2".repeat(64))
        val template =
            CiWorkflowProgressEvent.build(
                runId = runId,
                context = context,
                trigger = CiTrigger.MANUAL,
                status = CiWorkflowStatus.IN_PROGRESS,
                // Dropped: only a concluded marker carries a conclusion.
                conclusion = CiConclusion.SUCCESS,
                jobResults = jobs,
                inProgressJobs = listOf("test"),
                provenance = provenance,
                queuedAt = 100L,
                startedAt = 110L,
                createdAt = 120L,
            )
        val progress = assertIs<CiWorkflowProgressEvent>(EventFactory.create<Event>("1".repeat(64), coordinator, template.createdAt, template.kind, template.tags, template.content, "0".repeat(128)))

        assertEquals(runId, progress.runId())
        assertEquals(context.repositories.map { it.toTag() }, progress.repositories().map { it.toTag() })
        assertEquals(context.pullRequest, progress.pullRequest())
        assertEquals(CiWorkflowStatus.IN_PROGRESS, progress.status())
        assertNull(progress.tags.firstOrNull { it[0] == "conclusion" })
        assertEquals(jobs, progress.jobResults())
        assertEquals(listOf("test"), progress.inProgressJobs())
        assertEquals(provenance, progress.provenance())
        assertEquals(100L, progress.queuedAt())
        assertEquals(110L, progress.startedAt())
        assertEquals(120L + CiWorkflowProgressEvent.MAX_EXPIRATION_SECONDS, progress.validExpiration())
    }
}
