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
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.Podcasting20EpisodeEvent
import com.vitorpamplona.quartz.nipXXPodcasting20.metadata.Podcasting20PodcastMetadata
import com.vitorpamplona.quartz.nipXXPodcasting20.trailer.Podcasting20TrailerEvent
import com.vitorpamplona.quartz.podcasts.PodcastAudio
import com.vitorpamplona.quartz.podcasts.PodcastValue
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * `amy podcast20 <metadata|episode|trailer|list>` — the Podcasting-2.0 draft (derekross/podstr),
 * kept separate from the NIP-F4 `podcast` commands because the two models differ: here the
 * logged-in account IS the creator and signs everything with its own key, and episodes/trailers
 * are addressable (`d`-tag) events that can be edited in place.
 *
 *   metadata  publish kind:30078 show metadata (`d=podcast-metadata`, JSON body)
 *   episode   publish a kind:30054 episode
 *   trailer   publish a kind:30055 trailer
 *   list      list a creator's metadata + episodes + trailers
 *
 * Thin assembly only: events and JSON live in quartz (`Podcasting20EpisodeEvent`,
 * `Podcasting20TrailerEvent`, `Podcasting20PodcastMetadata`).
 */
object Podcast20Commands {
    val USAGE: String =
        """
        |Podcasts (Podcasting 2.0 / podstr):
        |  podcast20 metadata --title T                 publish kind:30078 show metadata (JSON body)
        |      [--description D] [--author A] [--email E] [--image URL] [--language L]
        |      [--categories A,B] [--funding URL,URL] [--website URL]
        |      [--copyright C] [--type episodic|serial] [--explicit] [--complete]
        |      [--locked] [--guid G] [--value-json JSON] [--relay URL[,URL…]]
        |  podcast20 episode --title T --audio URL[,URL]  publish a kind:30054 episode
        |      [--d ID] [--audio-type MIME] [--description D] [--image URL]
        |      [--duration SECS] [--video URL] [--video-type MIME]
        |      [--episode N] [--season N] [--transcript URL] [--chapters URL]
        |      [--value-json JSON] [--topic A,B] [--content MARKDOWN] [--pubdate RFC2822]
        |      [--relay URL[,URL…]]                      (--identifier is an alias for --d)
        |  podcast20 trailer --title T --url URL          publish a kind:30055 trailer
        |      [--d ID] [--type MIME] [--length BYTES] [--season N] [--pubdate RFC2822]
        |      [--relay URL[,URL…]]
        |  podcast20 list [USER] [--limit N]            list a creator's metadata + episodes + trailers
        |      [--relay URL[,URL…]]
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "podcast20",
            tail,
            "podcast20 <metadata|episode|trailer|list>",
            help = USAGE,
            routes =
                mapOf(
                    "metadata" to { rest -> metadata(dataDir, rest) },
                    "episode" to { rest -> episode(dataDir, rest) },
                    "trailer" to { rest -> trailer(dataDir, rest) },
                    "list" to { rest -> list(dataDir, rest) },
                ),
        )

    private suspend fun metadata(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val title = args.flag("title") ?: return Output.error("bad_args", "podcast20 metadata requires --title")
        val value =
            valueFlag(args).getOrElse {
                return Output.error("bad_args", "podcast20 metadata --value-json is not valid JSON")
            }

        val content =
            Podcasting20PodcastMetadata.Content(
                title = title,
                description = args.flag("description"),
                author = args.flag("author"),
                email = args.flag("email"),
                image = args.flag("image"),
                language = args.flag("language"),
                categories = listFlag(args, "categories"),
                explicit = trueIfPresent(args, "explicit"),
                website = args.flag("website"),
                copyright = args.flag("copyright"),
                funding = listFlag(args, "funding"),
                locked = trueIfPresent(args, "locked"),
                type = args.flag("type"),
                complete = trueIfPresent(args, "complete"),
                guid = args.flag("guid"),
                value = value,
            )
        args.rejectUnknown("relay")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val signed = ctx.signer.sign(Podcasting20PodcastMetadata.build(content))
            val ack = ctx.publish(signed, RawEventSupport.publishTargets(ctx, args))
            RawEventSupport.publishGuard(ack, signed.id)?.let { return it }
            Output.emit(
                mapOf(
                    "event_id" to signed.id,
                    "kind" to signed.kind,
                    "d" to Podcasting20PodcastMetadata.PODCAST_METADATA_D_TAG,
                    "title" to title,
                    "published_to" to ack.filterValues { it }.keys.map { it.url },
                ),
            )
            return 0
        }
    }

    private suspend fun episode(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val title = args.flag("title") ?: return Output.error("bad_args", "podcast20 episode requires --title")
        val audioType = args.flag("audio-type")
        val audios =
            args
                .flag("audio")
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.map { PodcastAudio(it, audioType) }
                .orEmpty()
        if (audios.isEmpty()) return Output.error("bad_args", "podcast20 episode requires --audio URL[,URL…]")
        val value =
            valueFlag(args).getOrElse {
                return Output.error("bad_args", "podcast20 episode --value-json is not valid JSON")
            }

        val dTag = args.flag("d") ?: args.flag("identifier") ?: generateDTag("episode")
        val video = args.flag("video")?.let { PodcastAudio(it, args.flag("video-type")) }

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val template =
                Podcasting20EpisodeEvent.build(
                    dTag = dTag,
                    title = title,
                    audios = audios,
                    pubdate = args.flag("pubdate") ?: rfc2822Now(),
                    description = args.flag("description"),
                    image = args.flag("image"),
                    durationInSeconds = args.flag("duration")?.toLongOrNull(),
                    video = video,
                    episodeNumber = args.flag("episode")?.toIntOrNull(),
                    season = args.flag("season")?.toIntOrNull(),
                    transcriptUrl = args.flag("transcript"),
                    chaptersUrl = args.flag("chapters"),
                    value = value,
                    topics = listFlag(args, "topic"),
                    markdownContent = args.flag("content", "") ?: "",
                )
            args.rejectUnknown("relay")
            val signed = ctx.signer.sign(template)
            val ack = ctx.publish(signed, RawEventSupport.publishTargets(ctx, args))
            RawEventSupport.publishGuard(ack, signed.id)?.let { return it }
            Output.emit(
                mapOf(
                    "event_id" to signed.id,
                    "kind" to signed.kind,
                    "d" to dTag,
                    "title" to title,
                    "audios" to audios.map { it.url },
                    "published_to" to ack.filterValues { it }.keys.map { it.url },
                ),
            )
            return 0
        }
    }

    private suspend fun trailer(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val title = args.flag("title") ?: return Output.error("bad_args", "podcast20 trailer requires --title")
        val url = args.flag("url") ?: return Output.error("bad_args", "podcast20 trailer requires --url")
        val dTag = args.flag("d") ?: args.flag("identifier") ?: generateDTag("trailer")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val template =
                Podcasting20TrailerEvent.build(
                    dTag = dTag,
                    title = title,
                    url = url,
                    pubdate = args.flag("pubdate") ?: rfc2822Now(),
                    lengthInBytes = args.flag("length")?.toLongOrNull(),
                    mimeType = args.flag("type"),
                    season = args.flag("season")?.toIntOrNull(),
                )
            args.rejectUnknown("relay")
            val signed = ctx.signer.sign(template)
            val ack = ctx.publish(signed, RawEventSupport.publishTargets(ctx, args))
            RawEventSupport.publishGuard(ack, signed.id)?.let { return it }
            Output.emit(
                mapOf(
                    "event_id" to signed.id,
                    "kind" to signed.kind,
                    "d" to dTag,
                    "title" to title,
                    "url" to url,
                    "published_to" to ack.filterValues { it }.keys.map { it.url },
                ),
            )
            return 0
        }
    }

    private suspend fun list(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val limit = args.intFlag("limit", 50)
        args.rejectUnknown("relay")
        // Read-only: runs anonymously when there is no account (pass a USER to
        // list someone else's episodes).
        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()
            val author = args.positionalOrNull(0)?.let { ctx.requireUserHex(it) } ?: ctx.identity.pubKeyHex
            val relays = RawEventSupport.queryTargets(ctx, args)
            val received =
                ctx.drain(
                    relays.associateWith {
                        listOf(
                            Filter(
                                kinds = listOf(Podcasting20EpisodeEvent.KIND, Podcasting20TrailerEvent.KIND, AppSpecificDataEvent.KIND),
                                authors = listOf(author),
                                limit = limit,
                            ),
                        )
                    },
                )
            val events = received.map { it.second }.distinctBy { it.id }
            val show =
                events
                    .filterIsInstance<AppSpecificDataEvent>()
                    .mapNotNull { Podcasting20PodcastMetadata.parse(it) }
                    .maxByOrNull { it.event.createdAt }
            val episodes =
                events
                    .filterIsInstance<Podcasting20EpisodeEvent>()
                    .sortedByDescending { it.createdAt }
                    .map {
                        mapOf(
                            "event_id" to it.id,
                            "d" to it.dTag(),
                            "title" to it.title(),
                            "season" to it.season(),
                            "episode" to it.number(),
                            "audios" to it.audios().map { a -> a.url },
                            "created_at" to it.createdAt,
                        )
                    }
            val trailers =
                events
                    .filterIsInstance<Podcasting20TrailerEvent>()
                    .sortedByDescending { it.createdAt }
                    .map {
                        mapOf(
                            "event_id" to it.id,
                            "d" to it.dTag(),
                            "title" to it.title(),
                            "url" to it.url(),
                            "season" to it.season(),
                            "created_at" to it.createdAt,
                        )
                    }
            Output.emit(
                mapOf(
                    "pubkey" to author,
                    "metadata" to
                        show?.let {
                            mapOf(
                                "title" to it.showTitle(),
                                "description" to it.showDescription(),
                                "image" to it.showImage(),
                                "author" to it.showAuthor(),
                                "categories" to it.showCategories(),
                                "funding" to it.showFundingUrls(),
                            )
                        },
                    "episode_count" to episodes.size,
                    "episodes" to episodes,
                    "trailer_count" to trailers.size,
                    "trailers" to trailers,
                ),
            )
            return 0
        }
    }

    private fun listFlag(
        args: Args,
        name: String,
    ): List<String> =
        args
            .flag(name)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    /** A boolean flag maps to `true` when present and `null` when absent, so it's omitted from the JSON. */
    private fun trueIfPresent(
        args: Args,
        name: String,
    ): Boolean? = if (args.bool(name)) true else null

    /**
     * Parses the `--value-json` value-for-value block. Success with null means the flag was absent;
     * a failure means it was present but malformed (the caller turns that into a bad_args error).
     */
    private fun valueFlag(args: Args): Result<PodcastValue?> {
        val json = args.flag("value-json") ?: return Result.success(null)
        return runCatching { JsonMapper.fromJson<PodcastValue>(json) }
    }

    private fun generateDTag(prefix: String): String = "$prefix-${System.currentTimeMillis() / 1000}-${UUID.randomUUID().toString().take(8)}"

    /** Current time as an RFC2822 date string (e.g. `Tue, 24 Jun 2025 12:00:00 GMT`), as the spec's `pubdate` expects. */
    private fun rfc2822Now(): String = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneId.of("GMT")))
}
