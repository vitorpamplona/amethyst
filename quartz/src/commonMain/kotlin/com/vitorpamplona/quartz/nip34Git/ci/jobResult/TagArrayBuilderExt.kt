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
package com.vitorpamplona.quartz.nip34Git.ci.jobResult

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip34Git.ci.tags.ArtifactTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiArtifact
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiJobOutput
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiOmittedOutput
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiOutputOmissionReason
import com.vitorpamplona.quartz.nip34Git.ci.tags.ExitCodeTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.JobTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.LogsTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.OutputOmittedTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.OutputTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.RunsOnTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.WorkflowRunQuoteTag
import com.vitorpamplona.quartz.nip34Git.repository.tags.NameTag

/** `add`, not `addUnique`: `q` also carries the optional `manual-trigger` provenance quote. */
fun TagArrayBuilder<CiJobResultEvent>.workflowRun(
    address: Address,
    relay: NormalizedRelayUrl?,
) = add(WorkflowRunQuoteTag.assemble(address, relay))

fun TagArrayBuilder<CiJobResultEvent>.jobId(jobId: String) = addUnique(JobTag.assemble(jobId))

fun TagArrayBuilder<CiJobResultEvent>.jobName(name: String) = addUnique(NameTag.assemble(name))

fun TagArrayBuilder<CiJobResultEvent>.logsUrl(url: String) = addUnique(LogsTag.assemble(url))

fun TagArrayBuilder<CiJobResultEvent>.artifacts(artifacts: List<CiArtifact>) = addAll(artifacts.map { ArtifactTag.assemble(it) })

fun TagArrayBuilder<CiJobResultEvent>.outputs(outputs: List<CiJobOutput>) = addAll(outputs.map { OutputTag.assemble(it) })

/** Omitted outputs with an unknown reason are skipped: a publisher must state one of the three. */
fun TagArrayBuilder<CiJobResultEvent>.omittedOutputs(omitted: List<CiOmittedOutput>) = addAll(omitted.mapNotNull { o -> o.reason?.let { OutputOmittedTag.assemble(o.name, it) } })

fun TagArrayBuilder<CiJobResultEvent>.omittedOutput(
    name: String,
    reason: CiOutputOmissionReason,
) = add(OutputOmittedTag.assemble(name, reason))

fun TagArrayBuilder<CiJobResultEvent>.exitCode(code: Int) = addUnique(ExitCodeTag.assemble(code))

fun TagArrayBuilder<CiJobResultEvent>.runsOn(labels: List<String>) = addUnique(RunsOnTag.assemble(labels))
