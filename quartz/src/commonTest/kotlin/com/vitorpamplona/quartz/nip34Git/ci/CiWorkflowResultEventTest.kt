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
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiConclusion
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiJobResultQuote
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiProvenanceKind
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiProvenanceQuote
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiTrigger
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiWorkflowFile
import com.vitorpamplona.quartz.nip34Git.ci.workflowResult.CiWorkflowResultEvent
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Kind 9842 against real Workflow Results seen on relays (2026-10), verbatim. */
class CiWorkflowResultEventTest {
    // A request-gated pull-request run quoting its Service Request and three Job Results.
    private val prResult =
        """{"id":"d2ad7db8ec840611c86ff856324a46fd2eb314dbb9ee02f1c8b1c65c18401d24","pubkey":"9c14feb6c51985448a350c53ecc4533cfa093b79a1e2079968ca8c344152169a","created_at":1791414105,"kind":9842,"tags":[["a","30617:a008def15796fba9a0d6fab04e8fd57089285d9fd505da5a83fe8aad57a3564d:gitworkshop"],["c","ef7b424f44bef44de0a8dfc92b265bf15c6691a7"],["w",".ngit/act/workflows/ci.yml","fec2d7f771cc72f5ec9440e1c20e0e2d14f654c79a8495b5b47fa5b74e7e6458"],["o","pull_request"],["q","ee6c39e15658c5f9e19028c0be60dcad70bfc161577c4a6426222b02415e47f4","wss://git.nostrhub.io","a008def15796fba9a0d6fab04e8fd57089285d9fd505da5a83fe8aad57a3564d","service-request"],["r","d506951551fa6b6b72071257a47dd141e948568c2c9f1a819916cc79dad2563f"],["conclusion","success"],["queued_at","1791413127"],["started_at","1791413127"],["q","0cb50aab2d7e3c620347f609ffb0f50cc6d1b1b8173b92da0bbb4afb797a33d9","wss://gitnostr.com","9c14feb6c51985448a350c53ecc4533cfa093b79a1e2079968ca8c344152169a","android-lint"],["q","b7c6a6ae331ab08d8328c763d93d073a5266a3cc6e96131a0436d9280a9913f2","wss://gitnostr.com","9c14feb6c51985448a350c53ecc4533cfa093b79a1e2079968ca8c344152169a","test"],["q","538ec372023bfc50864b8b75bb2e5347a375b80bed839b682cee470b27db42ed","wss://gitnostr.com","9c14feb6c51985448a350c53ecc4533cfa093b79a1e2079968ca8c344152169a","deploy-nsite-preview"],["E","f0d69a9e2a8db46e1e08589b1f89047bf0bf6bfdcd0ec4e104bcfa7e40285d29"],["K","1618"],["P","43185edecb675892824b1a37a57f3e407fbde2eda7201a3829b8cf4ba7c5b4f0"],["e","f0d69a9e2a8db46e1e08589b1f89047bf0bf6bfdcd0ec4e104bcfa7e40285d29"],["k","1618"],["p","43185edecb675892824b1a37a57f3e407fbde2eda7201a3829b8cf4ba7c5b4f0"]],"content":"","sig":"93649e869fdf4de1f438bea871698c4c99a82b2ba636efe298afbbaaf7163c205b9a908167ca8e9382b4c8c8e9aae9a18637718ecf606c0912868bfb5e134cb7"}"""

    // An automatic push run: run id `r` and git-ref `r` side by side.
    private val pushResult =
        """{"id":"a572909fa6ba7318fa45b7541b92ff9f5ed31623b1c704a1a52fa875ddb17eaf","pubkey":"9e086849862369ac74314dbd4701dc3dee79058203137df4c14d5e9ca7df7391","created_at":1791413327,"kind":9842,"tags":[["a","30617:b3c95ce33dfa84326611e8b7a9c10b78df28754c38b106a4bc0196b9be5f4e4a:wyrd"],["a","30617:86a314a7ef4aa4fb4e00d738a7bec5fc96ac1312246c7c25888ae609046e6965:wyrd"],["a","30617:d84afa5ba1239500e03ce4d6a2b1e5eef1696a814c3f9cc25b50721bedef0a0f:wyrd"],["c","bb5a386222890f018af7240c27a0e3e550fa61d6"],["w",".ngit/act/workflows/workflow.yml","ae6dd528f57e331ae5f0575153828af3ccb45b502e7a31da34d9c0a2876905d1"],["o","push"],["r","c092eb10ec4d4003a3184d519903519efd7cddd8fbce46d0bc4681d8e86dd112"],["conclusion","success"],["queued_at","1791412633"],["started_at","1791412739"],["q","7232e31f0ea3c8e97912fbb22c6e0363d6bcbfcd81fe07a78d70fe7cf40e764c","wss://grasp.t5.st","9e086849862369ac74314dbd4701dc3dee79058203137df4c14d5e9ca7df7391","rust"],["q","9b1a514fb176d38a5be70e1b55a225c6b9667cefe796fb1d0138f70fd59be71b","wss://grasp.t5.st","9e086849862369ac74314dbd4701dc3dee79058203137df4c14d5e9ca7df7391","nix"],["r","refs/heads/master"]],"content":"","sig":"4e96ba260e6666f395e1d11b4dcdb1bb2321aadf7020b783cacac7a43c85ca6b423b2cf9f9d10889276592cf0a9a9ca3db075f613438ee40e3e7928228c41955"}"""

    private val coordinator = "9c14feb6c51985448a350c53ecc4533cfa093b79a1e2079968ca8c344152169a"
    private val maintainer = "a008def15796fba9a0d6fab04e8fd57089285d9fd505da5a83fe8aad57a3564d"
    private val serviceRequest = "ee6c39e15658c5f9e19028c0be60dcad70bfc161577c4a6426222b02415e47f4"
    private val pr = "f0d69a9e2a8db46e1e08589b1f89047bf0bf6bfdcd0ec4e104bcfa7e40285d29"
    private val prAuthor = "43185edecb675892824b1a37a57f3e407fbde2eda7201a3829b8cf4ba7c5b4f0"

    @Test
    fun factoryBuildsTheWorkflowResult() {
        assertIs<CiWorkflowResultEvent>(Event.fromJson(prResult))
        assertTrue(EventFactory.isKnownKind(CiWorkflowResultEvent.KIND))
        assertFalse(Event.fromJson(prResult) is SearchableEvent)
    }

    @Test
    fun readsARequestGatedPullRequestRun() {
        val result = assertIs<CiWorkflowResultEvent>(Event.fromJson(prResult))
        assertEquals("d506951551fa6b6b72071257a47dd141e948568c2c9f1a819916cc79dad2563f", result.runId())
        assertEquals(CiTrigger.PULL_REQUEST, result.trigger())
        assertEquals(CiConclusion.SUCCESS, result.conclusion())
        assertNull(result.gitRef())
        assertEquals(pr, result.pullRequest()?.pullRequestId)

        val provenance = result.provenance()
        assertEquals(CiProvenanceKind.SERVICE_REQUEST, provenance?.kind)
        assertEquals(serviceRequest, provenance?.eventId)
        assertEquals(maintainer, provenance?.requester)
        assertEquals("wss://git.nostrhub.io/", provenance?.relay?.url)

        assertEquals(listOf("android-lint", "test", "deploy-nsite-preview"), result.jobResults().map { it.jobId })
        assertEquals(coordinator, result.jobResults().first().publisher)
        assertEquals("0cb50aab2d7e3c620347f609ffb0f50cc6d1b1b8173b92da0bbb4afb797a33d9", result.jobResults().first().eventId)
    }

    @Test
    fun readsAnAutomaticPushRun() {
        val result = assertIs<CiWorkflowResultEvent>(Event.fromJson(pushResult))
        // The `refs/` value is the ref; the other `r` is the run id.
        assertEquals("c092eb10ec4d4003a3184d519903519efd7cddd8fbce46d0bc4681d8e86dd112", result.runId())
        assertEquals("refs/heads/master", result.gitRef())
        assertEquals(3, result.repositories().size)
        assertNull(result.provenance())
        assertNull(result.pullRequest())
        assertEquals(listOf("rust", "nix"), result.jobResults().map { it.jobId })
        assertEquals(1791412739L, result.startedAt())
    }

    @Test
    fun graphEdges() {
        val result = assertIs<CiWorkflowResultEvent>(Event.fromJson(prResult))
        assertEquals(
            listOf(
                pr,
                pr,
                serviceRequest,
                "0cb50aab2d7e3c620347f609ffb0f50cc6d1b1b8173b92da0bbb4afb797a33d9",
                "b7c6a6ae331ab08d8328c763d93d073a5266a3cc6e96131a0436d9280a9913f2",
                "538ec372023bfc50864b8b75bb2e5347a375b80bed839b682cee470b27db42ed",
            ),
            result.linkedEventIds(),
        )
        // ROOT_AUTHOR, PARENT_AUTHOR, the requester, and the compute provider of each job.
        assertEquals(listOf(prAuthor, prAuthor, maintainer, coordinator, coordinator, coordinator), result.linkedPubKeys())
        assertEquals(listOf("30617:$maintainer:gitworkshop"), result.linkedAddressIds())
        assertEquals(4, result.eventHints().size)
        // Run ids and commit ids are not edges.
        assertFalse(result.linkedEventIds().contains("d506951551fa6b6b72071257a47dd141e948568c2c9f1a819916cc79dad2563f"))
    }

    @Test
    fun ambiguousShapesReadAsNull() {
        val tags =
            arrayOf(
                arrayOf("r", "run-1"),
                arrayOf("r", "run-2"),
                arrayOf("o", "push"),
                arrayOf("o", "schedule"),
                arrayOf("q", "1".repeat(64), "wss://a.com", "2".repeat(64), "manual-trigger"),
                arrayOf("q", "3".repeat(64), "wss://a.com", "2".repeat(64), "service-request"),
                // A provenance quote without its required requester pubkey is not one.
                arrayOf("q", "4".repeat(64), "wss://a.com", "", "manual-trigger"),
                // An unmarked quote is not a job.
                arrayOf("q", "5".repeat(64), "wss://a.com"),
            )
        val result = assertIs<CiWorkflowResultEvent>(EventFactory.create<Event>("1".repeat(64), "2".repeat(64), 1L, CiWorkflowResultEvent.KIND, tags, "", "0".repeat(128)))
        assertNull(result.runId())
        assertNull(result.trigger())
        assertNull(result.provenance())
        assertEquals(emptyList(), result.jobResults())
    }

    @Test
    fun builderRoundTrip() {
        val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.ngit.dev")
        val context =
            CiRunContext(
                repositories = listOf(ATag(30617, maintainer, "gitworkshop", null), ATag(30617, prAuthor, "gitworkshop", relay)),
                commits = listOf("ef7b424f44bef44de0a8dfc92b265bf15c6691a7"),
                workflow = CiWorkflowFile(".ngit/act/workflows/ci.yml", "f".repeat(64)),
                gitRef = "refs/heads/main",
            )
        val jobs = listOf(CiJobResultQuote("a".repeat(64), relay, coordinator, "lint"), CiJobResultQuote("b".repeat(64), null, null, "test"))
        val provenance = CiProvenanceQuote(CiProvenanceKind.SERVICE_REQUEST, serviceRequest, relay, maintainer)
        val template = CiWorkflowResultEvent.build(context, CiTrigger.PUSH, "run-42", CiConclusion.TIMED_OUT, jobs, provenance, 5L, 6L, createdAt = 7L)
        val result = assertIs<CiWorkflowResultEvent>(EventFactory.create<Event>("1".repeat(64), coordinator, template.createdAt, template.kind, template.tags, template.content, "0".repeat(128)))

        assertEquals("", result.content)
        assertEquals(context.repositories.map { it.toTag() }, result.repositories().map { it.toTag() })
        assertEquals("wss://relay.ngit.dev/", result.repositories()[1].relay?.url)
        assertEquals(context.commits, result.commits())
        assertEquals(context.workflow, result.workflow())
        assertEquals(CiTrigger.PUSH, result.trigger())
        assertEquals("refs/heads/main", result.gitRef())
        assertEquals("run-42", result.runId())
        assertEquals(CiConclusion.TIMED_OUT, result.conclusion())
        assertEquals(jobs, result.jobResults())
        assertEquals(provenance, result.provenance())
        assertEquals(5L, result.queuedAt())
        assertEquals(6L, result.startedAt())
    }
}
