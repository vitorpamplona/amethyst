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
import com.vitorpamplona.amethyst.commons.relayClient.nip17Dm.unwrapAndUnsealOrNull
import com.vitorpamplona.quartz.lightning.LnInvoiceUtil
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.base.BaseDMGroupEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKey
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.TimeUtils

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
            val info = cached.flow.value?.info
            return GetProfileResult(
                found = true,
                profile =
                    ProfileHit(
                        npub = NPub.create(pubkey),
                        pubkeyHex = pubkey,
                        displayName = cached.bestName(),
                        about = info?.about,
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
     * Most recent kind:1 notes the active account itself published. For
     * "what did I post recently?" — drains the user's own outbox relays
     * filtered to their own pubkey.
     *
     * @param limit max notes to return, capped to 100. Default 20.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getMyRecentNotes(
        appFunctionContext: AppFunctionContext,
        limit: Int = 20,
    ): SearchNotesResult {
        val cappedLimit = limit.coerceIn(1, 100)
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: return SearchNotesResult.empty()
        val client = Amethyst.instance.client
        val relays =
            account.homeRelays.flow.value
                .ifEmpty { DefaultNIP65RelaySet }
        if (relays.isEmpty()) return SearchNotesResult.empty()

        val myPub = account.signer.pubKey
        val filter =
            Filter(
                kinds = listOf(TextNoteEvent.KIND),
                authors = listOf(myPub),
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
                .filter { it.pubKey == myPub }
                .take(cappedLimit)
                .map { it.toNoteHit() }

        return SearchNotesResult(matches = hits)
    }

    /**
     * Notes where someone tagged the active account with a `p` tag —
     * the Nostr equivalent of being @-mentioned. Use this for "did
     * anyone mention me recently?".
     *
     * @param limit max notes to return, capped to 100. Default 20.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getMyMentions(
        appFunctionContext: AppFunctionContext,
        limit: Int = 20,
    ): SearchNotesResult {
        val cappedLimit = limit.coerceIn(1, 100)
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: return SearchNotesResult.empty()
        val client = Amethyst.instance.client
        val relays =
            account.homeRelays.flow.value
                .ifEmpty { DefaultNIP65RelaySet }
        if (relays.isEmpty()) return SearchNotesResult.empty()

        val myPub = account.signer.pubKey
        val filter =
            Filter(
                kinds = listOf(TextNoteEvent.KIND),
                tags = mapOf("p" to listOf(myPub)),
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
     * Replies to a specific note (kind:1 events with an `e` tag
     * pointing at [eventId]). Used for "did anyone respond to my last
     * post?" — pass `getMyRecentNotes(1).matches.first().eventId`
     * from a previous call, or any other note you want to track.
     *
     * @param eventId 64-character hex id of the note being replied to.
     * @param limit max replies to return, capped to 100. Default 20.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getRepliesToNote(
        appFunctionContext: AppFunctionContext,
        eventId: String,
        limit: Int = 20,
    ): SearchNotesResult {
        if (eventId.length != 64) {
            throw AppFunctionInvalidArgumentException("eventId must be 64-character hex (nevent bech32 not yet supported)")
        }
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
                tags = mapOf("e" to listOf(eventId)),
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
                .filter { it.id != eventId } // self-reference safety
                .take(cappedLimit)
                .map { it.toNoteHit() }

        return SearchNotesResult(matches = hits)
    }

    /**
     * Total sats received as NIP-57 zaps in the last [hoursBack] hours,
     * plus a count of distinct zappers. For "did I earn any sats
     * today?" — defaults to 24 hours.
     *
     * Each kind:9735 receipt carries a `bolt11` invoice; we parse the
     * amount out and sum them. Receipts without a parseable amount are
     * counted but contribute 0 sats.
     *
     * @param hoursBack window size in hours. Capped to 168 (7 days),
     *   default 24.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getZapsReceived(
        appFunctionContext: AppFunctionContext,
        hoursBack: Int = 24,
    ): ZapsReceivedResult {
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: return ZapsReceivedResult.empty()
        val client = Amethyst.instance.client
        val relays =
            account.homeRelays.flow.value
                .ifEmpty { DefaultNIP65RelaySet }
        if (relays.isEmpty()) return ZapsReceivedResult.empty()

        val cappedHours = hoursBack.coerceIn(1, 24 * 7)
        val sinceSecs = TimeUtils.now() - cappedHours.toLong() * 3600L
        val myPub = account.signer.pubKey

        val filter =
            Filter(
                kinds = listOf(LnZapEvent.KIND),
                tags = mapOf("p" to listOf(myPub)),
                since = sinceSecs,
                limit = 500,
            )
        val events =
            client.fetchAll(
                filters = relays.associateWith { listOf(filter) },
                timeoutMs = GEMINI_FETCH_TIMEOUT_MS,
            )

        val receipts = events.mapNotNull { it as? LnZapEvent }
        val zapperIds = mutableSetOf<HexKey>()
        var totalSats = 0L
        var unparseable = 0
        for (z in receipts) {
            val bolt11 =
                z.tags
                    .firstOrNull { it.size > 1 && it[0] == "bolt11" }
                    ?.get(1)
            val sats =
                bolt11
                    ?.let { runCatching { LnInvoiceUtil.getAmountInSats(it).toLong() }.getOrNull() }
                    ?: run {
                        unparseable++
                        0L
                    }
            totalSats += sats
            // Zap sender is recorded in the description's signed kind:9734;
            // we only have it as a pubkey-id mention via `P` tag on some
            // receipts. Best-effort:
            z.tags
                .firstOrNull { it.size > 1 && (it[0] == "P" || it[0] == "p" && it[1] != myPub) }
                ?.get(1)
                ?.let { zapperIds.add(it) }
        }

        return ZapsReceivedResult(
            windowHours = cappedHours,
            totalSats = totalSats,
            zapCount = receipts.size,
            uniqueZapperCount = zapperIds.size,
            unparseableInvoiceCount = unparseable,
        )
    }

    /**
     * Recent direct messages addressed to the active account. Drains
     * NIP-17 gift wraps from inbox relays, decrypts each, and returns
     * the inner kind:14 messages.
     *
     * @param peer optional npub/hex; when set, only returns messages
     *   from that specific peer. When null, returns from anyone.
     * @param hoursBack window size in hours. Capped to 168 (7 days),
     *   default 24.
     * @param limit max messages to return, capped to 100. Default 20.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getRecentDms(
        appFunctionContext: AppFunctionContext,
        peer: String?,
        hoursBack: Int = 24,
        limit: Int = 20,
    ): DmsResult {
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: return DmsResult.empty()
        val client = Amethyst.instance.client
        val cappedHours = hoursBack.coerceIn(1, 24 * 7)
        val cappedLimit = limit.coerceIn(1, 100)
        val peerPub = peer?.takeIf { it.isNotBlank() }?.let { decodeUserOrThrow(it) }

        // DM-inbox relays per kind:10050; fall back to home relays if the
        // user never published a kind:10050 (interop with stale clients).
        val relays =
            account.dmRelays.flow.value
                .ifEmpty { account.homeRelays.flow.value }
        if (relays.isEmpty()) return DmsResult.empty()

        val myPub = account.signer.pubKey
        val sinceSecs = TimeUtils.now() - cappedHours.toLong() * 3600L

        // NIP-59 gift wraps randomise their `created_at` up to two days
        // in the past, so we widen the filter by 2 days. Same trick the
        // foreground client and amy use.
        val filter =
            Filter(
                kinds = listOf(GiftWrapEvent.KIND),
                tags = mapOf("p" to listOf(myPub)),
                since = sinceSecs - TimeUtils.twoDays(),
                limit = 200,
            )
        val wraps =
            client
                .fetchAll(
                    filters = relays.associateWith { listOf(filter) },
                    timeoutMs = GEMINI_FETCH_TIMEOUT_MS,
                ).mapNotNull { it as? GiftWrapEvent }

        val seen = HashSet<HexKey>()
        val messages = mutableListOf<DmMessage>()
        for (wrap in wraps) {
            val inner = wrap.unwrapAndUnsealOrNull(account.signer) ?: continue
            if (inner !is BaseDMGroupEvent) continue
            if (inner !is ChatMessageEvent) continue // skip file headers for v1; keep payload small
            if (!seen.add(inner.id)) continue
            // After widening for randomised `created_at`, drop anything
            // outside the requested window so the result honours the
            // caller's hoursBack.
            if (inner.createdAt < sinceSecs) continue
            if (peerPub != null && peerPub !in inner.groupMembers()) continue

            messages.add(
                DmMessage(
                    fromNpub = NPub.create(inner.pubKey),
                    fromPubkeyHex = inner.pubKey,
                    fromDisplayName = displayNameOf(inner.pubKey),
                    sentByMe = inner.pubKey == myPub,
                    content = inner.content,
                    createdAt = inner.createdAt,
                ),
            )
        }

        return DmsResult(
            windowHours = cappedHours,
            messages =
                messages
                    .sortedByDescending { it.createdAt }
                    .take(cappedLimit),
        )
    }

    /**
     * NIP-50 search restricted to NIP-23 long-form articles
     * (kind:30023). Use for "find Nostr articles about [topic]" when
     * the user wants written-up posts rather than short notes.
     *
     * @param query free-form search text.
     * @param limit max articles to return, capped to 50. Default 10.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun searchArticles(
        appFunctionContext: AppFunctionContext,
        query: String,
        limit: Int = 10,
    ): SearchNotesResult {
        val cappedLimit = limit.coerceIn(1, 50)
        val filter =
            SearchActions.searchNotesFilter(
                query = query,
                kinds = listOf(LongTextNoteEvent.KIND),
                limit = cappedLimit,
            ) ?: return SearchNotesResult.empty()

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
                .mapNotNull { it as? LongTextNoteEvent }
                .take(cappedLimit)
                .map { ev ->
                    // Long-form articles can be book-length; cap the
                    // content payload so the AppFunctions result stays
                    // bounded — Gemini can ask for a follow-up if needed.
                    val snippet =
                        if (ev.content.length > LONG_FORM_SNIPPET_LIMIT) {
                            ev.content.take(LONG_FORM_SNIPPET_LIMIT) + "…"
                        } else {
                            ev.content
                        }
                    NoteHit(
                        eventId = ev.id,
                        npub = NPub.create(ev.pubKey),
                        pubkeyHex = ev.pubKey,
                        authorDisplayName = displayNameOf(ev.pubKey),
                        createdAt = ev.createdAt,
                        content = snippet,
                    )
                }

        return SearchNotesResult(matches = hits)
    }

    /**
     * Live audio/video streams currently broadcasting on Nostr (NIP-53
     * kind:30311 events with `status=live`). Use for "what's live on
     * Nostr right now?".
     *
     * @param limit max streams to return, capped to 50. Default 20.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getLiveStreams(
        appFunctionContext: AppFunctionContext,
        limit: Int = 20,
    ): LiveStreamsResult {
        val cappedLimit = limit.coerceIn(1, 50)
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: return LiveStreamsResult.empty()
        val client = Amethyst.instance.client
        val relays =
            account.homeRelays.flow.value
                .ifEmpty { DefaultNIP65RelaySet }
        if (relays.isEmpty()) return LiveStreamsResult.empty()

        // NIP-53 has no `since` semantics — a live activity can have an
        // arbitrarily old createdAt. We over-fetch and post-filter for
        // `isLive()`, which also applies the 8-hour staleness guard
        // (status=live + recent createdAt) baked into quartz.
        val filter =
            Filter(
                kinds = listOf(LiveActivitiesEvent.KIND),
                limit = cappedLimit * 4,
            )
        val events =
            client.fetchAll(
                filters = relays.associateWith { listOf(filter) },
                timeoutMs = GEMINI_FETCH_TIMEOUT_MS,
            )

        val streams =
            events
                .mapNotNull { it as? LiveActivitiesEvent }
                .filter { it.isLive() }
                .take(cappedLimit)
                .map { ev ->
                    val hostPub = ev.host()?.pubKey
                    LiveStreamHit(
                        eventId = ev.id,
                        title = ev.title(),
                        summary = ev.summary(),
                        streamingUrl = ev.streaming(),
                        hostNpub = hostPub?.let { NPub.create(it) },
                        hostPubkeyHex = hostPub,
                        hostDisplayName = hostPub?.let { displayNameOf(it) },
                        startsAt = ev.starts(),
                        createdAt = ev.createdAt,
                    )
                }

        return LiveStreamsResult(streams = streams)
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

    /**
     * Look up the cached display name for a pubkey. Returns null when no
     * kind:0 has been observed for this user yet — caller renders the
     * npub instead.
     *
     * Cheap in-memory read against the same LocalCache the foreground UI
     * uses; no relay round-trip, no allocation beyond the lookup.
     */
    private fun displayNameOf(pubkey: HexKey): String? =
        Amethyst.instance.cache
            .checkGetOrCreateUser(pubkey)
            ?.metadataOrNull()
            ?.bestName()

    private fun TextNoteEvent.toNoteHit(): NoteHit =
        NoteHit(
            eventId = id,
            npub = NPub.create(pubKey),
            pubkeyHex = pubKey,
            authorDisplayName = displayNameOf(pubKey),
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

        /**
         * Cap on the content payload returned from [searchArticles] — NIP-23
         * articles can be book-length; truncate so the AppFunctions response
         * stays bounded. Gemini can show the snippet and ask the user
         * whether to fetch the full article.
         */
        private const val LONG_FORM_SNIPPET_LIMIT = 2_000
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
    /** Best-effort display name of the author from the local kind:0 cache.
     *  Null when the author's profile hasn't been seen yet — caller renders
     *  the npub instead. */
    val authorDisplayName: String?,
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
 * Aggregate of NIP-57 zaps received in a recent time window. Returned
 * by [AmethystAppFunctions.getZapsReceived].
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
class ZapsReceivedResult(
    /** Window size in hours that was actually queried (after capping). */
    val windowHours: Int,
    /** Sum of sats from every parseable bolt11 invoice in the window. */
    val totalSats: Long,
    /** Total kind:9735 receipts observed — includes ones with unparseable invoices. */
    val zapCount: Int,
    /** Distinct zapping pubkeys, best-effort from the `P` / second-`p` tag. */
    val uniqueZapperCount: Int,
    /** Receipts whose bolt11 couldn't be parsed and didn't contribute to [totalSats]. */
    val unparseableInvoiceCount: Int,
) {
    companion object {
        fun empty() =
            ZapsReceivedResult(
                windowHours = 0,
                totalSats = 0L,
                zapCount = 0,
                uniqueZapperCount = 0,
                unparseableInvoiceCount = 0,
            )
    }
}

/** One decrypted NIP-17 direct message returned by [AmethystAppFunctions.getRecentDms]. */
@AppFunctionSerializable(isDescribedByKDoc = true)
class DmMessage(
    /** Bech32 npub of the sender. */
    val fromNpub: String,
    /** Hex pubkey of the sender. */
    val fromPubkeyHex: String,
    /** Best-effort display name of the sender from the local kind:0 cache. */
    val fromDisplayName: String?,
    /** True when the active account sent this message — useful for the
     *  caller to distinguish "Alice said X" from "I said Y" when both
     *  appear in the same thread snapshot. */
    val sentByMe: Boolean,
    /** Plaintext message body. */
    val content: String,
    /** Unix-seconds timestamp of the inner kind:14 event. */
    val createdAt: Long,
)

/** Decrypted recent NIP-17 DMs in a time window. */
@AppFunctionSerializable(isDescribedByKDoc = true)
class DmsResult(
    /** Window size in hours that was actually queried. */
    val windowHours: Int,
    /** Messages, newest first. Capped to the caller's limit. */
    val messages: List<DmMessage>,
) {
    companion object {
        fun empty() = DmsResult(windowHours = 0, messages = emptyList())
    }
}

/** Single hit from [AmethystAppFunctions.getLiveStreams]. */
@AppFunctionSerializable(isDescribedByKDoc = true)
class LiveStreamHit(
    /** Hex event id of the kind:30311 announcement. */
    val eventId: String,
    /** Stream title from the `title` tag, or null when absent. */
    val title: String?,
    /** Short description from the `summary` tag, or null when absent. */
    val summary: String?,
    /** The URL where the stream is playable (HLS / WebRTC / etc.) from
     *  the `streaming` tag. Null when the announcement carries no
     *  streaming endpoint — caller has nothing to play. */
    val streamingUrl: String?,
    /** Bech32 npub of the host, when a host tag is present. */
    val hostNpub: String?,
    /** Hex pubkey of the host, when a host tag is present. */
    val hostPubkeyHex: String?,
    /** Best-effort display name of the host from the local kind:0 cache. */
    val hostDisplayName: String?,
    /** Unix-seconds timestamp the stream's `starts` tag points to. */
    val startsAt: Long?,
    /** Unix-seconds timestamp of the kind:30311 event itself. */
    val createdAt: Long,
)

@AppFunctionSerializable(isDescribedByKDoc = true)
class LiveStreamsResult(
    /** Currently-live streams, in the order they were observed. */
    val streams: List<LiveStreamHit>,
) {
    companion object {
        fun empty() = LiveStreamsResult(streams = emptyList())
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
