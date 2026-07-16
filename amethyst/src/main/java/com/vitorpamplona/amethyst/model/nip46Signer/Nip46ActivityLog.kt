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
package com.vitorpamplona.amethyst.model.nip46Signer

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** One serviced NIP-46 request, for the "recent activity" feed. */
data class Nip46ActivityEntry(
    val atSeconds: Long,
    val clientPubKey: HexKey,
    /** The NIP-46 method (`sign_event`, `nip44_encrypt`, `get_public_key`, …). */
    val method: String,
    /** Event kind for a `sign_event`, else `null`. */
    val kind: Int? = null,
    /** `null` when the request succeeded; the error string when it failed or was denied. */
    val error: String? = null,
) {
    val ok: Boolean get() = error == null
}

/**
 * A bounded, newest-first, in-memory log of the requests this account's signer has serviced, so the
 * user can see what apps are actually doing. Not persisted across app restarts (it is a live feed,
 * not an audit trail); it survives service restarts because it lives on the account's signer state.
 */
class Nip46ActivityLog(
    private val capacity: Int = 100,
) {
    private val _entries = MutableStateFlow<List<Nip46ActivityEntry>>(emptyList())
    val entries: StateFlow<List<Nip46ActivityEntry>> = _entries

    fun record(entry: Nip46ActivityEntry) {
        _entries.update { (listOf(entry) + it).take(capacity) }
    }

    /** The most recent entries for one client (newest first). */
    fun forClient(clientPubKey: HexKey): List<Nip46ActivityEntry> = _entries.value.filter { it.clientPubKey == clientPubKey }
}
