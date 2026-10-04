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

import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArrayOrNull
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.IPubKeyEntity
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.utils.sha256.sha256

/**
 * Lays out a front page from a [ObserverCorpus], on the device, with no model.
 *
 * The web Observer hands the corpus to a language model that writes the page.
 * This is the native counterpart: the same lens picks the material, and the
 * editorial calls are made by rules that can be read and tested —
 *
 *  - **Order is what the reader's web of trust did.** The relay selects by trust
 *    score but delivers by `created_at`, and the score is not recoverable from
 *    the response (measured by the Observer). So ranking on the page comes from
 *    the reactions, reposts and replies the same lens surfaced for each event —
 *    [ObserverEngagement.score] — and falls back to the relay's order.
 *  - **Headlines are the author's own words**: a title tag when the kind has
 *    one, else the first sentence of the post. Nothing is paraphrased, so
 *    nothing can be misattributed or invented.
 *  - **Sections are earned, not fixed.** A desk with nothing on it today is not
 *    printed.
 *  - **The Observer's prunes**: a per-author cap per desk and a duplicate
 *    collapse, so one account filing its archive cannot own a section.
 *
 * Pure: no clock, no network. [ObserverEditorTest] holds it to that.
 */
object ObserverEditor {
    /** How many stories run under the lead, above the fold. */
    const val TOP_STORIES = 4

    /** A post shorter than this is a greeting, not a story — it can run on the wire but not above the fold. */
    const val MIN_STORY_CHARS = 40

    const val HEADLINE_CHARS = 120
    const val LEAD_BODY_CHARS = 900
    const val STORY_BODY_CHARS = 420
    const val BRIEF_BODY_CHARS = 240

    /** Measured on a live edition: without a cap, 13 of 17 large posts landed in the wire. */
    const val MAX_LARGE_PER_SECTION = 3

    fun edit(corpus: ObserverCorpus): ObserverEdition {
        val names = corpus.names
        val pruned = ObserverDesk.entries.associateWith { desk -> prune(desk, corpus.ranked[desk].orEmpty()) }

        fun storyOf(
            event: Event,
            desk: ObserverDesk,
            bodyChars: Int,
        ): ObserverStory = story(event, desk, engagementOf(corpus, event), names, bodyChars)

        // Above the fold: notes that are posts (not replies) with something to say,
        // and long-form. Ranked by what the reader's web of trust did with them.
        val candidates =
            (pruned.getValue(ObserverDesk.NOTES).filter { !isReply(it) } + pruned.getValue(ObserverDesk.ARTICLES))
                .map { it to engagementOf(corpus, it).score }
                .filter { (event, _) -> event.kind != 1 || cleanText(event.content, names).length >= MIN_STORY_CHARS }
                .sortedWith(compareByDescending<Pair<Event, Int>> { it.second }.thenByDescending { it.first.createdAt })
                .map { it.first }

        val front = mutableListOf<Event>()
        val frontAuthors = mutableSetOf<HexKey>()
        for (event in candidates) {
            if (front.size > TOP_STORIES) break
            // One story per author above the fold: a front page is a survey of the day.
            if (!frontAuthors.add(event.pubKey)) continue
            front.add(event)
        }
        val used = front.mapTo(mutableSetOf()) { it.id }

        val notable = notableScore(pruned.values.flatten().map { engagementOf(corpus, it).score })

        val lead = front.firstOrNull()?.let { storyOf(it, deskOf(it), LEAD_BODY_CHARS).copy(size = ObserverStorySize.LARGE) }
        val top =
            front.drop(1).map { event ->
                val story = storyOf(event, deskOf(event), STORY_BODY_CHARS)
                story.copy(size = if (story.imageUrl != null) ObserverStorySize.LARGE else ObserverStorySize.MEDIUM)
            }

        fun section(
            kind: ObserverSectionKind,
            vararg desks: ObserverDesk,
            bodyChars: Int = BRIEF_BODY_CHARS,
            sort: (List<Event>) -> List<Event> = { ranked(corpus, it) },
            keep: (Event) -> Boolean = { true },
        ): ObserverSection? {
            val events = sort(desks.flatMap { pruned.getValue(it) }.filter { it.id !in used && keep(it) })
            if (events.isEmpty()) return null
            var larges = 0
            val stories =
                events.mapIndexed { index, event ->
                    val story = storyOf(event, deskOf(event), bodyChars)
                    var size = sizeInSection(kind, index, story, notable)
                    // A section is a run of posts, not a wall of big ones.
                    if (size == ObserverStorySize.LARGE && ++larges > MAX_LARGE_PER_SECTION) size = ObserverStorySize.MEDIUM
                    story.copy(size = size)
                }
            return ObserverSection(kind, stories)
        }

        val sections =
            listOfNotNull(
                section(ObserverSectionKind.PHOTOS, ObserverDesk.PICTURES) { imageOf(it) != null },
                section(ObserverSectionKind.LONG_READS, ObserverDesk.ARTICLES, bodyChars = STORY_BODY_CHARS),
                section(ObserverSectionKind.LIVE, ObserverDesk.LIVE),
                section(ObserverSectionKind.VIDEO, ObserverDesk.VIDEOS, ObserverDesk.SHORTS),
                section(ObserverSectionKind.HIGHLIGHTS, ObserverDesk.HIGHLIGHTS, bodyChars = STORY_BODY_CHARS),
                section(ObserverSectionKind.POLLS, ObserverDesk.POLLS),
                // A listing is news for when it HAPPENS, so the calendar runs in date order.
                section(ObserverSectionKind.CALENDAR, ObserverDesk.CALENDAR, sort = { byStart(it) }),
                // A page that advertises a sold item sends readers after something that is gone.
                section(ObserverSectionKind.CLASSIFIEDS, ObserverDesk.CLASSIFIEDS) { !it.tagValue("status").equals("sold", ignoreCase = true) },
                section(ObserverSectionKind.APPS, ObserverDesk.APPS),
                section(ObserverSectionKind.CODE, ObserverDesk.GIT),
                section(ObserverSectionKind.WIKI, ObserverDesk.WIKI),
                // Everything else the lens surfaced, so nothing it chose goes unprinted.
                section(ObserverSectionKind.WIRE, ObserverDesk.NOTES) { !isReply(it) },
            )

        val printed = listOfNotNull(lead) + top + sections.flatMap { it.stories }

        return ObserverEdition(
            reader = corpus.reader,
            readerName = names[corpus.reader],
            since = corpus.since,
            until = corpus.until,
            code = code(corpus),
            stats =
                ObserverStats(
                    printed = printed.size,
                    authors = printed.mapTo(mutableSetOf()) { it.author }.size,
                    dayNotes = corpus.dayNotes,
                    signals = corpus.engagement.values.sumOf { it.reactions + it.reposts + it.replies + it.zaps },
                ),
            lead = lead,
            topStories = top,
            sections = sections,
            trending = trending(pruned.values.flatten()),
        )
    }

    /**
     * The score a story needs to count as notable today: the top quarter of
     * everything that got any signal at all. Relative, because a quiet day and a
     * busy one should both have a few big posts; never below 1, so a story
     * nobody touched is never notable.
     */
    internal fun notableScore(scores: List<Int>): Int {
        val positive = scores.filter { it > 0 }.sorted()
        if (positive.isEmpty()) return Int.MAX_VALUE
        return positive[(positive.size * 3) / 4].coerceAtLeast(1)
    }

    /**
     * A section opens with its best story at MEDIUM so every section has a
     * face; anything notable is at least MEDIUM, and LARGE when it has a
     * picture to carry it. Photographs are pictures first, so they are never
     * reduced to a line. Everything else is a SMALL post.
     */
    internal fun sizeInSection(
        kind: ObserverSectionKind,
        index: Int,
        story: ObserverStory,
        notable: Int,
    ): ObserverStorySize =
        when {
            kind == ObserverSectionKind.PHOTOS -> ObserverStorySize.MEDIUM
            story.score >= notable && story.imageUrl != null -> ObserverStorySize.LARGE
            story.score >= notable || index == 0 -> ObserverStorySize.MEDIUM
            else -> ObserverStorySize.SMALL
        }

    private fun deskOf(event: Event): ObserverDesk = ObserverDesk.entries.firstOrNull { event.kind in it.kinds } ?: ObserverDesk.NOTES

    private fun ranked(
        corpus: ObserverCorpus,
        events: List<Event>,
    ): List<Event> =
        events
            .withIndex()
            .sortedWith(compareByDescending<IndexedValue<Event>> { engagementOf(corpus, it.value).score }.thenBy { it.index })
            .map { it.value }

    private fun byStart(events: List<Event>): List<Event> =
        events.sortedWith(
            compareBy<Event> { startKey(it) == null }.thenBy { startKey(it) }.thenByDescending { it.createdAt },
        )

    /** Sortable start: unix seconds for 31923, the ISO date for 31922 (which sorts as text). */
    private fun startKey(event: Event): String? {
        val raw = event.tagValue("start") ?: return null
        raw.toLongOrNull()?.let { return epochDay(it) }
        return raw.takeIf { DATE.matches(it) }
    }

    /** `YYYY-MM-DD` for a unix time, without a clock or a timezone library: civil-from-days. */
    internal fun epochDay(seconds: Long): String {
        val days = seconds.floorDiv(86_400L)
        val z = days + 719_468
        val era = z.floorDiv(146_097L)
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        val y = yoe + era * 400 + if (m <= 2) 1 else 0
        return y.toString().padStart(4, '0') + "-" + m.toString().padStart(2, '0') + "-" + d.toString().padStart(2, '0')
    }

    internal fun engagementOf(
        corpus: ObserverCorpus,
        event: Event,
    ): ObserverEngagement {
        val byId = corpus.engagement[event.id]
        val byAddress = (event as? AddressableEvent)?.let { corpus.engagement[it.addressTag()] }
        if (byId == null) return byAddress ?: ObserverEngagement.NONE
        if (byAddress == null) return byId
        return byId + byAddress
    }

    /**
     * NIP-10: a kind 1 with any `e` tag that is not marked `mention` is a reply.
     * Replies are half a conversation; out of their thread they are not stories.
     */
    internal fun isReply(event: Event): Boolean =
        event.kind == 1 &&
            event.tags.any { it.size > 1 && it[0] == "e" && it.getOrNull(3) != "mention" }

    /**
     * The Observer's two prunes. A per-author cap, because one bot filing its
     * archive can own a whole desk; and a duplicate collapse, because the same
     * post arrives repeatedly when a client retries against several hosts.
     */
    internal fun prune(
        desk: ObserverDesk,
        events: List<Event>,
    ): List<Event> {
        val counts = mutableMapOf<HexKey, Int>()
        val seen = mutableSetOf<String>()
        val ids = mutableSetOf<HexKey>()
        val out = mutableListOf<Event>()
        for (event in events) {
            if (!ids.add(event.id)) continue
            val n = counts[event.pubKey] ?: 0
            if (n >= desk.perAuthor) continue
            val key = fingerprint(event)
            if (key.isNotEmpty() && !seen.add(key)) continue
            counts[event.pubKey] = n + 1
            out.add(event)
        }
        return out
    }

    /** Same author, same words — whitespace, case and links normalised away. */
    private fun fingerprint(event: Event): String {
        val text =
            event.content
                .replace(URL, "")
                .replace(WHITESPACE, " ")
                .trim()
                .lowercase()
        return if (text.length < 12) "" else event.pubKey + "|" + text.take(200)
    }

    internal fun story(
        event: Event,
        desk: ObserverDesk,
        engagement: ObserverEngagement,
        names: Map<HexKey, String>,
        bodyChars: Int,
    ): ObserverStory {
        val text = cleanText(event.content, names)
        val title = (event.tagValue("title") ?: event.tagValue("name"))?.let { cleanText(it, names) }?.takeIf { it.isNotBlank() }
        val summary =
            (event.tagValue("summary") ?: event.tagValue("description"))?.let { cleanText(it, names) }?.takeIf { it.isNotBlank() }

        val headline: String
        val body: String
        when {
            title != null -> {
                headline = clip(title, HEADLINE_CHARS)
                body = clip(summary ?: text, bodyChars)
            }

            desk == ObserverDesk.HIGHLIGHTS -> {
                // The excerpt IS the story; it runs as a pull quote.
                headline = clip(text, bodyChars)
                body = ""
            }

            else -> {
                val (first, rest) = firstSentence(text)
                headline = clip(first, HEADLINE_CHARS)
                body = clip(rest, bodyChars)
            }
        }

        return ObserverStory(
            event = event,
            desk = desk,
            byline = names[event.pubKey] ?: shortNpub(event.pubKey),
            headline = headline,
            body = body,
            imageUrl = imageOf(event),
            reactions = engagement.reactions,
            reposts = engagement.reposts,
            replies = engagement.replies,
            zaps = engagement.zaps,
            details = details(event, desk, names),
        )
    }

    private fun details(
        event: Event,
        desk: ObserverDesk,
        names: Map<HexKey, String>,
    ): List<ObserverDetail> =
        buildList {
            when (desk) {
                ObserverDesk.HIGHLIGHTS -> {
                    // Named when the highlight names them; the highlighter is NOT the author.
                    event.tagValue("p")?.takeIf { it.length == 64 }?.let {
                        add(ObserverDetail.QuotedAuthor(names[it] ?: shortNpub(it)))
                    }
                    event.tagValue("r")?.takeIf { it.isNotBlank() }?.let { add(ObserverDetail.Source(it.take(200))) }
                }

                ObserverDesk.LIVE -> {
                    event.tagValue("starts")?.toLongOrNull()?.let { add(ObserverDetail.Starts(it, null, null)) }
                    event
                        .tagValue("current_participants")
                        ?.toIntOrNull()
                        ?.takeIf { it > 0 }
                        ?.let { add(ObserverDetail.Watching(it)) }
                }

                ObserverDesk.POLLS -> {
                    // Two shapes in the wild, `["option", "0", "A"]` and `["option", "Bu2a9f", "Yes"]`: the label is always the third field.
                    val options =
                        event.tagValues("option").mapNotNull {
                            it
                                .getOrNull(2)
                                ?.trim()
                                ?.takeIf(String::isNotBlank)
                                ?.take(80)
                        }
                    if (options.isNotEmpty()) add(ObserverDetail.PollOptions(options.take(8)))
                }

                ObserverDesk.CALENDAR -> {
                    val raw = event.tagValue("start")
                    val zone = event.tagValue("start_tzid")
                    when {
                        raw?.toLongOrNull() != null -> add(ObserverDetail.Starts(raw.toLong(), null, zone))
                        raw != null && DATE.matches(raw) -> add(ObserverDetail.Starts(null, raw, null))
                    }
                }

                ObserverDesk.CLASSIFIEDS -> {
                    event.tagValues("price").firstOrNull()?.let { price ->
                        val amount = price.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() } ?: return@let
                        add(
                            ObserverDetail.Price(
                                amount.take(20),
                                price
                                    .getOrNull(2)
                                    ?.trim()
                                    ?.takeIf { it.isNotBlank() }
                                    ?.take(12),
                                price
                                    .getOrNull(3)
                                    ?.trim()
                                    ?.takeIf { it.isNotBlank() }
                                    ?.take(12),
                            ),
                        )
                    }
                    event.tagValue("status")?.takeIf { it.isNotBlank() }?.let { add(ObserverDetail.ItemStatus(it.take(20))) }
                }

                else -> {}
            }
            event.tagValue("location")?.takeIf { it.isNotBlank() }?.let { add(ObserverDetail.Location(it.take(120))) }
            event
                .tagValue("duration")
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
                ?.let { add(ObserverDetail.Duration(it)) }
        }

    /**
     * The first image the event itself declares, hotlinked, never fetched or
     * re-hosted — the Observer's rule. Video is filtered on declared MIME, not
     * on the URL suffix, because `imeta` carries video as often as stills.
     */
    internal fun imageOf(event: Event): String? {
        for (meta in event.imetas()) {
            val mime = meta.properties["m"]?.firstOrNull()
            if (mime != null && mime.startsWith("image/") && isHttps(meta.url)) return meta.url
            // A video's poster frame.
            meta.properties["image"]?.firstOrNull { isHttps(it) }?.let { return it }
            if (mime == null && IMAGE_URL.matches(meta.url) && isHttps(meta.url)) return meta.url
        }
        for (name in IMAGE_TAGS) {
            event
                .tagValue(name)
                ?.trim()
                ?.takeIf { isHttps(it) }
                ?.let { return it }
        }
        return URL.findAll(event.content).map { it.value.trimEnd('.', ',', ')') }.firstOrNull { IMAGE_URL.matches(it) && isHttps(it) }
    }

    private fun isHttps(url: String) = url.startsWith("https://", ignoreCase = true)

    /**
     * Prose a reader can read: `nostr:npub…` mentions become `@name`, event
     * references and links are taken out (the story itself is the link), and
     * whitespace is collapsed.
     */
    internal fun cleanText(
        content: String,
        names: Map<HexKey, String>,
    ): String {
        val mentionsResolved =
            Nip19Parser.nip19regex.replace(content) { match ->
                val entity = Nip19Parser.uriToRoute(match.value)?.entity
                if (entity is IPubKeyEntity) {
                    "@" + (names[entity.hex] ?: shortNpub(entity.hex)) + (match.groups[7]?.value ?: "")
                } else {
                    match.groups[7]?.value ?: ""
                }
            }
        return mentionsResolved
            .replace(URL, "")
            .replace(WHITESPACE, " ")
            .trim()
    }

    /** The first sentence (or line) as the headline, the rest as the body. */
    internal fun firstSentence(text: String): Pair<String, String> {
        if (text.isEmpty()) return "" to ""
        val end = SENTENCE_END.find(text)?.range?.last
        if (end != null && end + 1 in 20..HEADLINE_CHARS) {
            return text.substring(0, end + 1).trim() to text.substring(end + 1).trim()
        }
        if (text.length <= HEADLINE_CHARS) return text to ""
        // No usable sentence break: cut at a word boundary and run the rest as the body.
        val cut = text.lastIndexOf(' ', HEADLINE_CHARS).takeIf { it > 40 } ?: HEADLINE_CHARS
        return text.substring(0, cut).trim() + "…" to "…" + text.substring(cut).trim()
    }

    internal fun clip(
        text: String,
        limit: Int,
    ): String {
        if (text.length <= limit) return text
        val cut = text.lastIndexOf(' ', limit).takeIf { it > limit / 2 } ?: limit
        return text.substring(0, cut).trimEnd(',', ';', ':', ' ') + "…"
    }

    /** Tags at least two different people used today, most-used first. */
    internal fun trending(events: List<Event>): List<ObserverTrend> {
        val authorsByTag = mutableMapOf<String, MutableSet<HexKey>>()
        events.forEach { event -> event.hashtagList().forEach { authorsByTag.getOrPut(it) { mutableSetOf() }.add(event.pubKey) } }
        return authorsByTag
            .filterValues { it.size >= 2 }
            .map { ObserverTrend(it.key, it.value.size) }
            .sortedWith(compareByDescending<ObserverTrend> { it.authors }.thenBy { it.hashtag })
            .take(10)
    }

    /**
     * Who it was read for, the window, and the id of every event that came
     * back. Sorted, because desks are pulled in parallel and their finishing
     * order is a race. It cannot be a hash of the page — see the Observer's
     * `Corpus.code()` for why that is a fixed point.
     */
    internal fun code(corpus: ObserverCorpus): String {
        val sb = StringBuilder()
        sb.append(corpus.reader).append(corpus.since).append(corpus.until)
        corpus
            .all()
            .map { it.id }
            .sorted()
            .forEach { sb.append(it) }
        return sha256(sb.toString().encodeToByteArray()).toHexKey().take(6).uppercase()
    }

    /** No hex in front of a person: a name if we have one, a short npub if we do not. */
    internal fun shortNpub(hex: HexKey): String {
        val full = hex.hexToByteArrayOrNull()?.toNpub() ?: return "someone"
        return if (full.length <= 20) full else full.take(10) + "…" + full.takeLast(5)
    }

    private val IMAGE_TAGS = listOf("image", "thumb", "picture", "icon")
    private val URL = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
    private val IMAGE_URL = Regex("""https?://\S+\.(jpe?g|png|gif|webp|avif)(\?\S*)?""", RegexOption.IGNORE_CASE)
    private val WHITESPACE = Regex("""\s+""")
    private val SENTENCE_END = Regex("""[.!?](?=\s|$)""")
    private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
}
