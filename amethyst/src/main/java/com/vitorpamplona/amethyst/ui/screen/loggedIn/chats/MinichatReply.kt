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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats

import com.vitorpamplona.quartz.buzz.stream.StreamMessageV2Event
import com.vitorpamplona.quartz.buzz.threading.buzzThreadReply
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip22Comments.CommentEvent

/**
 * Whether [event] is a **minichat thread reply** — a reply that lives inside the thread opened from
 * its parent message, NOT as a flat sibling in the main timeline. Two dialects express the same idea:
 *
 * - **NIP-28/NIP-29 (public chats, Concord)**: a kind-1111 [CommentEvent].
 * - **Buzz workspaces**: a kind-40002 [StreamMessageV2Event] carrying a NIP-10 `reply`-marked `e` tag
 *   and NOT flagged `broadcast` (Buzz rejects kind-1111, so it threads chat with 40002 markers; a
 *   `broadcast=1` reply is an inline timeline sibling, matching block/buzz's `isThreadReply`).
 *
 * The timeline filter drops these (they belong in the minichat), the minichat count counts them, and
 * the minichat feed shows them — so all three agree on one definition.
 */
fun isMinichatReply(event: Event?): Boolean =
    when (event) {
        is CommentEvent -> true
        is StreamMessageV2Event -> !event.isBroadcast() && event.tags.buzzThreadReply() != null
        else -> false
    }
