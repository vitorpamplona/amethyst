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
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub

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
 * methods and generates the dispatcher glue (see
 * `amethyst/build/generated/ksp/playDebug/.../$AmethystAppFunctions_AppFunctionInvoker.kt`).
 * The generated invoker constructs this class via its default no-arg
 * constructor, so no `AppFunctionConfiguration.Provider` is required on
 * the Application. If we ever add an @AppFunction host class with
 * constructor parameters, we'll need to register a factory via Provider —
 * the docs nudge that direction, but the runtime does not require it for
 * default-constructed classes.
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

        // Snapshot the active account + relay set + client at function entry
        // and never touch sessionManager again from this dispatch. If the
        // user switches account mid-fetch, this snapshot keeps the request
        // routed to the relays we originally queried — caller still gets a
        // coherent result rather than events mixed across accounts.
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: return SearchProfilesResult.empty()
        val client = Amethyst.instance.client

        // SearchRelayListState's flow already resolves to a concrete relay
        // set: NIP-44-decrypted private entries + public entries, or the
        // curated default set when the user has no kind:10007. Same source
        // of truth the foreground UI uses.
        val relays = account.searchRelayList.flow.value
        if (relays.isEmpty()) return SearchProfilesResult.empty()

        // Quartz's INostrClient.fetchAll handles subscribe → drain on
        // EOSE/closed/cannot-connect → unsubscribe → dedup by id → sort
        // newest-first. Wraps everything in a withTimeoutOrNull(timeoutMs)
        // so a slow relay can't stall the dispatch.
        val events =
            client.fetchAll(
                filters = relays.associateWith { listOf(filter) },
                timeoutMs = GEMINI_FETCH_TIMEOUT_MS,
            )

        val hits =
            events
                .mapNotNull { it as? MetadataEvent }
                .distinctBy { it.pubKey }
                .sortedByDescending { it.createdAt }
                .take(cappedLimit)
                .map { it.toProfileHit() }

        return SearchProfilesResult(matches = hits)
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
         * 6-second fetch window. App Functions invocations are user-initiated
         * foreground requests in the Gemini UI — anything beyond a few seconds
         * is a poor user experience.
         */
        private const val GEMINI_FETCH_TIMEOUT_MS = 6_000L
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
