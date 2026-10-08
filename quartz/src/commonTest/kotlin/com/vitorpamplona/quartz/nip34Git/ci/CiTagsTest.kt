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

import com.vitorpamplona.quartz.nip34Git.ci.tags.AdmissionTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.ArtifactTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.BillingTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiAdmissionPolicy
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiArtifact
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiBillingPolicy
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiConclusion
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiExecutionPolicy
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiOutputOmissionReason
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiTrigger
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiWorkflowStatus
import com.vitorpamplona.quartz.nip34Git.ci.tags.CommitIdTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.ConclusionTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.ExecutionTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.GitRefTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.JobResultQuoteTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.OutputOmittedTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.ProvenanceQuoteTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.RepositoryTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.RunsOnTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.TriggerTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.WorkflowRunIdTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.WorkflowRunQuoteTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.WorkflowStatusTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.WorkflowTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The shared CI tag layer: every enum's unknown path, and parse/assemble agreement. */
class CiTagsTest {
    @Test
    fun everyEnumRoundTripsAndRejectsUnknownValues() {
        CiConclusion.entries.forEach { assertEquals(it, ConclusionTag.parse(ConclusionTag.assemble(it))) }
        CiTrigger.entries.forEach { assertEquals(it, TriggerTag.parse(TriggerTag.assemble(it))) }
        CiWorkflowStatus.entries.forEach { assertEquals(it, WorkflowStatusTag.parse(WorkflowStatusTag.assemble(it))) }
        CiOutputOmissionReason.entries.forEach { assertEquals(it, OutputOmittedTag.parse(OutputOmittedTag.assemble("n", it))?.reason) }

        assertNull(ConclusionTag.parse(arrayOf("conclusion", "Success")))
        assertNull(ConclusionTag.parse(arrayOf("conclusion")))
        assertNull(TriggerTag.parse(arrayOf("o", "tag")))
        assertNull(WorkflowStatusTag.parse(arrayOf("status", "done")))
        assertNull(OutputOmittedTag.parse(arrayOf("output-omitted", "n", "lost"))?.reason)
    }

    @Test
    fun policyEnumsHaveAnExplicitUnknownThatIsNeverPermissive() {
        CiAdmissionPolicy.entries.filter { it != CiAdmissionPolicy.UNKNOWN }.forEach { assertEquals(it, AdmissionTag.parse(AdmissionTag.assemble(it))) }
        CiExecutionPolicy.entries.filter { it != CiExecutionPolicy.UNKNOWN }.forEach { assertEquals(it, ExecutionTag.parse(ExecutionTag.assemble(it))) }
        CiBillingPolicy.entries.filter { it != CiBillingPolicy.UNKNOWN }.forEach { assertEquals(it, BillingTag.parse(BillingTag.assemble(it))) }

        assertEquals(CiAdmissionPolicy.UNKNOWN, AdmissionTag.parse(arrayOf("M", "OPEN")))
        assertEquals(CiExecutionPolicy.UNKNOWN, ExecutionTag.parse(arrayOf("X", "")))
        assertEquals(CiBillingPolicy.UNKNOWN, BillingTag.parse(arrayOf("B", "free")))
        assertNull(AdmissionTag.parse(arrayOf("M")))
    }

    @Test
    fun commitIdsAreGitObjectIds() {
        assertEquals("a".repeat(40), CommitIdTag.parse(arrayOf("c", "a".repeat(40))))
        assertEquals("a".repeat(64), CommitIdTag.parse(arrayOf("c", "a".repeat(64))))
        assertNull(CommitIdTag.parse(arrayOf("c", "a".repeat(41))))
        assertNull(CommitIdTag.parse(arrayOf("c", "z".repeat(40))))
        assertFalse(CommitIdTag.isTag(arrayOf("c")))
    }

    @Test
    fun theTwoMeaningsOfR() {
        assertEquals("refs/heads/main", GitRefTag.parse(arrayOf("r", "refs/heads/main")))
        assertNull(GitRefTag.parse(arrayOf("r", "run-1")))
        assertEquals("run-1", WorkflowRunIdTag.parse(arrayOf("r", "run-1")))
        assertNull(WorkflowRunIdTag.parse(arrayOf("r", "refs/tags/v1")))
        assertNull(WorkflowRunIdTag.parse(arrayOf("r", "")))
    }

    @Test
    fun workflowNeedsAPathAndAHash() {
        assertNull(WorkflowTag.parse(arrayOf("w", ".ngit/ci.yml")))
        assertNull(WorkflowTag.parse(arrayOf("w", ".ngit/ci.yml", "abc")))
        assertNull(WorkflowTag.parse(arrayOf("w", "", "a".repeat(64))))
        assertTrue(WorkflowTag.isTag(arrayOf("w", ".ngit/ci.yml", "a".repeat(64))))
    }

    @Test
    fun repositoryTagsAreOnly30617WithAnId() {
        val pk = "a".repeat(64)
        assertEquals("30617:$pk:repo", RepositoryTag.parseAddressId(arrayOf("a", "30617:$pk:repo")))
        assertNull(RepositoryTag.parseAddressId(arrayOf("a", "30618:$pk:repo")))
        assertNull(RepositoryTag.parseAddressId(arrayOf("a", "30617:$pk:")))
        assertNull(RepositoryTag.parseAddressId(arrayOf("a", "30617:short:repo")))
        assertNull(RepositoryTag.parseAsHint(arrayOf("a", "30617:$pk:repo")))
        assertEquals("wss://nos.lol/", RepositoryTag.parseAsHint(arrayOf("a", "30617:$pk:repo", "wss://nos.lol"))?.relay?.url)
    }

    @Test
    fun quoteRolesAreToldApartByTheirMarker() {
        val id = "1".repeat(64)
        val pk = "2".repeat(64)
        val job = arrayOf("q", id, "wss://a.com", pk, "lint")
        val manual = arrayOf("q", id, "wss://a.com", pk, "manual-trigger")
        val run = arrayOf("q", "39842:$pk:run-1", "wss://a.com")

        assertEquals("lint", JobResultQuoteTag.parse(job)?.jobId)
        assertNull(ProvenanceQuoteTag.parse(job))
        assertNull(JobResultQuoteTag.parse(manual))
        assertEquals(pk, ProvenanceQuoteTag.parse(manual)?.requester)
        assertNull(JobResultQuoteTag.parse(run))
        assertNull(ProvenanceQuoteTag.parse(run))
        assertEquals("39842:$pk:run-1", WorkflowRunQuoteTag.parseAddressId(run))
        assertNull(WorkflowRunQuoteTag.parseAddressId(arrayOf("q", "30617:$pk:repo")))
        assertNull(WorkflowRunQuoteTag.parseAddressId(arrayOf("q", "39842:$pk:")))

        // A job quote's publisher is optional; an empty relay slot reads as no relay.
        val bare = JobResultQuoteTag.parse(arrayOf("q", id, "", "", "test"))
        assertNull(bare?.publisher)
        assertNull(bare?.relay)
        assertEquals(bare, bare?.let { JobResultQuoteTag.parse(JobResultQuoteTag.assemble(it)) })
    }

    @Test
    fun smallTagsTolerateMissingValues() {
        assertEquals(CiArtifact("https://b/x", null, null), ArtifactTag.parse(arrayOf("artifact", "https://b/x")))
        assertEquals(listOf("artifact", "https://b/x", "", "bundle"), ArtifactTag.assemble("https://b/x", null, "bundle").toList())
        assertNull(RunsOnTag.parse(arrayOf("runs_on")))
        assertNull(RunsOnTag.parse(arrayOf("runs_on", "")))
        assertEquals(listOf("ubuntu-latest", "self-hosted"), RunsOnTag.parse(arrayOf("runs_on", "ubuntu-latest", "self-hosted")))
    }
}
