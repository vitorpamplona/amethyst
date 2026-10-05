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
package com.vitorpamplona.amethyst.commons.observer

import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The editor's pass: hands the stories that lead the paper to the on-device
 * model, one request at a time, and folds every answer that passes
 * [ObserverCopy]'s checks back into the edition.
 *
 * Order is the page's order — lead, top stories, conversations and topics,
 * the biggest posts further down — so if the reader opens the paper early, or
 * the model is slow, what has been written is what they see first. The
 * front-page brief goes last because it reads the headlines just written.
 *
 * Every request is optional. A timeout, an empty answer, a failed check or an
 * exception leaves that story in its author's own words and moves on; nothing
 * the model does can stop the paper from printing.
 */
object ObserverWritingPass {
    /**
     * How many stories one edition asks for. A small on-device model writes a
     * few words a second; this keeps a full pass to a couple of minutes.
     */
    const val MAX_STORIES = 14

    const val REQUEST_TIMEOUT_MS = 60_000L

    /** The work in page order, without running any of it. */
    internal fun plan(edition: ObserverEdition): List<Job> {
        val stories =
            (
                listOfNotNull(edition.lead) +
                    edition.topStories +
                    edition.sections.flatMap { s -> s.stories.filter { it.size == ObserverStorySize.LARGE } } +
                    edition.sections.filter { it.kind == ObserverSectionKind.LONG_READS }.flatMap { it.stories.take(2) }
            ).distinctBy { it.event.id }
                .take(MAX_STORIES)
                .map { Job.Story(it.event.id) }
        val roundups = edition.roundups.indices.map { Job.Roundup(it) }
        return stories.take(1) + roundups + stories.drop(1) + Job.Brief
    }

    sealed interface Job {
        data class Story(
            val id: String,
        ) : Job

        data class Roundup(
            val index: Int,
        ) : Job

        data object Brief : Job
    }

    /**
     * Writes into [edition], reporting each finished job through [onProgress]
     * with the edition as it stands, so the screen can fill in as it goes.
     */
    suspend fun write(
        edition: ObserverEdition,
        writer: ObserverWriter,
        onProgress: (done: Int, total: Int, edition: ObserverEdition) -> Unit = { _, _, _ -> },
    ): ObserverEdition {
        val jobs = plan(edition)
        var current = edition
        jobs.forEachIndexed { index, job ->
            current =
                when (job) {
                    is Job.Story -> writeStory(current, job.id, writer)
                    is Job.Roundup -> writeRoundup(current, job.index, writer)
                    Job.Brief -> writeBrief(current, writer)
                }
            onProgress(index + 1, jobs.size, current)
        }
        return current
    }

    private suspend fun writeStory(
        edition: ObserverEdition,
        id: String,
        writer: ObserverWriter,
    ): ObserverEdition {
        val story = edition.findStory(id) ?: return edition
        val request = ObserverCopy.story(story)
        val written = ObserverCopy.parseStory(ask(writer, request), request) ?: return edition
        return edition.mapStories { if (it.event.id == id) it.copy(written = written) else it }
    }

    private suspend fun writeRoundup(
        edition: ObserverEdition,
        index: Int,
        writer: ObserverWriter,
    ): ObserverEdition {
        val roundup = edition.roundups.getOrNull(index) ?: return edition
        val request = ObserverCopy.roundup(roundup) ?: return edition
        val summary = ObserverCopy.parseParagraph(ask(writer, request), request) ?: return edition
        return edition.copy(roundups = edition.roundups.mapIndexed { i, r -> if (i == index) r.copy(summary = summary) else r })
    }

    private suspend fun writeBrief(
        edition: ObserverEdition,
        writer: ObserverWriter,
    ): ObserverEdition {
        val request = ObserverCopy.brief(edition) ?: return edition
        val brief = ObserverCopy.parseParagraph(ask(writer, request), request, ObserverCopy.BRIEF_MAX) ?: return edition
        return edition.copy(brief = brief)
    }

    private suspend fun ask(
        writer: ObserverWriter,
        request: ObserverCopy.Request,
    ): String? =
        try {
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) { writer.write(request.instruction, request.input, request.maxOutputTokens) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("ObserverWritingPass", "The on-device model could not write a story", e)
            null
        }

    private fun ObserverEdition.findStory(id: String): ObserverStory? = (listOfNotNull(lead) + topStories + sections.flatMap { it.stories }).firstOrNull { it.event.id == id }

    /** Applies [f] to the story wherever it is printed: the lead, the top stories, or a section. */
    private fun ObserverEdition.mapStories(f: (ObserverStory) -> ObserverStory): ObserverEdition =
        copy(
            lead = lead?.let(f),
            topStories = topStories.map(f),
            sections = sections.map { s -> s.copy(stories = s.stories.map(f)) },
            roundups = roundups.map { r -> r.copy(anchor = r.anchor?.let(f)) },
        )
}
