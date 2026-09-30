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
package com.vitorpamplona.amethyst.cli.stores

import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.cli.SecureFileIO
import com.vitorpamplona.amethyst.commons.model.concord.ConcordDirectInviteInbox
import java.io.File

/** amy's bookkeeping for Concord Direct Invites (CORD-05 §6): the wrap ids the user declined. */
data class StoredInviteInbox(
    val declined: List<String> = emptyList(),
)

/**
 * `~/.amy/<account>/concord-invites.json` — the declined Direct Invite wrap ids, so a declined
 * invite (whose wrap relays keep serving until its NIP-40 expiration) never resurfaces in
 * `amy concord invites`.
 */
class ConcordInviteInboxStore(
    private val file: File,
) {
    fun load(): StoredInviteInbox =
        if (file.exists()) {
            runCatching { Output.mapper.readValue<StoredInviteInbox>(file.readText()) }.getOrDefault(StoredInviteInbox())
        } else {
            StoredInviteInbox()
        }

    fun declined(): Set<String> = load().declined.toSet()

    fun decline(wrapId: String) {
        val current = load()
        if (wrapId in current.declined) return
        // Bounded like the app's store: the newest declines are kept, a long-expired one is not worth a line.
        val next = (current.declined + wrapId).takeLast(ConcordDirectInviteInbox.DECLINED_CAP)
        SecureFileIO.writeTextAtomic(file, Output.mapper.writeValueAsString(current.copy(declined = next)))
    }
}
