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
package com.vitorpamplona.amethyst.appfunctions

import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.service.AppFunction
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.actions.SearchActions
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Bridge that exposes Amethyst's "verbs" (commons/.../actions/) to the Android
 * App Functions runtime, which Gemini and other system agents can drive.
 *
 * **Status — pre-stable.** Built against androidx.appfunctions 1.0.0-alpha09.
 * The API is still moving; treat every release as ABI-breaking until 1.0.0
 * ships. Scoped to the `play` build flavor only — the F-Droid channel
 * ships without any Google AI dependencies.
 *
 * Plain class, no inheritance — the KSP compiler discovers `@AppFunction`
 * methods and generates the dispatcher glue. Construction is wired in
 * [PlayAmethyst.appFunctionConfiguration].
 *
 * Only read-only verbs are exposed so far. Write verbs (post, follow, zap)
 * are intentionally deferred until we resolve the signer-prompt flow for
 * NIP-46 / NIP-55 signers, which cannot interact with the user from a
 * background AppFunctionService invocation.
 *
 * Account scoping uses the currently active account from
 * [com.vitorpamplona.amethyst.Amethyst.instance.sessionManager] — the same
 * Account the foreground UI is bound to. When no account is signed in,
 * every function returns an empty result rather than failing the call.
 */
class AmethystAppFunctions {
    /**
     * Searches for Nostr user profiles matching [query] via NIP-50 full-text
     * search across the active account's configured search relays
     * (kind:10007), falling back to Amethyst's curated default search-relay
     * set when none is configured.
     *
     * @param query free-form search text (display name, NIP-05 handle, etc.)
     * @param limit max number of profiles to return — capped to 50.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun searchProfiles(
        appFunctionContext: AppFunctionContext,
        query: String,
        limit: Int = 10,
    ): SearchProfilesResult {
        val cappedLimit = limit.coerceIn(1, 50)
        val filter = SearchActions.searchProfilesFilter(query, cappedLimit) ?: return SearchProfilesResult.empty()

        val app = Amethyst.instance
        val account = app.sessionManager.loggedInAccount() ?: return SearchProfilesResult.empty()

        // SearchRelayListState's flow already resolves to a concrete relay
        // set: NIP-44-decrypted private entries + public entries, or the
        // curated default set when the user has no kind:10007. Same source
        // of truth the foreground UI uses.
        val relays = account.searchRelayList.flow.value
        if (relays.isEmpty()) return SearchProfilesResult.empty()

        val events = drain(app, relays, filter, GEMINI_DRAIN_TIMEOUT_MS)

        val hits =
            events
                .mapNotNull { it as? MetadataEvent }
                .distinctBy { it.pubKey }
                .sortedByDescending { it.createdAt }
                .take(cappedLimit)
                .map { it.toProfileHit() }

        return SearchProfilesResult(matches = hits)
    }

    /**
     * One-shot relay drain: subscribe with [filter] against [relays], collect
     * events until every relay sends EOSE or [timeoutMs] elapses, then
     * unsubscribe. Mirrors `Context.drain` in amy — kept inline here because
     * Account exposes a live `INostrClient` rather than a drain helper.
     */
    private suspend fun drain(
        app: com.vitorpamplona.amethyst.AppModules,
        relays: Set<NormalizedRelayUrl>,
        filter: Filter,
        timeoutMs: Long,
    ): List<Event> {
        val client = app.client
        val incoming = Channel<Event>(UNLIMITED)
        val done = mutableSetOf<NormalizedRelayUrl>()
        val subId = newSubId()
        val listener =
            object : SubscriptionListener {
                override fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    incoming.trySend(event)
                }

                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    done += relay
                }

                override fun onClosed(
                    message: String,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    done += relay
                }

                override fun onCannotConnect(
                    relay: NormalizedRelayUrl,
                    message: String,
                    forFilters: List<Filter>?,
                ) {
                    done += relay
                }
            }

        val collected = mutableListOf<Event>()
        try {
            client.subscribe(subId, relays.associateWith { listOf(filter) }, listener)
            withTimeoutOrNull(timeoutMs) {
                while (done.size < relays.size) {
                    collected += incoming.receive()
                }
                while (true) {
                    val r = incoming.tryReceive()
                    if (!r.isSuccess) break
                    collected += r.getOrThrow()
                }
            }
        } finally {
            client.unsubscribe(subId)
            incoming.close()
        }
        return collected
    }

    private fun MetadataEvent.toProfileHit(): ProfileHit {
        val meta = contactMetaData()
        return ProfileHit(
            npub = NPub.create(pubKey),
            pubkeyHex = pubKey,
            displayName = meta?.bestName(),
            about = meta?.about,
            nip05 = meta?.nip05,
            picture = meta?.picture,
            lnAddress = meta?.lnAddress(),
        )
    }

    companion object {
        /**
         * 6-second drain window. App Functions invocations are user-initiated
         * foreground requests in the Gemini UI — anything beyond a few seconds
         * is a poor user experience.
         */
        private const val GEMINI_DRAIN_TIMEOUT_MS = 6_000L
    }
}

/**
 * Single match in [SearchProfilesResult]. Nullable fields let callers
 * render whatever subset of metadata the profile happens to publish.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
class ProfileHit(
    /** Bech32 npub identifier (`npub1…`) for the matched profile. */
    val npub: String,
    /** Hex-encoded pubkey (same identity as [npub], non-bech32 form). */
    val pubkeyHex: String,
    /** Best-effort display name (display_name then name). */
    val displayName: String?,
    /** Profile bio / about. */
    val about: String?,
    /** NIP-05 verified handle, e.g. `alice@example.com`. */
    val nip05: String?,
    /** Avatar image URL. */
    val picture: String?,
    /** Lightning address (lud16 preferred, otherwise lud06 LNURL). */
    val lnAddress: String?,
)

@AppFunctionSerializable(isDescribedByKDoc = true)
class SearchProfilesResult(
    /** Matched profiles, deduplicated by pubkey and sorted newest-first. */
    val matches: List<ProfileHit>,
) {
    companion object {
        fun empty() = SearchProfilesResult(matches = emptyList())
    }
}
