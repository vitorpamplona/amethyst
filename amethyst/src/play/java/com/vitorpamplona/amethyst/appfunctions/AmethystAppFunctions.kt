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
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.service.AppFunction
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.actions.SearchActions
import com.vitorpamplona.amethyst.commons.defaults.DefaultNIP65RelaySet
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKey
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

    /**
     * Drains recent kind:1 short text notes from the people the active
     * account follows. The "what's new on Nostr" verb — same query the
     * Amethyst home-feed UI runs, just truncated to one batch.
     *
     * Queried relays: the active account's home relays (NIP-65 outbox +
     * any private storage + local relays). Same source the UI uses, so
     * Gemini sees what the user would see on their timeline.
     *
     * @param limit max notes to return, capped to 200. Default 30.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getRecentFromFollows(
        appFunctionContext: AppFunctionContext,
        limit: Int = 30,
    ): SearchNotesResult {
        val cappedLimit = limit.coerceIn(1, 200)
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: return SearchNotesResult.empty()
        val client = Amethyst.instance.client

        val authors = account.kind3FollowList.flow.value.authors
        if (authors.isEmpty()) return SearchNotesResult.empty()

        val relays =
            account.homeRelays.flow.value
                .ifEmpty { DefaultNIP65RelaySet }
        if (relays.isEmpty()) return SearchNotesResult.empty()

        val filter =
            Filter(
                kinds = listOf(TextNoteEvent.KIND),
                authors = authors.toList(),
                limit = cappedLimit,
            )
        val events =
            client.fetchAll(
                filters = relays.associateWith { listOf(filter) },
                timeoutMs = GEMINI_FETCH_TIMEOUT_MS,
            )

        val hits =
            events
                .mapNotNull { it as? TextNoteEvent }
                .take(cappedLimit)
                .map { it.toNoteHit() }

        return SearchNotesResult(matches = hits)
    }

    /**
     * Recent kind:1 short text notes from a specific user. Use this when
     * the user asks "what did Vitor post recently?" or "catch me up on
     * Snowden" — pass that user's npub or 64-hex pubkey.
     *
     * Queried relays: prefer the target's NIP-65 write relays (where they
     * publish) when their kind:10002 is cached locally; fall back to the
     * active account's home relays.
     *
     * @param user npub (`npub1…`) or 64-character hex pubkey.
     * @param limit max notes to return, capped to 100. Default 20.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getNotesByUser(
        appFunctionContext: AppFunctionContext,
        user: String,
        limit: Int = 20,
    ): SearchNotesResult {
        val cappedLimit = limit.coerceIn(1, 100)
        val pubkey = decodeUserOrThrow(user)

        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: return SearchNotesResult.empty()
        val client = Amethyst.instance.client

        val targetWriteRelays =
            account.cache
                .checkGetOrCreateUser(pubkey)
                ?.outboxRelays()
                ?.toSet()
                .orEmpty()
        val relays =
            targetWriteRelays
                .ifEmpty { account.homeRelays.flow.value }
                .ifEmpty { DefaultNIP65RelaySet }
        if (relays.isEmpty()) return SearchNotesResult.empty()

        val filter =
            Filter(
                kinds = listOf(TextNoteEvent.KIND),
                authors = listOf(pubkey),
                limit = cappedLimit,
            )
        val events =
            client.fetchAll(
                filters = relays.associateWith { listOf(filter) },
                timeoutMs = GEMINI_FETCH_TIMEOUT_MS,
            )

        val hits =
            events
                .mapNotNull { it as? TextNoteEvent }
                .filter { it.pubKey == pubkey }
                .take(cappedLimit)
                .map { it.toNoteHit() }

        return SearchNotesResult(matches = hits)
    }

    /**
     * Looks up one Nostr profile by [user] (npub or 64-hex). Returns the
     * latest kind:0 metadata available — checks the local cache first,
     * falls back to a short network drain.
     *
     * @param user npub (`npub1…`) or 64-character hex pubkey.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getProfile(
        appFunctionContext: AppFunctionContext,
        user: String,
    ): GetProfileResult {
        val pubkey = decodeUserOrThrow(user)
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: return GetProfileResult.notFound(pubkey)

        // Cache-first: the foreground UI keeps observed metadata around.
        val cached =
            account.cache
                .checkGetOrCreateUser(pubkey)
                ?.metadataOrNull()
        if (cached != null) {
            return GetProfileResult(
                found = true,
                profile =
                    ProfileHit(
                        npub = NPub.create(pubkey),
                        pubkeyHex = pubkey,
                        displayName = cached.bestName(),
                        about = null,
                        nip05 = cached.nip05(),
                        picture = cached.profilePicture(),
                        lnAddress = cached.lnAddress(),
                    ),
            )
        }

        // Cache miss: drain bootstrap relays for the latest kind:0.
        val client = Amethyst.instance.client
        val relays =
            account.homeRelays.flow.value
                .ifEmpty { DefaultNIP65RelaySet }
        if (relays.isEmpty()) return GetProfileResult.notFound(pubkey)

        val filter =
            Filter(
                kinds = listOf(MetadataEvent.KIND),
                authors = listOf(pubkey),
                limit = 1,
            )
        val event =
            client
                .fetchAll(
                    filters = relays.associateWith { listOf(filter) },
                    timeoutMs = GEMINI_FETCH_TIMEOUT_MS,
                ).mapNotNull { it as? MetadataEvent }
                .filter { it.pubKey == pubkey }
                .maxByOrNull { it.createdAt }
                ?: return GetProfileResult.notFound(pubkey)

        return GetProfileResult(found = true, profile = event.toProfileHit())
    }

    /**
     * Find kind:1 short text notes tagged with a hashtag (NIP-12 `t`
     * tag). For "find me Nostr posts about Bitcoin" — pass `bitcoin`,
     * not `#bitcoin`. The hashtag is lowercased before matching, which
     * is the convention most Nostr clients (Amethyst included) follow.
     *
     * @param hashtag the tag value without the leading `#`.
     * @param limit max notes to return, capped to 100. Default 30.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun searchByHashtag(
        appFunctionContext: AppFunctionContext,
        hashtag: String,
        limit: Int = 30,
    ): SearchNotesResult {
        val tag = hashtag.trim().removePrefix("#").lowercase()
        if (tag.isEmpty()) throw AppFunctionInvalidArgumentException("hashtag must not be blank")
        val cappedLimit = limit.coerceIn(1, 100)

        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: return SearchNotesResult.empty()
        val client = Amethyst.instance.client
        val relays =
            account.homeRelays.flow.value
                .ifEmpty { DefaultNIP65RelaySet }
        if (relays.isEmpty()) return SearchNotesResult.empty()

        val filter =
            Filter(
                kinds = listOf(TextNoteEvent.KIND),
                tags = mapOf("t" to listOf(tag)),
                limit = cappedLimit,
            )
        val events =
            client.fetchAll(
                filters = relays.associateWith { listOf(filter) },
                timeoutMs = GEMINI_FETCH_TIMEOUT_MS,
            )

        val hits =
            events
                .mapNotNull { it as? TextNoteEvent }
                .take(cappedLimit)
                .map { it.toNoteHit() }

        return SearchNotesResult(matches = hits)
    }

    /**
     * Returns who the user is currently signed in as on Nostr — their
     * npub, best-effort display name, follow count, and how many relays
     * are configured for outbox / inbox. Diagnostic verb for queries
     * like "who am I logged in as?" or "what's my npub?".
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getActiveAccountInfo(appFunctionContext: AppFunctionContext): AccountInfoResult {
        val account =
            Amethyst.instance.sessionManager.loggedInAccount() ?: return AccountInfoResult.signedOut()

        val myPub = account.signer.pubKey
        val myUser = account.cache.checkGetOrCreateUser(myPub)
        val meta = myUser?.metadataOrNull()

        return AccountInfoResult(
            signedIn = true,
            npub = NPub.create(myPub),
            pubkeyHex = myPub,
            displayName = meta?.bestName(),
            nip05 = meta?.nip05(),
            followCount = account.kind3FollowList.userList.value.size,
            outboxRelayCount = account.homeRelays.flow.value.size,
            dmRelayCount = account.dmRelays.flow.value.size,
        )
    }

    /**
     * Accepts either an npub bech32 (`npub1…`) or 64-character hex
     * pubkey and returns the 64-char lowercase hex. Throws
     * [AppFunctionInvalidArgumentException] on anything else so the
     * caller sees a typed error rather than a generic crash.
     */
    private fun decodeUserOrThrow(input: String): HexKey =
        runCatching { decodePublicKey(input.trim()).toHexKey() }
            .getOrElse {
                throw AppFunctionInvalidArgumentException(
                    "Could not decode user '$input' — expected npub1… or 64-char hex pubkey.",
                )
            }

    private fun TextNoteEvent.toNoteHit(): NoteHit =
        NoteHit(
            eventId = id,
            npub = NPub.create(pubKey),
            pubkeyHex = pubKey,
            createdAt = createdAt,
            content = content,
        )

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

    /**
     * Searches Nostr notes for [query] via NIP-50 full-text search across the
     * active account's configured search relays (kind:10007), falling back to
     * Amethyst's curated default search-relay set when none is configured.
     *
     * Defaults to short text notes (kind:1) only. Currently no way to widen
     * to long-form or other kinds — add a parameter when the need is real;
     * App Functions doesn't support `List<Int>` parameters in alpha09.
     *
     * @param query free-form search text.
     * @param limit max number of notes to return — capped to 50.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun searchNotes(
        appFunctionContext: AppFunctionContext,
        query: String,
        limit: Int = 20,
    ): SearchNotesResult {
        val cappedLimit = limit.coerceIn(1, 50)
        val filter = SearchActions.searchNotesFilter(query, limit = cappedLimit) ?: return SearchNotesResult.empty()

        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: return SearchNotesResult.empty()
        val client = Amethyst.instance.client

        val relays = account.searchRelayList.flow.value
        if (relays.isEmpty()) return SearchNotesResult.empty()

        val events =
            client.fetchAll(
                filters = relays.associateWith { listOf(filter) },
                timeoutMs = GEMINI_FETCH_TIMEOUT_MS,
            )

        val hits =
            events
                .mapNotNull { it as? TextNoteEvent }
                // Sorted newest-first by fetchAll already, but the cast may
                // have dropped non-kind:1 events from a relay that ignored
                // our kinds filter.
                .take(cappedLimit)
                .map { it.toNoteHit() }

        return SearchNotesResult(matches = hits)
    }

    /**
     * Lists the active account's current follow set — the people the signed-in
     * user follows per their latest NIP-02 kind:3 contact list.
     *
     * Returned entries include best-effort display names sourced from each
     * user's cached kind:0; users with no cached metadata appear with
     * [FollowedUser.displayName] null. The order matches the on-disk follow
     * list (which is the order the user followed them in).
     *
     * @param limit cap on entries returned — capped to 500. Set to 0 for the
     *   full list when there's no specific bound.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getFollowing(
        appFunctionContext: AppFunctionContext,
        limit: Int = 100,
    ): FollowingResult {
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: return FollowingResult.empty()

        // userList resolves authors through LocalCache so display names /
        // pictures / nip05 are filled in for anyone whose kind:0 we've seen.
        val users = account.kind3FollowList.userList.value
        val effectiveLimit = if (limit <= 0) users.size else limit.coerceIn(1, 500)

        val out =
            users
                .take(effectiveLimit)
                .map { user ->
                    val meta = user.metadataOrNull()
                    FollowedUser(
                        npub = NPub.create(user.pubkeyHex),
                        pubkeyHex = user.pubkeyHex,
                        displayName = meta?.bestName(),
                        nip05 = meta?.nip05(),
                        picture = meta?.profilePicture(),
                    )
                }

        return FollowingResult(totalFollowing = users.size, returned = out)
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

/** Single match in [SearchNotesResult]. */
@AppFunctionSerializable(isDescribedByKDoc = true)
class NoteHit(
    /** Hex event id of the note. */
    val eventId: String,
    /** Bech32 npub of the note's author. */
    val npub: String,
    /** Hex pubkey of the note's author. */
    val pubkeyHex: String,
    /** Unix-seconds timestamp the note was created at. */
    val createdAt: Long,
    /** Raw content of the note (plain text, may contain Nostr URIs / hashtags). */
    val content: String,
)

@AppFunctionSerializable(isDescribedByKDoc = true)
class SearchNotesResult(
    /** Matched notes, sorted newest-first by created_at. */
    val matches: List<NoteHit>,
) {
    companion object {
        fun empty() = SearchNotesResult(matches = emptyList())
    }
}

/** Single entry in [FollowingResult]. Metadata fields may be null when the
 *  user's kind:0 hasn't been cached locally. */
@AppFunctionSerializable(isDescribedByKDoc = true)
class FollowedUser(
    /** Bech32 npub of the followed user. */
    val npub: String,
    /** Hex pubkey of the followed user. */
    val pubkeyHex: String,
    /** Best-effort display name (display_name then name). */
    val displayName: String?,
    /** NIP-05 verified handle, e.g. `alice@example.com`. */
    val nip05: String?,
    /** Avatar image URL. */
    val picture: String?,
)

@AppFunctionSerializable(isDescribedByKDoc = true)
class FollowingResult(
    /** Total number of follows in the active account's kind:3 — may exceed
     *  [returned] when the caller passed a limit. */
    val totalFollowing: Int,
    /** Subset of follows returned to the caller, in original on-disk order. */
    val returned: List<FollowedUser>,
) {
    companion object {
        fun empty() = FollowingResult(totalFollowing = 0, returned = emptyList())
    }
}

/**
 * Result of [AmethystAppFunctions.getProfile]. Distinguishes "user has no
 * cached + observable kind:0 metadata" (`found = false`) from "user has a
 * stub profile with empty fields" — the latter shouldn't normally happen
 * but the explicit flag keeps callers from rendering a hollow card.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
class GetProfileResult(
    /** True when a kind:0 was found (cache or relay). False means the
     *  user exists as a pubkey but no profile event was reachable. */
    val found: Boolean,
    /** Resolved profile when [found] is true, otherwise a stub with
     *  pubkey-only fields populated. */
    val profile: ProfileHit?,
) {
    companion object {
        fun notFound(pubkeyHex: String) =
            GetProfileResult(
                found = false,
                profile =
                    ProfileHit(
                        npub = NPub.create(pubkeyHex),
                        pubkeyHex = pubkeyHex,
                        displayName = null,
                        about = null,
                        nip05 = null,
                        picture = null,
                        lnAddress = null,
                    ),
            )
    }
}

/**
 * Summary of the active Nostr account on this device. Returned by
 * [AmethystAppFunctions.getActiveAccountInfo].
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
class AccountInfoResult(
    /** False when no account is currently logged into Amethyst. */
    val signedIn: Boolean,
    /** Bech32 npub of the active account, or null when signed out. */
    val npub: String?,
    /** Hex pubkey of the active account, or null when signed out. */
    val pubkeyHex: String?,
    /** Best-effort display name from cached kind:0. */
    val displayName: String?,
    /** NIP-05 verified handle. */
    val nip05: String?,
    /** Number of pubkeys in the user's current kind:3 follow list. */
    val followCount: Int,
    /** Number of NIP-65 outbox / home relays configured. */
    val outboxRelayCount: Int,
    /** Number of NIP-17 DM-inbox relays (kind:10050) configured. */
    val dmRelayCount: Int,
) {
    companion object {
        fun signedOut() =
            AccountInfoResult(
                signedIn = false,
                npub = null,
                pubkeyHex = null,
                displayName = null,
                nip05 = null,
                followCount = 0,
                outboxRelayCount = 0,
                dmRelayCount = 0,
            )
    }
}
