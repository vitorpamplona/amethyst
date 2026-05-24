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
package com.vitorpamplona.amethyst.commons.actions

import com.vitorpamplona.amethyst.commons.defaults.DefaultSearchRelayList
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent

/**
 * Pure NIP-50 search-filter assembly + search-relay resolution.
 *
 * Like [FollowActions], these are non-UI verbs callable from any context
 * (amy CLI, future Android App Functions adapter for Gemini, automation).
 * The actions only build [Filter]s and pick relays — caller drives the
 * actual subscription / drain via its own relay client.
 *
 * For client-side post-filtering (dedup, pseudo-kinds like `reply` / `media`)
 * see [com.vitorpamplona.amethyst.commons.search.SearchResultFilter].
 */
object SearchActions {
    /** Default kinds for "search notes" — kind:1 short text notes. */
    val DEFAULT_NOTE_KINDS: List<Int> = listOf(TextNoteEvent.KIND)

    /**
     * Build a NIP-50 filter for searching kind:0 profile metadata.
     *
     * Returns null for a blank [query] — callers should treat as "no
     * results" rather than issuing an unconstrained search that most
     * relays would reject anyway.
     */
    fun searchProfilesFilter(
        query: String,
        limit: Int = 20,
    ): Filter? {
        val q = query.trim()
        if (q.isEmpty()) return null
        return Filter(
            kinds = listOf(MetadataEvent.KIND),
            search = q,
            limit = limit,
        )
    }

    /**
     * Build a NIP-50 filter for searching event content. Defaults to
     * kind:1 short text notes; pass [kinds] to widen (e.g. include
     * kind:30023 long-form or kind:9802 highlights).
     */
    fun searchNotesFilter(
        query: String,
        kinds: List<Int> = DEFAULT_NOTE_KINDS,
        limit: Int = 50,
        since: Long? = null,
        until: Long? = null,
    ): Filter? {
        val q = query.trim()
        if (q.isEmpty()) return null
        return Filter(
            kinds = kinds,
            search = q,
            limit = limit,
            since = since,
            until = until,
        )
    }

    /**
     * Pick the relay set to query for NIP-50 search.
     *
     * Strategy: when [currentList] (the user's kind:10007 search-relay
     * list) is present, use its public + decrypted-private relays.
     * Otherwise fall back to [fallback] (defaults to Amethyst's curated
     * [DefaultSearchRelayList] — the same set the Android UI uses when
     * the user hasn't configured their own).
     *
     * [signer] is only consulted when [currentList] is non-null and has
     * private (NIP-44 encrypted) relay entries; an internal/local signer
     * is fine, a NIP-46/NIP-55 signer will cost a round-trip.
     */
    suspend fun resolveSearchRelays(
        signer: NostrSigner,
        currentList: SearchRelayListEvent?,
        fallback: Collection<NormalizedRelayUrl> = DefaultSearchRelayList,
    ): Set<NormalizedRelayUrl> {
        if (currentList == null) return fallback.toSet()
        val combined = currentList.relays(signer)
        return if (combined.isEmpty()) fallback.toSet() else combined.toSet()
    }
}
