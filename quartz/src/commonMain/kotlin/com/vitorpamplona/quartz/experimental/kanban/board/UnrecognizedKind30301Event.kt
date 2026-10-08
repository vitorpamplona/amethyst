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
package com.vitorpamplona.quartz.experimental.kanban.board

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * A kind-30301 event that is neither a [KanbanBoardEvent] nor a WalletScrutiny build
 * verification. Nearly all of them are an encrypted planner app's tasks: `d` like
 * `fasting-reminder:2026-11-02` or a UUID, `b` = a board hash, `["col", "day"]`, `status` =
 * `open` / `done` / `deleted`, and NIP-44 ciphertext in `content`.
 *
 * Still addressable, because the kind is: stores key it by `d`, so a newer version replaces an
 * older one and an `a`-tag deletion reaches it, as NIP-01 requires of 30000–39999. Not
 * searchable (the content is encrypted), carries no edges (`b` is a hash, not an event), and no
 * card is drawn for it.
 */
@Immutable
class UnrecognizedKind30301Event(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KanbanBoardEvent.KIND, tags, content, sig)
