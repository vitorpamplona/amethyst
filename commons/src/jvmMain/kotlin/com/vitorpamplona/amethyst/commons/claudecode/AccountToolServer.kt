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
package com.vitorpamplona.amethyst.commons.claudecode

import com.vitorpamplona.agents.HostTool
import com.vitorpamplona.agents.HostToolResult
import com.vitorpamplona.agents.HostToolServer
import com.vitorpamplona.amethyst.commons.actions.ReplyActions
import com.vitorpamplona.amethyst.commons.actions.SearchActions
import com.vitorpamplona.amethyst.commons.home.dal.HomeNewThreadFeedFilter
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.ThreadAssembler
import com.vitorpamplona.amethyst.commons.model.composer.messageTags
import com.vitorpamplona.amethyst.commons.notifications.dal.NotificationFeedFilter
import com.vitorpamplona.amethyst.commons.relays.eventsync.EventSync
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState
import com.vitorpamplona.amethyst.commons.service.uploads.mediaUriOfFile
import com.vitorpamplona.amethyst.commons.viewmodels.BaseAccountViewModel
import com.vitorpamplona.quartz.lightning.LnInvoiceUtil
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchFirst
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip19Bech32.decodeEventIdAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.IErrorResponseLike
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Response
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okio.Path.Companion.toPath
import java.io.File

/**
 * What the user does in Amethyst, as tools the assistant can call: read a note, post, reply,
 * upload a file, zap, pay an invoice, sync their events across relays. Each runs the path the
 * app's own screen runs. Reading runs without asking; every post, upload, payment and sync asks
 * the user, every time ([alwaysAsks]), with [describe] saying exactly what would go out.
 */
class AccountToolServer(
    private val accountViewModel: BaseAccountViewModel,
) : HostToolServer {
    override val name = NAME
    override val tools = TOOLS

    private val account get() = accountViewModel.account

    override fun alwaysAsks(tool: String): Boolean = tool in ALWAYS_ASK

    override suspend fun call(
        tool: String,
        arguments: JsonObject,
    ): HostToolResult {
        if (tool in ALWAYS_ASK && !account.isWriteable()) return HostToolResult(READ_ONLY, isError = true)
        return when (tool) {
            GET_NOTE -> note(arguments)?.let { HostToolResult(explain(it)) } ?: unknownNote(arguments)
            POST -> post(arguments.string("text"))
            REPLY -> reply(arguments)
            UPLOAD -> upload(arguments)
            ZAP -> zap(arguments)
            PAY -> pay(arguments.string("invoice"))
            WALLET -> HostToolResult(walletStatus())
            SYNC -> {
                accountViewModel.eventSync.start()
                HostToolResult("Relay sync started. It copies your events to your outbox, mentions to your inbox and DMs to your DM relays. Ask ${RELAY_SYNC_STATUS_NAME} for progress.")
            }
            SYNC_STATUS -> HostToolResult(syncStatus(accountViewModel.eventSync.syncState.value))
            SEARCH_NOTES -> searchNotes(arguments)
            SEARCH_PEOPLE -> searchPeople(arguments)
            READ_THREAD -> readThread(arguments)
            READ_PROFILE -> readProfile(arguments)
            READ_FEED -> readFeed(arguments, notifications = false)
            READ_NOTIFICATIONS -> readFeed(arguments, notifications = true)
            else -> HostToolResult("No tool named $tool", isError = true)
        }
    }

    /** For the approval card: the post as it would go out, the payment and who receives it. */
    override fun describe(
        tool: String,
        arguments: JsonObject,
    ): String? =
        when (tool) {
            POST -> "Post publicly:\n${arguments.string("text").orEmpty()}"
            REPLY -> {
                val parent = cachedNote(arguments.string("note"))
                "Reply publicly to ${parent?.author?.toBestDisplayName() ?: "a note"}" +
                    (parent?.event?.content?.let { " (“${it.take(SNIPPET)}”)" } ?: "") +
                    ":\n${arguments.string("text").orEmpty()}"
            }
            UPLOAD -> {
                val file = arguments.string("path")?.let(::File)
                "Upload ${file?.name ?: "a file"}" +
                    (file?.takeIf { it.isFile }?.let { " (${it.length() / 1024} KB)" } ?: "") +
                    " to ${account.settings.defaultFileServer.name}. Anyone with the link can see it."
            }
            ZAP -> {
                val note = cachedNote(arguments.string("note"))
                "Zap ${arguments.long("sats") ?: "?"} sats to ${note?.author?.toBestDisplayName() ?: "the note's author"}" +
                    (note?.event?.content?.let { " for “${it.take(SNIPPET)}”" } ?: "") +
                    (arguments.string("comment")?.takeIf { it.isNotBlank() }?.let { "\nComment: $it" } ?: "")
            }
            PAY -> {
                val invoice = arguments.string("invoice").orEmpty()
                val sats = runCatching { LnInvoiceUtil.getAmountInSats(invoice) }.getOrNull()
                "Pay a Lightning invoice" + (sats?.let { " of ${it.toLong()} sats" } ?: " (amount unknown)") + " from your connected wallet"
            }
            SYNC -> "Sync your events: download them from every relay you know and send them to your own relays"
            else -> null
        }

    private suspend fun post(text: String?): HostToolResult {
        if (text.isNullOrBlank()) return HostToolResult("Give the text to post.", isError = true)
        val event = account.signAndComputeBroadcast(TextNoteEvent.build(text.trim()) { messageTags(text.trim()) })
        return HostToolResult("Posted. Its id is ${noteIdOf(event)}.")
    }

    private suspend fun reply(arguments: JsonObject): HostToolResult {
        val text = arguments.string("text")?.trim()
        if (text.isNullOrBlank()) return HostToolResult("Give the text of the reply.", isError = true)
        val parent = note(arguments)?.event ?: return unknownNote(arguments)
        val signed =
            if (parent is TextNoteEvent && !ReplyActions.repliesAsComment(parent)) {
                ReplyActions.replyTo(EventHintBundle(parent), text, account.signer)
            } else {
                ReplyActions.commentOn(EventHintBundle(parent), text, account.signer)
            }
        // The composer's routing: our outbox and the inboxes of everyone the reply notifies.
        account.cache.justConsumeMyOwnEvent(signed)
        account.client.publish(signed, account.computeRelayListToBroadcast(signed))
        return HostToolResult("Replied. The reply's id is ${noteIdOf(signed)}.")
    }

    private suspend fun upload(arguments: JsonObject): HostToolResult {
        val path = arguments.string("path") ?: return HostToolResult("Give the file's path.", isError = true)
        val file = File(path)
        if (!file.isFile) return HostToolResult("There is no file at $path.", isError = true)
        val result =
            UploadOrchestrator().upload(
                uri = mediaUriOfFile(file.absolutePath.toPath()),
                mimeType = null,
                alt = arguments.string("alt")?.takeIf { it.isNotBlank() },
                contentWarningReason = null,
                compressionQuality = CompressorQuality.MEDIUM,
                server = account.settings.defaultFileServer,
                account = account,
                uploader = accountViewModel.mediaUploader,
            )
        return when (result) {
            is UploadingState.Finished ->
                when (val done = result.result) {
                    is UploadOrchestrator.OrchestratorResult.ServerResult -> HostToolResult("Uploaded: ${done.url}\nPut this link in a post to share it.")
                    else -> HostToolResult("The upload finished without a link.", isError = true)
                }
            is UploadingState.Error -> HostToolResult("The upload failed: ${result.error.name.lowercase().replace('_', ' ')} ${result.params.joinToString(" ")}".trim(), isError = true)
        }
    }

    private suspend fun zap(arguments: JsonObject): HostToolResult {
        val sats = arguments.long("sats")?.takeIf { it > 0 } ?: return HostToolResult("Give a whole number of sats above zero.", isError = true)
        val note = note(arguments) ?: return unknownNote(arguments)
        val outcome = CompletableDeferred<HostToolResult>()
        accountViewModel.zap(
            note = note,
            amountInMillisats = sats * 1000,
            pollOption = null,
            message = arguments.string("comment").orEmpty(),
            onError = { title, message, _ -> outcome.complete(HostToolResult("$title: $message", isError = true)) },
            onProgress = { if (it >= 1f) outcome.complete(HostToolResult("Zapped $sats sats.")) },
            onPayViaIntent = { outcome.complete(HostToolResult(NO_WALLET, isError = true)) },
        )
        return awaitPayment(outcome)
    }

    private suspend fun pay(invoice: String?): HostToolResult {
        val bolt11 = invoice?.trim()?.removePrefix("lightning:")
        if (bolt11.isNullOrEmpty()) return HostToolResult("Give the invoice.", isError = true)
        if (account.settings.nwcWallets.value
                .isEmpty()
        ) {
            return HostToolResult(NO_WALLET, isError = true)
        }
        val outcome = CompletableDeferred<HostToolResult>()
        account.zaps.sendZapPaymentRequestFor(
            bolt11 = bolt11,
            zappedNote = null,
            onTimeout = { outcome.complete(HostToolResult(NO_ANSWER, isError = true)) },
        ) { response: Response? ->
            outcome.complete(
                when (response) {
                    is PayInvoiceSuccessResponse -> HostToolResult("Paid.")
                    null -> HostToolResult(NO_ANSWER, isError = true)
                    else -> HostToolResult("The wallet refused: ${(response as? IErrorResponseLike)?.errorMessage() ?: "no reason given"}", isError = true)
                },
            )
        }
        return awaitPayment(outcome)
    }

    private suspend fun awaitPayment(outcome: CompletableDeferred<HostToolResult>): HostToolResult =
        try {
            withTimeout(PAYMENT_TIMEOUT_MS) { outcome.await() }
        } catch (_: TimeoutCancellationException) {
            HostToolResult(NO_ANSWER, isError = true)
        }

    /** Notes matching [arguments]' query: the relays that search (NIP-50), and what this app already holds. */
    private suspend fun searchNotes(arguments: JsonObject): HostToolResult {
        val limit = limitOf(arguments)
        val since = arguments.long("hours")?.takeIf { it > 0 }?.let { TimeUtils.now() - it * HOUR }
        val filter =
            SearchActions.searchNotesFilter(arguments.string("query").orEmpty(), SEARCH_KINDS, limit = limit, since = since)
                ?: return HostToolResult("Give something to search for.", isError = true)
        fetch(account.searchOnlyRelays(), filter)
        val found =
            withContext(Dispatchers.Default) {
                account.cache
                    .findNotesMatching(listOf(filter), account.hiddenUsers.flow.value)
                    .filter { note -> since == null || (note.event?.createdAt ?: 0) >= since }
                    .sortedByDescending { it.event?.createdAt ?: 0 }
                    .take(limit)
            }
        if (found.isEmpty()) return HostToolResult("No notes found for “${filter.search}”.")
        return HostToolResult(found.joinToString("\n\n") { line(it) })
    }

    private suspend fun searchPeople(arguments: JsonObject): HostToolResult {
        val query = arguments.string("query")?.trim().orEmpty()
        val filter = SearchActions.searchProfilesFilter(query, limit = PEOPLE_LIMIT) ?: return HostToolResult("Give a name to search for.", isError = true)
        fetch(account.searchOnlyRelays(), filter)
        val people = withContext(Dispatchers.Default) { account.cache.search.findUsersStartingWith(query, account, PEOPLE_LIMIT) }
        if (people.isEmpty()) return HostToolResult("No one found for “$query”.")
        return HostToolResult(
            people.joinToString("\n") { user ->
                val about =
                    user
                        .metadataOrNull()
                        ?.flow
                        ?.value
                        ?.info
                        ?.about
                        ?.let { " — " + oneLine(it).take(SNIPPET) } ?: ""
                val follows = if (account.isFollowing(user.pubkeyHex)) " (you follow them)" else ""
                "${user.toBestDisplayName()} ${user.pubkeyNpub()}$follows$about"
            },
        )
    }

    /** A person: their profile, and their latest notes from the relays and the cache. */
    private suspend fun readProfile(arguments: JsonObject): HostToolResult {
        val text =
            arguments
                .string("user")
                ?.trim()
                ?.removePrefix("nostr:")
                .orEmpty()
        val pubkey = decodePublicKeyAsHexOrNull(text) ?: return HostToolResult("Give the person's npub, nprofile or hex key. search_people finds it from a name.", isError = true)
        val limit = limitOf(arguments)
        val relays = (account.homeRelays.flow.value + account.defaultGlobalRelays.flow.value).take(MAX_FETCH_RELAYS).toSet()
        fetch(relays, Filter(kinds = listOf(MetadataEvent.KIND), authors = listOf(pubkey), limit = 1))
        val notesFilter = Filter(kinds = SEARCH_KINDS, authors = listOf(pubkey), limit = limit)
        fetch(relays, notesFilter)
        val user = account.cache.getOrCreateUser(pubkey)
        val notes =
            withContext(Dispatchers.Default) {
                account.cache
                    .findNotesMatching(listOf(notesFilter), account.hiddenUsers.flow.value)
                    .sortedByDescending { it.event?.createdAt ?: 0 }
                    .take(limit)
            }
        val info =
            user
                .metadataOrNull()
                ?.flow
                ?.value
                ?.info
        return HostToolResult(
            buildString {
                appendLine("${user.toBestDisplayName()} ${user.pubkeyNpub()}" + if (account.isFollowing(pubkey)) " (you follow them)" else "")
                info?.about?.let { appendLine("Bio: " + oneLine(it).take(NOTE_CHARS)) }
                user.lnAddress()?.let { appendLine("Takes zaps at $it.") }
                appendLine()
                if (notes.isEmpty()) append("No notes found.") else append("Latest notes:\n\n" + notes.joinToString("\n\n") { line(it) })
            },
        )
    }

    /** The whole conversation around a note, root first, after asking the relays for its replies. */
    private suspend fun readThread(arguments: JsonObject): HostToolResult {
        val note = note(arguments) ?: return unknownNote(arguments)
        val root = ThreadAssembler(account.cache).findRoot(note.idHex) ?: note
        val relays = (account.homeRelays.flow.value + account.defaultGlobalRelays.flow.value).take(MAX_FETCH_RELAYS).toSet()
        fetch(relays, Filter(kinds = REPLY_KINDS, tags = mapOf("e" to listOf(root.idHex)), limit = MAX_THREAD))
        fetch(relays, Filter(kinds = REPLY_KINDS, tags = mapOf("E" to listOf(root.idHex)), limit = MAX_THREAD))
        val thread = withContext(Dispatchers.Default) { ThreadAssembler(account.cache).findThreadFor(root.idHex) }
        val notes =
            thread
                ?.allNotes
                ?.filter { note -> note.event != null && note.author?.let { account.isHidden(it) } != true }
                ?.sortedBy { it.event?.createdAt ?: 0 }
                ?.take(MAX_THREAD)
                .orEmpty()
        if (notes.isEmpty()) return HostToolResult(explain(note))
        return HostToolResult("${notes.size} notes, oldest first:\n\n" + notes.joinToString("\n\n") { line(it) })
    }

    /**
     * What the home feed or the notifications show now, newest first, over the last [arguments]
     * hours: what this app has loaded, as the screens show it, the user's mutes applied.
     */
    private suspend fun readFeed(
        arguments: JsonObject,
        notifications: Boolean,
    ): HostToolResult {
        val limit = limitOf(arguments)
        val hours = arguments.long("hours")?.takeIf { it > 0 } ?: DEFAULT_HOURS
        val since = TimeUtils.now() - hours * HOUR
        val notes =
            withContext(Dispatchers.Default) {
                val feed = if (notifications) NotificationFeedFilter(account).feed() else HomeNewThreadFeedFilter(account).feed()
                feed.filter { (it.event?.createdAt ?: 0) >= since }.take(limit)
            }
        if (notes.isEmpty()) return HostToolResult("Nothing in the last $hours hours that the app has loaded. The app loads more while its feeds are open.")
        return HostToolResult(notes.joinToString("\n\n") { line(it) })
    }

    private suspend fun fetch(
        relays: Set<NormalizedRelayUrl>,
        filter: Filter,
    ) {
        if (relays.isEmpty()) return
        val events =
            try {
                account.client.fetchAll(filters = relays.associateWith { listOf(filter) }, idleTimeoutMs = FETCH_TIMEOUT_MS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Search on ${relays.size} relays failed", e)
                emptyList()
            }
        events.forEach { account.cache.justConsume(it, null, wasVerified = false) }
    }

    /** One note for the model: id, author, when, what kind, and its text, shortened. */
    private fun line(note: Note): String {
        val event = note.event ?: return note.idHex
        val ago = agoOf(event.createdAt)
        val text = oneLine(event.content).let { if (it.length > NOTE_CHARS) it.take(NOTE_CHARS) + "…" else it }
        return "${noteIdOf(event)} · ${note.author?.toBestDisplayName() ?: event.pubKey.take(8)} · $ago · kind ${event.kind}\n$text"
    }

    private fun agoOf(createdAt: Long): String {
        val seconds = (TimeUtils.now() - createdAt).coerceAtLeast(0)
        return when {
            seconds < HOUR -> "${seconds / 60} min ago"
            seconds < DAY -> "${seconds / HOUR} h ago"
            else -> "${seconds / DAY} d ago"
        }
    }

    private fun limitOf(arguments: JsonObject): Int = (arguments.long("limit") ?: DEFAULT_LIMIT.toLong()).toInt().coerceIn(1, MAX_LIMIT)

    private fun walletStatus(): String {
        val wallets = account.settings.nwcWallets.value
        return if (wallets.isEmpty()) {
            "No wallet is connected, so zaps and payments would go to a wallet app instead. The user can connect one (Nostr Wallet Connect) in Settings › Zaps."
        } else {
            "${wallets.size} wallet(s) connected over Nostr Wallet Connect. Zaps and invoices are paid from the default one."
        }
    }

    private fun syncStatus(state: EventSync.SyncState): String =
        when (state) {
            EventSync.SyncState.Idle -> "No relay sync has run since the app started."
            is EventSync.SyncState.Running ->
                "Running: ${state.relaysCompleted.value} of ${state.totalRelays.value} relays read, " +
                    "${state.eventsReceived.value} events found, ${state.eventsSent.value} sent, ${state.eventsAccepted.value} accepted."
            is EventSync.SyncState.Done ->
                "Done in ${state.durationMs / 1000} s: ${state.totalEventsReceived} events found, ${state.totalEventsSent} sent, ${state.totalEventsAccepted} accepted."
            is EventSync.SyncState.Error -> "It stopped: ${state.message}"
        }

    private fun explain(note: Note): String {
        val event = note.event ?: return "That note hasn't loaded."
        return buildString {
            appendLine("${noteIdOf(event)} — kind ${event.kind}, by ${note.author?.toBestDisplayName() ?: event.pubKey}")
            note.author?.lnAddress()?.let { appendLine("Its author takes zaps at $it.") } ?: appendLine("Its author has no Lightning address: it can't be zapped.")
            append(event.content.take(MAX_CONTENT))
        }
    }

    /** The note [arguments] names (`note1…`, `nevent1…`, `nostr:` or hex), from the cache or the relays. */
    private suspend fun note(arguments: JsonObject): Note? {
        val id = arguments.string("note")?.let(::idOf) ?: return null
        account.cache
            .getNoteIfExists(id)
            ?.takeIf { it.event != null }
            ?.let { return it }
        val relays = (account.homeRelays.flow.value + account.defaultGlobalRelays.flow.value).take(MAX_FETCH_RELAYS)
        val found = account.client.fetchFirst(FETCH_ID, relays.associateWith { listOf(Filter(ids = listOf(id))) }, FETCH_TIMEOUT_MS) ?: return null
        account.cache.justConsume(found, null, wasVerified = false)
        return account.cache.getNoteIfExists(id)
    }

    private fun cachedNote(text: String?): Note? = text?.let(::idOf)?.let { account.cache.getNoteIfExists(it) }

    private fun unknownNote(arguments: JsonObject) = HostToolResult("Could not find the note ${arguments.string("note")}. Give its note1…, nevent1… or hex id.", isError = true)

    companion object {
        const val NAME = "amethyst-account"
        const val GET_NOTE = "get_note"
        const val POST = "post_note"
        const val REPLY = "reply_to_note"
        const val UPLOAD = "upload_file"
        const val ZAP = "zap_note"
        const val PAY = "pay_invoice"
        const val WALLET = "wallet_status"
        const val SYNC = "start_relay_sync"
        const val SYNC_STATUS = "relay_sync_status"
        const val SEARCH_NOTES = "search_notes"
        const val SEARCH_PEOPLE = "search_people"
        const val READ_THREAD = "read_thread"
        const val READ_PROFILE = "read_profile"
        const val READ_FEED = "read_home_feed"
        const val READ_NOTIFICATIONS = "read_notifications"
        private const val RELAY_SYNC_STATUS_NAME = SYNC_STATUS
        private const val TAG = "AccountToolServer"

        /** Every call that publishes, sends a file away or moves money. */
        val ALWAYS_ASK = setOf(POST, REPLY, UPLOAD, ZAP, PAY, SYNC)

        /** The ones that only read: they run without asking. */
        val READ_ONLY_TOOLS = listOf(GET_NOTE, WALLET, SYNC_STATUS, SEARCH_NOTES, SEARCH_PEOPLE, READ_THREAD, READ_PROFILE, READ_FEED, READ_NOTIFICATIONS)

        /**
         * What a read returns at most. Every note read goes to the model, and on the user's own AI
         * key it is billed to them: a page of results, more only when asked.
         */
        private const val DEFAULT_LIMIT = 30
        private const val MAX_LIMIT = 50
        private const val NOTE_CHARS = 500
        private const val PEOPLE_LIMIT = 15
        private const val MAX_THREAD = 60
        private const val DEFAULT_HOURS = 24L
        private const val HOUR = 3_600L
        private const val DAY = 86_400L
        private val SEARCH_KINDS = listOf(TextNoteEvent.KIND, LongFormContentEvent.KIND, CommentEvent.KIND)
        private val REPLY_KINDS = listOf(TextNoteEvent.KIND, CommentEvent.KIND)

        private const val SNIPPET = 80
        private const val MAX_CONTENT = 4_000
        private const val MAX_FETCH_RELAYS = 20
        private const val FETCH_ID = "assistant-note"
        private const val FETCH_TIMEOUT_MS = 8_000L
        private const val PAYMENT_TIMEOUT_MS = 90_000L
        private const val READ_ONLY = "This account can't sign: it was opened with a public key only. Nothing was sent."
        private const val NO_WALLET = "No wallet is connected, so nothing was paid. The user can connect one (Nostr Wallet Connect) in Settings › Zaps."
        private const val NO_ANSWER = "The wallet hasn't answered. The payment may or may not have gone through: check the wallet before trying again."

        private fun schema(json: String) = Json.parseToJsonElement(json).jsonObject

        private const val LIMIT_ARG = """"limit":{"type":"integer","minimum":1,"maximum":50,"description":"At most this many (30 by default)"}"""

        private const val NOTE_ARG = """"note":{"type":"string","description":"The note's id: note1…, nevent1…, nostr:… or hex"}"""

        val TOOLS =
            listOf(
                HostTool(GET_NOTE, "Reads one note: who wrote it, whether it can be zapped, and its text.", schema("""{"type":"object","properties":{$NOTE_ARG},"required":["note"]}""")),
                HostTool(
                    POST,
                    "Publishes a public note (kind 1) as the user. The user approves each post, so write it in their voice and show it before calling. Links and nostr: mentions in the text are tagged as the app's composer does.",
                    schema("""{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}"""),
                ),
                HostTool(REPLY, "Publishes a public reply to a note as the user. The user approves each reply.", schema("""{"type":"object","properties":{$NOTE_ARG,"text":{"type":"string"}},"required":["note","text"]}""")),
                HostTool(
                    UPLOAD,
                    "Uploads a file from this computer to the user's media server and returns its link, to put in a post. The user approves each upload.",
                    schema("""{"type":"object","properties":{"path":{"type":"string","description":"The file's full path"},"alt":{"type":"string","description":"A description of the image for people who can't see it"}},"required":["path"]}"""),
                ),
                HostTool(
                    ZAP,
                    "Zaps a note: pays its author (and any zap splits) in sats from the user's wallet. The user approves each zap.",
                    schema("""{"type":"object","properties":{$NOTE_ARG,"sats":{"type":"integer","minimum":1},"comment":{"type":"string"}},"required":["note","sats"]}"""),
                ),
                HostTool(PAY, "Pays a Lightning invoice (bolt11) from the user's connected wallet. The user approves each payment.", schema("""{"type":"object","properties":{"invoice":{"type":"string"}},"required":["invoice"]}""")),
                HostTool(WALLET, "Says whether a wallet is connected for zaps and payments.", schema("""{"type":"object","properties":{}}""")),
                HostTool(
                    SYNC,
                    "Starts a relay sync: downloads the user's events from every relay the app knows and sends them to the user's own relays. Takes minutes. The user approves it.",
                    schema("""{"type":"object","properties":{}}"""),
                ),
                HostTool(SYNC_STATUS, "Reports the relay sync's progress or result.", schema("""{"type":"object","properties":{}}""")),
                HostTool(
                    SEARCH_NOTES,
                    "Searches notes and articles by words, on the user's search relays and in what the app has loaded. Returns up to `limit` notes, newest first: id, author, age, kind and text (shortened). Use read_thread or get_note for more of one.",
                    schema("""{"type":"object","properties":{"query":{"type":"string"},"hours":{"type":"integer","minimum":1,"description":"Only notes from the last this many hours"},$LIMIT_ARG},"required":["query"]}"""),
                ),
                HostTool(SEARCH_PEOPLE, "Finds people by name or NIP-05 address: their name, npub, whether the user follows them, and their bio.", schema("""{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}""")),
                HostTool(
                    READ_PROFILE,
                    "Reads a person: their name, bio, whether the user follows them, their Lightning address, and their latest notes.",
                    schema("""{"type":"object","properties":{"user":{"type":"string","description":"npub1…, nprofile1… or hex key"},$LIMIT_ARG},"required":["user"]}"""),
                ),
                HostTool(READ_THREAD, "Reads the whole conversation a note is part of, oldest first, asking the relays for replies the app hasn't loaded.", schema("""{"type":"object","properties":{$NOTE_ARG},"required":["note"]}""")),
                HostTool(
                    READ_FEED,
                    "Reads the user's home feed (new posts from the people they follow) as the app shows it, newest first, over the last `hours` (24 by default). Only what the app has loaded.",
                    schema("""{"type":"object","properties":{"hours":{"type":"integer","minimum":1},$LIMIT_ARG}}"""),
                ),
                HostTool(
                    READ_NOTIFICATIONS,
                    "Reads the user's notifications (replies, mentions, reactions, zaps, reposts) as the app shows them, newest first, over the last `hours` (24 by default).",
                    schema("""{"type":"object","properties":{"hours":{"type":"integer","minimum":1},$LIMIT_ARG}}"""),
                ),
            )

        /** A note id the tools take, as hex: `note1…`, `nevent1…`, `nostr:…` or hex. */
        fun idOf(text: String): String? = decodeEventIdAsHexOrNull(text.trim().removePrefix("nostr:"))

        private fun noteIdOf(event: Event): String = NNote.create(event.id)
    }
}

private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.long(key: String) = (this[key] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong()

private fun oneLine(text: String): String = text.replace(Regex("\\s+"), " ").trim()
