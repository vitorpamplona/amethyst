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

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip34Git.ci.ciSingleOrNull
import com.vitorpamplona.quartz.nip34Git.ci.tags.ArtifactTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiJobOutput
import com.vitorpamplona.quartz.nip34Git.ci.tags.CiOmittedOutput
import com.vitorpamplona.quartz.nip34Git.ci.tags.ExitCodeTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.JobTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.LogsTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.OutputOmittedTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.OutputTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.RunsOnTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.WorkflowRunQuoteTag
import com.vitorpamplona.quartz.nip34Git.repository.tags.NameTag

/**
 * The run a Job Result belongs to. ngit requires exactly one quoted Workflow Progress address so
 * the association stays unambiguous; zero or several read as null.
 */
fun TagArray.workflowRun() = ciSingleOrNull(WorkflowRunQuoteTag::parse)

fun TagArray.jobId() = ciSingleOrNull(JobTag::parse)

/** The job's human-readable name; the generic `name` tag, read with the NIP-34 repository parser. */
fun TagArray.jobName() = ciSingleOrNull(NameTag::parse)

fun TagArray.logsUrl() = ciSingleOrNull(LogsTag::parse)

fun TagArray.artifacts() = mapNotNull(ArtifactTag::parse)

/**
 * The public scalar outputs. A name that also appears in `output-omitted` — which the spec forbids
 * — is dropped from both, since neither claim can be trusted over the other.
 */
fun TagArray.outputs(): List<CiJobOutput> {
    val omitted = mapNotNull { OutputOmittedTag.parse(it)?.name }.toSet()
    return mapNotNull(OutputTag::parse).filter { it.name !in omitted }
}

fun TagArray.omittedOutputs(): List<CiOmittedOutput> {
    val valued = mapNotNull { OutputTag.parse(it)?.name }.toSet()
    return mapNotNull(OutputOmittedTag::parse).filter { it.name !in valued }
}

fun TagArray.exitCode() = ciSingleOrNull(ExitCodeTag::parse)

fun TagArray.runsOn() = firstNotNullOfOrNull(RunsOnTag::parse) ?: emptyList()

private val LOG_TAIL_OMITTED = Regex("^\\[log-tail omitted=(\\d+)\\]")

/** The `<bytes>` of a `[log-tail omitted=<bytes>]` prefix on a Job Result's content. */
internal fun String.omittedLogBytes(): Long? =
    LOG_TAIL_OMITTED
        .find(this)
        ?.groupValues
        ?.get(1)
        ?.toLongOrNull()
