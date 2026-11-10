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
package com.vitorpamplona.amethyst.commons.frontpage

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/*
 * Print today's front page: check the reader's ranking, pull the desks through it, have
 * the reader's own AI write the page, then resolve and check it.
 *
 * Ported from NosFabrica/the-nostr-observer @ 0901b92 (MIT, see ./LICENSE.observer): the
 * `nostr-observer` skill's Steps 2–6 and `generator/.../nostr/Pull.kt`, matched to
 * Brainstorm's port (client/src/services/frontPage.ts). Nothing after the page: no
 * publishing, no Blossom, no archive.
 *
 * The corpus is NOT consumed into LocalCache, on purpose: an edition is a MEASUREMENT of
 * what one ranked read returned in one window, its edition code fingerprints exactly that
 * set, and a day of strangers' events would otherwise show up in the app's own feeds.
 */

enum class ProbeVerdict { READY, PROJECTION_PENDING }

sealed interface PrintStep {
    data object Probe : PrintStep

    class Pull(
        val desksDone: Int,
        val desksTotal: Int,
        val events: Int,
    ) : PrintStep

    class Write(
        val round: Int,
        val chars: Int,
    ) : PrintStep

    class Check(
        val round: Int,
    ) : PrintStep
}

sealed interface PrintResult {
    class NotReady(
        val verdict: ProbeVerdict,
    ) : PrintResult

    class Printed(
        val html: String,
        val corpus: Corpus,
        val changes: List<ResolveChange>,
        val quotes: Int,
        val usage: Usage?,
        val rounds: Int,
        val headline: String? = null,
        val digestChars: Int = 0,
    ) : PrintResult

    class Refused(
        val violations: List<Violation>,
        val changes: List<ResolveChange>,
        val corpus: Corpus,
        val usage: Usage?,
    ) : PrintResult
}

/**
 * One run, start to finish. [write] is one Chat Completions call with the reader's own
 * key; [boundary] resolves and checks. The validator is not negotiable: a page still
 * refused after one repair round is never returned as a page.
 */
class FrontPagePress(
    private val client: INostrClient,
    private val relay: NormalizedRelayUrl,
    private val boundary: PaperBoundary,
    private val write: suspend (messages: List<ChatMessage>, onText: (Int) -> Unit) -> Written,
    private val floor: Int = DEFAULT_TRUST_FLOOR,
    private val deskIdleMs: Long = 25_000,
    private val probeIdleMs: Long = 15_000,
    private val now: () -> Long = { TimeUtils.now() },
) {
    private suspend fun ask(
        filter: Filter,
        idleMs: Long = deskIdleMs,
    ): List<Event> = client.fetchAll(relay, filter, idleMs)

    /**
     * The Observer's link 4: the same question through the reader's ranking and without it.
     * Amethyst's own gate has already seen a rank provider in the reader's kind 10040; this
     * is what it cannot see — cards stored but not yet PROJECTED, where the observed read
     * returns nothing while the anonymous one returns notes.
     */
    suspend fun probe(
        observer: HexKey,
        since: Long,
    ): ProbeVerdict =
        coroutineScope {
            val observed = async { ask(rankedProbe(observer, since), probeIdleMs) }
            val anonymous = async { ask(rankedProbe(null, since), probeIdleMs) }
            if (anonymous.await().isNotEmpty() && observed.await().isEmpty()) ProbeVerdict.PROJECTION_PENDING else ProbeVerdict.READY
        }

    suspend fun pull(
        observer: HexKey,
        until: Long,
        onStep: (PrintStep) -> Unit = {},
    ): Corpus =
        coroutineScope {
            val since = until - FRONT_PAGE_WINDOW_SECONDS
            val total = DESKS.size + 1
            var done = 0
            var events = 0
            // The desks finish on whatever thread their relay answered on.
            val tally = Mutex()

            suspend fun report(n: Int) =
                tally.withLock {
                    done += 1
                    events += n
                    onStep(PrintStep.Pull(done, total, events))
                }
            // The relay has spells of not answering: five desks at a time, not fourteen.
            val gate = Semaphore(5)
            val control =
                async {
                    // The same window, no ranking, NO FLOOR.
                    gate.withPermit { ask(deskFilter(listOf(1), since, until, 400, null, floor)) }.also { report(0) }
                }
            val desks =
                DESKS
                    .map { desk ->
                        async {
                            val kept = gate.withPermit { desk.belongs(observer, ask(deskFilter(desk.kinds, since, until, desk.limit, observer, floor))) }
                            report(kept.size)
                            desk.key to kept
                        }
                    }.awaitAll()
                    .toMap()

            // Bylines, chunked: one REQ with every author of a busy day is the largest frame
            // this builds, and the relay DROPS an oversized frame in silence.
            val authors =
                desks.values
                    .flatten()
                    .map { it.pubKey }
                    .distinct()
            val nameGate = Semaphore(3)
            val profiles =
                authors
                    .chunked(PROFILE_CHUNK)
                    .map { some -> async { nameGate.withPermit { ask(Filter(kinds = listOf(0), authors = some, search = INCLUDE_SPAM)) } } }
                    .awaitAll()
                    .flatten()

            assemble(observer, relay.url, floor, since, until, desks, control.await(), profilesFrom(profiles))
        }

    suspend fun print(
        observer: HexKey,
        readerName: String,
        language: String? = null,
        onStep: (PrintStep) -> Unit = {},
    ): PrintResult {
        val until = now()
        onStep(PrintStep.Probe)
        val verdict = probe(observer, until - FRONT_PAGE_WINDOW_SECONDS)
        if (verdict != ProbeVerdict.READY) return PrintResult.NotReady(verdict)

        val corpus = pull(observer, until, onStep)
        val digest = digest(corpus)
        val messages =
            mutableListOf(
                ChatMessage("system", systemPrompt()),
                ChatMessage("user", userMessage(digest, readerName, language)),
            )

        var usage: Usage? = null
        for (round in 1..2) {
            onStep(PrintStep.Write(round, 0))
            val written = write(messages) { chars -> onStep(PrintStep.Write(round, chars)) }
            usage = usage?.plus(written.usage) ?: written.usage
            val page = extractDocument(written.text)
            if (page.isBlank()) throw WriterException(WriterFailure.EMPTY)

            onStep(PrintStep.Check(round))
            val checked = boundary.resolveAndCheck(page, corpus)
            if (checked.violations.isEmpty()) {
                return PrintResult.Printed(checked.html, corpus, checked.changes, checked.quotes, usage, round, checked.headline, digest.length)
            }
            if (round == 2) return PrintResult.Refused(checked.violations, checked.changes, corpus, usage)
            messages.add(ChatMessage("assistant", page))
            messages.add(ChatMessage("user", repairMessage(checked.violations)))
        }
        error("unreachable: round 2 always returns")
    }

    companion object {
        /** Authors per kind 0 REQ: under the relay's 262,144-byte frame with room to spare. */
        const val PROFILE_CHUNK = 2_700
    }
}
