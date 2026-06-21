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
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent
import com.vitorpamplona.quartz.nipF4Podcasts.episode.tags.AudioTag
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent

/**
 * `amy podcast <metadata|publish|list>` — NIP-F4 podcasts (nak's `podcast`).
 *
 *   metadata  publish a kind:10154 show metadata (replaceable)
 *   publish   publish a kind:54 episode
 *   list      list a user's podcast metadata + episodes
 *
 * Thin assembly only: events live in quartz (`PodcastMetadataEvent`,
 * `PodcastEpisodeEvent`).
 */
object PodcastCommands {
    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "podcast",
            tail,
            "podcast <metadata|publish|list>",
            mapOf(
                "metadata" to { rest -> metadata(dataDir, rest) },
                "publish" to { rest -> publish(dataDir, rest) },
                "list" to { rest -> list(dataDir, rest) },
            ),
        )

    private suspend fun metadata(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val title = args.flag("title") ?: return Output.error("bad_args", "podcast metadata requires --title")
        val image = args.flag("image") ?: return Output.error("bad_args", "podcast metadata requires --image")
        val description = args.flag("description") ?: return Output.error("bad_args", "podcast metadata requires --description")
        val websites =
            args
                .flag("website")
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val signed = ctx.signer.sign(PodcastMetadataEvent.build(title, image, description, websites))
            val ack = ctx.publish(signed, RawEventSupport.publishTargets(ctx, args))
            Output.emit(
                mapOf(
                    "event_id" to signed.id,
                    "kind" to signed.kind,
                    "title" to title,
                    "published_to" to ack.filterValues { it }.keys.map { it.url },
                ),
            )
            return 0
        }
    }

    private suspend fun publish(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val title = args.flag("title") ?: return Output.error("bad_args", "podcast publish requires --title")
        val description = args.flag("description") ?: return Output.error("bad_args", "podcast publish requires --description")
        val audioType = args.flag("audio-type")
        val audios =
            args
                .flag("audio")
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.map { AudioTag(it, audioType) }
                .orEmpty()
        if (audios.isEmpty()) return Output.error("bad_args", "podcast publish requires --audio URL[,URL…]")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val template =
                PodcastEpisodeEvent.build(
                    title = title,
                    description = description,
                    audios = audios,
                    markdownContent = args.flag("content", "") ?: "",
                    image = args.flag("image"),
                )
            val signed = ctx.signer.sign(template)
            val ack = ctx.publish(signed, RawEventSupport.publishTargets(ctx, args))
            Output.emit(
                mapOf(
                    "event_id" to signed.id,
                    "kind" to signed.kind,
                    "title" to title,
                    "audios" to audios.map { it.url },
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
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val author = args.positionalOrNull(0)?.let { ctx.requireUserHex(it) } ?: ctx.identity.pubKeyHex
            val relays = RawEventSupport.queryTargets(ctx, args)
            val received =
                ctx.drain(
                    relays.associateWith {
                        listOf(Filter(kinds = listOf(PodcastMetadataEvent.KIND, PodcastEpisodeEvent.KIND), authors = listOf(author), limit = limit))
                    },
                )
            val events = received.map { it.second }.distinctBy { it.id }
            val show = events.filterIsInstance<PodcastMetadataEvent>().maxByOrNull { it.createdAt }
            val episodes =
                events
                    .filterIsInstance<PodcastEpisodeEvent>()
                    .sortedByDescending { it.createdAt }
                    .map {
                        mapOf(
                            "event_id" to it.id,
                            "title" to it.title(),
                            "description" to it.description(),
                            "audios" to it.audios().map { a -> a.url },
                            "created_at" to it.createdAt,
                        )
                    }
            Output.emit(
                mapOf(
                    "pubkey" to author,
                    "metadata" to
                        show?.let {
                            mapOf("title" to it.title(), "description" to it.description(), "image" to it.image(), "websites" to it.websites())
                        },
                    "episode_count" to episodes.size,
                    "episodes" to episodes,
                ),
            )
            return 0
        }
    }
}
