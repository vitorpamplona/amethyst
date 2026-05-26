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
import androidx.appfunctions.AppFunctionNotSupportedException
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.service.AppFunction
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.actions.DmActions
import com.vitorpamplona.amethyst.commons.actions.FollowActions
import com.vitorpamplona.amethyst.commons.actions.SearchActions
import com.vitorpamplona.amethyst.commons.actions.ZapActions
import com.vitorpamplona.amethyst.commons.defaults.DefaultNIP65RelaySet
import com.vitorpamplona.amethyst.commons.relayClient.nip17Dm.unwrapAndUnsealOrNull
import com.vitorpamplona.amethyst.commons.services.lnurl.LightningAddressResolver
import com.vitorpamplona.quartz.lightning.LnInvoiceUtil
import com.vitorpamplona.quartz.marmot.RecipientRelayFetcher
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirmDetailed
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
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
 * Read verbs work with any account state. Write verbs (post / follow /
 * unfollow / sendDm) require a signer that can sign in-process — i.e.
 * a local [NostrSignerInternal] or a remote NIP-46 bunker. NIP-55
 * external signers (Amber) are refused with
 * [AppFunctionNotSupportedException] for now because the agent
 * dispatch happens outside the foreground task stack — the user can't
 * see the Amber approval activity from inside Gemini. See
 * `amethyst/plans/2026-05-25-appfunctions-signer-prompts.md` for the
 * design and the planned PendingIntent escape hatch.
 *
 * Account scoping uses the currently active account from
 * [com.vitorpamplona.amethyst.Amethyst.instance.sessionManager] — the same
 * Account the foreground UI is bound to. When no account is signed in,
 * every function returns an empty result rather than failing the call.
 */
class AmethystAppFunctions {
    /**
     * Find a person on Nostr by name, handle, or NIP-05. Use when the user
     * wants to look someone up on Nostr ("find vitor on nostr", "search for
     * jack dorsey", "who is alice@damus on nostr"), translate a display
     * name to an npub, or discover a user before following / DMing /
     * zapping them.
     *
     * Backed by NIP-50 full-text search across the active account's
     * configured search relays (kind:10007), with a fallback to
     * Amethyst's curated default search-relay set.
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
     * Read the user's Nostr timeline / home feed. Use when the user asks
     * "what's new on Nostr", "what's happening on Nostr today", "catch me
     * up on my Nostr feed", or wants a summary of recent posts from
     * people they follow.
     *
     * Drains recent kind:1 short text notes from the people the active
     * account follows; the same query the Amethyst home-feed UI runs,
     * truncated to one batch. Queries the account's home relays (NIP-65
     * outbox + any private storage + local relays).
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
     * Read recent Nostr posts from a specific user. Use when the user
     * asks "what did Snowden post recently on Nostr", "catch me up on
     * what Jack has been posting", "show me Alice's latest notes", or
     * wants to see one specific Nostr user's activity.
     *
     * Pass the target user's npub or hex pubkey — use [searchProfiles]
     * first if you only have a display name. Queries the target's
     * NIP-65 write relays when cached, falling back to the active
     * account's home relays.
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
     * Look up one Nostr profile by npub or hex pubkey. Use when the user
     * asks "who is npub1…", "tell me about [npub]", "what's [user]'s
     * Nostr profile", or wants the bio / NIP-05 / Lightning address of a
     * specific Nostr user.
     *
     * Returns the latest kind:0 metadata — cache-first, with a short
     * network fallback when the user's profile hasn't been observed
     * locally yet.
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
     * Find Nostr posts about a topic via hashtag. Use when the user asks
     * "show me Nostr posts about Bitcoin", "find Nostr discussion of
     * #Tor", "what's the Nostr take on [topic]", or wants to browse
     * conversation about a specific subject.
     *
     * Pass the tag value without the leading `#` — "bitcoin", not
     * "#bitcoin". The hashtag is lowercased before matching (the
     * convention most Nostr clients follow).
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
     * Report who the user is signed in as on Nostr. Use when the user
     * asks "who am I logged in as on Nostr", "what's my npub", "what's
     * my Nostr identity", "how many people do I follow on Nostr", or
     * any other "tell me about my Nostr account" query.
     *
     * Returns the active account's npub, display name, NIP-05 handle,
     * follow count, and how many relays are configured for outbox /
     * DM inbox. Use this for Nostr-side diagnostics rather than as a
     * general "who am I" answer.
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
     * Report how many sats the user earned on Nostr in a time window.
     * Use when the user asks "did I get any zaps today", "how many sats
     * did I earn on Nostr this week", "did anyone zap my last post",
     * or wants a summary of incoming NIP-57 Lightning zaps.
     *
     * Drains kind:9735 zap receipts addressed to the user in the window
     * and parses the bolt11 invoice from each to compute total sats.
     * Returns total + per-window zap count + unique zapper count.
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
     * Read recent Nostr direct messages. Use when the user asks "did I
     * get any Nostr DMs", "what did Alice DM me", "show me my recent
     * Nostr messages", "summarize my unread Nostr DMs", or wants
     * decrypted message content (not just notifications) from Nostr.
     *
     * Drains NIP-17 gift wraps from the active account's DM-inbox
     * relays, decrypts each in-process (Amethyst is the only place
     * the user's NIP-44 keys live), and returns the inner kind:14
     * messages with sender display names attached. File-attachment
     * DMs (kind:15) are filtered out for now to keep the response
     * small.
     *
     * @param peer optional npub/hex; when set, only returns messages
     *   to/from that specific peer. When null, returns conversations
     *   with anyone.
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

    // ------------------------------------------------------------------
    // Write verbs — gated on a signer that can sign without launching a
    // foreground activity. See requireInProcessSigner below.
    // ------------------------------------------------------------------

    /**
     * Publish a short text note on Nostr. Use when the user asks "post
     * this to Nostr", "tweet this on Nostr", "share [X] on Nostr",
     * "publish a Nostr note saying [X]", or any other "send to Nostr"
     * intent for plain-text content.
     *
     * Publishes a NIP-10 kind:1 short text note as the signed-in user,
     * broadcast to the account's configured outbox relays. Returns per-
     * relay ack so the caller can confirm the post landed.
     *
     * @param text the note body. Cannot be blank; capped at 8000
     *   characters so an accidentally-pasted document doesn't try to
     *   become a Nostr post.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun postNote(
        appFunctionContext: AppFunctionContext,
        text: String,
    ): WriteResult {
        val body = text.trim()
        if (body.isEmpty()) throw AppFunctionInvalidArgumentException("text cannot be blank")
        if (body.length > MAX_NOTE_LENGTH) {
            throw AppFunctionInvalidArgumentException("text is $${body.length} chars; cap is $MAX_NOTE_LENGTH")
        }

        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: throw notSignedIn()
        requireInProcessSigner(account.signer)
        val relays = account.outboxRelays.flow.value
        if (relays.isEmpty()) throw AppFunctionInvalidArgumentException("account has no outbox relays configured")

        val template = TextNoteEvent.build(body)
        val signed = account.signer.sign(template)
        // Mirror the foreground UI: cache the freshly-signed event so
        // subsequent reads see it without waiting for a relay echo.
        account.cache.justConsumeMyOwnEvent(signed)
        val ack = Amethyst.instance.client.publishAndConfirmDetailed(signed, relays, PUBLISH_TIMEOUT_SECS)

        return WriteResult.from(signed.id, ack)
    }

    /**
     * Follow a user on Nostr. Use when the user asks "follow [X] on
     * Nostr", "add [npub] to my Nostr follows", or "subscribe to
     * [user]" with a Nostr context. Idempotent — re-following someone
     * already followed is a safe no-op.
     *
     * Adds [user] to the signed-in account's NIP-02 kind:3 follow list
     * and publishes the updated list. [WriteResult.changed] reports
     * `false` when the user is already followed.
     *
     * @param user npub (`npub1…`) or 64-character hex pubkey.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun followUser(
        appFunctionContext: AppFunctionContext,
        user: String,
    ): WriteResult {
        val target = decodeUserOrThrow(user)
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: throw notSignedIn()
        if (target == account.signer.pubKey) {
            throw AppFunctionInvalidArgumentException("cannot follow yourself")
        }
        requireInProcessSigner(account.signer)
        val relays = account.outboxRelays.flow.value
        if (relays.isEmpty()) throw AppFunctionInvalidArgumentException("account has no outbox relays configured")

        val currentList = account.kind3FollowList.getFollowListEvent()
        if (currentList != null && currentList.isTaggedUser(target)) {
            return WriteResult.unchanged()
        }

        // Relay hint from cached kind:10002 so the follow tag points
        // readers at where the target publishes.
        val relayHint =
            account.cache
                .checkGetOrCreateUser(target)
                ?.outboxRelays()
                ?.firstOrNull()

        val newList =
            FollowActions.buildFollow(
                signer = account.signer,
                pubkeyToFollow = target,
                currentContactList = currentList,
                relayHint = relayHint,
            )
        account.cache.justConsumeMyOwnEvent(newList)
        val ack = Amethyst.instance.client.publishAndConfirmDetailed(newList, relays, PUBLISH_TIMEOUT_SECS)
        return WriteResult.from(newList.id, ack)
    }

    /**
     * Unfollow a user on Nostr. Use when the user asks "unfollow [X]
     * on Nostr", "remove [npub] from my Nostr follows", or "stop
     * following [user]" with a Nostr context. Idempotent — unfollowing
     * someone the user wasn't following is a safe no-op.
     *
     * Removes [user] from the signed-in account's NIP-02 kind:3
     * follow list and publishes the updated list. [WriteResult.changed]
     * reports `false` when the user wasn't followed.
     *
     * @param user npub (`npub1…`) or 64-character hex pubkey.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun unfollowUser(
        appFunctionContext: AppFunctionContext,
        user: String,
    ): WriteResult {
        val target = decodeUserOrThrow(user)
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: throw notSignedIn()
        requireInProcessSigner(account.signer)
        val relays = account.outboxRelays.flow.value
        if (relays.isEmpty()) throw AppFunctionInvalidArgumentException("account has no outbox relays configured")

        val currentList = account.kind3FollowList.getFollowListEvent()
        if (currentList == null || !currentList.isTaggedUser(target)) {
            return WriteResult.unchanged()
        }

        val newList =
            FollowActions.buildUnfollow(
                signer = account.signer,
                pubkeyToUnfollow = target,
                currentContactList = currentList,
            ) ?: return WriteResult.unchanged()
        account.cache.justConsumeMyOwnEvent(newList)
        val ack = Amethyst.instance.client.publishAndConfirmDetailed(newList, relays, PUBLISH_TIMEOUT_SECS)
        return WriteResult.from(newList.id, ack)
    }

    /**
     * Send a direct message to a user on Nostr. Use when the user asks
     * "DM [X] on Nostr", "send a Nostr message to [user] saying [Y]",
     * "message [npub] on Nostr", or any other "send a private message"
     * intent in a Nostr context.
     *
     * The message is gift-wrapped (kind:1059) per NIP-59 — only the
     * recipient (and the signed-in user, who keeps their own copy)
     * can decrypt it. Recipients without a published kind:10050
     * DM-inbox list fall back through NIP-65 read relays then
     * bootstrap relays.
     *
     * @param recipient npub (`npub1…`) or 64-character hex pubkey.
     * @param text the message body. Cannot be blank; capped at 8000
     *   characters.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun sendDm(
        appFunctionContext: AppFunctionContext,
        recipient: String,
        text: String,
    ): SendDmResult {
        val body = text.trim()
        if (body.isEmpty()) throw AppFunctionInvalidArgumentException("text cannot be blank")
        if (body.length > MAX_NOTE_LENGTH) {
            throw AppFunctionInvalidArgumentException("text is $${body.length} chars; cap is $MAX_NOTE_LENGTH")
        }
        val recipientPub = decodeUserOrThrow(recipient)
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: throw notSignedIn()
        requireInProcessSigner(account.signer)

        val client = Amethyst.instance.client
        val result = DmActions.buildTextDm(account.signer, recipientPub, body)

        // One wrap per recipient — for a 1:1 DM that's two (the recipient's
        // copy + the sender's own copy on the sender's inbox).
        val deliveries = mutableListOf<DmDelivery>()
        for (wrap in result.wraps) {
            val target = wrap.recipientPubKey() ?: continue
            // Fetch the recipient's kind:10050 / 10051 / 10002 fresh — local
            // cache may be stale for users we rarely interact with, and the
            // cost is one short drain on already-warmed sockets.
            val lists =
                RecipientRelayFetcher.fetchRelayLists(client, target, account.outboxRelays.flow.value)
            val resolution =
                DmActions.resolveDmRelays(
                    recipientLists = lists,
                    bootstrap = account.outboxRelays.flow.value,
                    allowFallback = true,
                )
            if (resolution.relays.isEmpty()) {
                deliveries.add(
                    DmDelivery(
                        recipientNpub = NPub.create(target),
                        recipientPubkeyHex = target,
                        wrapId = wrap.id,
                        publishedTo = emptyList(),
                        rejectedBy = emptyList(),
                        relaySource = resolution.source.name.lowercase(),
                    ),
                )
                continue
            }
            val ack = client.publishAndConfirmDetailed(wrap, resolution.relays, PUBLISH_TIMEOUT_SECS)
            deliveries.add(
                DmDelivery(
                    recipientNpub = NPub.create(target),
                    recipientPubkeyHex = target,
                    wrapId = wrap.id,
                    publishedTo = ack.filterValues { it }.keys.map { it.url },
                    rejectedBy = ack.filterValues { !it }.keys.map { it.url },
                    relaySource = resolution.source.name.lowercase(),
                ),
            )
        }
        // Cache the inner kind:14 so the foreground UI sees the message
        // immediately in the relevant DM thread.
        account.cache.justConsumeMyOwnEvent(result.msg)

        return SendDmResult(
            messageEventId = result.msg.id,
            deliveries = deliveries,
        )
    }

    /**
     * Tip a Nostr user with Lightning sats (NIP-57 profile zap). Use
     * when the user asks "zap [X] on Nostr", "tip [user] [N] sats",
     * "send a Lightning tip to [npub]", or "thank [user] with sats".
     *
     * Builds the NIP-57 kind:9734 zap request and fetches a BOLT11
     * invoice from the recipient's Lightning service. Returns the
     * invoice — the user pastes it into a Lightning wallet to settle.
     * (NWC auto-pay is a separate verb, not yet exposed.)
     *
     * Defaults to 21 sats — the canonical "small thank-you" zap. Cap
     * is 1,000,000 sats so an accidental tip can't drain a wallet.
     *
     * @param user npub (`npub1…`) or 64-character hex pubkey of the
     *   zap recipient.
     * @param sats amount to zap, in whole sats. Capped at 1,000,000
     *   sats. Default 21.
     * @param comment optional message to attach to the zap. Capped at
     *   280 characters.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun zapUser(
        appFunctionContext: AppFunctionContext,
        user: String,
        sats: Long = 21,
        comment: String? = null,
    ): ZapResult {
        val cappedSats = sats.coerceIn(1L, MAX_ZAP_SATS)
        val trimmedComment = comment.orEmpty().trim().take(MAX_ZAP_COMMENT_LENGTH)
        val recipientPub = decodeUserOrThrow(user)
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: throw notSignedIn()
        requireInProcessSigner(account.signer)
        val client = Amethyst.instance.client

        // Pull the recipient's kind:0 — needs lnAddress to receive the zap.
        val metadata =
            account.cache
                .checkGetOrCreateUser(recipientPub)
                ?.metadataOrNull()
                ?.flow
                ?.value
                ?.info
                ?.let { extractLnAddressFromMetadata(it) }
                ?: fetchProfileForZap(client, account, recipientPub)
                ?: throw AppFunctionInvalidArgumentException(
                    "No kind:0 metadata for $user — recipient must have a Nostr profile first.",
                )
        val lnAddress =
            metadata.takeIf { it.isNotBlank() }
                ?: throw AppFunctionInvalidArgumentException(
                    "Recipient has no lud16 or lud06 in their profile — they can't receive Lightning zaps.",
                )

        val zapRequest =
            ZapActions.buildUserZapRequest(
                signer = account.signer,
                recipientPubkey = recipientPub,
                amountMillisats = ZapActions.satsToMillisats(cappedSats),
                inboxRelays = account.nip65RelayList.inboxFlow.value,
                comment = trimmedComment,
                zapType = LnZapEvent.ZapType.PUBLIC,
            )

        val invoice = fetchInvoiceOrThrow(lnAddress, cappedSats, trimmedComment, zapRequest)

        return ZapResult(
            recipientNpub = NPub.create(recipientPub),
            recipientPubkeyHex = recipientPub,
            recipientDisplayName = displayNameOf(recipientPub),
            lnAddress = lnAddress,
            amountSats = cappedSats,
            comment = trimmedComment,
            invoice = invoice,
            zapRequestId = zapRequest.id,
        )
    }

    /**
     * Zap a specific Nostr note (NIP-57 event zap). Use when the user
     * asks "zap this Nostr post", "tip the author of [event id]",
     * "send sats for that Nostr note about [X]", or "boost this Nostr
     * post with sats".
     *
     * Honors NIP-57 zap-split tags — a post with multiple `zap` tags
     * produces one invoice per recipient, proportional to weight, so
     * a multi-party collab post pays everyone correctly. Returns one
     * BOLT11 invoice per recipient; user pays each in a Lightning
     * wallet to complete the zap.
     *
     * @param eventId 64-character hex id of the note to zap. Must be
     *   in the local cache — get it via [getNotesByUser] /
     *   [getRecentFromFollows] / [searchByHashtag] / [searchNotes]
     *   first.
     * @param sats total amount to zap, in whole sats. Capped at
     *   1,000,000.
     * @param comment optional message attached to every zap request.
     *   Capped at 280 characters.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun zapEvent(
        appFunctionContext: AppFunctionContext,
        eventId: String,
        sats: Long = 21,
        comment: String? = null,
    ): ZapEventResult {
        if (eventId.length != 64) {
            throw AppFunctionInvalidArgumentException("eventId must be 64-character hex")
        }
        val cappedSats = sats.coerceIn(1L, MAX_ZAP_SATS)
        val trimmedComment = comment.orEmpty().trim().take(MAX_ZAP_COMMENT_LENGTH)
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: throw notSignedIn()
        requireInProcessSigner(account.signer)

        val note =
            account.cache.getNoteIfExists(eventId)
                ?: throw AppFunctionInvalidArgumentException(
                    "Event $eventId not in local cache. Fetch it via getNotesByUser or " +
                        "getRecentFromFollows first, or open the note in Amethyst.",
                )
        val event =
            note.event
                ?: throw AppFunctionInvalidArgumentException(
                    "Event $eventId is referenced locally but its content hasn't been observed yet.",
                )

        val client = Amethyst.instance.client
        val totalMsats = ZapActions.satsToMillisats(cappedSats)

        // Lookups for the split resolver — first try the local cache,
        // then fall back to a one-shot network drain.
        val lookupLnAddress: suspend (HexKey) -> String? = { pk ->
            account.cache
                .checkGetOrCreateUser(pk)
                ?.metadataOrNull()
                ?.lnAddress()
                ?: fetchProfileForZap(client, account, pk)
        }
        val lookupInboxRelays: suspend (HexKey) -> Set<NormalizedRelayUrl> = { pk ->
            account.cache
                .checkGetOrCreateUser(pk)
                ?.inboxRelays()
                ?.toSet()
                .orEmpty()
        }

        val requests =
            ZapActions.buildEventZapRequestsForSplits(
                signer = account.signer,
                zappedEvent = event,
                totalAmountMillisats = totalMsats,
                senderInboxRelays = account.nip65RelayList.inboxFlow.value,
                lookupLnAddress = lookupLnAddress,
                lookupInboxRelays = lookupInboxRelays,
                comment = trimmedComment,
                zapType = LnZapEvent.ZapType.PUBLIC,
            )
        if (requests.isEmpty()) {
            throw AppFunctionInvalidArgumentException(
                "No payable recipients — neither the author nor any zap-split recipient has a usable Lightning address.",
            )
        }

        val invoices =
            requests.map { req ->
                val shareSats = req.amountMillisats / 1000
                val result =
                    runCatching {
                        fetchInvoiceOrThrow(
                            lnAddress = req.recipient.lnAddress,
                            sats = shareSats,
                            comment = trimmedComment,
                            zapRequest = req.request,
                        )
                    }
                ZapInvoice(
                    recipientNpub = req.recipient.pubkey?.let { NPub.create(it) },
                    recipientPubkeyHex = req.recipient.pubkey,
                    recipientDisplayName = req.recipient.pubkey?.let { displayNameOf(it) },
                    lnAddress = req.recipient.lnAddress,
                    weight = req.recipient.weight,
                    amountSats = shareSats,
                    invoice = result.getOrNull(),
                    invoiceError = result.exceptionOrNull()?.message,
                    zapRequestId = req.request.id,
                )
            }

        return ZapEventResult(
            zappedEventId = eventId,
            requestedSats = cappedSats,
            billedSats = invoices.sumOf { it.amountSats },
            comment = trimmedComment,
            invoices = invoices,
        )
    }

    /** Read lnAddress out of an already-resolved UserMetadata. */
    private fun extractLnAddressFromMetadata(info: com.vitorpamplona.quartz.nip01Core.metadata.UserMetadata): String? = info.lnAddress()

    /**
     * Cache miss path for zap recipient profile lookup. Drain the
     * recipient's NIP-65 outbox / our home relays for their kind:0;
     * returns the lnAddress directly so callers don't have to re-parse
     * the metadata blob.
     */
    private suspend fun fetchProfileForZap(
        client: com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient,
        account: com.vitorpamplona.amethyst.model.Account,
        pubkey: HexKey,
    ): String? {
        val relays =
            account.cache
                .checkGetOrCreateUser(pubkey)
                ?.outboxRelays()
                ?.toSet()
                ?.ifEmpty { account.homeRelays.flow.value }
                ?: account.homeRelays.flow.value
        if (relays.isEmpty()) return null

        val filter = Filter(kinds = listOf(MetadataEvent.KIND), authors = listOf(pubkey), limit = 1)
        return client
            .fetchAll(
                filters = relays.associateWith { listOf(filter) },
                timeoutMs = GEMINI_FETCH_TIMEOUT_MS,
            ).mapNotNull { it as? MetadataEvent }
            .maxByOrNull { it.createdAt }
            ?.contactMetaData()
            ?.lnAddress()
    }

    /**
     * LNURL-pay round-trip: resolves the LN address to a callback URL,
     * posts the zap request, returns the BOLT11 invoice. Uses
     * Amethyst's roleBasedHttpClientBuilder so the request honors the
     * user's Tor / money-routing preferences.
     */
    private suspend fun fetchInvoiceOrThrow(
        lnAddress: String,
        sats: Long,
        comment: String,
        zapRequest: com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent,
    ): String {
        // Compute the LNURL-pay endpoint so we can ask the privacy-aware
        // HttpClient builder for the right OkHttpClient for that host.
        val endpointUrl =
            LightningAddressResolver(httpClient = okhttp3.OkHttpClient()).assembleUrl(lnAddress)
                ?: throw AppFunctionInvalidArgumentException("Couldn't resolve LN address '$lnAddress' to an LNURL-pay URL.")
        val client = Amethyst.instance.roleBasedHttpClientBuilder.okHttpClientForMoney(endpointUrl)
        val resolver = LightningAddressResolver(httpClient = client)
        val result =
            resolver.fetchInvoice(
                lnAddress = lnAddress,
                milliSats = ZapActions.satsToMillisats(sats),
                message = comment,
                zapRequest = zapRequest,
            )
        return when (result) {
            is LightningAddressResolver.Result.Success -> result.invoice
            is LightningAddressResolver.Result.Error ->
                throw AppFunctionInvalidArgumentException("Lightning service rejected the zap: ${result.message}")
        }
    }

    /**
     * Reject the call when the active signer can't sign in-process —
     * NIP-55 external signers (Amber) need a foreground activity to
     * show the user an approval prompt, which we can't launch from a
     * background AppFunctionService dispatch.
     *
     * Throws [AppFunctionNotSupportedException] when the user's signer
     * is read-only, and a typed [AppFunctionNotSupportedException]
     * with a clarifying message when it's an external signer.
     */
    private fun requireInProcessSigner(signer: NostrSigner) {
        if (!signer.isWriteable()) {
            throw AppFunctionNotSupportedException(
                "Active Amethyst account is read-only (npub login). Sign in with a private key or NIP-46 bunker to publish.",
            )
        }
        // NostrSignerExternal lives in quartz/androidMain and isn't visible
        // to commonMain — but we're already in android-app code, so the
        // class is on the classpath. Reflective name-check keeps the
        // dependency edge clean and avoids hard-coupling the bridge to
        // the NIP-55 implementation class.
        val klass = signer::class.qualifiedName
        if (klass == "com.vitorpamplona.quartz.nip55AndroidSigner.client.NostrSignerExternal") {
            throw AppFunctionNotSupportedException(
                "Amethyst is configured to use an external NIP-55 signer (Amber). " +
                    "Write actions from Gemini aren't supported with this signer yet — " +
                    "they require Amber's approval activity which can't launch from a " +
                    "background dispatch. Open Amethyst directly to complete the action.",
            )
        }
    }

    private fun notSignedIn(): AppFunctionNotSupportedException = AppFunctionNotSupportedException("No Amethyst account is signed in.")

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

        /**
         * Cap on the body of a Gemini-driven write (postNote / sendDm).
         * Anything larger is almost certainly an accidentally-pasted
         * document; bail out with a typed error instead of silently
         * publishing a wall of text to relays.
         */
        private const val MAX_NOTE_LENGTH = 8_000

        /**
         * Per-publish ack window. We wait this long for OK responses
         * from each relay; relays that don't answer in time are
         * reported as `rejectedBy` (no ack, no event). 15 s lines up
         * with what `cli/Context.publish` uses.
         */
        private const val PUBLISH_TIMEOUT_SECS = 15L

        /** Upper bound on a single zap. Anything above this is almost
         *  certainly a typo; bail out instead of letting Gemini bill
         *  the user a million sats by accident. */
        private const val MAX_ZAP_SATS = 1_000_000L

        /** LN providers typically reject longer comments — capping at
         *  280 keeps us under the most aggressive ceilings while still
         *  fitting a tweet-length thank-you note. */
        private const val MAX_ZAP_COMMENT_LENGTH = 280
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

/**
 * Result of a single-event write verb (postNote / followUser /
 * unfollowUser). When the verb is a no-op — already following, not
 * following, content unchanged — [changed] is false and [eventId] is
 * null; the relay lists are empty for the same reason.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
class WriteResult(
    /** True when a new event was actually signed and published. */
    val changed: Boolean,
    /** Hex event id of the signed event, or null when the verb was a no-op. */
    val eventId: String?,
    /** Relays that ACK'd the publish. */
    val publishedTo: List<String>,
    /** Relays that rejected the event or didn't answer in time. */
    val rejectedBy: List<String>,
) {
    companion object {
        fun unchanged() =
            WriteResult(
                changed = false,
                eventId = null,
                publishedTo = emptyList(),
                rejectedBy = emptyList(),
            )

        fun from(
            eventId: String,
            ack: Map<com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl, Boolean>,
        ) = WriteResult(
            changed = true,
            eventId = eventId,
            publishedTo = ack.filterValues { it }.keys.map { it.url },
            rejectedBy = ack.filterValues { !it }.keys.map { it.url },
        )
    }
}

/**
 * Per-recipient delivery status for a NIP-17 DM send. A 1:1 DM
 * produces two entries — the recipient's wrap and the sender's own
 * copy on their own DM-inbox relays. [relaySource] reports which
 * bucket the relays were drawn from: `kind_10050`, `nip65_read`,
 * `bootstrap`, or `none`.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
class DmDelivery(
    /** Bech32 npub of the recipient this wrap was addressed to. */
    val recipientNpub: String,
    /** Hex pubkey of the recipient. */
    val recipientPubkeyHex: String,
    /** Hex event id of the kind:1059 gift wrap published to this recipient. */
    val wrapId: String,
    /** Relays that ACK'd this wrap. */
    val publishedTo: List<String>,
    /** Relays that rejected this wrap or didn't answer in time. */
    val rejectedBy: List<String>,
    /** Bucket the relays were resolved from: kind_10050 / nip65_read / bootstrap / none. */
    val relaySource: String,
)

/** Result of [AmethystAppFunctions.sendDm]. */
@AppFunctionSerializable(isDescribedByKDoc = true)
class SendDmResult(
    /** Hex event id of the inner kind:14 (the plaintext message — only the
     *  signer and the recipient know it; relays only see the kind:1059 wraps). */
    val messageEventId: String,
    /** One entry per gift-wrap delivery. */
    val deliveries: List<DmDelivery>,
)

/**
 * Result of [AmethystAppFunctions.zapUser]. Carries the BOLT11 invoice
 * the user needs to pay in their Lightning wallet — this verb doesn't
 * auto-pay (NWC integration is a separate, not-yet-exposed verb).
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
class ZapResult(
    /** Bech32 npub of the zap recipient. */
    val recipientNpub: String,
    /** Hex pubkey of the recipient. */
    val recipientPubkeyHex: String,
    /** Best-effort display name from the local kind:0 cache. */
    val recipientDisplayName: String?,
    /** LN address the invoice was fetched from. */
    val lnAddress: String,
    /** Amount actually requested (after capping). */
    val amountSats: Long,
    /** Comment attached to the zap (truncated to 280 chars). */
    val comment: String,
    /** BOLT11 invoice the user pastes into a Lightning wallet. */
    val invoice: String,
    /** Hex event id of the signed kind:9734 zap request. */
    val zapRequestId: String,
)

/**
 * Per-recipient BOLT11 invoice for an event zap. Multiple invoices
 * appear when the zapped note carries NIP-57 zap-split tags.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
class ZapInvoice(
    /** Bech32 npub of the recipient, or null when the split tag carried
     *  only an LN address with no pubkey. */
    val recipientNpub: String?,
    /** Hex pubkey of the recipient, or null when only an LN address was given. */
    val recipientPubkeyHex: String?,
    /** Best-effort display name from the cache, when the recipient is known. */
    val recipientDisplayName: String?,
    /** LN address the invoice was fetched from. */
    val lnAddress: String,
    /** Relative weight in the zap split — 1.0 for unweighted recipients. */
    val weight: Double,
    /** This recipient's share of the total in whole sats. */
    val amountSats: Long,
    /** BOLT11 invoice, or null when the Lightning provider failed
     *  (see [invoiceError] for the reason). */
    val invoice: String?,
    /** Failure reason from the Lightning provider when [invoice] is null. */
    val invoiceError: String?,
    /** Hex event id of this recipient's kind:9734 zap request. */
    val zapRequestId: String,
)

/**
 * Result of [AmethystAppFunctions.zapEvent]. Total billed sats may
 * differ from requested by a few sats due to whole-sat rounding in the
 * splits — same drift the foreground UI has.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
class ZapEventResult(
    /** Hex event id of the note being zapped. */
    val zappedEventId: String,
    /** Total sats the caller asked for (capped, post-validation). */
    val requestedSats: Long,
    /** Sum of per-recipient sats actually billed across all invoices. */
    val billedSats: Long,
    /** Comment attached to every zap request. */
    val comment: String,
    /** One invoice per recipient — multiple entries when the note has
     *  NIP-57 zap-split tags. Pay each one in a Lightning wallet to
     *  complete the zap; invoices with non-null [ZapInvoice.invoiceError]
     *  couldn't be fetched and won't go through. */
    val invoices: List<ZapInvoice>,
)
