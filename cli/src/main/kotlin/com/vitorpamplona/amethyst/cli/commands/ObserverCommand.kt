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
package com.vitorpamplona.amethyst.cli.commands

import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.commons.observer.ObserverEdition
import com.vitorpamplona.amethyst.commons.observer.ObserverPress
import com.vitorpamplona.amethyst.commons.observer.ObserverPull
import com.vitorpamplona.amethyst.commons.observer.ObserverRoundup
import com.vitorpamplona.amethyst.commons.observer.ObserverStory
import com.vitorpamplona.amethyst.commons.observer.ObserverStorySize
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first

/**
 * `amy observer [USER] [--relay URL]`
 *
 * Prints the Nostr Observer front page for USER (default: the logged-in
 * account) from the last 24 hours, ranked through their own web-of-trust lens
 * on the search relay and laid out on this machine — the same press the app's
 * Observer screen runs. Read-only: it signs nothing, so it works anonymously.
 *
 * Thin assembly only: readiness, pull, engagement and layout live in
 * `commons/observer`; this file picks the reader and shapes the output.
 */
object ObserverCommand {
    val USAGE: String =
        """
        |Nostr Observer (a front page from your web of trust's last 24 hours):
        |  observer [USER] [--relay URL]   USER = npub/nprofile/hex/name@domain (default: you).
        |                                  Ranked by the `observer:` lens on the search relay
        |                                  (default wss://search.brainstorm.world); exits 1 with
        |                                  the unmet readiness link when no lens can rank yet.
        """.trimMargin()

    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.firstOrNull() == "--help" || rest.firstOrNull() == "-h") {
            System.err.println(USAGE)
            return 0
        }
        val args = Args(rest)
        val relay = args.flag("relay")?.let { RelayUrlNormalizer.normalizeOrNull(it) ?: return Output.error("bad_args", "not a relay url: $it") }
        val userArg = args.positionalOrNull(0)
        args.rejectUnknown()

        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()
            val reader =
                when {
                    userArg != null -> ctx.requireUserHex(userArg)
                    !ctx.anonymous -> ctx.identity.pubKeyHex
                    else -> return Output.error("bad_args", "no account: pass a USER (npub, hex or name@domain)")
                }

            val scope = CoroutineScope(SupervisorJob())
            try {
                val pull = if (relay != null) ObserverPull(ctx.client, relay) else ObserverPull(ctx.client)
                val press = ObserverPress(reader, pull, scope)
                press.print()
                val done = press.state.first { it !is ObserverPress.State.Printing && it !is ObserverPress.State.Idle }

                return when (done) {
                    is ObserverPress.State.NoLens -> {
                        Output.error("no_lens", done.reason.name.lowercase())
                    }

                    is ObserverPress.State.Failed -> {
                        Output.error("failed", done.message)
                    }

                    else -> {
                        val edition = press.edition.value ?: return Output.error("failed", "no edition")
                        Output.emit(toJson(edition)) { render(edition) }
                        0
                    }
                }
            } finally {
                scope.cancel()
            }
        }
    }

    private fun toJson(edition: ObserverEdition): Map<String, Any?> =
        mapOf(
            "reader" to edition.reader,
            "code" to edition.code,
            "since" to edition.since,
            "until" to edition.until,
            "printed" to edition.stats.printed,
            "authors" to edition.stats.authors,
            "day_notes" to edition.stats.dayNotes,
            "signals" to edition.stats.signals,
            "lead" to edition.lead?.let(::story),
            "top_stories" to edition.topStories.map(::story),
            "sections" to edition.sections.map { mapOf("kind" to it.kind.name.lowercase(), "stories" to it.stories.map(::story)) },
            "trending" to edition.trending.map { mapOf("hashtag" to it.hashtag, "authors" to it.authors) },
            "roundups" to (edition.roundups + edition.sections.mapNotNull { it.roundup }).map(::roundupJson),
        )

    private fun roundupJson(r: ObserverRoundup): Map<String, Any?> =
        mapOf(
            "kind" to r.kind.name.lowercase(),
            "topic" to r.topic,
            "anchor" to r.anchor?.event?.id,
            "people" to r.people,
            "lines" to r.lines.map { mapOf("id" to it.event.id, "byline" to it.byline, "text" to it.text, "detail" to it.detail?.toString()) },
        )

    private fun story(s: ObserverStory): Map<String, Any?> =
        mapOf(
            "id" to s.event.id,
            "kind" to s.event.kind,
            "size" to s.size.name.lowercase(),
            "byline" to s.byline,
            "headline" to s.headline,
            "body" to s.body,
            "image" to s.imageUrl,
            "reactions" to s.reactions,
            "reposts" to s.reposts,
            "replies" to s.replies,
            "zaps" to s.zaps,
            "details" to s.details.map { it.toString() },
        )

    private fun render(edition: ObserverEdition): String =
        buildString {
            appendLine("THE NOSTR OBSERVER · ${edition.code}")
            appendLine("Ranked as ${edition.readerName ?: edition.reader} · ${edition.stats.printed} stories from ${edition.stats.authors} people" + (edition.stats.dayNotes?.let { " · $it notes above the trust floor" } ?: "") + " · ${edition.stats.signals} signals")
            edition.trending.takeIf { it.isNotEmpty() }?.let { appendLine("Trending: " + it.joinToString(" ") { t -> "#${t.hashtag}(${t.authors})" }) }
            appendLine()
            edition.lead?.let {
                appendLine("== LEAD ==")
                line(this, it)
            }
            if (edition.topStories.isNotEmpty()) appendLine("== TOP STORIES ==")
            edition.topStories.forEach { line(this, it) }
            edition.roundups.forEach { roundup(this, it) }
            edition.sections.forEach { section ->
                appendLine("== ${section.kind.name} (${section.stories.size}) ==")
                section.roundup?.let { roundup(this, it) }
                section.stories.forEach { line(this, it) }
            }
        }

    /** A summary post: its kind and subject, then one quoted line per voice. */
    private fun roundup(
        sb: StringBuilder,
        r: ObserverRoundup,
    ) {
        sb
            .append("[ROUNDUP ")
            .append(r.kind.name)
            .append("] ")
            .append(r.topic?.let { "#$it" } ?: r.anchor?.headline ?: "")
            .append(" (")
            .append(r.people)
            .appendLine(" people)")
        r.lines.forEach { line ->
            sb
                .append("    > ")
                .append(line.byline)
                .append(": ")
                .append(line.text)
            line.detail?.let { sb.append("  {").append(it).append("}") }
            sb.appendLine()
        }
    }

    private fun line(
        sb: StringBuilder,
        s: ObserverStory,
    ) {
        // The post's size, as the app lays it out: [L]arge, [M]edium, [S]mall.
        sb
            .append("[")
            .append(s.size.name.first())
            .append("] ")
            .append(s.headline)
            .append(" — ")
            .append(s.byline)
        if (s.score > 0) {
            sb
                .append(" [♥")
                .append(s.reactions)
                .append(" ↻")
                .append(s.reposts)
                .append(" ↩")
                .append(s.replies)
                .append(" ⚡")
                .append(s.zaps)
                .append("]")
        }
        sb.appendLine()
        if (s.size != ObserverStorySize.SMALL && s.body.isNotBlank()) sb.append("    ").appendLine(s.body)
        if (s.size != ObserverStorySize.SMALL) s.imageUrl?.let { sb.append("    image: ").appendLine(it) }
        s.details.forEach { sb.append("    ").appendLine(it.toString()) }
    }
}
