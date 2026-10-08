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

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip34Git.ci.jobResult.CiJobResultEvent
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiArtifact
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiConclusion
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiJobOutput
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiOmittedOutput
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiOutputOmissionReason
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiProvenanceKind
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiProvenanceQuote
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiTrigger
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiWorkflowFile
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Kind 9841 against real Job Results seen on relays (2026-10); the log tails are trimmed. */
class CiJobResultEventTest {
    // A pull-request run's job, quoting its Workflow Progress address.
    private val prJob =
        """{"id":"0cb50aab2d7e3c620347f609ffb0f50cc6d1b1b8173b92da0bbb4afb797a33d9","pubkey":"9c14feb6c51985448a350c53ecc4533cfa093b79a1e2079968ca8c344152169a","created_at":1791414104,"kind":9841,"tags":[["a","30617:a008def15796fba9a0d6fab04e8fd57089285d9fd505da5a83fe8aad57a3564d:gitworkshop"],["c","ef7b424f44bef44de0a8dfc92b265bf15c6691a7"],["w",".ngit/act/workflows/ci.yml","fec2d7f771cc72f5ec9440e1c20e0e2d14f654c79a8495b5b47fa5b74e7e6458"],["o","pull_request"],["job","android-lint"],["name","CI and deployments/android-lint"],["conclusion","success"],["logs","https://blossom.ditto.pub/07dea510a5bb7282e2621827c3b86771b5c2d075e5f94c52af1c378c40c337ad.txt"],["queued_at","1791413127"],["started_at","1791413127"],["q","39842:9c14feb6c51985448a350c53ecc4533cfa093b79a1e2079968ca8c344152169a:d506951551fa6b6b72071257a47dd141e948568c2c9f1a819916cc79dad2563f","wss://gitnostr.com"],["E","f0d69a9e2a8db46e1e08589b1f89047bf0bf6bfdcd0ec4e104bcfa7e40285d29"],["K","1618"],["P","43185edecb675892824b1a37a57f3e407fbde2eda7201a3829b8cf4ba7c5b4f0"],["e","f0d69a9e2a8db46e1e08589b1f89047bf0bf6bfdcd0ec4e104bcfa7e40285d29"],["k","1618"],["p","43185edecb675892824b1a37a57f3e407fbde2eda7201a3829b8cf4ba7c5b4f0"]],"content":"[log-tail omitted=177040]\n  docker exec cmd=[node cache-save/index.js] user= workdir=","sig":"8212bfd12fbcc65ed8ef3fe265f2ae982b0a02038b7e6d37cbccba8d93a0185b078f0da55af197692dc520b87e481c7043c9841ea1f561bf50879c037bde86b7"}"""

    // A tag-push release job with artifacts and an annotated-tag `c`.
    private val releaseJob =
        """{"id":"712310604e3fed6af228b76bcaf10bf4ada89fc6746b461166e36cbd71e08eb7","pubkey":"f294f947268d6574fffee3d59b99aa9f1b9fa99fa3714e50ac089158b3991103","created_at":1791415322,"kind":9841,"tags":[["a","30617:0461fcbecc4c3374439932d6b8f11269ccdb7cc973ad7a50ae362db135a474dd:npanel"],["a","30617:781a1527055f74c1f70230f10384609b34548f8ab6a0a6caa74025827f9fdae5:npanel"],["c","e253992cab4a7e81d860c13ad8b12619d1d319ec"],["c","d3c405d221844c0ee421b1e389f1ed02e2e32f9e"],["w",".ngit/act/workflows/release.yml","c6a330b14b698ea04c1c44649b5e4d5470d634c1c4487c9d4082ff7e7bced3d1"],["o","push"],["job","release"],["name","Release/release"],["conclusion","success"],["logs","https://blossom.ditto.pub/98c0e78f5c98ae031eebb3f91d2c5c0f6e64c49606f306120932e4a5d0a2260e.txt"],["artifact","https://blossom.ditto.pub/bea4d43c130a4c26287aab87375115c8cd6296cf61855e26cd37d89424c44b4a","SHA256SUMS","npanel-0.5.0"],["artifact","https://blossom.ditto.pub/de95dc237c395399577243374f972645381c9ee54bbe772cc37e9782138a5d2c.0-linux-aarch64","npanel-0.5.0-linux-aarch64","npanel-0.5.0"],["queued_at","1791414471"],["started_at","1791414471"],["r","refs/tags/v0.5.0"]],"content":"[log-tail omitted=27428]\nCache Size: ~205 MB","sig":"1fce23bb034c83a92d0f0e430e75fca847bab0f5fb8f07192d60287269c9ec1ebe158238e267a1d2d8268314c4db16b85a60d0819a3c37eea4eb3fc0863bfe9c"}"""

    private val coordinator = "9c14feb6c51985448a350c53ecc4533cfa093b79a1e2079968ca8c344152169a"
    private val runId = "d506951551fa6b6b72071257a47dd141e948568c2c9f1a819916cc79dad2563f"
    private val pr = "f0d69a9e2a8db46e1e08589b1f89047bf0bf6bfdcd0ec4e104bcfa7e40285d29"
    private val prAuthor = "43185edecb675892824b1a37a57f3e407fbde2eda7201a3829b8cf4ba7c5b4f0"
    private val gitworkshop = "30617:a008def15796fba9a0d6fab04e8fd57089285d9fd505da5a83fe8aad57a3564d:gitworkshop"

    @Test
    fun factoryBuildsTheJobResult() {
        assertIs<CiJobResultEvent>(Event.fromJson(prJob))
        assertTrue(EventFactory.isKnownKind(CiJobResultEvent.KIND))
    }

    @Test
    fun readsAPullRequestJob() {
        val job = assertIs<CiJobResultEvent>(Event.fromJson(prJob))
        assertEquals(listOf(gitworkshop), job.repositories().map { it.toTag() })
        assertEquals("ef7b424f44bef44de0a8dfc92b265bf15c6691a7", job.commit())
        assertEquals(CiWorkflowFile(".ngit/act/workflows/ci.yml", "fec2d7f771cc72f5ec9440e1c20e0e2d14f654c79a8495b5b47fa5b74e7e6458"), job.workflow())
        assertEquals(CiTrigger.PULL_REQUEST, job.trigger())
        assertNull(job.gitRef())
        assertEquals(CiPullRequestContext(pr, prAuthor, 1618, pr, 1618, prAuthor), job.pullRequest())
        assertEquals("android-lint", job.jobId())
        assertEquals("CI and deployments/android-lint", job.name())
        assertEquals(CiConclusion.SUCCESS, job.conclusion())
        assertEquals(1791413127L, job.queuedAt())
        assertEquals(1791413127L, job.startedAt())
        assertEquals(177040L, job.omittedLogBytes())

        val run = job.workflowRun()
        assertEquals(39842, run?.kind)
        assertEquals(coordinator, run?.pubKeyHex)
        assertEquals(runId, run?.dTag)
        assertEquals("wss://gitnostr.com/", run?.relay?.url)
        assertNull(job.manualTrigger())
    }

    @Test
    fun readsAReleaseJobWithArtifacts() {
        val job = assertIs<CiJobResultEvent>(Event.fromJson(releaseJob))
        assertEquals(2, job.repositories().size)
        // The commit first, the annotated tag object second.
        assertEquals(listOf("e253992cab4a7e81d860c13ad8b12619d1d319ec", "d3c405d221844c0ee421b1e389f1ed02e2e32f9e"), job.commits())
        assertEquals("refs/tags/v0.5.0", job.gitRef())
        assertNull(job.pullRequest())
        assertEquals(
            CiArtifact("https://blossom.ditto.pub/bea4d43c130a4c26287aab87375115c8cd6296cf61855e26cd37d89424c44b4a", "SHA256SUMS", "npanel-0.5.0"),
            job.artifacts().first(),
        )
        assertEquals(2, job.artifacts().size)
        // This publisher omitted the run quote: the association is unknown, not guessed.
        assertNull(job.workflowRun())
    }

    @Test
    fun graphEdges() {
        val job = assertIs<CiJobResultEvent>(Event.fromJson(prJob))
        // E and e are the same PR here (a run on the PR's own revision).
        assertEquals(listOf(pr, pr), job.linkedEventIds())
        assertEquals(listOf(prAuthor, prAuthor), job.linkedPubKeys())
        assertEquals(listOf(gitworkshop, "39842:$coordinator:$runId"), job.linkedAddressIds())
        assertEquals(listOf("39842:$coordinator:$runId" to "wss://gitnostr.com/"), job.addressHints().map { it.addressId to it.relay.url })
    }

    @Test
    fun theLogTailIsNotIndexed() {
        assertFalse(Event.fromJson(prJob) is SearchableEvent)
    }

    @Test
    fun malformedTagsAreSkipped() {
        val job =
            assertIs<CiJobResultEvent>(
                EventFactory.create<Event>(
                    "1".repeat(64),
                    "2".repeat(64),
                    1L,
                    CiJobResultEvent.KIND,
                    arrayOf(
                        arrayOf("a", "30023:${"3".repeat(64)}:not-a-repo"),
                        arrayOf("a", "30617:${"3".repeat(64)}:"),
                        arrayOf("c", "not-a-commit"),
                        arrayOf("w", ".ngit/act/workflows/ci.yml"),
                        arrayOf("o", "nightly"),
                        arrayOf("conclusion", "exploded"),
                        arrayOf("queued_at", "soon"),
                        arrayOf("exit_code", "x"),
                        arrayOf("q", "39842:${"4".repeat(64)}:run-a"),
                        arrayOf("q", "39842:${"4".repeat(64)}:run-b"),
                        arrayOf("output", "only-name"),
                        arrayOf("output", "dup", "v"),
                        arrayOf("output-omitted", "dup", "missing"),
                        arrayOf("artifact"),
                    ),
                    "",
                    "0".repeat(128),
                ),
            )
        assertEquals(emptyList(), job.repositories())
        assertEquals(emptyList(), job.commits())
        assertNull(job.workflow())
        assertNull(job.trigger())
        assertNull(job.conclusion())
        assertNull(job.queuedAt())
        assertNull(job.exitCode())
        // Two run addresses are ambiguous.
        assertNull(job.workflowRun())
        // A name both valued and omitted is dropped from both.
        assertEquals(emptyList(), job.outputs())
        assertEquals(emptyList(), job.omittedOutputs())
        assertEquals(emptyList(), job.artifacts())
        assertNull(job.omittedLogBytes())
        // Non-30617 and empty-id `a` tags are not repository edges.
        assertEquals(listOf("39842:${"4".repeat(64)}:run-a", "39842:${"4".repeat(64)}:run-b"), job.linkedAddressIds())
    }

    @Test
    fun aServiceRequestQuoteIsNotAJobsProvenance() {
        val requester = "5".repeat(64)
        val tags =
            arrayOf(
                arrayOf("q", "6".repeat(64), "wss://relay.ngit.dev", requester, "service-request"),
            )
        val job = assertIs<CiJobResultEvent>(EventFactory.create<Event>("1".repeat(64), "2".repeat(64), 1L, CiJobResultEvent.KIND, tags, "", "0".repeat(128)))
        assertNull(job.manualTrigger())

        val manual = arrayOf(arrayOf("q", "6".repeat(64), "wss://relay.ngit.dev", requester, "manual-trigger"))
        val replay = assertIs<CiJobResultEvent>(EventFactory.create<Event>("1".repeat(64), "2".repeat(64), 1L, CiJobResultEvent.KIND, manual, "", "0".repeat(128)))
        assertEquals(CiProvenanceKind.MANUAL_TRIGGER, replay.manualTrigger()?.kind)
        assertEquals(requester, replay.manualTrigger()?.requester)
        assertTrue(replay.linkedPubKeys().contains(requester))
    }

    @Test
    fun builderRoundTrip() {
        val repo = ATag(30617, "a".repeat(64), "my-repo", null)
        val context =
            CiRunContext(
                repositories = listOf(repo),
                commits = listOf("b".repeat(40)),
                workflow = CiWorkflowFile(".ngit/act/workflows/ci.yml", "c".repeat(64)),
                pullRequest = CiPullRequestContext(pr, prAuthor, 1618, "d".repeat(64), 1619, prAuthor),
            )
        val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.ngit.dev")
        val manual = CiProvenanceQuote(CiProvenanceKind.MANUAL_TRIGGER, "e".repeat(64), relay, "f".repeat(64))
        val template =
            CiJobResultEvent.build(
                logTail = "[log-tail omitted=12]\nok",
                context = context,
                trigger = CiTrigger.MANUAL,
                workflowRun = Address(39842, coordinator, runId),
                workflowRunRelay = relay,
                jobId = "test",
                conclusion = CiConclusion.FAILURE,
                logsUrl = "https://blossom.example/log.txt",
                name = "CI/test",
                artifacts = listOf(CiArtifact("https://blossom.example/a", "a.bin", "bundle")),
                outputs = listOf(CiJobOutput("preview", ""), CiJobOutput("url", "https://x")),
                omittedOutputs = listOf(CiOmittedOutput("big", CiOutputOmissionReason.OVERSIZED)),
                queuedAt = 10L,
                startedAt = 20L,
                exitCode = 1,
                runsOn = listOf("ubuntu-latest"),
                manualTrigger = manual,
                createdAt = 30L,
            )
        val job = assertIs<CiJobResultEvent>(EventFactory.create<Event>("1".repeat(64), "2".repeat(64), template.createdAt, template.kind, template.tags, template.content, "0".repeat(128)))

        assertEquals(listOf(repo.toTag()), job.repositories().map { it.toTag() })
        assertEquals(context.commits, job.commits())
        assertEquals(context.workflow, job.workflow())
        assertEquals(CiTrigger.MANUAL, job.trigger())
        assertEquals(context.pullRequest, job.pullRequest())
        assertEquals("39842:$coordinator:$runId", job.workflowRun()?.toTag())
        assertEquals("test", job.jobId())
        assertEquals("CI/test", job.name())
        assertEquals(CiConclusion.FAILURE, job.conclusion())
        assertEquals("https://blossom.example/log.txt", job.logsUrl())
        assertEquals(listOf(CiArtifact("https://blossom.example/a", "a.bin", "bundle")), job.artifacts())
        assertEquals(listOf(CiJobOutput("preview", ""), CiJobOutput("url", "https://x")), job.outputs())
        assertEquals(listOf(CiOmittedOutput("big", CiOutputOmissionReason.OVERSIZED)), job.omittedOutputs())
        assertEquals(10L, job.queuedAt())
        assertEquals(20L, job.startedAt())
        assertEquals(1, job.exitCode())
        assertEquals(listOf("ubuntu-latest"), job.runsOn())
        assertEquals(manual, job.manualTrigger())
        assertEquals(12L, job.omittedLogBytes())
    }
}
