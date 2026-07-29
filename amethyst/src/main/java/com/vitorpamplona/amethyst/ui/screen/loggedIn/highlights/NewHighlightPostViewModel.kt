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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.highlights

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent

/**
 * Backs the "New Highlight" composer — a trimmed-down cousin of the short-note composer that
 * only carries the fields a NIP-84 kind:9802 highlight needs. It is pre-filled from a browser
 * share (parsed by [com.vitorpamplona.quartz.nip84Highlights.parse.SharedHighlightParser] in
 * the navigation layer) and lets the user confirm/edit before signing.
 *
 * The `textquoteselector` prefix/suffix anchors are carried through from the share but not
 * shown as editable fields — they are page-scraped positioning data, not something the user
 * would meaningfully edit.
 */
@Stable
class NewHighlightPostViewModel : ViewModel() {
    private var account: Account? = null

    /** The highlighted passage — becomes the event `content`. */
    var quote by mutableStateOf("")

    /** The source URL — becomes an `r` tag. */
    var url by mutableStateOf("")

    /** The user's own note about the passage — becomes a `comment` tag (a quote highlight). */
    var comment by mutableStateOf("")

    // Carried through from the share/source but not shown as editable fields — page-scraped
    // anchors and nostr-source references, not something the user would meaningfully edit.
    private var prefix: String? = null
    private var suffix: String? = null
    private var context: String? = null
    private var sourceAddress: String? = null
    private var sourceEventId: String? = null
    private var author: String? = null

    private var loaded = false

    fun init(accountViewModel: AccountViewModel) {
        account = accountViewModel.account
    }

    /**
     * Applies the incoming source once. Guarded so a recomposition (or a config change that
     * re-runs the loading effect) can't clobber edits the user already made.
     */
    fun load(
        quote: String?,
        url: String?,
        prefix: String?,
        suffix: String?,
        comment: String?,
        context: String?,
        sourceAddress: String?,
        sourceEventId: String?,
        author: String?,
    ) {
        if (loaded) return
        loaded = true

        this.quote = quote.orEmpty()
        this.url = url.orEmpty()
        this.comment = comment.orEmpty()
        this.prefix = prefix
        this.suffix = suffix
        this.context = context
        this.sourceAddress = sourceAddress
        this.sourceEventId = sourceEventId
        this.author = author
    }

    fun canPost(): Boolean = quote.isNotBlank()

    suspend fun sendHighlight() {
        val account = account ?: return
        if (!canPost()) return

        account.signAndComputeBroadcast(
            HighlightEvent.build(
                quote = quote.trim(),
                url = url.trim().ifBlank { null },
                prefix = prefix,
                suffix = suffix,
                comment = comment.trim().ifBlank { null },
                context = context,
                address = sourceAddress,
                event = sourceEventId,
                author = author,
            ),
        )
    }
}
