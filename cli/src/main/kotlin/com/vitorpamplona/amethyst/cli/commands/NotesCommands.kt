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

import com.vitorpamplona.amethyst.cli.DataDir

/**
 * `amy notes …` — NIP-10 kind:1 short text notes and the interactions on any
 * event (reply, quote, react, repost, delete, show, thread). Sits alongside
 * `profile` (kind:0) and `dm` (NIP-17) as a top-level verb group so the CLI
 * shape mirrors the Amethyst UI: one subcommand group per event family.
 *
 * EVENT is a 64-hex id, `note1…`, `nevent1…` or `naddr1…` (optionally
 * `nostr:`-prefixed). Every verb that takes one looks it up cache-first;
 * `--refresh` re-drains it from relays.
 */
object NotesCommands {
    val USAGE: String =
        """
        |Notes (NIP-10 kind:1) and interactions:
        |  notes post TEXT [--relay URL]               publish a kind:1 short text note
        |             [--pow BITS [--pow-timeout SECS]] mine a NIP-13 proof of work first
        |                                              (exit 124 on timeout, nothing published)
        |                                              (--relay accepts comma-separated extras)
        |  notes feed [--author USER]                  fetch kind:1 notes
        |             [--following | --hashtag TAG]    (default: own; --author: one user;
        |             [--limit N]                       --following: every contact-list pubkey;
        |             [--since TS] [--until TS]         --hashtag: anyone's notes tagged TAG)
        |             [--timeout SECS]
        |  notes show EVENT [--raw]                    render one event (any kind): author, body
        |                                              spans, reply/root, mentions, media, details
        |  notes thread EVENT [--limit N]              the whole conversation EVENT is in, depth-first
        |  notes reply EVENT TEXT                      reply: NIP-10 kind:1 to a kind:1, else (and
        |                                              to top-level Amethyst posts, like the app)
        |                                              a NIP-22 kind:1111 comment
        |  notes quote EVENT [TEXT]                    NIP-18 quote post (kind:1 + q tag)
        |  notes react EVENT [--content +|-|EMOJI]     NIP-25 reaction (default +, a like)
        |  notes repost EVENT                          NIP-18 repost (kind:6, or kind:16 for non-notes)
        |  notes delete EVENT…                         NIP-09 deletion of your own events (= amy delete)
        |
        |  Interaction verbs publish to your outbox + the relays the target was seen on +
        |  the target author's inbox; --relay URL,… adds more. Every EVENT verb takes
        |  --refresh (skip the cache) and --timeout SECS (default 8).
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "notes",
            tail,
            "notes <post|feed|show|thread|reply|quote|react|repost|delete> …",
            help = USAGE,
            routes =
                mapOf(
                    "post" to { rest -> PostCommand.run(dataDir, rest) },
                    "feed" to { rest -> FeedCommand.run(dataDir, rest) },
                    "show" to { rest -> NoteShowCommand.run(dataDir, rest) },
                    "thread" to { rest -> ThreadCommand.run(dataDir, rest) },
                    "reply" to { rest -> NoteActionCommands.reply(dataDir, rest) },
                    "quote" to { rest -> NoteActionCommands.quote(dataDir, rest) },
                    "react" to { rest -> NoteActionCommands.react(dataDir, rest) },
                    "repost" to { rest -> NoteActionCommands.repost(dataDir, rest) },
                    "delete" to { rest -> DeleteCommand.run(dataDir, rest) },
                ),
        )
}
